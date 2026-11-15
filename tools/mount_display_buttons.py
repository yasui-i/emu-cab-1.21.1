"""Mount screen keys on their actual bezels; preserve tangential placement and identity."""
import bpy,json,shutil,hashlib,sys
from pathlib import Path
from datetime import datetime
from mathutils import Vector
from mathutils.bvhtree import BVHTree

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'model-source/reduced/jiashitai_edit.blend'
INPUT=Path(sys.argv[sys.argv.index('--source')+1]) if '--source' in sys.argv else SOURCE
OUT=ROOT/'model-source/button-mount';OUT.mkdir(exist_ok=True)
BACKUP=ROOT/'backups/display-button-mount'/datetime.now().strftime('%Y%m%d-%H%M%S')
BACKUP.mkdir(parents=True,exist_ok=False)
shutil.copy2(SOURCE,BACKUP/SOURCE.name)
bpy.ops.wm.open_mainfile(filepath=str(INPUT))
def restore_atlas():
    atlas=bpy.data.images.get('cr400af_atlas.png')
    if atlas and not atlas.packed_file:
        atlas.filepath=str(SOURCE.parent/'textures/cr400af_atlas.png');atlas.reload()
restore_atlas()

def points(o):return [o.matrix_world@v.co for v in o.data.vertices]
def center(o):
    ps=points(o);return sum(ps,Vector())/len(ps)
def signature(o):
    h=hashlib.sha256()
    for v in o.data.vertices:h.update(str(tuple(v.co)).encode())
    for p in o.data.polygons:h.update(str((tuple(p.vertices),p.material_index)).encode())
    for uv in o.data.uv_layers:
        for p in uv.data:h.update(str(tuple(p.uv)).encode())
    return h.hexdigest()

original={o.name:(signature(o),o.matrix_world.copy()) for o in bpy.context.scene.objects if o.type=='MESH'}

def preview(path):
    scene=bpy.context.scene
    bpy.ops.object.camera_add(location=(1.1,-2.0,1.75))
    cam=bpy.context.object;cam.rotation_euler=(Vector((0,.5,1.1))-cam.location).to_track_quat('-Z','Y').to_euler()
    cam.data.type='ORTHO';cam.data.ortho_scale=2.65;scene.camera=cam
    scene.render.engine='BLENDER_WORKBENCH';scene.display.shading.color_type='TEXTURE'
    scene.display.shading.light='STUDIO';scene.display.shading.show_shadows=True
    scene.display.shading.show_cavity=True;scene.display.shading.show_object_outline=True
    scene.render.resolution_x=1800;scene.render.resolution_y=850;scene.render.resolution_percentage=100
    scene.render.filepath=str(path);bpy.ops.render.render(write_still=True)

preview(OUT/'before.png')
bpy.ops.wm.open_mainfile(filepath=str(INPUT))
restore_atlas()
report=[];moved=set()
for index in range(1,6):
    lcd=next(o for o in bpy.context.scene.objects if o.name.startswith(f'{index:02d}_') and o.name.endswith('_|_LCD'))
    prefix=lcd.name.split('_|_')[0]+'_|_'
    bezel=bpy.data.objects[prefix+'recessed_bezel']
    ps=points(lcd);lcd.data.calc_loop_triangles();tri=lcd.data.loop_triangles[0]
    a,b,c=[ps[i] for i in tri.vertices];normal=(b-a).cross(c-a).normalized()
    if normal.y>0:normal.negate()
    supports=[bezel,bpy.data.objects[prefix+'inner_frame']]
    trees=[BVHTree.FromPolygons(points(o),[list(p.vertices) for p in o.data.polygons]) for o in supports]
    keys=[o for o in bpy.context.scene.objects if o.type=='MESH' and o.name.startswith(prefix)
          and ('lower_softkey_' in o.name or ('side_key_' in o.name and 'inset' not in o.name)
               or ('keypad_' in o.name and 'marking' not in o.name))]
    centers={o.name:center(o) for o in keys}
    decorations=[o for o in bpy.context.scene.objects if o.type=='MESH' and o.name.startswith(prefix)
                 and any(t in o.name for t in ['softkey_centre','side_key_inset','keypad_marking'])]
    groups={o.name:[] for o in keys}
    for deco in decorations:
        kind='keypad_' if 'keypad_marking' in deco.name else 'lower_softkey_' if 'softkey_centre' in deco.name else 'side_key_'
        candidates=[o for o in keys if kind in o.name]
        closest=min(candidates,key=lambda o:(centers[o.name]-center(deco)).length_squared)
        groups[closest.name].append(deco)
    for key in keys:
        p=centers[key.name]
        hits=[tree.ray_cast(p+normal*.5,-normal,1)[0] for tree in trees]
        hits=[hit for hit in hits if hit is not None]
        if not hits:raise RuntimeError('No bezel beneath '+key.name)
        back=min(v.dot(normal) for v in points(key));support=max(hit.dot(normal) for hit in hits)
        shift=normal*(support-.0005-back)
        # One rigid translation per button and all of its markings.
        for o in [key]+groups[key.name]:
            matrix=o.matrix_world.copy();matrix.translation+=shift;o.matrix_world=matrix;moved.add(o.name)
        bpy.context.view_layer.update()
        mounted=min(v.dot(normal) for v in points(key))-support
        assert abs(mounted+.0005)<2e-6,(key.name,mounted)
        assert shift.cross(normal).length<1e-7
        report.append(dict(source=key.name,panel=index,shift=list(shift),old_gap=back-support,
                           new_gap=mounted,decorations=[o.name for o in groups[key.name]]))

for o in bpy.context.scene.objects:
    if o.type!='MESH':continue
    sig,matrix=original[o.name]
    assert signature(o)==sig,'Mesh/UV changed: '+o.name
    if o.name not in moved:assert o.matrix_world==matrix,'Unrelated object moved: '+o.name
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE))
(OUT/'mount-report.json').write_text(json.dumps(dict(backup=str(BACKUP),buttons=report),ensure_ascii=False,indent=2),encoding='utf-8')
preview(OUT/'after.png')
print('MOUNTED',len(report),'buttons,',len(moved),'objects; geometry, UV and unrelated transforms unchanged; backup',BACKUP)
