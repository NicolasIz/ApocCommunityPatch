package com.arkcronist.enchants.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** What an effect acts on: entities (@Victim, @Attacker, @Self, @Aoe) or blocks (@Block, @Trench, @Tunnel, @Veinmine). */
public final class Targets {

    public final List<LivingEntity> entities = new ArrayList<>();
    public final List<Block> blocks = new ArrayList<>();

    private Targets() {
    }

    public static Targets of(EffectLine line, Context ctx) {
        Targets t = new Targets();
        String target = line.target() == null ? "SELF" : line.target();
        Map<String, String> a = line.targetArgs();
        switch (target) {
            case "VICTIM" -> add(t, ctx.victim);
            case "ATTACKER" -> add(t, ctx.attacker);
            case "SELF", "PLAYER" -> add(t, ctx.holder);
            case "AOE" -> aoe(t, ctx, a);
            case "BLOCK" -> {
                if (ctx.block != null) {
                    t.blocks.add(ctx.block);
                }
            }
            case "TRENCH" -> trench(t, ctx, intArg(a, 3, "r", "radius"));
            case "TUNNEL" -> tunnel(t, ctx, a);
            case "VEINMINE" -> vein(t, ctx, intArg(a, 64, "limit", "max", "r"));
            default -> add(t, ctx.holder);
        }
        return t;
    }

    private static void add(Targets t, LivingEntity e) {
        if (e != null && e.isValid()) {
            t.entities.add(e);
        }
    }

    private static void aoe(Targets t, Context ctx, Map<String, String> a) {
        double r = doubleArg(a, 3, "radius", "r");
        String who = a.getOrDefault("target", "all").toLowerCase(Locale.ROOT);
        Location at = ctx.holder.getLocation();
        for (Entity e : at.getWorld().getNearbyEntities(at, r, r, r)) {
            if (!(e instanceof LivingEntity le) || e == ctx.holder || e.isDead()) {
                continue;
            }
            boolean player = e instanceof Player;
            if (who.startsWith("mob") && player || who.startsWith("player") && !player
                    || who.startsWith("hostile") && !(e instanceof Enemy)) {
                continue;
            }
            t.entities.add(le);
        }
    }

    /** A square of side r on the face the player is looking at (flat when looking up or down). */
    private static void trench(Targets t, Context ctx, int size) {
        if (ctx.block == null) {
            return;
        }
        int h = Math.max(0, size / 2);
        float pitch = ctx.holder.getLocation().getPitch();
        BlockFace facing = ctx.holder.getFacing();
        for (int i = -h; i <= h; i++) {
            for (int j = -h; j <= h; j++) {
                Block b;
                if (Math.abs(pitch) > 45) {
                    b = ctx.block.getRelative(i, 0, j);
                } else if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
                    b = ctx.block.getRelative(i, j, 0);
                } else {
                    b = ctx.block.getRelative(0, j, i);
                }
                if (!b.equals(ctx.block)) {
                    t.blocks.add(b);
                }
            }
        }
    }

    /** radiuscustom=WxHxD: a box W wide and H high going D blocks away from the player (or up/down). */
    private static void tunnel(Targets t, Context ctx, Map<String, String> a) {
        if (ctx.block == null) {
            return;
        }
        int w = 1, hgt = 3, d = 5;
        String rc = a.get("radiuscustom");
        if (rc != null) {
            String[] p = rc.toLowerCase(Locale.ROOT).split("x");
            try {
                w = Integer.parseInt(p[0].trim());
                hgt = Integer.parseInt(p[1].trim());
                d = Integer.parseInt(p[2].trim());
            } catch (RuntimeException ignored) {
                // keep the defaults
            }
        } else if (a.containsKey("r")) {
            d = intArg(a, 5, "r");
        }
        String mode = a.getOrDefault("mode", "STRAIGHT").toUpperCase(Locale.ROOT);
        BlockFace f = ctx.holder.getFacing();
        int fx = f.getModX(), fz = f.getModZ();
        int hw = w / 2;
        for (int k = 0; k < d; k++) {
            for (int i = -hw; i <= hw - (w % 2 == 0 ? 1 : 0); i++) {
                for (int j = 0; j < hgt; j++) {
                    Block b;
                    switch (mode) {
                        case "UP" -> b = ctx.block.getRelative(fz == 0 ? 0 : i, k, fx == 0 ? 0 : i).getRelative(fx * (j - hgt / 2), 0, fz * (j - hgt / 2));
                        case "DOWN" -> b = ctx.block.getRelative(fz == 0 ? 0 : i, -k, fx == 0 ? 0 : i).getRelative(fx * (j - hgt / 2), 0, fz * (j - hgt / 2));
                        default -> b = ctx.block.getRelative(fx * k + (fx == 0 ? i : 0), j - hgt / 2, fz * k + (fz == 0 ? i : 0));
                    }
                    if (!b.equals(ctx.block)) {
                        t.blocks.add(b);
                    }
                }
            }
        }
    }

    /** Every touching block of the same type as the one broken, up to a limit. */
    private static void vein(Targets t, Context ctx, int limit) {
        if (ctx.block == null) {
            return;
        }
        Material type = ctx.block.getType();
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(ctx.block);
        seen.add(ctx.block);
        while (!queue.isEmpty() && t.blocks.size() < limit) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (seen.add(n) && n.getType() == type) {
                            queue.add(n);
                            t.blocks.add(n);
                        }
                    }
                }
            }
        }
    }

    static int intArg(Map<String, String> a, int def, String... keys) {
        for (String k : keys) {
            String v = a.get(k);
            if (v != null) {
                try {
                    return (int) Double.parseDouble(v);
                } catch (NumberFormatException ignored) {
                    // fall through to the default
                }
            }
        }
        return def;
    }

    static double doubleArg(Map<String, String> a, double def, String... keys) {
        for (String k : keys) {
            String v = a.get(k);
            if (v != null) {
                try {
                    return Double.parseDouble(v);
                } catch (NumberFormatException ignored) {
                    // fall through to the default
                }
            }
        }
        return def;
    }
}
