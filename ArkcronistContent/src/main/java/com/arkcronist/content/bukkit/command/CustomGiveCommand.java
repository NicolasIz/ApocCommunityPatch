package com.arkcronist.content.bukkit.command;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /customgive <player> <item> [amount]}.
 *
 * <p>Registered through Brigadier, as a Paper plugin must: the client gets real argument types, so
 * player selectors like {@code @a} work and item ids are suggested as they are typed. An item can
 * be named in full, {@code demo:ruby}, or by its bare id when only one namespace uses it.</p>
 */
public final class CustomGiveCommand {

    /** A full inventory's worth of the largest stacks. */
    private static final int MAX_AMOUNT = 36 * 64;

    private final ItemRegistry registry;
    private final ItemFactory factory;

    public CustomGiveCommand(ItemRegistry registry, ItemFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("customgive")
                .requires(source -> source.getSender().hasPermission("arkcontent.give"))
                .then(Commands.argument("player", ArgumentTypes.players())
                        .then(Commands.argument("item", ArgumentTypes.key())
                                .suggests(this::suggestItems)
                                .executes(context -> give(context, 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                        .executes(context -> give(context,
                                                IntegerArgumentType.getInteger(context, "amount"))))))
                .build();
    }

    private CompletableFuture<Suggestions> suggestItems(CommandContext<CommandSourceStack> context,
                                                        SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String id : registry.ids()) {
            if (id.startsWith(typed) || id.substring(id.indexOf(':') + 1).startsWith(typed)) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private int give(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        Key key = context.getArgument("item", Key.class);

        // The key argument reads a bare "ruby" as "minecraft:ruby". Custom items never live in the
        // minecraft namespace, so that can only have been meant as a bare id.
        Optional<CustomItem> found = key.namespace().equals(Key.MINECRAFT_NAMESPACE)
                ? registry.find(key.value())
                : registry.get(key.asString());
        if (found.isEmpty()) {
            sender.sendMessage(Component.text("No custom item '" + key.asString()
                    + "'. Loaded: " + registry.size() + " - try tab completion.", NamedTextColor.RED));
            return 0;
        }
        CustomItem item = found.get();

        List<Player> players = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource());
        for (Player player : players) {
            giveTo(player, item, amount);
        }

        String who = players.size() == 1 ? players.get(0).getName() : players.size() + " players";
        sender.sendMessage(Component.text("Gave " + amount + " x " + item.id() + " to " + who + ".",
                NamedTextColor.GREEN));
        return players.size();
    }

    /** In stacks no larger than the material allows; whatever does not fit is dropped at the player's feet. */
    private void giveTo(Player player, CustomItem item, int amount) {
        int stackSize = item.material().getMaxStackSize();
        for (int left = amount; left > 0; left -= stackSize) {
            ItemStack stack = factory.create(item, Math.min(stackSize, left));
            for (ItemStack overflow : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItem(player.getLocation(), overflow);
            }
        }
    }
}
