"""
Dataset profile for the manuscript: how many clips, per class, per language,
per source, per split, and (where known) per signer / recording session.

Reads the landmark files the model is trained on (default ai/dataset/raw_iso/,
the aspect-corrected copy of raw/ that train.py uses) and writes:

  ai/evaluation/reports/dataset_profile.md            tables for the manuscript
  ai/evaluation/reports/dataset_profile_per_class.csv one row per class
  ai/evaluation/reports/dataset_profile_samples.csv   one row per clip
  ai/evaluation/reports/dataset_profile_summary.json  headline numbers
  ai/evaluation/signer_sessions_template.csv          (only if missing, or --write-template)

Sources are inferred from file names (see eval_common.classify_source):
  <digits>.npy -> FSL-105, asl_<n>.npy -> ASL Citizen,
  user_IMG_<nnnn>.npy -> team phone clips, drive_<id>.npy -> Google Drive phone clips.
Signers and sessions are NOT recoverable from the landmark files. Fill in a copy
of signer_sessions_template.csv, save it as ai/evaluation/signer_sessions.csv,
and re-run: the profile then counts signers / sessions per class and source.
The `label` and `file` columns accept wildcards (e.g. file = drive_* , label = *)
so one row can cover a whole group.

Usage (repo root):
  python ai/evaluation/dataset_profile.py
  python ai/evaluation/dataset_profile.py --signers ai/evaluation/signer_sessions.csv
"""

import argparse
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import eval_common as ec  # noqa: E402

SOURCE_ORDER = ["fsl105", "asl_citizen", "asl_citizen_concat", "team_phone", "drive_phone",
                "synthetic_none", "recorded_none", "unknown"]
SOURCE_SHORT = {"fsl105": "FSL-105", "asl_citizen": "ASL Citizen", "asl_citizen_concat": "ASL Citizen (concat.)",
                "team_phone": "Team phone", "drive_phone": "Drive phone", "synthetic_none": "Synthetic none",
                "recorded_none": "Recorded none", "unknown": "Unknown"}
KNOWN_SIGNER_INFO = {
    "fsl105": "Signer IDs are not kept in the file names. Check the FSL-105 data paper / Mendeley page for the "
              "number of signers and enter it in signer_sessions.csv.",
    "asl_citizen": "Crowd-sourced dataset with many signers; per-clip signer IDs were not kept when the clips were "
                   "imported, so the 6 clips per class may or may not come from 6 different people.",
    "asl_citizen_concat": "Phrase built by concatenating ASL Citizen word clips; component words can come from "
                          "different signers.",
    "team_phone": "Phone clips (iPhone IMG_ numbering, 350x640 portrait). Earlier notes describe them as one "
                  "person / one session - confirm who signed them.",
    "drive_phone": "Phone clips collected through Google Drive (file ids only). Who recorded them, and on how "
                   "many days, must be filled in by hand.",
}
IMG_GAP = 40  # a jump of more than this in IMG_ numbers starts a new block (session hint)


def img_number(name):
    m = re.search(r"IMG_(\d+)", name)
    return int(m.group(1)) if m else None


def session_hints(samples):
    """Group team phone clips into contiguous IMG_ number blocks (a proxy for
    recording sessions: an iPhone numbers clips consecutively)."""
    nums = sorted({img_number(Path(p).name) for p, _ in samples
                   if ec.classify_source(p) == "team_phone" and img_number(Path(p).name) is not None})
    blocks, hint = [], {}
    for n in nums:
        if not blocks or n - blocks[-1][-1] > IMG_GAP:
            blocks.append([n])
        else:
            blocks[-1].append(n)
    for b_i, b in enumerate(blocks, 1):
        for n in b:
            hint[n] = f"IMG block {b_i} (IMG_{b[0]:04d}-IMG_{b[-1]:04d})"
    return hint, blocks


def clip_stats(path):
    raw = np.load(path).astype(np.float32)
    hf = ec.trim_to_hand_frames(raw)
    present = np.abs(hf.reshape(len(hf), 2, 63)).sum(axis=2) > 0
    distinct = 1 + int(np.sum(np.any(np.diff(hf, axis=0) != 0, axis=1))) if len(hf) else 0
    return {"stored_frames": int(len(raw)), "hand_frames": int(np.sum(np.abs(raw).sum(1) > 0)),
            "distinct_hand_frames": distinct,
            "two_hand_fraction": float(np.mean(present.all(axis=1))) if len(hf) else 0.0}


def write_template(path, rows, hints):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["label", "file", "source", "signer_id", "session_id", "recording_date", "device",
                     "camera_front_or_rear", "recorded_by", "session_hint", "notes"])
        for r in rows:
            n = img_number(r["file"])
            w.writerow([r["label"], r["file"], r["source"], "", "", "", "", "", "",
                        hints.get(n, "") if r["source"] == "team_phone" else "", ""])
    print(f"wrote template {path} ({len(rows)} rows) - fill signer_id/session_id and save as signer_sessions.csv")


def stats(values):
    v = np.array(values, dtype=float)
    return {"classes": int(len(v)), "total": int(v.sum()), "min": int(v.min()), "max": int(v.max()),
            "mean": float(v.mean()), "median": float(np.median(v)), "std": float(v.std(ddof=0)),
            "imbalance_ratio_max_over_min": float(v.max() / v.min()) if v.min() > 0 else float("inf")}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data-dir", default=str(ec.DEFAULT_DATA_DIR))
    ap.add_argument("--labels", default=str(ec.DEFAULT_LABELS_PATH))
    ap.add_argument("--none-dir", default=str(ec.DEFAULT_NONE_DIR))
    ap.add_argument("--signers", default=str(ec.EVAL_DIR / "signer_sessions.csv"),
                    help="filled-in copy of signer_sessions_template.csv (used if it exists)")
    ap.add_argument("--template", default=str(ec.EVAL_DIR / "signer_sessions_template.csv"))
    ap.add_argument("--write-template", action="store_true", help="overwrite the template")
    ap.add_argument("--out-dir", default=str(ec.REPORTS_DIR))
    args = ap.parse_args()

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    meta = ec.load_label_meta(args.labels)
    order = [m["slug"] for m in meta]
    lang = {m["slug"]: m.get("language", "") for m in meta}
    cat = {m["slug"]: m.get("category", "") for m in meta}
    data_dir = Path(args.data_dir)

    samples = ec.list_samples(data_dir, order)
    if not samples:
        raise SystemExit(f"No .npy files under {data_dir}. Is the dataset present (or are these Git LFS stubs?)")
    lfs = [p for p, _ in samples[:50] if Path(p).stat().st_size < 400 and Path(p).read_bytes()[:30].startswith(b"version https://git-lfs")]
    if lfs:
        raise SystemExit("The .npy files are Git LFS pointer stubs. Run `git lfs pull` first.")

    # splits: the deployed model's original split, and train.py's split over everything today
    tr_o, va_o, bad_o = ec.make_split(data_dir, order, mode="orig", verbose=False)
    tr_a, va_a, bad_a = ec.make_split(data_dir, order, mode="all", verbose=False)
    split_orig = {p: "train" for p, _ in tr_o} | {p: "val" for p, _ in va_o}
    split_all = {p: "train" for p, _ in tr_a} | {p: "val" for p, _ in va_a}
    hints, blocks = session_hints(samples)

    signer_rows = []
    if Path(args.signers).exists():
        signer_rows = list(csv.DictReader(open(args.signers, encoding="utf-8-sig")))
        signer_rows = [r for r in signer_rows if (r.get("signer_id") or r.get("session_id"))]
        print(f"using {len(signer_rows)} filled rows from {args.signers}")

    rows = []
    for path, li in samples:
        label = order[li]
        name = Path(path).name
        src = ec.classify_source(path, label)
        st = clip_stats(path)
        sr = ec.match_rows(signer_rows, label, name) if signer_rows else None
        rows.append({
            "label": label, "class_index": li, "language": lang[label], "category": cat[label],
            "file": name, "source": src, "source_name": ec.SOURCE_NAMES[src],
            **st,
            "split_deployed_model": ("drive (30% test / 1 val / rest train; RNG not recorded)"
                                     if ec.is_drive_clip(path) else split_orig.get(path, "dropped")),
            "split_train_py_today": split_all.get(path, "dropped"),
            "dropped_reason": bad_a.get(path, ""),
            "signer_id": (sr or {}).get("signer_id", ""),
            "session_id": (sr or {}).get("session_id", ""),
            "session_hint": hints.get(img_number(name), "") if src == "team_phone" else "",
        })

    # none class (opt-in, separate from the 50 signs)
    none_paths = sorted(Path(args.none_dir).rglob("*.npy")) if Path(args.none_dir).is_dir() else []
    none_by_src = Counter(ec.classify_source(p, ec.NONE_LABEL) for p in none_paths)

    if args.write_template or not Path(args.template).exists():
        write_template(args.template, rows, hints)

    with open(out_dir / "dataset_profile_samples.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        for r in rows:
            w.writerow({k: (f"{v:.3f}" if isinstance(v, float) else v) for k, v in r.items()})

    # per class
    by_cls = defaultdict(list)
    for r in rows:
        by_cls[r["label"]].append(r)
    pc_rows = []
    for li, label in enumerate(order):
        rs = by_cls.get(label, [])
        c = Counter(r["source"] for r in rs)
        two = np.mean([r["two_hand_fraction"] for r in rs]) if rs else 0.0
        pc_rows.append({
            "class_index": li, "label": label, "display": meta[li].get("label", label), "language": lang[label],
            "category": cat[label], "total": len(rs),
            **{SOURCE_SHORT[s]: c.get(s, 0) for s in SOURCE_ORDER[:5]},
            "deployed_train_orig": sum(r["split_deployed_model"] == "train" for r in rs),
            "deployed_val_orig": sum(r["split_deployed_model"] == "val" for r in rs),
            "drive_clips": c.get("drive_phone", 0),
            "train_py_today_train": sum(r["split_train_py_today"] == "train" for r in rs),
            "train_py_today_val": sum(r["split_train_py_today"] == "val" for r in rs),
            "mean_hand_frames": float(np.mean([r["hand_frames"] for r in rs])) if rs else 0.0,
            "two_hand_frame_fraction": float(two),
            "hands_estimate": "two" if two >= 0.5 else ("mixed" if two >= 0.15 else "one"),
            "signers_known": len({r["signer_id"] for r in rs if r["signer_id"]}),
            "sessions_known": len({r["session_id"] for r in rs if r["session_id"]}),
        })
    with open(out_dir / "dataset_profile_per_class.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(pc_rows[0]))
        w.writeheader()
        for r in pc_rows:
            w.writerow({k: (f"{v:.3f}" if isinstance(v, float) else v) for k, v in r.items()})

    totals = [r["total"] for r in pc_rows]
    no_drive = [r["total"] - r["drive_clips"] for r in pc_rows]
    src_tot = Counter(r["source"] for r in rows)
    lang_tot = Counter(r["language"] for r in rows)
    lang_cls = Counter(lang[l] for l in order)
    summary = {
        "date": date.today().isoformat(), "data_dir": ec.rel(data_dir), "classes": len(order),
        "classes_by_language": dict(lang_cls), "total_clips": len(rows),
        "clips_by_language": dict(lang_tot), "clips_by_source": dict(src_tot),
        "balance_all_clips": stats(totals), "balance_without_drive": stats(no_drive),
        "deployed_model_split": {"orig_train": len(tr_o), "orig_val": len(va_o),
                                 "drive_total": src_tot.get("drive_phone", 0),
                                 "drive_train_val_test_reported": "258 / 46 / 131 (models/drive_retrain_results.json)"},
        "train_py_today_split": {"train": len(tr_a), "val": len(va_a)},
        "dropped_by_train_py": len(bad_a),
        "team_phone_img_blocks": [f"IMG_{b[0]:04d}-IMG_{b[-1]:04d} ({len(b)} clips)" for b in blocks],
        "none_class": {"total": len(none_paths), **dict(none_by_src)},
        "signer_session_file": ec.rel(args.signers) if signer_rows else None,
        "signers_known": len({r["signer_id"] for r in rows if r["signer_id"]}),
        "sessions_known": len({r["session_id"] for r in rows if r["session_id"]}),
        "frames": {"stored_frames_per_clip": 30,
                   "mean_hand_frames": float(np.mean([r["hand_frames"] for r in rows])),
                   "mean_distinct_hand_frames": float(np.mean([r["distinct_hand_frames"] for r in rows]))},
    }
    extra_dirs = {"raw/ (pre-aspect-correction originals, incl. unused FSL-105 classes)": DATASET_COUNT(ec.DATASET_DIR / "raw"),
                  "raw_archive_fsl105_unused/": DATASET_COUNT(ec.DATASET_DIR / "raw_archive_fsl105_unused"),
                  "removed_clips/ (duplicates / empty, excluded)": DATASET_COUNT(ec.DATASET_DIR / "removed_clips")}
    summary["other_folders_not_used_for_training"] = extra_dirs
    with open(out_dir / "dataset_profile_summary.json", "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2)

    write_markdown(out_dir / "dataset_profile.md", summary, pc_rows, rows, src_tot, signer_rows)
    b = summary["balance_all_clips"]
    print(f"{len(rows)} clips, {len(order)} classes ({dict(lang_cls)}); per class min {b['min']} max {b['max']} "
          f"mean {b['mean']:.1f} std {b['std']:.1f} ratio {b['imbalance_ratio_max_over_min']:.2f}")
    print(f"sources: {dict(src_tot)}")
    print(f"deployed split: {len(tr_o)} train / {len(va_o)} val (+{src_tot.get('drive_phone', 0)} drive); "
          f"train.py today: {len(tr_a)} / {len(va_a)}")
    print(f"wrote {out_dir / 'dataset_profile.md'}")


def DATASET_COUNT(d):
    d = Path(d)
    return sum(1 for _ in d.rglob("*.npy")) if d.is_dir() else 0


def write_markdown(path, s, pc_rows, rows, src_tot, signer_rows):
    b, bn = s["balance_all_clips"], s["balance_without_drive"]
    L = [f"# SignTalk dataset profile", "",
         f"Generated {s['date']} by `ai/evaluation/dataset_profile.py` from `{s['data_dir']}` "
         "(the aspect-corrected landmark files `train.py` trains on). Each clip is one `.npy` file of "
         "30 frames x 126 features (2 hands x 21 MediaPipe landmarks x xyz, each hand wrist-centred and "
         "scaled by its wrist-to-middle-MCP distance).", "",
         "## Totals", "",
         "| Item | Value |", "|---|---|",
         f"| Classes | {s['classes']} ({', '.join(f'{v} {k}' for k, v in s['classes_by_language'].items())}) |",
         f"| Clips (all) | {s['total_clips']} |",
         f"| Clips by language | {', '.join(f'{k}: {v}' for k, v in s['clips_by_language'].items())} |",
         f"| Clips per class (all) | min {b['min']}, max {b['max']}, mean {b['mean']:.1f}, median {b['median']:.0f}, SD {b['std']:.1f} |",
         f"| Imbalance ratio (max/min) | {b['imbalance_ratio_max_over_min']:.2f} |",
         f"| Clips per class without Drive clips (the 889-clip set) | min {bn['min']}, max {bn['max']}, mean {bn['mean']:.1f}, SD {bn['std']:.1f}, ratio {bn['imbalance_ratio_max_over_min']:.2f} |",
         f"| Mean hand-visible frames per clip | {s['frames']['mean_hand_frames']:.1f} of 30 ({s['frames']['mean_distinct_hand_frames']:.1f} distinct) |",
         f"| `none` (non-sign) sequences, opt-in class | {s['none_class']['total']}"
         + (f" ({', '.join(f'{k}: {v}' for k, v in s['none_class'].items() if k != 'total')})" if s['none_class']['total'] else "")
         + " |", "",
         "## Sources", "",
         "| Source | Clips | How identified | Signers / sessions |", "|---|---|---|---|"]
    ident = {"fsl105": "numeric file names `0.npy`, `1.npy`, ... (import_fsl105_full.py)",
             "asl_citizen": "`asl_<n>.npy`", "asl_citizen_concat": "`asl_<n>.npy` in the 6 phrase classes",
             "team_phone": "`user_IMG_<nnnn>.npy` (import_user_clips.py)",
             "drive_phone": "`drive_<Google Drive file id>.npy`"}
    for sname in SOURCE_ORDER:
        if src_tot.get(sname):
            L.append(f"| {ec.SOURCE_NAMES[sname]} | {src_tot[sname]} | {ident.get(sname, '')} | {KNOWN_SIGNER_INFO.get(sname, '')} |")
    if s["team_phone_img_blocks"]:
        L += ["", "Team phone clips fall into these contiguous IMG_ number blocks (a hint for recording sessions): "
              + "; ".join(s["team_phone_img_blocks"]) + "."]
    L += ["", "## Signers and recording sessions", ""]
    if signer_rows:
        L.append(f"From `{s['signer_session_file']}`: {s['signers_known']} distinct signers and "
                 f"{s['sessions_known']} sessions are recorded for the clips it covers (per-class counts in the "
                 "table below and in `dataset_profile_per_class.csv`).")
    else:
        L.append("Not derivable from the landmark files. Fill in `ai/evaluation/signer_sessions_template.csv` "
                 "(one row per clip; `label`/`file` accept wildcards such as `drive_*`), save it as "
                 "`ai/evaluation/signer_sessions.csv`, and re-run this script. Until then the manuscript should "
                 "say signer counts are unknown for the ASL Citizen and Drive subsets.")
    d = s["deployed_model_split"]
    L += ["", "## Train / validation / test splits", "",
          "| Split | Train | Val | Test | Notes |", "|---|---|---|---|---|",
          f"| Deployed model v1.1, original clips | {d['orig_train']} | {d['orig_val']} | - | train.py logic: seed 42, 20% per class, stratified; reproduced exactly |",
          f"| Deployed model v1.1, Drive clips | 258 | 46 | 131 | reported in `models/drive_retrain_results.json`; 30% per class test, 1 val, rest train; the RNG was not recorded, so the Drive test set cannot be rebuilt |",
          f"| `train.py` run today (all {s['total_clips']} clips) | {s['train_py_today_split']['train']} | {s['train_py_today_split']['val']} | - | same logic over everything in raw_iso/ |",
          "", "All splits are random per clip: the same signers appear in train and validation. "
          "A signer-independent test set is described in `ai/evaluation/signer_independent/README.md`.",
          "", f"Clips removed by `drop_bad_clips()` today: {s['dropped_by_train_py']} "
          "(the 5 conflicting/empty FSL-105 clips were already moved to `dataset/removed_clips/`).", "",
          "## Samples per class", "",
          "| # | Sign | Lang | Category | Total | FSL-105 | ASL Cit. | ASL Cit. concat | Team phone | Drive | Train (v1.1 orig) | Val (v1.1 orig) | Hands (est.) | Signers | Sessions |",
          "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in pc_rows:
        L.append(f"| {r['class_index']} | {r['label']} | {r['language']} | {r['category']} | {r['total']} | "
                 f"{r['FSL-105']} | {r['ASL Citizen']} | {r['ASL Citizen (concat.)']} | {r['Team phone']} | {r['Drive phone']} | "
                 f"{r['deployed_train_orig']} | {r['deployed_val_orig']} | {r['hands_estimate']} | "
                 f"{r['signers_known'] or '?'} | {r['sessions_known'] or '?'} |")
    L += ["", "`Hands (est.)` = share of hand-visible frames where MediaPipe found both hands "
          "(one < 0.15 <= mixed < 0.5 <= two); verify against the sign itself.", "",
          "## Other folders (not used for training)", ""]
    L += [f"- `ai/dataset/{k}`: {v} files" for k, v in s["other_folders_not_used_for_training"].items()]
    L += ["", "## Assumptions", "",
          "- Source is inferred from the file-name pattern only (see table above); any file that matches no "
          "pattern is listed as Unknown.",
          "- The 6 ASL phrase classes (good_morning, good_afternoon, good_evening, how_are_you, nice_to_meet_you, "
          "see_you_tomorrow) are counted as 'ASL Citizen (concat.)' because train.py documents them as "
          "concatenations of component-word clips.",
          "- 'Team phone' assumes all `user_IMG_*` files came from `import_user_clips.py` on the team's own phone "
          "recordings (350x640 portrait).",
          "- Drive clips for good_morning, good_afternoon (identical videos), yes (not downloaded) and youre_welcome "
          "(no folder) are absent by design - see the drive retrain notes.", ""]
    path.write_text("\n".join(L), encoding="utf-8")


if __name__ == "__main__":
    main()
