import os,sys,glob,numpy as np,cv2
import mediapipe as mp
from mediapipe.tasks import python as mpp
from mediapipe.tasks.python import vision
TASK='/tmp/diag/hand_landmarker.task'
def norm_hand(c):
    c=c-c[0]; s=np.linalg.norm(c[9,:2]); return (c/s if s>=1e-4 else c).flatten()
def run(path):
    o=vision.HandLandmarkerOptions(base_options=mpp.BaseOptions(model_asset_path=TASK),running_mode=vision.RunningMode.VIDEO,num_hands=2,
        min_hand_detection_confidence=.5,min_hand_presence_confidence=.5,min_tracking_confidence=.5)
    lmk=vision.HandLandmarker.create_from_options(o)
    pose=mp.solutions.pose.Pose(static_image_mode=False,model_complexity=1,min_detection_confidence=.5,min_tracking_confidence=.5)
    cap=cv2.VideoCapture(path); fps=cap.get(cv2.CAP_PROP_FPS) or 30
    shape=[];cent=[];body=[];i=0
    while True:
        ok,fr=cap.read()
        if not ok: break
        h,w=fr.shape[:2]; rgb=cv2.cvtColor(fr,cv2.COLOR_BGR2RGB)
        res=lmk.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb),int(i*1000/fps)); i+=1
        L=np.zeros(63,np.float32);R=np.zeros(63,np.float32);cL=np.full(2,np.nan,np.float32);cR=np.full(2,np.nan,np.float32)
        n=len(res.hand_landmarks)
        def pts(k): return np.array([[p.x*w,p.y*h,p.z*w] for p in res.hand_landmarks[k]],np.float32)
        if n==1:
            P=pts(0); Pn=np.array([[p.x,p.y,p.z] for p in res.hand_landmarks[0]],np.float32); L=norm_hand(Pn); cL=P[:,:2].mean(0)
        elif n>=2:
            for k in range(n):
                P=pts(k); Pn=np.array([[p.x,p.y,p.z] for p in res.hand_landmarks[k]],np.float32); v=norm_hand(Pn)
                if res.handedness[k][0].category_name.lower()=='left': L=v;cL=P[:,:2].mean(0)
                else: R=v;cR=P[:,:2].mean(0)
        pr=pose.process(rgb)
        if pr.pose_landmarks:
            lm=pr.pose_landmarks.landmark
            body.append([lm[0].x*w,lm[0].y*h,lm[11].x*w,lm[11].y*h,lm[12].x*w,lm[12].y*h,min(lm[0].visibility,lm[11].visibility,lm[12].visibility)])
        else: body.append([np.nan]*7)
        shape.append(np.concatenate([L,R]));cent.append(np.concatenate([cL,cR]))
    cap.release();lmk.close();pose.close()
    return dict(shape=np.array(shape,np.float32),cent=np.array(cent,np.float32),body=np.array(body,np.float32))
if __name__=='__main__':
    part,n=int(sys.argv[1]),int(sys.argv[2]); files=sorted(glob.glob('/tmp/diag/small/*/*.mp4'))[part::n]; out={}
    for f in files:
        out[f]=run(f); b=out[f]['body']; print(f.split('/')[-2],os.path.basename(f),len(b),int(np.isfinite(b[:,0]).sum()),flush=True)
    np.save(f'/tmp/diag/loc_part{part}.npy',out,allow_pickle=True)
