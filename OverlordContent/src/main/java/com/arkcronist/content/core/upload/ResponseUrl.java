package com.arkcronist.content.core.upload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the download URL in a storage service's answer to an upload.
 *
 * <p>Services answer in one of three shapes, and each has a setting:</p>
 * <ul>
 *   <li>JSON, the link somewhere inside it - {@code url-path} names where, as in
 *       {@code data.url} or {@code files[0].url};</li>
 *   <li>anything else, the link somewhere in the text - {@code url-pattern} is a regular
 *       expression whose first group (or whole match) is the link;</li>
 *   <li>the link and nothing else - neither set.</li>
 * </ul>
 *
 * <p>What is found can be put into {@code download-url}, a template with {@code {value}} and
 * {@code {sha1}} in it, for services that answer with an id rather than a link.</p>
 */
public final class ResponseUrl {

    private static final Pattern SEGMENT = Pattern.compile("([^.\\[\\]]+)|\\[(\\d+)]");

    private ResponseUrl() {
    }

    /**
     * The value at {@code path} in the JSON {@code body}, the first match of {@code pattern} in it,
     * or the whole body - in that order of preference.
     *
     * @throws UploadException when it is not there, saying what the body did contain
     */
    public static String extract(String body, @Nullable String path, @Nullable Pattern pattern) throws UploadException {
        if (pattern != null) {
            Matcher matcher = pattern.matcher(body);
            if (!matcher.find()) {
                throw new UploadException("the answer does not match url-pattern " + pattern.pattern() + ": " + preview(body));
            }
            String value = matcher.groupCount() >= 1 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
            return value.trim();
        }
        if (path != null && !path.isBlank()) {
            return atPath(body, path.trim());
        }
        String value = body.trim();
        if (value.isEmpty() || value.contains("\n")) {
            throw new UploadException("the answer is not a single link, and neither url-path nor url-pattern is set: "
                    + preview(body));
        }
        return value;
    }

    /**
     * Fills the {@code download-url} template and checks that the result is a link a client can
     * download from.
     */
    public static String finish(String template, String value, String sha1Hex) throws UploadException {
        String url = template.replace("{value}", value).replace("{sha1}", sha1Hex).trim();
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
                throw new UploadException("'" + url + "' is not an http(s) link; check download-url and the answer's shape");
            }
            return uri.toString();
        } catch (URISyntaxException exception) {
            throw new UploadException("'" + url + "' is not a valid link: " + exception.getReason());
        }
    }

    /** The parts of a path: {@code files[0].url} is "files", 0, "url". */
    static List<Object> segments(String path) throws UploadException {
        List<Object> segments = new ArrayList<>();
        Matcher matcher = SEGMENT.matcher(path);
        int at = 0;
        while (matcher.find()) {
            String between = path.substring(at, matcher.start());
            if (!between.isEmpty() && !between.equals(".")) {
                throw new UploadException("url-path '" + path + "' cannot be read near '" + between + "'");
            }
            segments.add(matcher.group(1) != null ? matcher.group(1) : Integer.parseInt(matcher.group(2)));
            at = matcher.end();
        }
        if (at != path.length() || segments.isEmpty()) {
            throw new UploadException("url-path '" + path + "' cannot be read; write it like data.url or files[0].url");
        }
        return segments;
    }

    private static String atPath(String body, String path) throws UploadException {
        JsonElement node;
        try {
            node = JsonParser.parseString(body);
        } catch (JsonParseException exception) {
            throw new UploadException("the answer is not JSON, so url-path '" + path + "' cannot be read: " + preview(body));
        }
        StringBuilder walked = new StringBuilder();
        for (Object segment : segments(path)) {
            if (segment instanceof Integer index) {
                if (!(node instanceof JsonArray array) || index >= array.size()) {
                    throw new UploadException("no element [" + index + "] at '" + walked + "' in the answer: " + preview(body));
                }
                node = array.get(index);
                walked.append('[').append(index).append(']');
            } else {
                String key = (String) segment;
                if (!(node instanceof JsonObject object) || !object.has(key)) {
                    throw new UploadException("no '" + key + "' at '" + walked + "' in the answer: " + preview(body));
                }
                node = object.get(key);
                walked.append(walked.isEmpty() ? "" : ".").append(key);
            }
        }
        if (!(node instanceof JsonPrimitive primitive) || primitive.isBoolean()) {
            throw new UploadException("'" + path + "' in the answer is not text: " + preview(body));
        }
        return primitive.getAsString().trim();
    }

    /** The start of a body, on one line, for an error message. */
    static String preview(String body) {
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "...";
    }
}
