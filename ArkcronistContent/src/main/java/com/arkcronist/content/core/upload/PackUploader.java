package com.arkcronist.content.core.upload;

import com.arkcronist.content.core.pack.Sha1;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Uploads the finished pack to a web storage API and comes back with the link players download it
 * from.
 *
 * <p>Nothing here blocks on the network: requests go out through {@link HttpClient}'s
 * asynchronous API and every step after them is a stage on the returned future. The few steps
 * that touch the disk - reading and writing data/upload.json - run on the executor given, which
 * is the content worker, like all of this plugin's other disk work.</p>
 *
 * <ol>
 *   <li>If data/upload.json says this exact pack already went to this exact service, its link is
 *       reused - checked first when {@code verify} is on - and nothing is uploaded;</li>
 *   <li>otherwise the zip is POSTed as {@code multipart/form-data}, with a {@code Content-Length}
 *       (not chunked: many upload scripts refuse that), retried on network errors, timeouts and
 *       5xx answers with a growing pause;</li>
 *   <li>the link is found in the answer and checked, by downloading it and comparing the SHA-1,
 *       when {@code verify} is on: a client given a link to anything but these exact bytes rejects
 *       the pack;</li>
 *   <li>the link is recorded in data/upload.json.</li>
 * </ol>
 */
public final class PackUploader {

    /**
     * Where the pack can be downloaded.
     *
     * @param reused   the link of an earlier upload of this same pack; nothing was sent
     * @param verified downloaded back and its SHA-1 matched
     * @param attempts uploads tried, 0 when reused
     */
    public record Result(String url, boolean reused, boolean verified, int attempts) {
    }

    /** The pause before the first retry; each further one doubles it. */
    private static final Duration FIRST_RETRY = Duration.ofSeconds(2);

    private final UploadSettings settings;
    private final HttpClient client;
    private final Path recordFile;
    private final Executor disk;
    private final String userAgent;
    private final Duration firstRetry;

    public PackUploader(UploadSettings settings, HttpClient client, Path recordFile, Executor disk, String userAgent) {
        this(settings, client, recordFile, disk, userAgent, FIRST_RETRY);
    }

    /** With a shorter pause between retries, for tests. */
    PackUploader(UploadSettings settings, HttpClient client, Path recordFile, Executor disk, String userAgent,
                 Duration firstRetry) {
        this.settings = settings;
        this.client = client;
        this.recordFile = recordFile;
        this.disk = disk;
        this.userAgent = userAgent;
        this.firstRetry = firstRetry;
    }

    /** A client following redirects, as file hosts' download links often do. */
    public static HttpClient defaultClient(UploadSettings settings) {
        return HttpClient.newBuilder()
                .connectTimeout(settings.timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Makes {@code zip} downloadable and returns the link. Call on the {@code disk} executor.
     *
     * @return fails with an {@link UploadException} saying what went wrong
     */
    public CompletableFuture<Result> publish(byte[] zip, String sha1Hex) {
        String endpoint = settings.endpoint().toString();
        UploadRecord previous = UploadRecord.read(recordFile);
        if (previous != null && previous.matches(sha1Hex, endpoint)) {
            if (!settings.verify()) {
                return CompletableFuture.completedFuture(new Result(previous.url(), true, false, 0));
            }
            // The service may have expired the file since: checked, and uploaded again if it is gone.
            return verify(previous.url(), sha1Hex).thenCompose(matches -> matches
                    ? CompletableFuture.completedFuture(new Result(previous.url(), true, true, 0))
                    : upload(zip, sha1Hex));
        }
        return upload(zip, sha1Hex);
    }

    /** Uploads, finds the link, checks it, records it. */
    private CompletableFuture<Result> upload(byte[] zip, String sha1Hex) {
        return attempt(zip, sha1Hex, 1)
                .thenCompose(uploaded -> !settings.verify()
                        ? CompletableFuture.completedFuture(uploaded)
                        : verify(uploaded.url(), sha1Hex).thenApply(matches -> {
                            if (!matches) {
                                throw new CompletionException(new UploadException("the service took the upload, but "
                                        + uploaded.url() + " does not serve the same bytes back (SHA-1 differs) - it"
                                        + " may recompress or rename files. Players would be refused the pack."));
                            }
                            return new Result(uploaded.url(), false, true, uploaded.attempts());
                        }))
                .thenApplyAsync(result -> {
                    try {
                        new UploadRecord(sha1Hex, result.url(), settings.endpoint().toString(), Instant.now().toString())
                                .write(recordFile);
                    } catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                    return result;
                }, disk);
    }

    private CompletableFuture<Result> attempt(byte[] zip, String sha1Hex, int attempt) {
        return send(zip).thenApply(body -> {
            try {
                String value = ResponseUrl.extract(body, settings.urlPath(), settings.urlPattern());
                return new Result(ResponseUrl.finish(settings.downloadUrl(), value, sha1Hex), false, false, attempt);
            } catch (UploadException exception) {
                throw new CompletionException(exception);
            }
        }).exceptionallyCompose(error -> {
            UploadException failure = unwrap(error);
            if (!failure.retryable() || attempt > settings.retries()) {
                return CompletableFuture.failedFuture(attempt > 1
                        ? new UploadException(failure.getMessage() + " (after " + attempt + " attempts)",
                        false, failure.getCause())
                        : failure);
            }
            long pause = firstRetry.toMillis() << Math.min(attempt - 1, 5);
            Executor later = CompletableFuture.delayedExecutor(pause, TimeUnit.MILLISECONDS, disk);
            return CompletableFuture.supplyAsync(() -> null, later).thenCompose(ignored -> attempt(zip, sha1Hex, attempt + 1));
        });
    }

    /** One upload request. Completes with the answer's body, or fails with an {@link UploadException}. */
    private CompletableFuture<String> send(byte[] zip) {
        MultipartBody body = MultipartBody.create();
        for (Map.Entry<String, String> field : settings.fields().entrySet()) {
            body.field(field.getKey(), field.getValue());
        }
        body.file(settings.fileField(), settings.fileName(), "application/zip", zip);

        HttpRequest.Builder request = HttpRequest.newBuilder(settings.endpoint())
                .timeout(settings.timeout())
                .header("User-Agent", userAgent)
                .header("Content-Type", body.contentType())
                .method(settings.method(), HttpRequest.BodyPublishers.fromPublisher(
                        HttpRequest.BodyPublishers.ofByteArrays(body.chunks()), body.length()));
        settings.headers().forEach(request::header);

        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, error) -> {
                    if (error != null) {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause() : error;
                        String what = cause instanceof HttpTimeoutException
                                ? "timed out after " + settings.timeout().toSeconds() + " s"
                                : cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
                        throw new CompletionException(new UploadException("could not reach "
                                + settings.endpoint() + " - " + what, true, cause));
                    }
                    int status = response.statusCode();
                    if (status / 100 == 2) {
                        return response.body();
                    }
                    throw new CompletionException(new UploadException(describe(status, response.body()),
                            status >= 500 || status == 408 || status == 429, null));
                });
    }

    /**
     * Downloads {@code url} and compares its SHA-1 with the pack's. The body is hashed chunk by
     * chunk as it arrives, never held whole.
     */
    public CompletableFuture<Boolean> verify(String url, String sha1Hex) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("every JVM has SHA-1", exception);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(settings.timeout())
                .header("User-Agent", userAgent)
                .GET()
                .build();
        HttpResponse.BodyHandler<Void> hashing = HttpResponse.BodyHandlers.ofByteArrayConsumer(chunk ->
                chunk.ifPresent(digest::update));
        return client.sendAsync(request, hashing)
                .thenApply(response -> response.statusCode() / 100 == 2 && Sha1.hex(digest.digest()).equals(sha1Hex))
                .exceptionally(error -> false);
    }

    /** What a refusal means, in the words an admin needs. */
    static String describe(int status, String body) {
        String reason = switch (status) {
            case 401, 403 -> "the service refused the credentials - check the headers (an API key or token)";
            case 404 -> "there is no upload API at that URL - check upload.url";
            case 405 -> "the service does not accept this method at that URL - check upload.method and upload.url";
            case 413 -> "the pack is larger than the service accepts";
            case 429 -> "too many uploads - the service is rate limiting";
            default -> status >= 500 ? "the service failed" : "the service refused the upload";
        };
        return "HTTP " + status + ": " + reason + (body == null || body.isBlank() ? "" : " - " + ResponseUrl.preview(body));
    }

    private static UploadException unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof UncheckedIOException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof UploadException upload
                ? upload
                : new UploadException(cause.getClass().getSimpleName() + ": " + cause.getMessage(), false, cause);
    }
}
