"""
SignLSTM: bidirectional LSTM classifier over (30, 126) hand-landmark
sequences -- same architecture family as the original SignTalk ai/ pipeline.
"""

import torch
import torch.nn as nn

INPUT_SIZE = 126


class SignLSTM(nn.Module):
    def __init__(self, num_classes, hidden_size=128, num_layers=2, dropout=0.3):
        super().__init__()
        self.lstm = nn.LSTM(
            input_size=INPUT_SIZE,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            bidirectional=True,
            dropout=dropout if num_layers > 1 else 0.0,
        )
        self.norm = nn.LayerNorm(hidden_size * 2)
        self.head = nn.Sequential(
            nn.Linear(hidden_size * 2, hidden_size),
            nn.ReLU(),
            nn.Dropout(dropout),
            nn.Linear(hidden_size, num_classes),
        )

    def forward(self, x):
        # x: (batch, seq_len, 126)
        out, _ = self.lstm(x)
        # mean-pool over time (robust to where in the sequence the sign appears)
        pooled = out.mean(dim=1)
        pooled = self.norm(pooled)
        return self.head(pooled)
