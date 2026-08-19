import gzip, struct, sys, json

class R:
    def __init__(self, b): self.b=b; self.i=0
    def u1(self): v=self.b[self.i]; self.i+=1; return v
    def i1(self): v=struct.unpack_from('>b',self.b,self.i)[0]; self.i+=1; return v
    def i2(self): v=struct.unpack_from('>h',self.b,self.i)[0]; self.i+=2; return v
    def u2(self): v=struct.unpack_from('>H',self.b,self.i)[0]; self.i+=2; return v
    def i4(self): v=struct.unpack_from('>i',self.b,self.i)[0]; self.i+=4; return v
    def i8(self): v=struct.unpack_from('>q',self.b,self.i)[0]; self.i+=8; return v
    def f4(self): v=struct.unpack_from('>f',self.b,self.i)[0]; self.i+=4; return v
    def f8(self): v=struct.unpack_from('>d',self.b,self.i)[0]; self.i+=8; return v
    def s(self):
        n=self.u2(); v=self.b[self.i:self.i+n].decode('utf-8','replace'); self.i+=n; return v
    def payload(self,t):
        if t==0: return None
        if t==1: return self.i1()
        if t==2: return self.i2()
        if t==3: return self.i4()
        if t==4: return self.i8()
        if t==5: return self.f4()
        if t==6: return self.f8()
        if t==7:
            n=self.i4(); v=self.b[self.i:self.i+n]; self.i+=n; return v
        if t==8: return self.s()
        if t==9:
            et=self.u1(); n=self.i4(); return [self.payload(et) for _ in range(n)]
        if t==10:
            d={}
            while True:
                tt=self.u1()
                if tt==0: break
                k=self.s(); d[k]=self.payload(tt)
            return d
        if t==11:
            n=self.i4(); v=list(struct.unpack_from('>%di'%n,self.b,self.i)); self.i+=4*n; return v
        if t==12:
            n=self.i4(); v=list(struct.unpack_from('>%dq'%n,self.b,self.i)); self.i+=8*n; return v
        raise ValueError('tag %d at %d'%(t,self.i))

def load(path):
    raw=open(path,'rb').read()
    if raw[:2]==b'\x1f\x8b': raw=gzip.decompress(raw)
    r=R(raw); t=r.u1(); name=r.s(); return name, r.payload(t)

def summarize(v, depth=0, key=''):
    pad='  '*depth
    if isinstance(v,dict):
        print(f'{pad}{key} (compound, {len(v)} entries)')
        for k,x in v.items(): summarize(x,depth+1,k)
    elif isinstance(v,bytes):
        print(f'{pad}{key} (bytes len={len(v)})')
    elif isinstance(v,list):
        print(f'{pad}{key} (list len={len(v)}) first={v[0] if v else None!r:.200}')
    else:
        print(f'{pad}{key} = {v!r:.200}')

if __name__=='__main__':
    n,v=load(sys.argv[1]); print('root name=',repr(n)); summarize(v)
