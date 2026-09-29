package com.arkcronist.content.core.http;

import com.arkcronist.content.core.pack.PackArtifact;
import com.arkcronist.content.core.pack.Sha1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackHttpServerTest {

    private final AtomicReference<PackArtifact> current = new AtomicReference<>();
    private final HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    private PackHttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = new PackHttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 2,
                current::get, Logger.getLogger("test"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void servesTheCurrentPack() throws Exception {
        PackArtifact pack = artifact("first build");
        current.set(pack);

        HttpResponse<byte[]> response = send("GET", PackHttpServer.PATH + "?sha1=" + pack.sha1Hex(), null);

        assertEquals(200, response.statusCode());
        assertArrayEquals(pack.bytes(), response.body());
        assertEquals("application/zip", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals('"' + pack.sha1Hex() + '"', response.headers().firstValue("ETag").orElseThrow());
    }

    @Test
    void aRebuildIsServedWithoutARestart() throws Exception {
        current.set(artifact("first build"));
        send("GET", PackHttpServer.PATH, null);

        PackArtifact second = artifact("second build");
        current.set(second);
        assertArrayEquals(second.bytes(), send("GET", PackHttpServer.PATH, null).body());
    }

    @Test
    void headGivesTheLengthWithoutTheBody() throws Exception {
        PackArtifact pack = artifact("some bytes");
        current.set(pack);

        HttpResponse<byte[]> response = send("HEAD", PackHttpServer.PATH, null);

        assertEquals(200, response.statusCode());
        assertEquals(0, response.body().length);
        assertEquals(String.valueOf(pack.size()),
                response.headers().firstValue("Content-Length").orElseThrow());
    }

    @Test
    void answersOnlyTheOnePath() throws Exception {
        current.set(artifact("x"));

        assertEquals(404, send("GET", "/", null).statusCode());
        assertEquals(404, send("GET", "/config.yml", null).statusCode());
        assertEquals(404, send("GET", PackHttpServer.PATH + "/../../config.yml", null).statusCode());
        assertEquals(404, send("GET", PackHttpServer.PATH + "/extra", null).statusCode());
    }

    @Test
    void onlyReads() throws Exception {
        current.set(artifact("x"));

        HttpResponse<byte[]> response = send("POST", PackHttpServer.PATH, null);

        assertEquals(405, response.statusCode());
        assertEquals("GET, HEAD", response.headers().firstValue("Allow").orElseThrow());
    }

    @Test
    void saysComeBackLaterBeforeTheFirstBuild() throws Exception {
        HttpResponse<byte[]> response = send("GET", PackHttpServer.PATH, null);

        assertEquals(503, response.statusCode());
        assertTrue(response.headers().firstValue("Retry-After").isPresent());
    }

    @Test
    void anUnchangedPackIsNotSentAgain() throws Exception {
        PackArtifact pack = artifact("cached");
        current.set(pack);

        HttpResponse<byte[]> response = send("GET", PackHttpServer.PATH, '"' + pack.sha1Hex() + '"');

        assertEquals(304, response.statusCode());
        assertEquals(0, response.body().length);
    }

    private HttpResponse<byte[]> send(String method, String path, String ifNoneMatch) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (ifNoneMatch != null) {
            request.header("If-None-Match", ifNoneMatch);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static PackArtifact artifact(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] sha1 = Sha1.of(bytes);
        return new PackArtifact(bytes, sha1, Sha1.hex(sha1), 1);
    }
}
