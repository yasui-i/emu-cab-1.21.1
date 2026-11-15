"""Run in live Blender after saving a copy; only rigidly translate existing screws."""
import bpy,json
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree

def pts(o):return [o.matrix_world@v.co for v in o.data.vertices]
def center(o):
    ps=pts(o);return sum(ps,Vector())/len(ps)
before={o.name:(o.matrix_world.copy(),[tuple(v.co) for v in o.data.vertices]) for o in bpy.context.scene.objects if o.type=='MESH'}
report=[]
for index in range(1,6):
    lcd=next(o for o in bpy.context.scene.objects if o.name.startswith(f'{index:02d}_') and o.name.endswith('_|_LCD'))
    pref=lcd.name.split('_|_')[0]+'_|_';lcd.data.calc_loop_triangles()
    a,b,c=[lcd.matrix_world@lcd.data.vertices[i].co for i in lcd.data.loop_triangles[0].vertices]
    right=(b-a).normalized();up=(c-b).normalized();normal=right.cross(up).normalized()
    if normal.y>0:normal.negate()
    trees=[]
    for suffix in ['recessed_bezel','inner_frame']:
        o=bpy.data.objects[pref+suffix]
        trees.append(BVHTree.FromPolygons(pts(o),[list(p.vertices) for p in o.data.polygons]))
    screws=[o for o in bpy.context.scene.objects if o.name.startswith(pref+'captive_screw')]
    for o in screws:
        p=center(o);start=o.matrix_world.translation.copy();tangent=Vector()
        hits=[t.ray_cast(p+normal*.5,-normal,1)[0] for t in trees];hits=[h for h in hits if h is not None]
        if not hits:
            # A radio corner screw was displaced beyond the frame. Align it to
            # its upper partner's column and the existing lower row.
            partner=bpy.data.objects[pref+'captive_screw.003']
            u=center(partner).dot(right);v=min(center(s).dot(up) for s in screws)
            tangent=right*(u-p.dot(right))+up*(v-p.dot(up))
            matrix=o.matrix_world.copy();matrix.translation+=tangent;o.matrix_world=matrix
            bpy.context.view_layer.update();p=center(o)
            hits=[t.ray_cast(p+normal*.5,-normal,1)[0] for t in trees];hits=[h for h in hits if h is not None]
        if not hits:raise RuntimeError('No frame support: '+o.name)
        support=max(h.dot(normal) for h in hits);back=min(v.dot(normal) for v in pts(o))
        matrix=o.matrix_world.copy();matrix.translation+=normal*(support-.0003-back);o.matrix_world=matrix
        bpy.context.view_layer.update()
        gap=min(v.dot(normal) for v in pts(o))-support
        assert abs(gap+.0003)<2e-6
        report.append(dict(name=o.name,shift=list(o.matrix_world.translation-start),corner_realign=list(tangent),gap=gap))
names={r['name'] for r in report}
for o in bpy.context.scene.objects:
    if o.type!='MESH':continue
    matrix,vertices=before[o.name]
    assert [tuple(v.co) for v in o.data.vertices]==vertices
    if o.name not in names:assert o.matrix_world==matrix,o.name
out=Path(r'D:/项目/emu-cab-1.21.1/model-source/button-mount/screw-report.json')
out.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('MOUNTED',len(report),'existing screws; all other transforms and mesh vertices unchanged')
