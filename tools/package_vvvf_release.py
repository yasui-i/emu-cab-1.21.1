"""Package only the two add-ons, local previews and bounded verification evidence."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
pw = root.parent / "pw-sable-fix-1.21.1"
out = root / "build/VVVF混音与电机迁移-20261104"
out.mkdir(parents=True, exist_ok=True)
cab_jar = root / "build/libs/emucab-0.5.15.jar"
pw_jar = pw / "build/libs/pwsablefix-1.4.1.jar"

with zipfile.ZipFile(cab_jar) as archive:
    names = set(archive.namelist())
    for name in ["com/yasui_i/emucab/audio/VvvfSynthesizer.class",
                 "com/yasui_i/emucab/client/BogeyMotorSounds.class",
                 "com/yasui_i/emucab/telemetry/BogeyMotorTelemetry.class",
                 "com/yasui_i/emucab/motor/VvvfMotorBlockEntity.class",
                 "assets/emucab/sounds/vvvf_stream.ogg",
                 "assets/emucab/blockstates/vvvf_motor.json",
                 "assets/emucab/models/item/vvvf_motor.json"]:
        assert name in names, name
    assert not any(n.startswith(("com/george_vi/", "assets/electroenergetics/", "assets/pantographsandwires/")) for n in names)
    meta = archive.read("META-INF/neoforge.mods.toml").decode()
    assert 'version="0.5.15"' in meta
with zipfile.ZipFile(pw_jar) as archive:
    names = set(archive.namelist())
    assert "com/yasui_i/pwsablefix/client/PantographContactSounds.class" in names
    assert not any("/motor/" in n or "vvvf_motor" in n for n in names)
    assert not any(n.startswith(("com/george_vi/", "assets/electroenergetics/", "assets/pantographsandwires/")) for n in names)
    assert 'version="1.4.1"' in archive.read("META-INF/neoforge.mods.toml").decode()

files = [(cab_jar, out / cab_jar.name), (pw_jar, out / pw_jar.name),
         (root / "model-source/vvvf-audio-20261104/使用说明.md", out / "使用说明.md"),
         (root / "model-source/vvvf-audio-20261104/VVVF_牵引滑行制动_24秒.ogg", out / "试听/VVVF_牵引滑行制动_24秒.ogg"),
         (root / "model-source/audio-options-20261104/已选_触网短电弧_0.35秒.ogg", out / "试听/触网短电弧_0.35秒.ogg"),
         (root / "model-source/audio-options-20261104/已选_脱网长电弧_1.20秒.ogg", out / "试听/脱网长电弧_1.20秒.ogg"),
         (root / "build/display-number-layout/网压网流位置对照.png", out / "网压网流位置对照.png"),
         (root / "model-source/vvvf-audio-20261104/设计与来源.json", out / "设计与来源.json")]
evidence = [
    (root / "build/vvvf-final-build.log", "驾驶台构建与19项检查.log"),
    (pw / "build/long-arc-build.log", "P&W构建与触网状态检查.log"),
    (root / "build/telemetry-server/telemetry-check.txt", "转向架实际遥测检查.txt"),
    (root / "build/motor-server/motor-server-check.txt", "电机供电与迁移检查.txt"),
    (root / "build/motor-client/motor-client-check.txt", "实时PCM与电机客户端检查.txt"),
    (root / "build/electrical-server/electrical-check-result.txt", "接触网真实供电与声音边沿检查.txt"),
    (root / "build/electrical-client/electrical-client-check.txt", "触脱网音色时长与随车客户端检查.txt"),
    (root / "build/vvvf-dsp-check/合成检查.txt", "DSP检查.txt"),
]
for source, filename in evidence:
    data = source.read_text(encoding="utf-8", errors="replace")
    assert "FAIL" not in data and ("PASS" in data or "BUILD SUCCESSFUL" in data), source
    files.append((source, out / "验证记录" / filename))
for log in [root / "build/long-arc-fresh-server.log", root / "build/long-arc-fresh-client.log"]:
    assert "BUILD SUCCESSFUL" in log.read_text(encoding="utf-8", errors="replace"), log

for source, target in files:
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
note = """验证通过：驾驶台/P&W构建、19项控制与DSP检查、真实轮面RPM及力遥测、5秒制动缓解冷却、CEE真实供电与轴输出、旧电机四类ID和NBT迁移、Sable装配、客户端实时PCM和原滚动声替代/恢复。
音色最终选择②CEE长电弧，触网7tick(0.35秒)、脱网24tick(1.2秒)，淡出2/3tick。实际客户端验证了两类包的codec、CEE音源解析、时长/淡出、350km/h合成位姿与单次坐标变换。真实服务端验证连续触网不重复、开VCB不伪造脱网、实际降弓或离网触发。
新电气验证使用隔离全新世界world-long-arc-20261104-212122。首次复用旧测试世界遇P&W历史残留图节点，未作为通过证据；改用全新隔离世界后全部通过。未修改用户正式安装或存档。
测试包含已知输入和合成高速位姿，不代表已完成用户完整列车实驾或人工听感标定。试听目录仅供本地试听，CEE录音未打入两个jar。
"""
(out / "验证记录/验证范围.txt").write_text(note, encoding="utf-8")
manifest = {p.name: {"bytes": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in [out/cab_jar.name, out/pw_jar.name]}
(out / "SHA256.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
archive_path = out.with_suffix(".zip")
with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(out.rglob("*")):
        if path.is_file():
            archive.write(path, path.relative_to(out))
print(json.dumps({"folder":str(out),"zip":str(archive_path),"manifest":manifest}, ensure_ascii=False, indent=2))
