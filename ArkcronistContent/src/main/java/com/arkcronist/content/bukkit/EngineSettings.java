package com.arkcronist.content.bukkit;

import com.arkcronist.content.core.http.PackHttpServer;
import com.arkcronist.content.core.pack.PackSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * config.yml, read once and then immutable, so any thread may hold on to it.
 */
public record EngineSettings(boolean extractExamples, Pack pack, Http http, Delivery delivery, Crops crops) {

    /** pack.mcmeta. {@code description} is MiniMessage. */
    public record Pack(String description, int format, int minFormat, int maxFormat) {

        public PackSettings toPackSettings() {
            Component text = MiniMessage.miniMessage().deserialize(description);
            return new PackSettings(GsonComponentSerializer.gson().serializeToTree(text),
                    format, minFormat, maxFormat);
        }
    }

    /**
     * The built-in web server.
     *
     * @param publicAddress the host players' clients download from - which is not the same thing
     *                      as the address the server listens on, behind NAT or a proxy
     */
    public record Http(boolean enabled, String bindAddress, int port, String publicAddress, int threads) {

        /**
         * The URL a player is sent for this build of the pack.
         *
         * <p>The hash rides along as a query string the server ignores. The client decides whether
         * to download by the hash it is given, not the URL, so this is not needed for players; it
         * is for any cache or CDN placed in front of the port, which would otherwise keep serving
         * the previous zip under an unchanged URL.</p>
         */
        public String packUrl(String sha1Hex) {
            String host = publicAddress.contains(":") && !publicAddress.startsWith("[")
                    ? "[" + publicAddress + "]"
                    : publicAddress;
            return "http://" + host + ":" + port + PackHttpServer.PATH + "?sha1=" + sha1Hex;
        }
    }

    /**
     * How and when players are sent the pack.
     *
     * @param prompt shown on the download screen; null leaves the client's own wording
     */
    public record Delivery(boolean sendOnJoin, boolean required, Component prompt) {
    }

    /**
     * Custom crops.
     *
     * @param tickSeconds how often the growth scheduler adds time to every crop; growth is measured
     *                    in seconds either way, this only sets how finely it is applied
     */
    public record Crops(int tickSeconds) {
    }

    static EngineSettings read(FileConfiguration config, String serverIp, Logger logger) {
        return new EngineSettings(
                config.getBoolean("extract-examples", true),
                readPack(section(config, "pack"), logger),
                readHttp(section(config, "http"), serverIp, logger),
                readDelivery(section(config, "delivery")),
                new Crops(Math.max(1, Math.min(60, section(config, "crops").getInt("tick-seconds", 5)))));
    }

    private static Pack readPack(ConfigurationSection section, Logger logger) {
        String description = section.getString("description", "Custom content");
        int format = section.getInt("format", PackSettings.FIRST_ITEM_MODEL_FORMAT);
        int min = section.getInt("min-format", PackSettings.FIRST_ITEM_MODEL_FORMAT);
        int max = section.getInt("max-format", 99);
        if (min > max || format < min || format > max) {
            logger.warning("pack.format " + format + " must lie between pack.min-format " + min
                    + " and pack.max-format " + max + " - using 46, 46 and 99.");
            return new Pack(description, PackSettings.FIRST_ITEM_MODEL_FORMAT,
                    PackSettings.FIRST_ITEM_MODEL_FORMAT, 99);
        }
        if (min < PackSettings.FIRST_ITEM_MODEL_FORMAT) {
            logger.warning("pack.min-format " + min + " is below 46 (1.21.4). Older clients cannot"
                    + " show these items whatever the pack claims.");
        }
        return new Pack(description, format, min, max);
    }

    private static Http readHttp(ConfigurationSection section, String serverIp, Logger logger) {
        int port = section.getInt("port", 8163);
        if (port < 1 || port > 65535) {
            logger.warning("http.port " + port + " is not a valid port - using 8163.");
            port = 8163;
        }

        String publicAddress = section.getString("public-address", "").trim();
        if (publicAddress.isEmpty() && serverIp != null && !serverIp.isBlank() && !serverIp.equals("0.0.0.0")) {
            publicAddress = serverIp.trim();
        }
        boolean enabled = section.getBoolean("enabled", true);
        if (publicAddress.isEmpty()) {
            publicAddress = "127.0.0.1";
            if (enabled) {
                logger.warning("http.public-address is not set and server.properties has no server-ip:"
                        + " the pack URL points at 127.0.0.1, which only a client on this same machine"
                        + " can reach. Set http.public-address to your server's public IP or domain.");
            }
        }

        return new Http(enabled,
                section.getString("bind-address", "0.0.0.0").trim(),
                port,
                publicAddress,
                Math.max(1, section.getInt("threads", 4)));
    }

    private static Delivery readDelivery(ConfigurationSection section) {
        String prompt = section.getString("prompt", "");
        return new Delivery(
                section.getBoolean("send-on-join", true),
                section.getBoolean("required", true),
                prompt.isBlank() ? null : MiniMessage.miniMessage().deserialize(prompt));
    }

    /** A missing section reads as all defaults rather than as a null to check at every use. */
    private static ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }
}
