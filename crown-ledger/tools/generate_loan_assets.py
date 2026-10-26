#!/usr/bin/env python3
"""Original pixel art and a single-block royal credit counter; no downloaded artwork."""
from pathlib import Path
import json, math, random
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ART=ROOT/'assets/kingdomtreasury'
random.seed(824)
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
def texture(name,base,kind='plain'):
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    for y in range(32):
        for x in range(32):
            v=random.choice([-6,-3,0,0,0,3,5]); grain=(x%8==0)*-15 if kind=='wood' else 0
            d.point((x,y),tuple(max(0,min(255,c+v+grain)) for c in base)+(255,))
    if kind=='wood':
        for y in [3,12,24]:d.line([(2,y),(7,y),(9,y+1),(22,y+1),(25,y),(31,y)],fill=(83,47,32),width=1)
        for x in [0,15,31]:d.line((x,0,x,31),fill=(56,31,24))
    if kind=='paper':
        d.rectangle((1,1,30,30),outline=(142,113,69));d.line((6,4,6,28),fill=(165,115,81))
        for y in [8,12,16,20,24]:d.line((9,y,random.randrange(20,28),y),fill=(112,89,60))
    if kind=='book':
        d.rectangle((2,2,29,29),outline=(175,135,65));d.rectangle((4,4,27,27),outline=(108,93,51));d.line((6,0,6,31),fill=(200,161,78),width=2)
        for y in [6,22]:d.rectangle((0,y,31,y+2),fill=(124,97,49))
    if kind=='crest':
        d.rectangle((1,1,30,30),outline=(193,152,69));d.rectangle((3,3,28,28),outline=(87,74,40))
        d.polygon([(7,10),(11,14),(16,7),(21,14),(25,10),(23,23),(9,23)],fill=(217,174,81));d.line((10,21,22,21),fill=(255,225,154),width=2);d.rectangle((15,17,17,19),fill=(129,41,44))
    path=ART/'textures/block'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
for name,base,kind in [('loan_walnut',(103,63,42),'wood'),('loan_brass',(182,137,65),'plain'),('loan_felt',(29,75,65),'plain'),('loan_paper',(226,209,165),'paper'),('loan_book',(31,62,55),'book'),('loan_crest',(46,65,50),'crest'),('loan_ink',(32,34,39),'plain'),('loan_quill',(234,222,187),'plain'),('loan_wax',(145,43,48),'plain')]:texture(name,base,kind)

textures={k:f'kingdomtreasury:block/loan_{k}' for k in ['walnut','brass','felt','paper','book','crest','ink','quill','wax']}
textures['particle']=textures['walnut']
elements=[]
def box(name,a,b,tex='walnut',rotation=None,front=None):
    faces={s:{'uv':[0,0,16,16],'texture':'#'+tex} for s in ['north','south','east','west','up','down']}
    if front:faces['north']['texture']='#'+front
    e={'name':name,'from':a,'to':b,'faces':faces}
    if rotation:e['rotation']=rotation
    elements.append(e)
# Feet, deep cabinet and a bevelled writing surface.
for x in [1,12]:
    for z in [1,12]:
        box('carved foot',[x,0,z],[x+3,2,z+3]);box('brass foot cap',[x-.12,0,z-.12],[x+3.12,.65,z+3.12],'brass')
box('cabinet',[1,1.4,1.8],[15,10.8,14.2]);box('lower moulding',[.5,1.4,1.3],[15.5,2.15,14.7]);box('lower gold bead',[.6,2.15,1.4],[15.4,2.4,14.6],'brass')
box('overhanging top',[0,10.8,0],[16,11.7,16]);box('polished brass lip',[0,11.7,0],[16,12,16],'brass');box('raised top',[.3,12,.3],[15.7,12.35,15.7]);box('green leather writing mat',[1,12.35,1.2],[15,12.43,14.8],'felt')
for x in [.7,14.5]:box('brass mat edge',[x,12.43,.9],[x+.5,12.48,15.1],'brass')
for z in [.8,14.8]:box('brass mat edge',[.7,12.43,z],[15,12.48,z+.3],'brass')
# Fluted stiles, front heraldry and drawers on the rear.
for x in [1.2,13.8]:
    box('front pillar',[x,2.5,1.45],[x+1,10.7,2.2]);box('pillar inset',[x+.28,3,1.35],[x+.72,10.2,1.5],'brass')
box('front green panel',[3,3.2,1.55],[13,9.5,1.8],'felt')
for y in [3.05,9.5]:box('panel gold frame',[2.8,y,1.35],[13.2,y+.22,1.8],'brass')
for x in [2.8,13]:box('panel gold frame',[x,3.05,1.35],[x+.22,9.72,1.8],'brass')
box('royal crest',[5.7,4.3,1.25],[10.3,8.9,1.55],'brass',front='crest')
for y in [3,6.6]:
    box('rear drawer',[2.2,y,14.15],[13.8,y+2.8,14.45]);box('drawer escutcheon',[7.1,y+.9,14.45],[8.9,y+1.9,14.65],'brass');box('drawer handle',[6.5,y+1.2,14.65],[9.5,y+1.65,14.95],'brass')
# A bound ledger with sculpted spine and layered open pages.
box('ledger lower cover',[1.8,12.48,6.4],[8.5,12.8,13.5],'book')
box('ledger page edges',[2.05,12.8,6.65],[8.25,13.25,13.25],'paper')
box('left illuminated page',[2,13.25,6.6],[5.03,13.4,13.3],'paper',{'origin':[5.05,13.3,10],'axis':'z','angle':-22.5})
box('right illuminated page',[5.08,13.25,6.6],[8.25,13.4,13.3],'paper',{'origin':[5.05,13.3,10],'axis':'z','angle':22.5})
box('ledger golden spine',[4.9,13.18,6.65],[5.18,13.5,13.3],'brass');box('velvet bookmark',[5.5,12.7,12.6],[6.05,12.85,14.2],'wax')
# The offered parchment, rolled ends, wax seal, treasury coin tray and inkwell.
box('contract sheet',[7.6,12.5,2.5],[13,12.6,7.5],'paper')
for z in [2.2,7.35]:
    box('rolled parchment',[7.4,12.5,z],[13.2,13.1,z+.45],'paper');box('parchment roll shadow',[7.7,12.6,z-.04],[12.9,12.95,z+.08],'brass')
box('wax seal base',[11.8,12.6,6.25],[12.7,12.78,7.15],'wax');box('wax seal crown',[12.05,12.78,6.5],[12.45,12.85,6.9],'brass')
box('coin tray',[1.7,12.47,2],[6.1,12.65,5.6],'brass');box('tray velvet',[2,12.65,2.3],[5.8,12.68,5.3],'felt')
for x,z,n in [(2.4,2.8,2),(4.2,3.4,3),(3.3,4.2,1)]:
    for i in range(n):
        box('stacked royal coin',[x,12.7+i*.18,z],[x+1.1,12.84+i*.18,z+1.1],'brass')
box('inkwell plinth',[11.7,12.48,10.4],[14.4,12.8,13.1],'brass');box('inkwell',[12,12.8,10.7],[14.1,13.8,12.8],'ink');box('inkwell collar',[12.4,13.8,11.1],[13.7,14.05,12.4],'brass');box('ink opening',[12.7,14.05,11.4],[13.4,14.1,12.1],'ink')
for i in range(5):
    width=[.4,.8,1,.8,.4][i];box('ivory quill feather',[13-width/2,13.8+i*.36,11.6],[13+width/2,14.16+i*.36,11.88],'quill',{'origin':[13,13.7,11.7],'axis':'z','angle':22.5})
box('quill shaft',[12.92,13.6,11.68],[13.08,15.9,11.82],'brass',{'origin':[13,13.7,11.7],'axis':'z','angle':22.5})
model={'credit':'Original ROCKABITS Realm Treasury artwork','render_type':'minecraft:cutout','textures':textures,'elements':elements,'display':{'gui':{'rotation':[25,225,0],'translation':[0,0,0],'scale':[.75,.75,.75]},'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,180,0],'scale':[.6,.6,.6]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4,.4,.4]}}}
write(ART/'models/block/loan_desk.json',model);write(ART/'models/item/loan_desk.json',{'parent':'kingdomtreasury:block/loan_desk'})
write(ART/'blockstates/loan_desk.json',{'variants':{'facing='+name:{'model':'kingdomtreasury:block/loan_desk',**({'y':angle} if angle else {})} for name,angle in [('north',0),('east',90),('south',180),('west',270)]}})
write(ROOT/'data/kingdomtreasury/loot_table/blocks/loan_desk.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'kingdomtreasury:loan_desk'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
write(ROOT/'data/kingdomtreasury/recipe/loan_desk.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['BPG','DCD','DDD'],'key':{'B':{'item':'minecraft:writable_book'},'P':{'item':'minecraft:paper'},'G':{'item':'minecraft:gold_ingot'},'D':{'item':'minecraft:dark_oak_planks'},'C':{'item':'minecraft:chest'}},'result':{'id':'kingdomtreasury:loan_desk','count':1}})
# GUI paper and a faceted wax medallion; low-contrast grain keeps text readable.
gui=ART/'textures/gui';gui.mkdir(parents=True,exist_ok=True)
paper=Image.new('RGBA',(256,256));d=ImageDraw.Draw(paper)
for y in range(256):
    for x in range(256):
        edge=min(x,y,255-x,255-y);v=random.choice([-2,-1,0,0,1,2])-max(0,9-edge)*2
        d.point((x,y),(max(0,239+v),max(0,224+v),max(0,188+v),255))
d.rectangle((7,7,248,248),outline=(181,146,86));d.rectangle((10,10,245,245),outline=(217,192,140))
for x,y in [(5,5),(250,5),(5,250),(250,250)]:d.polygon([(x,y-4),(x+4,y),(x,y+4),(x-4,y)],fill=(183,145,79))
paper.save(gui/'loan_parchment.png')
seal=Image.new('RGBA',(64,64));d=ImageDraw.Draw(seal)
d.polygon([(14,40),(25,47),(20,63),(14,57),(7,60)],fill=(143,47,54));d.polygon([(39,46),(50,39),(58,60),(50,57),(45,63)],fill=(126,35,43))
points=[(32+math.cos(i*math.tau/16)*(27 if i%2==0 else 25),29+math.sin(i*math.tau/16)*(27 if i%2==0 else 25)) for i in range(16)]
d.polygon(points,fill=(117,31,39));d.ellipse((8,5,55,52),fill=(166,49,55),outline=(202,79,71),width=2);d.ellipse((13,10,50,47),outline=(212,163,81),width=2)
d.polygon([(20,23),(26,28),(32,18),(38,28),(44,23),(41,37),(23,37)],fill=(233,187,94));d.line((24,34,40,34),fill=(255,222,153),width=2);d.rectangle((30,29,33,32),fill=(126,33,44));seal.save(gui/'loan_seal.png')
print(f'Generated original loan desk: {len(elements)} elements, 9 block textures, 2 GUI textures, recipe and loot table.')
