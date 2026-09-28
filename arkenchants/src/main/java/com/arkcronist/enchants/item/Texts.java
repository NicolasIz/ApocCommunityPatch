package com.arkcronist.enchants.item;

import com.arkcronist.enchants.model.Trigger;
import com.arkcronist.enchants.text.Percent;
import java.util.LinkedHashSet;
import java.util.Set;

/** Words for book lore: when an enchant fires, how often, and how long it rests. */
public final class Texts {

    private Texts() {
    }

    public static String triggers(Set<Trigger> ts) {
        Set<String> out = new LinkedHashSet<>();
        boolean atk = ts.contains(Trigger.ATTACK);
        boolean atkMob = ts.contains(Trigger.ATTACK_MOB);
        boolean shoot = ts.contains(Trigger.SHOOT);
        boolean shootMob = ts.contains(Trigger.SHOOT_MOB);
        boolean def = ts.contains(Trigger.DEFENSE) || ts.contains(Trigger.DEFENSE_MOB);
        boolean kill = ts.contains(Trigger.KILL_MOB) || ts.contains(Trigger.KILL_PLAYER);
        if (atk && atkMob) {
            out.add("al golpear");
        } else if (atk) {
            out.add("al golpear jugadores");
        } else if (atkMob) {
            out.add("al golpear criaturas");
        }
        if (shoot && shootMob) {
            out.add("al acertar un disparo");
        } else if (shoot) {
            out.add("al disparar a jugadores");
        } else if (shootMob) {
            out.add("al disparar a criaturas");
        }
        if (def) {
            out.add("al recibir un golpe");
        }
        if (kill) {
            out.add(ts.contains(Trigger.KILL_MOB) && ts.contains(Trigger.KILL_PLAYER) ? "al matar"
                    : ts.contains(Trigger.KILL_MOB) ? "al matar criaturas" : "al matar jugadores");
        }
        for (Trigger t : ts) {
            String w = switch (t) {
                case DEFENSE_PROJECTILE -> "al recibir un proyectil";
                case CHARGED_ATTACK -> "con el ataque cargado";
                case BOW_FIRE -> "al soltar la flecha";
                case MINING -> "al romper bloques";
                case DEATH -> "al recibir un golpe mortal";
                case FALL_DAMAGE -> "al caer";
                case FIRE -> "al quemarte";
                case EXPLOSION -> "en explosiones";
                case EFFECT_STATIC -> "siempre, mientras lo llevas";
                case HELD -> "siempre, mientras lo empuñas";
                case REPEATING -> "cada pocos segundos";
                case ELYTRA_FLY -> "al volar con élitros";
                case RIGHT_CLICK -> "con clic derecho";
                case CATCH_FISH -> "al pescar";
                case HOOK_ENTITY -> "al enganchar algo";
                case BITE_HOOK -> "cuando pica un pez";
                case ITEM_BREAK -> "cuando el objeto se rompe";
                default -> null;
            };
            if (w != null) {
                out.add(w);
            }
        }
        return out.isEmpty() ? "-" : String.join(", ", out);
    }

    public static String chance(double chance, Set<Trigger> ts) {
        if (chance >= 100) {
            boolean passive = ts.stream().allMatch(Trigger::passive);
            return passive ? "permanente" : "siempre";
        }
        return Percent.fmt(Math.max(0, chance)) + "%";
    }

    public static String cooldown(double seconds) {
        if (seconds <= 0) {
            return "ninguna";
        }
        if (seconds >= 60 && seconds % 60 == 0) {
            return (int) (seconds / 60) + " min";
        }
        return Percent.fmt(seconds) + " s";
    }
}
