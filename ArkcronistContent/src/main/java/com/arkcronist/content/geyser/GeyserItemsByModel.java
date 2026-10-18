package com.arkcronist.content.geyser;

import com.arkcronist.content.core.geyser.BedrockConverter;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaConsumable;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaEquippable;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.predicate.PredicateStrategy;
import org.geysermc.geyser.api.predicate.item.ItemRangeDispatchPredicate;
import org.geysermc.geyser.api.util.Identifier;

/**
 * Geyser 2.9 and later: an item is known by its {@code item_model}, as the Java client knows it -
 * and, since 3.3.2, also by its {@code custom_model_data} number where the Java pack draws it by
 * that. A class of its own, loaded only on a Geyser that has this API: on 2.8 nothing here is touched.
 */
final class GeyserItemsByModel {

    private static final String NAMESPACE = BedrockConverter.ITEM_NAMESPACE;
    /** 3.3.2: the same items, known by their number: a Bedrock item of its own for each. */
    private static final String BY_NUMBER_NAMESPACE = BedrockConverter.BY_NUMBER_NAMESPACE;

    private GeyserItemsByModel() {
    }

    static boolean register(GeyserDefineCustomItemsEvent event, BedrockConverter.BedrockItem item) {
        CustomItemDefinition.Builder builder = CustomItemDefinition.builder(Identifier.of(NAMESPACE, item.name()),
                        Identifier.of(item.itemModel()))
                .displayName(item.displayName())
                .bedrockOptions(options(item));
        event.register(Identifier.of(item.javaItem()), uses(builder, item.uses()).build());
        return true;
    }

    /**
     * 3.4: what the stack's own components make of it on Java - drunk, one to a slot, worn - which
     * Geyser takes from the item underneath unless told: a canteen made of paper would be paper.
     */
    private static CustomItemDefinition.Builder uses(CustomItemDefinition.Builder builder, BedrockConverter.Uses uses) {
        if (uses.drinkSeconds() > 0) {
            builder.component(JavaItemDataComponents.CONSUMABLE,
                    JavaConsumable.of(uses.drinkSeconds(), JavaConsumable.Animation.DRINK));
        }
        if (uses.maxStack() > 0) {
            builder.component(JavaItemDataComponents.MAX_STACK_SIZE, uses.maxStack());
        }
        if (uses.maxDamage() > 0) {
            builder.component(JavaItemDataComponents.MAX_DAMAGE, uses.maxDamage());
        }
        if (uses.slot() != null) {
            builder.component(JavaItemDataComponents.EQUIPPABLE,
                    JavaEquippable.of(JavaEquippable.EquipmentSlot.valueOf(uses.slot().toUpperCase(java.util.Locale.ROOT))));
        }
        return builder;
    }

    /**
     * 3.3.2: the item as the plain vanilla one with its {@code custom_model_data} number - a stack
     * ItemsAdder made, or a menu icon set up by number, which the Java pack draws by that number. Its
     * exact number: at least it, and not at least the next, so that no other number lands on it.
     * Only for items the Java pack draws so ({@link BedrockConverter.BedrockItem#byNumber()}).
     */
    static boolean registerByNumber(GeyserDefineCustomItemsEvent event, BedrockConverter.BedrockItem item) {
        Integer number = item.customModelData();
        if (number == null) {
            return false;
        }
        CustomItemDefinition.Builder builder = CustomItemDefinition.builder(Identifier.of(BY_NUMBER_NAMESPACE, item.name()),
                        Identifier.of(item.javaItem()))
                .displayName(item.displayName())
                .bedrockOptions(options(item))
                .predicate(ItemRangeDispatchPredicate.customModelData(0, number))
                .predicate(ItemRangeDispatchPredicate.customModelData(0, number + 1).negate())
                .predicateStrategy(PredicateStrategy.AND);
        event.register(Identifier.of(item.javaItem()), uses(builder, item.uses()).build());
        return true;
    }

    private static CustomItemBedrockOptions.Builder options(BedrockConverter.BedrockItem item) {
        return CustomItemBedrockOptions.builder()
                .icon(item.icon())
                .allowOffhand(true)
                .displayHandheld(item.handheld());
    }
}
