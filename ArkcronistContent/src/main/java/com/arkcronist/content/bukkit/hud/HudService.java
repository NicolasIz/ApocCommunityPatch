package com.arkcronist.content.bukkit.hud;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.core.hud.HudDefinition;
import com.arkcronist.content.core.hud.HudLayout;
import com.arkcronist.content.core.hud.Spaces;
import com.arkcronist.content.core.storage.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HUD bars: their values, and the text that draws them.
 *
 * <p>A bar fed by a placeholder - another plugin's mana - is read through PlaceholderAPI each time
 * it is drawn. A bar this plugin keeps - thirst - lives in memory per player, is read from SQLite
 * ({@code hud_values}) as they join, changes each second by its {@code per-second}, and is written
 * back every {@code huds.save-seconds} and as they leave.</p>
 *
 * <p>Drawn two ways. Through the placeholders - {@code %arkcontent_hud_thirst%} - into anything
 * that takes PlaceholderAPI text: TAB's scoreboard, header, footer and bossbar, DeluxeMenus' titles
 * and lore, holograms. They are answered from values kept for them, so TAB asking from its own
 * thread every tick of its refresh costs nothing. And straight onto the action bar, for HUDs with
 * {@code action-bar: true}, shared with short messages such as a gun's ammunition.</p>
 */
public final class HudService {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    private final Plugin plugin;
    private final HudRegistry registry;
    private final DatabaseManager database;
    private final ActionBars actionBars;
    private final EngineSettings.Huds settings;
    private final Logger logger;
    /** Resolves another plugin's placeholders; the text unchanged without PlaceholderAPI. */
    private final BiFunction<OfflinePlayer, String, String> placeholders;
    /** Stored values, per player and HUD id. Written on the main thread, read from any. */
    private final Map<UUID, Map<String, Double>> values = new ConcurrentHashMap<>();
    /** 2.0: the stats bars read (health, absorption, AuraSkills mana...), per player: read on the main thread, for any. */
    private final Map<UUID, Map<String, double[]>> stats = new ConcurrentHashMap<>();
    /** AuraSkills' mana and max mana of a player; null without AuraSkills. Main thread. */
    private final java.util.function.Function<Player, double[]> mana;
    private final Set<UUID> changed = ConcurrentHashMap.newKeySet();
    /** 3.1: HUDs BetterHud draws, by full id: not on the action bar while it runs. */
    private volatile Set<String> elsewhere = Set.of();
    private volatile java.util.function.BooleanSupplier drawing = () -> false;
    private BukkitTask secondly;
    private BukkitTask bars;
    private long seconds;

    public HudService(Plugin plugin, HudRegistry registry, DatabaseManager database, ActionBars actionBars,
                      EngineSettings.Huds settings, BiFunction<OfflinePlayer, String, String> placeholders) {
        this(plugin, registry, database, actionBars, settings, placeholders, player -> null);
    }

    public HudService(Plugin plugin, HudRegistry registry, DatabaseManager database, ActionBars actionBars,
                      EngineSettings.Huds settings, BiFunction<OfflinePlayer, String, String> placeholders,
                      java.util.function.Function<Player, double[]> mana) {
        this.mana = mana;
        this.plugin = plugin;
        this.registry = registry;
        this.database = database;
        this.actionBars = actionBars;
        this.settings = settings;
        this.logger = plugin.getLogger();
        this.placeholders = placeholders;
        actionBars.bars(this::actionBar);
    }

    public void start() {
        secondly = plugin.getServer().getScheduler().runTaskTimer(plugin, this::second, 20, 20);
        bars = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sendBars, settings.actionBarTicks(),
                settings.actionBarTicks());
        plugin.getServer().getOnlinePlayers().forEach(this::load);
    }

    public void shutdown() {
        if (secondly != null) {
            secondly.cancel();
        }
        if (bars != null) {
            bars.cancel();
        }
        // Queued ahead of the database closing.
        for (UUID player : Set.copyOf(values.keySet())) {
            save(player);
        }
    }

    // ------------------------------------------------------------------ values

    /** Reads a player's stored values. Until they arrive, every bar shows its start value. */
    public void load(Player player) {
        UUID id = player.getUniqueId();
        database.loadHudValues(id).whenComplete((stored, error) -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                logger.warning("Could not read the HUD values of " + player.getName() + ": " + error.getMessage());
                return;
            }
            Map<String, Double> mine = values.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>());
            stored.forEach(mine::putIfAbsent);
        });
    }

    /** Writes a player's values, if they changed, and forgets them once they have left. */
    public void save(UUID player) {
        Map<String, Double> mine = values.get(player);
        if (mine != null && changed.remove(player) && !mine.isEmpty()) {
            database.saveHudValues(player, new HashMap<>(mine)).exceptionally(error -> {
                logger.warning("Could not save HUD values for " + player + ": " + error.getMessage());
                return null;
            });
        }
        if (plugin.getServer().getPlayer(player) == null) {
            values.remove(player);
        }
    }

    /** The value of a stored HUD for a player; its start value when it has none yet. */
    public double stored(UUID player, HudLayout hud) {
        HudDefinition.Source.Stored source = (HudDefinition.Source.Stored) hud.definition().source();
        Map<String, Double> mine = values.get(player);
        Double value = mine == null ? null : mine.get(hud.definition().fullId());
        return value != null ? value : source.start();
    }

    /**
     * Sets a stored HUD's value, kept within 0 and its max. Main thread.
     *
     * @return the value it now has; NaN for a HUD fed by a placeholder, which cannot be set
     */
    public double set(UUID player, HudLayout hud, double value) {
        if (!(hud.definition().source() instanceof HudDefinition.Source.Stored source)) {
            return Double.NaN;
        }
        double clamped = source.clamp(value);
        values.computeIfAbsent(player, ignored -> new ConcurrentHashMap<>()).put(hud.definition().fullId(), clamped);
        changed.add(player);
        return clamped;
    }

    public double add(UUID player, HudLayout hud, double amount) {
        return set(player, hud, stored(player, hud) + amount);
    }

    /** What eating or drinking {@code item} - a material, or a custom item's id - adds to each HUD. */
    public void consumed(Player player, String material, @Nullable String customId) {
        for (HudLayout hud : registry.all()) {
            if (hud.definition().source() instanceof HudDefinition.Source.Stored source) {
                Double amount = customId != null ? source.consume().get(customId) : null;
                if (amount == null) {
                    amount = source.consume().get(material);
                }
                if (amount != null) {
                    add(player.getUniqueId(), hud, amount);
                }
            }
        }
    }

    /** Each second: stored values change by themselves, and an empty one may hurt; stats are read again. */
    private void second() {
        seconds++;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // For the placeholders other plugins ask from their own threads.
            for (HudLayout hud : registry.all()) {
                if (hud.definition().source() instanceof HudDefinition.Source.Stat stat) {
                    stat(player, stat);
                }
            }
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            for (HudLayout hud : registry.all()) {
                if (!(hud.definition().source() instanceof HudDefinition.Source.Stored source)) {
                    continue;
                }
                double value = source.perSecond() == 0 ? stored(player.getUniqueId(), hud)
                        : add(player.getUniqueId(), hud, source.perSecond());
                if (value <= 0 && source.emptyDamage() > 0 && !player.isDead()) {
                    player.damage(source.emptyDamage(), DamageSource.builder(DamageType.STARVE).build());
                }
            }
        }
        if (seconds % settings.saveSeconds() == 0) {
            for (UUID player : Set.copyOf(changed)) {
                save(player);
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    /** The value and max of a HUD for a player: stored, read from the player, or through placeholders. Any thread. */
    public double[] valueAndMax(OfflinePlayer player, HudLayout hud) {
        HudDefinition.Source source = hud.definition().source();
        if (source instanceof HudDefinition.Source.Stored stored) {
            return new double[]{stored(player.getUniqueId(), hud), stored.max()};
        }
        if (source instanceof HudDefinition.Source.Stat stat) {
            return stat(player, stat);
        }
        if (!(source instanceof HudDefinition.Source.Placeholder placeholder)) {
            return new double[]{0, 1};
        }
        return new double[]{number(placeholders.apply(player, placeholder.value())),
                number(placeholders.apply(player, placeholder.max()))};
    }

    /**
     * A stat bar's value and max: read from the player now on the main thread - and kept, for the
     * placeholders TAB asks from its own thread - or the last value kept, from any other.
     */
    private double[] stat(OfflinePlayer offline, HudDefinition.Source.Stat stat) {
        Player online = offline.getPlayer();
        double[] read = null;
        if (online != null && plugin.getServer().isPrimaryThread()) {
            read = readStat(online, stat.stat());
            if (read != null) {
                stats.computeIfAbsent(online.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(stat.stat(), read);
            }
        } else {
            Map<String, double[]> kept = stats.get(offline.getUniqueId());
            read = kept == null ? null : kept.get(stat.stat());
        }
        double value = read == null ? 0 : read[0];
        double max = stat.max() > 0 ? stat.max() : read == null ? 1 : read[1];
        return new double[]{value, max};
    }

    /** One stat of an online player, and its natural most. Main thread. */
    private @Nullable double[] readStat(Player player, String stat) {
        return switch (stat) {
            case "health" -> new double[]{player.getHealth(), attribute(player, org.bukkit.attribute.Attribute.MAX_HEALTH, 20)};
            case "absorption" -> new double[]{player.getAbsorptionAmount(), 20};
            case "armor" -> new double[]{attribute(player, org.bukkit.attribute.Attribute.ARMOR, 0), 20};
            case "toughness" -> new double[]{attribute(player, org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS, 0), 12};
            case "food" -> new double[]{player.getFoodLevel(), 20};
            case "saturation" -> new double[]{player.getSaturation(), 20};
            case "air" -> new double[]{Math.max(0, player.getRemainingAir()), Math.max(1, player.getMaximumAir())};
            case "level" -> new double[]{player.getLevel(), 100};
            case "experience" -> new double[]{player.getExp(), 1};
            case "auraskills:mana" -> mana.apply(player);
            default -> null;
        };
    }

    private static double attribute(Player player, org.bukkit.attribute.Attribute attribute, double fallback) {
        org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    /** The first number in a placeholder's text - "1,250" and "75.5 / 100" included; 0 for none. */
    static double number(@Nullable String text) {
        if (text == null) {
            return 0;
        }
        Matcher matcher = NUMBER.matcher(text.replaceAll("(?<=\\d),(?=\\d{3}(\\D|$))", ""));
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Double.parseDouble(matcher.group().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /** A bar's text for a player. Any thread. */
    public String render(OfflinePlayer player, HudLayout hud) {
        double[] value = valueAndMax(player, hud);
        return hud.render(value[0], value[1]);
    }

    /**
     * 3.1: the HUDs another plugin draws from now on, by full id - BetterHud's - left off the action
     * bar while {@code drawing} says that plugin runs: one that failed to start leaves them here.
     */
    public void drawnElsewhere(Set<String> fullIds, java.util.function.BooleanSupplier drawing) {
        this.elsewhere = Set.copyOf(fullIds);
        this.drawing = drawing;
    }

    /** Whether this plugin draws a HUD on the action bar itself. */
    private boolean onActionBar(HudLayout hud) {
        return hud.definition().actionBar()
                && !(elsewhere.contains(hud.definition().fullId()) && drawing.getAsBoolean());
    }

    /** Every action-bar HUD of a player's, in one component; null when there is none. */
    private @Nullable Component actionBar(Player player) {
        StringBuilder text = new StringBuilder();
        for (HudLayout hud : registry.all()) {
            if (onActionBar(hud)) {
                text.append(render(player, hud));
            }
        }
        // White leaves the icons their own colours; no shadow, or each would be drawn twice.
        return text.isEmpty() ? null
                : Component.text(text.toString(), NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    private void sendBars() {
        if (registry.all().stream().noneMatch(this::onActionBar)) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            actionBars.send(player);
        }
    }

    /**
     * {@code hud_<id>}, {@code hud_<id>_value}, {@code hud_<id>_max}, {@code hud_<id>_percent} (3.1.1,
     * 0-100) and {@code space_<pixels>}.
     * Any thread.
     */
    public @Nullable String placeholder(@Nullable OfflinePlayer player, String params) {
        if (params.startsWith("space_")) {
            try {
                return Spaces.of(Integer.parseInt(params.substring("space_".length()).trim()));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        if (!params.startsWith("hud_")) {
            return null;
        }
        String rest = params.substring("hud_".length());
        String part = "";
        for (String suffix : new String[]{"_value", "_max", "_percent"}) {
            if (rest.endsWith(suffix) && registry.find(rest).isEmpty()) {
                part = suffix;
                rest = rest.substring(0, rest.length() - suffix.length());
                break;
            }
        }
        HudLayout hud = registry.find(rest).orElse(null);
        if (hud == null) {
            return null;
        }
        if (player == null) {
            return "";
        }
        return switch (part) {
            case "_value" -> format(valueAndMax(player, hud)[0]);
            case "_max" -> format(valueAndMax(player, hud)[1]);
            case "_percent" -> {
                double[] value = valueAndMax(player, hud);
                yield String.valueOf(value[1] <= 0 ? 0 : Math.round(Math.max(0, Math.min(1, value[0] / value[1])) * 100));
            }
            default -> render(player, hud);
        };
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    public void quit(Player player) {
        stats.remove(player.getUniqueId());
        actionBars.forget(player.getUniqueId());
        save(player.getUniqueId());
        values.remove(player.getUniqueId());
    }
}
