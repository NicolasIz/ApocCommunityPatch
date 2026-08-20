"""
Splits a WorldEdit "bundle" schematic - many builds exported side by side on one grass
platform - into one .schem per build, ready to drop into prefabs/.

Each connected clump of blocks above the platform becomes its own file, named after what
it is made of, because the file name is what the generator reads to decide where a prefab
belongs:

    <size>_<family>_<detail>_<nn>.schem

Usage:
    python3 tools/split_schem_bundle.py trees     <bundle.schem> <output-dir> [label]
    python3 tools/split_schem_bundle.py buildings <bundle.schem> <output-root> [label]

The optional label goes into every file name, which is how two bundles of the same kind of
thing are kept apart - and, because the words in a file name are the prefab's tags, it is
also how a biome or a preset could later ask for that flavour by name.

In 'trees' mode every clump lands in one folder, sorted into families by its leaves.
In 'buildings' mode the clumps are sorted by what they are built of and written into the
prefab folder each one belongs in - houses/, castles/, towers/, temples/ - which is the
same folder layout the plugin reads at run time.

The generator never runs this. It exists so the bundled prefabs can be regenerated, and so
a new bundle can be turned into prefabs the same way.
"""

import sys, os, hashlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _schem import decode
from _write_nbt import save, varints
from collections import deque, Counter

if len(sys.argv) not in (4, 5) or sys.argv[1] not in ("trees", "buildings"):
    raise SystemExit(__doc__)
MODE = sys.argv[1]
BUNDLE = sys.argv[2]
OUT = sys.argv[3]
LABEL = sys.argv[4] if len(sys.argv) == 5 else 'medieval'
os.makedirs(OUT, exist_ok=True)

def emit(path, w,h,l, order, data, dataversion=3120):
    d={
      'Version': (3, 2),
      'DataVersion': (3, dataversion),
      'Width': (2, w), 'Height': (2, h), 'Length': (2, l),
      'Offset': (11, [0,0,0]),
      'PaletteMax': (3, len(order)),
      'Palette': (10, {n:(3,i) for i,n in enumerate(order)}),
      'BlockData': (7, varints(data)),
    }
    save(path, 'Schematic', d)

v,W,H,L,inv,out = decode(BUNDLE)
AIR=next(i for i,n in inv.items() if n=='minecraft:air')
occ=bytearray(W*L)
for y in range(1,H):
    base=y*W*L
    for i in range(W*L):
        if out[base+i]!=AIR: occ[i]=1
seen=bytearray(W*L); comps=[]
for z in range(L):
  for x in range(W):
    i=z*W+x
    if occ[i] and not seen[i]:
        q=deque([(x,z)]); seen[i]=1; cells=[]
        while q:
            cx,cz=q.popleft(); cells.append((cx,cz))
            for dz in (-1,0,1):
              for dx in (-1,0,1):
                nx,nz=cx+dx,cz+dz
                if 0<=nx<W and 0<=nz<L:
                    j=nz*W+nx
                    if occ[j] and not seen[j]: seen[j]=1; q.append((nx,nz))
        comps.append(cells)

KINDS=('dark_oak','pale_oak','oak','birch','spruce','jungle','acacia','mangrove','cherry','azalea')

def classify(names):
    """Sorts a tree into a family and gives it the species tags a biome can ask for.

    Thresholds are shares of the tree rather than block counts, because the same design
    shows up in a bundle at every scale and an absolute cut-off only fits one of them.
    """
    leaf=Counter(); wood=Counter(); other=Counter()
    for n,cnt in names.items():
        base=n.split('[')[0].replace('minecraft:','')
        if base.endswith('leaves'):
            kind=None
            for k in KINDS:
                if base.startswith(k+'_'): kind=k; break
            if 'azalea' in base: kind='azalea'
            leaf[kind or base]+=cnt
        elif base.endswith(('_log','_wood','_stem','_hyphae')):
            for k in KINDS:
                if base.startswith(k+'_'): wood[k]+=cnt; break
        else:
            other[base]+=cnt
    total = sum(leaf.values()) + sum(wood.values()) + sum(other.values())
    glass = sum(c for b, c in other.items() if 'stained_glass' in b)
    amethyst = sum(c for b, c in other.items() if 'amethyst' in b)
    if not leaf:
        if amethyst > total * 0.15: return ['crystal', 'amethyst']
        if glass > total * 0.15: return ['autumn'] + [w for w, _ in wood.most_common(1)]
        if not wood:
            # No leaves, no trunk, no fantasy canopy: this is scenery off the platform - a
            # boulder or a bit of path - and not a tree at all.
            return []
        return ['dead'] + [w for w, _ in wood.most_common(1)]
    top=leaf.most_common(2)
    tags=[k for k,c in top if c > top[0][1]*0.3]
    return tags

def size_class(w,h,l):
    spread=max(w,l)
    if spread>=26: return 'giant'
    if spread>=18: return 'large'
    if spread>=10: return 'medium'
    return 'small'

def trunk_clusters(cells, ylo, yhi):
    """Groups a clump's columns by the separate trunks holding it up.

    Two trees whose crowns touch are one blob seen from above, which is how a pair of
    neighbours in a bundle ends up merged into a single oversized prefab. Their trunks are
    still apart down at the base, so the base is what decides how many trees there are.
    """
    base = {(x, z) for (x, z) in cells
            if any(out[y * W * L + z * W + x] != AIR for y in range(ylo, yhi))}
    seen = set()
    clusters = []
    for start in sorted(base):
        if start in seen:
            continue
        stack = [start]
        seen.add(start)
        group = []
        while stack:
            cx, cz = stack.pop()
            group.append((cx, cz))
            for dz in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nxt = (cx + dx, cz + dz)
                    if nxt in base and nxt not in seen:
                        seen.add(nxt)
                        stack.append(nxt)
        clusters.append(group)
    return clusters


def split_by_trunk(cells):
    """Splits a merged clump into one group of columns per trunk, nearest trunk wins."""
    clusters = trunk_clusters(cells, 1, 4)
    if len(clusters) < 2:
        return [cells]
    centres = []
    for group in clusters:
        centres.append((sum(p[0] for p in group) / len(group),
                        sum(p[1] for p in group) / len(group)))
    # Trunks closer together than this are one tree with buttress roots, not two trees.
    merged = True
    while merged and len(centres) > 1:
        merged = False
        for i in range(len(centres)):
            for j in range(i + 1, len(centres)):
                if abs(centres[i][0] - centres[j][0]) < 4 and abs(centres[i][1] - centres[j][1]) < 4:
                    clusters[i] = clusters[i] + clusters[j]
                    centres[i] = (sum(p[0] for p in clusters[i]) / len(clusters[i]),
                                  sum(p[1] for p in clusters[i]) / len(clusters[i]))
                    del clusters[j]
                    del centres[j]
                    merged = True
                    break
            if merged:
                break
    if len(centres) < 2:
        return [cells]
    groups = [[] for _ in centres]
    for (x, z) in cells:
        best = min(range(len(centres)),
                   key=lambda i: (x - centres[i][0]) ** 2 + (z - centres[i][1]) ** 2)
        groups[best].append((x, z))
    return [g for g in groups if g]


if MODE == 'trees':
    comps = [part for clump in comps for part in split_by_trunk(clump)]

records=[]
for cells in comps:
    xs=[p[0] for p in cells]; zs=[p[1] for p in cells]
    x0,x1,z0,z1=min(xs),max(xs),min(zs),max(zs)
    cellset=set(cells)
    ys=[y for y in range(1,H) if any(out[y*W*L+z*W+x]!=AIR for (x,z) in cells)]
    y0,y1=min(ys),max(ys)
    w,h,l = x1-x0+1, y1-y0+1, z1-z0+1
    names=Counter(); localpal={}; order=[]
    data=[0]*(w*h*l)
    for y in range(y0,y1+1):
        for z in range(z0,z1+1):
            for x in range(x0,x1+1):
                b = out[y*W*L+z*W+x] if (x,z) in cellset else AIR
                n = inv[b]
                if n not in localpal: localpal[n]=len(order); order.append(n)
                data[(y-y0)*w*l + (z-z0)*w + (x-x0)] = localpal[n]
                if b!=AIR: names[n]+=1
    digest=hashlib.sha1((','.join(order)+'|'+','.join(map(str,data))).encode()).hexdigest()[:12]
    tags = classify(names)
    if MODE == 'trees' and not tags:
        continue
    records.append(dict(w=w,h=h,l=l,nonair=sum(names.values()),tags=tags,
                        names=names,order=order,data=data,digest=digest))

uniq={}
for r in records: uniq.setdefault(r['digest'], r)
recs=sorted(uniq.values(), key=lambda r:(-r['nonair']))

def rotation_key(r):
    """Identifies a build regardless of which way round it was displayed.

    A showcase usually presents the same building twice, turned ninety degrees, and the
    generator rotates prefabs itself - so keeping both would only make that design turn up
    twice as often as its neighbours. Comparing the tally of block types, with the facings
    dropped and the footprint sorted, matches a build against its own rotations without
    needing to rotate every block state to find out.
    """
    shape = (min(r['w'], r['l']), max(r['w'], r['l']), r['h'])
    tally = Counter()
    for name, count in r['names'].items():
        tally[name.split('[')[0]] += count
    return (shape, tuple(sorted(tally.items())))

seen_rotations = {}
deduped = []
for r in recs:
    key = rotation_key(r)
    if key in seen_rotations:
        continue
    seen_rotations[key] = True
    deduped.append(r)
if len(deduped) != len(recs):
    print('rotaciones duplicadas descartadas: %d' % (len(recs) - len(deduped)))
recs = deduped

PLASTER = ('smooth_sandstone', 'white_wool', 'white_terracotta', 'bone_block')
MASONRY = ('stone', 'andesite', 'stone_bricks', 'cobblestone', 'bricks', 'deepslate',
           'polished_andesite', 'diorite', 'granite')

def building_kind(names, w, h, l):
    """Sorts a building by what it is made of, which is what a builder's style comes down to."""
    tally = Counter()
    for n, c in names.items():
        base = n.split('[')[0].replace('minecraft:', '')
        for group, members in (('plaster', PLASTER), ('masonry', MASONRY)):
            if any(base == m or base.startswith(m + '_') for m in members):
                tally[group] += c
        if 'stained_glass' in base:
            tally['glass'] += c
        if base.endswith(('_planks', '_wood', '_log', '_stairs', '_slab')) and 'stone' not in base \
                and 'brick' not in base and 'andesite' not in base:
            tally['timber'] += c

    # A tall building with a stained glass window is a church, whatever else it is made of.
    if tally['glass'] > 30 and h >= 24:
        return 'temples', 'church'
    # A "house" the size of a small castle is a manor, and putting it in with the cottages
    # would space a whole village out to fit it.
    if max(w, l) > 30:
        return 'castles', 'manor'
    # Plaster panels between timber: the half-timbered house this pack is mostly made of.
    if tally['plaster'] > 0:
        return 'houses', 'house'
    if tally['masonry'] > tally['timber']:
        # Bare masonry. A keep is chunky in both directions; anything long and thin is a
        # gatehouse or a turret, so it goes with the towers.
        return ('castles', 'keep') if min(w, l) >= 16 else ('towers', 'tower')
    return 'houses', 'house'

counts = Counter()
for r in recs:
    size = size_class(r['w'], r['h'], r['l'])
    if MODE == 'trees':
        folder = ''
        key = '_'.join([size] + r['tags'])
    else:
        folder, detail = building_kind(r['names'], r['w'], r['h'], r['l'])
        key = '%s_%s_%s' % (size, LABEL, detail)
    counts[key] += 1
    name = '%s_%02d' % (key, counts[key])
    directory = os.path.join(OUT, folder) if folder else OUT
    os.makedirs(directory, exist_ok=True)
    emit(os.path.join(directory, name + '.schem'), r['w'], r['h'], r['l'], r['order'], r['data'])
    print('%-12s %-34s %3dx%3dx%3d %6d' % (folder or 'trees', name, r['w'], r['h'], r['l'], r['nonair']))
print(dict(counts))
