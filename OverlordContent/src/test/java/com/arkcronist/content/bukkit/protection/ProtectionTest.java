package com.arkcronist.content.bukkit.protection;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every protection plugin is asked, all must agree, and a broken one refuses. */
class ProtectionTest {

    /** A protection plugin with fixed answers; the blocks and players asked about are not looked at. */
    record Fixed(String name, boolean players, boolean explosions, boolean changes) implements ProtectionProvider {

        @Override
        public boolean allows(Player player, Block block, Interaction interaction) {
            return players;
        }

        @Override
        public boolean allowsExplosion(Block block, Location origin, Entity source) {
            return explosions;
        }

        @Override
        public boolean allowsChange(Block block, Location from) {
            return changes;
        }
    }

    /** One whose API changed under it. */
    record Broken(String name) implements ProtectionProvider {

        @Override
        public boolean allows(Player player, Block block, Interaction interaction) {
            throw new NoSuchMethodError("RegionQuery.testBuild");
        }

        @Override
        public boolean allowsExplosion(Block block, Location origin, Entity source) {
            throw new IllegalStateException("not loaded");
        }

        @Override
        public boolean allowsChange(Block block, Location from) {
            throw new IllegalStateException("not loaded");
        }
    }

    private final List<LogRecord> logged = new ArrayList<>();
    private final Protection protection = new Protection(logger());

    @Test
    void withNoProtectionPluginEverythingIsAllowed() {
        assertTrue(protection.allows(null, null, Interaction.BREAK));
        assertTrue(protection.allowsExplosion(null, null, null));
        assertTrue(protection.allowsChange(null, null));
        assertEquals(List.of(), protection.names());
    }

    @Test
    void everyPluginMustAgree() {
        protection.add(new Fixed("WorldGuard", true, true, true));
        protection.add(new Fixed("GriefPrevention", false, true, false));

        for (Interaction interaction : Interaction.values()) {
            assertFalse(protection.allows(null, null, interaction), interaction.name());
        }
        assertTrue(protection.allowsExplosion(null, null, null));
        assertFalse(protection.allowsChange(null, null));
        assertEquals(List.of("WorldGuard", "GriefPrevention"), protection.names());
    }

    @Test
    void aBrokenCheckRefusesAndIsLoggedOnce() {
        protection.add(new Fixed("GriefPrevention", true, true, true));
        protection.add(new Broken("WorldGuard"));

        assertFalse(protection.allows(null, null, Interaction.CONTAINER));
        assertFalse(protection.allows(null, null, Interaction.SIT));
        assertFalse(protection.allowsExplosion(null, null, null));
        assertFalse(protection.allowsChange(null, null));

        assertEquals(1, logged.size(), "said once, not on every click");
        assertTrue(logged.getFirst().getMessage().contains("WorldGuard"));
    }

    private Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }
}
