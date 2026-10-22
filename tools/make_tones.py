"""Generates the soft alarm tones (res/raw). Pure synthesis, so they belong to the project and have no license to track.
Run: python3 tools/make_tones.py"""
import wave
import numpy as np

SR = 22050
OUT = "app/src/main/res/raw/tone_%s.wav"
NOTE = {"C4": 261.63, "E4": 329.63, "G4": 392.00, "A4": 440.00, "C5": 523.25, "E5": 659.25, "G5": 783.99, "D5": 587.33, "B4": 493.88}


def t(sec):
    return np.arange(int(SR * sec)) / SR


def place(buf, sig, start):
    i = int(SR * start)
    n = min(len(sig), len(buf) - i)
    if n > 0:
        buf[i:i + n] += sig[:n]


def pluck(freq, sec, decay, partials=((1, 1.0), (2, 0.35), (3, 0.12))):
    x = t(sec)
    s = sum(a * np.sin(2 * np.pi * freq * k * x) for k, a in partials)
    return s * np.exp(-x * decay) * np.minimum(1, x / 0.004)


def finish(name, buf, peak=0.45):
    buf = buf / max(1e-9, np.max(np.abs(buf))) * peak
    fade = int(SR * 0.15)
    buf[-fade:] *= np.linspace(1, 0, fade)
    with wave.open(OUT % name, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((buf * 32767).astype("<i2").tobytes())


def harp():
    b = np.zeros(int(SR * 3.0))
    for i, n in enumerate(["C5", "E5", "G5", "C5"]):
        place(b, pluck(NOTE[n], 1.6, 3.2), i * 0.28)
    place(b, pluck(NOTE["G4"], 1.8, 2.6), 1.45)
    place(b, pluck(NOTE["C5"], 1.8, 2.6), 1.8)
    return b


def bells():
    b = np.zeros(int(SR * 3.2))
    parts = ((1, 1.0), (2.76, 0.45), (5.4, 0.18), (8.93, 0.07))
    for start, n in ((0.0, "E5"), (1.2, "C5")):
        place(b, pluck(NOTE[n], 2.0, 2.2, parts), start)
    return b


def glow():
    x = t(3.2)
    env = np.minimum(1, x / 1.0) * np.minimum(1, (3.2 - x) / 1.0)
    s = sum(np.sin(2 * np.pi * f * x) * a for f, a in ((NOTE["C4"], 1.0), (NOTE["E4"], 0.8), (NOTE["G4"], 0.7), (NOTE["C5"], 0.4)))
    s *= 1 + 0.06 * np.sin(2 * np.pi * 4.5 * x)
    return s * env


def drops():
    b = np.zeros(int(SR * 3.0))
    for start, f0 in ((0.0, 880), (0.55, 1046), (1.1, 784), (1.9, 988)):
        x = t(0.7)
        freq = f0 * (1 + 0.35 * np.exp(-x * 18))
        ph = 2 * np.pi * np.cumsum(freq) / SR
        place(b, np.sin(ph) * np.exp(-x * 7) * np.minimum(1, x / 0.003), start)
    return b


def marimba():
    b = np.zeros(int(SR * 3.0))
    parts = ((1, 1.0), (4, 0.25))
    for i, n in enumerate(["E4", "G4", "C5", "G4"]):
        place(b, pluck(NOTE[n], 1.0, 5.5, parts), i * 0.32)
    place(b, pluck(NOTE["E5"], 1.4, 4.0, parts), 1.5)
    return b


for name, fn in (("soft_harp", harp), ("calm_bells", bells), ("warm_glow", glow), ("water_drops", drops), ("soft_marimba", marimba)):
    finish(name, fn())
    print("wrote", OUT % name)
