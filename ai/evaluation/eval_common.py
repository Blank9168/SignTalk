"""
Shared helpers for the SignTalk evaluation scripts (evaluate_model.py,
threshold_sweep.py, prepare_signer_independent.py, dataset_profile.py).

Everything here mirrors the training pipeline exactly:
  * sequences are preprocessed like training/dataset.py does for validation:
    trim_to_hand_frames() then sample_to_fixed_length(.., 30)
  * the default test set is train.py's validation split (training/splits.py)
  * video clips are converted with the same extractor and frame sampling as
    dataset/import_user_clips.py (HandLandmarkExtractor + 45 sampled frames)

Paths are resolved relative to this file, so the scripts run from any working
directory on Windows / macOS / Linux.
"""

import csv
import fnmatch
import json
import os
import re
import sys
from pathlib import Path

import numpy as np

EVAL_DIR = Path(__file__).resolve().parent
AI_DIR = EVAL_DIR.parent
REPO_ROOT = AI_DIR.parent
DATASET_DIR = AI_DIR / "dataset"
MODELS_DIR = AI_DIR / "models"
REPORTS_DIR = EVAL_DIR / "reports"
APP_ASSETS_DIR = REPO_ROOT / "android" / "SignTalk" / "app" / "src" / "main" / "assets"

for _p in (AI_DIR / "training", DATASET_DIR, MODELS_DIR):
    if str(_p) not in sys.path:
        sys.path.append(str(_p))

from splits import (  # noqa: E402  (training/splits.py, torch-free)
    DEFAULT_DATA_DIR, DEFAULT_LABELS_PATH, DEFAULT_NONE_DIR, NONE_LABEL,
    is_drive_clip, list_samples, load_label_meta, load_label_order, make_split,
)
from landmarks import (  # noqa: E402  (dataset/landmarks.py)
    FEATURES_PER_FRAME, normalize_hand, sample_to_fixed_length, trim_to_hand_frames,
)

SEQUENCE_LENGTH = 30
APP_MIN_FRAMES = 12          # RecognitionEngine.minFramesForPrediction
APP_THRESHOLD = 0.6          # RecognitionEngine default confidenceThreshold
VIDEO_EXTS = (".mp4", ".mov", ".avi", ".mkv", ".m4v", ".3gp", ".webm")

# The six ASL phrase classes were built by concatenating ASL Citizen
# component-word clips (see train.py docstring), not recorded as phrases.
ASL_CONCAT_PHRASES = {"good_morning", "good_afternoon", "good_evening",
                      "how_are_you", "nice_to_meet_you", "see_you_tomorrow"}

SOURCE_NAMES = {
    "fsl105": "FSL-105 dataset (De La Salle University / DOST, CC-BY-4.0)",
    "asl_citizen": "ASL Citizen dataset (Microsoft Research)",
    "asl_citizen_concat": "ASL Citizen, phrase built by concatenating component-word clips",
    "team_phone": "Team-recorded phone clips (user_IMG_*.npy, import_user_clips.py)",
    "drive_phone": "Google Drive phone clips collected by the team (drive_<fileId>.npy)",
    "synthetic_none": "Synthetic non-sign sequences (make_none_from_existing.py) - stopgap",
    "recorded_none": "Recorded non-sign clips (ai/dataset/none/)",
    "signer_independent": "Signer-independent test clips (new signers)",
    "unknown": "Unknown (file name does not match a known pattern)",
}


def classify_source(path, label=None):
    """Infer where a landmark file came from, from its file name.

    Naming conventions found in ai/dataset/raw_iso (documented in
    dataset_profile.md):
      <digits>.npy        FSL-105 import (import_fsl105_full.py)
      asl_<n>.npy         ASL Citizen import
      user_IMG_<nnnn>.npy team phone clips (iPhone IMG_ numbering)
      drive_<id>.npy      Google Drive phone clips (Drive file id)
      synthetic_*.npy     make_none_from_existing.py output
    """
    name = os.path.basename(str(path))
    parts = Path(path).parts
    if name.startswith("synthetic_"):
        return "synthetic_none"
    if label == NONE_LABEL:
        return "recorded_none"
    if any(part.startswith("signer_independent") for part in parts):
        return "signer_independent"
    if name.startswith("drive_"):
        return "drive_phone"
    if name.startswith("user_"):
        return "team_phone"
    if name.startswith("asl_"):
        return "asl_citizen_concat" if label in ASL_CONCAT_PHRASES else "asl_citizen"
    if re.fullmatch(r"\d+\.npy", name):
        return "fsl105"
    return "unknown"


# ---------------------------------------------------------------- labels
def load_labels_any(path=None, num_outputs=None):
    """Label order from labels_50.json, models/metadata.json or the app's
    assets/labels.json. If the model has one more output than the file lists
    and the extra class is the opt-in `none` class, it is appended last."""
    path = Path(path) if path else DEFAULT_LABELS_PATH
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    if isinstance(raw, dict) and "labels" in raw:          # metadata.json / app labels.json
        labels = [e["label"] if isinstance(e, dict) else e for e in raw["labels"]]
    elif isinstance(raw, dict) and "0" in raw:              # labels_50.json
        labels = [raw[str(i)]["slug"] for i in range(len(raw))]
    elif isinstance(raw, list):
        labels = [e["label"] if isinstance(e, dict) else e for e in raw]
    else:
        raise ValueError(f"unrecognised label file format: {path}")
    if num_outputs is not None and num_outputs == len(labels) + 1 and NONE_LABEL not in labels:
        labels = labels + [NONE_LABEL]
    if num_outputs is not None and num_outputs != len(labels):
        raise ValueError(f"model has {num_outputs} outputs but {path} lists {len(labels)} labels")
    return labels


def label_language_map(labels_path=DEFAULT_LABELS_PATH):
    meta = load_label_meta(labels_path)
    out = {m["slug"]: m.get("language", "") for m in meta}
    out[NONE_LABEL] = "-"
    return out


def label_category_map(labels_path=DEFAULT_LABELS_PATH):
    meta = load_label_meta(labels_path)
    out = {m["slug"]: m.get("category", "") for m in meta}
    out[NONE_LABEL] = "NON_SIGN"
    return out


# ---------------------------------------------------------- preprocessing
def preprocess(seq, prefix_frames=None):
    """Exactly what SignDataset.__getitem__ does for un-augmented samples.

    prefix_frames=k additionally keeps only the first k of the 30 resampled
    frames and resamples them back to 30 (the "12-frame" early-prediction
    metric in metadata.json, which matches the app starting to classify after
    12 real hand frames)."""
    seq = np.asarray(seq, dtype=np.float32)
    seq = sample_to_fixed_length(trim_to_hand_frames(seq), SEQUENCE_LENGTH)
    if prefix_frames:
        seq = sample_to_fixed_length(seq[:prefix_frames], SEQUENCE_LENGTH)
    return seq.astype(np.float32)


def load_sequences(paths, prefix_frames=None):
    return np.stack([preprocess(np.load(p), prefix_frames) for p in paths]).astype(np.float32)


def hand_frames(path):
    """Hand-visible frames of a stored clip (variable length <= 30)."""
    return trim_to_hand_frames(np.load(path).astype(np.float32))


# ------------------------------------------------------------------ models
def _softmax(z):
    z = z - z.max(axis=1, keepdims=True)
    e = np.exp(z)
    return e / e.sum(axis=1, keepdims=True)


class Predictor:
    """Wraps a PyTorch .pt state dict (model.py SignLSTM) or a .tflite file."""

    def __init__(self, model_path):
        self.path = Path(model_path)
        self.kind = "tflite" if self.path.suffix.lower() == ".tflite" else "pt"
        if self.kind == "pt":
            import torch
            from model import SignLSTM  # ai/models/model.py
            state = torch.load(str(self.path), map_location="cpu")
            if isinstance(state, dict) and "state_dict" in state:
                state = state["state_dict"]
            self.num_outputs = int(state["head.3.weight"].shape[0])
            self._torch = torch
            self._model = SignLSTM(num_classes=self.num_outputs)
            self._model.load_state_dict(state)
            self._model.eval()
        else:
            Interpreter = None
            for mod, attr in (("ai_edge_litert.interpreter", "Interpreter"),
                              ("tflite_runtime.interpreter", "Interpreter"),
                              ("tensorflow.lite", "Interpreter")):
                try:
                    Interpreter = getattr(__import__(mod, fromlist=[attr]), attr)
                    break
                except ImportError:
                    continue
            if Interpreter is None:
                raise ImportError("No TFLite runtime found. pip install ai-edge-litert "
                                  "(or tflite-runtime / tensorflow), or evaluate the .pt model.")
            self._it = Interpreter(model_path=str(self.path))
            self._it.allocate_tensors()
            self._in = self._it.get_input_details()[0]
            self._out = self._it.get_output_details()[0]
            self.num_outputs = int(self._out["shape"][-1])

    def predict_proba(self, X, batch_size=256):
        X = np.asarray(X, dtype=np.float32)
        if self.kind == "pt":
            outs = []
            with self._torch.no_grad():
                for i in range(0, len(X), batch_size):
                    logits = self._model(self._torch.from_numpy(X[i:i + batch_size]))
                    outs.append(self._torch.softmax(logits, dim=1).numpy())
            return np.concatenate(outs) if outs else np.zeros((0, self.num_outputs), np.float32)
        out = np.zeros((len(X), self.num_outputs), dtype=np.float32)
        for i in range(len(X)):
            self._it.set_tensor(self._in["index"], X[i:i + 1])
            self._it.invoke()
            out[i] = self._it.get_tensor(self._out["index"])[0]
        is_prob = np.all(out >= -1e-6) and np.allclose(out.sum(axis=1), 1.0, atol=1e-3)
        return out if is_prob else _softmax(out)


def default_model_path():
    return MODELS_DIR / "sign_lstm.pt"


# ---------------------------------------------------------------- test sets
def _is_label_dir_layout(test_dir, label_set):
    subdirs = [d.name for d in Path(test_dir).iterdir() if d.is_dir()]
    return any(d in label_set for d in subdirs)


def collect_test_dir(test_dir, label_order, landmarks_cache=None, allow_none=True):
    """Collect (npy_path, label_idx_or_-1, group, label_name) from a folder.

    Accepted layouts:
      <test_dir>/<label>/<clip>.npy|.mp4              group = "all"
      <test_dir>/<signer_id>/<label>/<clip>.npy|.mp4  group = signer_id
    Videos are converted to landmarks first (see extract_videos) and cached as
    <landmarks_cache>/<group>/<label>/<clip>.npy. Folders whose name is not a
    known label are skipped with a warning; a `none` folder is returned with
    label_idx -1 (unless the model itself has a `none` class).
    """
    test_dir = Path(test_dir)
    label_set = set(label_order)
    if _is_label_dir_layout(test_dir, label_set | {NONE_LABEL}):
        groups = [("all", test_dir)]
    else:
        groups = [(d.name, d) for d in sorted(test_dir.iterdir()) if d.is_dir()]
    cache = Path(landmarks_cache) if landmarks_cache else test_dir.parent / (test_dir.name + "_landmarks")
    items, skipped = [], set()
    for group, gdir in groups:
        for ldir in sorted(d for d in gdir.iterdir() if d.is_dir()):
            label = ldir.name
            if label not in label_set and not (allow_none and label == NONE_LABEL):
                skipped.add(label)
                continue
            idx = label_order.index(label) if label in label_set else -1
            for f in sorted(ldir.iterdir()):
                if f.suffix.lower() == ".npy":
                    items.append((str(f), idx, group, label))
                elif f.suffix.lower() in VIDEO_EXTS:
                    out = cache / group / label / (f.stem + ".npy")
                    items.append(((str(f), str(out)), idx, group, label))
    if skipped:
        print(f"WARNING: skipped folders that are not labels of this model: {sorted(skipped)}")
    videos = [it for it in items if isinstance(it[0], tuple)]
    if videos:
        extract_videos([v[0] for v in videos])
    return [((it[0][1] if isinstance(it[0], tuple) else it[0]), it[1], it[2], it[3])
            for it in items if not isinstance(it[0], tuple) or Path(it[0][1]).exists()]


def resolve_split(model_path, split, manifest=None, data_dir=DEFAULT_DATA_DIR, label_order=None):
    """Return val samples [(path, label_idx)] for the chosen split.

    split="auto": use <model dir>/split_manifest.csv if train.py wrote one
                  (new runs), else "orig" (the deployed model's split).
    """
    model_dir = Path(model_path).parent
    if split == "auto":
        cand = Path(manifest) if manifest else model_dir / "split_manifest.csv"
        split = "manifest" if cand.exists() else "orig"
        manifest = cand
    if split == "manifest":
        rows = list(csv.DictReader(open(manifest, encoding="utf-8")))
        out = []
        for r in rows:
            if r["split"] != "val":
                continue
            p = Path(r["path"])
            if not p.is_absolute():
                p = AI_DIR / p
            if r["label"] in label_order:
                out.append((str(p), label_order.index(r["label"])))
        return out, f"manifest:{manifest}"
    base_order = [l for l in label_order if l != NONE_LABEL]
    _, val, _ = make_split(data_dir, base_order, mode=split, verbose=False)
    return val, split


# ------------------------------------------------------- video extraction
class _TasksHandExtractor:
    """Fallback for mediapipe>0.10.14 (no legacy solutions.hands): the Tasks
    HandLandmarker in VIDEO mode with the app's hand_landmarker.task. Output
    format and normalization are identical to HandLandmarkExtractor.extract
    (slot 0 = Left, slot 1 = Right, normalize_hand with frame aspect, same
    duplicate-handedness fallback)."""

    def __init__(self, task_path):
        import mediapipe as mp
        from mediapipe.tasks.python import BaseOptions
        from mediapipe.tasks.python.vision import (HandLandmarker, HandLandmarkerOptions,
                                                   RunningMode)
        self._mp = mp
        opts = HandLandmarkerOptions(
            base_options=BaseOptions(model_asset_path=str(task_path)),
            running_mode=RunningMode.VIDEO, num_hands=2,
            min_hand_detection_confidence=0.5, min_hand_presence_confidence=0.5,
            min_tracking_confidence=0.5)
        self._lm = HandLandmarker.create_from_options(opts)
        self._ts = 0

    def extract(self, frame_bgr):
        import cv2
        h, w = frame_bgr.shape[:2]
        aspect = w / float(h)
        img = self._mp.Image(image_format=self._mp.ImageFormat.SRGB,
                             data=cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB))
        self._ts += 33
        res = self._lm.detect_for_video(img, self._ts)
        left = np.zeros(63, np.float32)
        right = np.zeros(63, np.float32)
        hands = []
        for lms, hd in zip(res.hand_landmarks or [], res.handedness or []):
            coords = np.array([[p.x, p.y, p.z] for p in lms], dtype=np.float32)
            hands.append((hd[0].category_name, coords[0, 0], normalize_hand(coords, aspect).flatten()))
        if len(hands) == 1:
            # Same rule as the app (RecognitionEngine): one visible hand always goes in the
            # LEFT slot. The Tasks API labels a lone hand opposite to the legacy API that
            # produced the training data (checked on the team phone clips: 2-hand frames
            # agree, 1-hand frames land in the other slot), and the training data has its
            # one-handed signs in the left slot.
            left = hands[0][2]
        elif len(hands) == 2 and hands[0][0] == hands[1][0]:
            hands.sort(key=lambda t: t[1])
            left, right = hands[0][2], hands[1][2]
        else:
            for lab, _, feats in hands:
                if lab == "Left":
                    left = feats
                else:
                    right = feats
        return np.concatenate([left, right])

    def close(self):
        self._lm.close()


def make_extractor(prefer_legacy=True):
    """HandLandmarkExtractor (identical to training) when mediapipe==0.10.14 is
    installed; otherwise the Tasks fallback above."""
    try:
        from landmarks import HandLandmarkExtractor, mp_hands
        if mp_hands is not None and prefer_legacy:
            return HandLandmarkExtractor(), "legacy mediapipe.solutions.hands (same as training)"
    except ImportError:
        pass
    for task in (APP_ASSETS_DIR / "hand_landmarker.task", AI_DIR / "_diagnostics" / "hand_landmarker.task"):
        if task.exists():
            print("NOTE: legacy MediaPipe Hands API not available; using the Tasks HandLandmarker "
                  f"({task.name}) instead. For results identical to training install mediapipe==0.10.14.")
            return _TasksHandExtractor(task), f"mediapipe Tasks HandLandmarker ({task.name})"
    raise ImportError('Install mediapipe==0.10.14 (pip install "mediapipe==0.10.14" opencv-python)')


def extract_videos(pairs, overwrite=False):
    """pairs: [(video_path, out_npy_path)]. Uses import_user_clips.extract_video_landmarks
    (45 evenly-sampled frames -> trim -> 30) so new clips match the training data."""
    todo = [(v, o) for v, o in pairs if overwrite or not Path(o).exists()]
    if not todo:
        return
    from import_user_clips import extract_video_landmarks  # dataset/import_user_clips.py
    extractor, how = make_extractor()  # one extractor for the batch, like import_user_clips.py
    print(f"Extracting landmarks from {len(todo)} video(s) with {how} ...")
    try:
        for i, (video, out) in enumerate(todo, 1):
            seq = extract_video_landmarks(str(video), extractor)
            if seq is None:
                print(f"  WARNING: no frames read from {video}")
                continue
            Path(out).parent.mkdir(parents=True, exist_ok=True)
            np.save(out, seq.astype(np.float32))
            if not np.any(seq):
                print(f"  WARNING: no hand detected in {video}")
            if i % 25 == 0 or i == len(todo):
                print(f"  {i}/{len(todo)}")
    finally:
        extractor.close()


# ------------------------------------------------------------- misc
def match_rows(rows, label, filename):
    """First row of a fill-in CSV whose label/file pattern (fnmatch) matches."""
    for r in rows:
        if fnmatch.fnmatch(label, r.get("label") or "*") and fnmatch.fnmatch(filename, r.get("file") or "*"):
            return r
    return None


def rel(p):
    """Path relative to the ai/ folder, with forward slashes (stable across OSes)."""
    try:
        return Path(p).resolve().relative_to(AI_DIR).as_posix()
    except ValueError:
        return Path(p).as_posix()
