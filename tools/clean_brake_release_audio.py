"""Reduce narrow motor tones while retaining the broadband brake exhaust.

Requires numpy, scipy, soundfile and imageio-ffmpeg; never modifies the source.
"""
from pathlib import Path
import hashlib
import io
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'build/audio-python'))
import numpy as np
import soundfile as sf
import imageio_ffmpeg
from scipy import ndimage, signal

source = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('C:/Users/zhuyian/Videos/制动缓解.ogg')
out = ROOT / 'model-source/brake-motor-audio-20261104'
out.mkdir(parents=True, exist_ok=True)
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
decoded = subprocess.run([ffmpeg, '-v', 'error', '-i', str(source), '-ac', '1', '-ar', '48000', '-f', 'wav', '-'], capture_output=True, check=True)
x, sr = sf.read(io.BytesIO(decoded.stdout))
# Remove low motor rumble without selecting a different exhaust recording.
filtered = signal.sosfiltfilt(signal.butter(4, 320, 'highpass', fs=sr, output='sos'), x)
f, t, z = signal.stft(filtered, sr, nperseg=2048, noverlap=1536)
mag = abs(z)
# Persistent narrow ridges are motor harmonics. Broadband energy is retained.
harmonic = ndimage.median_filter(mag, size=(1, 19), mode='nearest')
broadband = ndimage.median_filter(mag, size=(21, 1), mode='nearest')
prominence = harmonic / (broadband + 1e-8)
gain = np.clip((1.35 / np.maximum(prominence, 1.35)) ** 1.6, 0.16, 1.0)
gain = ndimage.gaussian_filter(gain, sigma=(0.7, 1.2))
_, y = signal.istft(z * gain, sr, nperseg=2048, noverlap=1536)
y = y[:len(x)]
# Short fades prevent clicks when the positional one-shot starts or ends.
fade = round(.025*sr)
y[:fade] *= np.linspace(0, 1, fade)
y[-fade:] *= np.linspace(1, 0, fade)
before_normalize = y.copy()
y *= min(.115 / max(float(np.sqrt(np.mean(y*y))), 1e-8), .89 / max(float(np.max(abs(y))), 1e-8))
destination = ROOT / 'src/main/resources/assets/emucab/sounds/bogey_brake_release.ogg'
destination.parent.mkdir(parents=True, exist_ok=True)
sf.write(destination, y, sr, format='OGG', subtype='VORBIS')
shutil.copy2(source, out / '制动缓解_原始.ogg')
shutil.copy2(destination, out / '制动缓解_降噪.ogg')
check, actual_sr = sf.read(destination, always_2d=True)
assert actual_sr == sr and check.shape == (len(x), 1)
assert .02 < np.sqrt(np.mean(check*check)) < .4
assert np.max(abs(check)) < 1
def band_db(lo, hi):
    original = np.fft.rfft(x)
    processed = np.fft.rfft(before_normalize)
    freq = np.fft.rfftfreq(len(x), 1/sr)
    band = (freq >= lo) & (freq < hi)
    return float(10*np.log10((np.sum(abs(processed[band])**2)+1e-12)/(np.sum(abs(original[band])**2)+1e-12)))
report = dict(source=str(source), source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
              output=str(destination), output_sha256=hashlib.sha256(destination.read_bytes()).hexdigest(),
              seconds=len(x)/sr, sample_rate=sr, channels=1, codec='Vorbis',
              highpass_hz=320, minimum_tonal_gain=.16, target_rms=.115,
              peak=float(np.max(abs(check))), rms=float(np.sqrt(np.mean(check*check))),
              reduction_before_normalization_db={str(lo)+'-'+str(hi):round(band_db(lo,hi),2) for lo,hi in [(0,250),(250,1000),(1000,4000),(4000,10000)]},
              note='Suppresses rumble and narrow motor harmonics; overlapping broadband motor/exhaust noise cannot be fully separated.')
(out / 'cleanup-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=True, indent=2))
