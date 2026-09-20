import numpy as np, torch, torch.nn as nn, collections, sys
from feat2 import *
sys.path.insert(0,'/tmp/diag')
import torch.nn as nn
class Net(nn.Module):
    def __init__(s,inp,nc=50,H=128,dr=.3):
        super().__init__(); s.lstm=nn.LSTM(inp,H,2,batch_first=True,bidirectional=True,dropout=dr); s.norm=nn.LayerNorm(2*H)
        s.head=nn.Sequential(nn.Linear(2*H,H),nn.ReLU(),nn.Dropout(dr),nn.Linear(H,nc))
    def forward(s,x): o,_=s.lstm(x); return s.head(s.norm(o.mean(1)))
D=load(); keys=sorted(D)
order=list(np.load('/tmp/diag/bundle.npz')['order'])
Y=np.array([order.index(k.split('/')[-2]) for k in keys]); ID=np.array([int(k.split('_')[-1].split('.')[0]) for k in keys])
rank=np.zeros(len(Y),int)
for c in set(Y):
    ix=sorted([i for i in range(len(Y)) if Y[i]==c],key=lambda i:ID[i])
    for r,i in enumerate(ix): rank[i]=r
L=63
def mirror(s,loc):
    o=s.copy(); o[:,0:126:3]*=-1; l=o[:,:L].copy(); r=o[:,L:126].copy(); o[:,:L]=r; o[:,L:126]=l
    if loc:
        e=o[:,126:].copy(); e[:,0::2]*=-1; o[:,126:]=np.concatenate([e[:,4:],e[:,:4]],1)
    return o
def aug(s,loc):
    s=s.copy()
    if np.random.rand()<.3: s=mirror(s,loc)
    if np.random.rand()<.5:
        t=np.deg2rad(np.random.uniform(-10,10)); c,sn=np.cos(t),np.sin(t); h=s[:,:126].reshape(len(s),-1,3); x,y=h[...,0].copy(),h[...,1].copy(); h[...,0]=x*c-y*sn; h[...,1]=x*sn+y*c; s[:,:126]=h.reshape(len(s),-1)
    if np.random.rand()<.5:
        h=s[:,:126].reshape(len(s),-1,3); h[...,:2]*=np.random.uniform(.9,1.1); s[:,:126]=h.reshape(len(s),-1)
    if np.random.rand()<.7: s[:,:126]+=np.random.normal(0,.01,(len(s),126)).astype(np.float32)
    if loc:
        e=s[:,126:]; m=e!=0
        shift=np.random.uniform(-.15,.15,2)  # person stands slightly differently
        e2=e.copy(); e2[:,0::2]+=shift[0]; e2[:,1::2]+=shift[1]; e2=e2*m; e2+=np.random.normal(0,.03,e.shape)*m; s[:,126:]=e2
    return s
def fit(Xtr,ytr,Xv,yv,loc,seed=0,mult=6,epochs=60):
    np.random.seed(seed); torch.manual_seed(seed); m=Net(Xtr.shape[2]); opt=torch.optim.Adam(m.parameters(),1e-3,weight_decay=1e-4)
    sch=torch.optim.lr_scheduler.ReduceLROnPlateau(opt,'min',factor=.5,patience=5); ce=nn.CrossEntropyLoss(); best=(9,None); bad=0
    for ep in range(epochs):
        m.train(); data=[(p,c) for p,c in zip(Xtr,ytr)]+[(aug(p,loc),c) for _ in range(mult) for p,c in zip(Xtr,ytr)]
        idx=np.random.permutation(len(data))
        for b in range(0,len(idx),32):
            bi=idx[b:b+32]; xb=torch.tensor(np.stack([data[i][0] for i in bi])); yb=torch.tensor([data[i][1] for i in bi]); opt.zero_grad(); ce(m(xb),yb).backward(); opt.step()
        m.eval()
        with torch.no_grad(): vl=ce(m(torch.tensor(Xv)),torch.tensor(yv)).item()
        sch.step(vl)
        if vl<best[0]-1e-4: best=(vl,{k:v.clone() for k,v in m.state_dict().items()}); bad=0
        else:
            bad+=1
            if bad>=10: break
    m.load_state_dict(best[1]); m.eval(); return m
def pred(m,X):
    with torch.no_grad(): p=torch.softmax(m(torch.tensor(X)),1).numpy()
    return p.argmax(1),p.max(1)
if __name__=='__main__':
    res={}
    for loc in (False,True):
        X=np.stack([build(D[k],loc) for k in keys]); errs=collections.Counter(); accs=[]
        for f in range(5):
            te=rank==f; tr=~te
            m=fit(X[tr],Y[tr],X[te],Y[te],loc); pr,c=pred(m,X[te]); accs.append(np.mean(pr==Y[te]))
            for t,p_ in zip(Y[te],pr):
                if t!=p_: errs[(order[t],order[p_])]+=1
        print(f"{'shape+location' if loc else 'shape only    '}: 5-fold (leave-one-clip-per-sign-out) acc = {np.mean(accs):.3f}  folds={[round(a,2) for a in accs]}")
        print("   top errors:",errs.most_common(8))
