"""Recorded texture layers for LOCAL VVVF listening studies, never runtime assets.

Sources remain in the user's installed dependency jars. These are game recordings,
not isolated field measurements of a particular EMU. The user video is not used as
a supposedly clean mechanical recording. load_layers returns mono float64 arrays;
loop_layer makes finite playback with overlapping loop edges.
"""
from __future__ import annotations

import hashlib
import io
import json
import math
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/audio-python"))
import numpy as np
import soundfile as sf
from scipy import signal, ndimage


def _recording(jar: Path, asset: str, rate: int):
    with zipfile.ZipFile(jar) as archive:
        raw = archive.read(asset)
    data, original_rate = sf.read(io.BytesIO(raw), always_2d=True)
    data = data.mean(axis=1)
    if original_rate != rate:
        divisor = math.gcd(rate, original_rate)
        data = signal.resample_poly(data, rate // divisor, original_rate // divisor)
    data -= np.mean(data)
    return data, {"jar": str(jar), "asset": asset,
                  "asset_sha256": hashlib.sha256(raw).hexdigest(),
                  "original_sample_rate": original_rate,
                  "original_duration_seconds": len(data) / rate}


def _texture(data: np.ndarray, rate: int, low: float, high: float):
    # Remove DC/subsonic energy and distracting bright hiss before mixing. The
    # fixed narrowband suppression reduces baked-in tones; it is not source
    # separation and cannot guarantee an isolated motor-free recording.
    sos = signal.butter(3, [low, high], fs=rate, btype="bandpass", output="sos")
    data = signal.sosfiltfilt(sos, data)
    _, _, spectrum = signal.stft(data, fs=rate, nperseg=2048, noverlap=1536)
    average = np.mean(abs(spectrum) ** 2, axis=1)
    broad = ndimage.median_filter(average, size=15, mode="nearest")
    ratio = average / np.maximum(broad, 1e-12)
    gain = np.clip(np.sqrt(5 / np.maximum(ratio, 5)), .35, 1)
    _, restored = signal.istft(spectrum * gain[:, None], fs=rate,
                               nperseg=2048, noverlap=1536)
    data = restored[:len(data)]
    data -= np.mean(data)
    rms = float(np.sqrt(np.mean(data ** 2)))
    if rms < 1e-10:
        raise ValueError("Recorded texture is silent")
    data *= .1 / rms  # Reproducible layer level; final mix owns loudness.
    return data


def load_layers(root: str | Path = ROOT, rate: int = 48000):
    """Return {'mechanical': mono array, 'wind': mono array, 'metadata': dict}."""
    root = Path(root)
    cache = root / "build/vvvf-timbre-study-cache"
    cache.mkdir(parents=True, exist_ok=True)
    definitions = {
        "mechanical": (root / "libs/create.jar", "assets/create/sounds/train.ogg", 90, 3800),
        "wind": (root / "libs/electroenergetics.jar", "assets/electroenergetics/sounds/train/background/wind_static.ogg", 120, 4500),
    }
    layers = {}
    metadata = {"usage": "Local listening study only; not bundled in addon jar",
                "warning": "Existing game recordings, not isolated EMU field recordings; narrowband reduction is not perfect source separation",
                "sample_rate": rate, "layers": {}}
    for key, (jar, asset, low, high) in definitions.items():
        raw, source = _recording(jar, asset, rate)
        data = _texture(raw, rate, low, high)
        source.update({"processing": ["mono", "polyphase resample", "DC removal",
                                      f"{low}-{high} Hz bandpass", "fixed narrowband attenuation capped at -9.1 dB", "RMS 0.1"],
                       "output_rms": float(np.sqrt(np.mean(data ** 2))),
                       "output_peak": float(abs(data).max())})
        layers[key] = data
        metadata["layers"][key] = source
        sf.write(cache / (key + ".wav"), data, rate, subtype="FLOAT")
    layers["metadata"] = metadata
    (cache / "sources.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return layers


def loop_layer(samples: np.ndarray, length: int, rate: int = 48000,
               fade_seconds: float = .12, offset_seconds: float = 0):
    """Crossfaded periodic texture, with no hard concatenation of loop edges."""
    source = np.asarray(samples, dtype=np.float64)
    if source.ndim != 1 or len(source) < 8 or length < 0:
        raise ValueError("Expected a mono source and nonnegative output length")
    overlap = min(max(2, round(rate * fade_seconds)), len(source) // 4)
    cycle = source[overlap:].copy()
    blend = np.sin(np.linspace(0, np.pi / 2, overlap)) ** 2
    cycle[-overlap:] = source[-overlap:] * (1 - blend) + source[:overlap] * blend
    shift = round(offset_seconds * rate) % len(cycle)
    indices = (np.arange(length) + shift) % len(cycle)
    return cycle[indices]


if __name__ == "__main__":
    layers = load_layers()
    checks = {}
    for name in ("mechanical", "wind"):
        audio = loop_layer(layers[name], 48000 * 20)
        assert np.all(np.isfinite(audio))
        assert abs(float(np.mean(layers[name]))) < 1e-10
        checks[name] = {"duration_seconds": len(layers[name]) / 48000,
                        "loop_duration_seconds": len(audio) / 48000,
                        "max_step": float(np.max(abs(np.diff(audio)))),
                        "rms": float(np.sqrt(np.mean(audio ** 2)))}
    print(json.dumps(checks, ensure_ascii=False, indent=2))
