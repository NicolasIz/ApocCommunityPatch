package com.arkcronist.content.core.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One state of {@code minecraft:note_block}, the carrier every custom block is drawn through.
 *
 * <p>A note block has three properties - instrument, note and powered - and the client looks up a
 * model for every combination through the pack's blockstate file. That makes each combination a
 * slot a custom block can occupy. Only the {@link #INSTRUMENTS 16 block instruments} are used; the
 * other seven (zombie, skeleton, creeper, dragon, wither_skeleton, piglin, custom_head) are the
 * mob-head instruments, which the game derives from a head placed on top, and they are left to
 * vanilla. 16 x 25 notes x 2 gives {@value #CAPACITY} states, addressed here by an index:</p>
 *
 * <pre>
 *   index = instrument * 50 + note * 2 + (powered ? 1 : 0)
 * </pre>
 *
 * <p>Index 0 - harp, note 0, unpowered - is {@link #VANILLA}: the state a plain note block is held
 * in, so that a vanilla note block never looks like a custom one. Custom blocks get 1 to 799.</p>
 */
public record NoteBlockState(String instrument, int note, boolean powered) {

    /** Instruments that come from the block beneath: the ones custom blocks may use, in index order. */
    public static final List<String> INSTRUMENTS = List.of("harp", "basedrum", "snare", "hat", "bass",
            "flute", "bell", "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
            "banjo", "pling");

    /** Every instrument the client knows. The blockstate file has to name a model for all of them. */
    public static final List<String> ALL_INSTRUMENTS = List.of("harp", "basedrum", "snare", "hat", "bass",
            "flute", "bell", "guitar", "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit",
            "banjo", "pling", "zombie", "skeleton", "creeper", "dragon", "wither_skeleton", "piglin",
            "custom_head");

    public static final int NOTES = 25;
    public static final int CAPACITY = INSTRUMENTS.size() * NOTES * 2;

    /** The state vanilla note blocks are held in; never given to a custom block. */
    public static final NoteBlockState VANILLA = new NoteBlockState("harp", 0, false);

    private static final Map<String, Integer> INSTRUMENT_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < INSTRUMENTS.size(); i++) {
            INSTRUMENT_INDEX.put(INSTRUMENTS.get(i), i);
        }
    }

    public NoteBlockState {
        if (!ALL_INSTRUMENTS.contains(instrument)) {
            throw new IllegalArgumentException("unknown instrument '" + instrument + "'");
        }
        if (note < 0 || note >= NOTES) {
            throw new IllegalArgumentException("note " + note + " is outside 0-24");
        }
    }

    /** The state at {@code index}, 0 to {@value #CAPACITY} - 1. */
    public static NoteBlockState fromIndex(int index) {
        if (index < 0 || index >= CAPACITY) {
            throw new IllegalArgumentException("note block state index " + index + " is outside 0-" + (CAPACITY - 1));
        }
        return new NoteBlockState(INSTRUMENTS.get(index / (NOTES * 2)), (index / 2) % NOTES, index % 2 == 1);
    }

    /** This state's index, or -1 for a mob head instrument, which no custom block uses. */
    public int index() {
        Integer instrumentIndex = INSTRUMENT_INDEX.get(instrument);
        return instrumentIndex == null ? -1 : instrumentIndex * NOTES * 2 + note * 2 + (powered ? 1 : 0);
    }

    /** The key of this state in a blockstate file: {@code instrument=harp,note=0,powered=false}. */
    public String variantKey() {
        return "instrument=" + instrument + ",note=" + note + ",powered=" + powered;
    }

    /** The server's block data string, e.g. for {@code Bukkit.createBlockData}. */
    public String asBlockData() {
        return "minecraft:note_block[" + variantKey() + "]";
    }

    /**
     * Reads a block data string such as {@code BlockData#getAsString()} gives, with the properties in
     * any order.
     *
     * @return empty when it is not a complete note block state
     */
    public static Optional<NoteBlockState> parse(String blockData) {
        String text = blockData.trim();
        int open = text.indexOf('[');
        if (open < 0 || !text.endsWith("]")) {
            return Optional.empty();
        }
        String block = text.substring(0, open);
        if (!block.equals("minecraft:note_block") && !block.equals("note_block")) {
            return Optional.empty();
        }
        Map<String, String> properties = new HashMap<>();
        for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        try {
            String instrument = properties.get("instrument");
            String note = properties.get("note");
            String powered = properties.get("powered");
            if (instrument == null || note == null || powered == null) {
                return Optional.empty();
            }
            return Optional.of(new NoteBlockState(instrument, Integer.parseInt(note), Boolean.parseBoolean(powered)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
