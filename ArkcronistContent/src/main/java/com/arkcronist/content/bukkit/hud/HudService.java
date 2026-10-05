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
    private final Set<UUID> changed = ConcurrentHashMap.newKeySet();
    private BukkitTask secondly;
    private BukkitTask bars;
    private long seconds;

    public HudService(Plugin plugin, HudRegistry registry, DatabaseManager database, ActionBars actionBars,
                      EngineSettings.Huds settings, BiFunction<OfflinePlayer, String, String> placeholders) {
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

    /** Each second: stored values change by themselves, and an empty one may hurt. */
    private void second() {
        seconds++;
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

    /** The value and max of a HUD for a player: stored, or read through placeholders. Any thread. */
    public double[] valueAndMax(OfflinePlayer player, HudLayout hud) {
        HudDefinition.Source source = hud.definition().source();
        if (source instanceof HudDefinition.Source.Stored stored) {
            return new double[]{stored(player.getUniqueId(), hud), stored.max()};
        }
        HudDefinition.Source.Placeholder placeholder = (HudDefinition.Source.Placeholder) source;
        return new double[]{number(placeholders.apply(player, placeholder.value())),
                number(placeholders.apply(player, placeholder.max()))};
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

    /** Every action-bar HUD of a player's, in one component; null when there is none. */
    private @Nullable Component actionBar(Player player) {
        StringBuilder text = new StringBuilder();
        for (HudLayout hud : registry.all()) {
            if (hud.definition().actionBar()) {
                text.append(render(player, hud));
            }
        }
        // White leaves the icons their own colours; no shadow, or each would be drawn twice.
        return text.isEmpty() ? null
                : Component.text(text.toString(), NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    private void sendBars() {
        if (registry.all().stream().noneMatch(hud -> hud.definition().actionBar())) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            actionBars.send(player);
        }
    }

    /**
     * {@code hud_<id>}, {@code hud_<id>_value}, {@code hud_<id>_max} and {@code space_<pixels>}.
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
        for (String suffix : new String[]{"_value", "_max"}) {
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
            default -> render(player, hud);
        };
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    public void quit(Player player) {
        actionBars.forget(player.getUniqueId());
        save(player.getUniqueId());
        values.remove(player.getUniqueId());
    }
}
