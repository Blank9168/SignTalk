"""
Signer-independent evaluation: extract landmarks from clips of people who are
NOT in any training data, then evaluate the model on them with accuracy
reported per signer.

Expected layout (see ai/evaluation/signer_independent/README.md):
  ai/dataset/signer_independent/<signer_id>/<label>/<clip>.mp4
  e.g. ai/dataset/signer_independent/S01/isa/isa_1.mp4
Label folder names must match labels_50.json slugs (isa, dalawa, ..., married).
A `none` folder per signer is allowed (non-sign movements) and is reported as a
false-accept rate instead of being scored as a class.

Steps:
  1. landmarks are extracted with the training pipeline
     (dataset/landmarks.HandLandmarkExtractor + dataset/import_user_clips.
     extract_video_landmarks: 45 evenly-spaced frames -> hand frames -> 30)
     and cached in ai/dataset/signer_independent_landmarks/<signer>/<label>/
  2. evaluate_model.py runs on them -> reports/<run-name>/ (per_group.csv has
     accuracy per signer, summary.md has the table)
  3. threshold_sweep.py runs on them -> reports/<run-name>_threshold/
  4. recording_coverage.csv lists clips per signer x label so missing
     recordings are easy to spot

Usage (repo root; needs mediapipe==0.10.14 + opencv-python for step 1):
  python ai/evaluation/prepare_signer_independent.py
  python ai/evaluation/prepare_signer_independent.py --input-dir D:/si_clips --run-name signer_independent_v1
"""

import argparse
import csv
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_common as ec  # noqa: E402
import evaluate_model  # noqa: E402
import threshold_sweep  # noqa: E402


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--input-dir", default=str(ec.DATASET_DIR / "signer_independent"))
    ap.add_argument("--landmarks-dir", default=None,
                    help="default: <input-dir>_landmarks next to the input folder")
    ap.add_argument("--model", default=str(ec.default_model_path()))
    ap.add_argument("--tflite", default=None)
    ap.add_argument("--labels", default=None)
    ap.add_argument("--run-name", default="signer_independent")
    ap.add_argument("--out-root", default=str(ec.REPORTS_DIR))
    ap.add_argument("--clips-per-sign", type=int, default=3, help="target per signer, for the coverage check")
    ap.add_argument("--skip-sweep", action="store_true")
    args = ap.parse_args()

    inp = Path(args.input_dir)
    if not inp.is_dir():
        raise SystemExit(f"{inp} not found. Record clips as described in "
                         "ai/evaluation/signer_independent/README.md first.")
    cache = Path(args.landmarks_dir) if args.landmarks_dir else inp.parent / (inp.name + "_landmarks")
    pred = ec.Predictor(args.model)
    label_order = ec.load_labels_any(args.labels, pred.num_outputs)

    # 1. extract (cached) + collect
    items = ec.collect_test_dir(inp, label_order, cache)
    signers = sorted({g for _, _, g, _ in items})
    print(f"{len(items)} clips from {len(signers)} signer(s): {', '.join(signers)}")

    # coverage check
    counts = Counter((g, lab) for _, _, g, lab in items)
    out_dir = Path(args.out_root) / args.run_name
    out_dir.mkdir(parents=True, exist_ok=True)
    signs = [l for l in label_order if l != ec.NONE_LABEL]
    missing = 0
    with open(out_dir / "recording_coverage.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["label"] + signers + ["total"])
        for lab in signs + [ec.NONE_LABEL]:
            row = [counts.get((s, lab), 0) for s in signers]
            if lab != ec.NONE_LABEL:
                missing += sum(max(0, args.clips_per_sign - c) for c in row)
            w.writerow([lab] + row + [sum(row)])
    if missing:
        print(f"NOTE: {missing} clips short of {args.clips_per_sign} per sign per signer "
              f"(see {out_dir / 'recording_coverage.csv'})")

    # 2. evaluate (per-signer accuracy lands in per_group.csv / summary.md)
    ev_args = ["--model", args.model, "--test-dir", str(inp), "--landmarks-cache", str(cache),
               "--run-name", args.run_name, "--out-root", args.out_root]
    if args.tflite:
        ev_args += ["--tflite", args.tflite]
    if args.labels:
        ev_args += ["--labels", args.labels]
    evaluate_model.main(ev_args)

    # per-signer table to the console
    rows = list(csv.DictReader(open(out_dir / "per_group.csv", encoding="utf-8")))
    print("\nPer-signer accuracy:")
    for r in rows:
        if r["grouping"] == "signer":
            print(f"  {r['group']:<12} {r['correct']:>4}/{r['n']:<4} = {float(r['accuracy']):.3f}")

    # 3. threshold sweep on the same clips
    if not args.skip_sweep:
        sw_args = ["--model", args.model, "--test-dir", str(inp), "--landmarks-cache", str(cache),
                   "--run-name", args.run_name + "_threshold", "--out-root", args.out_root]
        if args.labels:
            sw_args += ["--labels", args.labels]
        threshold_sweep.main(sw_args)


if __name__ == "__main__":
    main()
