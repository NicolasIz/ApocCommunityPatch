package com.arkcronist.content.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 5.0: the plugin was renamed - ArkcronistContent is OverlordContent now - and a plugin's data folder
 * is named after it. A server upgrading has everything in the old folder: its configuration, its
 * content, its database. Before anything reads it, the old folder is renamed to the new one, as it
 * is - one move, nothing copied, nothing lost.
 */
public final class FormerDataFolder {

    /** What was found, and done. */
    public enum Outcome {
        /** No old folder: a new install, or one already moved. */
        NONE,
        /** The old folder is the new one now. */
        MOVED,
        /** Both have something in them: the new one is used, the old one left as it is. */
        BOTH,
        /** The old folder could not be renamed - see the server's log. */
        FAILED
    }

    private FormerDataFolder() {
    }

    /** Renames {@code former} to {@code folder}, if there is a former and the folder is absent or empty. */
    public static Outcome adopt(Path folder, Path former) {
        if (!Files.isDirectory(former) || folder.toAbsolutePath().normalize().equals(former.toAbsolutePath().normalize())) {
            return Outcome.NONE;
        }
        try {
            if (Files.exists(folder)) {
                if (!empty(folder)) {
                    return Outcome.BOTH;
                }
                Files.delete(folder);
            }
            Files.move(former, folder);
            return Outcome.MOVED;
        } catch (IOException failure) {
            return Outcome.FAILED;
        }
    }

    private static boolean empty(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> inside = Files.list(folder)) {
            return inside.findAny().isEmpty();
        }
    }
}
