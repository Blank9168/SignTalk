import os; os.environ['TF_USE_LEGACY_KERAS']='1'
import numpy as np, torch
from exp2 import *
X=np.stack([build(D[k],True) for k in keys])
m=fit(X,Y,X[rank==4],Y[rank==4],True,seed=3)
p,c=pred(m,X); print("train acc",np.mean(p==Y),"mean conf",c.mean().round(3))
torch.save(m.state_dict(),'sign_lstm_loc.pt')
# --- TFLite via unrolled Keras (torch->keras weights)
import tensorflow as tf
sd={k:v.numpy() for k,v in m.state_dict().items()}; H=128; F=X.shape[2]
inp=tf.keras.Input((30,F),batch_size=1); x=inp
for l in range(2):
    fw=tf.keras.layers.LSTM(H,return_sequences=True,unroll=True,recurrent_activation='sigmoid')
    bw=tf.keras.layers.LSTM(H,return_sequences=True,go_backwards=True,unroll=True,recurrent_activation='sigmoid')
    bi=tf.keras.layers.Bidirectional(fw,backward_layer=bw); x=bi(x)
    w=lambda sfx:[sd[f'lstm.weight_ih_l{l}{sfx}'].T,sd[f'lstm.weight_hh_l{l}{sfx}'].T,sd[f'lstm.bias_ih_l{l}{sfx}']+sd[f'lstm.bias_hh_l{l}{sfx}']]
    bi.set_weights(w('')+w('_reverse'))
x=tf.keras.layers.Lambda(lambda t:tf.reduce_mean(t,axis=1))(x)
ln=tf.keras.layers.LayerNormalization(epsilon=1e-5); x=ln(x); ln.set_weights([sd['norm.weight'],sd['norm.bias']])
d1=tf.keras.layers.Dense(H,activation='relu'); x=d1(x); d1.set_weights([sd['head.0.weight'].T,sd['head.0.bias']])
d2=tf.keras.layers.Dense(50); out=d2(x); d2.set_weights([sd['head.3.weight'].T,sd['head.3.bias']])
km=tf.keras.Model(inp,out); tfl=tf.lite.TFLiteConverter.from_keras_model(km).convert(); open('sign_lstm_loc.tflite','wb').write(tfl)
it=tf.lite.Interpreter(model_content=tfl); it.allocate_tensors(); i=it.get_input_details()[0]; o=it.get_output_details()[0]; print(i['shape'],o['shape'])
A=np.concatenate([X[:100],np.random.randn(20,30,F).astype(np.float32)]); tl=[]
for a in A: it.set_tensor(i['index'],a[None]); it.invoke(); tl.append(it.get_tensor(o['index'])[0])
with torch.no_grad(): pl=m(torch.tensor(A)).numpy()
tl=np.array(tl); print('parity max diff',np.abs(tl-pl).max(),'argmax match',np.mean(tl.argmax(1)==pl.argmax(1)),'bytes',len(tfl))
# robustness: perturb body reference (person stands closer/further/off-centre)
def perturb(Xs,scale,dx,dy):
    Z=Xs.copy(); e=Z[:,:,126:]; msk=e!=0; e2=e*scale; e2[:,:,0::2]+=dx; e2[:,:,1::2]+=dy; Z[:,:,126:]=e2*msk; return Z
for s,dx,dy in ((1,0,0),(1.15,0.1,0.1),(0.85,-0.1,-0.1),(1,0.3,0)):
    p,_=pred(m,perturb(X,s,dx,dy)); print(f"loc perturbed scale={s} dx={dx} dy={dy}: acc {np.mean(p==Y):.3f}")
