package com.arkcronist.content.bukkit.minigame;

import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.hud.ActionBars;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.minigame.MinigameDefinition;
import com.arkcronist.content.core.minigame.MinigameRenderer;
import com.arkcronist.content.core.minigame.PrecisionGame;
import com.arkcronist.content.core.storage.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * HUD precision minigames (3.0): a needle sweeping across a bar on the action bar, stopped with a
 * left click.
 *
 * <ul>
 *   <li><b>A rare ore</b> - a custom block with {@code minigame:} - is played with a right click on
 *       it, and can only be mined that way: a hit breaks it with {@code drops}, a critical hit with
 *       {@code critical-drops}, a miss leaves it ({@code on-miss: keep}) or breaks it for nothing.
 *       Breaking it goes through {@link Player#breakBlock}, so protection plugins, tools and every
 *       listener see an ordinary break.</li>
 *   <li><b>A fishing rod</b> with {@code minigame:} is played when it reels something in: the catch
 *       is held while the needle runs; a hit lands it, a critical hit lands it with
 *       {@code critical-drops}, a miss loses it.</li>
 * </ul>
 *
 * <p>Threads: the game runs on the server's clock. Frames are drawn on the main thread each tick
 * from the game's own time - cheap, a string of a dozen characters. A press is timed on the main
 * thread as it arrives, then judged by a {@link CompletableFuture} on {@code ArkContent-Minigames},
 * which hands the verdict back to the main thread to break the block and give the drops. The
 * player's client is never asked whether it hit: it only says when the button went down, and the
 * server, which drew every frame, decides where the needle was then - with the player's measured
 * round trip taken off, up to {@link #MAX_LATENCY_MILLIS}.</p>
 */
public final class MinigameService {

    /** Pings above this are not taken off a press: a slow connection must not look ahead. */
    public static final long MAX_LATENCY_MILLIS = 250;
    /** The right click that starts a game swings the arm too; that is not a press. */
    private static final long START_GRACE_NANOS = 200_000_000L;
    /** How long the stopped needle stays on screen after the verdict. */
    private static final int RESULT_TICKS = 25;

    /** What a game is played for. */
    public sealed interface Stake permits OreStake, CatchStake {
    }

    /** A rare ore: where it is, and which custom block it was when the game started. */
    public record OreStake(Location block, String blockId) implements Stake {
    }

    /** A catch held while the game runs. */
    public record CatchStake(ItemStack caught, int experience) implements Stake {
    }

    private static final class Game {
        final String id;
        final PrecisionGame game;
        final Stake stake;
        final long startNanos;
        volatile boolean judging;
        double frozenAt = -1;
        long hideAtTick;

        Game(String id, PrecisionGame game, Stake stake, long startNanos) {
            this.id = id;
            this.game = game;
            this.stake = stake;
            this.startNanos = startNanos;
        }
    }

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final CustomBlockService blocks;
    private final Protection protection;
    private final DatabaseManager database;
    private final ActionBars actionBars;
    private final Executor mainThread;
    private final ExecutorService judge;
    private final SplittableRandom zones = new SplittableRandom();
    private final Map<UUID, Game> games = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    /** The block being broken by a won game: its break event is let through. */
    private @Nullable Block breaking;
    private volatile @Nullable MinigameRenderer.Glyphs glyphs;
    private volatile Predicate<Player> plainText = player -> false;
    private @Nullable BukkitTask ticker;

    public MinigameService(Plugin plugin, ItemRegistry items, ItemFactory factory, CustomBlockService blocks,
                           Protection protection, DatabaseManager database, ActionBars actionBars, Executor mainThread) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.blocks = blocks;
        this.protection = protection;
        this.database = database;
        this.actionBars = actionBars;
        this.mainThread = mainThread;
        this.judge = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ArkContent-Minigames");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The glyphs of the latest pack; null when it has none, and games are then drawn in plain characters. */
    public void glyphs(@Nullable MinigameRenderer.Glyphs glyphs) {
        this.glyphs = glyphs;
    }

    /** Who sees plain characters instead of the pack's glyphs - Bedrock players. */
    public void plainTextFor(Predicate<Player> plainText) {
        this.plainText = plainText;
    }

    public boolean playing(Player player) {
        return games.containsKey(player.getUniqueId());
    }

    /** The minigame of a custom block, if it has one. */
    public Optional<MinigameDefinition> ore(Block block) {
        return blocks.identify(block).map(custom -> custom.item().definition().minigame());
    }

    /** Whether breaking {@code block} the ordinary way is refused: it is a rare ore, mined only by playing. */
    public boolean guarded(Block block, Player player) {
        return player.getGameMode() != GameMode.CREATIVE && !block.equals(breaking) && ore(block).isPresent();
    }

    /** A right click on a rare ore. Main thread. */
    public boolean startOre(Player player, Block block) {
        Optional<CustomBlock> custom = blocks.identify(block);
        if (custom.isEmpty() || custom.get().item().definition().minigame() == null || playing(player)) {
            return false;
        }
        if (!protection.check(player, block, Interaction.BREAK)) {
            return true;
        }
        CustomItem item = custom.get().item();
        return start(player, item.id(), item.definition().minigame(), new OreStake(block.getLocation(), item.id()));
    }

    /** A fishing rod with a minigame reeled something in. Main thread. */
    public boolean startCatch(Player player, CustomItem rod, ItemStack caught, int experience) {
        if (playing(player)) {
            return false;
        }
        return start(player, rod.id(), rod.definition().minigame(), new CatchStake(caught.clone(), experience));
    }

    private boolean start(Player player, String id, MinigameDefinition definition, Stake stake) {
        long now = plugin.getServer().getCurrentTick();
        Long ready = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(id);
        if (ready != null && ready > now) {
            return stake instanceof OreStake;
        }
        long started = System.nanoTime();
        PrecisionGame game = new PrecisionGame(definition, started, zones.split());
        games.put(player.getUniqueId(), new Game(id, game, stake, started));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.6f, 1.6f);
        draw(player, games.get(player.getUniqueId()), started);
        ensureTicking();
        return true;
    }

    /** A left click while playing: timed now, judged off the main thread. */
    public void press(Player player) {
        Game game = games.get(player.getUniqueId());
        long pressed = System.nanoTime();
        if (game == null || game.judging || game.frozenAt >= 0 || pressed - game.startNanos < START_GRACE_NANOS) {
            return;
        }
        game.judging = true;
        long latency = TimeUnit.MILLISECONDS.toNanos(Math.max(0, player.getPing()));
        long cap = TimeUnit.MILLISECONDS.toNanos(MAX_LATENCY_MILLIS);
        UUID id = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> game.game.judge(pressed, latency, cap), judge)
                .thenAcceptAsync(result -> finish(id, game, result, pressed - Math.min(latency, cap)), mainThread)
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "A minigame could not be judged", error);
                    return null;
                });
    }

    /** Each tick while anyone plays: frames, and games that ran out. */
    private void tick() {
        if (games.isEmpty()) {
            if (ticker != null) {
                ticker.cancel();
                ticker = null;
            }
            return;
        }
        long now = System.nanoTime();
        long tick = plugin.getServer().getCurrentTick();
        for (Map.Entry<UUID, Game> entry : new ArrayList<>(games.entrySet())) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Game game = entry.getValue();
            if (player == null) {
                games.remove(entry.getKey());
                continue;
            }
            if (game.frozenAt >= 0) {
                if (tick >= game.hideAtTick) {
                    games.remove(entry.getKey());
                    actionBars.overlay(player, null);
                }
                continue;
            }
            if (game.judging) {
                continue;
            }
            if (game.game.over(now)) {
                game.judging = true;
                finish(entry.getKey(), game, PrecisionGame.timeout(), now);
                continue;
            }
            draw(player, game, now);
        }
    }

    private void draw(Player player, Game game, long nanos) {
        double needle = game.frozenAt >= 0 ? game.frozenAt : game.game.needleAt(nanos);
        MinigameRenderer.Glyphs current = glyphs;
        Component frame = current == null || plainText.test(player)
                ? LegacyComponentSerializer.legacySection().deserialize(MinigameRenderer.plain(game.game, needle))
                : Component.text(MinigameRenderer.frame(current, game.game, needle))
                .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
        actionBars.overlay(player, frame);
    }

    /** The verdict, back on the main thread: the bar stops where it was judged, and the stake is settled. */
    private void finish(UUID playerId, Game game, PrecisionGame.Result result, long judgedAt) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (games.get(playerId) != game) {
            return;
        }
        MinigameDefinition definition = game.game.definition();
        cooldowns.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .put(game.id, plugin.getServer().getCurrentTick() + (long) definition.cooldownTicks());
        database.recordMinigame(playerId, game.id, result.outcome().hit(),
                result.outcome() == PrecisionGame.Outcome.CRITICAL, result.error());
        if (player == null) {
            games.remove(playerId);
            return;
        }
        game.frozenAt = game.game.needleAt(judgedAt);
        game.hideAtTick = plugin.getServer().getCurrentTick() + RESULT_TICKS;
        draw(player, game, judgedAt);
        settle(player, game, result.outcome());
    }

    private void settle(Player player, Game game, PrecisionGame.Outcome outcome) {
        MinigameDefinition definition = game.game.definition();
        List<MinigameDefinition.Drop> drops = outcome == PrecisionGame.Outcome.CRITICAL
                ? (definition.criticalDrops().isEmpty() ? definition.drops() : definition.criticalDrops())
                : outcome == PrecisionGame.Outcome.HIT ? definition.drops() : List.of();
        Component verdict = switch (outcome) {
            case CRITICAL -> Component.text("Critical hit!", NamedTextColor.GREEN, TextDecoration.BOLD);
            case HIT -> Component.text("Hit", NamedTextColor.GOLD);
            case MISS -> Component.text("Missed", NamedTextColor.RED);
            case TIMEOUT -> Component.text("Too slow", NamedTextColor.GRAY);
        };
        actionBars.flash(player, verdict, RESULT_TICKS);
        player.playSound(player.getLocation(), switch (outcome) {
            case CRITICAL -> Sound.ENTITY_PLAYER_LEVELUP;
            case HIT -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            default -> Sound.BLOCK_NOTE_BLOCK_BASS;
        }, SoundCategory.PLAYERS, 0.7f, outcome == PrecisionGame.Outcome.CRITICAL ? 1.4f : 1f);

        if (game.stake instanceof OreStake ore) {
            Block block = ore.block().getBlock();
            Optional<CustomBlock> still = blocks.identify(block);
            if (still.isEmpty() || !still.get().id().equals(ore.blockId())) {
                return;
            }
            boolean breakIt = outcome.hit() || definition.onMiss() == MinigameDefinition.OnMiss.BREAK;
            if (!breakIt) {
                return;
            }
            breaking = block;
            boolean broken;
            try {
                broken = player.breakBlock(block);
            } finally {
                breaking = null;
            }
            if (broken) {
                Location centre = block.getLocation().add(0.5, 0.5, 0.5);
                for (ItemStack stack : roll(drops)) {
                    block.getWorld().dropItemNaturally(centre, stack);
                }
            }
        } else if (game.stake instanceof CatchStake caught) {
            if (!outcome.hit()) {
                return;
            }
            List<ItemStack> landed = new ArrayList<>();
            landed.add(caught.caught());
            if (outcome == PrecisionGame.Outcome.CRITICAL) {
                landed.addAll(roll(definition.criticalDrops()));
            } else {
                landed.addAll(roll(definition.drops()));
            }
            give(player, landed);
            if (caught.experience() > 0) {
                player.giveExp(caught.experience());
            }
        }
    }

    /** Each drop that passes its chance, as stacks: a custom item by id, or a material. Unknown ones are skipped. */
    private List<ItemStack> roll(List<MinigameDefinition.Drop> drops) {
        List<ItemStack> stacks = new ArrayList<>();
        for (MinigameDefinition.Drop drop : drops) {
            if (drop.chance() < 1 && ThreadLocalRandom.current().nextDouble() >= drop.chance()) {
                continue;
            }
            Optional<CustomItem> custom = items.get(drop.item());
            if (custom.isPresent()) {
                stacks.add(factory.create(custom.get(), drop.amount()));
                continue;
            }
            Material material = Material.matchMaterial(drop.item());
            if (material != null && material.isItem() && !material.isAir()) {
                stacks.add(new ItemStack(material, drop.amount()));
            } else {
                plugin.getLogger().warning("Minigame drop '" + drop.item() + "' is neither a custom item nor a material.");
            }
        }
        return stacks;
    }

    private static void give(Player player, List<ItemStack> stacks) {
        for (ItemStack left : player.getInventory().addItem(stacks.toArray(ItemStack[]::new)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /** A player left or died: their game ends, a held catch with it. */
    public void cancel(Player player) {
        if (games.remove(player.getUniqueId()) != null) {
            actionBars.overlay(player, null);
        }
    }

    public void forget(UUID player) {
        games.remove(player);
        cooldowns.remove(player);
    }

    private void ensureTicking() {
        if (ticker == null) {
            ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        games.clear();
        judge.shutdownNow();
    }
}
