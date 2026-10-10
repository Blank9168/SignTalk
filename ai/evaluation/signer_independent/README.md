# Signer-independent test set: recording protocol

Every accuracy SignTalk reports so far (96.6% on the validation split, 97.7% on held-out Drive clips) comes from a random per-clip split. The same people appear in training and testing, so those numbers do not show how the model does for someone it has never seen. This test set answers that question. Expect a lower number. That is normal, and it is the honest figure for the manuscript.

## Who to record

- **2–3 people who appear in NO training data.** That means they are not in the FSL-105 or ASL Citizen datasets, did not record any `user_IMG_*` phone clips, and did not record any Google Drive clips. Ask each person and note the answer in `signers.csv` (below).
- If you can, mix people: at least one Deaf or fluent FSL signer, plus one or two hearing learners. Include at least one left-handed signer if possible.
- Give each person an anonymous ID (`S01`, `S02`, `S03`). Never use names in folder or file names.
- Get written consent to record and keep the clips for the study.

## What to record

- **All 50 signs, about 3 clips per sign per person** (150 clips per person). Use a fresh take each time: drop the hands, then sign again. Do not cut one long video into three.
- Before recording, the signer watches the app's reference video for each sign (`android/SignTalk/app/src/main/assets/sign_videos/`). They then sign it the way they naturally would, without copying frame by frame.
- **Optional `none` clips:** 10–20 clips per person of things that are not signs: resting hands, scratching the face, waving at someone, fixing hair, holding a phone, pointing. These measure false accepts.

## How to record

| Item | Setting |
|---|---|
| Camera | A phone, held still or on a tripod/stand. Portrait orientation, matching how the app is used. |
| Framing | Head to waist. Both hands stay in frame for the whole sign, including the start and end. |
| Distance | About 0.5–1 m (arm's length). |
| Lighting | Ordinary indoor light, facing the signer. Avoid a bright window behind them. |
| Background | Any normal background. A mix of plain and busy across clips is fine. |
| Clip length | 2–4 s: hands down → sign → hands down. |
| Format | `.mp4` or `.mov`, the phone's default resolution, 30 fps. |

Vary lighting, background and distance *between* signers or sessions, not within one sign's three clips. Write down what you used. The full robustness matrix (lighting × background × distance × device × hand × camera) is a separate test; see `../robustness_matrix_template.csv`.

## Folder layout

```
ai/dataset/signer_independent/
  S01/
    isa/      isa_1.mp4  isa_2.mp4  isa_3.mp4
    dalawa/   ...
    ...
    married/  ...
    none/     none_1.mp4 ...        (optional)
  S02/
  S03/
```

Label folder names must match the slugs in `ai/dataset/labels_50.json` exactly: `isa dalawa tatlo apat lima anim pito walo siyam sampu asul berde pula kayumanggi itim puti dilaw kahel abo rosas lila maliwanag magandang_umaga magandang_hapon magandang_gabi good_morning good_afternoon good_evening hello how_are_you im_fine nice_to_meet_you thank_you youre_welcome see_you_tomorrow understand dont_understand know dont_know yes no wrong correct slow fast father mother boy girl married`.

Also add `ai/dataset/signer_independent/signers.csv` with these columns:

```
signer_id,in_training_data(yes/no),deaf_or_hearing,fsl_experience,dominant_hand,device,camera(front/rear),recording_date,lighting,background,notes
S01,no,hearing,beginner,right,iPhone 11,rear,2026-10-15,indoor,plain wall,
```

## Run it

From the repo root on the PC that has `mediapipe==0.10.14`:

```
python ai/evaluation/prepare_signer_independent.py
```

This does four things:
1. Extracts landmarks with the same code as training: `dataset/landmarks.HandLandmarkExtractor` plus `import_user_clips.extract_video_landmarks`, which samples 45 frames, keeps hand frames, and resamples to 30. The results go to `ai/dataset/signer_independent_landmarks/<signer>/<label>/*.npy`. Clips are only extracted once.
2. Runs `evaluate_model.py` and writes `ai/evaluation/reports/signer_independent/`. In there, `summary.md` and `per_group.csv` give **accuracy per signer**, and the other files give the confusion matrix, most-confused pairs and per-class F1.
3. Runs `threshold_sweep.py` on the same clips and writes `reports/signer_independent_threshold/`.
4. Writes `recording_coverage.csv`, which shows clips per signer × sign so you can spot missing recordings.

Add `--tflite android/SignTalk/app/src/main/assets/sign_lstm_iso.tflite` to also score the exact on-device model.

If only a newer mediapipe is installed (no `solutions.hands`), the script falls back to the MediaPipe Tasks `HandLandmarker`, the same API the app uses, with the app's single-hand slot rule. On 100 of the team's own phone clips the fallback gave the same predictions as the stored training features (100/100). For the manuscript, still prefer `mediapipe==0.10.14`.

## Reporting

- Report overall accuracy, macro F1, and accuracy **per signer**, as mean ± SD across signers, next to the random-split number. Do not merge the two.
- State: number of signers, clips per sign, devices, and that none of the signers are in the training data.
- Do not add these clips to training until the numbers are reported. After that, a retrain may use them, but then a new, unseen set of signers is needed for any later claim.
