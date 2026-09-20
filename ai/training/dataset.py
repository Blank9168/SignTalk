"""
SignDataset: loads (30, 126) landmark sequences from ai/dataset/raw/<label>/*.npy
and applies light augmentation (mirror, rotate, scale, noise, time-warp) so a
small sample count per class still yields a workable amount of training
variation. Mirrors the augmentation set used in the original SignTalk ai/
pipeline's training/dataset.py.

NOTE: coordinates here are HAND-RELATIVE (see ../dataset/landmarks.py) --
each hand is centered on its own wrist (so 0,0,0 = that hand's wrist, not
frame center) and scaled by hand size (so ~1.0 = one wrist-to-middle-MCP
length, not "full frame"). The augmentation math below reflects that: mirror
flips around x=0, rotate/scale pivot around the origin -- not around 0.5,
which was only correct for the old raw-frame-coordinate representation.
"""

import os
import glob
import numpy as np
import sys
import torch
from torch.utils.data import Dataset

sys.path.append(os.path.join(os.path.dirname(__file__), "..", "dataset"))
from landmarks import trim_to_hand_frames, sample_to_fixed_length  # noqa: E402

LANDMARKS_PER_HAND = 21
COORDS_PER_LANDMARK = 3


def _load_labels(raw_dir, label_order):
    samples = []
    for idx, label in enumerate(label_order):
        for path in sorted(glob.glob(os.path.join(raw_dir, label, "*.npy"))):
            samples.append((path, idx))
    return samples


def _mirror(seq):
    """Flip horizontally (x -> -x, hand-relative) and swap left/right hand slots."""
    out = seq.copy()
    out[:, 0::3] = -out[:, 0::3]  # flip all x coords (both hands)
    left = out[:, : LANDMARKS_PER_HAND * COORDS_PER_LANDMARK].copy()
    right = out[:, LANDMARKS_PER_HAND * COORDS_PER_LANDMARK :].copy()
    out[:, : LANDMARKS_PER_HAND * COORDS_PER_LANDMARK] = right
    out[:, LANDMARKS_PER_HAND * COORDS_PER_LANDMARK :] = left
    return out


def _rotate(seq, max_deg=10):
    theta = np.deg2rad(np.random.uniform(-max_deg, max_deg))
    cos_t, sin_t = np.cos(theta), np.sin(theta)
    out = seq.copy().reshape(seq.shape[0], -1, 3)
    x, y = out[..., 0], out[..., 1]  # already centered on the wrist (origin)
    out[..., 0] = x * cos_t - y * sin_t
    out[..., 1] = x * sin_t + y * cos_t
    return out.reshape(seq.shape[0], -1)


def _scale(seq, low=0.9, high=1.1):
    factor = np.random.uniform(low, high)
    out = seq.copy().reshape(seq.shape[0], -1, 3)
    out[..., :2] = out[..., :2] * factor  # scale around the origin (wrist)
    return out.reshape(seq.shape[0], -1)


def _jitter(seq, sigma=0.01):
    # Only perturb real landmarks. A missing hand is exactly 0 at inference time;
    # adding noise to those slots taught the model a "noisy empty hand" that
    # never occurs live.
    noise = np.random.normal(0, sigma, size=seq.shape).astype(np.float32)
    return seq + noise * (seq != 0)


def _time_warp(seq, max_shift=3):
    """Randomly resample the time axis within a small window (speed variation)."""
    t = seq.shape[0]
    shift = np.random.randint(-max_shift, max_shift + 1)
    src_idx = np.clip(np.linspace(0, t - 1, t) + shift * np.linspace(-1, 1, t), 0, t - 1)
    src_idx = np.round(src_idx).astype(int)
    return seq[src_idx]


def _crop(seq):
    """Random temporal crop (60-100% of the sequence), resampled back to full length.
    Live windows rarely start/end exactly on the sign."""
    n = seq.shape[0]
    k = np.random.randint(int(0.6 * n), n + 1)
    o = np.random.randint(0, n - k + 1)
    idx = np.clip(np.round(np.linspace(0, k - 1, n)).astype(int), 0, k - 1)
    return seq[o:o + k][idx]


def _prefix(seq):
    """Only the first k (6-30) frames of the sign, resampled to full length.
    This is what the live buffer holds right after a sign starts. The app now
    classifies from ~12 frames instead of waiting for 20, so the model has to be
    accurate on partial signs: on held-out phone clips this took 12-frame accuracy
    from 60% to 80% and 8-frame accuracy from 51% to 66%."""
    n = seq.shape[0]
    k = np.random.randint(6, n + 1)
    idx = np.clip(np.round(np.linspace(0, k - 1, n)).astype(int), 0, k - 1)
    return seq[:k][idx]


def _hand_drop(seq):
    """One hand vanishes for 2-5 frames (tracking loss), as happens live."""
    out = seq.copy()
    h = np.random.randint(2)
    start = np.random.randint(0, max(1, seq.shape[0] - 5))
    out[start:start + np.random.randint(2, 6), h * 63:(h + 1) * 63] = 0
    return out


def _aniso(seq):
    """Small residual x-stretch: aspect ratios of old clips are estimates, lenses/tilt differ."""
    out = seq.copy().reshape(seq.shape[0], -1, 3)
    out[..., 0] *= np.random.uniform(0.85, 1.15)
    return out.reshape(seq.shape[0], -1)


class SignDataset(Dataset):
    def __init__(self, raw_dir, label_order, augment=False, augment_multiplier=1):
        self.raw_dir = raw_dir
        self.label_order = label_order
        self.augment = augment
        self.base_samples = _load_labels(raw_dir, label_order)
        self.multiplier = augment_multiplier if augment else 1

    def __len__(self):
        return len(self.base_samples) * self.multiplier

    def __getitem__(self, idx):
        path, label = self.base_samples[idx % len(self.base_samples)]
        seq = np.load(path).astype(np.float32)
        # Match live inference: hand-visible frames only, resampled to 30.
        # (Works on existing zero-padded .npy files and is idempotent on new ones.)
        seq = sample_to_fixed_length(trim_to_hand_frames(seq), 30)

        if self.augment and idx >= len(self.base_samples):
            if np.random.rand() < 0.4:
                seq = _mirror(seq)
            if np.random.rand() < 0.6:
                seq = _rotate(seq, max_deg=20)
            if np.random.rand() < 0.5:
                seq = _scale(seq)
            if np.random.rand() < 0.5:
                seq = _aniso(seq)
            if np.random.rand() < 0.7:
                seq = _jitter(seq)
            if np.random.rand() < 0.4:
                seq = _time_warp(seq)
            if np.random.rand() < 0.5:
                seq = _crop(seq)
            if np.random.rand() < 0.3:
                seq = _hand_drop(seq)
            if np.random.rand() < 0.5:
                seq = _prefix(seq)

        return torch.from_numpy(seq.astype(np.float32)), label
