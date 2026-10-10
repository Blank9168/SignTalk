"""
Build a STARTER `none` (not-a-sign) set for the opt-in 51st class.

!!! SYNTHETIC STOPGAP !!!
The sequences written by this script are generated from the existing sign
clips; they are NOT recordings of people doing non-sign movements. They give
train.py --include-none something to learn "not one of the 50 signs" from
until the team records real non-sign clips (random hand movement, resting
hands, scratching the face, waving, fixing hair, holding a phone, ...).
Every file is named synthetic_*.npy and listed in MANIFEST.csv so it can be
excluded or replaced later. Do not report results on this set as real-world
non-sign performance.

Methods (default 25 each = 100 sequences, roughly one class's worth):
  cross_shuffle  30 frames drawn at random from clips of >= 3 different signs
                 (handshapes jump randomly; no real sign does this)
  splice         first half of sign A + second half of a different sign B
  rest_hold      a generic relaxed hand (mean of all training hand poses, small
                 random rotation/scale/finger noise) held almost still, one or
                 both hands - stands in for "hands resting in view"
  random_walk    a real hand pose that drifts by a heavy cumulative random walk
                 (aimless, jittery motion), re-normalised every frame

Only clips that are in the TRAIN part of train.py's split (both the original
889-clip split and the current all-clips split) are used, so no validation /
test clip leaks into the none class.

Real recordings: put non-sign videos in any folder and run
  python ai/training/make_none_from_existing.py --import-videos path/to/none_videos
to extract them (same extractor as training) into ai/dataset/none/recorded/.

Usage (repo root):
  python ai/training/make_none_from_existing.py              # 100 synthetic sequences
  python ai/training/make_none_from_existing.py --per-method 40 --seed 7
"""

import argparse
import csv
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.append(str(HERE.parent / "dataset"))
from splits import (DEFAULT_DATA_DIR, DEFAULT_NONE_DIR, load_label_order,  # noqa: E402
                    make_split)
from landmarks import sample_to_fixed_length, trim_to_hand_frames  # noqa: E402

T = 30
WRIST, MCP = 0, 9


def renorm_hand(h):
    """h: (21,3). Keep the training normalisation: wrist at origin, |wrist->MCP|_xy = 1."""
    h = h - h[WRIST]
    s = np.linalg.norm(h[MCP, :2])
    return h / s if s > 1e-4 else h


def rot_z(h, deg):
    t = np.deg2rad(deg)
    c, s = np.cos(t), np.sin(t)
    out = h.copy()
    out[:, 0], out[:, 1] = h[:, 0] * c - h[:, 1] * s, h[:, 0] * s + h[:, 1] * c
    return out


def load_train_clips(data_dir):
    order = load_label_order()
    tr_orig, _, _ = make_split(data_dir, order, mode="orig", verbose=False)
    tr_all, _, _ = make_split(data_dir, order, mode="all", verbose=False)
    keep = sorted(set(tr_orig) & set(tr_all))
    clips = []
    for path, label in keep:
        frames = trim_to_hand_frames(np.load(path).astype(np.float32))
        if np.any(frames):
            clips.append((path, label, frames))
    return clips


def cross_shuffle(rng, clips):
    while True:
        picks = rng.integers(0, len(clips), size=T)
        if len({clips[i][1] for i in picks}) >= 3:
            break
    seq = np.stack([clips[i][2][rng.integers(0, len(clips[i][2]))] for i in picks])
    return seq, ";".join(sorted({Path(clips[i][0]).parent.name for i in picks}))


def splice(rng, clips):
    while True:
        a, b = rng.integers(0, len(clips), size=2)
        if clips[a][1] != clips[b][1]:
            break
    fa, fb = clips[a][2], clips[b][2]
    first = sample_to_fixed_length(fa[: max(3, len(fa) // 2)], T // 2)
    second = sample_to_fixed_length(fb[len(fb) // 2:], T - T // 2)
    src = f"{Path(clips[a][0]).parent.name}/{Path(clips[a][0]).name} + {Path(clips[b][0]).parent.name}/{Path(clips[b][0]).name}"
    return np.concatenate([first, second]), src


def mean_hand(clips):
    hands = []
    for _, _, frames in clips:
        for f in frames[:: max(1, len(frames) // 5)]:
            for slot in range(2):
                h = f[slot * 63:(slot + 1) * 63].reshape(21, 3)
                if np.any(h):
                    # bring left-slot hands into right-slot orientation before averaging
                    hands.append(h * np.array([-1, 1, 1], np.float32) if slot == 0 else h)
    return renorm_hand(np.mean(hands, axis=0))  # right-slot orientation


def rest_hold(rng, clips, base):
    slots = [[0], [1], [0, 1]][rng.integers(0, 3)]
    seq = np.zeros((T, 126), np.float32)
    for slot in slots:
        h = base + rng.normal(0, 0.08, base.shape)        # per-sample finger variation
        h = renorm_hand(rot_z(h, rng.uniform(-35, 35)))
        drift = np.cumsum(rng.normal(0, 0.004, (T, 21, 3)), axis=0)
        for t in range(T):
            ht = renorm_hand(h + drift[t] + rng.normal(0, 0.01, h.shape))
            if slot == 0:  # mirror for the other hand slot (x -> -x), like dataset._mirror
                ht = ht * np.array([-1, 1, 1], np.float32)
            seq[t, slot * 63:(slot + 1) * 63] = ht.reshape(-1)
    return seq, "mean training hand pose"


def random_walk(rng, clips):
    path, _, frames = clips[rng.integers(0, len(clips))]
    start = frames[rng.integers(0, len(frames))].copy()
    seq = np.zeros((T, 126), np.float32)
    slots = [s for s in range(2) if np.any(start[s * 63:(s + 1) * 63])]
    for slot in slots:
        h = start[slot * 63:(slot + 1) * 63].reshape(21, 3)
        steps = np.cumsum(rng.normal(0, 0.06, (T, 21, 3)), axis=0)
        angle = np.cumsum(rng.normal(0, 4.0, T))
        for t in range(T):
            seq[t, slot * 63:(slot + 1) * 63] = renorm_hand(rot_z(h + steps[t], angle[t])).reshape(-1)
    return seq, f"{Path(path).parent.name}/{Path(path).name}"


def import_videos(video_dir, out_dir):
    sys.path.insert(0, str(HERE.parent / "evaluation"))
    import eval_common as ec
    vids = [p for p in sorted(Path(video_dir).rglob("*")) if p.suffix.lower() in ec.VIDEO_EXTS]
    pairs = [(str(v), str(out_dir / (v.stem + ".npy"))) for v in vids]
    ec.extract_videos(pairs)
    print(f"imported {len(pairs)} recorded non-sign clips -> {out_dir}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data-dir", default=str(DEFAULT_DATA_DIR))
    ap.add_argument("--none-dir", default=str(DEFAULT_NONE_DIR))
    ap.add_argument("--per-method", type=int, default=25)
    ap.add_argument("--seed", type=int, default=2026)
    ap.add_argument("--import-videos", default=None, help="extract recorded non-sign videos instead")
    args = ap.parse_args()

    none_dir = Path(args.none_dir)
    if args.import_videos:
        import_videos(args.import_videos, none_dir / "recorded")
        return

    out = none_dir / "synthetic"
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("synthetic_*.npy"):
        old.unlink()
    rng = np.random.default_rng(args.seed)
    clips = load_train_clips(args.data_dir)
    base = mean_hand(clips)
    print(f"using {len(clips)} train-split clips as raw material")

    makers = {"cross_shuffle": lambda: cross_shuffle(rng, clips),
              "splice": lambda: splice(rng, clips),
              "rest_hold": lambda: rest_hold(rng, clips, base),
              "random_walk": lambda: random_walk(rng, clips)}
    rows = []
    for method, make in makers.items():
        for k in range(args.per_method):
            seq, src = make()
            seq = np.nan_to_num(seq.astype(np.float32))
            assert seq.shape == (T, 126)
            name = f"synthetic_{method}_{k:03d}.npy"
            np.save(out / name, seq)
            rows.append({"file": name, "method": method, "source": src, "seed": args.seed,
                         "synthetic": "yes", "note": "stopgap - replace with recorded non-sign clips"})
    with open(out / "MANIFEST.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    (out / "README.txt").write_text(
        "SYNTHETIC non-sign sequences generated by ai/training/make_none_from_existing.py from existing\n"
        "sign clips (train split only). They are a STOPGAP for the opt-in `none` class, not recordings of\n"
        "real non-sign movement. Replace/augment with real clips in ../recorded/ (see the script docstring).\n"
        "Methods: cross_shuffle, splice, rest_hold, random_walk. See MANIFEST.csv for per-file provenance.\n",
        encoding="utf-8")
    print(f"wrote {len(rows)} synthetic none sequences -> {out}")


if __name__ == "__main__":
    main()
