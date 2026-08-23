"""
Swaps one block for another inside already-extracted tree schematics.

Written for the scarlet forest. Its canopies were nether wart block, which is red because the
texture is red - it takes no tint at all. To have the leaves follow the biome's own colour they have
to be a leaf the client actually tints, and that rules out birch: birch and spruce leaves carry a
hardcoded colour in vanilla and ignore the biome. Oak does follow it, so oak is what goes in.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _schem import decode
from _write_nbt import save

FROM = "minecraft:nether_wart_block"
TO = "minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]"


def varints(values):
    out = bytearray()
    for v in values:
        while True:
            b = v & 0x7F
            v >>= 7
            out.append(b | (0x80 if v else 0))
            if not v:
                break
    return out


def retint(path):
    v, W, H, L, inv, cells = decode(path)
    names = [inv[i] for i in range(len(inv))]
    if FROM not in names:
        return False

    # Merge onto an existing entry when the target is already in the palette, so the file does not
    # end up with the same block twice under two ids.
    remap = {}
    order = []
    for i, n in enumerate(names):
        n = TO if n == FROM else n
        if n not in order:
            order.append(n)
        remap[i] = order.index(n)

    data = [remap[c] for c in cells]
    save(path, 'Schematic', {
        'Version': (3, 2),
        'DataVersion': (3, 3120),
        'Width': (2, W),
        'Height': (2, H),
        'Length': (2, L),
        'PaletteMax': (3, len(order)),
        'Palette': (10, {n: (3, i) for i, n in enumerate(order)}),
        'BlockData': (7, varints(data)),
    })
    return True


if __name__ == '__main__':
    root = sys.argv[1] if len(sys.argv) > 1 else 'src/main/resources/prefabs/trees'
    changed = 0
    for fn in sorted(os.listdir(root)):
        if fn.endswith('.schem') and retint(os.path.join(root, fn)):
            print(f"  {fn}")
            changed += 1
    print(f"{changed} file(s) retinted: {FROM} -> {TO}")
