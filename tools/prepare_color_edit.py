"""Create a textured editing copy without altering the user's source geometry."""
import bpy
import hashlib
import struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'model-source/reduced'
SOURCE=FOLDER/'jiashitai_reduced.blend'
TARGET=FOLDER/'jiashitai_edit.blend'
TEXTURE=FOLDER/'textures/cr400af_atlas.png'
if TARGET.exists():raise SystemExit('Editing copy exists; refusing to overwrite it.')
bpy.ops.wm.open_mainfile(filepath=str(SOURCE))

def geometry_hash():
    h=hashlib.sha256()
    for o in sorted(bpy.context.scene.objects,key=lambda o:o.name):
        if o.type!='MESH':continue
        h.update(o.name.encode());h.update(bytes(str(o.matrix_world),'utf-8'))
        for v in o.data.vertices:h.update(struct.pack('3f',*v.co))
        for p in o.data.polygons:h.update(str(tuple(p.vertices)).encode())
        if o.data.uv_layers.active:
            for v in o.data.uv_layers.active.data:h.update(struct.pack('2f',*v.uv))
    return h.hexdigest()

before=geometry_hash()
atlas=bpy.data.images.get('cr400af_atlas.png')
TEXTURE.parent.mkdir(parents=True,exist_ok=True)
if TEXTURE.exists():raise SystemExit('External texture exists; refusing to overwrite it.')
TEXTURE.write_bytes(bytes(atlas.packed_file.data))
if atlas.packed_file:atlas.unpack(method='REMOVE')
atlas.filepath='//textures/cr400af_atlas.png'
for m in bpy.data.materials:
    if not m.node_tree:continue
    images=[n for n in m.node_tree.nodes if n.type=='TEX_IMAGE' and n.image==atlas]
    if images:
        for n in m.node_tree.nodes:n.select=False
        images[0].select=True;m.node_tree.nodes.active=images[0]
        for n in images:n.interpolation='Closest'
for screen in bpy.data.screens:
    for area in screen.areas:
        for space in area.spaces:
            if space.type=='VIEW_3D':
                space.shading.type='MATERIAL'
                space.shading.use_scene_world=False
                space.shading.use_scene_lights=False
            elif space.type=='IMAGE_EDITOR':space.image=atlas
assert before==geometry_hash(),'Geometry changed'
bpy.ops.wm.save_as_mainfile(filepath=str(TARGET))
assert before==geometry_hash()
print('COLOR EDIT COPY',TARGET,'geometry/UV hash',before)
