package com.arkcronist.content.core.definition;

/**
 * What an item is allowed to keep from its base material.
 *
 * <p>A custom item is still a vanilla item underneath: a ruby made from {@code PAPER} can be used
 * to craft books, and a trophy made from {@code PLAYER_HEAD} gets placed as a head. These flags say
 * which of those vanilla reflexes survive. New flags belong here, read by {@code ContentLoader} and
 * enforced by a listener.</p>
 *
 * @param cancelVanillaUse right-clicking does nothing vanilla: no eating, throwing or placing.
 *                         Listeners can still act on the click through {@code CustomItemUseEvent}
 * @param placeable        an item whose base material is a block may be placed as that block
 */
public record ItemBehaviour(boolean cancelVanillaUse, boolean placeable) {

    public static final ItemBehaviour DEFAULT = new ItemBehaviour(false, false);
}
