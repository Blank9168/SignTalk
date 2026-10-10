"""
Train SignLSTM on ai/dataset/raw/<label>/*.npy for the 2026-09-06
bilingual 50-sign vocabulary (25 FSL + 25 ASL -- see labels_50.json and
proposal-notes.md's "Sign vocabulary" section for the full audit).

As of 2026-09-06, all 50 of the 50 classes have real training data:
  - 22 FSL classes (10 numbers: isa-sampu, 12 colors: asul/berde/pula/
    kayumanggi/itim/puti/dilaw/kahel/abo/rosas/lila/maliwanag): real
    FSL-105 video + landmark data (De La Salle University / DOST,
    CC-BY-4.0), copied from the FSL-105 dataset's unused english-slug
    classes (one, two, ... blue, green, ...) to these Filipino slugs --
    the same file-copy pattern used earlier for the greetings
    (magandang_umaga/hapon/gabi). These 22 classes replace an earlier,
    unfilmed 22-word FSL list (question words, vehicles, Paalam, Ingat Ka,
    calendar/weather) for which five research passes found no usable
    open dataset or reference footage; see proposal-notes.md for the
    swap rationale and the full english->Filipino slug mapping.
  - 3 FSL greetings (magandang_umaga/hapon/gabi): real FSL-105 video data
    (De La Salle University / DOST, CC-BY-4.0), ~20-22 clips/class.
  - 25 ASL words: real clips from Microsoft's ASL Citizen dataset -- 19
    direct single-sign words (6 clips/class) plus 6 multi-word phrases
    (Good Morning, Good Afternoon, Good Evening, How Are You, Nice To Meet
    You, See You Tomorrow) built by concatenating + resampling their
    component-word landmark sequences (4 samples/class).
See ../dataset/raw_archive_fsl105_unused/ for the OLD FSL-105 clips that
used to sit under some of these English slugs (hello, understand, know,
yes, ... -- FSL-105 data mislabeled as if it were these ASL/FSL words) --
archived, not deleted, in case anyone wants to compare, but no longer used
since they don't correspond to any of the actual 50 target signs.

Per-class sample counts here are much smaller (4-22) than the old 105-class
version's ~18-22 uniform count, so treat any reported accuracy as a signal
on this exact tiny dataset, not a generalization claim -- more so than the
105-class caveat already was.

With all 50 classes now populated, the full 50x50 confusion matrix is
meaningful; this script reports overall accuracy, macro/weighted P/R/F1,
the full classification_report.txt (per-class, with zero_division=0 so any
still-thin classes just show lower support instead of erroring), a short
list of the most confused class pairs, and the raw confusion matrix as CSV.

Command line (2026-10-10; with no arguments it behaves exactly as before and
writes into ai/models/):
  python ai/training/train.py                      # 50 classes -> ai/models/
  python ai/training/train.py --include-none       # 51 classes (+ `none`) -> ai/models/candidate/none_class/
  python ai/training/train.py --include-none --epochs 2 --out-dir <tmp>   # smoke test
Options: --data-dir, --none-dir, --out-dir, --epochs, --patience, --seed, --split {all,orig}.
Every run also writes split_manifest.csv (which clip went to train / val) next
to the model so ai/evaluation/evaluate_model.py can evaluate on exactly that
validation split.

Opt-in `none` class (--include-none): sequences from ai/dataset/none/ (any
*.npy directly in it or one folder down, e.g. none/synthetic/ from
make_none_from_existing.py and none/recorded/ for real non-sign clips) become
class "none", appended LAST so the 50 existing label indices do not change.
A labels.json in the Android app format (with "none" appended) is written to
the output folder; the app must also be taught to treat "none" as "no sign".
"""

import os
import sys
import json
import csv
import glob
import argparse

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sklearn.metrics import confusion_matrix, classification_report, precision_recall_fscore_support

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # training/ first (also under python -I)
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "dataset"))
sys.path.append(os.path.join(os.path.dirname(__file__), "..", "models"))

from dataset import SignDataset  # local (training/dataset.py), sys.path appended, not shadowed
from model import SignLSTM
import splits  # training/splits.py: torch-free copy of the split logic, shared with ai/evaluation

# raw_iso/ = raw/ converted to aspect-corrected features by dataset/fix_aspect.py
# (see normalize_hand in dataset/landmarks.py). Falls back to raw/ only if it has
# not been generated -- but a model trained on raw/ does NOT match the corrected
# feature space and will not transfer to phone cameras.
_DATASET_DIR = os.path.join(os.path.dirname(__file__), "..", "dataset")
RAW_DIR = os.path.join(_DATASET_DIR, "raw_iso")
if not os.path.isdir(RAW_DIR):
    print("WARNING: dataset/raw_iso not found -- run dataset/fix_aspect.py first. Using raw/ (distorted aspect).")
    RAW_DIR = os.path.join(_DATASET_DIR, "raw")
MODELS_DIR = os.path.join(os.path.dirname(__file__), "..", "models")
LABELS_PATH = os.path.join(os.path.dirname(__file__), "..", "dataset", "labels_50.json")
NONE_DIR = os.path.join(_DATASET_DIR, "none")
APP_LABELS_PATH = os.path.join(os.path.dirname(__file__), "..", "..", "android", "SignTalk", "app",
                               "src", "main", "assets", "labels.json")

BATCH_SIZE = 32
MAX_EPOCHS = 80
PATIENCE = 12
LR = 1e-3
AUGMENT_MULTIPLIER = 6
VAL_FRACTION = 0.2
SEED = 42
TOP_CONFUSED_PAIRS = 25


def load_label_order():
    with open(LABELS_PATH) as f:
        raw = json.load(f)
    # keys are string ids "0".."104"; sort numerically, slug is the class label
    ordered = [raw[str(i)]["slug"] for i in range(len(raw))]
    return ordered


LABEL_ORDER = load_label_order()


def set_seed(seed):
    np.random.seed(seed)
    torch.manual_seed(seed)


def drop_bad_clips(samples):
    """Remove clips that would poison training: (a) byte-identical clips filed under
    different labels (FSL-105 has four/6 == seven/7 and four/8 == seven/9, which is
    why apat<->pito was the top confusion) and (b) clips with no detected hand at all
    (walo/7 is an all-zero sequence labelled 'walo'). Implementation: splits.py."""
    return splits.drop_bad_clips(samples)


def build_datasets(raw_dir=None, label_order=None, none_dir=None, split_mode="all", seed=SEED):
    raw_dir = raw_dir or RAW_DIR
    label_order = label_order or LABEL_ORDER
    full_clean = SignDataset(raw_dir, label_order, augment=False, none_dir=none_dir)
    if split_mode == "orig":  # the deployed model's original 889-clip pool (no Drive clips)
        full_clean.base_samples = [s for s in full_clean.base_samples if not splits.is_drive_clip(s[0])]
    full_clean.base_samples = drop_bad_clips(full_clean.base_samples)

    # Stratified split: with only ~18-22 samples per class, a plain random
    # split can leave some classes with zero val examples. Split per class
    # instead so every class is represented in both splits.
    train_indices, val_indices = splits.stratified_split(full_clean.base_samples, VAL_FRACTION, seed)

    train_ds = SignDataset(raw_dir, label_order, augment=True, augment_multiplier=AUGMENT_MULTIPLIER,
                           none_dir=none_dir)
    train_ds.base_samples = [full_clean.base_samples[i] for i in train_indices]

    val_ds = SignDataset(raw_dir, label_order, augment=False, none_dir=none_dir)
    val_ds.base_samples = [full_clean.base_samples[i] for i in val_indices]

    return train_ds, val_ds


def write_split_manifest(path, train_ds, val_ds, label_order):
    ai_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["path", "label", "split"])
        for name, ds in (("train", train_ds), ("val", val_ds)):
            for p, l in ds.base_samples:
                rel = os.path.relpath(os.path.abspath(p), ai_dir).replace(os.sep, "/")
                w.writerow([rel, label_order[l], name])


def write_app_labels(path, label_order):
    """labels.json in the Android app format with `none` appended at the end."""
    entries = []
    if os.path.exists(APP_LABELS_PATH):
        with open(APP_LABELS_PATH, encoding="utf-8") as f:
            app = json.load(f)
        entries = [e for e in app.get("labels", []) if e.get("label") != "none"]
    if [e.get("label") for e in entries] != [l for l in label_order if l != "none"]:
        entries = [{"label": l, "displayName": l.replace("_", " ").title()} for l in label_order if l != "none"]
    if "none" in label_order:
        entries.append({"label": "none", "displayName": "Not a sign"})
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"_comment": "Generated by ai/training/train.py --include-none. Index = model output index; "
                               "'none' is appended last so the 50 sign indices are unchanged. The app must "
                               "treat 'none' as 'no sign' (never display/speak it).",
                   "labels": entries}, f, indent=2, ensure_ascii=False)


def run_epoch(model, loader, criterion, optimizer, device, train):
    model.train(mode=train)
    total_loss, total_correct, total_n = 0.0, 0, 0
    with torch.set_grad_enabled(train):
        for x, y in loader:
            x, y = x.to(device), y.to(device)
            logits = model(x)
            loss = criterion(logits, y)
            if train:
                optimizer.zero_grad()
                loss.backward()
                optimizer.step()
            total_loss += loss.item() * x.size(0)
            total_correct += (logits.argmax(dim=1) == y).sum().item()
            total_n += x.size(0)
    return total_loss / total_n, total_correct / total_n


def top_confused_pairs(cm, label_order, top_n):
    pairs = []
    n = cm.shape[0]
    for i in range(n):
        for j in range(n):
            if i != j and cm[i, j] > 0:
                pairs.append((cm[i, j], label_order[i], label_order[j]))
    pairs.sort(key=lambda p: -p[0])
    return pairs[:top_n]


def parse_args(argv=None):
    ap = argparse.ArgumentParser(description="Train SignLSTM (see module docstring).")
    ap.add_argument("--data-dir", default=RAW_DIR)
    ap.add_argument("--include-none", action="store_true",
                    help="add the opt-in 'none' (not-a-sign) class from --none-dir as the LAST class")
    ap.add_argument("--none-dir", default=NONE_DIR)
    ap.add_argument("--out-dir", default=None,
                    help="default: ai/models (or ai/models/candidate/none_class with --include-none)")
    ap.add_argument("--epochs", type=int, default=MAX_EPOCHS)
    ap.add_argument("--patience", type=int, default=PATIENCE)
    ap.add_argument("--seed", type=int, default=SEED)
    ap.add_argument("--split", choices=["all", "orig"], default="all",
                    help="all = every clip in data-dir (default, as before); orig = exclude drive_* clips")
    return ap.parse_args(argv)


def main(argv=None):
    global RAW_DIR, MODELS_DIR, LABEL_ORDER, MAX_EPOCHS, PATIENCE
    args = parse_args(argv)
    RAW_DIR = args.data_dir
    MAX_EPOCHS, PATIENCE = args.epochs, args.patience
    none_dir = None
    if args.include_none:
        LABEL_ORDER = splits.load_label_order(LABELS_PATH, include_none=True)
        none_dir = args.none_dir
        n_none = len(splits.list_samples(RAW_DIR, ["none"], none_dir=none_dir))
        if n_none == 0:
            sys.exit(f"--include-none: no .npy files in {none_dir} (run make_none_from_existing.py "
                     "or add recorded non-sign clips)")
        n_syn = len(glob.glob(os.path.join(none_dir, "**", "synthetic_*.npy"), recursive=True))
        print(f"'none' class: {n_none} sequences from {none_dir}"
              + (f" ({n_syn} SYNTHETIC stopgap)" if n_syn else ""))
    MODELS_DIR = args.out_dir or (os.path.join(MODELS_DIR, "candidate", "none_class") if args.include_none
                                  else MODELS_DIR)
    print(f"output folder: {os.path.abspath(MODELS_DIR)}")

    set_seed(args.seed)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    os.makedirs(MODELS_DIR, exist_ok=True)

    n_with_data = sum(1 for lbl in LABEL_ORDER if glob.glob(os.path.join(RAW_DIR, lbl, "*.npy"))
                      or (lbl == "none" and none_dir is not None))
    print(f"Training on {len(LABEL_ORDER)} classes ({n_with_data} have real data, "
          f"{len(LABEL_ORDER) - n_with_data} have none yet and will never be predicted)")

    train_ds, val_ds = build_datasets(RAW_DIR, LABEL_ORDER, none_dir, args.split, args.seed)
    write_split_manifest(os.path.join(MODELS_DIR, "split_manifest.csv"), train_ds, val_ds, LABEL_ORDER)
    if args.include_none:
        write_app_labels(os.path.join(MODELS_DIR, "labels.json"), LABEL_ORDER)
    print(f"train samples (pre-augment base): {len(train_ds.base_samples)}, "
          f"effective with augmentation: {len(train_ds)}")
    print(f"val samples: {len(val_ds)}")

    train_loader = DataLoader(train_ds, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(val_ds, batch_size=BATCH_SIZE, shuffle=False)

    model = SignLSTM(num_classes=len(LABEL_ORDER)).to(device)
    criterion = nn.CrossEntropyLoss(label_smoothing=0.1)
    optimizer = torch.optim.Adam(model.parameters(), lr=LR, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode="min", factor=0.5, patience=5)

    history = []
    best_val_loss = float("inf")
    best_state = None
    epochs_without_improvement = 0

    for epoch in range(1, MAX_EPOCHS + 1):
        train_loss, train_acc = run_epoch(model, train_loader, criterion, optimizer, device, train=True)
        val_loss, val_acc = run_epoch(model, val_loader, criterion, optimizer, device, train=False)
        scheduler.step(val_loss)

        history.append(dict(epoch=epoch, train_loss=train_loss, train_acc=train_acc,
                             val_loss=val_loss, val_acc=val_acc))
        print(f"epoch {epoch:3d}  train_loss={train_loss:.4f} train_acc={train_acc:.3f}  "
              f"val_loss={val_loss:.4f} val_acc={val_acc:.3f}")

        if val_loss < best_val_loss - 1e-4:
            best_val_loss = val_loss
            best_state = {k: v.clone() for k, v in model.state_dict().items()}
            epochs_without_improvement = 0
        else:
            epochs_without_improvement += 1
            if epochs_without_improvement >= PATIENCE:
                print(f"Early stopping at epoch {epoch} (no val improvement for {PATIENCE} epochs)")
                break

    if best_state is not None:
        model.load_state_dict(best_state)

    # --- persist artifacts ---
    torch.save(model.state_dict(), os.path.join(MODELS_DIR, "sign_lstm.pt"))

    with open(os.path.join(MODELS_DIR, "training_history.csv"), "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["epoch", "train_loss", "train_acc", "val_loss", "val_acc"])
        writer.writeheader()
        writer.writerows(history)

    epochs = [h["epoch"] for h in history]
    fig, axes = plt.subplots(1, 2, figsize=(10, 4))
    axes[0].plot(epochs, [h["train_loss"] for h in history], label="train")
    axes[0].plot(epochs, [h["val_loss"] for h in history], label="val")
    axes[0].set_title("Loss"); axes[0].set_xlabel("epoch"); axes[0].legend()
    axes[1].plot(epochs, [h["train_acc"] for h in history], label="train")
    axes[1].plot(epochs, [h["val_acc"] for h in history], label="val")
    axes[1].set_title("Accuracy"); axes[1].set_xlabel("epoch"); axes[1].legend()
    fig.tight_layout()
    fig.savefig(os.path.join(MODELS_DIR, "training_curves.png"), dpi=120)
    plt.close(fig)

    # --- evaluation on held-out val split ---
    model.eval()
    all_preds, all_true = [], []
    with torch.no_grad():
        for x, y in val_loader:
            x = x.to(device)
            preds = model(x).argmax(dim=1).cpu().numpy()
            all_preds.extend(preds.tolist())
            all_true.extend(y.numpy().tolist())

    cm = confusion_matrix(all_true, all_preds, labels=list(range(len(LABEL_ORDER))))

    # Full confusion matrix as CSV (a 50x50 image is workable but a CSV is
    # still easier to load/filter/pivot for anyone who wants the full detail).
    with open(os.path.join(MODELS_DIR, "confusion_matrix.csv"), "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["true\\pred"] + LABEL_ORDER)
        for i, label in enumerate(LABEL_ORDER):
            writer.writerow([label] + cm[i].tolist())

    overall_acc = float(np.mean(np.array(all_true) == np.array(all_preds)))
    precision, recall, f1, support = precision_recall_fscore_support(
        all_true, all_preds, labels=list(range(len(LABEL_ORDER))), zero_division=0
    )
    macro_p, macro_r, macro_f1 = float(np.mean(precision)), float(np.mean(recall)), float(np.mean(f1))

    confused = top_confused_pairs(cm, LABEL_ORDER, TOP_CONFUSED_PAIRS)

    report = classification_report(
        all_true, all_preds, labels=list(range(len(LABEL_ORDER))),
        target_names=LABEL_ORDER, zero_division=0,
    )
    with open(os.path.join(MODELS_DIR, "classification_report.txt"), "w") as f:
        f.write("SignTalk-mini -- 50-class bilingual (FSL+ASL) real-data model\n")
        f.write("All 50 classes are now backed by real video + landmark data: FSL-105\n")
        f.write("(De La Salle University / DOST, CC-BY-4.0) for the 25 FSL classes,\n")
        f.write("ASL Citizen (Microsoft Research) for the 25 ASL classes.\n")
        f.write("Small per-class sample count (~18-22 clips/class, ~4/class held out) --\n")
        f.write("high accuracy here is an encouraging signal on this dataset/signer, not\n")
        f.write("proof of generalization to new signers, lighting, or camera setups.\n\n")
        f.write(f"Overall val accuracy: {overall_acc:.4f}\n")
        f.write(f"Macro precision/recall/F1: {macro_p:.4f} / {macro_r:.4f} / {macro_f1:.4f}\n\n")
        f.write(f"Top {len(confused)} most-confused class pairs (val split):\n")
        for count, true_label, pred_label in confused:
            f.write(f"  {true_label:<20s} predicted as {pred_label:<20s}  x{count}\n")
        f.write("\nFull per-class report:\n")
        f.write(report)
    print(f"\nOverall val accuracy: {overall_acc:.4f}")
    print(f"Macro P/R/F1: {macro_p:.4f} / {macro_r:.4f} / {macro_f1:.4f}")
    print(f"\nTop confused pairs:")
    for count, true_label, pred_label in confused[:10]:
        print(f"  {true_label} -> predicted {pred_label}  x{count}")

    metadata = {
        "labels": LABEL_ORDER,
        "num_classes": len(LABEL_ORDER),
        "input_features": 126,
        "sequence_length": 30,
        "architecture": "bidirectional LSTM (SignLSTM)",
        "data_source": {
            "type": "real",
            "source": "FSL-105 (De La Salle University / DOST, CC-BY-4.0) + ASL Citizen (Microsoft Research)",
            "license": "CC-BY-4.0 (FSL-105) / see ASL Citizen terms",
            "url": "https://data.mendeley.com/datasets/48y2y99mb9/2 ; https://www.microsoft.com/en-us/research/project/asl-citizen/",
            "clips_used": len(train_ds.base_samples) + len(val_ds.base_samples),
            "classes_with_data": n_with_data,
            "classes_total": len(LABEL_ORDER),
        },
        "overall_val_accuracy": overall_acc,
        "macro_precision": macro_p,
        "macro_recall": macro_r,
        "macro_f1": macro_f1,
        "best_val_loss": best_val_loss,
        "epochs_trained": len(history),
        "include_none": bool(args.include_none),
        "split": args.split,
        "seed": args.seed,
    }
    if args.include_none:
        metadata["none_class"] = {
            "index": LABEL_ORDER.index("none"),
            "source_dir": os.path.abspath(none_dir),
            "samples": sum(1 for _, l in train_ds.base_samples + val_ds.base_samples if LABEL_ORDER[l] == "none"),
            "synthetic_files": len(glob.glob(os.path.join(none_dir, "**", "synthetic_*.npy"), recursive=True)),
        }
    with open(os.path.join(MODELS_DIR, "metadata.json"), "w") as f:
        json.dump(metadata, f, indent=2)

    print("\nSaved model + artifacts to", MODELS_DIR)


if __name__ == "__main__":
    main()
