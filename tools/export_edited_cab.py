"""Blender --background --python tools/export_edited_cab.py

Export the user's saved editable blend without modifying it or reducing it again.
One proper rotation (determinant +1) converts Blender to Minecraft coordinates.
"""
import bpy
import json
import sys
import re
import hashlib
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
EDIT = ROOT / 'model-source/reduced/jiashitai_edit.blend'
PIXEL=ROOT/'model-source/pixel-integration-20261101/驾驶台_A方案_无底座整合版.blend'
PIXEL_WITH_BASE=ROOT/'model-source/pixel-integration-20261101/驾驶台_A方案_整合版.blend'
args=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
SOURCE = Path(args[0]) if args else next(p for p in [PIXEL,PIXEL_WITH_BASE,EDIT,ROOT/'model-source/reduced/jiashitai_reduced.blend'] if p.exists())
ASSETS = ROOT / 'src/main/resources/assets/emucab'
bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
bpy.context.view_layer.update()
depsgraph = bpy.context.evaluated_depsgraph_get()


def mc(v):
    # Driver at MC -Z sees source left on screen left; preserve winding.
    # The old (x,z,y) mapping was a reflection, reversing all face normals.
    return [-v.x * 1.05 + .5, v.z * 1.05, v.y * 1.05 + .5]


geometry = []
exported_images={}
def texture_key(image):
    if image.name.startswith('cr400af_atlas'):return 'atlas'
    key='pixel_'+re.sub(r'[^a-z0-9_]', '_',Path(image.filepath).stem.lower())
    exported_images[key]=image
    return key
for obj in sorted(bpy.context.scene.objects, key=lambda o: o.name):
    if obj.type != 'MESH' or obj.hide_render:
        continue
    evaluated = obj.evaluated_get(depsgraph)
    mesh = evaluated.to_mesh()
    mesh.calc_loop_triangles()
    uv = mesh.uv_layers.active
    triangles = []
    for tri in mesh.loop_triangles:
        mat = mesh.materials[tri.material_index] if mesh.materials else None
        texture, color = 'atlas', [1, 1, 1]
        if mat and mat.node_tree:
            bsdf=next((n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED'),None)
            if bsdf:
                base=bsdf.inputs['Base Color']
                if base.is_linked:
                    node=base.links[0].from_node
                    if node.type!='TEX_IMAGE' or not node.image:raise ValueError('Unsupported colour node: '+mat.name)
                    texture=texture_key(node.image)
                else:
                    # Shader colours are linear; Minecraft vertex colours are sRGB.
                    def srgb(v):return 12.92*v if v<=.0031308 else 1.055*v**(1/2.4)-.055
                    texture='screen' if mat.name.endswith('screen_off') else 'shell'
                    color=[round(srgb(max(0,min(1,float(v)))),6) for v in base.default_value[:3]]
        points = []
        for vi, li in zip(tri.vertices, tri.loops):
            p = mc(obj.matrix_world @ mesh.vertices[vi].co)
            tex = list(uv.data[li].uv) if uv else [0, 0]
            points.append([round(x, 7) for x in p] + [round(tex[0], 7), round(1-tex[1], 7)])
        # Account for any mirror transforms in the user's file as well.
        if obj.matrix_world.determinant() < 0:
            points.reverse()
        triangles.append(dict(p=points, texture=texture, color=color))
    geometry.append(dict(name=obj.get('source_name',obj.name), triangles=triangles))
    evaluated.to_mesh_clear()

# Animation assemblies are exported from the same saved scene as the mesh.
# Only moving hats/selector grips and attached face markings are separated;
# silver rims, collars, mounting rings and rubber boots stay in chunk buffers.
scene_objects={obj.get('source_name',obj.name):obj for obj in bpy.context.scene.objects if obj.type=='MESH' and not obj.hide_render}
number_map=json.loads((ROOT/'model-source/button-numbers.json').read_text(encoding='utf-8'))
previous=json.loads((ROOT/'model-source/original-controls.json').read_text(encoding='utf-8'))
control_ids={control['source']:control['id'] for control in previous}
assignments=json.loads((ROOT/'model-source/control-functions.json').read_text(encoding='utf-8'))
active_numbers={number for number,assignment in assignments.items() if assignment.get('action') not in {'UNASSIGNED','HANDLE',None}}
geometry_by_name={mesh['name']:mesh for mesh in geometry}

def source_points(obj):return [obj.matrix_world@vertex.co for vertex in obj.data.vertices]
def source_center(obj):
    points=source_points(obj)
    return Vector([(min(p[axis] for p in points)+max(p[axis] for p in points))/2 for axis in range(3)])
def mc_direction(v):return Vector((-v.x,v.z,v.y)).normalized()
def lcd_normal(prefix):
    lcd=next(mesh for mesh in geometry if mesh['name'].startswith(prefix) and mesh['name'].endswith('_|_LCD'))
    a,b,c=[Vector(p[:3]) for p in lcd['triangles'][0]['p']]
    return (b-a).cross(c-a).normalized()
def mounting_axis(name,obj):
    if name[:2].isdigit():return lcd_normal(name[:2])
    if number_map[name] in {'B042','B172'}:return lcd_normal('05')
    if 'red_mushroom' in name or name=='ATP_acknowledgement_button':
        nearest=min((key for key in ['01','02','03','04','05']),key=lambda key:(source_center(scene_objects[next(n for n in scene_objects if n.startswith(key) and n.endswith('_|_LCD'))])-source_center(obj)).length)
        return lcd_normal(nearest)
    if name.startswith('Underdesk_telephone'):
        # The broad face is the thin key's installation plane. Its player-facing
        # normal points towards Blender -Y; object origins are donor coordinates.
        face=max(obj.data.polygons,key=lambda polygon:polygon.area)
        normal=(obj.matrix_world.to_3x3().inverted().transposed()@face.normal).normalized()
        if normal.y>0:normal=-normal
        return mc_direction(normal)
    return Vector((0,1,0))

animations=[]
for name,number in sorted(number_map.items(),key=lambda pair:int(pair[1][1:])):
    if number not in active_numbers or name not in scene_objects or name.startswith('Traction_controller_|_'):continue
    obj=scene_objects[name]
    kind='rotary' if '_|_selector' in name or number=='B172' else 'press'
    axis=mounting_axis(name,obj)
    points=[Vector(p[:3]) for triangle in geometry_by_name[name]['triangles'] for p in triangle['p']]
    center=Vector(mc(source_center(obj)))
    # Rotation around the mounting normal does not depend on pivot depth.
    # A centred pivot avoids orbital motion even for tilted monitor controls.
    thickness=max(p.dot(axis) for p in points)-min(p.dot(axis) for p in points)
    travel=min(.0042,max(.0006,thickness*.35)) if kind=='press' else 0
    if name not in control_ids:raise ValueError('Missing stable control ID: '+name)
    animations.append(dict(id=control_ids[name],number=number,kind=kind,pivot=[round(v,8) for v in center],axis=[round(v,8) for v in axis],travel=round(travel,8),parts=[name]))

# Move each key's own inset/mark with its cap. Names of copied donor parts do
# not encode their matching key, so use nearest centre within the same monitor.
for name,obj in scene_objects.items():
    if not any(token in name for token in ['_|_keypad_marking','_|_softkey_centre','_|_side_key_inset']):continue
    prefix=name.split('_|_')[0]
    token='_|_keypad_' if 'keypad_marking' in name else '_|_lower_softkey_' if 'softkey_centre' in name else '_|_side_key_'
    candidates=[motion for motion in animations if motion['parts'][0].startswith(prefix+token)]
    if not candidates:continue # Unassigned softkeys and their markings remain baked together.
    closest=min(candidates,key=lambda motion:(source_center(scene_objects[motion['parts'][0]])-source_center(obj)).length)
    distance=(source_center(scene_objects[closest['parts'][0]])-source_center(obj)).length
    if distance>.025:raise ValueError('Detached key marking too far from cap: '+name)
    closest['parts'].append(name)

animated_parts=[name for motion in animations for name in motion['parts']]
assert len(animated_parts)==len(set(animated_parts)),'A mesh belongs to two animation assemblies'
assert {motion['number'] for motion in animations}==active_numbers,'An assigned control lacks animation geometry'
(ASSETS/'meshes').mkdir(parents=True,exist_ok=True)
(ASSETS/'meshes/cab_animations.json').write_text(json.dumps(animations,separators=(',',':')),encoding='utf-8')

assert len({m['name'] for m in geometry})==len(geometry),'Duplicate source names in active scene'
asset = ASSETS / 'meshes/cab_mesh.json'
asset.parent.mkdir(parents=True, exist_ok=True)
asset.write_text(json.dumps(geometry, separators=(',', ':')), encoding='utf-8')
atlas = next(i for i in bpy.data.images if i.name == 'cr400af_atlas.png')
def exact_image_bytes(image):
    tuple(image.size)
    return image.packed_file.data if image.packed_file else Path(bpy.path.abspath(image.filepath)).read_bytes()
(ASSETS/'textures/block/cab_atlas.png').write_bytes(exact_image_bytes(atlas))
texture_dir=ASSETS/'textures/block/cab_pixel';texture_dir.mkdir(exist_ok=True)
for key,image in exported_images.items():
    (texture_dir/(key.removeprefix('pixel_')+'.png')).write_bytes(exact_image_bytes(image))
report=dict(source=str(SOURCE),source_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),scene=bpy.context.scene.name,mesh_objects=len(geometry),triangles=sum(len(m['triangles']) for m in geometry),textures={key:list(image.size) for key,image in exported_images.items()},animated_controls=len(animations),rotary_controls=sum(motion['kind']=='rotary' for motion in animations),press_controls=sum(motion['kind']=='press' for motion in animations),animated_parts=len(animated_parts))
(ROOT/'model-source/pixel-integration-20261101/export-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('EXPORTED EDITED BLEND:', len(geometry), 'objects,', sum(len(m['triangles']) for m in geometry), 'triangles')
