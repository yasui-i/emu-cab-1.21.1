"""Produce local numbered reference views and a fillable HTML/CSV control register."""
import csv,json,math,html
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'model-source/button-reference';OUT.mkdir(parents=True,exist_ok=True)
controls=json.loads((ROOT/'model-source/original-controls.json').read_text())
assignments=json.loads((ROOT/'model-source/control-functions.json').read_text(encoding='utf-8'))
translations=json.loads((ROOT/'src/main/resources/assets/emucab/lang/zh_cn.json').read_text(encoding='utf-8'))
meshes=json.loads((ROOT/'src/main/resources/assets/emucab/meshes/cab_mesh.json').read_text())
atlas=Image.open(ROOT/'src/main/resources/assets/emucab/textures/block/cab_atlas.png').convert('RGB')
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',23)
titlefont=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',32)
def centre(c):return [(c['from'][i]+c['to'][i])/32 for i in range(3)]
def render(name,items,tilt):
    # Driver view: Minecraft +X appears left. Tilted top view exposes desk controls.
    def project(p):return (-p[0],p[1]*math.cos(tilt)+p[2]*math.sin(tilt),p[2]*math.cos(tilt)-p[1]*math.sin(tilt))
    points=[project(centre(c)) for c in items]
    lo=[min(p[i] for p in points) for i in range(2)];hi=[max(p[i] for p in points) for i in range(2)]
    pad=.1 if len(items)>20 else .16
    lo=[v-pad for v in lo];hi=[v+pad for v in hi]
    w,h=1800,1200;scale=min(1150/max(hi[0]-lo[0],.2),900/max(hi[1]-lo[1],.2))
    cx=(lo[0]+hi[0])/2;cy=(lo[1]+hi[1])/2
    def screen(p):return (w/2+(p[0]-cx)*scale,h/2-(p[1]-cy)*scale)
    im=Image.new('RGB',(w,h),'#17222d');d=ImageDraw.Draw(im)
    triangles=[]
    for m in meshes:
        for t in m['triangles']:
            ps=[project(p) for p in t['p']];xy=[screen(p) for p in ps]
            if max(p[0] for p in xy)<260 or min(p[0] for p in xy)>1540 or max(p[1] for p in xy)<75 or min(p[1] for p in xy)>1150:continue
            if t['texture']=='atlas':
                u=sum(p[3] for p in t['p'])/3;v=sum(p[4] for p in t['p'])/3
                col=atlas.getpixel((min(atlas.width-1,max(0,int(u*atlas.width))),min(atlas.height-1,max(0,int(v*atlas.height)))))
            else:col=tuple(int(v*255) for v in t['color'])
            triangles.append((sum(p[2] for p in ps)/3,xy,col))
    for _,xy,col in sorted(triangles,reverse=True):d.polygon(xy,fill=col,outline=tuple(max(0,int(v*.75)) for v in col))
    d.rectangle((0,0,w,65),fill='#101923');d.text((28,12),name+' · 已指定功能按编号绑定，留空无功能',font=titlefont,fill='white')
    # Arrange labels in two columns with leaders, so dense keys remain legible.
    positions=sorted([(screen(project(centre(c))),c) for c in items],key=lambda pair:pair[0][0])
    mid=(len(positions)+1)//2
    for side,group in enumerate([positions[:mid],positions[mid:]]):
        group.sort(key=lambda pair:pair[0][1])
        for i,(p,c) in enumerate(group):
            y=110+i*(1000/max(len(group)-1,1));x=35 if side==0 else 1650
            end=(x+104 if side==0 else x-10,y+14)
            color='#ffd372';d.line([p,end],fill=color,width=2)
            d.ellipse((p[0]-5,p[1]-5,p[0]+5,p[1]+5),fill=color,outline='black',width=1)
            d.rectangle((x-5,y-3,x+93,y+34),fill='#101923',outline=color)
            d.text((x,y),c['number'],font=font,fill=color)
    path=OUT/(name+'.png');im.save(path);return path.name

groups=['台面主控']+[f'屏幕 {i:02d}' for i in range(1,6)]+['台下电话']
images=[]
for group in groups:
    subset=[c for c in controls if c['region']==group]
    if subset:images.append((group,render(group,subset,.85 if group=='台面主控' else .2)))
saved={}
if (OUT/'按钮功能填写.csv').exists():
    with (OUT/'按钮功能填写.csv').open(encoding='utf-8-sig',newline='') as f:
        saved={r[0]:r[4:6] for r in list(csv.reader(f))[1:] if len(r)>=6}
with (OUT/'按钮功能填写.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.writer(f);writer.writerow(['编号','区域','物件名','现有功能（待确认）','你指定的功能','操作方式/联锁条件'])
    for c in sorted(controls,key=lambda c:c['number']):
        label=c.get('label',c['action']);hint=translations.get('hint.emucab.'+c['action'].lower(),'')
        entry=saved.get(c['number'],['',''])
        if c['number'] in assignments:entry=[label,hint]
        writer.writerow([c['number'],c['region'],c['source'],label,*entry])
rows=''.join(f'<tr><td>{c["number"]}</td><td>{c["region"]}</td><td>{html.escape(c["source"])}</td><td>{html.escape(c.get("label",c["action"]))}</td><td contenteditable="true">{html.escape(saved.get(c["number"],["",""])[0] if c["number"] not in assignments else c["label"])}</td><td contenteditable="true">{html.escape(translations.get("hint.emucab."+c["action"].lower(),""))}</td></tr>' for c in sorted(controls,key=lambda c:c['number']))
sections=''.join(f'<h2>{name}</h2><a href="{file}"><img src="{file}"></a>' for name,file in images)
(OUT/'按钮编号对照.html').write_text('''<!doctype html><meta charset="utf-8"><title>驾驶台按钮编号</title><style>body{font:16px sans-serif;background:#17222d;color:#e8edf2;margin:30px}img{max-width:100%}table{border-collapse:collapse;width:100%}td,th{border:1px solid #607489;padding:8px}td[contenteditable]{background:#263c50;min-width:150px}a{color:#ffd372}thead{position:sticky;top:0;background:#17222d}</style><h1>驾驶台按钮编号 · {len(controls)} 个</h1><p>功能按用户指定编号绑定；未指定按钮无功能。B173 为新增白色操纵模式选择按钮，仅预留请求信号。车门及制动状态是驾驶台命令，尚无车辆反馈。网页直接填写不会自动保存，请复制内容或填写旁边的 CSV 文件。</p>'''.replace('{len(controls)}',str(len(controls)))+sections+'<h2>完整清单</h2><table><thead><tr><th>编号</th><th>区域</th><th>物件名称</th><th>当前功能</th><th>你指定的功能</th><th>操作方式/联锁</th></tr></thead><tbody>'+rows+'</tbody></table>',encoding='utf-8')
print('Numbered',len(controls),'controls; maps:',len(images),';',OUT)
