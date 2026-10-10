# Threshold recommendation: deployed_v1.1_threshold_sweep

- Model: `models/sign_lstm.pt`
- Test set: train.py validation split of the original 889 clips (seed 42, 20% per class) - the held-out split of the deployed model (175 clips)
- Non-sign set: 100 non-sign sequences from `synthetic_none` - SYNTHETIC stopgap data; replace with recorded non-sign clips before citing
- Rule: highest coverage with accuracy on accepted clips >= 0.95

- Recommended (single window): threshold **0.30**: coverage 0.994, accepted accuracy 0.971, error 0.029, non-sign false accept 0.810
- Recommended (stream, 3 agreeing windows): threshold **0.70**: coverage 0.949, accepted accuracy 0.952, error 0.048, non-sign false accept 0.520
- Alternative, also capping non-sign false accept at 0.20 (single window): threshold **0.80**: coverage 0.863, accepted accuracy 0.993, error 0.007, non-sign false accept 0.180
- Alternative, also capping non-sign false accept at 0.20 (stream, 3 agreeing windows): threshold **0.95**: coverage 0.069, accepted accuracy 1.000, error 0.000, non-sign false accept 0.040
- Current app threshold 0.6: threshold **0.60**: coverage 0.960, accepted accuracy 0.988, error 0.012, non-sign false accept 0.360

## Single window (whole clip)

| Threshold | Coverage | Accepted accuracy | Error on accepted | Correct overall | Non-sign accepted |
|---|---|---|---|---|---|
| 0.30 | 0.994 | 0.971 | 0.029 | 0.966 | 0.810 |
| 0.35 | 0.989 | 0.977 | 0.023 | 0.966 | 0.720 |
| 0.40 | 0.989 | 0.977 | 0.023 | 0.966 | 0.660 |
| 0.45 | 0.989 | 0.977 | 0.023 | 0.966 | 0.560 |
| 0.50 | 0.971 | 0.976 | 0.024 | 0.949 | 0.510 |
| 0.55 | 0.966 | 0.982 | 0.018 | 0.949 | 0.450 |
| 0.60 | 0.960 | 0.988 | 0.012 | 0.949 | 0.360 |
| 0.65 | 0.960 | 0.988 | 0.012 | 0.949 | 0.340 |
| 0.70 | 0.920 | 0.988 | 0.012 | 0.909 | 0.290 |
| 0.75 | 0.903 | 0.987 | 0.013 | 0.891 | 0.220 |
| 0.80 | 0.863 | 0.993 | 0.007 | 0.857 | 0.180 |
| 0.85 | 0.823 | 1.000 | 0.000 | 0.823 | 0.150 |
| 0.90 | 0.651 | 1.000 | 0.000 | 0.651 | 0.080 |
| 0.95 | 0.046 | 1.000 | 0.000 | 0.046 | 0.010 |

## Streaming stability rule (coverage / accepted accuracy / non-sign accepted)

| Threshold | N=1 | N=2 | N=3 | N=5 |
|---|---|---|---|---|
| 0.30 | 1.00 / 0.851 / 0.92 | 0.99 / 0.868 / 0.91 | 0.99 / 0.902 / 0.91 | 0.99 / 0.908 / 0.87 |
| 0.35 | 0.99 / 0.868 / 0.89 | 0.99 / 0.868 / 0.88 | 0.99 / 0.902 / 0.87 | 0.99 / 0.913 / 0.82 |
| 0.40 | 0.99 / 0.874 / 0.86 | 0.99 / 0.891 / 0.84 | 0.99 / 0.908 / 0.82 | 0.99 / 0.913 / 0.77 |
| 0.45 | 0.99 / 0.879 / 0.81 | 0.99 / 0.902 / 0.78 | 0.99 / 0.914 / 0.74 | 0.99 / 0.925 / 0.71 |
| 0.50 | 0.99 / 0.896 / 0.79 | 0.99 / 0.908 / 0.73 | 0.99 / 0.919 / 0.70 | 0.98 / 0.924 / 0.66 |
| 0.55 | 0.99 / 0.908 / 0.76 | 0.99 / 0.913 / 0.69 | 0.99 / 0.919 / 0.64 | 0.98 / 0.930 / 0.60 |
| 0.60 | 0.99 / 0.908 / 0.74 | 0.99 / 0.913 / 0.65 | 0.98 / 0.924 / 0.60 | 0.97 / 0.935 / 0.54 |
| 0.65 | 0.99 / 0.913 / 0.71 | 0.98 / 0.924 / 0.60 | 0.97 / 0.935 / 0.55 | 0.95 / 0.952 / 0.52 |
| 0.70 | 0.97 / 0.918 / 0.63 | 0.97 / 0.929 / 0.57 | 0.95 / 0.952 / 0.52 | 0.94 / 0.958 / 0.50 |
| 0.75 | 0.94 / 0.958 / 0.58 | 0.93 / 0.969 / 0.52 | 0.93 / 0.969 / 0.49 | 0.93 / 0.969 / 0.43 |
| 0.80 | 0.91 / 0.963 / 0.51 | 0.91 / 0.969 / 0.45 | 0.90 / 0.968 / 0.42 | 0.88 / 0.974 / 0.38 |
| 0.85 | 0.89 / 0.981 / 0.45 | 0.87 / 0.980 / 0.40 | 0.85 / 0.993 / 0.37 | 0.81 / 0.993 / 0.32 |
| 0.90 | 0.70 / 1.000 / 0.33 | 0.69 / 1.000 / 0.27 | 0.67 / 1.000 / 0.23 | 0.64 / 1.000 / 0.18 |
| 0.95 | 0.07 / 1.000 / 0.06 | 0.07 / 1.000 / 0.04 | 0.07 / 1.000 / 0.04 | 0.07 / 1.000 / 0.03 |

Notes:
- Label smoothing 0.1 caps the softmax: confidences rarely exceed ~0.9, so thresholds >= 0.9 reject almost everything regardless of correctness.
- On a random per-clip split almost every clip is correct, so accuracy hardly changes with the threshold. The threshold is mainly a guard against NON-signs and new signers: re-run this on the signer-independent set and a recorded none set before fixing the app value.
