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
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * AuraSkills knows every custom item by its id, so its menus, rewards and loot tables can show and
 * give them - drawn with their own models, the same stacks {@code /customgive} makes:
 *
 * <pre>
 * # AuraSkills/menus/skills.yml - the Farming skill's icon
 * skills:
 *   farming:
 *     key: demo:ruby_seeds     # any custom item, as namespace:id (or namespace/id)
 * </pre>
 *
 * <p>Items are registered with AuraSkills' item registry under their own namespace and id. The
 * items are live before AuraSkills loads its menus - see {@code ContentPipeline#preload} - so a
 * menu written with them works from the first start. After {@code /arkcontent reload} the registry
 * is brought up to date: new items added, removed ones taken out; a menu that names an item added
 * by the reload shows it after {@code /skills reload}, which re-reads the menus.</p>
 *
 * <p>Breaking a custom block earns Mining experience, harvesting a ripe custom crop Farming
 * experience, as their {@code skill-xp} says.</p>
 */
public final class AuraSkillsHook implements ContentHook, SkillXpHook {

    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;
    private final ItemManager registry;
    private final Set<NamespacedId> registered = new HashSet<>();

    public AuraSkillsHook(ItemRegistry items, ItemFactory factory, Logger logger) {
        this.items = items;
        this.factory = factory;
        this.logger = logger;
        this.registry = AuraSkillsBukkit.get().getItemManager();
        int count = registerAll();
        logger.info("AuraSkills: " + count + " custom item(s) registered; use them in its menus, rewards and loot"
                + " as key: <namespace>:<id>.");
    }

    @Override
    public void contentReloaded() {
        registerAll();
    }

    /** Every loaded item in, every unloaded one out. */
    private int registerAll() {
        Set<NamespacedId> now = new HashSet<>();
        for (CustomItem item : items.all()) {
            NamespacedId id = id(item);
            registry.register(id, factory.create(item, 1));
            now.add(id);
        }
        for (NamespacedId gone : registered) {
            if (!now.contains(gone)) {
                registry.unregister(gone);
            }
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
