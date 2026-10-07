package com.arkcronist.content.core.net;

import org.jetbrains.annotations.Nullable;

import java.net.BindException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the built-in web server tries when its port cannot be opened - the contingency that keeps
 * the plugin starting whatever the port does.
 *
 * <ol>
 *   <li>The port in use - often the server's previous run, still letting go of it: the same port
 *       again, after {@value #FIRST_RETRY_MILLIS} ms and then twice that.</li>
 *   <li>Then, or at once when the port is refused outright (a port below 1024 without root), each
 *       fallback port in turn: {@code http.fallback-ports}, or when none is set, the three ports
 *       after it (8080, 8163 and 8164 for a port below 1024).</li>
 *   <li>A bind address this machine does not have is replaced by every interface, 0.0.0.0, and the
 *       same port tried again.</li>
 *   <li>With everything tried, {@link #next} returns null: the caller keeps retrying the configured
 *       port now and then ({@link #retry}), so the server comes up once the port is free.</li>
 * </ol>
 *
 * <p>Not thread-safe; the plugin uses one plan from its worker thread only.</p>
 */
public final class BindPlan {

    public static final String ANY = "0.0.0.0";
    static final long FIRST_RETRY_MILLIS = 2_000;
    private static final int SAME_PORT_RETRIES = 2;

    /** Why a bind failed, as far as it matters for what to try next. */
    public enum Failure {
        /** Another process has the port. */
        IN_USE,
        /** Not allowed: a privileged port, a sandbox, a firewall rule. */
        DENIED,
        /** The bind address is not one of this machine's, or not an address at all. */
        NO_SUCH_ADDRESS,
        OTHER;

        /** The failure an exception from opening a server socket stands for. */
        public static Failure of(Throwable error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
                if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException
                        || message.contains("cannot assign requested address") || message.contains("unresolved")
                        || message.contains("requested address is not valid")) {
                    return NO_SUCH_ADDRESS;
                }
                if (cause instanceof AccessDeniedException || message.contains("permission denied")
                        || message.contains("access denied") || message.contains("forbidden by its access permissions")) {
                    return DENIED;
                }
                if (cause instanceof BindException || message.contains("in use")) {
                    return IN_USE;
                }
                if (cause instanceof SocketException && message.contains("bind")) {
                    return IN_USE;
                }
            }
            return OTHER;
        }

        public String describe() {
            return switch (this) {
                case IN_USE -> "already in use";
                case DENIED -> "permission denied";
                case NO_SUCH_ADDRESS -> "not an address of this machine";
                case OTHER -> "could not be opened";
            };
        }
    }

    /** One try: where, and how long to wait first. */
    public record Attempt(String address, int port, long delayMillis) {
    }

    private final String address;
    private final int port;
    private final List<Integer> fallbacks;
    private String current;
    private int sameRetries;
    private int nextFallback;

    /** @param fallbackPorts {@code http.fallback-ports}; empty for the automatic ones */
    public BindPlan(String address, int port, List<Integer> fallbackPorts) {
        this.address = address;
        this.port = port;
        this.current = address;
        this.fallbacks = fallbackPorts.isEmpty() ? automaticFallbacks(port) : List.copyOf(fallbackPorts);
    }

    /** The ports tried after {@code port} when none are configured. */
    public static List<Integer> automaticFallbacks(int port) {
        List<Integer> ports = new ArrayList<>();
        if (port < 1024) {
            for (int fallback : new int[] {8080, 8163, 8164}) {
                if (fallback != port) {
                    ports.add(fallback);
                }
            }
            return ports;
        }
        for (int fallback = port + 1; fallback <= Math.min(65535, port + 3); fallback++) {
            ports.add(fallback);
        }
        return ports;
    }

    public Attempt first() {
        return new Attempt(address, port, 0);
    }

    /** What to try after {@code failed} failed with {@code failure}; null once there is nothing left. */
    public @Nullable Attempt next(Attempt failed, Failure failure) {
        if (failure == Failure.NO_SUCH_ADDRESS && !current.equals(ANY)) {
            current = ANY;
            return new Attempt(ANY, failed.port(), 0);
        }
        if (failed.port() == port && failure != Failure.DENIED && sameRetries < SAME_PORT_RETRIES) {
            sameRetries++;
            return new Attempt(current, port, FIRST_RETRY_MILLIS << (sameRetries - 1));
        }
        if (nextFallback < fallbacks.size()) {
            return new Attempt(current, fallbacks.get(nextFallback++), 0);
        }
        return null;
    }

    /** The configured port again, later: what is tried while everything else has failed. */
    public Attempt retry(long delayMillis) {
        return new Attempt(current, port, delayMillis);
    }

    public int port() {
        return port;
    }

    public List<Integer> fallbacks() {
        return fallbacks;
    }
}
