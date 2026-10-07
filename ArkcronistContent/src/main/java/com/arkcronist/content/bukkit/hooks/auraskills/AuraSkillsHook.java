package com.arkcronist.content.bukkit.hooks.auraskills;

import com.arkcronist.content.bukkit.hooks.ContentHook;
import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.AuraSkillsBukkit;
import dev.aurelium.auraskills.api.item.ItemManager;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuraSkills knows every custom item by its id, so its menus, rewards and loot tables can show and
 * give them - drawn with their own models, the same stacks {@code /customgive} makes:
 *
 * <pre>
 * # AuraSkills/menus/skills.yml - the Farming skill's icon
 * skills:
 *   farming:
 *     key: demo/ruby_seeds     # any custom item, as namespace/id
 * </pre>
 *
 * <p>Items are registered with AuraSkills' item registry under their own namespace and id. The
 * items are live before AuraSkills loads its menus - see {@code ContentPipeline#preload} - so a
 * menu written with them works from the first start. After {@code /arkcontent reload} the registry
 * is brought up to date: new items added, removed ones taken out; a menu that names an item added
 * by the reload shows it after {@code /skills reload}, which re-reads the menus.</p>
 *
 * <p>AuraSkills 2.4.0 opened its item registry to other plugins ({@link ItemManager#register}).
 * Before that - 2.0 to 2.3 - the same registry was there, filled by {@code /skills item register},
 * just not in the API; with one of those versions the items go into it directly, the call 2.4.0's
 * API makes itself. If neither is reachable, the items are not registered and the rest of the hook
 * still works.</p>
 *
 * <p>Breaking a custom block earns Mining experience, harvesting a ripe custom crop Farming
 * experience, as their {@code skill-xp} says - with any AuraSkills 2.x.</p>
 */
public final class AuraSkillsHook implements ContentHook, SkillXpHook {

    /** AuraSkills' item registry, however this version of it is reached. */
    interface Registration {
        void register(NamespacedId id, ItemStack item) throws ReflectiveOperationException;

        void unregister(NamespacedId id) throws ReflectiveOperationException;
    }

    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;
    private final String version;
    private @Nullable Registration registration;
    private final Set<NamespacedId> registered = new HashSet<>();

    public AuraSkillsHook(ItemRegistry items, ItemFactory factory, Logger logger) {
        this.items = items;
        this.factory = factory;
        this.logger = logger;
        Plugin auraSkills = Bukkit.getPluginManager().getPlugin("AuraSkills");
        this.version = auraSkills == null ? "?" : auraSkills.getPluginMeta().getVersion();
        this.registration = registration(auraSkills);
        if (registration == null) {
            logger.info("AuraSkills " + version + ": its item registry cannot be reached, so custom items cannot be"
                    + " named in its menus, rewards and loot (AuraSkills 2.4.0 or later can). Skill experience for"
                    + " custom blocks and crops works.");
            return;
        }
        int count = registerAll();
        if (registration != null) {
            logger.info("AuraSkills " + version + ": " + count + " custom item(s) registered; use them in its menus,"
                    + " rewards and loot as key: <namespace>/<id>.");
        }
    }

    /** The API's registry from 2.4.0 on; the plugin's own, by reflection, before; null when neither is there. */
    private @Nullable Registration registration(@Nullable Plugin auraSkills) {
        if (hasApiRegistry()) {
            ItemManager manager = AuraSkillsBukkit.get().getItemManager();
            return new Registration() {
                @Override
                public void register(NamespacedId id, ItemStack item) {
                    manager.register(id, item);
                }

                @Override
                public void unregister(NamespacedId id) {
                    manager.unregister(id);
                }
            };
        }
        if (auraSkills == null) {
            return null;
        }
        try {
            Object registry = auraSkills.getClass().getMethod("getItemRegistry").invoke(auraSkills);
            Method register = registry.getClass().getMethod("register", NamespacedId.class, ItemStack.class);
            Method unregister = registry.getClass().getMethod("unregister", NamespacedId.class);
            return new Registration() {
                @Override
                public void register(NamespacedId id, ItemStack item) throws ReflectiveOperationException {
                    register.invoke(registry, id, item);
                }

                @Override
                public void unregister(NamespacedId id) throws ReflectiveOperationException {
                    unregister.invoke(registry, id);
                }
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreachable) {
            logger.log(Level.FINE, "AuraSkills' own item registry is not reachable", unreachable);
            return null;
        }
    }

    /** Whether this AuraSkills has 2.4.0's {@code ItemManager.register(NamespacedId, ItemStack)}. */
    static boolean hasApiRegistry() {
        try {
            ItemManager.class.getMethod("register", NamespacedId.class, ItemStack.class);
            ItemManager.class.getMethod("unregister", NamespacedId.class);
            return true;
        } catch (NoSuchMethodException | LinkageError older) {
            return false;
        }
    }

    @Override
    public void contentReloaded() {
        if (registration != null) {
            registerAll();
        }
    }

    /** Every loaded item in, every unloaded one out. A registry that stops answering is left alone from then on. */
    private int registerAll() {
        Registration target = registration;
        Set<NamespacedId> now = new HashSet<>();
        try {
            for (CustomItem item : items.all()) {
                NamespacedId id = id(item);
                target.register(id, factory.create(item, 1));
                now.add(id);
            }
            for (NamespacedId gone : registered) {
                if (!now.contains(gone)) {
                    target.unregister(gone);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException wrapped && wrapped.getCause() != null
                    ? wrapped.getCause() : error;
            registration = null;
            logger.warning("AuraSkills " + version + " refused a custom item (" + cause + "); custom items are not"
                    + " registered with it from now on. Skill experience for custom blocks and crops still works.");
            return now.size();
        }
        registered.clear();
        registered.addAll(now);
        return now.size();
    }

    /** {@code demo:ruby} as AuraSkills writes it: {@code demo/ruby}. */
    static NamespacedId id(CustomItem item) {
        String full = item.id();
        int colon = full.indexOf(':');
        return NamespacedId.of(full.substring(0, colon), full.substring(colon + 1));
    }

    @Override
    public void reward(Player player, Source source, String contentId, double xp) {
        SkillsUser user = AuraSkillsApi.get().getUser(player.getUniqueId());
        if (user == null || !user.isLoaded()) {
            return;
        }
        user.addSkillXp(source == Source.CROP ? Skills.FARMING : Skills.MINING, xp);
    }
}
