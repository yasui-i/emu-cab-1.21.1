"""Blender background comparison of source and reduced meshes using identical cameras."""
import bpy
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]
bpy.ops.wm.open_mainfile(filepath=str(ROOT/'model-source/reduced/jiashitai_reduced.blend'))
reduced=[o for o in bpy.context.scene.objects if o.type=='MESH']
scene=bpy.context.scene
bpy.ops.object.camera_add(location=(3.2,-4.7,3.3))
camera=bpy.context.object
camera.rotation_euler=(Vector((0,.25,.7))-camera.location).to_track_quat('-Z','Y').to_euler()
camera.data.type='ORTHO';camera.data.ortho_scale=3.7
scene.camera=camera
scene.render.engine='BLENDER_WORKBENCH'
scene.display.shading.light='STUDIO'
scene.display.shading.color_type='MATERIAL'
scene.display.shading.show_shadows=True
scene.display.shading.show_cavity=True
scene.render.resolution_x=1000;scene.render.resolution_y=750;scene.render.resolution_percentage=100
scene.render.filepath=str(ROOT/'model-source/reduced/preview_reduced.png')
bpy.ops.render.render(write_still=True)
for o in reduced:o.hide_render=True
bpy.ops.wm.obj_import(filepath=str(ROOT/'model-source/jiashitai.obj'),forward_axis='NEGATIVE_Z',up_axis='Y')
scene.render.filepath=str(ROOT/'model-source/reduced/preview_source.png')
bpy.ops.render.render(write_still=True)
