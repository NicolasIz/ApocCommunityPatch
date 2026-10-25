package com.arkcronist.content.core.net;

import com.arkcronist.content.core.http.PackHttpServer;
import org.jetbrains.annotations.Nullable;

/**
 * Where players download the pack from the built-in web server: a host and a port that can change
 * while the server runs - the public address once it is found, a fallback port once one is bound.
 * Read by the rebuild on the worker, changed on the server thread; each read sees one whole value.
 */
public final class ServedAddress {

    /** Where the host came from, for {@code /arkcontent info}. */
    public enum Origin {
        /** {@code http.public-address}. */
        CONFIG,
        /** {@code server-ip} in server.properties. */
        SERVER_IP,
        /** Found by asking a public-IP service. */
        DISCOVERED,
        /** Nothing better known: only a client on this machine can reach it. */
        LOOPBACK
    }

    private record Value(String host, int port, Origin origin, @Nullable String via) {
    }

    private volatile Value value;

    public ServedAddress(String host, int port, Origin origin) {
        this.value = new Value(host, port, origin, null);
    }

    public String host() {
        return value.host();
    }

    public int port() {
        return value.port();
    }

    public Origin origin() {
        return value.origin();
    }

    /** The service a discovered host came from; null otherwise. */
    public @Nullable String via() {
        return value.via();
    }

    /** @return whether anything changed */
    public synchronized boolean setHost(String host, Origin origin, @Nullable String via) {
        Value now = value;
        if (now.host().equals(host) && now.origin() == origin) {
            return false;
        }
        value = new Value(host, now.port(), origin, via);
        return true;
    }

    /** @return whether anything changed */
    public synchronized boolean setPort(int port) {
        Value now = value;
        if (now.port() == port) {
            return false;
        }
        value = new Value(now.host(), port, now.origin(), now.via());
        return true;
    }

    /** The pack's URL for one build: {@code http://host:port/resource_pack.zip?sha1=...}. */
    public String packUrl(String sha1Hex) {
        return base() + "?sha1=" + sha1Hex;
    }

    /** Whether {@code url} is one this address hands out - as opposed to an upload's or an external link. */
    public boolean serves(@Nullable String url) {
        return url != null && url.startsWith(base() + "?");
    }

    private String base() {
        Value now = value;
        String host = now.host().contains(":") && !now.host().startsWith("[") ? "[" + now.host() + "]" : now.host();
        return "http://" + host + ":" + now.port() + PackHttpServer.PATH;
    }
}
