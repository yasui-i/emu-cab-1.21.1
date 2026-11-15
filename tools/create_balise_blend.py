"""Build the editable balise source and preview from the exact vanilla JSON mesh."""
from pathlib import Path
import json
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "model-source/balise"
spec = json.loads((SOURCE / "balise-model.json").read_text(encoding="utf-8"))
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

image = bpy.data.images.load(str(SOURCE / "textures/balise.png"))
material = bpy.data.materials.new("Balise shared pixel atlas")
material.use_nodes = True
principled = material.node_tree.nodes.get("Principled BSDF")
principled.inputs["Roughness"].default_value = .82
texture = material.node_tree.nodes.new("ShaderNodeTexImage")
texture.image = image
texture.interpolation = "Closest"
material.node_tree.links.new(texture.outputs["Color"], principled.inputs["Base Color"])

# Cuboid face coordinates use vanilla axes (X right, Y up, Z back).
for element in spec["elements"]:
    x0, y0, z0 = element["from"]
    x1, y1, z1 = element["to"]
    points = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
              (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    # Blender +Z is up; preserve handedness through (X, -Z, Y).
    vertices = [((x-8)/16, -(z-8)/16, y/16) for x, y, z in points]
    faces = [(1, 0, 3, 2), (4, 5, 6, 7), (5, 1, 2, 6), (0, 4, 7, 3), (3, 7, 6, 2), (0, 1, 5, 4)]
    names = ["north", "south", "east", "west", "up", "down"]
    mesh = bpy.data.meshes.new(element["name"])
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(element["name"], mesh)
    scene.collection.objects.link(obj)
    obj.data.materials.append(material)
    uv_layer = mesh.uv_layers.new(name="PixelAtlas")
    for polygon, name in zip(mesh.polygons, names):
        u0, v0, u1, v1 = element["faces"][name]["uv"]
        coords = [(u0/16, 1-v1/16), (u1/16, 1-v1/16), (u1/16, 1-v0/16), (u0/16, 1-v0/16)]
        if name == "up":
            coords = [(u0/16, 1-v0/16), (u0/16, 1-v1/16), (u1/16, 1-v1/16), (u1/16, 1-v0/16)]
        for index, coord in zip(polygon.loop_indices, coords):
            uv_layer.data[index].uv = coord

def matte(name, color):
    mat = bpy.data.materials.new(name)
    mat.diffuse_color = (*color, 1)
    mat.use_nodes = True
    shader = mat.node_tree.nodes.get("Principled BSDF")
    shader.inputs["Base Color"].default_value = (*color, 1)
    shader.inputs["Roughness"].default_value = .9
    return mat

floor_mat = matte("Preview ground", (.105, .125, .14))
bpy.ops.mesh.primitive_plane_add(size=200, location=(0, 0, -.006))
floor = bpy.context.object
floor.name = "PREVIEW ONLY ground"
floor.data.materials.append(floor_mat)
floor.hide_viewport = True

world = bpy.data.worlds.new("Neutral studio")
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (.20, .23, .26, 1)
world.node_tree.nodes["Background"].inputs[1].default_value = .65
scene.world = world
for name, position, power, size in [("Key", (-2, -3, 4), 360, 4), ("Fill", (3, 1, 2), 130, 3)]:
    light = bpy.data.lights.new(name, "AREA")
    light.energy = power
    light.shape = "DISK"
    light.size = size
    obj = bpy.data.objects.new(name, light)
    scene.collection.objects.link(obj)
    obj.location = position
    obj.rotation_euler = (Vector((0, 0, 0))-obj.location).to_track_quat("-Z", "Y").to_euler()

camera = bpy.data.cameras.new("Balise preview")
obj = bpy.data.objects.new("Balise preview", camera)
scene.collection.objects.link(obj)
obj.location = (1.25, -1.65, 1.55)
obj.rotation_euler = (Vector((0, 0, .03))-obj.location).to_track_quat("-Z", "Y").to_euler()
camera.type = "ORTHO"
camera.ortho_scale = 1.28
scene.camera = obj
scene.render.engine = "CYCLES"
scene.cycles.samples = 32
scene.render.resolution_x = 1200
scene.render.resolution_y = 800
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "PNG"
scene.view_settings.view_transform = "Standard"
scene.render.filepath = str(SOURCE / "balise-preview.png")
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == "VIEW_3D":
            area.spaces.active.shading.type = "MATERIAL"
            area.spaces.active.region_3d.view_distance = 1.6
            area.spaces.active.region_3d.view_location = (0, 0, .05)
            area.spaces.active.region_3d.view_rotation = obj.rotation_euler.to_quaternion()
image.filepath = "//textures/balise.png"
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE / "balise.blend"))
bpy.ops.render.render(write_still=True)
print("BALISE_SOURCE: 4 cuboids, 24 quads, 48 triangles; exact JSON mesh; saved editable blend and preview")
