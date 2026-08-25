"""
Import real user-recorded webcam clips and add them to ai/dataset/raw/<label>/,
alongside the FSL-105 clips, using the same landmark-extraction pipeline
(so hand-relative normalization is applied consistently).

Files are saved with a "user_" prefix so they don't collide with FSL-105's
numeric filenames and stay easy to distinguish/remove later.

Usage:
    python import_user_clips.py <label> <path-to-folder-of-video-files>

Example:
    python import_user_clips.py yes \
        "/mnt/user-data/uploads/.../my clips/15"
"""

import os
import sys
import glob
import cv2
import numpy as np

sys.path.append(os.path.dirname(__file__))
from landmarks import HandLandmarkExtractor, sample_to_fixed_length

SEQUENCE_LENGTH = 30
SAMPLE_FRAMES = 45
RAW_DIR = os.path.join(os.path.dirname(__file__), "raw")


def extract_video_landmarks(video_path, extractor):
    cap = cv2.VideoCapture(video_path)
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    if total <= 0:
        frames = []
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            frames.append(extractor.extract(frame))
        cap.release()
        return sample_to_fixed_length(frames, SEQUENCE_LENGTH) if frames else None

    target_indices = set(
        int(round(i)) for i in np.linspace(0, total - 1, min(SAMPLE_FRAMES, total))
    )
    frames = []
    idx = 0
    while True:
        if idx in target_indices:
            ret, frame = cap.read()
            if not ret:
                break
            frames.append(extractor.extract(frame))
        else:
            ret = cap.grab()
            if not ret:
                break
        idx += 1
    cap.release()
    if not frames:
        return None
    return sample_to_fixed_length(frames, SEQUENCE_LENGTH)


def main():
    if len(sys.argv) != 3:
        print(f"Usage: python {sys.argv[0]} <label> <folder-of-video-files>")
        sys.exit(1)

    label, src_dir = sys.argv[1], sys.argv[2]
    dst_dir = os.path.join(RAW_DIR, label)
    os.makedirs(dst_dir, exist_ok=True)

    video_paths = sorted(
        glob.glob(os.path.join(src_dir, "*.mp4"))
        + glob.glob(os.path.join(src_dir, "*.MOV"))
        + glob.glob(os.path.join(src_dir, "*.mov"))
        + glob.glob(os.path.join(src_dir, "*.avi"))
    )
    print(f"[{label}] found {len(video_paths)} user clips in {src_dir}")

    saved = 0
    with HandLandmarkExtractor() as extractor:
        for i, video_path in enumerate(video_paths):
            seq = extract_video_landmarks(video_path, extractor)
            if seq is None:
                print(f"  WARNING: no frames read from {video_path}, skipping")
                continue
            out_path = os.path.join(dst_dir, f"user_{i}.npy")
            np.save(out_path, seq.astype(np.float32))
            saved += 1
    print(f"[{label}] saved {saved} user-recorded sequences -> {dst_dir}")


if __name__ == "__main__":
    main()
