package com.arkcronist.enchants.item;

import com.arkcronist.enchants.model.Enchant;
import java.util.Map;

/** The rules for putting an enchant level on an item (pure logic, testable). */
public final class Applying {

    public enum Result { OK, NOT_APPLICABLE, ALREADY, CONFLICT, MISSING_REQUIRED, NO_SLOTS }

    /** What the item's level of this enchant becomes, or the reason it cannot. */
    public record Outcome(Result result, int newLevel, String detail) {
    }

    private Applying() {
    }

    public static Outcome check(Enchant e, int bookLevel, Map<String, Integer> current, boolean applicable,
                                int maxEnchants, boolean upgradeSame) {
        if (!applicable) {
            return new Outcome(Result.NOT_APPLICABLE, 0, e.appliesTo());
        }
        for (String c : e.conflicts()) {
            if (current.containsKey(c)) {
                return new Outcome(Result.CONFLICT, 0, c);
            }
        }
        for (String r : e.required()) {
            String id = r;
            int need = 1;
            int colon = r.indexOf(':');
            if (colon > 0) {
                id = r.substring(0, colon);
                try {
                    need = Integer.parseInt(r.substring(colon + 1).trim());
                } catch (NumberFormatException ignored) {
                    need = 1;
                }
            }
            if (current.getOrDefault(id, 0) < need) {
                return new Outcome(Result.MISSING_REQUIRED, 0, r);
            }
        }
        Integer have = current.get(e.id());
        if (have != null) {
            if (have > bookLevel || have >= e.maxLevel() && have == bookLevel) {
                return new Outcome(Result.ALREADY, have, "");
            }
            if (have == bookLevel) {
                return upgradeSame ? new Outcome(Result.OK, Math.min(e.maxLevel(), have + 1), "")
                        : new Outcome(Result.ALREADY, have, "");
            }
            return new Outcome(Result.OK, Math.min(e.maxLevel(), bookLevel), "");
        }
        if (current.size() >= maxEnchants) {
            return new Outcome(Result.NO_SLOTS, 0, String.valueOf(maxEnchants));
        }
        return new Outcome(Result.OK, Math.min(Math.max(1, e.maxLevel()), bookLevel), "");
    }
}
