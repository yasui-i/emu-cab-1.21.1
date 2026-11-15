"""Validated exterior-audio release, excluding third-party audio assets."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/audio-python"))
import imageio_ffmpeg
import soundfile as sf

OUT = ROOT / "build/车外VVVF整合-0.5.17"
OUT.mkdir(parents=True, exist_ok=True)
jar = ROOT / "build/libs/emucab-0.5.17.jar"
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None
    assert 'version="0.5.17"' in z.read("META-INF/neoforge.mods.toml").decode()
    for name in ["client/BogeyRecordedLayers", "client/BogeyMotorSounds", "audio/VvvfSynthesizer", "audio/VvvfProfile", "telemetry/BogeyMotorTelemetry"]:
        assert "com/yasui_i/emucab/" + name + ".class" in z.namelist()
    assert not any(n.startswith(("assets/create/", "assets/electroenergetics/", "com/george_vi/")) for n in z.namelist())

copies = [(jar, OUT / jar.name),
          (ROOT / "model-source/vvvf-exterior-integration-20261104/使用说明.md", OUT / "使用说明.md"),
          (ROOT / "model-source/vvvf-exterior-integration-20261104/纹理来源.md", OUT / "纹理来源.md"),
          (ROOT / "model-source/vvvf-exterior-integration-20261104/0.5.17修复说明.md", OUT / "0.5.17修复说明.md")]
for src, dst in copies:
    shutil.copy2(src, dst)
evidence = [("build/audio-fix-final-build.log", "构建与19项检查.log"),
            ("build/motor-client/motor-client-check.txt", "客户端实际PCM与纹理加载.txt"),
            ("build/motor-client/streaming-continuity.txt", "OpenAL连续播放检查.txt"),
            ("build/motor-server/motor-server-check.txt", "电机供电回归.txt"),
            ("build/telemetry-server/telemetry-check.txt", "实际Sable轮速与车体速度.txt"),
            ("build/vvvf-exterior-runtime-preview.txt", "游戏合成器54秒检查.txt"),
            ("build/bug-audio-diagnosis/loudness-comparison.json", "电机与制动缓解响度对比.json")]
for source, name in evidence:
    src = ROOT / source
    text = src.read_text(encoding="utf-8", errors="replace")
    assert "FAIL" not in text and (src.suffix == ".json" or "PASS" in text or "BUILD SUCCESSFUL" in text), src
    target = OUT / "验证记录" / name
    target.parent.mkdir(exist_ok=True)
    shutil.copy2(src, target)
for name in ["exterior-telemetry-server.log", "audio-fix-final-server.log", "audio-fix-final-client.log"]:
    assert "BUILD SUCCESSFUL" in (ROOT / "build" / name).read_text(encoding="utf-8", errors="replace"), name

preview = OUT / "游戏实时合成器_54秒试听.ogg"
subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-i", str(ROOT / "build/vvvf-exterior-runtime-preview.wav"),
                "-c:a", "libvorbis", "-q:a", "6", str(preview)], check=True)
audio, rate = sf.read(preview)
assert rate == 48000 and len(audio) == 48000 * 54 and max(abs(audio)) < 1
manifest = {jar.name: {"bytes": jar.stat().st_size, "sha256": hashlib.sha256(jar.read_bytes()).hexdigest()}}
(OUT / "SHA256.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
note = """全部通过：19项控制/DSP检查，实际Sable刚体350km/h线速度、角速度点速度、空转与锁轮独立遥测、codec2；真实客户端Create/CEE OGG后台解码、RMS/DC/循环边缘、机械20ms包络压缩、缺失/损坏素材回退和缓存换代；实际Sable PCM声音通道、100ms缓冲、非零混音、OpenAL 50/100/150ms补充与600ms断粮恢复、原滚动声交叉接管与恢复、十次350km/h合成位姿及单次坐标变换。
54秒试听来自客户端资源管理器解码的纹理与正式Java实时合成器，以压缩工况离线驱动，单声道点源输出；不是实驾录音。游戏空间定位由多个转向架声源产生。没有在用户完整列车上实驾或人工评定听感。
本轮实际OpenAL持续播放超过12.6秒，正常补充最少仍有2段可播放缓冲；注入600毫秒饥饿后同PCM/合成器恢复，主动停止后不复活。未在用户完整列车上实驾。
本轮只需客户端和服务端更新emucab-0.5.17.jar，P&W继续1.4.1。未修改用户PCL目录或存档。
"""
(OUT / "验证记录/验证范围.txt").write_text(note, encoding="utf-8")
archive = OUT.parent / (OUT.name + ".zip")
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
    for path in sorted(OUT.rglob("*")):
        if path.is_file():
            z.write(path, path.relative_to(OUT))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
print(json.dumps({"jar": str(OUT / jar.name), "archive": str(archive), "manifest": manifest}, ensure_ascii=True, indent=2))
