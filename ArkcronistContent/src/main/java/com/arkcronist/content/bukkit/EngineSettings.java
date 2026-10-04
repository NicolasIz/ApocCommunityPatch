package com.arkcronist.content.bukkit;

import com.arkcronist.content.core.http.PackHttpServer;
import com.arkcronist.content.core.pack.PackSettings;
import com.arkcronist.content.core.upload.UploadSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * config.yml, read once and then immutable, so any thread may hold on to it.
 */
public record EngineSettings(boolean extractExamples, Pack pack, Http http, @Nullable UploadSettings upload,
                             Delivery delivery, Crops crops) {

    /** Where players download the pack from. */
    public enum Hosting {
        /** The plugin's own web server, {@code http:}. */
        BUILTIN,
        /** A link the admin hosts the zip at themselves: {@code http.external-url}. */
        EXTERNAL,
        /** A web storage API the zip is uploaded to after every rebuild: {@code upload:}. */
        UPLOAD
    }

    /** Upload wins over a fixed external link, which wins over the built-in server. */
    public Hosting hosting() {
        if (upload != null) {
            return Hosting.UPLOAD;
        }
        return http.externalUrl().isEmpty() ? Hosting.BUILTIN : Hosting.EXTERNAL;
    }

    /**
     * pack.mcmeta, and the other packs merged in. {@code description} is MiniMessage.
     *
     * @param merge other plugins' packs to merge into this one: folders holding {@code assets/}, or
     *              zips, relative to the server folder
     */
    public record Pack(String description, int format, int minFormat, int maxFormat, List<String> merge,
                       ModelData modelData) {

        public Pack(String description, int format, int minFormat, int maxFormat, List<String> merge) {
            this(description, format, minFormat, maxFormat, merge, ModelData.DEFAULT);
        }

        public Pack {
            merge = List.copyOf(merge);
        }

        public Pack(String description, int format, int minFormat, int maxFormat) {
            this(description, format, minFormat, maxFormat, List.of());
        }

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
     * @param externalUrl   where the admin hosts output/resource_pack.zip themselves - a CDN, a web
     *                      host, Dropbox - sent to players instead of the built-in server's address;
     *                      empty when not used. {@code {sha1}} in it becomes the pack's hash
     */
    public record Http(boolean enabled, String bindAddress, int port, String publicAddress, int threads,
                       String externalUrl) {

        /** The external link for this build of the pack. */
        public String externalUrl(String sha1Hex) {
            return externalUrl.replace("{sha1}", sha1Hex);
        }

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
     * Every item made from a plain vanilla material also drawn by a {@code custom_model_data}
     * number, for plugins that take only a material and a number (see ModelDataDispatch).
     *
     * @param first the lowest number handed out
     */
    public record ModelData(boolean enabled, int first) {

        public static final ModelData DEFAULT = new ModelData(true, 10000);
    }

    /**
     * How and when players are sent the pack.
     *
     * @param prompt     shown on the download screen; null leaves the client's own wording
     * @param afterLogin with a login plugin (AuthMe), send it once the player has logged in rather
     *                   than on joining
     */
    public record Delivery(boolean sendOnJoin, boolean required, Component prompt, boolean afterLogin) {

        public Delivery(boolean sendOnJoin, boolean required, Component prompt) {
            this(sendOnJoin, required, prompt, true);
        }
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
        UploadSettings upload = readUpload(section(config, "upload"), logger);
        return new EngineSettings(
                config.getBoolean("extract-examples", true),
                readPack(section(config, "pack"), logger),
                readHttp(section(config, "http"), serverIp, upload != null, logger),
                upload,
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
                    PackSettings.FIRST_ITEM_MODEL_FORMAT, 99, merges(section), modelData(section));
        }
        if (min < PackSettings.FIRST_ITEM_MODEL_FORMAT) {
            logger.warning("pack.min-format " + min + " is below 46 (1.21.4). Older clients cannot"
                    + " show these items whatever the pack claims.");
        }
        return new Pack(description, format, min, max, merges(section), modelData(section));
    }

    private static ModelData modelData(ConfigurationSection pack) {
        ConfigurationSection section = section(pack, "custom-model-data");
        return new ModelData(section.getBoolean("enabled", ModelData.DEFAULT.enabled()),
                Math.max(1, section.getInt("first", ModelData.DEFAULT.first())));
    }

    private static List<String> merges(ConfigurationSection section) {
        return section.getStringList("merge").stream().map(String::trim).filter(path -> !path.isEmpty()).toList();
    }

    /** @param uploading whether upload: is on, in which case the built-in server is only a fallback */
    private static Http readHttp(ConfigurationSection section, String serverIp, boolean uploading, Logger logger) {
        int port = section.getInt("port", 8163);
        if (port < 1 || port > 65535) {
            logger.warning("http.port " + port + " is not a valid port - using 8163.");
            port = 8163;
        }

        String externalUrl = section.getString("external-url", "").trim();
        if (!externalUrl.isEmpty() && !isHttpUrl(externalUrl.replace("{sha1}", "0"))) {
            logger.warning("http.external-url '" + externalUrl + "' is not an http(s) link - ignoring it.");
            externalUrl = "";
        }

        String publicAddress = section.getString("public-address", "").trim();
        if (publicAddress.isEmpty() && serverIp != null && !serverIp.isBlank() && !serverIp.equals("0.0.0.0")) {
            publicAddress = serverIp.trim();
        }
        boolean enabled = section.getBoolean("enabled", true);
        if (publicAddress.isEmpty()) {
            publicAddress = "127.0.0.1";
            if (enabled && externalUrl.isEmpty() && !uploading) {
                logger.warning("http.public-address is not set and server.properties has no server-ip:"
                        + " the pack URL points at 127.0.0.1, which only a client on this same machine"
                        + " can reach. Set http.public-address to your server's public IP or domain.");
            }
        }

        return new Http(enabled,
                section.getString("bind-address", "0.0.0.0").trim(),
                port,
                publicAddress,
                Math.max(1, section.getInt("threads", 4)),
                externalUrl);
    }

    /**
     * The {@code upload} section, or null when it is off - or when it is on but cannot work, which
     * is logged, so a typo leaves the built-in server serving rather than nobody.
     */
    private static @Nullable UploadSettings readUpload(ConfigurationSection section, Logger logger) {
        if (!section.getBoolean("enabled", false)) {
            return null;
        }
        String url = section.getString("url", "").trim();
        if (!isHttpUrl(url)) {
            logger.warning("upload.enabled is true but upload.url '" + url + "' is not an http(s) link - not uploading.");
            return null;
        }
        String method = section.getString("method", "POST").trim().toUpperCase(Locale.ROOT);
        if (!method.equals("POST") && !method.equals("PUT")) {
            logger.warning("upload.method must be POST or PUT, not '" + method + "' - using POST.");
            method = "POST";
        }
        ConfigurationSection response = section(section, "response");
        String rawPattern = response.getString("url-pattern", "").trim();
        Pattern pattern = null;
        if (!rawPattern.isEmpty()) {
            try {
                pattern = Pattern.compile(rawPattern);
            } catch (PatternSyntaxException exception) {
                logger.warning("upload.response.url-pattern is not a valid regular expression ("
                        + exception.getDescription() + ") - not uploading.");
                return null;
            }
        }
        String path = response.getString("url-path", "").trim();
        return new UploadSettings(URI.create(url), method,
                section.getString("file-field", "file").trim(),
                section.getString("file-name", "resource_pack.zip").trim(),
                strings(section, "fields"),
                strings(section, "headers"),
                path.isEmpty() ? null : path,
                pattern,
                section.getString("download-url", "{value}").trim(),
                Duration.ofSeconds(Math.max(5, Math.min(600, section.getInt("timeout-seconds", 60)))),
                Math.max(0, Math.min(10, section.getInt("retries", 2))),
                section.getBoolean("verify", true));
    }

    /** A section of plain key: value pairs, in the order written. */
    private static Map<String, String> strings(ConfigurationSection parent, String path) {
        Map<String, String> values = new LinkedHashMap<>();
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object value = section.get(key);
                if (value != null && !(value instanceof ConfigurationSection)) {
                    values.put(key, String.valueOf(value));
                }
            }
        }
        return values;
    }

    private static boolean isHttpUrl(String text) {
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static Delivery readDelivery(ConfigurationSection section) {
        String prompt = section.getString("prompt", "");
        return new Delivery(
                section.getBoolean("send-on-join", true),
                section.getBoolean("required", true),
                prompt.isBlank() ? null : MiniMessage.miniMessage().deserialize(prompt),
                section.getBoolean("after-login", true));
    }

    /** A missing section reads as all defaults rather than as a null to check at every use. */
    private static ConfigurationSection section(ConfigurationSection config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section != null ? section : new MemoryConfiguration();
    }
}
