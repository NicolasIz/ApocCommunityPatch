package com.arkcronist.content.geyser;

import com.arkcronist.content.core.geyser.GeyserExport;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 3.3: this jar as a Geyser extension, for a Geyser that runs on a proxy (Velocity, BungeeCord) or
 * standalone - where this plugin, on the Paper server behind it, cannot reach Geyser's API.
 *
 * <p>The plugin writes, after every build, {@code output/bedrock/geyser-extension/}: the items and
 * blocks Geyser is to be told ({@link GeyserExport#FILE}) and the .mcpack. Those two files go in
 * this extension's folder, {@code extensions/arkcontent/} in Geyser's own; the jar itself, the same
 * one the server runs, goes in {@code extensions/}. As Geyser starts it asks for custom items,
 * blocks and packs, and gets them from those files, registered as the plugin's hook registers them
 * on a server with Geyser-Spigot. Furniture works the same way too: the Paper server sends Bedrock
 * players - known there by Floodgate - the carrier states, and this Geyser draws them.</p>
 *
 * <p>Nothing here touches Bukkit, Paper or Gson: Geyser's classes and the JDK only.</p>
 */
public final class ArkContentGeyserExtension implements Extension {

    private @Nullable GeyserExport.Content content;
    private boolean read;

    // Geyser's event bus keeps what a listener throws to itself: each handler says so here.

    @Subscribe
    public void onDefineCustomItems(GeyserDefineCustomItemsEvent event) {
        try {
            GeyserExport.Content found = content();
            if (found != null) {
                registration().items(event, found.items());
            }
        } catch (RuntimeException | LinkageError failure) {
            failed("custom items", failure);
        }
    }

    @Subscribe
    public void onDefineCustomBlocks(GeyserDefineCustomBlocksEvent event) {
        try {
            GeyserExport.Content found = content();
            if (found != null) {
                registration().blocks(event, found.blocks(), found.carriers());
            }
        } catch (RuntimeException | LinkageError failure) {
            failed("custom blocks", failure);
        }
    }

    @Subscribe
    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        try {
            Path pack = find(GeyserExport.PACK);
            if (pack != null) {
                registration().pack(event, pack);
            } else {
                logger().warning("[bedrock] No " + GeyserExport.PACK + " in " + dataFolder() + ": Bedrock players get no"
                        + " textures for this content. Copy it there from the Paper server's"
                        + " plugins/OverlordContent/output/bedrock/geyser-extension/.");
            }
        } catch (RuntimeException | LinkageError failure) {
            failed("resource pack", failure);
        }
    }

    private void failed(String what, Throwable failure) {
        try {
            logger().error("[bedrock] Could not hand Geyser the " + what + ": " + failure, failure);
        } catch (RuntimeException | LinkageError alsoFailed) {
            System.err.println("[OverlordContent] Could not hand Geyser the " + what + ": " + failure);
            failure.printStackTrace();
        }
    }

    /**
     * 3.3.1: one of the two files, in this extension's folder - or where it lands when the server's
     * whole output/bedrock/ folder, or its geyser-extension/ folder, is copied in instead.
     */
    private @Nullable Path find(String name) {
        for (Path folder : new Path[]{dataFolder(), dataFolder().resolve("geyser-extension"),
                dataFolder().resolve("bedrock").resolve("geyser-extension")}) {
            Path file = folder.resolve(name);
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }

    private GeyserRegistration registration() {
        return new GeyserRegistration(logger()::info, logger()::warning);
    }

    /** The export, read once - the three events ask in turn; null, said once, when it is missing. */
    private synchronized @Nullable GeyserExport.Content content() {
        if (read) {
            return content;
        }
        read = true;
        Path file = find(GeyserExport.FILE);
        if (file == null) {
            logger().warning("[bedrock] No " + GeyserExport.FILE + " in " + dataFolder() + ": no custom items, blocks"
                    + " or furniture for Bedrock players. Copy both files of the Paper server's"
                    + " plugins/OverlordContent/output/bedrock/geyser-extension/ there and restart Geyser.");
            return null;
        }
        try {
            content = GeyserExport.read(Files.readString(file, StandardCharsets.UTF_8));
            logger().info("[bedrock] Read " + content.items().size() + " item(s) and " + content.blocks().size()
                    + " block(s) from " + dataFolder().relativize(file) + ".");
        } catch (IOException | RuntimeException failure) {
            logger().error("[bedrock] Could not read " + file + ": " + failure);
        }
        return content;
    }
}
