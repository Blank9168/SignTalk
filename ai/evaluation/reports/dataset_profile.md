# SignTalk dataset profile

Generated 2026-10-10 by `ai/evaluation/dataset_profile.py` from `dataset/raw_iso` (the aspect-corrected landmark files `train.py` trains on). Each clip is one `.npy` file of 30 frames x 126 features (2 hands x 21 MediaPipe landmarks x xyz, each hand wrist-centred and scaled by its wrist-to-middle-MCP distance).

## Totals

| Item | Value |
|---|---|
| Classes | 50 (25 FSL, 25 ASL) |
| Clips (all) | 1324 |
| Clips by language | FSL: 866, ASL: 458 |
| Clips per class (all) | min 9, max 37, mean 26.5, median 24, SD 8.7 |
| Imbalance ratio (max/min) | 4.11 |
| Clips per class without Drive clips (the 889-clip set) | min 9, max 27, mean 17.8, SD 7.3, ratio 3.00 |
| Mean hand-visible frames per clip | 20.5 of 30 (18.1 distinct) |
| `none` (non-sign) sequences, opt-in class | 100 (synthetic_none: 100) |

## Sources

| Source | Clips | How identified | Signers / sessions |
|---|---|---|---|
| FSL-105 dataset (De La Salle University / DOST, CC-BY-4.0) | 501 | numeric file names `0.npy`, `1.npy`, ... (import_fsl105_full.py) | Signer IDs are not kept in the file names. Check the FSL-105 data paper / Mendeley page for the number of signers and enter it in signer_sessions.csv. |
| ASL Citizen dataset (Microsoft Research) | 114 | `asl_<n>.npy` | Crowd-sourced dataset with many signers; per-clip signer IDs were not kept when the clips were imported, so the 6 clips per class may or may not come from 6 different people. |
| ASL Citizen, phrase built by concatenating component-word clips | 24 | `asl_<n>.npy` in the 6 phrase classes | Phrase built by concatenating ASL Citizen word clips; component words can come from different signers. |
| Team-recorded phone clips (user_IMG_*.npy, import_user_clips.py) | 250 | `user_IMG_<nnnn>.npy` (import_user_clips.py) | Phone clips (iPhone IMG_ numbering, 350x640 portrait). Earlier notes describe them as one person / one session - confirm who signed them. |
| Google Drive phone clips collected by the team (drive_<fileId>.npy) | 435 | `drive_<Google Drive file id>.npy` | Phone clips collected through Google Drive (file ids only). Who recorded them, and on how many days, must be filled in by hand. |

Team phone clips fall into these contiguous IMG_ number blocks (a hint for recording sessions): IMG_0926-IMG_1201 (250 clips).

## Signers and recording sessions

Not derivable from the landmark files. Fill in `ai/evaluation/signer_sessions_template.csv` (one row per clip; `label`/`file` accept wildcards such as `drive_*`), save it as `ai/evaluation/signer_sessions.csv`, and re-run this script. Until then the manuscript should say signer counts are unknown for the ASL Citizen and Drive subsets.

## Train / validation / test splits

| Split | Train | Val | Test | Notes |
|---|---|---|---|---|
| Deployed model v1.1, original clips | 714 | 175 | - | train.py logic: seed 42, 20% per class, stratified; reproduced exactly |
| Deployed model v1.1, Drive clips | 258 | 46 | 131 | reported in `models/drive_retrain_results.json`; 30% per class test, 1 val, rest train; the RNG was not recorded, so the Drive test set cannot be rebuilt |
| `train.py` run today (all 1324 clips) | 1061 | 263 | - | same logic over everything in raw_iso/ |

All splits are random per clip: the same signers appear in train and validation. A signer-independent test set is described in `ai/evaluation/signer_independent/README.md`.

Clips removed by `drop_bad_clips()` today: 0 (the 5 conflicting/empty FSL-105 clips were already moved to `dataset/removed_clips/`).

## Samples per class

| # | Sign | Lang | Category | Total | FSL-105 | ASL Cit. | ASL Cit. concat | Team phone | Drive | Train (v1.1 orig) | Val (v1.1 orig) | Hands (est.) | Signers | Sessions |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 0 | isa | FSL | NUMBER | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 1 | dalawa | FSL | NUMBER | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 2 | tatlo | FSL | NUMBER | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 3 | apat | FSL | NUMBER | 28 | 18 | 0 | 0 | 5 | 5 | 18 | 5 | one | ? | ? |
| 4 | lima | FSL | NUMBER | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 5 | anim | FSL | NUMBER | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 6 | pito | FSL | NUMBER | 34 | 19 | 0 | 0 | 5 | 10 | 19 | 5 | one | ? | ? |
| 7 | walo | FSL | NUMBER | 34 | 20 | 0 | 0 | 5 | 9 | 20 | 5 | one | ? | ? |
| 8 | siyam | FSL | NUMBER | 33 | 20 | 0 | 0 | 5 | 8 | 20 | 5 | one | ? | ? |
| 9 | sampu | FSL | NUMBER | 33 | 20 | 0 | 0 | 5 | 8 | 20 | 5 | one | ? | ? |
| 10 | asul | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 11 | berde | FSL | COLOR | 36 | 21 | 0 | 0 | 5 | 10 | 21 | 5 | one | ? | ? |
| 12 | pula | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 13 | kayumanggi | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | two | ? | ? |
| 14 | itim | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 15 | puti | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 16 | dilaw | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 17 | kahel | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 18 | abo | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | two | ? | ? |
| 19 | rosas | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 20 | lila | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | two | ? | ? |
| 21 | maliwanag | FSL | COLOR | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | two | ? | ? |
| 22 | magandang_umaga | FSL | GREETING | 35 | 20 | 0 | 0 | 5 | 10 | 20 | 5 | one | ? | ? |
| 23 | magandang_hapon | FSL | GREETING | 36 | 21 | 0 | 0 | 5 | 10 | 21 | 5 | one | ? | ? |
| 24 | magandang_gabi | FSL | GREETING | 37 | 22 | 0 | 0 | 5 | 10 | 22 | 5 | one | ? | ? |
| 25 | good_morning | ASL | GREETING | 9 | 0 | 0 | 4 | 5 | 0 | 7 | 2 | mixed | ? | ? |
| 26 | good_afternoon | ASL | GREETING | 9 | 0 | 0 | 4 | 5 | 0 | 7 | 2 | mixed | ? | ? |
| 27 | good_evening | ASL | GREETING | 17 | 0 | 0 | 4 | 5 | 8 | 7 | 2 | mixed | ? | ? |
| 28 | hello | ASL | GREETING | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 29 | how_are_you | ASL | GREETING | 19 | 0 | 0 | 4 | 5 | 10 | 7 | 2 | mixed | ? | ? |
| 30 | im_fine | ASL | GREETING | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 31 | nice_to_meet_you | ASL | GREETING | 18 | 0 | 0 | 4 | 5 | 9 | 7 | 2 | mixed | ? | ? |
| 32 | thank_you | ASL | GREETING | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 33 | youre_welcome | ASL | GREETING | 11 | 0 | 6 | 0 | 5 | 0 | 9 | 2 | one | ? | ? |
| 34 | see_you_tomorrow | ASL | GREETING | 14 | 0 | 0 | 4 | 5 | 5 | 7 | 2 | one | ? | ? |
| 35 | understand | ASL | BASIC_RESPONSE | 20 | 0 | 6 | 0 | 5 | 9 | 9 | 2 | one | ? | ? |
| 36 | dont_understand | ASL | BASIC_RESPONSE | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | two | ? | ? |
| 37 | know | ASL | BASIC_RESPONSE | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | mixed | ? | ? |
| 38 | dont_know | ASL | BASIC_RESPONSE | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | two | ? | ? |
| 39 | yes | ASL | BASIC_RESPONSE | 11 | 0 | 6 | 0 | 5 | 0 | 9 | 2 | one | ? | ? |
| 40 | no | ASL | BASIC_RESPONSE | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 41 | wrong | ASL | BASIC_RESPONSE | 15 | 0 | 6 | 0 | 5 | 4 | 9 | 2 | one | ? | ? |
| 42 | correct | ASL | BASIC_RESPONSE | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | two | ? | ? |
| 43 | slow | ASL | BASIC_RESPONSE | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | mixed | ? | ? |
| 44 | fast | ASL | BASIC_RESPONSE | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | two | ? | ? |
| 45 | father | ASL | FAMILY | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 46 | mother | ASL | FAMILY | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 47 | boy | ASL | PEOPLE_RELATIONSHIPS | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 48 | girl | ASL | PEOPLE_RELATIONSHIPS | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | one | ? | ? |
| 49 | married | ASL | PEOPLE_RELATIONSHIPS | 21 | 0 | 6 | 0 | 5 | 10 | 9 | 2 | mixed | ? | ? |

`Hands (est.)` = share of hand-visible frames where MediaPipe found both hands (one < 0.15 <= mixed < 0.5 <= two); verify against the sign itself.

## Other folders (not used for training)

- `ai/dataset/raw/ (pre-aspect-correction originals, incl. unused FSL-105 classes)`: 2502 files
- `ai/dataset/raw_archive_fsl105_unused/`: 452 files
- `ai/dataset/removed_clips/ (duplicates / empty, excluded)`: 10 files

## Assumptions

- Source is inferred from the file-name pattern only (see table above); any file that matches no pattern is listed as Unknown.
- The 6 ASL phrase classes (good_morning, good_afternoon, good_evening, how_are_you, nice_to_meet_you, see_you_tomorrow) are counted as 'ASL Citizen (concat.)' because train.py documents them as concatenations of component-word clips.
- 'Team phone' assumes all `user_IMG_*` files came from `import_user_clips.py` on the team's own phone recordings (350x640 portrait).
- Drive clips for good_morning, good_afternoon (identical videos), yes (not downloaded) and youre_welcome (no folder) are absent by design - see the drive retrain notes.
