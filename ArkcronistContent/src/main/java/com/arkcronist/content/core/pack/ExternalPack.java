package com.arkcronist.content.core.pack;

import java.nio.file.Path;

/**
 * Another plugin's resource pack folder, merged into this one so players get a single pack.
 *
 * <p>Only its {@code assets/} are taken - the {@code pack.mcmeta} is this plugin's - and they go in
 * after this plugin's own files, so on a path both supply, this plugin's file is kept and the
 * clash is reported.</p>
 *
 * @param name how the pack is named in messages, e.g. the plugin it came from
 * @param root the folder holding its {@code assets/}
 */
public record ExternalPack(String name, Path root) {
}
