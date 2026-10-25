package com.arkcronist.content.core.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PackZipperTest {

    @TempDir
    Path temp;

    @Test
    void entriesAreSortedAndUseForwardSlashes() throws IOException {
        Path source = temp.resolve("pack");
        write(source, "pack.mcmeta", "{}");
        write(source, "assets/demo/textures/item/ruby.png", "png");
        write(source, "assets/demo/items/ruby.json", "{}");
        Path zip = temp.resolve("out/resource_pack.zip");

        assertEquals(3, PackZipper.zip(source, zip));

        assertEquals(List.of("assets/demo/items/ruby.json", "assets/demo/textures/item/ruby.png", "pack.mcmeta"),
                entries(zip));
        assertFalse(Files.exists(zip.resolveSibling("resource_pack.zip.tmp")));
    }

    /**
     * The client reuses a cached pack whenever the hash matches, so a rebuild of unchanged content
     * must produce the same bytes - file times included - or every restart forces a re-download.
     */
    @Test
    void sameContentGivesTheSameHashWhateverTheFileTimes() throws IOException {
        Path source = temp.resolve("pack");
        write(source, "pack.mcmeta", "{}");
        write(source, "assets/demo/models/item/ruby.json", "{\"parent\":\"item/generated\"}");
        Path zip = temp.resolve("resource_pack.zip");

        PackZipper.zip(source, zip);
        byte[] first = Sha1.of(zip);

        for (String file : List.of("pack.mcmeta", "assets/demo/models/item/ruby.json")) {
            Files.setLastModifiedTime(source.resolve(file), FileTime.from(Instant.parse("2031-05-06T07:08:09Z")));
        }
        PackZipper.zip(source, zip);
        assertArrayEquals(first, Sha1.of(zip));

        write(source, "assets/demo/models/item/ruby.json", "{\"parent\":\"item/handheld\"}");
        PackZipper.zip(source, zip);
        assertNotEquals(Sha1.hex(first), Sha1.hex(Sha1.of(zip)));
    }

    @Test
    void zipAsyncRunsOnTheGivenExecutor() throws Exception {
        Path source = temp.resolve("pack");
        write(source, "pack.mcmeta", "{}");
        Path zip = temp.resolve("resource_pack.zip");
        ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "zip-test"));
        try {
            List<String> threads = new ArrayList<>();
            worker.submit(() -> threads.add(Thread.currentThread().getName())).get();
            assertEquals(1, PackZipper.zipAsync(source, zip, worker).get());
            assertEquals(List.of("zip-test"), threads);
            assertEquals(List.of("pack.mcmeta"), entries(zip));
        } finally {
            worker.shutdownNow();
        }
    }

    private static List<String> entries(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static void write(Path root, String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
