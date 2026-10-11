package com.arkcronist.content.bukkit.hooks.geyser;

import com.arkcronist.content.core.geyser.BedrockConverter;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.util.Identifier;

/**
 * Geyser 2.9 and later: an item is known by its {@code item_model}, as the Java client knows it.
 * A class of its own, loaded only on a Geyser that has this API: on 2.8 nothing here is touched.
 */
final class GeyserItemsByModel {

    private static final String NAMESPACE = "arkcontent";

    private GeyserItemsByModel() {
    }

    static boolean register(GeyserDefineCustomItemsEvent event, BedrockConverter.BedrockItem item) {
        CustomItemDefinition definition = CustomItemDefinition.builder(Identifier.of(NAMESPACE, item.name()),
                        Identifier.of(item.itemModel()))
                .displayName(item.displayName())
                .bedrockOptions(CustomItemBedrockOptions.builder()
                        .icon(item.icon())
                        .allowOffhand(true)
                        .displayHandheld(item.handheld()))
                .build();
        event.register(Identifier.of(item.javaItem()), definition);
        return true;
    }
}
