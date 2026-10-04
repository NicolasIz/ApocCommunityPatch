package com.arkcronist.content.bukkit.hooks.betonquest;

import com.arkcronist.content.bukkit.hooks.ContentAccess;
import net.kyori.adventure.text.Component;
import org.betonquest.betonquest.api.BetonQuestApi;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.instruction.Instruction;
import org.betonquest.betonquest.api.integration.Integration;
import org.betonquest.betonquest.api.integration.IntegrationService;
import org.betonquest.betonquest.api.item.QuestItem;
import org.betonquest.betonquest.api.item.QuestItemWrapper;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.quest.action.PlayerAction;
import org.betonquest.betonquest.api.quest.action.PlayerActionFactory;
import org.betonquest.betonquest.api.quest.condition.NullableCondition;
import org.betonquest.betonquest.api.quest.condition.NullableConditionAdapter;
import org.betonquest.betonquest.api.quest.condition.PlayerCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerConditionFactory;
import org.betonquest.betonquest.api.quest.condition.PlayerlessCondition;
import org.betonquest.betonquest.api.quest.condition.PlayerlessConditionFactory;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

import java.util.List;
import java.util.Objects;

/**
 * BetonQuest: this plugin's items and blocks in quests, the way BetonQuest's own ItemsAdder
 * integration has them - an item type, a block condition and a block action:
 *
 * <pre>
 * items:
 *   ruby: "arkcontent demo:ruby"
 * conditions:
 *   hasRuby: "item ruby:3"
 *   rubyOnAltar: "arkcontentBlock demo:ruby_block 100;64;100;world"
 * actions:
 *   giveRuby: "give ruby:5"
 *   buildAltar: "arkcontentBlock demo:ruby_block 100;64;100;world"
 * </pre>
 *
 * <p>Through the item type every item action and condition BetonQuest has - give, take, item,
 * hand, chest, objectives that count items - works with the plugin's items, matched by id. It is
 * registered through BetonQuest's integration service, the way BetonQuest asks other plugins to
 * add to it.</p>
 */
public final class BetonQuestHook implements Integration {

    static final String ITEM_TYPE = "arkcontent";
    static final String BLOCK = "arkcontentBlock";

    private final ContentAccess content;

    private BetonQuestHook(ContentAccess content) {
        this.content = content;
    }

    /** Hands the integration to BetonQuest, which enables it when it is ready for one. */
    public static Boolean register(ContentAccess content, Plugin plugin, ServicesManager services) {
        IntegrationService integrations = services.load(IntegrationService.class);
        if (integrations == null) {
            throw new IllegalStateException("BetonQuest's integration service is not registered");
        }
        integrations.withPolicies().register(plugin, () -> new BetonQuestHook(content));
        return Boolean.TRUE;
    }

    @Override
    public void enable(BetonQuestApi api) {
        api.items().registry().register(ITEM_TYPE, this::item);
        api.items().registry().registerSerializer(ITEM_TYPE, this::serialize);
        api.conditions().registry().registerCombined(BLOCK, new BlockConditionFactory());
        api.actions().registry().register(BLOCK, new BlockActionFactory());
    }

    @Override
    public void postEnable(BetonQuestApi api) {
    }

    @Override
    public void disable() {
    }

    private String parseId(String raw) throws QuestException {
        String id = raw.trim();
        if (!content.exists(id)) {
            throw new QuestException("'" + id + "' is not one of ArkcronistContent's items");
        }
        return id;
    }

    private QuestItemWrapper item(Instruction instruction) throws QuestException {
        Argument<String> id = instruction.parse(this::parseId).get();
        return profile -> new Item(id.getValue(profile));
    }

    private String serialize(ItemStack stack) throws QuestException {
        return content.id(stack).orElseThrow(() -> new QuestException("Item is not an ArkcronistContent item!"));
    }

    /**
     * Only that the item exists: BetonQuest reads its packages before this plugin's first build has
     * given the custom blocks their states, so whether it is a block is asked when the quest runs.
     */
    private String blockId(String raw) throws QuestException {
        return parseId(raw);
    }

    /** One of the plugin's items, as BetonQuest gives, counts and takes it. */
    private final class Item implements QuestItem {

        private final String id;

        Item(String id) {
            this.id = id;
        }

        private ItemStack sample() throws QuestException {
            return content.item(id, 1).orElseThrow(() -> new QuestException("'" + id + "' is no longer defined"));
        }

        @Override
        public Component getName() {
            try {
                ItemMeta meta = sample().getItemMeta();
                return meta != null && meta.hasDisplayName() ? Objects.requireNonNull(meta.displayName())
                        : meta != null && meta.hasItemName() ? meta.itemName() : Component.empty();
            } catch (QuestException exception) {
                return Component.empty();
            }
        }

        @Override
        public List<Component> getLore() {
            try {
                ItemMeta meta = sample().getItemMeta();
                List<Component> lore = meta == null ? null : meta.lore();
                return lore == null ? List.of() : lore;
            } catch (QuestException exception) {
                return List.of();
            }
        }

        @Override
        public ItemStack generate(int amount, Profile profile) throws QuestException {
            return content.item(id, amount).orElseThrow(() -> new QuestException("'" + id + "' is no longer defined"));
        }

        @Override
        public boolean matches(ItemStack stack) {
            String canonical = content.item(id, 1).flatMap(content::id).orElse(id);
            return content.id(stack).filter(canonical::equals).isPresent();
        }
    }

    /** {@code arkcontentBlock <block> <location>}: whether that custom block stands there. */
    private final class BlockConditionFactory implements PlayerConditionFactory, PlayerlessConditionFactory {

        @Override
        public PlayerCondition parsePlayer(Instruction instruction) throws QuestException {
            return new NullableConditionAdapter(parse(instruction));
        }

        @Override
        public PlayerlessCondition parsePlayerless(Instruction instruction) throws QuestException {
            return new NullableConditionAdapter(parse(instruction));
        }

        private NullableCondition parse(Instruction instruction) throws QuestException {
            Argument<String> block = instruction.parse(BetonQuestHook.this::blockId).get();
            Argument<Location> location = instruction.location().get();
            return new NullableCondition() {
                @Override
                public boolean check(Profile profile) throws QuestException {
                    String id = content.item(block.getValue(profile), 1).flatMap(content::id).orElse("");
                    return content.blockAt(location.getValue(profile).getBlock()).filter(id::equals).isPresent();
                }

                @Override
                public boolean isPrimaryThreadEnforced() {
                    return true;
                }
            };
        }
    }

    /** {@code arkcontentBlock <block> <location>}: puts that custom block there. */
    private final class BlockActionFactory implements PlayerActionFactory {

        @Override
        public PlayerAction parsePlayer(Instruction instruction) throws QuestException {
            Argument<String> block = instruction.parse(BetonQuestHook.this::blockId).get();
            Argument<Location> location = instruction.location().get();
            return new PlayerAction() {
                @Override
                public void execute(Profile profile) throws QuestException {
                    if (!content.setBlock(location.getValue(profile).getBlock(), block.getValue(profile))) {
                        throw new QuestException("Could not place " + block.getValue(profile));
                    }
                }

                @Override
                public boolean isPrimaryThreadEnforced() {
                    return true;
                }
            };
        }
    }
}
