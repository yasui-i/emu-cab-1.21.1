"""Compare runtime exterior motor preview with the brake-release reference.

Requires the task-local audio Python dependencies; does not modify game assets.
"""
from pathlib import Path
import json
import math
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/audio-python"))
import imageio_ffmpeg
import numpy as np
import soundfile as sf

OUT = ROOT / "build/bug-audio-diagnosis"
OUT.mkdir(parents=True, exist_ok=True)
FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()


def measure(path, start=0.0, duration=None):
    data, rate = sf.read(path, always_2d=True)
    first = int(start * rate)
    stop = None if duration is None else first + int(duration * rate)
    data = data[first:stop]
    mono = data.mean(axis=1)
    rms = float(np.sqrt(np.mean(mono * mono)))
    filters = [f"atrim=start={start}" + ("" if duration is None else f":duration={duration}"),
               "asetpts=PTS-STARTPTS", "loudnorm=I=-23:TP=-1:LRA=11:print_format=json"]
    result = subprocess.run([FFMPEG, "-hide_banner", "-nostats", "-i", str(path), "-af", ",".join(filters),
                             "-f", "null", "-"], capture_output=True, text=True, encoding="utf-8", errors="replace", check=True)
    objects = re.findall(r'\{\s*"input_i".*?\}', result.stderr, flags=re.S)
    loud = json.loads(objects[-1])
    block = int(.1 * rate)
    powers = np.mean(mono[:len(mono) // block * block].reshape(-1, block) ** 2, axis=1)
    active = powers[powers > np.max(powers) * .01]
    return {"path": str(path), "start_s": start, "duration_s": len(data) / rate,
            "rms": rms, "rms_dbfs": 20 * math.log10(max(rms, 1e-12)),
            "sample_peak": float(np.max(abs(mono))), "integrated_lufs": float(loud["input_i"]),
            "true_peak_dbtp": float(loud["input_tp"]),
            "active_100ms_rms_median_dbfs": float(10 * np.log10(np.median(active)))}


def main():
    release = measure(ROOT / "src/main/resources/assets/emucab/sounds/bogey_brake_release.ogg")
    motor = ROOT / "build/车外VVVF整合-0.5.16/游戏实时合成器_54秒试听.ogg"
    results = {"brake_release": release,
               "baseline_version": "0.5.16 archived preview; original motor gain1.0",
               "motor_traction_low_mid": measure(motor, 4, 10),
               "motor_traction_high": measure(motor, 14, 10),
               "motor_coast": measure(motor, 25, 4),
               "motor_braking": measure(motor, 33, 10)}
    base = results["motor_traction_high"]
    gain_db = release["integrated_lufs"] - base["integrated_lufs"]
    results["comparison"] = {"default_motor_and_brake_volume": .65,
                             "high_traction_to_release_lufs_gap_db": gain_db,
                             "full_mix_linear_gain_for_equal_integrated_loudness": 10 ** (gain_db / 20),
                             "note": "Distance, number of bogeys, dynamic load, source gain balancing and listener orientation still affect in-game level. This is digital reference comparison, not measured acoustic SPL."}
    balance_db = -10 * math.log10(3)
    low_gap = release["integrated_lufs"] - (results["motor_traction_low_mid"]["integrated_lufs"] + balance_db)
    results["runtime_balance_and_recommendation"] = {
        "three_source_single_bogey_gain_db": balance_db,
        "low_mid_traction_single_near_bogey_lufs_gap_db": low_gap,
        "low_mid_traction_compensating_linear_gain": 10 ** (low_gap / 20),
        "implemented_electromagnetic_gain_default": 1.8,
        "implemented_gain_db": 20 * math.log10(1.8),
        "reason": "Count normalization attenuates even the only close bogey while other selected sources may be distant. The implemented motor-only boost is approximately5.1dB before the PCM limiter; rolling and wind are unchanged.",
        "limitation": "A single fixed gain cannot equalize every RPM/load and distance against a one-shot release. Full-mix reference includes mechanical and wind; the recommendation prioritizes the weak low/mid-speed motor heard near one bogey."}
    current = ROOT / "build/vvvf-exterior-runtime-preview.wav"
    current_low = measure(current, 4, 10)
    results["current_version"] = {
        "version": "0.5.17; motor_gain1.8; conditioned mechanical texture",
        "traction_low_mid": current_low,
        "traction_high": measure(current, 14, 10),
        "single_near_bogey_at_three_source_balance_lufs": current_low["integrated_lufs"] + balance_db,
        "same_distance_gain_and_blocks_category": True,
        "limitation": "Full mixed PCM reference; not in-game SPL or a guarantee of equal loudness at every RPM."}
    (OUT / "loudness-comparison.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
