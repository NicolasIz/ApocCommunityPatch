package com.arkcronist.content.core.pack;

import java.nio.file.Path;

/**
 * Another plugin's resource pack - a folder or a zip - merged into this one so players get a single
 * pack.
 *
 * <p>Its {@code assets/} are taken, and the overlays its {@code pack.mcmeta} declares, with their
 * entries added to this plugin's {@code pack.mcmeta} ({@link PackSource}); every file goes in as it
 * is. They go in after this plugin's own files, so on a path both supply, this plugin's file is kept
 * and the clash is reported; shared lists such as fonts, atlases, blockstates and custom_model_data
 * definitions are combined instead ({@link JsonMerge}).</p>
 *
 * @param name how the pack is named in messages, e.g. the plugin it came from
 * @param root the folder holding its {@code assets/}, or a zip with {@code assets/} at its top (or
 *             one folder down)
 */
public record ExternalPack(String name, Path root) {
}
