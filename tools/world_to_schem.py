"""
Exports a box of a Minecraft world save as a Sponge schematic.

Some bundles arrive as a whole world rather than as a .schem: the builder zips their
creative save and the builds sit on a flat platform inside it. This turns the interesting
box back into a schematic, after which split_schem_bundle.py treats it like any other
bundle and nothing downstream has to know where it came from.

Usage:
    python3 tools/world_to_schem.py <region-dir> <out.schem> x0 y0 z0 x1 y1 z1

Pass y0 as the platform layer the builds stand on, exactly as a WorldEdit selection would.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from read_anvil import chunks, section_blocks
from _write_nbt import save, varints

AIR = 'minecraft:air'


def export(region_dir, out_path, x0, y0, z0, x1, y1, z1):
    width = x1 - x0 + 1
    height = y1 - y0 + 1
    length = z1 - z0 + 1
    palette = {AIR: 0}
    order = [AIR]
    data = [0] * (width * height * length)

    for entry in sorted(os.listdir(region_dir)):
        if not entry.endswith('.mca'):
            continue
        for chunkX, chunkZ, nbt in chunks(os.path.join(region_dir, entry)):
            if not (x0 // 16 - 1 <= chunkX <= x1 // 16 + 1 and z0 // 16 - 1 <= chunkZ <= z1 // 16 + 1):
                continue
            version = nbt.get('DataVersion', 0) or 0
            level = nbt.get('Level') if isinstance(nbt.get('Level'), dict) else {}
            for section in (nbt.get('sections') or level.get('Sections') or []):
                if not isinstance(section, dict):
                    continue
                unpacked = section_blocks(section, version)
                if unpacked is None:
                    continue
                names, indices = unpacked
                if len(names) == 1 and names[0] == AIR:
                    continue
                baseY = section.get('Y', 0) * 16
                for i, index in enumerate(indices):
                    name = names[index] if index < len(names) else AIR
                    if name == AIR:
                        continue
                    x = chunkX * 16 + (i & 15)
                    y = baseY + (i >> 8)
                    z = chunkZ * 16 + ((i >> 4) & 15)
                    if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
                        continue
                    if name not in palette:
                        palette[name] = len(order)
                        order.append(name)
                    cell = (y - y0) * width * length + (z - z0) * width + (x - x0)
                    data[cell] = palette[name]

    save(out_path, 'Schematic', {
        'Version': (3, 2),
        'DataVersion': (3, 3120),
        'Width': (2, width), 'Height': (2, height), 'Length': (2, length),
        'Offset': (11, [0, 0, 0]),
        'PaletteMax': (3, len(order)),
        'Palette': (10, {n: (3, i) for i, n in enumerate(order)}),
        'BlockData': (7, varints(data)),
    })
    solid = sum(1 for v in data if v != 0)
    print('%s  %dx%dx%d  %d bloques  %d estados' % (out_path, width, height, length, solid, len(order)))


if __name__ == '__main__':
    if len(sys.argv) != 9:
        raise SystemExit(__doc__)
    export(sys.argv[1], sys.argv[2], *[int(v) for v in sys.argv[3:9]])
