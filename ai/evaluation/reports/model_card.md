# SignTalk recognition model: model card (v1.1, 2026-10-10)

For the manuscript's "Model configuration and training procedure" section. Sources: `ai/models/model.py`, `ai/training/train.py`, `ai/training/dataset.py`, `ai/dataset/landmarks.py`, `ai/models/metadata.json`, `ai/models/classification_report.txt`, `ai/models/drive_retrain_results.json`. Evaluation numbers were re-computed with `ai/evaluation/evaluate_model.py` (see `deployed_v1.1_orig_val/`).

> **The model is a bidirectional LSTM (BiLSTM) on MediaPipe hand-landmark sequences. It is NOT a convolutional neural network (CNN), and it does not take image pixels or background-subtracted frames as input.** The camera image is processed only by MediaPipe Hand Landmarker, which outputs 21 3-D keypoints per hand. The classifier sees only those keypoint coordinates over time.

## 1. Deployed artefacts

| Item | Value |
|---|---|
| On-device model | `android/SignTalk/app/src/main/assets/sign_lstm_iso.tflite` (3.1 MB, float32; identical file to `ai/models/sign_lstm.tflite`) |
| PyTorch counterpart | `ai/models/sign_lstm.pt` (state dict for `ai/models/model.py::SignLSTM`) |
| Labels | `android/SignTalk/app/src/main/assets/labels.json` / `ai/dataset/labels_50.json` (50 classes, index = output index) |
| TFLite I/O | input `[1, 30, 126]` float32 → output `[1, 50]` logits (the app applies softmax) |
| TFLite vs PyTorch parity | argmax agreement 100% (175/175), max probability difference 3.6e-7 |
| Vocabulary | 50 signs: 25 FSL (10 numbers, 12 colours, 3 greetings) + 25 ASL (greetings, basic responses, family/people) |

## 2. Input representation

| Step | Detail |
|---|---|
| Landmark extraction | MediaPipe Hands (Python `mediapipe==0.10.14` `solutions.hands` for training data; MediaPipe Tasks `HandLandmarker` with `hand_landmarker.task` on Android). Max 2 hands, detection/tracking confidence 0.5. |
| Per-frame features | 2 hands × 21 landmarks × (x, y, z) = **126 values**. Slot 0 = left hand, slot 1 = right hand. An undetected hand is all zeros. If both hands get the same handedness label, they are ordered by image x. On the phone, one visible hand always goes in the left slot. |
| Normalisation (per hand) | x and z are multiplied by the frame aspect ratio (width/height), so the axes share units. Each hand is then re-centred on its wrist (landmark 0) and divided by the 2-D wrist→middle-finger-MCP (landmark 9) distance. The result is invariant to where the hand sits in the frame and how large it appears. |
| Sequence | Only frames where a hand is visible are kept (`trim_to_hand_frames`), then uniformly resampled to **30 frames** (`sample_to_fixed_length`). Clips are imported by sampling 45 evenly spaced video frames. |
| Model input | tensor of shape **30 × 126** per sample |

## 3. Architecture (`SignLSTM`, 697,266 parameters)

| Layer | Configuration | Output |
|---|---|---|
| Input | 30 time steps × 126 features | 30 × 126 |
| BiLSTM | 2 stacked layers, hidden size 128 per direction, bidirectional, dropout 0.3 between layers | 30 × 256 |
| Temporal mean pooling | average over the 30 time steps | 256 |
| LayerNorm | 256 | 256 |
| Dense + ReLU | 256 → 128 | 128 |
| Dropout | p = 0.3 | 128 |
| Dense (output) | 128 → 50 logits, softmax at inference | 50 |

## 4. Training procedure

| Hyper-parameter | Value |
|---|---|
| Loss | Cross-entropy with **label smoothing 0.1** |
| Optimiser | Adam, learning rate 1e-3, weight decay 1e-4 |
| LR schedule | ReduceLROnPlateau on validation loss, factor 0.5, patience 5 |
| Batch size | 32 |
| Epochs | max 80, early stopping patience 12 on validation loss; best-validation-loss weights kept. **v1.1 trained for 42 epochs** (best val loss 0.821). |
| Augmentation multiplier | 6: each epoch shows every training clip once un-augmented plus 5 randomly augmented copies |
| Split | Stratified per class, 20% validation, NumPy seed 42 (`training/splits.py`). Drive clips: 30% per class held-out test, 1 per class validation, rest training. |
| Data cleaning | Byte-identical clips filed under different labels, and clips with no detected hand, are removed (5 FSL-105 clips, kept in `dataset/removed_clips/`). |
| Training data (v1.1) | 714 original clips + 258 Drive phone clips (train); 175 + 46 (validation); 131 Drive clips held out as test |

### Augmentations (applied on the fly, each with probability p; `training/dataset.py`)

| Augmentation | p | Parameters | Purpose |
|---|---|---|---|
| Mirror | 0.4 | x → −x, swap left/right hand slots | left-handed signers / mirrored cameras |
| Rotation | 0.6 | ±20° in the image plane, around the wrist | camera/hand tilt |
| Scale | 0.5 | ×0.9–1.1 (x, y) | residual size variation |
| Anisotropic x-stretch | 0.5 | x ×0.85–1.15 | lens / aspect-estimate error |
| Gaussian jitter | 0.7 | σ = 0.01, only on detected landmarks | landmark noise |
| Time warp | 0.4 | ±3-frame non-uniform resampling | signing-speed variation |
| Temporal crop | 0.5 | keep 60–100% of the clip, resample to 30 | window not aligned to sign start/end |
| Hand drop | 0.3 | one hand zeroed for 2–5 frames | tracking dropouts |
| Prefix (partial sign) | 0.5 | first 6–30 frames only, resampled to 30 | early prediction (app classifies after 12 frames) |

## 5. Results (v1.1)

| Evaluation set | n | Accuracy | Macro P / R / F1 | Notes |
|---|---|---|---|---|
| Original validation split | 175 | **96.6%** (169/175) | 0.950 / 0.940 / 0.936 | Same split as train.py, reproduced exactly. Also used for early stopping, so it is a *validation*, not a fully independent test, figure. |
| Same, first 12 of 30 frames only | 175 | 85.1% | – | Approximates the app's earliest prediction. |
| Drive phone clips, held-out test | 131 | **97.7%** (128/131) | – | Never trained on and not used for model selection. The split RNG was not saved, so this set cannot be rebuilt from the repo. |
| Drive test, first 12 frames | 131 | 89.3% | – | from `drive_retrain_results.json` |
| Combined held-out (orig val + Drive test) | 306 | 97% | 0.976 / 0.954 / 0.959 | `ai/models/classification_report.txt`, `metadata.json` |
| Previous model (before Drive retrain) on Drive test | 131 | 48.1% | – | Phone clips from new recordings were the main gap. |

Accuracy by data source on the 175-clip validation split: FSL-105 100% (99/99), team phone clips 100% (47/47), ASL Citizen 78% (18/23), concatenated ASL phrases 83% (5/6). ASL classes have only 2 validation clips each, so per-class ASL figures are very noisy.

**Limitations to state in the manuscript:**
- All splits are random per clip, so the same signers appear in training and evaluation. **No signer-independent result exists yet.** See `ai/evaluation/signer_independent/README.md`.
- The data is small: 9–37 clips per class, imbalance ratio 4.1 (see `dataset_profile.md`).
- There is no "unknown / not a sign" class in v1.1. Every hand movement is mapped to one of the 50 signs, and only the 0.6 confidence threshold rejects inputs. An opt-in `none` class is now supported (`train.py --include-none`) but is not deployed.
- The features are wrist-centred hand shape and motion only. Signs that differ mainly by **location on the body** (e.g. ASL FATHER vs MOTHER, BOY vs GIRL) cannot be told apart by location.
- The six ASL phrase classes were built by concatenating ASL Citizen word clips, not recorded as continuous phrases.

## 6. Inference in the app

`RecognitionEngine`: MediaPipe Tasks HandLandmarker → the same normalisation (`LandmarkNormalizer`, aspect-corrected) → rolling buffer of up to 30 hand frames. Classification starts after 12 frames and the buffer is resampled to 30. A prediction is shown when softmax confidence ≥ 0.6, with a 3-prediction majority vote. The buffer resets after 8 consecutive frames with no hand. Threshold evidence: `ai/evaluation/reports/deployed_v1.1_threshold_sweep/`.
