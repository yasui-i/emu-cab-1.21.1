"""Check pixel cab resources and optionally their packaged JAR contents."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/emucab'
OUT = ROOT / 'model-source/pixel-integration-20261101'

def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))

def sha(data):
    return hashlib.sha256(data).hexdigest()

export = read_json(OUT / 'export-report.json')
integration = read_json(OUT / 'integration-report.json')
source = Path(export['source'])
assert sha(source.read_bytes()) == export['source_sha256'], 'Blend changed since export'
meshes = read_json(ASSETS / 'meshes/cab_mesh.json')
assert len(meshes) == export['mesh_objects'] == 426
assert sum(len(m['triangles']) for m in meshes) == integration['source_triangles'] == 5966
assert len({m['name'] for m in meshes}) == len(meshes)
assert not any('_|_socket' in m['name'] for m in meshes), 'Pushbutton black base remains'
controls = read_json(ROOT / 'model-source/original-controls.json')
numbers = read_json(ROOT / 'model-source/button-numbers.json')
assignments = read_json(ROOT / 'model-source/control-functions.json')
assert len(controls) == len({c['number'] for c in controls}) == 172
for control in controls:
    assert numbers[control['source']] == control['number']
    assert control['action'] == assignments.get(control['number'], {}).get('action', 'UNASSIGNED')
old = read_json(ROOT / 'backups/pixel-integration-20261101/original-hashes.json')
for path in ['model-source/button-numbers.json', 'model-source/control-functions.json']:
    assert sha((ROOT / path).read_bytes()) == old[path], path
concrete = ASSETS / 'textures/block/cab_pixel/gray_concrete.png'
original = Path('D:/杂物/gray_concrete.png')
assert concrete.read_bytes() == original.read_bytes(), 'Original concrete PNG was altered'
report = dict(status='PASS', source=str(source), source_sha256=export['source_sha256'],
              mesh_objects=len(meshes), source_triangles=integration['source_triangles'], controls=172,
              number_and_function_files_unchanged=True, gray_concrete_sha256=sha(concrete.read_bytes()), models={})
for path in sorted((ASSETS / 'models').glob('cab_*.obj')):
    lines = path.read_text().splitlines()
    uv = [list(map(float, line.split()[1:])) for line in lines if line.startswith('vt ')]
    assert all(0 <= v <= 1 for pair in uv for v in pair), path
    count = sum(line.startswith('f ') for line in lines)
    expected = integration['baked_item_triangles'] if path.stem == 'cab_item' else integration['static_slice_triangles'][path.stem.removeprefix('cab_static_')]
    assert count == expected, path
    report['models'][path.name] = dict(triangles=count, uv_in_0_to_1=True)
textures = re.findall(r'^map_Kd emucab:(.+)$', (ASSETS / 'models/cab_item.mtl').read_text(), re.M)
for texture in textures:
    assert (ASSETS / 'textures' / f'{texture}.png').is_file(), texture
report['resolved_materials'] = textures
dynamic = read_json(ASSETS / 'meshes/cab_dynamic.json')
lcds = [m for m in dynamic if m['name'].endswith('_|_LCD')]
assert len(lcds) == 5 and all(len(m['triangles']) == 2 for m in lcds)
report['dynamic_triangles'] = sum(len(m['triangles']) for m in dynamic)
motions = read_json(ASSETS / 'meshes/cab_animations.json')
assert len(motions) == 24
assert len({motion['id'] for motion in motions}) == 24
parts = {part for motion in motions for part in motion['parts']}
assert len(parts) == sum(len(motion['parts']) for motion in motions)
dynamic_names = {mesh['name'] for mesh in dynamic}
assert parts <= dynamic_names
for name in parts:
    assert not any(token in name for token in ['chrome_ring', 'socket', 'mounting_ring', '_|_boot', '_|_collar']), name
report['animated_controls'] = len(motions)
report['animated_parts'] = len(parts)
assert report['dynamic_triangles'] == integration['dynamic_triangles']
if len(sys.argv) > 1:
    jar = Path(sys.argv[1])
    with zipfile.ZipFile(jar) as z:
        for file in ASSETS.rglob('*'):
            if file.is_file():
                entry = file.relative_to(ROOT / 'src/main/resources').as_posix()
                assert z.read(entry) == file.read_bytes(), entry
        assert not any(name.startswith('emucabtest/') or name.startswith('emucabpixeltest/') for name in z.namelist()), 'Test harness leaked into release'
    report['jar'] = dict(path=str(jar.resolve()), sha256=sha(jar.read_bytes()), resources_match=True)
(OUT / 'resource-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS: no-base pixel cab; source and export match; unchanged 172 numbers/functions; exact concrete PNG; valid UV/materials; five LCDs' + ('; JAR resources match' if len(sys.argv) > 1 else ''))
