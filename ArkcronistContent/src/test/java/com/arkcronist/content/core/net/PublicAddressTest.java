package com.arkcronist.content.core.net;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only a public IP address, written as one, is taken from a "what is my IP" service. */
class PublicAddressTest {

    @Test
    void onlyPublicAddressesCount() {
        for (String yes : List.of("8.8.8.8", "93.184.216.34", "1.1.1.1", "100.63.255.255", "100.128.0.1",
                "172.15.0.1", "172.32.0.1", "192.169.0.1", "223.255.255.254", "2606:4700:4700::1111",
                "2a00:1450:4001:80b::200e")) {
            assertTrue(PublicAddress.isPublic(yes), yes);
        }
        for (String no : List.of("10.0.0.5", "127.0.0.1", "0.0.0.0", "169.254.1.1", "172.16.0.1", "172.31.255.255",
                "192.168.1.20", "100.64.0.1", "100.127.255.255", "192.0.2.1", "198.51.100.7", "203.0.113.9",
                "198.18.0.1", "224.0.0.1", "255.255.255.255", "240.0.0.1", "::1", "::", "fe80::1", "fd00::1",
                "fc00::1", "ff02::1", "2001:db8::1", "::ffff:10.0.0.1", "08.8.8.8", "256.1.1.1", "1.2.3",
                "example.com", "localhost", "", "1.2.3.4.5", "8.8.8.8 ", "fe80::1%eth0")) {
            assertFalse(PublicAddress.isPublic(no), no);
        }
    }

    @Test
    void answersAreReadAsPlainTextOrJson() {
        assertEquals("93.184.216.34", PublicAddress.parse("93.184.216.34\n"));
        assertEquals("93.184.216.34", PublicAddress.parse("  93.184.216.34  \r\n"));
        assertEquals("93.184.216.34", PublicAddress.parse("{\"ip\": \"93.184.216.34\", \"country\": \"US\"}"));
        assertEquals("2606:4700:4700:0:0:0:0:1111", PublicAddress.parse("2606:4700:4700::1111"));
        assertEquals("8.8.8.8", PublicAddress.parse("::ffff:8.8.8.8"), "an IPv4 address written as IPv6 is IPv4");
        assertNull(PublicAddress.parse("<html><body>Login to the hotel Wi-Fi</body></html>"));
        assertNull(PublicAddress.parse("10.1.2.3"), "a private address is the service's view of a proxy, not ours");
        assertNull(PublicAddress.parse("{\"error\": \"rate limited\"}"));
        assertNull(PublicAddress.parse(null));
    }

    @Test
    void servicesAreAskedInTurnUntilOneGivesAPublicAddress() throws IOException {
        AtomicInteger asked = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/down", exchange -> {
            asked.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/private", exchange -> answer(exchange, asked, "192.168.0.10\n"));
        server.createContext("/page", exchange -> answer(exchange, asked, "<html>" + "x".repeat(10_000)));
        server.createContext("/good", exchange -> answer(exchange, asked, "93.184.216.34\n"));
        server.createContext("/never", exchange -> answer(exchange, asked, "1.1.1.1"));
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            // Straight to the test server, whatever proxy the machine running the tests has.
            HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
                    .connectTimeout(Duration.ofSeconds(2)).build();
            Optional<PublicAddress.Found> found = PublicAddress.discover(client, List.of(base + "/down",
                    "not a url", base + "/private", base + "/page", base + "/good", base + "/never"),
                    Duration.ofSeconds(2)).get(10, TimeUnit.SECONDS);
            assertEquals(Optional.of(new PublicAddress.Found("93.184.216.34", base + "/good")), found);
            assertEquals(4, asked.get(), "the one after the answer is not asked");

            Optional<PublicAddress.Found> none = PublicAddress.discover(client, List.of(base + "/down",
                    "http://127.0.0.1:1/closed"), Duration.ofSeconds(2)).get(10, TimeUnit.SECONDS);
            assertEquals(Optional.empty(), none, "nothing found is an answer too, never an exception");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        } finally {
            server.stop(0);
        }
        assertTrue(ProxySelector.getDefault() != null);
    }

    private static void answer(com.sun.net.httpserver.HttpExchange exchange, AtomicInteger asked, String body)
            throws IOException {
        asked.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        } catch (IOException cutOff) {
            // The client reads only the first bytes of a long page.
        }
        exchange.close();
    }
}
