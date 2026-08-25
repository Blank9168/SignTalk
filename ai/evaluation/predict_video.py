"""
Offline test: run the trained model on a single video file (e.g. a clip you
record on your phone) and print the predicted label + confidence.

This is the easiest way to test the model without setting up a live webcam
loop -- just point it at any short video of a hand sign.

Usage:
    python predict_video.py path/to/your_clip.mp4

Requires (install locally):
    pip install "mediapipe==0.10.14" opencv-python torch numpy
    (mediapipe is pinned to 0.10.14 because newer releases dropped the
    legacy solutions.hands API this project uses in favor of one that
    needs a model file downloaded from Google's servers.)
"""

import os
import sys
import json

import cv2
import numpy as np
import torch

sys.path.append(os.path.join(os.path.dirname(__file__), "..", "dataset"))
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "models"))
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "training"))

from landmarks import HandLandmarkExtractor, sample_to_fixed_length
from model import SignLSTM

MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models")
LABELS_PATH = os.path.join(os.path.dirname(__file__), "..", "dataset", "labels_105.json")
SEQUENCE_LENGTH = 30


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


def extract_sequence(video_path):
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        raise FileNotFoundError(f"Could not open video: {video_path}")

    frames = []
    with HandLandmarkExtractor() as extractor:
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            frames.append(extractor.extract(frame))
    cap.release()

    if not frames:
        raise RuntimeError("No frames read from video")
    return sample_to_fixed_length(frames, SEQUENCE_LENGTH)


def main():
    if len(sys.argv) != 2:
        print(f"Usage: python {sys.argv[0]} path/to/video.mp4")
        sys.exit(1)

    video_path = sys.argv[1]
    print(f"Reading {video_path} ...")
    seq = extract_sequence(video_path)

    model = load_model()
    x = torch.from_numpy(seq.astype(np.float32)).unsqueeze(0)
    with torch.no_grad():
        probs = torch.softmax(model(x), dim=1)[0]

    ranked = sorted(range(len(LABEL_ORDER)), key=lambda i: -probs[i].item())
    print("\nTop predictions:")
    for i in ranked[:5]:
        label = LABEL_ORDER[i]
        print(f"  {label:<20} {probs[i].item()*100:5.1f}%")


if __name__ == "__main__":
    main()
