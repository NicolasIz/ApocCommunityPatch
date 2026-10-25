package com.arkcronist.content.core.upload;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseUrlTest {

    @Test
    void readsANestedJsonPath() throws Exception {
        String body = "{\"status\":\"ok\",\"data\":{\"file\":{\"url\":{\"full\":\"https://a.example/x.zip\"}}}}";
        assertEquals("https://a.example/x.zip", ResponseUrl.extract(body, "data.file.url.full", null));
    }

    @Test
    void readsArrayIndices() throws Exception {
        String body = "{\"files\":[{\"url\":\"https://a.example/first.zip\"},{\"url\":\"https://a.example/second.zip\"}]}";
        assertEquals("https://a.example/second.zip", ResponseUrl.extract(body, "files[1].url", null));
        assertEquals("https://a.example/top.zip", ResponseUrl.extract("[\"https://a.example/top.zip\"]", "[0]", null));
    }

    @Test
    void aMissingKeySaysWhereTheWalkStopped() {
        UploadException failure = assertThrows(UploadException.class,
                () -> ResponseUrl.extract("{\"data\":{\"link\":\"x\"}}", "data.url", null));
        assertTrue(failure.getMessage().contains("no 'url' at 'data'"), failure.getMessage());
        assertTrue(failure.getMessage().contains("\"link\""), "the answer is quoted: " + failure.getMessage());
    }

    @Test
    void anAnswerThatIsNotJsonIsReported() {
        UploadException failure = assertThrows(UploadException.class,
                () -> ResponseUrl.extract("<html>502 Bad Gateway</html>", "data.url", null));
        assertTrue(failure.getMessage().contains("not JSON"), failure.getMessage());
    }

    @Test
    void anObjectWhereTextWasExpectedIsReported() {
        assertThrows(UploadException.class, () -> ResponseUrl.extract("{\"data\":{\"url\":{}}}", "data.url", null));
        assertThrows(UploadException.class, () -> ResponseUrl.extract("{\"data\":true}", "data", null));
    }

    @Test
    void badPathsAreRefused() {
        assertThrows(UploadException.class, () -> ResponseUrl.segments("data..url["));
        assertThrows(UploadException.class, () -> ResponseUrl.segments(""));
        assertThrows(UploadException.class, () -> ResponseUrl.segments("files[x]"));
    }

    @Test
    void pathSegments() throws Exception {
        assertEquals(List.of("files", 0, "url"), ResponseUrl.segments("files[0].url"));
        assertEquals(List.of(2, "a"), ResponseUrl.segments("[2].a"));
    }

    @Test
    void aPatternTakesItsFirstGroupOrTheWholeMatch() throws Exception {
        String body = "Uploaded! Download: https://dl.example/abc.zip (expires in 7 days)";
        assertEquals("https://dl.example/abc.zip",
                ResponseUrl.extract(body, null, Pattern.compile("(https://\\S+\\.zip)")));
        assertEquals("https://dl.example/abc.zip", ResponseUrl.extract(body, null, Pattern.compile("https://\\S+\\.zip")));
        assertThrows(UploadException.class, () -> ResponseUrl.extract(body, null, Pattern.compile("ftp://\\S+")));
    }

    @Test
    void aBareAnswerMustBeOneLine() throws Exception {
        assertEquals("https://x.example/p.zip", ResponseUrl.extract("  https://x.example/p.zip\n", null, null));
        assertThrows(UploadException.class, () -> ResponseUrl.extract("line one\nline two", null, null));
        assertThrows(UploadException.class, () -> ResponseUrl.extract("   ", "", null));
    }

    @Test
    void theTemplateMakesTheLink() throws Exception {
        assertEquals("https://cdn.example/packs/abc123.zip?v=ff",
                ResponseUrl.finish("https://cdn.example/packs/{value}.zip?v={sha1}", "abc123", "ff"));
        assertEquals("https://x.example/p.zip", ResponseUrl.finish("{value}", "https://x.example/p.zip", "ff"));
    }

    @Test
    void onlyHttpLinksAreSentToPlayers() {
        assertThrows(UploadException.class, () -> ResponseUrl.finish("{value}", "abc123", "ff"));
        assertThrows(UploadException.class, () -> ResponseUrl.finish("{value}", "ftp://x.example/p.zip", "ff"));
        assertThrows(UploadException.class, () -> ResponseUrl.finish("{value}", "https://bad host/p.zip", "ff"));
    }

    @Test
    void longAnswersAreShortenedInMessages() {
        String preview = ResponseUrl.preview("x".repeat(500) + "\n\n  y");
        assertEquals(203, preview.length());
        assertTrue(preview.endsWith("..."));
    }

    // ---------------------------------------------------------------- the request body

    @Test
    void theBodyIsAWellFormedForm() {
        byte[] zip = {'P', 'K', 3, 4, 0, (byte) 0xFF, '\r', '\n', '-', '-'};
        MultipartBody body = MultipartBody.withBoundary("BOUNDARY")
                .field("key", "välue")
                .file("file", "pack.zip", "application/zip", zip);

        byte[] bytes = body.toByteArray();
        assertEquals(bytes.length, body.length());
        assertEquals("multipart/form-data; boundary=BOUNDARY", body.contentType());

        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("--BOUNDARY\r\nContent-Disposition: form-data; name=\"key\"\r\n\r\n"));
        assertTrue(text.endsWith("\r\n--BOUNDARY--\r\n"));
        PackUploaderTest.Received parsed = PackUploaderTest.parse("POST", new HashMap<>(), "BOUNDARY", bytes);
        assertEquals(Map.of("key", "välue"), parsed.fields());
        assertEquals("pack.zip", parsed.fileName());
        assertArrayEquals(zip, parsed.file());
    }

    @Test
    void theFileIsNotCopied() {
        byte[] zip = new byte[1024];
        MultipartBody body = MultipartBody.withBoundary("B").file("file", "p.zip", "application/zip", zip);
        assertTrue(body.chunks().stream().anyMatch(chunk -> chunk == zip));
    }

    @Test
    void namesCannotBreakOutOfTheirQuotes() {
        assertEquals("a%22b%0D%0Ac", MultipartBody.quote("a\"b\r\nc"));
    }

    @Test
    void boundariesAreRandom() {
        assertTrue(!MultipartBody.create().boundary().equals(MultipartBody.create().boundary()));
    }
}
