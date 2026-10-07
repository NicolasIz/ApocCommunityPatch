package com.arkcronist.content.core.net;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the address the internet sees this machine at, by asking small "what is my IP" services
 * with Java's own {@link HttpClient}, asynchronously - for a pack URL that works when
 * {@code http.public-address} is left empty.
 *
 * <p>An answer is believed only if it is an IP address and a public one: never a host name (which
 * would need a lookup), never a private, loopback, link-local, shared, documentation or multicast
 * address - a broken service or a captive proxy answering with its own page is passed over, and the
 * next service asked.</p>
 */
public final class PublicAddress {

    /** Asked in this order; each answers with nothing but the address. */
    public static final List<String> SERVICES = List.of(
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://icanhazip.com",
            "https://ifconfig.me/ip");

    /** More than any address with a line break; a page is cut off here and then refused. */
    private static final int MAX_ANSWER = 256;
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private static final Pattern JSON_IP = Pattern.compile("\"ip\"\\s*:\\s*\"([^\"]{1,64})\"");

    /** An address found, and the service that told it. */
    public record Found(String address, String service) {
    }

    private PublicAddress() {
    }

    /**
     * The address in a service's answer - plain text, or JSON with an {@code ip} field - or null
     * when it is not a public IP address.
     */
    public static @Nullable String parse(@Nullable String answer) {
        if (answer == null) {
            return null;
        }
        String text = answer.strip();
        if (text.startsWith("{")) {
            Matcher json = JSON_IP.matcher(text);
            text = json.find() ? json.group(1).strip() : "";
        } else {
            int newline = text.indexOf('\n');
            text = (newline < 0 ? text : text.substring(0, newline)).strip();
        }
        return isPublic(text) ? normalise(text) : null;
    }

    /** Whether {@code literal} is an IP address - written as one, not a name - that is publicly routable. */
    public static boolean isPublic(@Nullable String literal) {
        InetAddress address = literal(literal);
        if (address instanceof Inet4Address v4) {
            return publicV4(v4.getAddress());
        }
        if (address instanceof Inet6Address v6) {
            byte[] bytes = v6.getAddress();
            // Global unicast is 2000::/3; 2001:db8::/32 is for documentation.
            boolean global = (bytes[0] & 0xE0) == 0x20;
            boolean documentation = (bytes[0] & 0xFF) == 0x20 && (bytes[1] & 0xFF) == 0x01
                    && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8;
            return global && !documentation;
        }
        return false;
    }

    /** {@code literal} as an address, never looked up: null unless it is written as an IP address. */
    static @Nullable InetAddress literal(@Nullable String literal) {
        if (literal == null || literal.isEmpty() || literal.length() > 45) {
            return null;
        }
        Matcher v4 = IPV4.matcher(literal);
        try {
            if (v4.matches()) {
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    String part = v4.group(i + 1);
                    int value = Integer.parseInt(part);
                    // A leading zero is octal to some parsers and decimal to others: refused.
                    if (value > 255 || (part.length() > 1 && part.startsWith("0"))) {
                        return null;
                    }
                    bytes[i] = (byte) value;
                }
                return InetAddress.getByAddress(bytes);
            }
            // With a colon it is taken as an IPv6 literal: no lookup is made.
            if (IPV6.matcher(literal).matches() && literal.indexOf(':') >= 0) {
                return InetAddress.getByName(literal);
            }
        } catch (UnknownHostException | NumberFormatException exception) {
            return null;
        }
        return null;
    }

    private static boolean publicV4(byte[] b) {
        int a = b[0] & 0xFF;
        int c = b[1] & 0xFF;
        int d = b[2] & 0xFF;
        return !(a == 0 || a == 10 || a == 127 || a >= 224          // this network, private, loopback, multicast, reserved
                || (a == 100 && c >= 64 && c <= 127)                  // shared address space (carrier NAT)
                || (a == 169 && c == 254)                             // link-local
                || (a == 172 && c >= 16 && c <= 31)                   // private
                || (a == 192 && c == 168)                             // private
                || (a == 192 && c == 0 && (d == 0 || d == 2))         // protocol assignments, TEST-NET-1
                || (a == 198 && (c == 18 || c == 19))                 // benchmarking
                || (a == 198 && c == 51 && d == 100)                  // TEST-NET-2
                || (a == 203 && c == 0 && d == 113));                 // TEST-NET-3
    }

    /** Written the one way: {@code ::ffff:8.8.8.8} is 8.8.8.8, and IPv6 in its full form. */
    private static String normalise(String literal) {
        InetAddress address = literal(literal);
        return address == null ? literal : address.getHostAddress();
    }

    /** A client for {@link #discover}: short timeouts, redirects followed, the system's proxy settings. */
    public static HttpClient client(Duration timeout) {
        return HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * Asks {@code services} one after another until one answers with a public address. Never
     * blocks the caller; never fails - a service that is down, slow or wrong is simply passed over.
     *
     * @return completes with the address, or empty when no service gave one
     */
    public static CompletableFuture<Optional<Found>> discover(HttpClient client, List<String> services,
                                                              Duration timeout) {
        return ask(client, List.copyOf(services), 0, timeout);
    }

    private static CompletableFuture<Optional<Found>> ask(HttpClient client, List<String> services, int index,
                                                          Duration timeout) {
        if (index >= services.size()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String service = services.get(index);
        CompletableFuture<String> answer;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(service)).timeout(timeout)
                    .header("Accept", "text/plain, application/json").header("User-Agent", "ArkcronistContent")
                    .GET().build();
            answer = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(PublicAddress::read);
        } catch (RuntimeException badService) {
            answer = CompletableFuture.completedFuture(null);
        }
        return answer.handle((text, error) -> error == null ? parse(text) : null)
                .thenCompose(address -> address != null
                        ? CompletableFuture.completedFuture(Optional.of(new Found(address, service)))
                        : ask(client, services, index + 1, timeout));
    }

    /** The first bytes of a successful answer; null for an error status. */
    private static @Nullable String read(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            return new String(body.readNBytes(MAX_ANSWER), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return null;
        }
    }
}
