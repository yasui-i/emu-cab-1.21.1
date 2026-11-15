"""Package the exact continuous-pass-by build with verified test evidence."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.5.24"
jar = ROOT / f"build/libs/emucab-{VERSION}.jar"
baseline = ROOT / "build/libs/emucab-0.5.23.jar"
out = ROOT / f"build/高速通过连续性-{VERSION}"
out.mkdir(parents=True, exist_ok=True)

with zipfile.ZipFile(jar) as current, zipfile.ZipFile(baseline) as previous:
    assert current.testzip() is None
    assert f'version="{VERSION}"' in current.read("META-INF/neoforge.mods.toml").decode()
    old_names, names = set(previous.namelist()), set(current.namelist())
    assert not old_names - names, sorted(old_names - names)
    unchanged_assets = [n for n in old_names if n.startswith("assets/") and n != "assets/emucab/sounds.json"]
    assert all(current.read(n) == previous.read(n) for n in unchanged_assets)
    for name in ["client/BogeyExteriorSounds", "client/BogeyExteriorBuffers", "mixin/BogeyExteriorBufferMixin", "mixin/BogeyLoopChannelAccessor"]:
        assert f"com/yasui_i/emucab/{name}.class" in names
    assert not any(n.startswith(("assets/create/", "assets/electroenergetics/", "com/george_vi/", "emucabmotortest/")) for n in names)
    comparison = {
        "baseline": baseline.name,
        "baseline_class_count": sum(n.endswith(".class") for n in old_names),
        "class_count": sum(n.endswith(".class") for n in names),
        "removed_entries": [],
        "previous_assets_identical_except_sound_registry": len(unchanged_assets),
        "added_entries": sorted(names - old_names),
    }

evidence = {
    "build/passby-continuous-final-build.log": ("构建及21组回归.log", "BUILD SUCCESSFUL"),
    "build/passby-continuous-client-final.log": ("客户端运行.log", "BUILD SUCCESSFUL"),
    "build/passby-continuous-server-final.log": ("服务端运行.log", "BUILD SUCCESSFUL"),
    "build/motor-client/motor-client-check.txt": ("客户端检查.txt", "PASS"),
    "build/motor-client/static-exterior-continuity.txt": ("OpenAL静态循环持续播放.txt", "PASS"),
    "build/motor-server/motor-server-check.txt": ("CEE供电及Sable传动回归.txt", "PASS"),
}
checks = out / "验证记录"
checks.mkdir(exist_ok=True)
for source, (name, token) in evidence.items():
    path = ROOT / source
    value = path.read_text(encoding="utf-8", errors="replace")
    assert token in value and "FAIL " not in value and "BUILD FAILED" not in value, source
    shutil.copy2(path, checks / name)
shutil.copy2(ROOT / "build/motor-client/continuous-bogey-passby.csv", checks / "350kmh声源交接.csv")
(checks / "包内容与上一版对比.json").write_text(json.dumps(comparison, ensure_ascii=False, indent=2), encoding="utf-8")

note = """EMU Cab 0.5.24 — 近距离高速通过声音连续性

客户端和服务端均用本包替换旧 emucab jar，不要同时保留多个 emucab 版本。P&W 附属无需随本次更新。

本轮修复：
1. 风噪、滚动声改为各已登记转向架持续循环，独立于“最近三个”VVVF声源选择；高速交接不再重启这两层背景声。
2. 两份8秒静态PCM由各转向架共享，播放相位错开；无需逐块补充背景声缓冲，降低帧停顿造成断声的风险。
3. 停车保持静音循环，重新行驶继续播放；附近停车源不再压低通过列车的音量。名额不足时已静音的停车源可以让位。
4. 静态池分配失败后可重试；过期遥测、卸载与资源重载仍会清理旧声音。

范围继续支持64–512格，默认256格；保留原音量和混音配置。
客户端 config/emucab-bogey-audio.toml 的 bogey_motor.radius 控制范围。
maximum_sources 只限制VVVF实时合成器（最多3），风噪/滚动声支持附近最多32个已登记转向架。声音仍随距离衰减。
未恢复原Simurail撞轨录音。驾驶台、电气、线缆、模型及其他已有功能由当前源码延续；对比0.5.23无删除条目，既有贴图、模型及音源资源逐字节一致（sounds.json追加两个事件）。

验证：
21组控制、配置、显示、调速器及合成检查通过；隔离真实客户端/服务端检查通过。
6个转向架、12个静态背景声实例在40步350km/h模拟位姿中保持身份，覆盖3组最近电机声源选择；淡入后无聚合静音，单步增益最大变化0.068dB，停车再开及过期清理通过。
4个真实OpenAL静态源共享2份生成缓冲，持续9.29秒、73次检查，全部跨8秒循环边界；音源与缓冲未重建，始终处于looping/playing，包含约907ms无补流间隔。
静态循环底层测试使用正式缓存缓冲和独立OpenAL探针；高速位置测试是合成位姿，不是用户完整列车实驾录音。本轮未重新运行旧VVVF流式补流计时测试，也不以对象存活断言实车听感已完全恢复。
未修改PCL安装、用户存档或P&W项目。
"""
(out / "说明.txt").write_text(note, encoding="utf-8")
shutil.copy2(jar, out / jar.name)
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
manifest = {jar.name: {"bytes": jar.stat().st_size, "sha256": digest}}
(out / "SHA256.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
archive = out.parent / (out.name + ".zip")
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
    for path in sorted(out.rglob("*")):
        if path.is_file():
            z.write(path, path.relative_to(out))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert hashlib.sha256(z.read(jar.name)).hexdigest() == digest
assert hashlib.sha256((out / jar.name).read_bytes()).hexdigest() == digest
print(json.dumps({"jar": str(out / jar.name), "zip": str(archive), "manifest": manifest, "comparison": comparison}, ensure_ascii=False, indent=2))
