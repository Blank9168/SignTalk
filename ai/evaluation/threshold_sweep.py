"""
Confidence-threshold sweep: how many predictions does each threshold let
through (coverage), and how accurate are the ones it lets through?

For thresholds 0.30, 0.35, ..., 0.95:
  coverage           fraction of test clips whose max softmax >= threshold
                     (and, for a model with a `none` class, whose argmax is not `none`)
  accepted_accuracy  accuracy on those accepted clips
  accepted_error     1 - accepted_accuracy
  correct_overall    fraction of ALL clips that are accepted AND correct
  none_false_accept  fraction of `none` / non-sign clips that would be accepted
                     as a sign (only if a none set is available)

Stability rule (simulates the app's streaming input): each clip's 30-frame
hand-only sequence is fed in growing windows of 12, 13, ..., 30 frames
(12 = RecognitionEngine.minFramesForPrediction), each resampled to 30 like
SequenceBuffer does. A sign is accepted the first time N consecutive windows
agree on the same label with confidence >= threshold. Reported for N = 1, 2,
3, 5. Stored clips are already resampled (FSL-105 clips contain many duplicated
frames), so this approximates the live stream; it does not replace on-device
tests.

Outputs (ai/evaluation/reports/<run-name>/):
  threshold_sweep.csv, threshold_sweep_stability.csv, threshold_sweep.png,
  threshold_recommendation.md

Examples (repo root):
  python ai/evaluation/threshold_sweep.py
  python ai/evaluation/threshold_sweep.py --target-accuracy 0.97 --max-none-accept 0.10
  python ai/evaluation/threshold_sweep.py --test-dir ai/dataset/signer_independent --run-name si_threshold
"""

import csv
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_common as ec  # noqa: E402
from evaluate_model import build_parser, load_test_set  # noqa: E402


def none_files(none_dir):
    if not none_dir or not Path(none_dir).is_dir():
        return []
    return sorted(str(p) for p in Path(none_dir).rglob("*.npy"))


def accept_mask(probs, thr, none_idx):
    pred = probs.argmax(1)
    ok = probs.max(1) >= thr
    if none_idx is not None:
        ok &= pred != none_idx
    return ok, pred


def window_sweep(probs, y, none_probs, thresholds, none_idx):
    rows = []
    for t in thresholds:
        acc_m, pred = accept_mask(probs, t, none_idx)
        n_acc = int(acc_m.sum())
        correct = (pred == y) & acc_m
        row = {"threshold": t, "n": len(y), "n_accepted": n_acc,
               "coverage": n_acc / len(y),
               "accepted_accuracy": (correct.sum() / n_acc) if n_acc else float("nan"),
               "correct_overall": correct.sum() / len(y)}
        row["accepted_error"] = 1 - row["accepted_accuracy"] if n_acc else float("nan")
        if none_probs is not None and len(none_probs):
            fa, _ = accept_mask(none_probs, t, none_idx)
            row["none_n"] = len(none_probs)
            row["none_false_accept"] = float(fa.mean())
        rows.append(row)
    return rows


def stream_probs(predictor, paths, min_frames):
    """Per clip: (n_windows, C) probabilities for growing prefixes k = min_frames..30 of
    its 30-frame hand-only sequence (the same construction as the 12-frame metric in
    evaluate_model.py). Stored clips have a variable number of distinct hand frames
    (often < 20), so the 30-frame resampled sequence is used as a ~1 s, 30 fps
    stand-in for the live stream; every clip then yields 31 - min_frames windows."""
    windows, owner = [], []
    for k, p in enumerate(paths):
        seq30 = ec.preprocess(np.load(p))
        for t in range(min_frames, ec.SEQUENCE_LENGTH + 1):
            windows.append(ec.sample_to_fixed_length(seq30[:t], ec.SEQUENCE_LENGTH))
            owner.append(k)
    if not windows:
        return [np.zeros((0, predictor.num_outputs))] * len(paths)
    probs = predictor.predict_proba(np.stack(windows).astype(np.float32))
    owner = np.array(owner)
    return [probs[owner == k] for k in range(len(paths))]


def stable_decision(seq_probs, thr, n_agree, none_idx):
    """First label reached by n_agree consecutive confident, agreeing windows (or None)."""
    run_label, run_len = None, 0
    for pr in seq_probs:
        lab, conf = int(pr.argmax()), float(pr.max())
        if conf >= thr and lab != none_idx:
            run_len = run_len + 1 if lab == run_label else 1
            run_label = lab
            if run_len >= n_agree:
                return lab
        else:
            run_label, run_len = None, 0
    return None


def stability_sweep(streams, y, none_streams, thresholds, ns, none_idx):
    rows = []
    for n in ns:
        for t in thresholds:
            dec = [stable_decision(s, t, n, none_idx) for s in streams]
            acc = np.array([d is not None for d in dec])
            cor = np.array([d == yy for d, yy in zip(dec, y)])
            row = {"n_agree": n, "threshold": t, "n": len(y), "n_accepted": int(acc.sum()),
                   "coverage": acc.mean(),
                   "accepted_accuracy": (cor.sum() / acc.sum()) if acc.sum() else float("nan"),
                   "correct_overall": cor.mean()}
            if none_streams:
                fa = [stable_decision(s, t, n, none_idx) is not None for s in none_streams]
                row["none_false_accept"] = float(np.mean(fa))
            rows.append(row)
    return rows


def recommend(rows, target, max_none):
    ok = [r for r in rows if r["n_accepted"] > 0 and r["accepted_accuracy"] >= target - 1e-12
          and (max_none is None or "none_false_accept" not in r or r["none_false_accept"] <= max_none + 1e-12)]
    if not ok:
        return None
    return max(ok, key=lambda r: (round(r["coverage"], 9), r["threshold"]))


def write_csv(path, rows):
    keys = list(dict.fromkeys(k for r in rows for k in r))
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        for r in rows:
            w.writerow({k: (f"{v:.4f}" if isinstance(v, float) else v) for k, v in r.items()})


def plot(rows, stab_rows, rec, path, title, app_thr):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    blue, orange, aqua, ink, muted, grid = "#2a78d6", "#eb6834", "#1baf7a", "#0b0b0b", "#898781", "#e1e0d9"
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(12, 4.8))
    cov = [r["coverage"] for r in rows]
    acc = [r["accepted_accuracy"] for r in rows]
    a1.plot(cov, acc, color=blue, lw=2, marker="o", ms=5, label="single window (whole clip)")
    shown = []
    for r in rows:  # label thresholds on the curve, skipping points that would overlap
        if r["n_accepted"] == 0:
            continue
        xy = (r["coverage"], r["accepted_accuracy"])
        if all(abs(xy[0] - x) > 0.06 for x, _ in shown):
            a1.annotate(f"t={r['threshold']:.2f}", xy, textcoords="offset points", xytext=(-4, 7),
                        fontsize=7.5, color=muted, ha="right")
            shown.append(xy)
    s3 = [r for r in stab_rows if r["n_agree"] == 3]
    if s3:
        a1.plot([r["coverage"] for r in s3], [r["accepted_accuracy"] for r in s3], color=orange, lw=2,
                marker="s", ms=4, label="stream, 3 agreeing windows")
    for r in rows:
        if abs(r["threshold"] - app_thr) < 1e-9:
            a1.scatter([r["coverage"]], [r["accepted_accuracy"]], s=110, facecolors="none",
                       edgecolors=ink, lw=1.5, zorder=5, label=f"app threshold {app_thr}")
    if rec:
        a1.scatter([rec["coverage"]], [rec["accepted_accuracy"]], s=160, marker="*", color=aqua,
                   edgecolors="#ffffff", zorder=6, label=f"recommended {rec['threshold']:.2f}")
    a1.set_xlabel("Coverage (fraction of clips accepted)")
    a1.set_ylabel("Accuracy on accepted clips")
    a1.set_title("Accuracy vs coverage", loc="left", fontsize=11)
    a1.legend(fontsize=8, frameon=False, loc="upper center", bbox_to_anchor=(0.5, -0.16), ncol=2)

    th = [r["threshold"] for r in rows]
    a2.plot(th, cov, color=blue, lw=2, label="coverage")
    a2.plot(th, acc, color=orange, lw=2, label="accuracy on accepted")
    if any("none_false_accept" in r for r in rows):
        a2.plot(th, [r.get("none_false_accept", np.nan) for r in rows], color=aqua, lw=2,
                label="non-sign clips accepted (false accept)")
    a2.axvline(app_thr, color=muted, lw=1, ls="--")
    a2.text(app_thr + 0.007, 0.5, f"app {app_thr}", color=muted, fontsize=8)
    a2.set_xlabel("Confidence threshold (max softmax)")
    a2.set_ylabel("Fraction")
    a2.set_title("Metrics by threshold", loc="left", fontsize=11)
    a2.legend(fontsize=8, frameon=False, loc="upper center", bbox_to_anchor=(0.5, -0.16), ncol=3)
    accs = [v for v in acc + [r["accepted_accuracy"] for r in s3] if v == v]
    a1.set_ylim(max(0.0, min(accs) - 0.05) if accs else 0, 1.01)
    a1.set_xlim(-0.02, 1.04)
    a2.set_ylim(-0.02, 1.04)
    for a in (a1, a2):
        a.grid(color=grid, lw=0.6)
        for s in ("top", "right"):
            a.spines[s].set_visible(False)
        for s in ("left", "bottom"):
            a.spines[s].set_color(grid)
        a.tick_params(colors=muted)
    fig.suptitle(title, fontsize=11, x=0.01, ha="left", color=ink)
    fig.tight_layout()
    fig.savefig(path, dpi=150, facecolor="#ffffff")
    plt.close(fig)


def main(argv=None):
    ap = build_parser()
    ap.description = __doc__
    ap.add_argument("--none-dir", default=None,
                    help="folder of non-sign .npy sequences (searched recursively). Default: ai/dataset/none "
                         "for a 50-class model; for a model trained WITH a none class only the none clips of "
                         "its own validation split are used (the rest were trained on)")
    ap.add_argument("--target-accuracy", type=float, default=0.95)
    ap.add_argument("--max-none-accept", type=float, default=None,
                    help="also require the non-sign false-accept rate to be <= this (needs a none set)")
    ap.add_argument("--alt-max-none-accept", type=float, default=0.20,
                    help="when a none set exists, also report the best threshold under this false-accept cap")
    ap.add_argument("--stability-n", default="1,2,3,5")
    ap.add_argument("--min-frames", type=int, default=ec.APP_MIN_FRAMES)
    ap.add_argument("--t-start", type=float, default=0.30)
    ap.add_argument("--t-stop", type=float, default=0.95)
    ap.add_argument("--t-step", type=float, default=0.05)
    args = ap.parse_args(argv)

    pred = ec.Predictor(args.model)
    label_order = ec.load_labels_any(args.labels, pred.num_outputs)
    none_idx = label_order.index(ec.NONE_LABEL) if ec.NONE_LABEL in label_order else None
    samples, _, desc, dir_none = load_test_set(args, label_order)
    if none_idx is not None:  # a `none` class in the model: its test clips are the none set
        dir_none += [p for p, l in samples if l == none_idx]
        samples = [(p, l) for p, l in samples if l != none_idx]
    none_dir = args.none_dir or (str(ec.DEFAULT_NONE_DIR) if none_idx is None else None)
    npaths = sorted(set(dir_none + none_files(none_dir)))
    thresholds = [round(x, 2) for x in np.arange(args.t_start, args.t_stop + 1e-9, args.t_step)]
    ns = [int(x) for x in args.stability_n.split(",") if x.strip()]
    run = args.run_name or (Path(args.model).stem + "_threshold_sweep")
    out_dir = Path(args.out_root) / run
    out_dir.mkdir(parents=True, exist_ok=True)

    paths = [p for p, _ in samples]
    y = np.array([l for _, l in samples])
    probs = pred.predict_proba(ec.load_sequences(paths))
    nprobs = pred.predict_proba(ec.load_sequences(npaths)) if npaths else None
    rows = window_sweep(probs, y, nprobs, thresholds, none_idx)

    streams = stream_probs(pred, paths, args.min_frames)
    nstreams = stream_probs(pred, npaths, args.min_frames) if npaths else []
    stab = stability_sweep(streams, y, nstreams, thresholds, ns, none_idx)

    write_csv(out_dir / "threshold_sweep.csv", rows)
    write_csv(out_dir / "threshold_sweep_stability.csv", stab)
    rec = recommend(rows, args.target_accuracy, args.max_none_accept)
    rec3 = recommend([r for r in stab if r["n_agree"] == 3], args.target_accuracy, args.max_none_accept)
    plot(rows, stab, rec, out_dir / "threshold_sweep.png",
         f"Threshold sweep - {run} (n={len(y)}{', none set n=' + str(len(npaths)) if npaths else ''})",
         ec.APP_THRESHOLD)

    none_note = ""
    if npaths:
        sources = sorted({ec.classify_source(p, ec.NONE_LABEL) for p in npaths})
        none_note = (f"{len(npaths)} non-sign sequences from {', '.join(f'`{s}`' for s in sources)}"
                     + (" - SYNTHETIC stopgap data; replace with recorded non-sign clips before citing"
                        if "synthetic_none" in sources else ""))
    app_row = next(r for r in rows if abs(r["threshold"] - ec.APP_THRESHOLD) < 1e-9)
    lines = [f"# Threshold recommendation: {run}", "",
             f"- Model: `{ec.rel(args.model)}`", f"- Test set: {desc} ({len(y)} clips)",
             f"- Non-sign set: {none_note or 'none available (add ai/dataset/none/ to measure false accepts)'}",
             f"- Rule: highest coverage with accuracy on accepted clips >= {args.target_accuracy:.2f}"
             + (f" and non-sign false-accept <= {args.max_none_accept:.2f}" if args.max_none_accept is not None else ""),
             ""]

    def fmt(r):
        s = (f"threshold **{r['threshold']:.2f}**: coverage {r['coverage']:.3f}, accepted accuracy "
             f"{r['accepted_accuracy']:.3f}, error {1 - r['accepted_accuracy']:.3f}")
        if "none_false_accept" in r:
            s += f", non-sign false accept {r['none_false_accept']:.3f}"
        return s

    lines.append(f"- Recommended (single window): {fmt(rec) if rec else 'no threshold meets the target'}")
    lines.append(f"- Recommended (stream, 3 agreeing windows): {fmt(rec3) if rec3 else 'no threshold meets the target'}")
    alt = alt3 = None
    if npaths and args.max_none_accept is None and args.alt_max_none_accept is not None:
        alt = recommend(rows, args.target_accuracy, args.alt_max_none_accept)
        alt3 = recommend([r for r in stab if r["n_agree"] == 3], args.target_accuracy, args.alt_max_none_accept)
        cap = f"also capping non-sign false accept at {args.alt_max_none_accept:.2f}"
        lines.append(f"- Alternative, {cap} (single window): {fmt(alt) if alt else 'no threshold meets both'}")
        lines.append(f"- Alternative, {cap} (stream, 3 agreeing windows): {fmt(alt3) if alt3 else 'no threshold meets both'}")
    lines.append(f"- Current app threshold {ec.APP_THRESHOLD}: {fmt(app_row)}")
    lines += ["", "## Single window (whole clip)", "",
              "| Threshold | Coverage | Accepted accuracy | Error on accepted | Correct overall | Non-sign accepted |",
              "|---|---|---|---|---|---|"]
    for r in rows:
        lines.append(f"| {r['threshold']:.2f} | {r['coverage']:.3f} | {r['accepted_accuracy']:.3f} | "
                     f"{r['accepted_error']:.3f} | {r['correct_overall']:.3f} | "
                     f"{r.get('none_false_accept', float('nan')):.3f} |")
    lines += ["", "## Streaming stability rule (coverage / accepted accuracy / non-sign accepted)", "",
              "| Threshold | " + " | ".join(f"N={n}" for n in ns) + " |", "|---|" + "---|" * len(ns)]
    for t in thresholds:
        cells = []
        for n in ns:
            r = next(x for x in stab if x["n_agree"] == n and x["threshold"] == t)
            cells.append(f"{r['coverage']:.2f} / {r['accepted_accuracy']:.3f} / {r.get('none_false_accept', float('nan')):.2f}")
        lines.append(f"| {t:.2f} | " + " | ".join(cells) + " |")
    lines += ["", "Notes:",
              "- Label smoothing 0.1 caps the softmax: confidences rarely exceed ~0.9, so thresholds >= 0.9 "
              "reject almost everything regardless of correctness.",
              "- On a random per-clip split almost every clip is correct, so accuracy hardly changes with the "
              "threshold. The threshold is mainly a guard against NON-signs and new signers: re-run this on "
              "the signer-independent set and a recorded none set before fixing the app value.",
              ""]
    (out_dir / "threshold_recommendation.md").write_text("\n".join(lines), encoding="utf-8")
    with open(out_dir / "threshold_recommendation.json", "w", encoding="utf-8") as f:
        json.dump({"recommended_single_window": rec, "recommended_stream_n3": rec3,
                   "alternative_with_none_cap": alt, "alternative_with_none_cap_stream_n3": alt3,
                   "alt_max_none_accept": args.alt_max_none_accept, "app_threshold_row": app_row,
                   "target_accuracy": args.target_accuracy, "max_none_accept": args.max_none_accept,
                   "test_set": desc, "n": int(len(y)), "none_n": len(npaths)}, f, indent=2, default=float)

    print(f"{len(y)} test clips, {len(npaths)} non-sign clips -> {out_dir}")
    print(f"{'thr':>5} {'cover':>6} {'acc':>6} {'noneFA':>7}")
    for r in rows:
        print(f"{r['threshold']:5.2f} {r['coverage']:6.3f} {r['accepted_accuracy']:6.3f} "
              f"{r.get('none_false_accept', float('nan')):7.3f}")
    print("RECOMMENDED threshold (single window):", f"{rec['threshold']:.2f}" if rec else "none meets target")
    print("RECOMMENDED threshold (3 agreeing windows):", f"{rec3['threshold']:.2f}" if rec3 else "none meets target")
    if alt is not None or alt3 is not None:
        print(f"  with non-sign false accept <= {args.alt_max_none_accept:.2f}: single "
              f"{alt['threshold'] if alt else '-'}, 3 agreeing windows {alt3['threshold'] if alt3 else '-'}")


if __name__ == "__main__":
    main()
