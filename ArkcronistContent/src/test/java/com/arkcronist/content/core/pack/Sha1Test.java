package com.arkcronist.content.core.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha1Test {

    @Test
    void matchesTheStandardTestVector() {
        // FIPS 180-1, appendix A.
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
                Sha1.hex(Sha1.of("abc".getBytes(StandardCharsets.US_ASCII))));
    }

    @Test
    void streamingAFileGivesTheSameHashAsItsBytes(@TempDir Path temp) throws IOException {
        byte[] data = new byte[300_000];
        new Random(7).nextBytes(data);
        Path file = temp.resolve("resource_pack.zip");
        Files.write(file, data);

        assertArrayEquals(Sha1.of(data), Sha1.of(file));
        assertEquals(20, Sha1.of(file).length);

        PackArtifact artifact = PackArtifact.load(file, 1);
        assertEquals(Sha1.hex(Sha1.of(file)), artifact.sha1Hex());
        assertEquals(data.length, artifact.size());
    }
}
