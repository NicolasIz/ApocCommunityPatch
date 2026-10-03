package com.arkcronist.content.core.upload;

import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Where and how the pack is uploaded after a rebuild: the {@code upload} section of config.yml.
 *
 * @param endpoint    the storage API's upload URL
 * @param method      POST, or PUT for the services that want it
 * @param fileField   the form field the zip goes in - often {@code file}
 * @param fileName    the file name sent with it
 * @param fields      more form fields, sent before the file - an API key, a folder, an expiry
 * @param headers     request headers - usually {@code Authorization}
 * @param urlPath     where the link is in a JSON answer, such as {@code data.url}; null or blank
 *                    when the answer is not JSON
 * @param urlPattern  a regular expression finding the link in any other answer; null for none
 * @param downloadUrl the link players are sent, {@code {value}} being what was found in the
 *                    answer and {@code {sha1}} the pack's hash
 * @param timeout     for each request, upload or check
 * @param retries     further attempts after a failure that might pass - a network error, a 5xx
 * @param verify      download what was uploaded and compare its SHA-1 before sending anyone the
 *                    link: a service that recompresses or truncates files would otherwise hand
 *                    players a pack their client rejects
 */
public record UploadSettings(URI endpoint, String method, String fileField, String fileName, Map<String, String> fields,
                             Map<String, String> headers, @Nullable String urlPath, @Nullable Pattern urlPattern,
                             String downloadUrl, Duration timeout, int retries, boolean verify) {

    public UploadSettings {
        fields = Map.copyOf(fields);
        headers = Map.copyOf(headers);
    }
}
