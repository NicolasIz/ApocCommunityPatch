package com.arkcronist.content.core.pack;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs the compiled folder into the zip that is sent to players.
 *
 * <p>The output is deterministic: entries in path order, every timestamp fixed. The client keeps
 * downloaded packs keyed by their SHA-1 and reuses a cached one whenever the hash it is offered
 * matches, so a zip whose bytes changed on every restart - as it would with real file times -
 * would make every player download the whole pack again after every restart for nothing. With
 * fixed bytes, the hash only moves when the content does.</p>
 *
 * <p>The zip is written beside the target and moved over it in one step, so the file named
 * {@code resource_pack.zip} is only ever a complete pack, never one being written.</p>
 */
public final class PackZipper {

    /** Stamped on every entry. Any fixed value works; this one sits well inside the DOS date range. */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    private PackZipper() {
    }

    /**
     * {@link #zip} on {@code executor}, so a caller on the server thread never waits on the disk.
     */
    public static CompletableFuture<Integer> zipAsync(Path sourceDir, Path target, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return zip(sourceDir, target);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, executor);
    }

    /**
     * Zips every file under {@code sourceDir} into {@code target}. Blocking.
     *
     * @return how many files went in
     */
    public static int zip(Path sourceDir, Path target) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            files = walk.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(file -> entryName(sourceDir, file)))
                    .toList();
        }

        Path parent = target.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = parent.resolve(target.getFileName() + ".tmp");

        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(entryName(sourceDir, file));
                entry.setTimeLocal(FIXED_TIME);
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        } catch (IOException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }

        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return files.size();
    }

    /** Zip entries always use forward slashes, whatever the server's file system does. */
    private static String entryName(Path sourceDir, Path file) {
        Path relative = sourceDir.relativize(file);
        return relative.toString().replace(relative.getFileSystem().getSeparator(), "/");
    }
}
