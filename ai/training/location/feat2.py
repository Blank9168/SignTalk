import numpy as np, glob
def resamp(f,T=30):
    i=np.clip(np.round(np.linspace(0,len(f)-1,T)).astype(int),0,len(f)-1); return f[i]
def build(rec, use_loc=True):
    """rec: dict(shape (T,126), cent (T,4) px [Lx,Ly,Rx,Ry], body (T,7) px [nx,ny,lsx,lsy,rsx,rsy,vis]) -> (30,134) or (30,126)"""
    sh,ce,bo=rec['shape'],rec['cent'],rec['body']
    ok=np.isfinite(bo[:,0])&(bo[:,6]>0.5)
    b=np.nanmedian(bo[ok],0) if ok.sum()>=3 else np.nanmedian(bo,0)
    nose=b[0:2]; sc=(b[2:4]+b[4:6])/2; w=max(np.linalg.norm(b[2:4]-b[4:6]),1e-3)
    loc=np.zeros((len(sh),8),np.float32)
    for s in range(2):
        c=ce[:,2*s:2*s+2]; has=np.isfinite(c[:,0])
        loc[has,4*s:4*s+2]=(c[has]-sc)/w
        loc[has,4*s+2:4*s+4]=(c[has]-nose)/w
    keep=np.abs(sh).sum(1)>0
    if keep.sum()<3: keep[:]=True
    f=np.concatenate([sh,loc],1)[keep] if use_loc else sh[keep]
    return resamp(f).astype(np.float32)
def load(pattern='/tmp/diag/loc_part*.npy'):
    D={}
    for f in glob.glob(pattern): D.update(np.load(f,allow_pickle=True).item())
    return D
