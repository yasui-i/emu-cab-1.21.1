"""Offline exterior acoustic study; does not write any runtime Java or resources.

The measured reference ridge is an acoustic target, not a measured motor current,
carrier, model identification or speed trace. All speed/profile constants below
are explicit audition assumptions. No VVVFSimulator implementation is copied.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/audio-python"))
import numpy as np
import soundfile as sf
from scipy import signal
import imageio_ffmpeg
from vvvf_study_layers import load_layers, loop_layer

OUT = ROOT / "model-source/vvvf-timbre-study-20261104"
RATE = 48000
DURATION = 54.0


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def lowpass(x, hz, order=2, rate=RATE):
    return signal.sosfilt(signal.butter(order, hz, fs=rate, output="sos"), x)


def bandpass(x, lo, hi, order=2, rate=RATE):
    return signal.sosfilt(signal.butter(order, [lo, hi], fs=rate, btype="bandpass", output="sos"), x)


def follow(x, seconds, rate=RATE):
    a = np.exp(-1 / (rate * seconds))
    return signal.lfilter([1 - a], [1, -a], x)


def normalized(x, rms):
    return x * (rms / max(np.sqrt(np.mean(x * x)), 1e-12))


def phase_of(hz, rate=RATE):
    # Integrate an unwrapped phase: multiplying a wrapped fundamental by a
    # noninteger order is discontinuous, which the former prototype did.
    return np.cumsum(hz, dtype=np.float64) * (2 * np.pi / rate)


def resonator(x, hz, q, gain):
    b, a = signal.iirpeak(hz, q, fs=RATE)
    return signal.lfilter(b, a, x) * gain


def trajectory():
    t = np.arange(round(DURATION * RATE), dtype=np.float64) / RATE
    speed = np.zeros_like(t)
    traction = np.zeros_like(t)
    brake = np.zeros_like(t)
    accelerating = (t >= 1.5) & (t < 24)
    coasting = (t >= 24) & (t < 30)
    braking = (t >= 30) & (t < 52)
    speed[accelerating] = 350 * (t[accelerating] - 1.5) / 22.5
    speed[coasting] = 350 - (t[coasting] - 24) * 2
    speed[braking] = 338 * (1 - (t[braking] - 30) / 22)
    traction[accelerating] = .86 - .22 * smooth((speed[accelerating] - 180) / 170)
    brake[braking] = .72 * smooth(speed[braking] / 12)
    # These compressed demonstration accelerations are not a train performance
    # prediction. Speed and wheel speed remain separate controls in the DSP.
    wheel_rpm = speed / 3.6 * 60 / (2 * np.pi * .46)
    return {"time": t, "speed_kmh": speed, "wheel_rpm": wheel_rpm,
            "traction": follow(traction, .12), "brake": follow(brake, .18)}


def harmonic_targets(reference):
    points = reference["selected_harmonic_neighborhoods"]
    tone_hz = np.array([p["tracked_midrange_ridge_hz"] for p in points])
    order_db = np.array([[p["peaks"][i]["relative_to_ridge_db"] for i in range(5)] for p in points])
    # Broad spectral target, bounded so a single noisy phone-recording bin
    # cannot prescribe the entire synthesized spectrum.
    order_db = np.clip(order_db, -32, 5)
    return tone_hz, order_db


def pwm_current_force(electrical_hz, carrier_hz, modulation, seed):
    """Simplified RL-current/quadratic force texture, not electromagnetic FEA.

    Async PWM and 9/5-pulse synchronous branches coexist during transitions.
    Synchronous triangles are calculated from electrical phase directly, so
    they cannot drift relative to the fundamental as independent oscillators do.
    """
    rng = np.random.default_rng(seed)
    # Twice the output sample rate, decimated with an anti-alias FIR.
    fe = np.repeat(electrical_hz, 2)
    fc = np.repeat(carrier_hz, 2)
    mod = np.repeat(modulation, 2)
    ph = phase_of(fe, RATE * 2) + rng.uniform(0, 2 * np.pi)
    cp = phase_of(fc, RATE * 2)
    ta = 4 * np.abs((cp / (2 * np.pi)) % 1 - .5) - 1
    p9 = 4 * np.abs((ph / (2 * np.pi) * 9) % 1 - .5) - 1
    p5 = 4 * np.abs((ph / (2 * np.pi) * 5) % 1 - .5) - 1
    sync_mix = smooth((fe - 105) / 20)
    five_mix = smooth((fe - 145) / 15)
    # Acoustic crossfade of separately evaluated modes, rather than morphing
    # their triangle waves, which could invent irregular switch patterns.
    accum = np.zeros_like(fe)
    for triangle, weight in [(ta, 1 - sync_mix), (p9, sync_mix * (1 - five_mix)), (p5, sync_mix * five_mix)]:
        currents = []
        volts = []
        for shift in (0, 2 * np.pi / 3, 4 * np.pi / 3):
            volts.append(np.where(np.sin(ph + shift) * mod > triangle, 1., -1.))
        common = (volts[0] + volts[1] + volts[2]) / 3
        for voltage in volts:
            # RL response attenuates switching voltage before force calculation.
            currents.append(follow(voltage - common, .00075, RATE * 2))
        force = currents[0] ** 2 - .5 * currents[1] ** 2 - .5 * currents[2] ** 2
        accum += force * weight
        del currents, volts, common, force
    output = signal.resample_poly(accum, 1, 2)
    output = bandpass(output, 240, 4200)
    return normalized(output, .035)


def exterior_motor(curve, reference, seed, offset):
    rng = np.random.default_rng(seed)
    t, rpm = curve["time"], curve["wheel_rpm"]
    electrical_hz = rpm * 2.5 / 60 * 2
    load = np.maximum(curve["traction"], curve["brake"] * .78)
    running = smooth(curve["speed_kmh"] / 4)
    # Ref. begins with a near-fixed ~451 Hz ridge, then sweeps upward. The
    # reference supplies no RPM: this frequency-to-speed mapping is designed,
    # exposed in the report, and must not be described as measured train data.
    ratio = smooth((curve["speed_kmh"] - 80) / 270)
    tone = 451.2 + 416 * ratio
    tone *= 1 + offset + .00011 * np.sin(2 * np.pi * .41 * t + seed)
    phase = phase_of(tone) + rng.uniform(0, 2 * np.pi)
    targets, db = harmonic_targets(reference)
    tonal = np.zeros_like(t)
    for order in range(5):
        level = 10 ** (np.interp(tone, targets, db[:, order]) / 20)
        # Small independent phase offsets preserve harmonic family while
        # avoiding identical waveforms from every virtual motor.
        tonal += np.sin((order + 1) * phase + rng.uniform(-.14, .14)) * level / 2.8
    # Smooth harmonics are the calibrated acoustic part, not an assertion that
    # the tracked ridge is the electrical fundamental or PWM carrier.
    tonal = bandpass(tonal, 300, 4300)
    shaped = .63 * tonal + resonator(tonal, 560, 3.5, .24) + resonator(tonal, 1450, 2.8, .30)
    modulation = np.clip(.18 + electrical_hz / 110 * .75, .18, .96)
    texture = pwm_current_force(electrical_hz, tone, modulation, seed)
    texture = .6 * texture + resonator(texture, 850, 4, .35) + resonator(texture, 2150, 3, .25)
    # A tiny modulation of the envelope adds finite winding/structural texture;
    # no arbitrary wide-pitch wobble is imposed on the main tone.
    breath = 1 + .025 * np.sin(2 * np.pi * 6.3 * t + seed)
    excitation = (shaped * .34 + texture * .35) * np.sqrt(load) * running * breath
    # Gear/shaft orders have independent integrated phase and low gain, so a
    # low-frequency sinusoid no longer overwhelms the measured midrange tone.
    shaft_hz = rpm * 2.5 / 60
    gear = (np.sin(phase_of(shaft_hz * 23) + seed) * .010
            + np.sin(phase_of(shaft_hz * 46) + seed * .4) * .004)
    gear *= smooth(curve["speed_kmh"] / 25) * (.28 + .72 * np.sqrt(load))
    return excitation, bandpass(gear, 180, 5000), {"electrical_hz": electrical_hz, "tone_hz": tone}


def shifted_texture(source, curve, seed):
    rng = np.random.default_rng(seed)
    length = len(curve["time"])
    texture = loop_layer(source, length + RATE * 2, RATE, offset_seconds=rng.uniform(0, 1))
    # Mild wheel-rail texture-rate change; electric tones are never obtained
    # by pitching these recordings.
    read_rate = .68 + .38 * np.sqrt(curve["speed_kmh"] / 350)
    index = np.cumsum(read_rate)
    return np.interp(index, np.arange(len(texture)), texture)


def render(curve, reference, layers):
    n = len(curve["time"])
    electric = np.zeros((n, 2), dtype=np.float64)
    gear = np.zeros_like(electric)
    tones = None
    # Two close traction sound groups, as a co-moving exterior listening
    # perspective. Fixed stereo placement; no staged trackside Doppler pass.
    for seed, detune, pan in [(8143, -.00065, -.24), (2099, .00065, .24)]:
        motor, mesh, info = exterior_motor(curve, reference, seed, detune)
        gains = [np.sqrt((1 - pan) / 2), np.sqrt((1 + pan) / 2)]
        for ch in range(2):
            electric[:, ch] += motor * gains[ch] / np.sqrt(2)
            gear[:, ch] += mesh * gains[ch] / np.sqrt(2)
        tones = info
    rolling = np.zeros_like(electric)
    wind = np.zeros_like(electric)
    speed = curve["speed_kmh"] / 350
    for ch, seed in [(0, 41), (1, 93)]:
        rail = shifted_texture(layers["mechanical"], curve, seed)
        rolling[:, ch] = rail * (.13 + .54 * np.sqrt(speed)) * smooth(speed / .012)
        air = loop_layer(layers["wind"], n, RATE, offset_seconds=ch * 5.371)
        wind[:, ch] = air * .58 * speed ** 1.75
    # Fixed causal filters can be implemented in a real-time audio block later.
    # No dependence on future samples in this prototype's synthesis or mixing.
    mixed = electric + gear + rolling + wind
    for ch in range(2):
        mixed[:, ch] = bandpass(mixed[:, ch], 95, 6500)
    envelope = smooth(curve["time"] / .025) * smooth((DURATION - curve["time"]) / .25)
    mixed *= envelope[:, None]
    active = curve["speed_kmh"] > 5
    gain = .105 / np.sqrt(np.mean(mixed[active] ** 2))
    # One constant gain for the entire run, preserving real coasting/braking
    # level differences. Peak protection avoids per-stage normalization.
    gain = min(gain, .86 / np.max(np.abs(mixed)))
    mixed *= gain
    stems = {"electromagnetic": electric * gain, "gear": gear * gain,
             "rolling": rolling * gain, "wind": wind * gain}
    return mixed, stems, tones, gain


def encode(wav, ogg):
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", str(wav),
                    "-c:a", "libvorbis", "-q:a", "6", str(ogg)], check=True)
    data, rate = sf.read(ogg, always_2d=True)
    assert rate == RATE and np.isfinite(data).all() and np.max(np.abs(data)) < 1


def metric(audio):
    rms = np.sqrt(np.mean(audio ** 2))
    return {"seconds": len(audio) / RATE, "channels": audio.shape[1] if audio.ndim > 1 else 1,
            "peak": float(np.max(np.abs(audio))), "rms_dbfs": float(20 * np.log10(max(rms, 1e-12))),
            "dc": float(np.mean(audio)), "maximum_sample_step": float(np.max(np.abs(np.diff(audio, axis=0))))}


def diagnostic(mixed, curve, stems):
    assert np.isfinite(mixed).all()
    assert np.max(np.abs(mixed)) <= .861
    assert abs(float(np.mean(mixed))) < .001
    assert mixed.shape == (round(DURATION * RATE), 2)
    assert np.max(np.abs(mixed[:RATE])) < 1e-5
    assert np.max(np.abs(mixed[-RATE:])) < .001
    # At the *same* maintained high speed, neutral drops electrical excitation
    # while rolling/air continue. They are not all muted by the master handle.
    coast = (curve["time"] > 25.5) & (curve["time"] < 29)
    pull = (curve["time"] > 21) & (curve["time"] < 23.5)
    assert np.sqrt(np.mean(stems["electromagnetic"][coast] ** 2)) < .01 * np.sqrt(np.mean(stems["electromagnetic"][pull] ** 2))
    assert np.sqrt(np.mean(stems["rolling"][coast] ** 2)) > .005
    assert np.sqrt(np.mean(stems["wind"][coast] ** 2)) > .005
    # Air is controlled by body speed, not wheel RPM: render() never uses RPM
    # when calculating its wind layer. The declared test profile has no slip.
    return {"status": "PASS", "checks": ["finite stereo PCM", "headroom", "DC", "initial/final silence", "neutral electromagnetic fade", "rolling and wind survive neutral"],
            "stems": {k: metric(v) for k, v in stems.items()}}


def main():
    started = time.perf_counter()
    OUT.mkdir(parents=True, exist_ok=True)
    reference = json.loads((OUT / "reference-analysis.json").read_text(encoding="utf-8"))
    layers = load_layers(ROOT, RATE)
    curve = trajectory()
    mixed, stems, tones, gain = render(curve, reference, layers)
    report = diagnostic(mixed, curve, stems)
    main_name = "车外_VVVF音色样片_54秒"
    sf.write(OUT / (main_name + ".wav"), mixed, RATE, subtype="PCM_24")
    encode(OUT / (main_name + ".wav"), OUT / (main_name + ".ogg"))
    # Compact audition with three explicit sections and .4 s silent separators.
    pieces = []
    for start, end in [(3, 9), (16, 22), (34, 40)]:
        clip = mixed[round(start * RATE):round(end * RATE)].copy()
        fade = smooth(np.arange(len(clip)) / (RATE * .035)) * smooth((len(clip) - 1 - np.arange(len(clip))) / (RATE * .06))
        pieces.extend([clip * fade[:, None], np.zeros((round(.4 * RATE), 2))])
    short = np.concatenate(pieces[:-1])
    short_name = "车外_起步加速制动_19秒摘要"
    sf.write(OUT / (short_name + ".wav"), short, RATE, subtype="PCM_24")
    encode(OUT / (short_name + ".wav"), OUT / (short_name + ".ogg"))
    csv = np.column_stack([curve[k][::RATE // 10] for k in ["time", "speed_kmh", "wheel_rpm", "traction", "brake"]])
    np.savetxt(OUT / "演示工况.csv", csv, delimiter=",", header="seconds,speed_kmh,wheel_rpm,traction01,brake01", comments="", fmt="%.5f")
    report.update({"output": metric(mixed), "master_gain": float(gain), "render_seconds": time.perf_counter() - started,
                   "files": [main_name + ".ogg", short_name + ".ogg"],
                   "reference_use": "Measured acoustic ridge and broad relative harmonic levels only; no reference audio is mixed into this preview",
                   "assumptions": {"wheel_radius_metres": .46, "gear_ratio": 2.5, "pole_pairs": 2, "gear_teeth": 23,
                                   "demonstration_maximum_kmh": 350, "compressed_acceleration": True,
                                   "tone_speed_map": "451.2Hz through 80km/h; designed smooth interpolation to867.2Hz at350km/h; NOT a measured speed map"},
                   "model": ["phase-integrated reference-inspired harmonic family", "2x-oversampled simplified RL-current quadratic-force texture", "electrical-phase-locked 9/5 PWM branches with continuous acoustic crossfade", "parallel low-Q casing resonators", "independent wheel-rail and body-speed air layers", "small independent motor variation", "stereo co-moving exterior perspective"],
                   "asset_sources": layers["metadata"],
                   "limitations": ["No complete electromagnetic/mechanical simulation", "No claimed target-train identification or exact tuning", "No manual listening judgement", "Recorded game textures are local audition placeholders, not isolated real-EMU field recordings", "No addon jar, runtime source, installed mods or saves modified"]})
    for name in report["files"]:
        report.setdefault("sha256", {})[name] = hashlib.sha256((OUT / name).read_bytes()).hexdigest()
    (OUT / "试听验证.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    # Separate diagnostic float stems stay under build, rather than cluttering
    # the listening folder or becoming mod resources by accident.
    cache = ROOT / "build/vvvf-timbre-study-cache"
    for name, stem in stems.items():
        sf.write(cache / (name + "-full.wav"), stem, RATE, subtype="FLOAT")
    print(json.dumps({"status":report["status"], "output":report["output"], "render_seconds":report["render_seconds"], "files":report["files"]}, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
