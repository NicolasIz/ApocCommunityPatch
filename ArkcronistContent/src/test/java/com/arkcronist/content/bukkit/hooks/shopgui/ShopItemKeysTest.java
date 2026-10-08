package com.arkcronist.content.bukkit.hooks.shopgui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** ShopGUI+ buttons and shop items written for ItemsAdder, as a server's config.yml has them. */
class ShopItemKeysTest {

    private static ConfigurationSection item(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return config.getConfigurationSection("goBack.item");
    }

    @Test
    void itemsAddersKeyNamesTheImportedItemWhenItemsAdderIsGone() throws InvalidConfigurationException {
        ConfigurationSection back = item("""
                goBack:
                  item:
                    itemsAdder: ginko_fantasy_shop:transparent
                    name: '&c&lBack'
                  slot: 49
                """);
        assertEquals("ginko_fantasy_shop:transparent", ShopItemKeys.id(back, false));
        assertNull(ShopItemKeys.id(back, true), "with ItemsAdder installed, its key is its own");
    }

    @Test
    void ownKeyWinsAndAVanillaItemIsNotOurs() throws InvalidConfigurationException {
        assertEquals("demo:ruby", ShopItemKeys.id(item("""
                goBack: {item: {arkcontent: demo:ruby, itemsAdder: other:thing}}
                """), true));
        assertEquals("ns:id", ShopItemKeys.id(item("goBack: {item: {itemsadder: ' ns:id '}}"), false));
        assertNull(ShopItemKeys.id(item("goBack: {item: {material: STONE}}"), false));
    }
}
