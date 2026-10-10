"""
Evaluate a SignTalk model on a held-out test set and write manuscript-ready
metrics.

Outputs (ai/evaluation/reports/<run-name>/):
  summary.md                 human-readable summary of everything below
  metrics.json               accuracy, macro/weighted P/R/F1, top-3, 12-frame accuracy
  classification_report.txt  sklearn per-class report
  per_class.csv              precision / recall / F1 / support per class (+ language)
  confusion_matrix.csv       rows = true, cols = predicted (counts)
  confusion_matrix.png       row-normalised heatmap with counts
  most_confused_pairs.csv    top-N off-diagonal (true -> predicted) pairs, count, rate
  per_group.csv              accuracy per signer (signer-independent sets) / per source
  predictions.csv            one row per clip: true, predicted, confidence, top-3

Test set (pick one):
  default            the validation split train.py used for this model
                     (split_manifest.csv next to the model if train.py wrote one,
                     otherwise the deployed model's split: train.py's seed-42 20%
                     per-class split of the 889 non-Drive clips = 175 clips)
  --split all        train.py's split over everything now in raw_iso/
  --test-dir DIR     a separate folder: DIR/<label>/*.npy|*.mp4 or
                     DIR/<signer>/<label>/*.npy|*.mp4 (videos are converted with
                     the training extractor first)

Examples (run from the repo root):
  python ai/evaluation/evaluate_model.py
  python ai/evaluation/evaluate_model.py --tflite android/SignTalk/app/src/main/assets/sign_lstm_iso.tflite
  python ai/evaluation/evaluate_model.py --test-dir ai/dataset/signer_independent --run-name signer_independent
"""

import argparse
import csv
import json
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_common as ec  # noqa: E402

FSL_NUMBERS = ["isa", "dalawa", "tatlo", "apat", "lima", "anim", "pito", "walo", "siyam", "sampu"]
PAIR_REASONS = {
    frozenset({"father", "mother"}): "ASL FATHER/MOTHER use the same open-5 handshape and tap; they differ only in location (forehead vs chin). Features are wrist-centred hand landmarks with no face reference, so location is invisible to the model.",
    frozenset({"boy", "girl"}): "ASL BOY/GIRL differ mainly by location on the face (forehead vs cheek/jaw), which wrist-centred hand landmarks do not encode.",
    frozenset({"berde", "sampu"}): "Similar handshape and wrist movement in the FSL-105 recordings (team observation); differences are small finger details.",
    frozenset({"thank_you", "youre_welcome"}): "Likely: both use a flat hand moving away from the chin/body area; check the clips - the distinguishing part is short.",
    frozenset({"understand", "dont_understand"}): "Likely: DONT UNDERSTAND contains the UNDERSTAND movement plus a negation; partial windows look alike.",
    frozenset({"know", "dont_know"}): "DONT KNOW starts like KNOW (hand at the forehead) and differs only in the second half.",
}


def likely_reason(a, b):
    key = frozenset({a, b})
    if key in PAIR_REASONS:
        return PAIR_REASONS[key]
    if a in FSL_NUMBERS and b in FSL_NUMBERS:
        return ("FSL number handshapes differ by which/how many fingers are extended or touch the thumb; "
                "one occluded or partly-bent finger flips the class (e.g. pito vs apat/walo).")
    if a.startswith("magandang_") and b.startswith("magandang_"):
        return "Shared MAGANDANG component; only the second sign (umaga/hapon/gabi) differs."
    if a.startswith("good_") and b.startswith("good_"):
        return "Shared GOOD component (built from the same ASL Citizen clips); only the second word differs."
    return "(add explanation: compare handshape, movement, location, one vs two hands)"


# ---------------------------------------------------------------- metrics
def compute_metrics(y_true, probs, label_order, prefix_probs=None):
    from sklearn.metrics import precision_recall_fscore_support
    y_pred = probs.argmax(1)
    labels = list(range(len(label_order)))
    present = sorted(set(y_true.tolist()))
    out = {"n": int(len(y_true)), "accuracy": float(np.mean(y_pred == y_true))}
    for avg in ("macro", "weighted"):
        # macro over classes PRESENT in the test set (a class with no test clips
        # would otherwise count as F1 = 0)
        p, r, f, _ = precision_recall_fscore_support(y_true, y_pred, labels=present,
                                                     average=avg, zero_division=0)
        out[f"{avg}_precision"], out[f"{avg}_recall"], out[f"{avg}_f1"] = float(p), float(r), float(f)
    top3 = np.argsort(-probs, axis=1)[:, :3]
    out["top3_accuracy"] = float(np.mean([t in row for t, row in zip(y_true, top3)]))
    out["mean_confidence"] = float(probs.max(1).mean())
    out["classes_in_test_set"] = len(present)
    out["classes_in_model"] = len(labels)
    if prefix_probs is not None:
        out[f"accuracy_first_{ec.APP_MIN_FRAMES}_frames"] = float(np.mean(prefix_probs.argmax(1) == y_true))
    return out


def top_confused(cm, label_order, top_n):
    support = cm.sum(axis=1)
    pairs = []
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            if i != j and cm[i, j] > 0:
                pairs.append({"true": label_order[i], "predicted": label_order[j],
                              "count": int(cm[i, j]), "true_support": int(support[i]),
                              "rate": float(cm[i, j] / support[i]) if support[i] else 0.0,
                              "reverse_count": int(cm[j, i])})
    pairs.sort(key=lambda p: (-p["count"], -p["rate"], p["true"]))
    for p in pairs:
        p["likely_reason"] = likely_reason(p["true"], p["predicted"])
    return pairs[:top_n]


def plot_confusion(cm, label_order, path, title):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    from matplotlib.colors import LinearSegmentedColormap
    # single-hue sequential ramp (light -> dark blue), surface colour at zero
    cmap = LinearSegmentedColormap.from_list(
        "seq_blue", ["#fcfcfb", "#cde2fb", "#86b6ef", "#3987e5", "#1c5cab", "#0d366b"])
    support = cm.sum(axis=1, keepdims=True)
    norm = np.divide(cm, support, out=np.zeros_like(cm, dtype=float), where=support > 0)
    n = len(label_order)
    size = max(7, 0.26 * n + 3)
    fig, ax = plt.subplots(figsize=(size, size * 0.92))
    im = ax.imshow(norm, cmap=cmap, vmin=0, vmax=1)
    ax.set_xticks(range(n))
    ax.set_yticks(range(n))
    fs = 7 if n > 30 else 9
    ax.set_xticklabels(label_order, rotation=90, fontsize=fs, color="#52514e")
    ax.set_yticklabels(label_order, fontsize=fs, color="#52514e")
    ax.set_xlabel("Predicted sign", color="#0b0b0b")
    ax.set_ylabel("True sign", color="#0b0b0b")
    ax.set_title(title, fontsize=11, color="#0b0b0b", loc="left")
    for i in range(n):
        for j in range(n):
            if cm[i, j]:
                ax.text(j, i, str(int(cm[i, j])), ha="center", va="center", fontsize=fs - 1,
                        color="#ffffff" if norm[i, j] > 0.45 else "#0b0b0b",
                        fontweight="normal" if i == j else "bold")
    for s in ax.spines.values():
        s.set_color("#e1e0d9")
    cb = fig.colorbar(im, ax=ax, fraction=0.035, pad=0.02)
    cb.set_label("fraction of true-class clips (row-normalised)", color="#52514e")
    cb.outline.set_edgecolor("#e1e0d9")
    fig.tight_layout()
    fig.savefig(path, dpi=150, facecolor="#ffffff")
    plt.close(fig)


def write_report(out_dir, label_order, samples, probs, prefix_probs, info, top_n=15, groups=None):
    """samples: list of (path, label_idx); groups: optional list of group ids."""
    from sklearn.metrics import classification_report, confusion_matrix, precision_recall_fscore_support
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    y_true = np.array([l for _, l in samples])
    y_pred = probs.argmax(1)
    conf = probs.max(1)
    lang = ec.label_language_map()
    metrics = compute_metrics(y_true, probs, label_order, prefix_probs)
    metrics.update(info)
    idx = list(range(len(label_order)))
    cm = confusion_matrix(y_true, y_pred, labels=idx)

    with open(out_dir / "confusion_matrix.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["true\\pred"] + label_order)
        for i, lab in enumerate(label_order):
            w.writerow([lab] + cm[i].tolist())
    plot_confusion(cm, label_order, out_dir / "confusion_matrix.png",
                   f"Confusion matrix - {info.get('run_name', '')} (n={len(y_true)}, acc={metrics['accuracy']:.3f})")

    rep = classification_report(y_true, y_pred, labels=idx, target_names=label_order, zero_division=0, digits=4)
    with open(out_dir / "classification_report.txt", "w", encoding="utf-8") as f:
        f.write(f"Model: {info.get('model')}\nTest set: {info.get('test_set')}\n\n{rep}")
    p, r, f1, s = precision_recall_fscore_support(y_true, y_pred, labels=idx, zero_division=0)
    with open(out_dir / "per_class.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["label", "language", "precision", "recall", "f1", "support"])
        for i, lab in enumerate(label_order):
            w.writerow([lab, lang.get(lab, ""), f"{p[i]:.4f}", f"{r[i]:.4f}", f"{f1[i]:.4f}", int(s[i])])

    pairs = top_confused(cm, label_order, top_n)
    with open(out_dir / "most_confused_pairs.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["true", "predicted", "count", "true_support", "rate",
                                          "reverse_count", "likely_reason"])
        w.writeheader()
        for pr in pairs:
            w.writerow({**pr, "rate": f"{pr['rate']:.3f}"})

    # per group (signer) and per data source
    sources = [ec.classify_source(pth, label_order[l]) for pth, l in samples]
    group_rows = []
    for kind, keys in (("signer", groups), ("source", sources), ("language", [lang.get(label_order[l], "") for l in y_true])):
        if keys is None:
            continue
        acc = defaultdict(list)
        for k, ok in zip(keys, y_pred == y_true):
            acc[k].append(ok)
        for k in sorted(acc):
            group_rows.append({"grouping": kind, "group": k, "n": len(acc[k]),
                               "correct": int(np.sum(acc[k])), "accuracy": f"{np.mean(acc[k]):.4f}"})
    with open(out_dir / "per_group.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["grouping", "group", "n", "correct", "accuracy"])
        w.writeheader()
        w.writerows(group_rows)

    with open(out_dir / "predictions.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["file", "group", "source", "true", "predicted", "confidence", "correct", "top3"])
        for k, (pth, l) in enumerate(samples):
            top3 = np.argsort(-probs[k])[:3]
            w.writerow([ec.rel(pth), groups[k] if groups else "", sources[k], label_order[l],
                        label_order[y_pred[k]], f"{conf[k]:.4f}", int(y_pred[k] == l),
                        "; ".join(f"{label_order[t]}={probs[k, t]:.3f}" for t in top3)])

    with open(out_dir / "metrics.json", "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)

    # summary.md
    m = metrics
    lines = [f"# Evaluation: {info.get('run_name')}", "",
             f"- Date: {date.today().isoformat()}",
             f"- Model: `{info.get('model')}` ({info.get('model_kind')}, {len(label_order)} classes)",
             f"- Test set: {info.get('test_set')}",
             f"- Clips: {m['n']} across {m['classes_in_test_set']} classes", "",
             "| Metric | Value |", "|---|---|",
             f"| Accuracy | {m['accuracy']:.4f} ({int(round(m['accuracy'] * m['n']))}/{m['n']}) |",
             f"| Macro precision / recall / F1 | {m['macro_precision']:.4f} / {m['macro_recall']:.4f} / {m['macro_f1']:.4f} |",
             f"| Weighted precision / recall / F1 | {m['weighted_precision']:.4f} / {m['weighted_recall']:.4f} / {m['weighted_f1']:.4f} |",
             f"| Top-3 accuracy | {m['top3_accuracy']:.4f} |",
             f"| Mean max-softmax confidence | {m['mean_confidence']:.4f} |"]
    k = f"accuracy_first_{ec.APP_MIN_FRAMES}_frames"
    if k in m:
        lines.append(f"| Accuracy from first {ec.APP_MIN_FRAMES} of 30 frames (app's earliest prediction) | {m[k]:.4f} |")
    if "tflite_parity" in info:
        tp = info["tflite_parity"]
        lines.append(f"| TFLite vs PyTorch: argmax agreement / max prob diff | {tp['argmax_agreement']:.4f} / {tp['max_abs_prob_diff']:.2e} |")
    lines += ["", "Macro averages are over the classes present in the test set.", "",
              "## Accuracy by group", "", "| Grouping | Group | n | Accuracy |", "|---|---|---|---|"]
    lines += [f"| {g['grouping']} | {g['group']} | {g['n']} | {g['accuracy']} |" for g in group_rows]
    lines += ["", f"## Top {len(pairs)} most-confused pairs", "",
              "| True | Predicted as | Count | Rate (of true class) | Likely reason |", "|---|---|---|---|---|"]
    lines += [f"| {pr['true']} | {pr['predicted']} | {pr['count']} | {pr['rate']:.2f} ({pr['count']}/{pr['true_support']}) | {pr['likely_reason']} |"
              for pr in pairs]
    if not pairs:
        lines.append("| - | - | 0 | - | no errors |")
    if info.get("caveats"):
        lines += ["", "## Caveats", ""] + [f"- {c}" for c in info["caveats"]]
    lines += ["", "Files: `metrics.json`, `classification_report.txt`, `per_class.csv`, `confusion_matrix.csv`, "
              "`confusion_matrix.png`, `most_confused_pairs.csv`, `per_group.csv`, `predictions.csv`."]
    (out_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return metrics, pairs


# ------------------------------------------------------------- test sets
def load_test_set(args, label_order):
    """Returns (samples [(path,label_idx)], groups or None, description, none_paths)."""
    none_paths = []
    if args.test_dir:
        items = ec.collect_test_dir(args.test_dir, label_order, args.landmarks_cache)
        samples = [(p, l) for p, l, g, lab in items if l >= 0]
        groups = [g for p, l, g, lab in items if l >= 0]
        none_paths = [p for p, l, g, lab in items if l < 0]
        if not samples:
            raise SystemExit(f"No clips found under {args.test_dir} (expected <label>/ or <signer>/<label>/ folders)")
        return samples, (groups if len(set(groups)) > 1 or groups[0] != "all" else None), \
            f"separate test folder `{ec.rel(args.test_dir)}`", none_paths
    samples, desc = ec.resolve_split(args.model, args.split, args.manifest, args.data_dir, label_order)
    if desc == "orig":
        desc = ("train.py validation split of the original 889 clips (seed 42, 20% per class) - "
                "the held-out split of the deployed model")
    elif desc == "all":
        desc = "train.py validation split over every clip now in raw_iso/ (seed 42, 20% per class)"
    return samples, None, desc, none_paths


def build_parser():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--model", default=str(ec.default_model_path()), help="PyTorch state dict (.pt) or .tflite")
    ap.add_argument("--tflite", default=None, help="also evaluate this .tflite and check parity with --model")
    ap.add_argument("--labels", default=None, help="labels_50.json (default), metadata.json or app labels.json")
    ap.add_argument("--split", default="auto", choices=["auto", "orig", "all", "manifest"])
    ap.add_argument("--manifest", default=None, help="split_manifest.csv written by train.py")
    ap.add_argument("--data-dir", default=str(ec.DEFAULT_DATA_DIR))
    ap.add_argument("--test-dir", default=None, help="separate test folder (see docstring)")
    ap.add_argument("--landmarks-cache", default=None, help="where extracted video landmarks are cached")
    ap.add_argument("--run-name", default=None)
    ap.add_argument("--out-root", default=str(ec.REPORTS_DIR))
    ap.add_argument("--top-pairs", type=int, default=15)
    return ap


def main(argv=None):
    args = build_parser().parse_args(argv)
    pred = ec.Predictor(args.model)
    label_order = ec.load_labels_any(args.labels, pred.num_outputs)
    samples, groups, desc, none_paths = load_test_set(args, label_order)
    run = args.run_name or (Path(args.model).stem + ("_" + Path(args.test_dir).name if args.test_dir else "_val"))
    out_dir = Path(args.out_root) / run
    print(f"Evaluating {args.model} on {len(samples)} clips -> {out_dir}")

    X = ec.load_sequences([p for p, _ in samples])
    Xp = ec.load_sequences([p for p, _ in samples], prefix_frames=ec.APP_MIN_FRAMES)
    probs, prefix_probs = pred.predict_proba(X), pred.predict_proba(Xp)
    info = {"run_name": run, "model": ec.rel(args.model), "model_kind": pred.kind, "test_set": desc,
            "caveats": []}
    if not args.test_dir:
        info["caveats"].append("Random per-clip split: the same signers (FSL-105 signer(s), the team phone "
                               "signer) appear in both train and test, so this is NOT a signer-independent result.")
    if none_paths:
        nprobs = pred.predict_proba(ec.load_sequences(none_paths))
        acc_rate = float(np.mean(nprobs.max(1) >= ec.APP_THRESHOLD))
        info["none_clips"] = len(none_paths)
        info["none_accepted_at_app_threshold"] = acc_rate
        info["caveats"].append(f"{len(none_paths)} `none` clips excluded from the metrics; {acc_rate:.1%} of them "
                               f"would be accepted as a sign at the app threshold {ec.APP_THRESHOLD} "
                               "(see threshold_sweep.py).")

    if args.tflite:
        tpred = ec.Predictor(args.tflite)
        tprobs = tpred.predict_proba(X)
        info["tflite_parity"] = {"model": ec.rel(args.tflite),
                                 "argmax_agreement": float(np.mean(tprobs.argmax(1) == probs.argmax(1))),
                                 "max_abs_prob_diff": float(np.abs(tprobs - probs).max())}
        tinfo = dict(info, run_name=run + "_tflite", model=ec.rel(args.tflite), model_kind="tflite")
        write_report(Path(args.out_root) / (run + "_tflite"), label_order, samples, tprobs,
                     tpred.predict_proba(Xp), tinfo, args.top_pairs, groups)

    metrics, pairs = write_report(out_dir, label_order, samples, probs, prefix_probs, info, args.top_pairs, groups)
    print(f"accuracy {metrics['accuracy']:.4f} | macro P/R/F1 {metrics['macro_precision']:.4f} / "
          f"{metrics['macro_recall']:.4f} / {metrics['macro_f1']:.4f} | weighted F1 {metrics['weighted_f1']:.4f}")
    if "tflite_parity" in info:
        print(f"TFLite parity: argmax agreement {info['tflite_parity']['argmax_agreement']:.4f}, "
              f"max prob diff {info['tflite_parity']['max_abs_prob_diff']:.2e}")
    for pr in pairs[:5]:
        print(f"  {pr['true']} -> {pr['predicted']} x{pr['count']} ({pr['rate']:.0%})")
    print(f"Wrote {out_dir}")
    return metrics


if __name__ == "__main__":
    main()
