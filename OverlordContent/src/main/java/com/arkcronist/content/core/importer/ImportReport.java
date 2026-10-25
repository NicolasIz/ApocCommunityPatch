package com.arkcronist.content.core.importer;

import java.util.List;

/**
 * What one run of the ItemsAdder importer did.
 *
 * @param files     YAML files read under import/
 * @param converted content files written - one per ItemsAdder config with items, one per namespace of
 *                  a generated pack
 * @param items     items written into contents/
 * @param skipped   items left out, each with its reason in {@code problems}
 * @param resources model, texture, animation, sound and item definition files copied into contents/
 * @param packs     ItemsAdder generated resource packs taken whole into packs/
 * @param packFiles files those packs hold, every one copied byte for byte
 * @param written   the content files written, relative to contents/
 * @param problems  what could not be imported - a broken file, a missing texture, a skipped item
 * @param notes     what was imported differently from how ItemsAdder does it, or not at all
 */
public record ImportReport(int files, int converted, int items, int skipped, int resources, int packs, int packFiles,
                           List<String> written, List<String> problems, List<String> notes) {

    public ImportReport {
        written = List.copyOf(written);
        problems = List.copyOf(problems);
        notes = List.copyOf(notes);
    }

    /** True when import/ held nothing that looked like ItemsAdder content. */
    public boolean empty() {
        return converted == 0 && items == 0 && skipped == 0 && packs == 0;
    }
}
