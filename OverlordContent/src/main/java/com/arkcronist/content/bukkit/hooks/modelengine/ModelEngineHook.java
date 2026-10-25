package com.arkcronist.content.bukkit.hooks.modelengine;

import com.arkcronist.content.bukkit.hooks.ModelEngineBridge;
import com.arkcronist.content.core.definition.Placement;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.entity.Entity;
import org.joml.Vector3f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Furniture drawn by ModelEngine: an animated blueprint instead of an item display's static model.
 *
 * <p>The furniture keeps everything else it has - support block, chunk link, stored row - and its
 * item display stays the entity the model is attached to. ModelEngine hides the display, so its
 * item is not drawn under the model; should ModelEngine ever be removed, the display simply shows
 * its item model again and the furniture still looks like something.</p>
 *
 * <p>The model is marked to be saved with the entity, so ModelEngine restores it when the chunk
 * loads again; {@code FurnitureListener} re-attaches it should that not have happened.</p>
 */
public final class ModelEngineHook implements ModelEngineBridge {

    private final Logger logger;
    private final Set<String> reportedMissing = ConcurrentHashMap.newKeySet();

    public ModelEngineHook(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean attach(Entity display, String blueprintId, Placement.Vec3 scale) {
        ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(blueprintId);
        if (blueprint == null) {
            if (reportedMissing.add(blueprintId)) {
                logger.warning("ModelEngine has no blueprint '" + blueprintId
                        + "' - furniture using it is drawn with its item model instead.");
            }
            return false;
        }
        ActiveModel model = ModelEngineAPI.createActiveModel(blueprint);
        model.setScale(new Vector3f(scale.x(), scale.y(), scale.z()));

        ModeledEntity modeled = ModelEngineAPI.getOrCreateModeledEntity(display);
        // false: the display stays as it is. The furniture's body is its support block.
        modeled.addModel(model, false);
        modeled.setBaseEntityVisible(false);
        modeled.setSaved(true);
        return true;
    }

    @Override
    public void detach(Entity display) {
        ModeledEntity modeled = ModelEngineAPI.getModeledEntity(display);
        if (modeled != null && !modeled.isDestroyed()) {
            modeled.destroy();
        }
    }

    @Override
    public boolean isAttached(Entity display) {
        return ModelEngineAPI.getModeledEntity(display) != null;
    }
}
