package com.arkcronist.gen.bukkit.mythic;

/**
 * What MythicMobs will tell us about one of its mobs.
 *
 * <p>Deliberately small, and deliberately tolerant of missing pieces. Every field here is read by
 * reflection from a plugin whose API has moved packages at least once, so any of them may come back
 * empty on a version this build has never seen. The classifier is written to still reach a sensible
 * verdict from whatever did arrive, because a pack half-read is worth more than a pack refused.</p>
 *
 * @param name        the internal id, which is what a spawn list has to contain
 * @param entityType  the vanilla entity it is built on, upper case, or empty when unknown
 * @param health      its configured health, or 0 when unknown
 * @param displayName what a player sees over its head, or empty
 * @param faction     the MythicMobs faction, or empty - few packs set it
 */
public record MobFacts(String name, String entityType, double health, String displayName,
                       String faction) {

    public MobFacts {
        name = name == null ? "" : name.trim();
        entityType = entityType == null ? "" : entityType.trim().toUpperCase(java.util.Locale.ROOT);
        displayName = displayName == null ? "" : displayName.trim();
        faction = faction == null ? "" : faction.trim();
    }

    /** Just the name, for the common case of a pack that says nothing else. */
    public static MobFacts of(String name) {
        return new MobFacts(name, "", 0.0, "", "");
    }
}
