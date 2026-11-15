"""Local previews only; runtime references CEE's original ARC without bundling it."""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/audio-python"))
import numpy as np
import soundfile as sf
import imageio_ffmpeg

folder = ROOT / "model-source/audio-options-20261104"
audio, rate = sf.read(folder / "02_持续电弧.ogg")
outputs = []
for label, ticks, fade in [("已选_触网短电弧_0.35秒", 7, 2), ("已选_脱网长电弧_1.20秒", 24, 3)]:
    count = round(rate * ticks / 20)
    cut = audio[:count].copy()
    # Smooth preview of the in-game tick envelope, at the same .9 gain.
    remaining = (count - np.arange(count)) / rate
    cut *= .9 * np.minimum(1, remaining / (fade / 20))
    wav = folder / (label + ".wav")
    ogg = folder / (label + ".ogg")
    sf.write(wav, cut, rate, subtype="PCM_16")
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(ogg)], check=True)
    outputs.append({"file": ogg.name, "duration_seconds": ticks / 20, "fade_seconds": fade / 20})
(folder / "当前选择.json").write_text(json.dumps({"source": "electroenergetics:arc", "option": "02_持续电弧", "runtime": "CEE original event, bounded moving SoundInstance; no CEE asset in addon jar", "previews": outputs}, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(outputs, ensure_ascii=False))
