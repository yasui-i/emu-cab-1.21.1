"""Generate picking and baked OBJ slices from the active editable cab export."""
import json
import math
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/emucab'
meshes=json.loads((ASSETS/'meshes/cab_mesh.json').read_text(encoding='utf-8'))
objects=[]
for m in meshes:
    pts=[[v*16 for v in p[:3]] for t in m['triangles'] for p in t['p']]
    if not pts:continue
    o=dict(name=m['name'])
    o['from']=[min(p[a] for p in pts) for a in range(3)]
    o['to']=[max(p[a] for p in pts) for a in range(3)]
    objects.append(o)

actions={
 'Direction_selector':('direction','DIRECTION'), 'Master_key':('master_key','MASTER'),
 'Pantograph_selector':('pantograph','PANTOGRAPH'), 'Circuit_breaker_selector':('breaker','BREAKER'),
 'Auxiliary_control':('auxiliary','AUXILIARY'), 'Brake_test':('brake_test','BRAKE_TEST'),
 'Yellow_vigilance_button':('vigilance','ACK'), 'Left_bank_eb_reset':('eb_reset','RESET'),
 'Left_bank_power':('power','POWER'), 'Left_bank_cab_light':('cab_light','CAB_LIGHT'),
 'Left_bank_cab_active':('cab_active','CAB_ACTIVE'), 'Left_bank_sand':('sand','SAND'),
 'Left_door_door_left':('door_left','DOOR_LEFT'), 'Wiper_switch':('wiper','WIPER'),
 'Headlight_switch':('headlight','HEADLIGHT')}
controls=[]
for o in objects:
    name=o['name'];prefix=name.split('_|_')[0];action=None;arg=0;front=False
    if prefix in actions and any(t in name for t in ['_|_button','_|_selector']):
        id,action=actions[prefix];suffix=name.rsplit('.',1)[1] if '.' in name else ''
        if suffix:id+='_'+suffix
        if prefix=='Left_bank_cab_active' and suffix in ['010','011']:
            action,id=('DOOR_LEFT','door_left_rightbank') if suffix=='010' else ('DOOR_RIGHT','door_right')
        elif prefix=='Left_bank_eb_reset' and suffix in ['010','011']:
            action,id=('DOOR_CLOSE','door_close') if suffix=='010' else ('HORN','horn')
    elif 'red_mushroom' in name:action='EMERGENCY';id='emergency_'+str(len(controls));front=True
    elif name=='ATP_acknowledgement_button':action='ACK';id='atp_ack';front=True
    elif name=='Desk_fastener_chrome_centre.003':action='UNASSIGNED';id='silver_button_b172';front=True
    elif ('lower_softkey_' in name or 'side_key_' in name) and 'inset' not in name:
        action='SCREEN';screen=int(name[:2])-1
        arg=screen*16+int(name.rsplit('_',1)[1])%10
        id='screen_'+str(screen)+'_'+name.split('_|_')[1];front=True
    elif '_|_keypad_' in name and 'marking' not in name:
        action='RADIO';row,col=map(int,name.rsplit('_',2)[1:]);arg=row*4+col;id=f'radio_key_{row}_{col}';front=True
    elif name.startswith('Traction_controller_|_ergonomic_grip'):
        action='HANDLE';id='master_handle'
    elif name.startswith('Underdesk_telephone_|_key'):
        action='RADIO';arg=12;id='telephone_'+str(len(controls));front=True
    if action:
        controls.append(dict(id=id,action=action,arg=arg,front=front,source=name,**{'from':o['from'],'to':o['to']}))
# Persist source-to-number identity; moving parts never renumbers existing controls.
previous_path=ROOT/'model-source/original-controls.json'
previous=json.loads(previous_path.read_text(encoding='utf-8')) if previous_path.exists() else []
previous_ids={c['source']:c['id'] for c in previous}
for c in controls:
    if c['source'] in previous_ids:c['id']=previous_ids[c['source']]
number_path=ROOT/'model-source/button-numbers.json'
numbers=json.loads(number_path.read_text(encoding='utf-8')) if number_path.exists() else {}
next_number=max((int(v[1:]) for v in numbers.values()),default=0)+1
def region(c):
    n=c['source']
    if n[:2].isdigit():return '屏幕 '+n[:2]
    if n.startswith('Underdesk_telephone'):return '台下电话'
    return '台面主控'
for c in sorted(controls,key=lambda c:(region(c),-sum([c['from'][0],c['to'][0]])/2,-sum([c['from'][1],c['to'][1]])/2,c['source'])):
    if c['source'] not in numbers:
        numbers[c['source']]=f'B{next_number:03d}';next_number+=1
    c['number']=numbers[c['source']];c['region']=region(c)
# Numbered assignments are authoritative. Object names came from the donor model
# and no longer describe the user's controls. Unlisted buttons have no function.
assignments=json.loads((ROOT/'model-source/control-functions.json').read_text(encoding='utf-8'))
for c in controls:
    assignment=assignments.get(c['number'],{})
    c['action']=assignment.get('action','UNASSIGNED')
    c['label']=assignment.get('label','无功能')
    c['arg']=0
assert all(numbers[c['source']]==c['number'] for c in controls)
number_text=json.dumps(numbers,ensure_ascii=False,indent=2)+'\n'
if not number_path.exists() or json.loads(number_path.read_text(encoding='utf-8'))!=numbers:
    number_path.write_text(number_text,encoding='utf-8')
assert len({c['id'] for c in controls})==len(controls),'Duplicate control IDs'
assert len({c['number'] for c in controls})==len(controls),'Duplicate button numbers'
displays=[]
for o in sorted(objects,key=lambda o:o['name']):
    if o['name'].endswith('_|_LCD'):
        lo,hi=o['from'],o['to']
        displays.append(dict(x=(lo[0]+hi[0])/2,top=hi[1],z=(lo[2]+hi[2])/2,width=hi[0]-lo[0],height=hi[1]-lo[1]))

# The pixel rod has a horizontal lower face. Its centre is the shared physical
# bearing for animation and picking; derive it from the edited geometry.
rod=next(m for m in meshes if m['name']=='Traction_controller_|_moving_lever')
rod_points={tuple(p[:3]) for t in rod['triangles'] for p in t['p']}
bottom_y=min(p[1] for p in rod_points)
bottom=[p for p in rod_points if abs(p[1]-bottom_y)<1e-6]
if len(bottom)>=3 and max(p[0] for p in bottom)-min(p[0] for p in bottom)>1e-6 and max(p[2] for p in bottom)-min(p[2] for p in bottom)>1e-6:
    lever_pivot=[(min(p[a] for p in bottom)+max(p[a] for p in bottom))/2 for a in range(3)]
    lever_pivot_source='moving_lever lower face'
else:
    # Explicit exports of the inclined donor rod retain its previous bearing.
    lever_pivot=[-.23*1.05+.5,.938*1.05,.33*1.05+.5]
    lever_pivot_source='legacy inclined rod bearing'

lines=['package com.yasui_i.emucab.control;','','// Generated from active editable model bounds by tools/integrate_original_cab.py.',
 'public final class CabGeometry {',
 '    // Shared animation/picking bearing, in Minecraft block coordinates.',
 *[f'    public static final double LEVER_PIVOT_{axis} = {value:.8f};' for axis,value in zip('XYZ',lever_pivot)],
 '    public record Control(String id, String number, CabAction action, int argument, boolean front,',
 '                          double x0, double y0, double z0, double x1, double y1, double z1) {}',
 '    public record Display(double x, double top, double z, double width, double height) {}',
 '    public static final Control[] CONTROLS = {']
for c in controls:
    coords=', '.join(str(round(v,7)) for v in c['from']+c['to'])
    lines.append(f'        new Control("{c["id"]}", "{c["number"]}", CabAction.{c["action"]}, {c["arg"]}, {str(c["front"]).lower()}, {coords}),')
lines+=['    };','    public static final Display[] DISPLAYS = {']
for d in displays: lines.append('        new Display('+', '.join(str(round(v,7)) for v in d.values())+'),')
lines+=['    };','    public static final double[][][] SHAPES = {']
for p,offset in [('left',-16),('center',0),('right',16)]:
    lines.append('        {')
    for o in objects:
        # Broad selection/collision shell only. Tiny controls use analytic ray picking,
        # avoiding a VoxelShape grid containing hundreds of unrelated coordinates.
        if not (o['name'].startswith('Desk_|_blue-grey') or o['name'].startswith('Screen_hood_|_')
                or o['name'] in ['Left_equipment_pedestal','Right_equipment_pedestal']):continue
        lo,hi=o['from'].copy(),o['to'].copy()
        lo[0],hi[0]=max(lo[0],offset),min(hi[0],offset+16)
        if lo[0]>=hi[0]:continue
        lo[0]-=offset;hi[0]-=offset
        lo=[math.floor(v*2)/2 for v in lo]
        hi=[math.ceil(v*2)/2 for v in hi]
        for a in range(3):hi[a]=max(hi[a],lo[a]+.5)
        lines.append('            {'+', '.join(str(round(v,7)) for v in lo+hi)+'},')
    lines.append('        },')
lines+=['    };','    private CabGeometry() {}','}']
(ROOT/'src/main/java/com/yasui_i/emucab/control/CabGeometry.java').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(ROOT/'model-source/original-controls.json').write_text(json.dumps(controls,indent=2),encoding='utf-8')
Image.new('RGBA',(16,16),(255,255,255,255)).save(ASSETS/'textures/block/cab_white.png')
print('Original structure integrated;',len(controls),'controls,',len(objects),'objects')

def moving(name):
    return name.startswith('Traction_controller_|_') and any(t in name for t in ['moving_lever','ergonomic_grip','top_release','red_cap'])

animation_path=ASSETS/'meshes/cab_animations.json'
animations=json.loads(animation_path.read_text(encoding='utf-8')) if animation_path.exists() else []
animated_parts={name for motion in animations for name in motion['parts']}
control_by_id={control['id']:control for control in controls}
mesh_by_name={mesh['name']:mesh for mesh in meshes}
for motion in animations:
    assert motion['id'] in control_by_id and control_by_id[motion['id']]['number']==motion['number'],'Animation control identity changed'
    assert all(name in mesh_by_name for name in motion['parts']),'Missing animation mesh'
    assert math.isclose(sum(v*v for v in motion['axis']),1,abs_tol=1e-6),'Animation axis is not unit length'
    assert motion['kind'] in ['press','rotary']
    assert not any(any(token in name for token in ['chrome_ring','mounting_ring','_|_boot','_|_collar','_|_socket']) for name in motion['parts']),'Fixed mount incorrectly animated'
assert len(animated_parts)==sum(len(motion['parts']) for motion in animations),'Animation mesh has two owners'

animation_lines=['package com.yasui_i.emucab.control;','','// Generated from the saved model installation planes. Units are Minecraft blocks.',
 'public final class CabAnimationGeometry {',
 '    public record Motion(String id, String number, String kind, double pivotX, double pivotY, double pivotZ,',
 '                         double axisX, double axisY, double axisZ, double travel,',
 '                         double x0, double y0, double z0, double x1, double y1, double z1) {}',
 '    public static final Motion[] MOTIONS = {']
for motion in animations:
    # Sweep actual mesh vertices, not the corners of their already-expanded
    # axis-aligned box. Rotating an AABB around an inclined mounting axis can
    # cover the neighbouring key even when the meshes never touch.
    points=[p[:3] for name in motion['parts'] for triangle in mesh_by_name[name]['triangles'] for p in triangle['p']]
    swept=list(points)
    axis=motion['axis'];pivot=motion['pivot']
    if motion['kind']=='press':
        swept += [[p[i]-axis[i]*motion['travel'] for i in range(3)] for p in points]
    else:
        lower,upper=map(math.radians,[-45,90])
        for p in points:
            delta=[p[i]-pivot[i] for i in range(3)]
            axial=[axis[i]*sum(axis[j]*delta[j] for j in range(3)) for i in range(3)]
            radial=[delta[i]-axial[i] for i in range(3)]
            tangent=[axis[1]*delta[2]-axis[2]*delta[1],axis[2]*delta[0]-axis[0]*delta[2],axis[0]*delta[1]-axis[1]*delta[0]]
            angles=[lower,upper]
            for i in range(3):
                critical=math.atan2(tangent[i],radial[i])
                angles += [critical+k*math.pi for k in range(-2,3) if lower<=critical+k*math.pi<=upper]
            swept += [[pivot[i]+axial[i]+radial[i]*math.cos(angle)+tangent[i]*math.sin(angle) for i in range(3)] for angle in angles]
    motion_bounds=[min(p[i] for p in swept)*16-.0001 for i in range(3)]+[max(p[i] for p in swept)*16+.0001 for i in range(3)]
    values=', '.join(str(value) for value in motion['pivot']+motion['axis']+[motion['travel']]+[round(v,8) for v in motion_bounds])
    animation_lines.append(f'        new Motion("{motion["id"]}", "{motion["number"]}", "{motion["kind"]}", {values}),')
animation_lines+=['    };',
 '    private static final java.util.Map<String,Motion> BY_ID = java.util.Arrays.stream(MOTIONS)',
 '            .collect(java.util.stream.Collectors.toUnmodifiableMap(Motion::id, java.util.function.Function.identity()));',
 '    public static Motion byId(String id) { return BY_ID.get(id); }',
 '    private CabAnimationGeometry() {}','}']
(ROOT/'src/main/java/com/yasui_i/emucab/control/CabAnimationGeometry.java').write_text('\n'.join(animation_lines)+'\n',encoding='utf-8')

(ASSETS/'meshes/cab_dynamic.json').write_text(json.dumps([m for m in meshes if moving(m['name']) or m['name'] in animated_parts or m['name'].endswith('_|_LCD')],separators=(',',':')),encoding='utf-8')

def clip(poly,bound,greater):
    out=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        ina=a[0]>=bound if greater else a[0]<=bound
        inb=b[0]>=bound if greater else b[0]<=bound
        if ina:out.append(a)
        if ina!=inb:
            t=(bound-a[0])/(b[0]-a[0])
            out.append([a[i]+t*(b[i]-a[i]) for i in range(5)])
    return out


def texture_path(texture):
    if texture=='atlas':return 'emucab:block/cab_atlas'
    if texture.startswith('pixel_'):return 'emucab:block/cab_pixel/'+texture.removeprefix('pixel_')
    return 'emucab:block/cab_white'

def clip_uv(poly,axis,bound,greater):
    out=[]
    for a,b in zip(poly,poly[1:]+poly[:1]):
        ina=a[axis]>=bound if greater else a[axis]<=bound
        inb=b[axis]>=bound if greater else b[axis]<=bound
        if ina:out.append(a)
        if ina!=inb:
            fraction=(bound-a[axis])/(b[axis]-a[axis])
            out.append([a[i]+fraction*(b[i]-a[i]) for i in range(5)])
    return out

def tiled_triangles(triangle):
    # OBJ textures are stitched sprites: repeating UVs must be clipped to tile
    # borders before baking, otherwise they sample neighbouring atlas sprites.
    if not triangle['texture'].startswith('pixel_'):return [triangle]
    poly=triangle['p'];result=[]
    u0=math.floor(min(p[3] for p in poly)+1e-9);u1=math.ceil(max(p[3] for p in poly)-1e-9)
    v0=math.floor(min(p[4] for p in poly)+1e-9);v1=math.ceil(max(p[4] for p in poly)-1e-9)
    for u in range(u0,max(u0+1,u1)):
        for v in range(v0,max(v0+1,v1)):
            clipped=clip_uv(clip_uv(clip_uv(clip_uv(poly,3,u,True),3,u+1,False),4,v,True),4,v+1,False)
            for i in range(1,len(clipped)-1):
                points=[[p[0],p[1],p[2],max(0,min(1,p[3]-u)),max(0,min(1,p[4]-v))] for p in [clipped[0],clipped[i],clipped[i+1]]]
                piece=dict(triangle,p=points)
                if area(piece)>1e-14:
                    assert sum(a*b for a,b in zip(cross(triangle),cross(piece)))>0,'UV clipping reversed face'
                    result.append(piece)
    assert math.isclose(sum(area(t) for t in result),area(triangle),rel_tol=1e-6,abs_tol=1e-10),'UV tiling lost area'
    return result

def write_obj(name,triangles,offset=0):
    obj=['mtllib cab_item.mtl'];materials={};vertex=1
    for tri in triangles:
        key=(tri['texture'],tuple(tri['color']))
        if key not in materials:materials[key]='mat_'+str(len(materials))
        obj.append('usemtl '+materials[key])
        for p in tri['p']:obj.append(f'v {p[0]-offset:.7f} {p[1]:.7f} {p[2]:.7f}')
        for p in tri['p']:obj.append(f'vt {p[3]:.7f} {1-p[4]:.7f}')
        obj.append('f '+' '.join(f'{v}/{v}' for v in range(vertex,vertex+3)));vertex+=3
    (ASSETS/f'models/{name}.obj').write_text('\n'.join(obj)+'\n',encoding='utf-8')
    return materials


def cross(t):
    a,b,c=t['p'];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
    return [u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]

def area(t):return math.sqrt(sum(v*v for v in cross(t)))/2

tiled_meshes=[dict(m,triangles=[piece for t in m['triangles'] for piece in tiled_triangles(t)]) for m in meshes]
all_triangles=[t for m in tiled_meshes for t in m['triangles']]
materials=write_obj('cab_item',all_triangles)
mtl=[]
for (tex,col),name in materials.items():
    mtl+=['newmtl '+name,'Ka 0 0 0','Kd '+' '.join(str(v) for v in col),
          'map_Kd '+texture_path(tex)]
(ASSETS/'models/cab_item.mtl').write_text('\n'.join(mtl)+'\n',encoding='utf-8')

# Use the same material ordering for every slice by writing all definitions together.
def model(obj):
    return dict(loader='neoforge:obj',model=f'emucab:models/{obj}.obj',automatic_culling=False,
                flip_v=True,emissive_ambient=False,ambientocclusion=False,
                textures={'particle':'emucab:block/cab_pixel/gray_concrete'},render_type='minecraft:cutout')

static=[t for m in tiled_meshes if not moving(m['name']) and m['name'] not in animated_parts for t in m['triangles']]
slice_counts={}
sliced_area=0
for part,offset,low,high in [('left',-1,None,0),('center',0,0,1),('right',1,1,None)]:
    triangles=[]
    for t in static:
        poly=t['p']
        if low is not None:poly=clip(poly,low,True)
        if high is not None:poly=clip(poly,high,False)
        for i in range(1,len(poly)-1):
            piece=dict(t,p=[poly[0],poly[i],poly[i+1]])
            if area(piece)<1e-14:continue
            assert sum(a*b for a,b in zip(cross(t),cross(piece)))>0, 'Clipping reversed a face'
            triangles.append(piece)
    sliced_area+=sum(area(t) for t in triangles)
    local_materials=write_obj('cab_static_'+part,triangles,offset)
    # Local material names must retain their meaning regardless of first occurrence.
    objpath=ASSETS/f'models/cab_static_{part}.obj'
    data=objpath.read_text(encoding='utf-8')
    for key,local in local_materials.items():data=data.replace('usemtl '+local+'\n','usemtl shared_'+materials[key]+'\n')
    data=data.replace('usemtl shared_','usemtl ')
    objpath.write_text(data,encoding='utf-8')
    (ASSETS/f'models/block/cab_desk_{part}.json').write_text(json.dumps(model('cab_static_'+part),indent=2),encoding='utf-8')
    slice_counts[part]=len(triangles)
source_area=sum(area(t) for t in static)
assert math.isclose(source_area,sliced_area,rel_tol=1e-7,abs_tol=1e-10), 'Static slicing lost surface area'
print('Surface conservation:',source_area,sliced_area)
item=model('cab_item')
item['display']={'gui':{'rotation':[25,225,0],'scale':[.38,.38,.38]},'ground':{'scale':[.18,.18,.18]},'firstperson_righthand':{'scale':[.3,.3,.3]}}
(ASSETS/'models/item/cab_desk.json').write_text(json.dumps(item,indent=2),encoding='utf-8')
print('Static chunk slices:',slice_counts,'dynamic triangles:',sum(len(m['triangles']) for m in meshes if moving(m['name']) or m['name'] in animated_parts or m['name'].endswith('_|_LCD')))
report=dict(source_triangles=sum(len(m['triangles']) for m in meshes),baked_item_triangles=len(all_triangles),static_slice_triangles=slice_counts,static_area=source_area,sliced_area=sliced_area,controls=len(controls),lever_pivot=lever_pivot,lever_pivot_source=lever_pivot_source,textures=sorted({t['texture'] for t in all_triangles}),animated_controls=len(animations),animated_parts=len(animated_parts),animated_control_triangles=sum(len(mesh_by_name[name]['triangles']) for name in animated_parts),dynamic_triangles=sum(len(m['triangles']) for m in meshes if moving(m['name']) or m['name'] in animated_parts or m['name'].endswith('_|_LCD')))
report_path=ROOT/'model-source/pixel-integration-20261101/integration-report.json'
if report_path.parent.exists():report_path.write_text(json.dumps(report,indent=2),encoding='utf-8')
