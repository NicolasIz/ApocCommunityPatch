package com.arkcronist.enchants.engine;

import com.arkcronist.enchants.item.Items;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Fills %victim health%, %block type%, %souls on item% and the rest of AdvancedEnchantments' variables. */
public final class Placeholders {

    private static final Pattern VAR = Pattern.compile("%([a-zA-Z _]+)%");

    private final Engine engine;

    Placeholders(Engine engine) {
        this.engine = engine;
    }

    public String fill(String text, Context ctx) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        Matcher m = VAR.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = value(m.group(1).toLowerCase(Locale.ROOT).trim(), ctx);
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group() : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String value(String key, Context c) {
        LivingEntity other = c.other();
        return switch (key) {
            case "victim health" -> hp(c.victim);
            case "attacker health" -> hp(c.attacker);
            case "player health" -> hp(c.holder);
            case "victim max health" -> maxHp(c.victim);
            case "victim health percent" -> pct(c.victim);
            case "attacker health percent" -> pct(c.attacker);
            case "player health percent" -> pct(c.holder);
            case "victim is marked" -> String.valueOf(engine.marked(c.victim));
            case "charge" -> String.valueOf(engine.charges.percent(c.holder));
            case "clones" -> String.valueOf(engine.clones.count(c.holder));
            case "attacker max health" -> maxHp(c.attacker);
            case "player max health" -> maxHp(c.holder);
            case "victim name" -> c.victim == null ? "" : c.victim.getName();
            case "attacker name" -> c.attacker == null ? "" : c.attacker.getName();
            case "player name" -> c.holder.getName();
            case "mob type", "victim type", "entity type" -> other == null ? "" : other.getType().name();
            case "attacker type" -> c.attacker == null ? "" : c.attacker.getType().name();
            case "is hostile" -> String.valueOf(other instanceof Enemy);
            case "is player", "victim is player" -> String.valueOf(other instanceof Player);
            case "victim is holding" -> holding(c.victim);
            case "attacker is holding" -> holding(c.attacker);
            case "player is holding", "is holding" -> holding(c.holder);
            case "item type" -> c.item == null ? "AIR" : c.item.getType().name();
            case "player is sneaking" -> String.valueOf(c.holder.isSneaking());
            case "victim is sneaking" -> String.valueOf(c.victim instanceof Player p && p.isSneaking());
            case "attacker is sneaking" -> String.valueOf(c.attacker instanceof Player p && p.isSneaking());
            case "victim is blocking" -> String.valueOf(c.victim instanceof Player p && p.isBlocking());
            case "player world", "world" -> c.holder.getWorld().getName();
            case "is night" -> {
                long t = c.holder.getWorld().getTime();
                yield String.valueOf(t >= 13000 && t <= 23000);
            }
            case "is under water", "is in water" -> String.valueOf(c.holder.isInWater());
            case "is headshot" -> String.valueOf(c.headshot);
            case "is critical" -> String.valueOf(c.critical);
            case "combo", "attacker combo", "player combo" -> String.valueOf(engine.combos.get(c.trigger.name().startsWith("DEFENSE")
                    ? c.attacker : c.holder));
            case "victim combo" -> String.valueOf(engine.combos.get(c.victim));
            case "souls on item", "souls" -> String.valueOf(Items.souls(c.item));
            case "damage" -> Engine.num(c.baseDamage);
            case "exp" -> String.valueOf(c.exp);
            case "level", "enchant level" -> String.valueOf(c.level);
            case "pitch" -> Engine.num(c.holder.getLocation().getPitch());
            case "yaw" -> Engine.num(c.holder.getLocation().getYaw());
            case "block type" -> c.block == null ? "AIR" : c.block.getType().name();
            case "block drop type" -> c.block == null ? "AIR" : dropType(c.block);
            case "block natural" -> String.valueOf(c.block == null || !engine.placed.contains(c.block));
            case "is crop" -> String.valueOf(c.block != null && c.block.getBlockData() instanceof Ageable);
            case "is fully grown" -> String.valueOf(c.block != null && c.block.getBlockData() instanceof Ageable a
                    && a.getAge() >= a.getMaximumAge());
            case "caught" -> c.caught == null ? "NONE" : c.caught instanceof org.bukkit.entity.Item it
                    ? it.getItemStack().getType().name() : c.caught.getType().name();
            case "victim", "attacker" -> null;
            default -> {
                engine.unknownPlaceholder(key);
                yield "";
            }
        };
    }

    private static String hp(LivingEntity e) {
        return e == null ? "0" : Engine.num(e.getHealth());
    }

    private static String pct(LivingEntity e) {
        if (e == null) {
            return "0";
        }
        var a = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = a == null ? 20 : a.getValue();
        return Engine.num(max <= 0 ? 0 : e.getHealth() * 100 / max);
    }

    private static String maxHp(LivingEntity e) {
        if (e == null) {
            return "0";
        }
        var a = e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return Engine.num(a == null ? 20 : a.getValue());
    }

    private static String holding(LivingEntity e) {
        if (e == null || e.getEquipment() == null) {
            return "AIR";
        }
        ItemStack i = e.getEquipment().getItemInMainHand();
        return i.getType().name();
    }

    private static String dropType(Block b) {
        var drops = b.getDrops();
        return drops.isEmpty() ? b.getType().name() : drops.iterator().next().getType().name();
    }
}
