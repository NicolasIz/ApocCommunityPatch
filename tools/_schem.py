import sys, os, gzip, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _nbt import load

def decode(path):
    n,v=load(path)
    # Sponge v2 keeps everything in the root; v3 nests it under 'Schematic', with the palette and
    # the block data one level further down again under 'Blocks'.
    v = v.get('Schematic', v)
    W,H,L=v['Width'],v['Height'],v['Length']
    blocks = v.get('Blocks', v)
    pal=blocks['Palette']  # name -> id
    inv={i:k for k,i in pal.items()}
    data=blocks.get('Data')
    if data is None:
        data=blocks['BlockData']
    out=[0]*(W*H*L)
    i=0; idx=0
    b=data
    while i<len(b):
        val=0; shift=0
        while True:
            c=b[i]; i+=1
            val |= (c & 0x7F) << shift
            if not (c & 0x80): break
            shift += 7
        out[idx]=val; idx+=1
    assert idx==W*H*L, (idx, W*H*L)
    return v, W,H,L, inv, out

def at(out,W,H,L,x,y,z): return out[y*W*L + z*W + x]
