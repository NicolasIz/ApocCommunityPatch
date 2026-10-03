package com.arkcronist.content.core.upload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The last pack uploaded, and where it went: data/upload.json.
 *
 * <p>A restart rebuilds the same pack byte for byte, and without this it would be uploaded again
 * every time - a new file on the service per restart, and the rate limits most free hosts have.
 * With it, an unchanged pack keeps the link it already has.</p>
 *
 * @param endpoint where it was uploaded to; a different service in config.yml means uploading again
 * @param uploaded when, as an ISO-8601 instant, for whoever reads the file
 */
public record UploadRecord(String sha1, String url, String endpoint, String uploaded) {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Whether this record describes {@code sha1Hex} uploaded to {@code endpoint}. */
    public boolean matches(String sha1Hex, String endpoint) {
        return sha1Hex.equals(sha1) && endpoint.equals(this.endpoint) && url != null && !url.isBlank();
    }

    /** The record in {@code file}, or null if there is none or it cannot be read. Blocking. */
    public static @Nullable UploadRecord read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), UploadRecord.class);
        } catch (IOException | JsonParseException exception) {
            return null;
        }
    }

    /** Writes the record through a temporary file, so a crash never leaves half of one. Blocking. */
    public void write(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(this) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
