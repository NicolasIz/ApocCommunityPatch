package net.Indyuce.mmoitems.stat.data.random;

import net.Indyuce.mmoitems.api.item.build.MMOItemBuilder;
import net.Indyuce.mmoitems.stat.data.type.StatData;

/** Signature of MMOItems 6.10's RandomStatData. Not packaged. */
public interface RandomStatData<S extends StatData> {

    S randomize(MMOItemBuilder builder);
}
