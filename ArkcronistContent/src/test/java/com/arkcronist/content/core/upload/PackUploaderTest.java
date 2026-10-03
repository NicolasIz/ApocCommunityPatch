package com.arkcronist.content.core.upload;

import com.arkcronist.content.core.pack.Sha1;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uploads against a small storage service running in the test: takes multipart uploads, serves them back. */
class PackUploaderTest {

    /** What the fake service received in one upload. */
    record Received(String method, Map<String, String> headers, int length, Map<String, String> fields,
                    String fileField, String fileName, String fileType, byte[] file) {
    }

    @TempDir
    Path dir;

    private HttpServer server;
    private String base;
    private final ExecutorService disk = Executors.newSingleThreadExecutor();
    private final HttpClient client = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final List<Received> uploads = new CopyOnWriteArrayList<>();
    private final Map<String, byte[]> stored = new ConcurrentHashMap<>();
    /** Answers to give before a normal one, such as 503. */
    private final List<Integer> failures = new CopyOnWriteArrayList<>();
    private final AtomicInteger downloads = new AtomicInteger();
    private volatile boolean corrupt;
    private volatile String answer = "json";

    private final byte[] pack = "PK\u0003\u0004 a pretend zip with \"quotes\" and \r\n--line breaks".getBytes(StandardCharsets.ISO_8859_1);
    private final String sha1 = Sha1.hex(Sha1.of(pack));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/upload", this::upload);
        server.createContext("/files/", this::download);
        server.createContext("/moved/", exchange -> {
            exchange.getResponseHeaders().add("Location", base + "/files/" + exchange.getRequestURI().getPath().substring(7));
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        disk.shutdownNow();
    }

    @Test
    void uploadsTheZipAsAFormAndSendsBackAVerifiedLink() throws Exception {
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 0, true));

        PackUploader.Result result = uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS);

        assertEquals(1, uploads.size());
        Received received = uploads.getFirst();
        assertEquals("POST", received.method());
        assertEquals("Bearer secret", received.headers().get("authorization"));
        assertEquals(String.valueOf(received.length()), received.headers().get("content-length"),
                "sent with a length, not chunked");
        assertFalse(received.headers().containsKey("transfer-encoding"));
        assertTrue(received.headers().get("content-type").startsWith("multipart/form-data; boundary="));
        assertEquals("packs", received.fields().get("folder"));
        assertEquals("file", received.fileField());
        assertEquals("resource_pack.zip", received.fileName());
        assertEquals("application/zip", received.fileType());
        assertArrayEquals(pack, received.file());

        assertTrue(result.url().startsWith(base + "/files/"));
        assertTrue(result.verified());
        assertFalse(result.reused());
        assertEquals(1, result.attempts());

        UploadRecord record = UploadRecord.read(dir.resolve("upload.json"));
        assertNotNull(record);
        assertEquals(sha1, record.sha1());
        assertEquals(result.url(), record.url());
    }

    @Test
    void theSamePackIsNotUploadedTwice() throws Exception {
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 0, true));
        String first = uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS).url();

        PackUploader.Result again = uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS);

        assertEquals(1, uploads.size());
        assertTrue(again.reused());
        assertTrue(again.verified(), "the old link is still checked before it is trusted");
        assertEquals(first, again.url());
    }

    @Test
    void aLinkTheServiceExpiredIsReplaced() throws Exception {
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 0, true));
        String first = uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS).url();
        stored.clear();

        PackUploader.Result again = uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS);

        assertEquals(2, uploads.size());
        assertFalse(again.reused());
        assertFalse(first.equals(again.url()));
    }

    @Test
    void anotherPackOrAnotherServiceIsUploadedAgain() throws Exception {
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 0, false));
        uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS);
        byte[] changed = "a different pack".getBytes(StandardCharsets.UTF_8);
        uploader.publish(changed, Sha1.hex(Sha1.of(changed))).get(10, TimeUnit.SECONDS);
        assertEquals(2, uploads.size());

        PackUploader elsewhere = new PackUploader(new UploadSettings(URI.create(base + "/upload?bucket=2"), "POST",
                "file", "resource_pack.zip", Map.of(), Map.of(), "data.url", null, "{value}", Duration.ofSeconds(5),
                0, false), client, dir.resolve("upload.json"), disk, "test");
        elsewhere.publish(changed, Sha1.hex(Sha1.of(changed))).get(10, TimeUnit.SECONDS);
        assertEquals(3, uploads.size());
    }

    @Test
    void anOverloadedServiceIsRetried() throws Exception {
        failures.add(503);
        failures.add(502);
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 2, true));

        PackUploader.Result result = uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS);

        assertEquals(3, result.attempts());
        assertEquals(1, uploads.size(), "only the third request got through to storage");
    }

    @Test
    void retriesRunOut() {
        failures.add(503);
        failures.add(503);
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 1, true));

        UploadException failure = failure(uploader);

        assertTrue(failure.getMessage().contains("HTTP 503"), failure.getMessage());
        assertTrue(failure.getMessage().contains("after 2 attempts"), failure.getMessage());
    }

    @Test
    void aRefusalIsNotRetried() {
        failures.add(401);
        failures.add(401);
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 3, true));

        UploadException failure = failure(uploader);

        assertTrue(failure.getMessage().contains("credentials"), failure.getMessage());
        assertFalse(failure.getMessage().contains("attempts"), failure.getMessage());
        assertEquals(1, failures.size(), "one request, not four");
    }

    @Test
    void aServiceThatChangesTheFileIsCaught() {
        corrupt = true;
        PackUploader uploader = uploader(settings("data.url", null, "{value}", 0, true));

        UploadException failure = failure(uploader);

        assertTrue(failure.getMessage().contains("SHA-1"), failure.getMessage());
        assertFalse(Files.exists(dir.resolve("upload.json")), "a link that does not work is not recorded");
    }

    @Test
    void anUnreachableServiceSaysSo() {
        PackUploader uploader = new PackUploader(new UploadSettings(URI.create("http://127.0.0.1:1/upload"), "POST",
                "file", "resource_pack.zip", Map.of(), Map.of(), "data.url", null, "{value}", Duration.ofSeconds(2),
                0, false), client, dir.resolve("upload.json"), disk, "test");

        UploadException failure = failure(uploader);

        assertTrue(failure.getMessage().contains("could not reach"), failure.getMessage());
        assertTrue(failure.retryable());
    }

    @Test
    void aPlainTextAnswerIsTheLink() throws Exception {
        answer = "text";
        PackUploader.Result result = uploader(settings(null, null, "{value}", 0, true)).publish(pack, sha1)
                .get(10, TimeUnit.SECONDS);
        assertTrue(result.url().startsWith(base + "/files/"));
    }

    @Test
    void anIdIsTurnedIntoALinkByTheTemplate() throws Exception {
        answer = "id";
        PackUploader.Result result = uploader(settings(null, Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\""),
                base + "/moved/{value}?v={sha1}", 0, true)).publish(pack, sha1).get(10, TimeUnit.SECONDS);
        assertTrue(result.url().startsWith(base + "/moved/"));
        assertTrue(result.url().endsWith("?v=" + sha1));
        assertTrue(result.verified(), "redirects are followed to the file");
    }

    // ---------------------------------------------------------------- helpers

    private UploadSettings settings(String path, Pattern pattern, String template, int retries, boolean verify) {
        return new UploadSettings(URI.create(base + "/upload"), "POST", "file", "resource_pack.zip",
                Map.of("folder", "packs"), Map.of("Authorization", "Bearer secret"), path, pattern, template,
                Duration.ofSeconds(5), retries, verify);
    }

    private PackUploader uploader(UploadSettings settings) {
        return new PackUploader(settings, client, dir.resolve("upload.json"), disk, "test", Duration.ofMillis(20));
    }

    private UploadException failure(PackUploader uploader) {
        ExecutionException error = assertThrows(ExecutionException.class,
                () -> uploader.publish(pack, sha1).get(10, TimeUnit.SECONDS));
        return assertInstanceOf(UploadException.class, error.getCause());
    }

    private void upload(HttpExchange exchange) throws IOException {
        if (!failures.isEmpty()) {
            int status = failures.removeFirst();
            byte[] body = ("{\"error\":\"status " + status + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            return;
        }
        byte[] body = exchange.getRequestBody().readAllBytes();
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.getFirst()));
        String boundary = headers.get("content-type").replaceFirst(".*boundary=", "");
        Received received = parse(exchange.getRequestMethod(), headers, boundary, body);
        uploads.add(received);

        String id = "f" + uploads.size();
        byte[] kept = received.file().clone();
        if (corrupt) {
            kept[kept.length - 1] ^= 1;
        }
        stored.put(id, kept);
        String text = switch (answer) {
            case "text" -> base + "/files/" + id + "\n";
            case "id" -> "{\"ok\": true, \"id\": \"" + id + "\"}";
            default -> "{\"success\":true,\"data\":{\"url\":\"" + base + "/files/" + id + "\",\"size\":" + kept.length + "}}";
        };
        byte[] answerBytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, answerBytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(answerBytes);
        }
    }

    private void download(HttpExchange exchange) throws IOException {
        downloads.incrementAndGet();
        byte[] file = stored.get(exchange.getRequestURI().getPath().substring("/files/".length()));
        if (file == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, file.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(file);
        }
    }

    /** Splits a multipart body the way a web server would. */
    static Received parse(String method, Map<String, String> headers, String boundary, byte[] body) {
        String text = new String(body, StandardCharsets.ISO_8859_1);
        Map<String, String> fields = new HashMap<>();
        String fileField = null;
        String fileName = null;
        String fileType = null;
        byte[] file = null;
        List<String> parts = new ArrayList<>(List.of(text.split("--" + Pattern.quote(boundary))));
        parts.removeFirst();
        for (String part : parts) {
            if (part.startsWith("--")) {
                break;
            }
            int split = part.indexOf("\r\n\r\n");
            String head = part.substring(2, split);
            String content = part.substring(split + 4, part.length() - 2);
            String name = head.replaceFirst("(?s).*?; name=\"([^\"]*)\".*", "$1");
            if (head.contains("filename=")) {
                fileField = name;
                fileName = head.replaceFirst("(?s).*filename=\"([^\"]*)\".*", "$1");
                fileType = head.replaceFirst("(?s).*Content-Type: ([^\r\n]*).*", "$1");
                file = content.getBytes(StandardCharsets.ISO_8859_1);
            } else {
                fields.put(name, new String(content.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
            }
        }
        return new Received(method, headers, body.length, fields, fileField, fileName, fileType, file);
    }
}
