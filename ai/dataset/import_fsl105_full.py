"""
Import ALL 105 FSL-105 sign classes (2130 video clips total, ~20 per class)
and convert each clip into a 30-frame MediaPipe hand-landmark sequence,
saved as .npy -- same on-disk format as SignTalk's collect_data.py output
(one .npy file per recorded sequence, one folder per label, shape (30, 126)
per file).

This supersedes import_fsl105.py, which only imported 4 of the 105 classes
(hello / thank_you / no / yes) and paired them with 8 fabricated synthetic
placeholder classes. This script uses ALL of FSL-105's real data across all
105 classes and produces no synthetic data at all.

Source: FSL-105 (De La Salle University / DOST), CC-BY-4.0.
https://data.mendeley.com/datasets/48y2y99mb9/2

id -> slug mapping comes from labels_105.json (built from the dataset's own
labels.csv), e.g. id 3 "HELLO" -> slug "hello", id 11 "DON'T UNDERSTAND" ->
slug "dont_understand".

Usage:
    python import_fsl105_full.py
"""

import os
import sys
import json
import glob
import cv2
import numpy as np

sys.path.append(os.path.dirname(__file__))
from landmarks import HandLandmarkExtractor, sample_to_fixed_length, FEATURES_PER_FRAME

SEQUENCE_LENGTH = 30
SAMPLE_FRAMES = 45  # frames actually run through MediaPipe per clip, then
                     # resampled to SEQUENCE_LENGTH; keeps runtime reasonable
                     # on long 60fps/4s clips without losing temporal coverage

FSL105_CLIPS_DIR = "/mnt/user-data/uploads/Desktop--Claude-share-folder/c105/clips"
LABELS_PATH = os.path.join(os.path.dirname(__file__), "labels_105.json")
RAW_DIR = os.path.join(os.path.dirname(__file__), "raw")


def load_label_map():
    with open(LABELS_PATH) as f:
        raw = json.load(f)
    return {int(k): v["slug"] for k, v in raw.items()}


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
    id_to_slug = load_label_map()
    class_ids = sorted(id_to_slug.keys())
    print(f"Importing {len(class_ids)} classes from {FSL105_CLIPS_DIR}")

    totals = {"classes": 0, "clips_found": 0, "clips_saved": 0, "clips_failed": 0}

    with HandLandmarkExtractor() as extractor:
        for class_id in class_ids:
            slug = id_to_slug[class_id]
            src_dir = os.path.join(FSL105_CLIPS_DIR, str(class_id))
            dst_dir = os.path.join(RAW_DIR, slug)
            os.makedirs(dst_dir, exist_ok=True)

            video_paths = sorted(
                glob.glob(os.path.join(src_dir, "*.MOV")),
                key=lambda p: int(os.path.splitext(os.path.basename(p))[0]),
            )
            totals["classes"] += 1
            totals["clips_found"] += len(video_paths)

            saved = 0
            for i, video_path in enumerate(video_paths):
                seq = extract_video_landmarks(video_path, extractor)
                if seq is None:
                    print(f"  WARNING [{slug}]: no frames read from {video_path}, skipping")
                    totals["clips_failed"] += 1
                    continue
                out_path = os.path.join(dst_dir, f"{i}.npy")
                np.save(out_path, seq.astype(np.float32))
                saved += 1
            totals["clips_saved"] += saved
            print(f"[{class_id:3d}] {slug:<20s} {saved:2d}/{len(video_paths):2d} clips -> {dst_dir}")

    print("\n=== Import complete ===")
    print(f"classes: {totals['classes']}")
    print(f"clips found: {totals['clips_found']}")
    print(f"clips saved: {totals['clips_saved']}")
    print(f"clips failed: {totals['clips_failed']}")
    print(f"feature dim per frame: {FEATURES_PER_FRAME}, sequence length: {SEQUENCE_LENGTH}")


if __name__ == "__main__":
    main()
