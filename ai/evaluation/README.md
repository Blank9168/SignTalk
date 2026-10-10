# SignTalk evaluation tooling

These scripts produce the objective evidence the expert reviewer asked for: a dataset profile, test metrics with a confusion matrix, most-confused pairs, a justified confidence threshold, a signer-independent test, a robustness test matrix, and an opt-in `none` (not a sign) class.

Every command below runs **from the repo root** on Windows, macOS or Linux. Forward slashes work in Windows PowerShell and cmd. All paths are resolved relative to the scripts, so the working directory does not matter.

## Setup

```
pip install -r ai/evaluation/requirements.txt
```

- `mediapipe==0.10.14` (Python 3.9–3.12) is only needed to turn **videos** into landmarks. Scripts that read the existing `.npy` files need only numpy, scikit-learn, matplotlib and torch.
- To score the `.tflite` file directly, also run `pip install ai-edge-litert` (or `tflite-runtime` / `tensorflow`).

## Files

| File | What it does |
|---|---|
| `dataset_profile.py` | Dataset profile: totals, clips per class, language, source, splits, balance stats, and signers/sessions once filled in |
| `signer_sessions_template.csv` | One row per clip. The team fills in `signer_id`, `session_id`, date, device, ... |
| `evaluate_model.py` | Accuracy, macro and weighted P/R/F1, per-class report, confusion matrix CSV + PNG, top-15 most-confused pairs, per-signer and per-source accuracy, optional TFLite parity |
| `threshold_sweep.py` | Coverage and accepted-accuracy for thresholds 0.30–0.95, a streaming "N agreeing windows" rule, the non-sign false-accept rate, a plot, and a recommended threshold |
| `prepare_signer_independent.py` | Extracts landmarks from new-signer videos with the training extractor, then runs the evaluation and the sweep with accuracy per signer |
| `signer_independent/README.md` | Recording protocol for the signer-independent test set |
| `robustness_matrix_template.csv` | Lighting × background × distance × device × hand × camera test sheet (96 conditions; 8 marked `core`) |
| `eval_common.py` | Shared code: split reproduction, preprocessing, model loading (.pt/.tflite), video extraction |
| `../training/splits.py` | The train.py split logic, moved here unchanged so evaluation can reproduce it without torch |
| `../training/make_none_from_existing.py` | Builds a **synthetic** starter `none` set, or imports recorded non-sign videos |
| `reports/model_card.md` | Model configuration and training procedure (BiLSTM, not CNN) for the manuscript |

## 1. Dataset profile

```
python ai/evaluation/dataset_profile.py
```

This writes `reports/dataset_profile.md`, `dataset_profile_per_class.csv`, `dataset_profile_samples.csv` (one row per clip) and `dataset_profile_summary.json`. It also creates `signer_sessions_template.csv` if that file is missing.

The source of each clip is worked out from its file name in `ai/dataset/raw_iso/<label>/`:

| Pattern | Source |
|---|---|
| `0.npy`, `1.npy`, ... | FSL-105 (DLSU/DOST), imported by `dataset/import_fsl105_full.py` |
| `asl_<n>.npy` | ASL Citizen. In the 6 phrase classes these are concatenated word clips. |
| `user_IMG_<nnnn>.npy` | Team phone clips (`dataset/import_user_clips.py`) |
| `drive_<Google Drive file id>.npy` | Google Drive phone clips (2026-10-10 retrain) |

**Signers and sessions** cannot be recovered from landmark files. To add them:
1. Copy `signer_sessions_template.csv` to `ai/evaluation/signer_sessions.csv`.
2. Fill in `signer_id` and `session_id`. You can fill every row, or replace them with a few wildcard rows. For example, the row `*,drive_*,drive_phone,S_drive_A,...` covers every Drive clip; the first matching row wins. `session_hint` already groups the team phone clips by IMG_ number.
3. Re-run `dataset_profile.py`. The per-class table then shows signer and session counts.

## 2. Test metrics and confusion matrix

```
# deployed model (PyTorch) + parity check against the exact on-device file
python ai/evaluation/evaluate_model.py --tflite android/SignTalk/app/src/main/assets/sign_lstm_iso.tflite --run-name deployed_v1.1_orig_val
```

- **Default test set:** the validation split train.py used for that model. If train.py wrote a `split_manifest.csv` next to the model (every new run does), that file is used. Otherwise the script uses the deployed model's split: train.py's seed-42, 20%-per-class split of the original 889 clips (175 clips). This reproduces the 96.57% in `metadata.json` exactly.
- `--split all` uses train.py's split over everything now in `raw_iso/`. Only use it for a model trained that way.
- `--test-dir DIR` points at a separate folder, laid out as `DIR/<label>/*.npy|*.mp4` or `DIR/<signer>/<label>/*.npy|*.mp4`. Videos are converted first and cached in `DIR_landmarks/`.
- The output goes to `reports/<run-name>/`: `summary.md`, `metrics.json`, `classification_report.txt`, `per_class.csv`, `confusion_matrix.csv`, `confusion_matrix.png`, `most_confused_pairs.csv`, `per_group.csv` and `predictions.csv`.
- `metrics.json` also has `accuracy_first_12_frames`: accuracy when only the first 12 of 30 frames are seen, which is when the app makes its first prediction.

**The Drive held-out test set (131 clips, 97.7%) cannot be rebuilt.** The retrain script that split the Drive clips is not in the repo and its random seed was not saved. About 70% of the Drive clips were trained on, so do not evaluate the deployed model on "all Drive clips". From now on, train.py writes `split_manifest.csv` with every model, so this cannot happen again.

## 3. Confidence threshold

```
python ai/evaluation/threshold_sweep.py --run-name deployed_v1.1_threshold_sweep
python ai/evaluation/threshold_sweep.py --target-accuracy 0.97 --max-none-accept 0.10   # stricter rule
```

For each threshold from 0.30 to 0.95 in steps of 0.05, the sweep reports:
- **coverage**: the share of clips accepted
- **accepted accuracy** and **accepted error**
- **correct overall**
- **non-sign false accept**: the share of `none` sequences accepted as a sign. It uses `ai/dataset/none/` when that folder exists.

It reports the same for a streaming rule that simulates the app. Windows of 12, 13, ..., 30 frames are fed in, and a sign is accepted once N consecutive windows (N = 1, 2, 3, 5) agree with confidence ≥ the threshold.

The recommended threshold is the one with the **highest coverage whose accepted accuracy is ≥ the target (default 0.95)**. When a none set exists, the sweep also reports an alternative that caps non-sign false accepts at 20% (`--alt-max-none-accept`). The outputs are `threshold_sweep.csv`, `threshold_sweep_stability.csv`, `threshold_sweep.png` and `threshold_recommendation.md`/`.json`.

## 4. Signer-independent test

Record the clips following `signer_independent/README.md` into `ai/dataset/signer_independent/<signer_id>/<label>/<clip>.mp4`, then run:

```
python ai/evaluation/prepare_signer_independent.py --tflite android/SignTalk/app/src/main/assets/sign_lstm_iso.tflite
```

This writes `reports/signer_independent/` (per-signer accuracy is in `summary.md` and `per_group.csv`), `reports/signer_independent_threshold/`, and `recording_coverage.csv`.

For the robustness matrix, fill in `robustness_matrix_template.csv` from live app tests. Do the 8 `core` rows first: a baseline, then each factor changed one at a time. Do the `optional` rows if time allows.

## 5. Opt-in `none` (not a sign) class

```
# a) starter set: 100 SYNTHETIC sequences -> ai/dataset/none/synthetic/ (already generated, seed 2026)
python ai/training/make_none_from_existing.py
# b) real non-sign clips (recommended): record videos, then
python ai/training/make_none_from_existing.py --import-videos path/to/none_videos   # -> ai/dataset/none/recorded/
# c) train a 51-class candidate (does NOT touch ai/models/sign_lstm.pt)
python ai/training/train.py --include-none          # -> ai/models/candidate/none_class/
# d) evaluate it on its own split, and sweep (its none validation clips give the false-accept rate)
python ai/evaluation/evaluate_model.py --model ai/models/candidate/none_class/sign_lstm.pt --labels ai/models/candidate/none_class/metadata.json --run-name none_class_val
python ai/evaluation/threshold_sweep.py --model ai/models/candidate/none_class/sign_lstm.pt --labels ai/models/candidate/none_class/metadata.json --run-name none_class_threshold
```

- `none` is appended as the **last** class (index 50), so the 50 existing indices stay the same. train.py writes `labels.json` in the app format with `none` appended, plus `metadata.json` and `split_manifest.csv`.
- The synthetic set uses four methods: `cross_shuffle` (frames from ≥3 different signs in random order), `splice` (first half of sign A + second half of sign B), `rest_hold` (a generic relaxed hand held almost still) and `random_walk` (heavy random drift). It only uses train-split clips. **It is a stopgap.** Splices and shuffles can look like partial real signs, so check that per-class recall on `none_class_val` does not drop. Replace the set with recorded clips of resting hands, face scratching, waving, fixing hair and holding a phone.
- Deploying a `none` model also needs app changes, which are outside `ai/`. You would export it to TFLite, copy the new `labels.json`, and make `RecognitionEngine` treat `none` as "no sign" (never display or speak it).

## For the manuscript

| Manuscript table / figure | File(s) |
|---|---|
| Dataset profile: totals, per-class counts, sources, balance | `reports/dataset_profile.md` (tables), `reports/dataset_profile_per_class.csv` |
| Signers and sessions | `dataset_profile.md` after `signer_sessions.csv` is filled in |
| Train/validation/test split sizes | `dataset_profile.md` → "Train / validation / test splits" |
| Model configuration and training procedure | `reports/model_card.md` (BiLSTM on landmarks, **not a CNN**) |
| Test metrics: accuracy, macro and weighted P/R/F1 | `reports/deployed_v1.1_orig_val/summary.md`, `metrics.json` |
| Per-class precision/recall/F1 | `reports/deployed_v1.1_orig_val/per_class.csv` |
| Confusion matrix (figure) | `reports/deployed_v1.1_orig_val/confusion_matrix.png` (+ `.csv`) |
| Most-confused pairs with reasons | `reports/deployed_v1.1_orig_val/most_confused_pairs.csv` (re-run on the signer-independent set, which will have more errors) |
| On-device model = evaluated model | the TFLite parity row in `summary.md`; `reports/deployed_v1.1_orig_val_tflite/` |
| Confidence-threshold justification | `reports/deployed_v1.1_threshold_sweep/threshold_sweep.png`, `threshold_recommendation.md` |
| Signer-independent accuracy (per signer, mean ± SD) | `reports/signer_independent/summary.md`, `per_group.csv` (after recording) |
| Robustness test matrix | `robustness_matrix_template.csv` (filled in) |

**Most-confused pairs and likely reasons.** Use the counts from the current run's `most_confused_pairs.csv`. The `likely_reason` column fills in these reasons where they are known:
- **pito vs apat / walo** (and other FSL numbers): the handshapes differ only in which fingers are extended or touch the thumb. One finger partly hidden from the camera flips the class. Two byte-identical apat/pito clips in FSL-105 made this worse; they have been removed.
- **berde vs sampu**: similar handshape and wrist movement in the FSL-105 recordings (team observation).
- **father vs mother** (and **boy vs girl**): the same handshape and movement, differing mainly in **location on the face**, forehead vs chin. The features are wrist-centred hand landmarks with no face reference, so location is invisible to the model.
- **thank_you vs youre_welcome**, **know vs dont_know**, **understand vs dont_understand**, the **good_\*** and **magandang_\*** greetings: each pair shares a component, so short or partial windows look alike.

**Threshold justification.** On the v1.1 validation split, accuracy barely changes with the threshold because almost every clip is correct. The threshold mainly guards against **non-signs** and **unfamiliar signers**. So report the plot, report the non-sign false-accept rate (from recorded `none` clips; the current synthetic set is only indicative), and redo the sweep on the signer-independent set before you fix the app value. The streaming results show that requiring ~3 agreeing windows raises accepted accuracy for the same threshold.

**Honesty notes.** All current splits are random per clip, so the same signers appear in training and evaluation. The 175-clip validation split was also used for early stopping. Label these figures "validation accuracy". Present the signer-independent result as the generalisation figure.
