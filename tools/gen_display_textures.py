"""Clean instrument artwork traced in code from the supplied cab references."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from gen_central_display import artwork, preview as central_preview
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/emucab/textures/display'
OUT.mkdir(parents=True,exist_ok=True)
WHITE='#dce2dc'; MUTED='#9ca7af'; BLUE='#244fe0'; BG='#30343d'
FONTS={}
class Face:
    def __init__(self,bg=BG):
        self.im=Image.new('RGB',(512,352),bg);self.d=ImageDraw.Draw(self.im)
    def rect(self,box,fill,outline=None):
        self.d.rectangle(tuple(round(v*2) for v in box),fill=fill,outline=outline,width=1)
    def line(self,points,fill=WHITE,width=.5):
        self.d.line([(round(x*2),round(y*2)) for x,y in points],fill=fill,width=max(1,round(width*2)))
    def text(self,x,y,value,size=5,color=WHITE,anchor='lm'):
        n=round(size*2)
        if n not in FONTS:FONTS[n]=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',n)
        self.d.text((round(x*2),round(y*2)),value,font=FONTS[n],fill=color,anchor=anchor)
    def save(self,i):self.im.save(OUT/f'screen_{i}.png')
def status(f,title):
    f.text(139,7,'km/h',4,MUTED);f.text(171,7,'车辆监视',5,'#e7d59d')
    f.line([(238,11),(245,1),(252,11),(238,11)],WHITE,1)
    f.text(245,8,'!',5,WHITE,'mm');f.line([(2,15),(253,15)],MUTED)
def menu(f,items):
    for i,item in enumerate(items):
        x=2+i*252/len(items)
        f.line([(x,163),(x+252/len(items)-1,163)],'#8c949f')
        f.text(x+126/len(items),170,item,4.5,WHITE,'mm')
def bar(f,x,y,w,h,value,minimum,maximum,unit,side='left',magenta=False):
    f.rect((x,y,x+w,y+h),None,'#adb4c0');top=y+h-h*value
    if value>0:f.rect((x+.5,top,x+w-.5,y+h-.5),BLUE)
    if magenta:
        f.d.polygon([(round(x*2),round(top*2)),(round((x+w)*2),round(top*2)),(round(x*2),round((top+4)*2))],fill='#b24bd1')
    for i in range(11):
        yy=y+h-i*h/10;edge=x if side=='left' else x+w;end=edge+(-4 if side=='left' else 4)
        f.line([(edge,yy),(end,yy)],MUTED)
        if i%5==0:f.text(end+(-2 if side=='left' else 2),yy,f'{minimum+(maximum-minimum)*i/10:g}',4.5,MUTED,'rm' if side=='left' else 'lm')
    f.text(x+w/2,y-7,unit,5,WHITE,'mm')
def pantograph(f,x,y):
    f.rect((x-7,y,x+7,y+16),None,MUTED)
    f.line([(x-5,y+3),(x+4,y+3),(x-3,y+8),(x+4,y+13),(x-5,y+13)],WHITE,1)
    f.rect((x-7,y+19,x+7,y+32),None,MUTED)
    f.line([(x,y+21),(x-2,y+25),(x+2,y+27),(x,y+30)],WHITE,1)
# Far left radio terminal: blue header/softkeys, black workspace.
f=Face('#080b10');f.rect((0,0,255,17),'#4267bf')
f.text(5,8,'GSM-R',7);f.text(89,8,'车次：—',6);f.text(165,8,'机车号：',6)
f.rect((0,18,255,32),'#c8d0ce');f.text(5,25,'通信状态：未注册',6,'#b23c39');f.text(172,25,'工作模式：—',5,'#283039')
f.text(246,39,'无线列调',5,'#e7ce55','rm');f.text(12,51,'○',13,'#e7ce55');f.text(32,52,'待机',6,'#e7ce55');f.text(12,120,'GSM-R',6,'#e7ce55')
f.line([(2,132),(253,132)],'#b3bdc9');f.rect((1,133,254,155),'#788ca8')
for x in (62,126,190):f.line([(x,133),(x,155)],'#c9d1da')
for x,label in [(7,'呼叫号码'),(68,'车次号'),(132,'调度台'),(196,'通信状态')]:f.text(x,144,label,5)
for i,label in enumerate(['菜单','呼叫','接听','挂断','组呼','音量','设置','返回']):
    x=i*32;f.rect((x,157,x+31,175),'#315eb8','#c0cad6');f.text(x+16,166,label,6,WHITE,'mm')
f.save(0)
# Left inner electrical overview.
f=Face();status(f,'牵引监视');f.text(128,23,'电气牵引系统',5,WHITE,'mm');f.line([(69,31),(186,31)],MUTED)
for x,label in [(99,'03'),(158,'06')]:f.text(x,28,label,4,WHITE,'mm');pantograph(f,x,35)
f.line([(72,32),(72,70)],MUTED);f.text(63,40,'◀',6,'#80e466')
bar(f,27,48,11,106,0,17.5,32.5,'网压 kV');bar(f,220,48,11,106,0,0,600,'网流 A','right')
for i,x in enumerate([80,112,144,176]):
    f.text(x+5,79,['02','04','05','07'][i],4,WHITE,'mm')
    f.rect((x,88,x+10,154),None,MUTED)
    for yy in (88,121,154):f.line([(x+10,yy),(x+13,yy)],MUTED)
f.text(134,157,'牵引 / 制动 kN',4.5,WHITE,'mm');menu(f,['运行状态','电气牵引','牵引曲线','设备信息','故障记录','维护']);f.save(1)
artwork().save(OUT/'screen_2.png')
# Right inner: match reference's eight yellow brake states, four traction units,
# four larger yellow/green status blocks and tall reservoir-pressure gauge.
f=Face();status(f,'车辆监视');f.text(124,22,'车辆 / 制动监视',5,WHITE,'mm')
f.line([(69,31),(186,31)],MUTED)
for x,label in [(99,'03'),(158,'06')]:f.text(x,28,label,4,WHITE,'mm');pantograph(f,x,35)
f.line([(72,32),(72,70)],MUTED);f.text(63,40,'◀',6,'#80e466')
for i in range(8):
    x=55+i*19;f.text(x+8,76,f'{i+1:02}',4,WHITE,'mm')
f.line([(51,81),(205,81)],MUTED)
f.text(7,98,'牵引',5);f.text(7,119,'E 电制动',5);f.text(7,130,'M 预控',5);f.text(7,147,'制动状态',5)
for x in (75.5,113.5,132.5,170.5):
    f.rect((x,87,x+13,101),None,'#929ba6')
    f.rect((x,104,x+13,110),None,'#929ba6')
for i in range(8):
    x=55+i*19
    f.d.pieslice((round(x*2),round(109*2),round((x+16)*2),round(125*2)),180,360,fill='#252b33',outline=MUTED,width=1)
    f.rect((x,126,x+16,132),None,'#a7adb5')
    f.line([(x+4,129),(x+12,129)],MUTED)
for x in (55,93,150,188):
    f.rect((x,139,x+15,151),None,'#a7adb5')
    f.rect((x,152,x+15,158),None,'#7f8993')
    f.line([(x+4,149),(x+11,149)],'#efdf80')
bar(f,224,91,9,67,0,0,100,'制动 %','right')
f.rect((79,158,204,162),'#431d24')
menu(f,['运行','监视','制动','车门','信息','诊断','记录','维护','设置','设备控制']);f.save(3)
Face('#020304').save(4)
# Labelled review sheet. Centre curve and side-system bars are illustrative.
sheet=Image.new('RGB',(1536,792),'#10151c');d=ImageDraw.Draw(sheet);font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',17)
for i,(label,im) in enumerate([('左1 · 无线通信',Image.open(OUT/'screen_0.png')),('左2 · 牵引监视',Image.open(OUT/'screen_1.png')),('中央 · ATP / 速度—距离曲线示例',central_preview()),('右2 · 车辆 / 制动监视',Image.open(OUT/'screen_3.png')),('右1 · PIS / 后视监控（黑屏）',Image.open(OUT/'screen_4.png'))]):
    x=(i%3)*512;y=(i//3)*396;d.text((x+10,y+10),label,font=font,fill=WHITE);sheet.paste(im,(x,y+38))
d.text((1034,422),'顶栏支持动态日期 / 时间 / 速度。',font=font,fill=MUTED)
d.text((1034,454),'曲线示例：350 → 80 km/h，前方 650 m。',font=font,fill=MUTED)
(ROOT/'build').mkdir(exist_ok=True);sheet.save(ROOT/'build/display-artwork.png')
print('Generated five 512x352 faces and review sheet.')
