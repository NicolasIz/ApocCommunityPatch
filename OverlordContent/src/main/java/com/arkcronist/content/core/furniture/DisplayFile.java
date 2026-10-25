package com.arkcronist.content.core.furniture;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes the editor's values into the content file an item came from - see {@link DisplayYaml} for
 * what changes in it - safely: the file must be under contents/, its previous text is kept as a
 * backup first, and the new text replaces it in one move, so a crash leaves either file whole and
 * never half of one. Disk work: call it on the worker.
 */
public final class DisplayFile {

    /** How many backups of one file are kept; the oldest go first. */
    public static final int BACKUPS_KEPT = 10;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);
    private static final String BOM = "﻿";

    /**
     * @param file   the file written, as resolved
     * @param backup its previous text, or null when nothing changed and nothing was written
     */
    public record Saved(Path file, @Nullable Path backup) {
    }

    private DisplayFile() {
    }

    /**
     * Writes {@code transform} as {@code items.<itemId>.furniture.display} of {@code file}.
     *
     * @param contentsDir the folder content files live in; nothing outside it is written
     * @param backupsDir  where the previous text goes, under the file's path within contents/
     * @throws DisplayYaml.EditException when the file is not one the editor can change safely; the
     *                                   file is then left as it was
     */
    public static Saved save(Path contentsDir, Path backupsDir, Path file, String itemId, DisplayTransform transform,
                             Instant now) throws IOException, DisplayYaml.EditException {
        Path root = contentsDir.toRealPath();
        Path real;
        try {
            real = file.toRealPath();
        } catch (IOException exception) {
            throw new DisplayYaml.EditException(file.getFileName() + " is gone - it was moved or deleted since the"
                    + " last reload");
        }
        if (!real.startsWith(root) || !Files.isRegularFile(real)) {
            throw new DisplayYaml.EditException(file + " is not a file under contents/; it is not changed from here");
        }
        byte[] bytes = Files.readAllBytes(real);
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new DisplayYaml.EditException(root.relativize(real) + " is not UTF-8 text; it is left as it was");
        }
        boolean bom = text.startsWith(BOM);
        String body = bom ? text.substring(1) : text;
        String edited = DisplayYaml.write(body, itemId, transform);
        if (edited.equals(body)) {
            return new Saved(real, null);
        }

        Path relative = root.relativize(real);
        Path backup = backupsDir.resolve(relative.toString() + "." + STAMP.format(now) + ".bak");
        Files.createDirectories(backup.getParent());
        Files.write(backup, bytes);

        Path temp = real.resolveSibling("." + real.getFileName() + ".arkcontent-edit");
        Files.writeString(temp, bom ? BOM + edited : edited, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(real));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Not a POSIX file system: there are no permissions to carry over.
        }
        try {
            Files.move(temp, real, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, real, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        prune(backup.getParent(), real.getFileName() + ".");
        return new Saved(real, backup);
    }

    /** Keeps the newest {@link #BACKUPS_KEPT} backups of one file - their names sort by time. */
    private static void prune(Path folder, String prefix) throws IOException {
        List<Path> backups = new ArrayList<>();
        try (Stream<Path> list = Files.list(folder)) {
            list.filter(path -> {
                String name = path.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(".bak")
                        && name.length() == prefix.length() + "yyyyMMdd-HHmmss-SSS".length() + ".bak".length();
            }).forEach(backups::add);
        }
        backups.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (int i = 0; i < backups.size() - BACKUPS_KEPT; i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
