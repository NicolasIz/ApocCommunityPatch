package com.arkcronist.content.bukkit;

import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Entity ids from the server's own counter, so an entity that exists only on the clients - a
 * cosmetic, an emote's rig - never shares an id with a real one. Paper keeps the counter under its
 * "unsafe" values, deprecated but there for exactly this - taking nothing before 26.3, and from 26.3
 * the world the entity is in. Whichever this server has is looked up once, by name.
 */
public final class EntityIds {

    private static volatile @Nullable Method next;

    private EntityIds() {
    }

    @SuppressWarnings("deprecation")
    public static int next(Server server, World world) {
        UnsafeValues unsafe = server.getUnsafe();
        try {
            Method method = next;
            if (method == null) {
                try {
                    method = UnsafeValues.class.getMethod("nextEntityId");
                } catch (NoSuchMethodException since26_3) {
                    method = UnsafeValues.class.getMethod("nextEntityId", World.class);
                }
                next = method;
            }
            Object id = method.getParameterCount() == 0 ? method.invoke(unsafe) : method.invoke(unsafe, world);
            return (Integer) id;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("This server hands out no entity ids", exception);
        }
    }
}
