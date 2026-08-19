import struct, gzip

def _s(v): b=v.encode('utf-8'); return struct.pack('>H',len(b))+b

def write_tag(t, payload):
    if t==1: return struct.pack('>b',payload)
    if t==2: return struct.pack('>h',payload)
    if t==3: return struct.pack('>i',payload)
    if t==4: return struct.pack('>q',payload)
    if t==7: return struct.pack('>i',len(payload))+bytes(payload)
    if t==8: return _s(payload)
    if t==11: return struct.pack('>i',len(payload))+struct.pack('>%di'%len(payload),*payload)
    raise ValueError(t)

def write_compound(d):
    # d: name -> (tag, payload) ; tag 10 -> dict, tag 9 -> (elemtag, [payloads])
    out=b''
    for k,(t,p) in d.items():
        out += struct.pack('>B',t)+_s(k)
        if t==10: out += write_compound(p)
        elif t==9:
            et, items = p
            out += struct.pack('>B',et)+struct.pack('>i',len(items))
            for it in items:
                out += write_compound(it) if et==10 else write_tag(et,it)
        else: out += write_tag(t,p)
    return out + b'\x00'

def save(path, root_name, d):
    body = struct.pack('>B',10)+_s(root_name)+write_compound(d)
    with gzip.GzipFile(path,'wb',mtime=0) as f: f.write(body)

def varints(vals):
    out=bytearray()
    for v in vals:
        while True:
            b = v & 0x7F; v >>= 7
            if v: out.append(b|0x80)
            else: out.append(b); break
    return bytes(out)
