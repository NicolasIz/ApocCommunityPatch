package com.arkcronist.content.core.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A finished pack as it is served: the zip's bytes and their hash, taken together.
 *
 * <p>The web server hands out these bytes rather than reading the file on each request. The zip on
 * disk is replaced on every rebuild, and serving from a snapshot means a download that started
 * before a rebuild finishes with the pack it started with - and the hash a player was sent always
 * describes the bytes that player receives. It also sidesteps Windows, where a file that is open
 * for a download cannot be replaced.</p>
 *
 * <p>Treat both arrays as read-only; they are shared with every request.</p>
 *
 * @param entries how many files the zip holds
 */
public record PackArtifact(byte[] bytes, byte[] sha1, String sha1Hex, int entries) {

    /** Reads a finished zip and hashes it. Blocking. */
    public static PackArtifact load(Path zip, int entries) throws IOException {
        byte[] bytes = Files.readAllBytes(zip);
        byte[] sha1 = Sha1.of(bytes);
        return new PackArtifact(bytes, sha1, Sha1.hex(sha1), entries);
    }

    public int size() {
        return bytes.length;
    }
}
