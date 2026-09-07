"""
Live webcam test: run this on YOUR machine (not in a cloud sandbox -- it
needs a real camera). Shows a rolling prediction with majority-vote temporal
smoothing, same idea as the original SignTalk evaluation/predict.py.

Usage:
    python predict.py

Requires (install locally):
    pip install "mediapipe==0.10.14" opencv-python torch numpy
    (see predict_video.py for why mediapipe is pinned)

Press 'q' to quit.
"""

import os
import sys
import json
from collections import deque, Counter

import cv2
import numpy as np
import torch

sys.path.append(os.path.join(os.path.dirname(__file__), "..", "dataset"))
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "models"))

from landmarks import HandLandmarkExtractor
from model import SignLSTM

MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models")
LABELS_PATH = os.path.join(os.path.dirname(__file__), "..", "dataset", "labels_50.json")
# NOTE (2026-09-06): repointed from labels_105.json to labels_50.json now that
# sign_lstm.pt has been retrained on the 50-class bilingual FSL+ASL vocabulary
# (all 50 classes have real data -- see proposal-notes.md's "VOCABULARY SWAP"
# and "AI-side implementation" sections). The old 105-class labels/model are
# still available as labels_105.json if anyone wants to go back to that.
SEQUENCE_LENGTH = 30
VOTE_WINDOW = 15          # number of recent predictions to majority-vote over
CONFIDENCE_THRESHOLD = 0.6


def load_label_order():
    with open(LABELS_PATH) as f:
        raw = json.load(f)
    return [raw[str(i)]["slug"] for i in range(len(raw))]


LABEL_ORDER = load_label_order()


def load_model():
    model = SignLSTM(num_classes=len(LABEL_ORDER))
    model.load_state_dict(torch.load(os.path.join(MODELS_DIR, "sign_lstm.pt"), map_location="cpu"))
    model.eval()
    return model


def main():
    model = load_model()
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("Could not open webcam (index 0). Try a different camera index.")
        sys.exit(1)

    frame_buffer = deque(maxlen=SEQUENCE_LENGTH)
    vote_buffer = deque(maxlen=VOTE_WINDOW)
    last_logged = None
    frame_count = 0

    print(f"Press 'q' to quit. Recognizing {len(LABEL_ORDER)} FSL-105 signs, all backed "
          "by real Filipino Sign Language video data -- see README for the full list "
          "and known limitations (small per-class sample count, single signer).\n"
          "Per-frame raw predictions are printed to this console below, and whenever "
          "the smoothed majority label changes -- copy/paste this output if you're "
          "reporting a bug.\n")

    with HandLandmarkExtractor() as extractor:
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            frame_count += 1

            # IMPORTANT: extract landmarks from the RAW (unflipped) frame.
            # The FSL-105 training clips are plain recordings, not mirrored
            # "selfie view" video -- so if we fed the model a mirrored frame,
            # left/right-handed gestures would come out flipped relative to
            # what it was trained on. We only flip a *copy* for on-screen
            # display, after feature extraction, so it still feels natural
            # to look at while not messing with what the model sees.
            features = extractor.extract(frame)
            hand_detected = bool(np.any(features != 0))
            frame_buffer.append(features)
            frame = cv2.flip(frame, 1)  # display-only, after extraction

            display_text = "collecting frames..."
            if len(frame_buffer) == SEQUENCE_LENGTH:
                seq = np.stack(frame_buffer, axis=0).astype(np.float32)
                x = torch.from_numpy(seq).unsqueeze(0)
                with torch.no_grad():
                    probs = torch.softmax(model(x), dim=1)[0]
                pred_idx = probs.argmax().item()
                confidence = probs[pred_idx].item()

                if confidence >= CONFIDENCE_THRESHOLD:
                    vote_buffer.append(pred_idx)

                if vote_buffer:
                    majority_idx, count = Counter(vote_buffer).most_common(1)[0]
                    label = LABEL_ORDER[majority_idx]
                    display_text = f"{label}  {confidence*100:.0f}%"
                else:
                    display_text = "no confident sign detected"

                # Console log: every 15 frames (~0.5s), or whenever the
                # smoothed majority label changes, or when no hand is seen.
                if display_text != last_logged or frame_count % 15 == 0:
                    ranked = sorted(range(len(LABEL_ORDER)), key=lambda i: -probs[i].item())
                    top3 = ", ".join(f"{LABEL_ORDER[i]}={probs[i].item()*100:.0f}%" for i in ranked[:3])
                    hand_note = "hand detected" if hand_detected else "NO HAND DETECTED this frame"
                    print(f"[frame {frame_count}] {hand_note} | raw top-3: {top3} | smoothed: {display_text}")
                    last_logged = display_text

            cv2.putText(frame, display_text, (10, 40), cv2.FONT_HERSHEY_SIMPLEX,
                        1.0, (0, 255, 0), 2, cv2.LINE_AA)
            cv2.imshow("SignTalk-mini (press q to quit)", frame)
            if cv2.waitKey(1) & 0xFF == ord("q"):
                break

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
