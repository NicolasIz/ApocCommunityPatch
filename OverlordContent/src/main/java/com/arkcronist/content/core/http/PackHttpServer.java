package com.arkcronist.content.core.http;

import com.arkcronist.content.core.pack.PackArtifact;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A web server with one job: hand out the current resource pack.
 *
 * <p>Built on the JDK's own {@code com.sun.net.httpserver}, so the plugin ships no web framework.
 * It answers exactly one path, {@value #PATH}; everything else is a 404, so the port exposes
 * nothing but a file every player is sent anyway. Requests are handled on a small pool of its own
 * daemon threads - never the server thread - and the pool is bounded, so a burst of players joining
 * at once queues for a moment instead of multiplying threads.</p>
 *
 * <p>The pack is read from {@code source} on every request, which lets a rebuild swap it without
 * restarting anything. Until the first build has finished, requests get a 503 with a
 * {@code Retry-After}.</p>
 */
public final class PackHttpServer {

    public static final String PATH = "/resource_pack.zip";

    private final InetSocketAddress address;
    private final int threads;
    private final Supplier<PackArtifact> source;
    private final Logger logger;

    private HttpServer server;
    private ExecutorService pool;

    /**
     * @param address where to listen; port 0 picks a free one, see {@link #port()}
     * @param threads upper bound on downloads served at the same time
     * @param source  the pack to serve right now, or null while there is none
     */
    public PackHttpServer(InetSocketAddress address, int threads, Supplier<PackArtifact> source, Logger logger) {
        this.address = address;
        this.threads = Math.max(1, threads);
        this.source = source;
        this.logger = logger;
    }

    /** Binds the port and starts answering. Returns at once; requests run on the server's own pool. */
    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        HttpServer http = HttpServer.create(address, 0);
        ExecutorService workers = Executors.newFixedThreadPool(threads, daemonThreads("ArkContent-HTTP"));
        http.createContext(PATH, this::handle);
        http.setExecutor(workers);
        http.start();
        this.server = http;
        this.pool = workers;
    }

    /** Stops listening. Downloads still in flight are cut off: the server is shutting down. */
    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        pool.shutdownNow();
        server = null;
        pool = null;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    /** The port actually bound, which differs from the requested one only when that was 0. */
    public synchronized int port() {
        return server != null ? server.getAddress().getPort() : address.getPort();
    }

    private void handle(HttpExchange exchange) {
        try {
            respond(exchange);
        } catch (IOException exception) {
            // Nearly always a client that went away mid-download: a player who disconnected.
            logger.log(Level.FINE, "Pack download from " + exchange.getRemoteAddress() + " ended early", exception);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Pack request from " + exchange.getRemoteAddress() + " failed", exception);
        } finally {
            exchange.close();
        }
    }

    private void respond(HttpExchange exchange) throws IOException {
        // A context matches by prefix, so /resource_pack.zip/anything would land here too.
        if (!PATH.equals(exchange.getRequestURI().getPath())) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        Headers headers = exchange.getResponseHeaders();
        String method = exchange.getRequestMethod();
        boolean head = "HEAD".equals(method);
        if (!head && !"GET".equals(method)) {
            headers.set("Allow", "GET, HEAD");
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        PackArtifact pack = source.get();
        if (pack == null) {
            headers.set("Retry-After", "5");
            exchange.sendResponseHeaders(503, -1);
            return;
        }

        String etag = '"' + pack.sha1Hex() + '"';
        headers.set("Content-Type", "application/zip");
        headers.set("Content-Disposition", "attachment; filename=\"resource_pack.zip\"");
        headers.set("ETag", etag);
        headers.set("Cache-Control", "no-cache");

        if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            return;
        }
        if (head) {
            // The JDK wants HEAD's length as a header, not as the argument, which means "no body".
            headers.set("Content-Length", Integer.toString(pack.size()));
            exchange.sendResponseHeaders(200, -1);
            return;
        }

        exchange.sendResponseHeaders(200, pack.size());
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(pack.bytes());
        }
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
