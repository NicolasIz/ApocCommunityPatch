"""
Reads Minecraft Anvil region files (.mca) so a world save can be mined for builds.

Bundles do not always arrive as schematics. A builder who ships a world instead leaves
their work sitting in region files, and this turns those back into a block grid that
split_schem_bundle.py can treat exactly like a bundle.

Only what is needed to recover blocks is implemented: the region header, the per-chunk
zlib/gzip envelope, and both section layouts in circulation - the modern one where a
section holds a 'block_states' compound, and the pre-1.18 one where 'Palette' and
'BlockStates' sit directly under 'Level'.
"""

import io
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _nbt import R

SECTOR = 4096


def _chunk_nbt(blob):
    length, compression = struct.unpack_from('>IB', blob, 0)
    payload = blob[5:5 + length - 1]
    if compression == 1:
        payload = zlib.decompress(payload, 16 + zlib.MAX_WBITS)
    elif compression == 2:
        payload = zlib.decompress(payload)
    elif compression != 3:
        raise ValueError('unsupported chunk compression %d' % compression)
    reader = R(payload)
    tag = reader.u1()
    reader.s()
    return reader.payload(tag)


def chunks(path):
    """Yields (chunkX, chunkZ, nbt) for every chunk present in a region file."""
    if os.path.getsize(path) < 2 * SECTOR:
        return
    name = os.path.basename(path).split('.')
    regionX, regionZ = int(name[1]), int(name[2])
    with open(path, 'rb') as handle:
        header = handle.read(SECTOR)
        for index in range(1024):
            offset = int.from_bytes(header[index * 4:index * 4 + 3], 'big')
            count = header[index * 4 + 3]
            if offset == 0 or count == 0:
                continue
            handle.seek(offset * SECTOR)
            blob = handle.read(count * SECTOR)
            try:
                nbt = _chunk_nbt(blob)
            except Exception:
                continue
            yield regionX * 32 + (index % 32), regionZ * 32 + (index // 32), nbt


def _state_name(entry):
    name = entry.get('Name', 'minecraft:air')
    props = entry.get('Properties')
    if not props:
        return name
    body = ','.join('%s=%s' % (k, props[k]) for k in sorted(props))
    return '%s[%s]' % (name, body)


def section_blocks(section, data_version=0):
    """Returns (palette, indices) for one 16x16x16 section, or None when it is empty.

    Two layouts are accepted. Before 1.18 the palette and the packed data sit straight in
    the section under capitalised names; from 1.18 they live in a 'block_states' compound.
    Before 1.16 an entry could straddle two longs, and after it could not, so the packing
    is chosen from the world's data version rather than guessed.
    """
    states = section.get('block_states')
    if isinstance(states, dict):
        raw_palette = states.get('palette', [])
        data = states.get('data')
    else:
        raw_palette = section.get('Palette', [])
        data = section.get('BlockStates')
    palette = [_state_name(e) for e in raw_palette if isinstance(e, dict)]
    if not palette:
        return None
    if data is None:
        # A section of a single block type stores no index array at all.
        return palette, [0] * 4096
    bits = max(4, (len(palette) - 1).bit_length())
    mask = (1 << bits) - 1
    indices = []
    if data_version >= 2529:
        per_long = 64 // bits
        for word in data:
            value = word & 0xFFFFFFFFFFFFFFFF
            for slot in range(per_long):
                if len(indices) == 4096:
                    break
                indices.append((value >> (slot * bits)) & mask)
            if len(indices) == 4096:
                break
    else:
        # Pre-1.16: one continuous bit stream, entries may cross a long boundary.
        stream = 0
        held = 0
        for word in data:
            stream |= (word & 0xFFFFFFFFFFFFFFFF) << held
            held += 64
            while held >= bits and len(indices) < 4096:
                indices.append(stream & mask)
                stream >>= bits
                held -= bits
            if len(indices) == 4096:
                break
    while len(indices) < 4096:
        indices.append(0)
    return palette, indices


def world_blocks(directory, on_block):
    """Walks every block in every region, calling on_block(x, y, z, state_name)."""
    for entry in sorted(os.listdir(directory)):
        if not entry.endswith('.mca'):
            continue
        for chunkX, chunkZ, nbt in chunks(os.path.join(directory, entry)):
            version = nbt.get('DataVersion', 0) or 0
            level = nbt.get('Level') if isinstance(nbt.get('Level'), dict) else {}
            sections = nbt.get('sections') or level.get('Sections') or []
            for section in sections:
                if not isinstance(section, dict):
                    continue
                unpacked = section_blocks(section, version)
                if unpacked is None:
                    continue
                palette, indices = unpacked
                if len(palette) == 1 and palette[0] == 'minecraft:air':
                    continue
                baseY = section.get('Y', 0) * 16
                for i, index in enumerate(indices):
                    name = palette[index] if index < len(palette) else 'minecraft:air'
                    if name == 'minecraft:air':
                        continue
                    on_block(chunkX * 16 + (i & 15), baseY + (i >> 8), chunkZ * 16 + ((i >> 4) & 15), name)
