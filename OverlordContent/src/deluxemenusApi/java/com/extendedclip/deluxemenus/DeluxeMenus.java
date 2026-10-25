package com.extendedclip.deluxemenus;

import com.extendedclip.deluxemenus.hooks.ItemHook;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/** Signature copied from DeluxeMenus 1.14.1 - see build.gradle. Never packaged. */
public class DeluxeMenus extends JavaPlugin {

    public Map<String, ItemHook> getItemHooks() {
        throw new UnsupportedOperationException("signature only");
    }
}
