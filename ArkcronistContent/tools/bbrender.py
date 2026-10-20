"""Orthographic software renderer for Blockbench (.bbmodel) and Java (.json) models, over a vanilla
player mannequin. Pure Python: z-buffer, nearest texel, alpha test, per-face light.

    python3 bbrender.py out.png model.bbmodel [--views front,threequarter,side,back,backthreequarter]
                         [--scale 9] [--no-body] [--texture N] [--ymin Y]
    python3 bbrender.py out.png model.json --pack DIR [--head]    (--head: a Java item model worn as a helmet)

--ymin crops the frame below a height (21 shows just the head). ItemsAdder-protected PNGs are read
through the plugin: compile java/Unprotect.java against build/classes/java/main and set ARK_CLASSES
to that folder.
"""
import base64, json, math, os, struct, sys, zlib

# ---------------------------------------------------------------- png
def read_png_bytes(data):
    pos, w, h, ctype, depth, idat, plte, trns = 8, 0, 0, 6, 8, b"", None, None
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        if tag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            plte = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        pos += 12 + n
    if depth != 8:
        raise ValueError("only 8-bit PNGs")
    bpp = {6: 4, 2: 3, 3: 1, 0: 1, 4: 2}[ctype]
    raw, stride = zlib.decompress(idat), w * bpp
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else (b if pb <= pc else c))) & 255
        px = []
        for x in range(w):
            q = line[x * bpp:(x + 1) * bpp]
            if ctype == 6:
                px.append(tuple(q))
            elif ctype == 2:
                px.append((q[0], q[1], q[2], 255))
            elif ctype == 3:
                i = q[0]
                al = trns[i] if trns and i < len(trns) else 255
                px.append((plte[3 * i], plte[3 * i + 1], plte[3 * i + 2], al))
            elif ctype == 0:
                px.append((q[0], q[0], q[0], 255))
            else:
                px.append((q[0], q[0], q[0], q[1]))
        rows.append(px)
        prev = line
    return w, h, rows


def read_any_png(path):
    """Plain PNGs here; ItemsAdder-protected ones through the plugin's PngReader (one java call each)."""
    try:
        return read_png_bytes(open(path, "rb").read())
    except Exception:
        import subprocess, tempfile
        out = tempfile.mktemp(suffix=".png")
        here = os.path.dirname(os.path.abspath(__file__))
        subprocess.run(["java", "-cp", here + "/java:" + os.environ.get("ARK_CLASSES", ""), "Unprotect", path, out],
                       check=True, capture_output=True)
        r = read_png_bytes(open(out, "rb").read())
        os.remove(out)
        return r


def write_png(path, w, h, pix):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(pix[y * w + x])

    def chunk(tag, d):
        return struct.pack(">I", len(d)) + tag + d + struct.pack(">I", zlib.crc32(tag + d) & 0xFFFFFFFF)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))

# ---------------------------------------------------------------- math
def rot_x(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)


def rot_y(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)


def rot_z(p, a):
    c, s = math.cos(a), math.sin(a)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2])


def euler_zyx(p, r, origin):
    """Blockbench rotates cubes and groups in ZYX order: v' = Rz Ry Rx (v - o) + o."""
    q = (p[0] - origin[0], p[1] - origin[1], p[2] - origin[2])
    q = rot_x(q, math.radians(r[0]))
    q = rot_y(q, math.radians(r[1]))
    q = rot_z(q, math.radians(r[2]))
    return (q[0] + origin[0], q[1] + origin[1], q[2] + origin[2])


# corners of each face seen from outside: top-left, top-right, bottom-right, bottom-left
def face_corners(a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }


def uv_corners(uv, rotation):
    u0, v0, u1, v1 = uv
    c = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    k = (int(rotation or 0) // 90) % 4
    return c[k:] + c[:k]

# ---------------------------------------------------------------- loading
class Quad:
    __slots__ = ("p", "uv", "tex", "solid")

    def __init__(self, p, uv, tex, solid=None):
        self.p, self.uv, self.tex, self.solid = p, uv, tex, solid


def load_bbmodel(path, tex_index=None):
    d = json.load(open(path))
    res = d.get("resolution", {"width": 16, "height": 16})
    textures = []
    for t in d.get("textures", []):
        src = t.get("source", "")
        if src.startswith("data:image/png;base64,"):
            w, h, rows = read_png_bytes(base64.b64decode(src.split(",", 1)[1]))
            textures.append((w, h, rows, t.get("uv_width", res["width"]), t.get("uv_height", res["height"])))
        else:
            textures.append(None)
    els = {e["uuid"]: e for e in d["elements"]}
    groups = {g["uuid"]: g for g in d.get("groups", [])}
    quads = []

    def walk(nodes, chain):
        for n in nodes:
            if isinstance(n, str):
                e = els.get(n)
                if e and e.get("visibility", True) is not False and e.get("type", "cube") == "cube":
                    add_cube(e, chain)
            else:
                g = groups.get(n.get("uuid"), n)
                if g.get("visibility", True) is False:
                    continue
                walk(n.get("children", []), chain + [(g.get("rotation", [0, 0, 0]), g.get("origin", [0, 0, 0]))])

    def add_cube(e, chain):
        inf = e.get("inflate", 0)
        a = [e["from"][i] - inf for i in range(3)]
        b = [e["to"][i] + inf for i in range(3)]
        corners = face_corners(a, b)
        for f, fc in e.get("faces", {}).items():
            if fc.get("texture") is None or not fc.get("uv"):
                continue
            ti = fc["texture"] if tex_index is None else tex_index
            tex = textures[ti] if isinstance(ti, int) and ti < len(textures) else None
            if tex is None:
                continue
            pts = []
            for p in corners[f]:
                q = euler_zyx(p, e.get("rotation", [0, 0, 0]), e.get("origin", [0, 0, 0]))
                for r, o in reversed(chain):
                    q = euler_zyx(q, r, o)
                pts.append(q)
            w, h, rows, uw, uh = tex
            uvs = [(u * w / uw, v * h / uh) for u, v in uv_corners(fc["uv"], fc.get("rotation"))]
            quads.append(Quad(pts, uvs, tex))

    walk(d.get("outliner", []), [])
    return quads


def load_java(path, pack, head=False):
    """A Java block/item model; textures resolved under pack/assets. head: worn as a helmet (display.head)."""
    m = json.load(open(path))
    seen = 0
    while "elements" not in m and "parent" in m and seen < 8:
        ns, p = (m["parent"].split(":", 1) + [None])[:2] if ":" in m["parent"] else ("minecraft", m["parent"])
        parent = json.load(open(os.path.join(pack, "assets", ns, "models", p + ".json")))
        parent.setdefault("textures", {}).update(m.get("textures", {}))
        m, seen = parent, seen + 1
    texmap = m.get("textures", {})

    def tex_of(ref):
        k = 0
        while ref.startswith("#") and k < 8:
            ref, k = texmap.get(ref[1:], ""), k + 1
        ns, p = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        f = os.path.join(pack, "assets", ns, "textures", p + ".png")
        if not os.path.exists(f):
            return None
        return (*read_any_png(f), 16, 16)
    cache = {}
    disp = m.get("display", {}).get("head", {}) if head else {}
    sc = disp.get("scale", [1, 1, 1])
    tr = disp.get("translation", [0, 0, 0])
    rt = disp.get("rotation", [0, 0, 0])
    quads = []
    for e in m.get("elements", []):
        corners = face_corners(e["from"], e["to"])
        r = e.get("rotation")
        for f, fc in e.get("faces", {}).items():
            ref = fc.get("texture", "")
            if ref not in cache:
                cache[ref] = tex_of(ref)
            tex = cache[ref]
            if tex is None:
                continue
            w, h, rows, _, _ = tex
            uv = fc.get("uv") or default_uv(f, e["from"], e["to"])
            frames = h // w if h > w and h % w == 0 else 1
            uvs = [(u * w / 16, v * (h / frames) / 16) for u, v in uv_corners(uv, fc.get("rotation"))]
            pts = []
            for p in corners[f]:
                q = p
                if r:
                    ang = [0, 0, 0]
                    ang["xyz".index(r["axis"])] = r["angle"]
                    q = euler_zyx(q, ang, r["origin"])
                if head:
                    # item space (0..16) -> display.head (scale, rotate, translate) -> worn at 0.625 on the
                    # head's centre; the model's north is the wearer's face (-z here)
                    q = ((q[0] - 8) * sc[0], (q[1] - 8) * sc[1], (q[2] - 8) * sc[2])
                    q = euler_zyx(q, rt, (0, 0, 0))
                    q = (q[0] + tr[0], q[1] + tr[1], q[2] + tr[2])
                    q = (q[0] * 0.625, q[1] * 0.625 + 28, q[2] * 0.625)
                pts.append(q)
            quads.append(Quad(pts, uvs, tex))
    return quads


def default_uv(f, a, b):
    if f in ("north", "south"):
        return [a[0], 16 - b[1], b[0], 16 - a[1]]
    if f in ("east", "west"):
        return [a[2], 16 - b[1], b[2], 16 - a[1]]
    return [a[0], a[2], b[0], b[2]]


SKIN = (150, 156, 170, 255)
def mannequin():
    boxes = [((-4, 24, -4), (4, 32, 4)), ((-4, 12, -2), (4, 24, 2)), ((4, 12, -2), (8, 24, 2)),
             ((-8, 12, -2), (-4, 24, 2)), ((0, 0, -2), (4, 12, 2)), ((-4, 0, -2), (0, 12, 2))]
    qs = []
    for a, b in boxes:
        for f, pts in face_corners(a, b).items():
            qs.append(Quad(pts, None, None, SKIN))
    return qs

# ---------------------------------------------------------------- raster
LIGHT = {"x": 0.62, "y": 1.0, "z": 0.8}


def render(quads, yaw, pitch, scale, size=None, pad=3):
    """yaw 0: looking at the model's front (-z side) from -z."""
    ya, pa = math.radians(yaw), math.radians(pitch)

    def view(p):
        # camera looks along +z at yaw 0; the viewer's right is -x
        q = rot_y(p, -ya)
        q = rot_x(q, -pa)
        return (-q[0], q[1], q[2])
    vq = []
    for qd in quads:
        vp = [view(p) for p in qd.p]
        vq.append((qd, vp))
    xs = [p[0] for _, vp in vq for p in vp]
    ys = [p[1] for _, vp in vq for p in vp]
    if size:
        x0, x1, y0, y1 = size
    else:
        x0, x1, y0, y1 = min(xs) - pad, max(xs) + pad, min(ys) - pad, max(ys) + pad
    W, H = int((x1 - x0) * scale) + 1, int((y1 - y0) * scale) + 1
    zb = [1e9] * (W * H)
    pix = [(0, 0, 0, 0)] * (W * H)
    for qd, vp in vq:
        # face normal in view space -> simple directional light
        ax, ay, az = (vp[1][i] - vp[0][i] for i in range(3))
        bx, by, bz = (vp[3][i] - vp[0][i] for i in range(3))
        nx, ny, nz = ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx
        ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
        nx, ny, nz = nx / ln, ny / ln, nz / ln
        light = 0.55 + 0.45 * max(0.0, 0.35 * abs(nx) + 0.75 * max(ny, 0) + 0.85 * abs(nz))
        light = min(1.0, light) - (0.18 if ny < -0.5 else 0)
        sp = [((p[0] - x0) * scale, (y1 - p[1]) * scale, p[2]) for p in vp]
        for tri in ((0, 1, 2), (0, 2, 3)):
            raster_tri(sp, tri, qd, light, W, H, zb, pix)
    return W, H, pix


def raster_tri(sp, tri, qd, light, W, H, zb, pix):
    (xa, ya, za), (xb, yb, zb_), (xc, yc, zc) = sp[tri[0]], sp[tri[1]], sp[tri[2]]
    den = (yb - yc) * (xa - xc) + (xc - xb) * (ya - yc)
    if abs(den) < 1e-9:
        return
    minx, maxx = max(0, int(min(xa, xb, xc))), min(W - 1, int(max(xa, xb, xc)) + 1)
    miny, maxy = max(0, int(min(ya, yb, yc))), min(H - 1, int(max(ya, yb, yc)) + 1)
    if qd.uv:
        ua, ub, uc = qd.uv[tri[0]], qd.uv[tri[1]], qd.uv[tri[2]]
        tw, th, rows = qd.tex[0], qd.tex[1], qd.tex[2]
    inv = 1.0 / den
    for py in range(miny, maxy + 1):
        cy = py + 0.5
        for px in range(minx, maxx + 1):
            cx = px + 0.5
            l1 = ((yb - yc) * (cx - xc) + (xc - xb) * (cy - yc)) * inv
            l2 = ((yc - ya) * (cx - xc) + (xa - xc) * (cy - yc)) * inv
            l3 = 1 - l1 - l2
            if l1 < -1e-6 or l2 < -1e-6 or l3 < -1e-6:
                continue
            z = l1 * za + l2 * zb_ + l3 * zc
            i = py * W + px
            if z >= zb[i]:
                continue
            if qd.uv:
                u = l1 * ua[0] + l2 * ub[0] + l3 * uc[0]
                v = l1 * ua[1] + l2 * ub[1] + l3 * uc[1]
                tx = min(tw - 1, max(0, int(u)))
                ty = min(th - 1, max(0, int(v)))
                c = rows[ty][tx]
                if c[3] < 128:
                    continue
            else:
                c = qd.solid
            zb[i] = z
            pix[i] = (int(c[0] * light), int(c[1] * light), int(c[2] * light), 255)


def compose(images, gap=12, bg=(32, 34, 40, 255)):
    W = sum(w for w, _, _ in images) + gap * (len(images) + 1)
    H = max(h for _, h, _ in images) + 2 * gap
    out = [bg] * (W * H)
    x = gap
    for w, h, pix in images:
        top = H - gap - h
        for yy in range(h):
            for xx in range(w):
                c = pix[yy * w + xx]
                if c[3]:
                    out[(top + yy) * W + x + xx] = c
        x += w + gap
    return W, H, out


VIEWS = {"front": (0, 0), "threequarter": (-35, 12), "side": (-90, 0), "back": (180, 0), "backthreequarter": (145, 12)}

if __name__ == "__main__":
    args = sys.argv[1:]
    out, model = args[0], args[1]
    opt = {args[i][2:]: (args[i + 1] if i + 1 < len(args) and not args[i + 1].startswith("--") else True)
           for i in range(2, len(args)) if args[i].startswith("--")}
    scale = float(opt.get("scale", 9))
    views = str(opt.get("views", "front,threequarter,side,back")).split(",")
    if model.endswith(".bbmodel"):
        quads = load_bbmodel(model, int(opt["texture"]) if "texture" in opt else None)
    else:
        quads = load_java(model, opt["pack"], head=bool(opt.get("head")))
    if not opt.get("no-body"):
        quads = mannequin() + quads
    # one frame for all views so the figures share a scale and a ground line
    ext = 0
    for qd in quads:
        for p in qd.p:
            ext = max(ext, abs(p[0]), abs(p[2]))
    ys = [p[1] for qd in quads for p in qd.p]
    frame = (-ext - 2, ext + 2, max(min(ys), float(opt.get("ymin", -99))) - 2, max(ys) + 2)
    if "ymin" in opt:
        # only what lies above ymin sets the width of the frame
        ext = max([0] + [max(abs(p[0]), abs(p[2])) for qd in quads for p in qd.p if p[1] >= float(opt["ymin"])])
        frame = (-ext - 2, ext + 2) + frame[2:]
    imgs = [render(quads, *VIEWS[v], scale, size=frame) for v in views]
    W, H, pix = compose(imgs)
    write_png(out, W, H, pix)
    print(out, W, H, len(quads), "faces")
