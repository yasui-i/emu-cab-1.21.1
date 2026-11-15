from pathlib import Path
import json
import sys
import subprocess
import io

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'build/audio-python'))
import numpy as np
import soundfile as sf
import imageio_ffmpeg
from scipy import signal

source = Path('C:/Users/zhuyian/Videos/制动缓解.ogg')
out = ROOT / 'model-source/brake-motor-audio-20261104'
out.mkdir(parents=True, exist_ok=True)
decoded = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-v', 'error', '-i', str(source), '-ac', '1', '-ar', '48000', '-f', 'wav', '-'], capture_output=True, check=True)
x, sr = sf.read(io.BytesIO(decoded.stdout))
sf.write(out / 'original-decoded.wav', x, sr)
f, t, z = signal.stft(x, sr, nperseg=2048, noverlap=1536)
mag = abs(z)
peaks, _ = signal.find_peaks(np.mean(mag, axis=1), distance=2)
peaks = sorted(peaks, key=lambda k: np.mean(mag[k]), reverse=True)[:35]
report = {'source': str(source), 'sample_rate': sr, 'seconds': len(x)/sr, 'peak': float(max(abs(x))), 'rms': float(np.sqrt(np.mean(x*x))),
          'spectral_peaks_hz': [round(float(f[k]), 1) for k in peaks],
          'rms_per_quarter_second': [round(float(np.sqrt(np.mean(v*v))), 5) for v in np.array_split(x, max(1, int(len(x)/sr*4)))]}
(out / 'analysis.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=True, indent=2))
try:
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(2, 1, figsize=(13, 7), constrained_layout=True)
    axes[0].plot(np.arange(len(x))/sr, x, linewidth=.4)
    axes[1].pcolormesh(t, f, 20*np.log10(mag+1e-7), vmin=-90, vmax=-30, shading='auto', cmap='magma')
    axes[1].set_ylim(0, 10000)
    axes[0].set_ylabel('Amplitude')
    axes[1].set_ylabel('Hz')
    axes[1].set_xlabel('Time (s)')
    fig.savefig(out / 'original-spectrum.png', dpi=120)
except ImportError:
    from PIL import Image
    db = 20*np.log10(mag+1e-7)
    db = db[f <= 10000][::-1]
    v = np.clip((db+95)/65, 0, 1)
    rgb = np.stack([np.clip(v*2, 0, 1), np.clip(v*2-0.6, 0, 1), np.clip(v*2-1.3, 0, 1)], axis=-1)
    Image.fromarray((rgb*255).astype('uint8')).resize((1200, 600)).save(out / 'original-spectrum.png')
