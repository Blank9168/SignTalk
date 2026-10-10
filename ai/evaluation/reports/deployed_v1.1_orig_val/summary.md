# Evaluation: deployed_v1.1_orig_val

- Date: 2026-10-10
- Model: `models/sign_lstm.pt` (pt, 50 classes)
- Test set: train.py validation split of the original 889 clips (seed 42, 20% per class) - the held-out split of the deployed model
- Clips: 175 across 50 classes

| Metric | Value |
|---|---|
| Accuracy | 0.9657 (169/175) |
| Macro precision / recall / F1 | 0.9500 / 0.9400 / 0.9359 |
| Weighted precision / recall / F1 | 0.9629 / 0.9657 / 0.9587 |
| Top-3 accuracy | 0.9829 |
| Mean max-softmax confidence | 0.8760 |
| Accuracy from first 12 of 30 frames (app's earliest prediction) | 0.8514 |
| TFLite vs PyTorch: argmax agreement / max prob diff | 1.0000 / 3.58e-07 |

Macro averages are over the classes present in the test set.

## Accuracy by group

| Grouping | Group | n | Accuracy |
|---|---|---|---|
| source | asl_citizen | 23 | 0.7826 |
| source | asl_citizen_concat | 6 | 0.8333 |
| source | fsl105 | 99 | 1.0000 |
| source | team_phone | 47 | 1.0000 |
| language | ASL | 50 | 0.8800 |
| language | FSL | 125 | 1.0000 |

## Top 6 most-confused pairs

| True | Predicted as | Count | Rate (of true class) | Likely reason |
|---|---|---|---|---|
| father | mother | 1 | 0.50 (1/2) | ASL FATHER/MOTHER use the same open-5 handshape and tap; they differ only in location (forehead vs chin). Features are wrist-centred hand landmarks with no face reference, so location is invisible to the model. |
| girl | itim | 1 | 0.50 (1/2) | (add explanation: compare handshape, movement, location, one vs two hands) |
| girl | magandang_umaga | 1 | 0.50 (1/2) | (add explanation: compare handshape, movement, location, one vs two hands) |
| good_afternoon | married | 1 | 0.50 (1/2) | (add explanation: compare handshape, movement, location, one vs two hands) |
| im_fine | asul | 1 | 0.50 (1/2) | (add explanation: compare handshape, movement, location, one vs two hands) |
| youre_welcome | thank_you | 1 | 0.50 (1/2) | Likely: both use a flat hand moving away from the chin/body area; check the clips - the distinguishing part is short. |

## Caveats

- Random per-clip split: the same signers (FSL-105 signer(s), the team phone signer) appear in both train and test, so this is NOT a signer-independent result.

Files: `metrics.json`, `classification_report.txt`, `per_class.csv`, `confusion_matrix.csv`, `confusion_matrix.png`, `most_confused_pairs.csv`, `per_group.csv`, `predictions.csv`.
