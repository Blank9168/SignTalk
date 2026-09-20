"""
Convert the existing aspect-distorted landmark clips in raw/ into aspect-corrected
clips in raw_iso/ (the feature space landmarks.normalize_hand() now produces).

Why: MediaPipe normalizes x by frame width and y by frame height, so hand shapes were
squashed differently depending on the video's aspect ratio (FSL-105 is landscape,
the phone clips are portrait). The stored features are (x_n, y_n, z_n) / s with
s = |(x_n9, y_n9)|, so the correction is exactly invertible given the frame aspect
a = W/H:   f' = (a*x, y, a*z) / sqrt((a*x9)^2 + y9^2).

The source videos of FSL-105 / ASL Citizen are not in the repo, so their aspect ratios
are estimates (measured from the data itself: palm-width/palm-length is 0.34 for FSL,
0.41 for ASL Citizen and 0.98 for the phone clips, i.e. ~16:9, ~4:3 and ~9:16).
If you still have the original videos, re-extracting them with the fixed
HandLandmarkExtractor is better than this conversion.

Usage:  python fix_aspect.py            (originals in raw/ are left untouched)
"""
import os
import re
import glob
import json
import numpy as np

HERE = os.path.dirname(__file__)
SRC = os.path.join(HERE, "raw")
DST = os.path.join(HERE, "raw_iso")

ASPECT_FSL = 16 / 9        # FSL-105 clips: numeric filenames (0.npy, 1.npy, ...)
ASPECT_ASL = 4 / 3         # ASL Citizen clips: asl_*.npy
ASPECT_USER = 350 / 640    # phone clips imported with import_user_clips.py: user_*.npy


def aspect_for(filename):
    if filename.startswith("user_"):
        return ASPECT_USER
    if filename.startswith("asl_"):
        return ASPECT_ASL
    return ASPECT_FSL


def to_isotropic(seq, a):
    """seq: (T, 126) stored features -> aspect-corrected (T, 126)."""
    out = np.array(seq, dtype=np.float32, copy=True).reshape(len(seq), 2, 21, 3)
    for h in range(2):
        hand = out[:, h]
        present = np.abs(hand).sum(axis=(1, 2)) > 0
        scale = np.sqrt((hand[:, 9, 0] * a) ** 2 + hand[:, 9, 1] ** 2)
        hand[..., 0] *= a
        hand[..., 2] *= a
        hand /= np.where(present, np.maximum(scale, 1e-4), 1.0)[:, None, None]
    return out.reshape(len(seq), 126)


def main():
    labels = [v["slug"] for _, v in sorted(json.load(open(os.path.join(HERE, "labels_50.json"))).items(), key=lambda kv: int(kv[0]))]
    n = 0
    for label in labels:
        os.makedirs(os.path.join(DST, label), exist_ok=True)
        for path in glob.glob(os.path.join(SRC, label, "*.npy")):
            name = os.path.basename(path)
            np.save(os.path.join(DST, label, name), to_isotropic(np.load(path), aspect_for(name)))
            n += 1
    print(f"converted {n} clips -> {DST}")


if __name__ == "__main__":
    main()
