package com.arkcronist.content.core.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-1, the hash the client checks a downloaded pack against.
 *
 * <p>The client wants the 20 raw bytes; {@code server.properties} and most hosting panels want the
 * 40-character hex form. Both come from here. Every JDK is required to provide SHA-1, so this never
 * depends on the server's security providers.</p>
 */
public final class Sha1 {

    private static final HexFormat HEX = HexFormat.of();

    private Sha1() {
    }

    public static byte[] of(byte[] data) {
        return digest().digest(data);
    }

    /** Streams the file, so a large pack is hashed without being loaded whole. */
    public static byte[] of(Path file) throws IOException {
        MessageDigest digest = digest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    public static String hex(byte[] hash) {
        return HEX.formatHex(hash);
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("the JDK is required to provide SHA-1", exception);
        }
    }
}
