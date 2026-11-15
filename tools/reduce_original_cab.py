"""Run with Blender --background --python. Keeps source component transforms/layout.
Only weld duplicate vertices and dissolve coplanar edges; optional small-angle
decimation is bounded by measured geometry error. Never voxelizes or rebuilds parts.
"""
import bpy
import bmesh
import json
import math
import shutil
from pathlib import Path
from mathutils.bvhtree import BVHTree

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'model-source/reduced'
if (OUT / 'jiashitai_reduced.blend').exists():
    raise SystemExit('Refusing to overwrite the editable blend. Use tools/export_edited_cab.py to export your saved edits.')
OUT.mkdir(parents=True, exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.ops.wm.obj_import(filepath=str(ROOT/'model-source/jiashitai.obj'), forward_axis='NEGATIVE_Z', up_axis='Y')
objects = sorted((o for o in bpy.context.scene.objects if o.type=='MESH'),key=lambda o:o.name)
atlas=bpy.data.images.load(str(ROOT/'model-source/cr400af_atlas.png'),check_existing=True)
for mat in bpy.data.materials:
    if mat.node_tree:
        for node in mat.node_tree.nodes:
            if node.type=='TEX_IMAGE': node.image=atlas
stats=[]
for obj in objects:
    mesh=obj.data
    before=len(mesh.polygons)
    mesh.calc_loop_triangles(); before_triangles=len(mesh.loop_triangles)
    bm=bmesh.new();bm.from_mesh(mesh)
    # Strictly planar cleanup first. Material/UV boundaries are not crossed.
    bmesh.ops.remove_doubles(bm, verts=list(bm.verts), dist=0.000001)
    bmesh.ops.dissolve_limit(bm, angle_limit=math.radians(.1), use_dissolve_boundaries=False,
                            verts=list(bm.verts),edges=list(bm.edges),delimit={'MATERIAL','UV'})
    bm.to_mesh(mesh);bm.free();mesh.update()
    original=mesh.copy()
    def surface(m):
        return BVHTree.FromPolygons([v.co for v in m.vertices], [list(p.vertices) for p in m.polygons],all_triangles=False)
    ref=surface(original)
    error=0
    if before>10:
        bpy.context.view_layer.objects.active=obj
        mod=obj.modifiers.new('Measured reduction','DECIMATE');mod.ratio=.55
        candidate=bpy.data.meshes.new_from_object(obj.evaluated_get(bpy.context.evaluated_depsgraph_get()))
        obj.modifiers.remove(mod)
        tree=surface(candidate)
        # Bidirectional vertex and face-centre distance, plus per-axis extent checks.
        distances=[]
        for m,bvh in [(candidate,ref),(original,tree)]:
            points=[v.co for v in m.vertices]+[p.center for p in m.polygons]
            distances.extend(bvh.find_nearest(v)[3] for v in points)
        error=max(distances,default=0)
        extent_error=max(abs(fn(v.co[a] for v in candidate.vertices)-fn(v.co[a] for v in original.vertices)) for a in range(3) for fn in [min,max])
        if error<=.002 and extent_error<=.001:
            obj.data=candidate;mesh=candidate
        else:
            bpy.data.meshes.remove(candidate);error=0
    mesh.calc_loop_triangles()
    stats.append(dict(name=obj.name,before=before,after=len(mesh.polygons),before_triangles=before_triangles,
                      after_triangles=len(mesh.loop_triangles),sampled_surface_error=error))
print('REDUCTION',sum(x['before'] for x in stats),sum(x['after'] for x in stats),flush=True)
(OUT/'reduction_stats.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
bpy.ops.wm.obj_export(filepath=str(OUT/'jiashitai_reduced.obj'),forward_axis='NEGATIVE_Z',up_axis='Y',export_materials=True)
atlas.pack()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'jiashitai_reduced.blend'))

# Preserve original UVs and local shape, use only a uniform scale/axis conversion for MC.
def mc(v):
    # Blender import maps OBJ (x,y,z) -> Blender (x,-z,y).
    return [v.x*1.05+.5, v.z*1.05, v.y*1.05+.5]

geometry=[]
for obj in objects:
    mesh=obj.data;mesh.calc_loop_triangles()
    uv=mesh.uv_layers.active
    triangles=[]
    for tri in mesh.loop_triangles:
        mat=obj.material_slots[tri.material_index].material if obj.material_slots else None
        color=[1,1,1]
        texture='atlas'
        if mat and 'first_version_grey-black' in mat.name: texture='shell';color=[.055,.07,.085]
        if mat and 'screen_off' in mat.name: texture='screen';color=[.006,.008,.010]
        points=[]
        for vi,li in zip(tri.vertices,tri.loops):
            p=mc(obj.matrix_world@mesh.vertices[vi].co)
            tex=list(uv.data[li].uv) if uv else [0,0]
            points.append([round(x,7) for x in p]+[round(tex[0],7),round(1-tex[1],7)])
        triangles.append(dict(p=points,texture=texture,color=color))
    geometry.append(dict(name=obj.name,triangles=triangles))
asset=ROOT/'src/main/resources/assets/emucab/meshes/cab_mesh.json'
asset.parent.mkdir(parents=True,exist_ok=True)
asset.write_text(json.dumps(geometry,separators=(',',':')),encoding='utf-8')
shutil.copyfile(ROOT/'model-source/cr400af_atlas.png',ROOT/'src/main/resources/assets/emucab/textures/block/cab_atlas.png')
