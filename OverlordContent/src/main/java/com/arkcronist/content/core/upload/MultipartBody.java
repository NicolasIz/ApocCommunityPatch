package com.arkcronist.content.core.upload;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * A {@code multipart/form-data} request body (RFC 7578), the format of an HTML form with a file
 * input - which is what file hosting APIs take.
 *
 * <p>Kept as a list of byte arrays rather than copied into one, so the pack's bytes, already in
 * memory, are sent as they are: the zip is never duplicated to build the request.</p>
 */
public final class MultipartBody {

    private static final byte[] CRLF = {'\r', '\n'};

    private final String boundary;
    private final List<byte[]> chunks = new ArrayList<>();

    private MultipartBody(String boundary) {
        this.boundary = boundary;
    }

    /** A body with a random boundary - one that cannot occur inside a zip by any practical chance. */
    public static MultipartBody create() {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        return new MultipartBody("----ArkContentBoundary" + HexFormat.of().formatHex(random));
    }

    /** A body with a fixed boundary, for tests. */
    static MultipartBody withBoundary(String boundary) {
        return new MultipartBody(boundary);
    }

    /** A plain text field. */
    public MultipartBody field(String name, String value) {
        chunks.add(ascii("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + quote(name) + "\"\r\n\r\n"));
        chunks.add(value.getBytes(StandardCharsets.UTF_8));
        chunks.add(CRLF);
        return this;
    }

    /** A file field. {@code content} is sent as it is, never copied. */
    public MultipartBody file(String name, String fileName, String contentType, byte[] content) {
        chunks.add(ascii("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + quote(name)
                + "\"; filename=\"" + quote(fileName) + "\"\r\nContent-Type: " + contentType + "\r\n\r\n"));
        chunks.add(content);
        chunks.add(CRLF);
        return this;
    }

    /** The request's {@code Content-Type} header. */
    public String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    public String boundary() {
        return boundary;
    }

    /** Every part followed by the closing delimiter, in order, for {@code BodyPublishers.ofByteArrays}. */
    public List<byte[]> chunks() {
        List<byte[]> all = new ArrayList<>(chunks);
        all.add(ascii("--" + boundary + "--\r\n"));
        return all;
    }

    /** The body's length in bytes - the request's {@code Content-Length}. */
    public long length() {
        long length = 0;
        for (byte[] chunk : chunks()) {
            length += chunk.length;
        }
        return length;
    }

    /** The body as one array. For tests; uploads use {@link #chunks()}. */
    byte[] toByteArray() {
        byte[] all = new byte[Math.toIntExact(length())];
        int at = 0;
        for (byte[] chunk : chunks()) {
            System.arraycopy(chunk, 0, all, at, chunk.length);
            at += chunk.length;
        }
        return all;
    }

    /**
     * A name inside a quoted header parameter. As browsers do, a quote and line breaks are
     * percent-encoded: left raw they would end the parameter or the header early.
     */
    static String quote(String name) {
        return name.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A");
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
