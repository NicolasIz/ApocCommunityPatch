"""
Pulls individual trees out of a landscape selection.

split_schem_bundle.py expects a bundle: separate builds standing on one flat platform, far
enough apart that a connected clump is a whole build. A slice of real forest is not that.
The ground is natural terrain rather than a platform, and the canopies touch - so connectivity
alone returns the whole wood as two or three enormous blobs.

This works from the trunks instead:

  1. ground and undergrowth are dropped outright - they are not part of any tree;
  2. trunk blocks are clustered, and clusters whose footprints nearly touch are merged, so a
     thick trunk stays one tree rather than several;
  3. every leaf is given to the nearest trunk, by horizontal distance, within a limit.

Usage:
    python3 tools/extract_trees.py <selection.schem> <output-dir> <tag> [max-leaf-radius]
"""

import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _schem import decode, at
from _write_nbt import save, varints
from collections import deque, Counter

if len(sys.argv) not in (4, 5):
    raise SystemExit(__doc__)
SRC, OUT, TAG = sys.argv[1], sys.argv[2], sys.argv[3]
MAX_R = int(sys.argv[4]) if len(sys.argv) == 5 else 14
MERGE = int(os.environ.get('MERGE_GAP', '2'))
os.makedirs(OUT, exist_ok=True)

v, W, H, L, inv, out = decode(SRC)
name = {i: inv[i].split('[')[0].replace('minecraft:', '') for i in inv}

def is_air(i):    return name[i] in ('air', 'cave_air', 'void_air')
def is_leaf(i):   return 'leaves' in name[i]
def is_trunk(i):  return ('log' in name[i] or 'wood' in name[i] or 'stem' in name[i]
                          or 'hyphae' in name[i])
def keep(i):
    # Vines, moss and cocoa hang on the tree; everything else that is not trunk or leaf is
    # ground, undergrowth or scenery and belongs to the landscape, not to a tree.
    n = name[i]
    # pink_petals and the like are ground cover: they need a block under them, and carried along
    # with a tree they would be left floating and pop off as items on the first update.
    return is_leaf(i) or is_trunk(i) or n in ('vine', 'moss_carpet', 'cocoa', 'glow_lichen',
                                              'shroomlight', 'mangrove_roots')

idx = lambda x, y, z: y * W * L + z * W + x

# --- 1. trunk clusters -------------------------------------------------------------------
# Clustering is done on the BASE of the trunks only. Big trees touch each other higher up -
# a branch of one resting on a branch of the next - and connectivity through that fuses two
# trees into one. At the foot they are clearly separate, which is where the count comes from.
trunk_ys = [y for x in range(W) for y in range(H) for z in range(L)
            if is_trunk(out[idx(x, y, z)])]
BASE_TOP = min(trunk_ys) + int(os.environ.get('BASE_LAYERS', '6')) if trunk_ys else H

seen = set()
clusters = []
for x in range(W):
    for y in range(H):
        for z in range(L):
            i = out[idx(x, y, z)]
            if not is_trunk(i) or (x, y, z) in seen or y > BASE_TOP:
                continue
            q = deque([(x, y, z)]); seen.add((x, y, z)); cells = []
            while q:
                cx, cy, cz = q.popleft(); cells.append((cx, cy, cz))
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        for dz in (-1, 0, 1):
                            nx, ny, nz = cx + dx, cy + dy, cz + dz
                            if not (0 <= nx < W and 0 <= ny < H and 0 <= nz < L): continue
                            if (nx, ny, nz) in seen or ny > BASE_TOP: continue
                            if not is_trunk(out[idx(nx, ny, nz)]): continue
                            seen.add((nx, ny, nz)); q.append((nx, ny, nz))
            if len(cells) >= 12:                     # a stray log is not a tree
                clusters.append(cells)

# merge clusters whose footprints nearly touch: one thick trunk, not four
def foot(c): return {(x, z) for x, _, z in c}
merged = True
while merged:
    merged = False
    for a in range(len(clusters)):
        for b in range(a + 1, len(clusters)):
            fa, fb = foot(clusters[a]), foot(clusters[b])
            if any(abs(ax - bx) <= MERGE and abs(az - bz) <= MERGE for ax, az in fa for bx, bz in fb):
                clusters[a] += clusters[b]; del clusters[b]; merged = True; break
        if merged: break

print(f"{len(clusters)} trunk cluster(s)")

# --- 2. give every leaf to the nearest trunk ---------------------------------------------
centres = []
for c in clusters:
    xs = [p[0] for p in c]; zs = [p[2] for p in c]
    centres.append((sum(xs) / len(xs), sum(zs) / len(zs)))

trees = [list(c) for c in clusters]
for x in range(W):
    for y in range(H):
        for z in range(L):
            i = out[idx(x, y, z)]
            if is_air(i) or not keep(i) or (x, y, z) in seen:
                continue
            best, bd = -1, MAX_R * MAX_R
            for k, (cx, cz) in enumerate(centres):
                d = (x - cx) ** 2 + (z - cz) ** 2
                if d < bd: bd, best = d, k
            if best >= 0:
                trees[best].append((x, y, z))

# --- 3. write one file per tree ----------------------------------------------------------
def size_of(w, h, l):
    spread = max(w, l)
    if spread <= 9 and h <= 12: return 'small'
    if h >= 30 or spread >= 26: return 'giant'
    if h >= 20 or spread >= 18: return 'large'
    return 'medium'

written = Counter()
for n, cells in enumerate(sorted(trees, key=len, reverse=True), 1):
    if len(cells) < 60:
        continue
    xs = [c[0] for c in cells]; ys = [c[1] for c in cells]; zs = [c[2] for c in cells]
    x0, x1 = min(xs), max(xs); y0, y1 = min(ys), max(ys); z0, z1 = min(zs), max(zs)
    w, h, l = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    pal = {'minecraft:air': 0}
    # Optional substitution, given as FROM=TO on the environment. Minecraft has no red leaf block,
    # and leaves take their colour from the biome the client is standing in - so a scarlet canopy
    # cannot be made out of leaves at all. Swapping them for a block that is red in its own right
    # is what makes the colour survive without a resource pack.
    swap = {}
    for pair in os.environ.get('REPLACE', '').split(','):
        if '=' in pair:
            a, b = pair.split('=', 1)
            swap[a.strip()] = b.strip()
    data = [0] * (w * h * l)
    for (x, y, z) in cells:
        key = inv[out[idx(x, y, z)]]
        bare = key.split('[')[0]
        if bare in swap:
            key = swap[bare]
        if key not in pal: pal[key] = len(pal)
        data[(y - y0) * w * l + (z - z0) * w + (x - x0)] = pal[key]
    cls = size_of(w, h, l)
    fn = f"{cls}_{TAG}_{n:02d}.schem"
    # Sponge v2, the same shape split_schem_bundle.py writes, because that is what the rest of
    # the bundled prefabs are and the reader takes either.
    order = [k for k, _ in sorted(pal.items(), key=lambda kv: kv[1])]
    save(os.path.join(OUT, fn), 'Schematic', {
        'Version': (3, 2),
        'DataVersion': (3, 3120),
        'Width': (2, w), 'Height': (2, h), 'Length': (2, l),
        'Offset': (11, [0, 0, 0]),
        'PaletteMax': (3, len(order)),
        'Palette': (10, {n: (3, i) for i, n in enumerate(order)}),
        'BlockData': (7, varints(data)),
    })
    written[cls] += 1
    print(f"  {fn:34s} {w:3d}x{h:3d}x{l:3d}  {len(cells):6d} blocks")
print(dict(written))
