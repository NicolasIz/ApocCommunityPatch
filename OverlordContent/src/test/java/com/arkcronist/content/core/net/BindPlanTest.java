package com.arkcronist.content.core.net;

import com.arkcronist.content.core.http.PackHttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The web server's contingency: what is tried when its port will not open. */
class BindPlanTest {

    @Test
    void aPortInUseIsRetriedThenTheFallbacksThenNothing() {
        BindPlan plan = new BindPlan("0.0.0.0", 8163, List.of());
        BindPlan.Attempt first = plan.first();
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8163, 0), first);
        BindPlan.Attempt again = plan.next(first, BindPlan.Failure.IN_USE);
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8163, 2_000), again, "the last run may still be letting go");
        BindPlan.Attempt third = plan.next(again, BindPlan.Failure.IN_USE);
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8163, 4_000), third);
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8164, 0), plan.next(third, BindPlan.Failure.IN_USE));
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8165, 0), plan.next(new BindPlan.Attempt("0.0.0.0", 8164, 0),
                BindPlan.Failure.IN_USE));
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8166, 0), plan.next(new BindPlan.Attempt("0.0.0.0", 8165, 0),
                BindPlan.Failure.DENIED));
        assertNull(plan.next(new BindPlan.Attempt("0.0.0.0", 8166, 0), BindPlan.Failure.IN_USE));
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8163, 60_000), plan.retry(60_000), "then the port, now and then");
    }

    @Test
    void aRefusedPortGoesStraightToTheFallbacks() {
        BindPlan plan = new BindPlan("0.0.0.0", 80, List.of());
        assertEquals(List.of(8080, 8163, 8164), plan.fallbacks(), "a privileged port falls back to usual ones");
        assertEquals(new BindPlan.Attempt("0.0.0.0", 8080, 0), plan.next(plan.first(), BindPlan.Failure.DENIED));

        BindPlan configured = new BindPlan("0.0.0.0", 25566, List.of(30120, 30121));
        assertEquals(new BindPlan.Attempt("0.0.0.0", 30120, 0), configured.next(configured.first(),
                BindPlan.Failure.DENIED), "http.fallback-ports, when set, replace the automatic ones");
        assertEquals(List.of(65534, 65535), BindPlan.automaticFallbacks(65533));
    }

    @Test
    void anAddressThisMachineDoesNotHaveBecomesEveryInterface() {
        BindPlan plan = new BindPlan("203.0.113.50", 8163, List.of());
        BindPlan.Attempt any = plan.next(plan.first(), BindPlan.Failure.NO_SUCH_ADDRESS);
        assertEquals(new BindPlan.Attempt(BindPlan.ANY, 8163, 0), any);
        assertEquals(new BindPlan.Attempt(BindPlan.ANY, 8163, 2_000), plan.next(any, BindPlan.Failure.IN_USE),
                "the address is changed once, then the port's own steps follow");
        assertEquals(new BindPlan.Attempt(BindPlan.ANY, 8163, 60_000), plan.retry(60_000));
    }

    @Test
    void failuresAreToldApartFromWhatTheJdkThrows() throws IOException {
        try (ServerSocket taken = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            PackHttpServer clash = new PackHttpServer(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                    taken.getLocalPort()), 1, () -> null, Logger.getAnonymousLogger());
            IOException error = assertThrows(IOException.class, clash::start);
            assertEquals(BindPlan.Failure.IN_USE, BindPlan.Failure.of(error), String.valueOf(error));
        }
        PackHttpServer nowhere = new PackHttpServer(InetSocketAddress.createUnresolved("no-such-host.invalid", 8163),
                1, () -> null, Logger.getAnonymousLogger());
        Exception unresolved = assertThrows(Exception.class, nowhere::start);
        assertEquals(BindPlan.Failure.NO_SUCH_ADDRESS, BindPlan.Failure.of(unresolved), String.valueOf(unresolved));

        assertEquals(BindPlan.Failure.DENIED, BindPlan.Failure.of(new SocketException("Permission denied")));
        assertEquals(BindPlan.Failure.DENIED, BindPlan.Failure.of(new BindException(
                "An attempt was made to access a socket in a way forbidden by its access permissions")));
        assertEquals(BindPlan.Failure.IN_USE, BindPlan.Failure.of(new BindException("Address already in use: bind")));
        assertEquals(BindPlan.Failure.NO_SUCH_ADDRESS, BindPlan.Failure.of(new BindException(
                "Cannot assign requested address")));
        assertEquals(BindPlan.Failure.NO_SUCH_ADDRESS, BindPlan.Failure.of(new IOException("wrapped",
                new UnknownHostException("nope"))));
        assertEquals(BindPlan.Failure.NO_SUCH_ADDRESS, BindPlan.Failure.of(new UnresolvedAddressException()));
        assertEquals(BindPlan.Failure.OTHER, BindPlan.Failure.of(new IllegalStateException("odd")));
        assertTrue(BindPlan.Failure.DENIED.describe().contains("permission"));
    }

    @Test
    void theServedAddressMakesTheLinkAndKnowsItsOwn() {
        ServedAddress address = new ServedAddress("127.0.0.1", 8163, ServedAddress.Origin.LOOPBACK);
        String before = address.packUrl("abc");
        assertEquals("http://127.0.0.1:8163/resource_pack.zip?sha1=abc", before);
        assertTrue(address.serves(before));
        assertTrue(address.setHost("93.184.216.34", ServedAddress.Origin.DISCOVERED, "https://api.ipify.org"));
        assertTrue(address.setPort(8164));
        assertEquals("http://93.184.216.34:8164/resource_pack.zip?sha1=abc", address.packUrl("abc"));
        assertTrue(!address.serves(before), "the old link is not this address's any more");
        assertTrue(!address.serves("https://cdn.example.com/pack.zip?sha1=abc"));
        assertTrue(!address.setPort(8164), "no change, nothing to republish");
        address.setHost("2606:4700::1111", ServedAddress.Origin.CONFIG, null);
        assertEquals("http://[2606:4700::1111]:8164/resource_pack.zip?sha1=abc", address.packUrl("abc"));
        assertEquals("https://api.ipify.org", new ServedAddress("1.1.1.1", 1, ServedAddress.Origin.CONFIG)
                .setHost("8.8.8.8", ServedAddress.Origin.DISCOVERED, "https://api.ipify.org") ? "https://api.ipify.org" : "");
    }
}
