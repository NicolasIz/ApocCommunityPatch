package com.arkcronist.content.bukkit.pack;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.core.http.PackHttpServer;
import com.arkcronist.content.core.net.BindPlan;
import com.arkcronist.content.core.net.PublicAddress;
import com.arkcronist.content.core.net.ServedAddress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * The built-in web server's side of the network: opening its port, whatever the port does, and
 * telling players an address they can reach.
 *
 * <ul>
 *   <li><b>Port.</b> Opened on the worker. When it cannot be - in use, refused - {@link BindPlan}
 *       says what to try next: the same port a little later, then fallback ports, then the same
 *       port again every {@value #RETRY_SECONDS} seconds. The plugin starts either way; admins are
 *       told in the console and when they join, until the server is up on its own port.</li>
 *   <li><b>Address.</b> With {@code http.public-address} empty, this machine's public IP is asked
 *       of a few services with Java's {@link HttpClient}, asynchronously, and used for the pack
 *       link - unless {@code server-ip} already names a public address. Nothing is written to
 *       config.yml: an empty line keeps finding the address on every start, so a host that changes
 *       it is followed.</li>
 * </ul>
 *
 * <p>When either changes after the pack has gone live, the live pack is published again with the
 * new link and sent to everyone online.</p>
 */
public final class PackWebHost implements Listener {

    static final long RETRY_SECONDS = 60;
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(4);

    private final ArkContentPlugin plugin;
    private final EngineSettings settings;
    private final PackDelivery delivery;
    private final Logger logger;
    private final ServedAddress address;
    private final BindPlan plan;
    /** Every failed try so far, for the contingency message. Worker only. */
    private final List<String> failures = new ArrayList<>();
    private volatile @Nullable PackHttpServer server;
    /** What admins are told while the port is not the one configured; null when it is. */
    private volatile @Nullable String portProblem;
    /** What admins are told while the pack link points at this machine only; null otherwise. */
    private volatile @Nullable String addressProblem;
    /** The contingency is in force: every port failed and the configured one is retried. */
    private volatile boolean retrying;
    private volatile boolean stopped;
    private volatile @Nullable HttpClient client;

    public PackWebHost(ArkContentPlugin plugin, EngineSettings settings, PackDelivery delivery, String serverIp) {
        this.plugin = plugin;
        this.settings = settings;
        this.delivery = delivery;
        this.logger = plugin.getLogger();
        EngineSettings.Http http = settings.http();
        ServedAddress.Origin origin = http.addressConfigured() ? ServedAddress.Origin.CONFIG
                : http.publicAddress().equals("127.0.0.1") && !"127.0.0.1".equals(serverIp)
                ? ServedAddress.Origin.LOOPBACK : ServedAddress.Origin.SERVER_IP;
        this.address = new ServedAddress(http.publicAddress(), http.port(), origin);
        this.plan = new BindPlan(http.bindAddress(), http.port(), http.fallbackPorts());
    }

    /** Where players download from; read by every rebuild. */
    public ServedAddress address() {
        return address;
    }

    /** Opens the port (on the worker) and, when the address is not set, looks it up. Main thread. */
    public void start() {
        EngineSettings.Http http = settings.http();
        if (!http.enabled()) {
            return;
        }
        attempt(plan.first());
        boolean wanted = !http.addressConfigured() && http.detectAddress()
                && settings.hosting() != EngineSettings.Hosting.EXTERNAL;
        if (wanted) {
            HttpClient lookups = PublicAddress.client(LOOKUP_TIMEOUT);
            this.client = lookups;
            PublicAddress.discover(lookups, http.addressServices(), LOOKUP_TIMEOUT)
                    .thenAcceptAsync(this::discovered, plugin.pipeline().mainThread())
                    .exceptionally(error -> {
                        logger.log(java.util.logging.Level.WARNING, "Could not use the public address found", error);
                        return null;
                    });
        }
    }

    public void stop() {
        stopped = true;
        PackHttpServer running = server;
        if (running != null) {
            running.stop();
        }
        HttpClient lookups = client;
        if (lookups != null) {
            lookups.shutdownNow();
        }
    }

    public boolean running() {
        PackHttpServer running = server;
        return running != null && running.isRunning();
    }

    /** For {@code /arkcontent info}. */
    public String status() {
        if (!settings.http().enabled()) {
            return "off (http.enabled: false)";
        }
        PackHttpServer running = server;
        String origin = switch (address.origin()) {
            case CONFIG -> "http.public-address";
            case SERVER_IP -> "server-ip";
            case DISCOVERED -> "found via " + address.via();
            case LOOPBACK -> "nothing better known - only this machine can reach it";
        };
        String where = "players download from " + address.host() + ":" + address.port() + " (" + origin + ")";
        if (running != null && running.isRunning()) {
            return "running on port " + running.port() + (running.port() != plan.port() ? " (fallback for "
                    + plan.port() + ")" : "") + "; " + where;
        }
        return (retrying ? "down, port " + plan.port() + " retried every " + RETRY_SECONDS + " s" : "starting")
                + "; " + where;
    }

    /** What admins should know about the web server, or null when all is as configured. */
    public @Nullable String problem() {
        String port = portProblem;
        String link = addressProblem;
        return port == null ? link : link == null ? port : port + " " + link;
    }

    // ---------------------------------------------------------------- port

    private void attempt(BindPlan.Attempt attempt) {
        if (stopped) {
            return;
        }
        Executor worker = plugin.pipeline().worker();
        Executor run = attempt.delayMillis() > 0
                ? CompletableFuture.delayedExecutor(attempt.delayMillis(), TimeUnit.MILLISECONDS, worker)
                : worker;
        try {
            run.execute(() -> bind(attempt));
        } catch (RejectedExecutionException shuttingDown) {
            // The worker has stopped: so has the plugin.
        }
    }

    /** One try, on the worker. */
    private void bind(BindPlan.Attempt attempt) {
        if (stopped) {
            return;
        }
        EngineSettings.Http http = settings.http();
        try {
            PackHttpServer opened = new PackHttpServer(new InetSocketAddress(attempt.address(), attempt.port()),
                    http.threads(), delivery::current, logger);
            opened.start();
            if (stopped) {
                opened.stop();
                return;
            }
            this.server = opened;
            plugin.pipeline().mainThread().execute(() -> bound(attempt, opened));
        } catch (Exception exception) {
            BindPlan.Failure failure = BindPlan.Failure.of(exception);
            if (!retrying) {
                failures.add(attempt.address() + ":" + attempt.port() + " " + failure.describe());
            }
            BindPlan.Attempt next = plan.next(attempt, failure);
            if (next == null) {
                if (!retrying) {
                    retrying = true;
                    String message = "The resource pack web server could not open a port (" + String.join(", ", failures)
                            + "). The plugin runs on, but players are not sent the pack; port " + plan.port()
                            + " is tried again every " + RETRY_SECONDS + " s. Free it, or set http.port"
                            + " (and http.fallback-ports) to ports your host forwards.";
                    portProblem = message;
                    logger.warning(message);
                    plugin.pipeline().mainThread().execute(() -> tellAdmins(message));
                }
                next = plan.retry(TimeUnit.SECONDS.toMillis(RETRY_SECONDS));
            }
            attempt(next);
        }
    }

    /** Main thread: the server is listening. */
    private void bound(BindPlan.Attempt attempt, PackHttpServer opened) {
        EngineSettings.Http http = settings.http();
        boolean otherPort = opened.port() != plan.port();
        boolean otherAddress = !attempt.address().equals(http.bindAddress());
        if (otherPort || otherAddress) {
            String message = (otherPort ? "Port " + plan.port() + " could not be opened (" + String.join(", ", failures)
                    + "); the resource pack is served on port " + opened.port() + " instead. Players can download it"
                    + " only if that port is open to them too - free " + plan.port() + ", or set http.port to a"
                    + " port your host forwards." : "")
                    + (otherAddress ? (otherPort ? " " : "") + "http.bind-address " + http.bindAddress()
                    + " is not an address of this machine; listening on every interface (0.0.0.0) instead." : "");
            portProblem = message;
            logger.warning(message);
            tellAdmins(message);
        } else {
            if (retrying) {
                tellAdmins("The resource pack web server is up on port " + opened.port() + " now.");
            }
            portProblem = null;
        }
        boolean wasDown = retrying;
        retrying = false;
        logger.info("Resource pack web server listening on " + attempt.address() + ":" + opened.port()
                + "; players download from " + address.host() + ":" + opened.port() + ".");
        republishIf(() -> address.setPort(opened.port()));
        if (wasDown && address.serves(delivery.currentUrl())) {
            // Whoever was sent the link while nothing listened failed to download; now it works.
            delivery.sendAll(plugin.getServer().getOnlinePlayers());
        }
    }

    // ---------------------------------------------------------------- address

    /** Main thread: what the public-IP services said. */
    private void discovered(Optional<PublicAddress.Found> found) {
        HttpClient lookups = client;
        if (lookups != null) {
            lookups.shutdownNow();
        }
        if (stopped) {
            return;
        }
        if (found.isEmpty()) {
            boolean loopback = address.origin() == ServedAddress.Origin.LOOPBACK;
            String message = "Could not find this server's public IP address (no answer from "
                    + String.join(", ", settings.http().addressServices()) + "). Players are sent " + address.host()
                    + (loopback ? ", which only a client on this machine can reach" : "")
                    + ". Set http.public-address in config.yml to your server's public IP or domain.";
            logger.warning(message);
            if (loopback) {
                addressProblem = message;
                tellAdmins(message);
            }
            return;
        }
        PublicAddress.Found answer = found.get();
        if (address.origin() == ServedAddress.Origin.SERVER_IP && PublicAddress.isPublic(address.host())) {
            if (!address.host().equals(answer.address())) {
                logger.info("The pack link uses server-ip " + address.host() + "; this machine reaches the internet"
                        + " as " + answer.address() + ". Set http.public-address if players should use that one.");
            }
            return;
        }
        republishIf(() -> address.setHost(answer.address(), ServedAddress.Origin.DISCOVERED, answer.service()));
        addressProblem = null;
        logger.info("http.public-address is empty: players download the pack from " + answer.address()
                + ", this server's public IP (found via " + answer.service() + "). Set http.public-address"
                + " to pin it - to a domain, say.");
    }

    /**
     * Applies a change to the address and, when the live pack is served from it, publishes the pack
     * again with the new link and sends it to everyone online. Main thread.
     */
    private void republishIf(BooleanSupplier change) {
        PackDelivery.Live live = delivery.live();
        boolean ours = live != null && address.serves(live.url());
        if (!change.getAsBoolean() || !ours) {
            return;
        }
        String url = address.packUrl(live.artifact().sha1Hex());
        if (delivery.publish(live.artifact(), url)) {
            delivery.sendAll(plugin.getServer().getOnlinePlayers());
            logger.info("The pack link changed; players are sent " + url + " now.");
        }
    }

    // ---------------------------------------------------------------- admins

    private void tellAdmins(String message) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.hasPermission("arkcontent.admin")) {
                player.sendMessage(Component.text("[ArkcronistContent] " + message, NamedTextColor.YELLOW));
            }
        }
    }

    /** An admin joining while the web server is not as configured hears so, a moment after joining. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        String message = problem();
        Player player = event.getPlayer();
        if (message != null && player.hasPermission("arkcontent.admin")) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(Component.text("[ArkcronistContent] " + message, NamedTextColor.YELLOW));
                }
            }, 40);
        }
    }
}
