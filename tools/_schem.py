import sys, os, gzip, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from _nbt import load

def decode(path):
    n,v=load(path)
    W,H,L=v['Width'],v['Height'],v['Length']
    pal=v['Palette']  # name -> id
    inv={i:k for k,i in pal.items()}
    data=v['BlockData']
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
