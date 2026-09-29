package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.core.definition.Placement;
import org.bukkit.entity.Entity;

/**
 * What furniture needs from ModelEngine, in this plugin's own types.
 *
 * <p>Nothing outside the hook package may name a ModelEngine class: a class that did would fail to
 * load on a server without ModelEngine. Furniture talks to this interface; only
 * {@code ModelEngineHook} implements it, and only that class is loaded when ModelEngine is there.</p>
 */
public interface ModelEngineBridge {

    /**
     * Draws a blueprint on the furniture's display entity and hides the display itself.
     *
     * @return false when ModelEngine has no such blueprint; the display keeps drawing the item model
     */
    boolean attach(Entity display, String blueprintId, Placement.Vec3 scale);

    /** Takes the model off, before the display is removed. */
    void detach(Entity display);

    boolean isAttached(Entity display);
}
