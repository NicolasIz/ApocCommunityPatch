package net.Indyuce.mmoitems.stat.data;

import net.Indyuce.mmoitems.api.item.build.MMOItemBuilder;
import net.Indyuce.mmoitems.stat.data.random.RandomStatData;
import net.Indyuce.mmoitems.stat.data.type.StatData;

/** Signatures of MMOItems 6.10's StringData. Not packaged. */
public class StringData implements StatData, RandomStatData<StringData> {

    public StringData(String value) {
        throw new UnsupportedOperationException("signature stub");
    }

    public String getString() {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public StringData randomize(MMOItemBuilder builder) {
        throw new UnsupportedOperationException("signature stub");
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("signature stub");
    }
}
