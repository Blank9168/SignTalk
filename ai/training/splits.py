"""
Torch-free sample listing + train/val split logic shared by train.py and the
evaluation scripts in ai/evaluation/.

This is the exact logic train.py has always used (moved here unchanged so
dataset_profile.py / evaluate_model.py can reproduce the split without
importing torch):

  1. list ai/dataset/raw_iso/<label>/*.npy in label order (labels_50.json),
     paths sorted within each label
  2. drop_bad_clips(): remove byte-identical clips filed under different
     labels, and all-zero (no hand detected) clips
  3. per-class stratified split: numpy default_rng(42), shuffle each class's
     indices, first max(1, round(n * 0.2)) go to val, the rest to train

IMPORTANT (2026-10-10): raw_iso/ now also holds 435 Google Drive phone clips
(drive_*.npy). The deployed model (ai/models/sign_lstm.pt == assets/
sign_lstm_iso.tflite) was trained by a one-off retrain script that is NOT in
the repo: it applied steps 1-3 to the ORIGINAL 889 clips only (no drive_*),
then split the drive clips separately (30% per class test, 1 val, rest train)
with an unrecorded RNG. So:
  * split "orig"   -> steps 1-3 on non-drive clips. Reproduces the deployed
                      model's 714 train / 175 val exactly.
  * split "all"    -> steps 1-3 on everything in raw_iso/ (what train.py
                      does if you run it today).
The drive held-out test set cannot be reproduced bit-for-bit; see
evaluation/README.md.
"""

import glob
import hashlib
import json
import os
from pathlib import Path

import numpy as np

AI_DIR = Path(__file__).resolve().parents[1]
DATASET_DIR = AI_DIR / "dataset"
DEFAULT_DATA_DIR = DATASET_DIR / "raw_iso"
DEFAULT_LABELS_PATH = DATASET_DIR / "labels_50.json"
DEFAULT_NONE_DIR = DATASET_DIR / "none"
NONE_LABEL = "none"

VAL_FRACTION = 0.2
SEED = 42


def load_label_meta(labels_path=DEFAULT_LABELS_PATH):
    """Return the labels_50.json entries as a list ordered by class index."""
    with open(labels_path, encoding="utf-8") as f:
        raw = json.load(f)
    return [raw[str(i)] for i in range(len(raw))]


def load_label_order(labels_path=DEFAULT_LABELS_PATH, include_none=False):
    order = [m["slug"] for m in load_label_meta(labels_path)]
    if include_none and NONE_LABEL not in order:
        order.append(NONE_LABEL)  # appended LAST so existing indices are unchanged
    return order


def list_samples(data_dir, label_order, none_dir=None):
    """[(path, label_idx)] in label order, paths sorted within a label.

    Identical to dataset._load_labels(). If label_order contains "none" and
    none_dir is given, that class is read from none_dir/*.npy (and
    none_dir/*/*.npy, so synthetic/ and recorded/ subfolders both count)
    instead of data_dir/none.
    """
    samples = []
    for idx, label in enumerate(label_order):
        if label == NONE_LABEL and none_dir is not None:
            paths = sorted(set(glob.glob(os.path.join(str(none_dir), "*.npy"))
                               + glob.glob(os.path.join(str(none_dir), "*", "*.npy"))))
        else:
            paths = sorted(glob.glob(os.path.join(str(data_dir), label, "*.npy")))
        samples.extend((p, idx) for p in paths)
    return samples


def is_drive_clip(path):
    return os.path.basename(path).startswith("drive_")


def find_bad_clips(samples):
    """Return {path: reason} for clips train.py's drop_bad_clips() removes."""
    by_hash = {}
    for path, label in samples:
        with open(path, "rb") as f:
            by_hash.setdefault(hashlib.md5(f.read()).hexdigest(), []).append((path, label))
    bad = {}
    for group in by_hash.values():
        if len({label for _, label in group}) > 1:
            for p, _ in group:
                bad[p] = "identical clip filed under different labels"
    for path, _ in samples:
        if path not in bad and not np.any(np.load(path)):
            bad[path] = "no hand detected in any frame (all zeros)"
    return bad


def drop_bad_clips(samples, verbose=True):
    bad = find_bad_clips(samples)
    kept = [(p, l) for p, l in samples if p not in bad]
    if verbose:
        print(f"dropped {len(samples) - len(kept)} conflicting/empty clips")
    return kept


def stratified_split(samples, val_fraction=VAL_FRACTION, seed=SEED):
    """train.py's per-class split. Returns (train_indices, val_indices) into samples."""
    rng = np.random.default_rng(seed)
    by_class = {}
    for i, (_, label_idx) in enumerate(samples):
        by_class.setdefault(label_idx, []).append(i)
    train_idx, val_idx = [], []
    for _, indices in by_class.items():
        indices = list(indices)
        rng.shuffle(indices)
        n_val = max(1, round(len(indices) * val_fraction))
        val_idx.extend(indices[:n_val])
        train_idx.extend(indices[n_val:])
    return train_idx, val_idx


def make_split(data_dir=DEFAULT_DATA_DIR, label_order=None, mode="orig", none_dir=None,
               verbose=True):
    """Reproduce a train/val split.

    mode="orig": non-drive clips only (the deployed model's split).
    mode="all":  every clip in data_dir (train.py today).
    Returns (train_samples, val_samples, dropped) with samples = [(path, label_idx)].
    """
    if label_order is None:
        label_order = load_label_order()
    samples = list_samples(data_dir, label_order, none_dir=none_dir)
    if mode == "orig":
        samples = [s for s in samples if not is_drive_clip(s[0])]
    elif mode != "all":
        raise ValueError(f"unknown split mode {mode!r}")
    bad = find_bad_clips(samples)
    clean = [s for s in samples if s[0] not in bad]
    if verbose:
        print(f"[split:{mode}] {len(samples)} clips, dropped {len(bad)} conflicting/empty")
    tr, va = stratified_split(clean)
    return [clean[i] for i in tr], [clean[i] for i in va], bad
