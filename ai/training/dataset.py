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
import torch
from torch.utils.data import Dataset

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
    return seq + np.random.normal(0, sigma, size=seq.shape).astype(np.float32)


def _time_warp(seq, max_shift=3):
    """Randomly resample the time axis within a small window (speed variation)."""
    t = seq.shape[0]
    shift = np.random.randint(-max_shift, max_shift + 1)
    src_idx = np.clip(np.linspace(0, t - 1, t) + shift * np.linspace(-1, 1, t), 0, t - 1)
    src_idx = np.round(src_idx).astype(int)
    return seq[src_idx]


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

        if self.augment and idx >= len(self.base_samples):
            if np.random.rand() < 0.3:
                seq = _mirror(seq)
            if np.random.rand() < 0.5:
                seq = _rotate(seq)
            if np.random.rand() < 0.5:
                seq = _scale(seq)
            if np.random.rand() < 0.7:
                seq = _jitter(seq)
            if np.random.rand() < 0.3:
                seq = _time_warp(seq)

        return torch.from_numpy(seq.astype(np.float32)), label
