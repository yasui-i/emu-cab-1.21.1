"""CTCS3-300H-inspired central screen, fitted to the existing LCD without moving it."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/emucab/textures/display/screen_2.png'
S=2
BG='#151b22';WHITE='#dce2dc';MUTED='#859391';YELLOW='#eee3ac'

def artwork():
    im=Image.new('RGB',(512,352),BG);d=ImageDraw.Draw(im)
    def line(points,fill,width=1):d.line([(round(x*S),round(y*S)) for x,y in points],fill=fill,width=max(1,round(width*S)))
    def rect(box,fill,outline=None):d.rectangle(tuple(round(v*S) for v in box),fill=fill,outline=outline,width=1)
    def text(x,y,value,size=5,color=WHITE,anchor='mm'):
        d.text((round(x*S),round(y*S)),value,font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',round(size*S)),fill=color,anchor=anchor)
    rect((0,0,255.5,175.5),BG,'#59676b')
    text(7,7,'CTCS3-300H',5.5,anchor='lm');text(209,7,'列控车载显示',5,anchor='rm')
    line([(5,13),(215,13)],'#38454e',.5)
    # 270-degree dial, matching the 0..450 scale and white centre disc in the reference.
    cx,cy,r=68,71,55
    points=[(cx+math.cos(math.radians(135+i))*r,cy+math.sin(math.radians(135+i))*r) for i in range(271)]
    line(points,'#b9c4c4',1.2)
    for value in range(0,451,5):
        a=math.radians(135+value/450*270);major=value%50==0;length=7 if major else 3.5
        line([(cx+math.cos(a)*(r-length),cy+math.sin(a)*(r-length)),(cx+math.cos(a)*r,cy+math.sin(a)*r)],WHITE,1 if major else .6)
        if major:
            text(cx+math.cos(a)*41,cy+math.sin(a)*41,str(value),6)
    d.ellipse((round((cx-10)*S),round((cy-10)*S),round((cx+10)*S),round((cy+10)*S)),fill='#dce4dc')
    text(cx,87,'km/h',4.5,MUTED)
    # Right-hand blue planning field. Current limit remains a single numeric readout.
    rect((140,29,217,91),'#111b2a')
    for value in range(0,451,100):
        y=91-value/450*62
        line([(140,y),(217,y)],'#26364c',.5);text(137,y,str(value),4,MUTED,'rm')
    for x in range(140,218,13):line([(x,29),(x,91)],'#26364c',.5)
    text(177,22,'速度规划',5,YELLOW)
    text(140,105,'当前限速',5,YELLOW,'lm');text(215,116,'km/h',4.5,MUTED,'rm')
    # The screenshot's right menu strip is artwork; it does not assign new button actions.
    for i,label in enumerate(['数据','模式','特殊','等级','其它','启动','警报','设置']):
        y=4+i*21
        rect((224,y,254,y+19),'#20282f' if i%2==0 else '#182027')
        text(239,y+9,label,6,YELLOW)
    line([(220,2),(220,172)],'#4b585b',.5)
    # Lower message area: use real state and route placeholder instead of invented train IDs.
    line([(6,132),(215,132)],'#44514f',.5)
    text(7,141,'下一站',5,MUTED,'lm')
    text(7,154,'ATP',5,MUTED,'lm')
    text(7,166,'提示',5,MUTED,'lm')
    text(151,143,'主控',4.5,MUTED,'lm')
    text(151,155,'方向',4.5,MUTED,'lm')
    text(151,167,'前探',4.5,MUTED,'lm')
    # Thin bottom and display divisions keep the compact DMI arrangement legible.
    line([(145,137),(145,171)],'#34423f',.5)
    return im

def preview(speed=184,limit=350):
    im=artwork();d=ImageDraw.Draw(im)
    def line(points,fill,width):d.line([(round(x*S),round(y*S)) for x,y in points],fill=fill,width=round(width*S))
    def text(x,y,value,size,color=WHITE):
        d.text((round(x*S),round(y*S)),value,font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',round(size*S)),fill=color,anchor='mm')
    a=math.radians(135+min(1,max(0,speed/450))*270)
    line([(68,71),(68+math.cos(a)*47,71+math.sin(a)*47)],WHITE,1.5)
    d.ellipse((58*S,61*S,78*S,81*S),fill='#dce4dc')
    text(68,71,str(speed),8,'#182321')
    a=math.radians(135+min(1,max(0,limit/450))*270)
    line([(68+math.cos(a)*51,71+math.sin(a)*51),(68+math.cos(a)*57,71+math.sin(a)*57)],YELLOW,2)
    points=[]
    for i in range(77):
        distance=i/76*1024
        v=min(limit,math.sqrt((80/3.6)**2+2*.7*max(650-distance,0))*3.6)
        points.append((141+i,91-min(450,v)/450*61))
    d.polygon([(141*S,91*S)]+[(round(x*S),round(y*S)) for x,y in points]+[(217*S,91*S)],fill='#164eb5')
    line(points,'#98c5ff',.65)
    text(143,96,'0',3.5,MUTED);text(204,96,'1024 m',3.5,MUTED)
    text(185,114,str(limit),11,YELLOW)
    text(69,141,'—',6);text(73,154,'运行就绪',5.5,'#8bda85');text(73,166,'—',5,MUTED)
    text(194,143,'P3',6,'#f0e6b0');text(194,155,'前进',5.5);text(194,167,'650 m',5)
    return im

if __name__=='__main__':
    OUT.parent.mkdir(parents=True,exist_ok=True);artwork().save(OUT)
    (ROOT/'build').mkdir(exist_ok=True)
    preview().resize((1024,704),Image.Resampling.NEAREST).save(ROOT/'build/central-display-preview.png')
    print('Generated reference-style central LCD and sample preview (184 / 300).')
