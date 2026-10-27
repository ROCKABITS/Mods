#!/usr/bin/env python3
"""Original municipal strongbox: carved walnut, blue enamel, brass, ledger and export parcels."""
import json,random
from pathlib import Path
from PIL import Image,ImageDraw
BASE=Path(__file__).resolve().parents[1]/'src/main/resources'
def write(path,value):
 p=BASE/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
def texture(name,base,grain=False):
 rng=random.Random(name);im=Image.new('RGBA',(32,32));p=im.load()
 for y in range(32):
  for x in range(32):
   n=rng.randrange(-8,9)+(6 if grain and y%8==0 else -10 if grain and y%8==7 else 0);p[x,y]=tuple(max(0,min(255,c+n)) for c in base)+(255,)
 d=ImageDraw.Draw(im)
 if grain:
  for y in range(4,32,8):
   for j in range(4):
    x=rng.randrange(26);d.line((x,y+j%2,x+rng.randrange(3,7),y+j%2),fill=(63,39,29))
 if name=='colony_brass':
  d.line((0,0,31,0),fill='#f3d896');d.line((0,1,0,31),fill='#e6bd67');d.line((0,31,31,31),fill='#7e572c')
 if name=='colony_paper':
  for y in range(5,29,5):d.line((4,y,26,y),fill='#a89c7c')
 if name=='colony_crest':
  d.rectangle((1,1,30,30),outline='#d5b164',width=2);d.polygon([(6,7),(14,7),(14,24),(6,24)],fill='#d5b164');d.polygon([(18,11),(26,11),(26,24),(18,24)],fill='#bda56c');d.rectangle((12,16,20,24),fill='#e7cb85')
  for x,y in [(6,5),(11,5),(18,9),(23,9)]:d.rectangle((x,y,x+2,y+3),fill='#e7cb85')
  d.rectangle((9,13,11,16),fill='#263b4b');d.rectangle((21,16,23,19),fill='#263b4b');d.rectangle((14,20,17,25),fill='#263b4b')
 if name=='colony_coin':
  d.rectangle((1,1,30,30),outline='#f0d991',width=2);d.ellipse((6,6,25,25),outline='#946327',width=2);d.line((16,9,16,23),fill='#f9df9c',width=2);d.line((10,16,22,16),fill='#f9df9c',width=2)
 p=BASE/f'assets/realmeconomy/textures/block/{name}.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
for name,c,g in [('colony_oak',(94,62,43),True),('colony_brass',(177,133,65),False),('colony_iron',(57,66,69),False),('colony_blue',(32,61,78),False),('colony_paper',(226,211,172),False),('colony_crest',(30,55,74),False),('colony_coin',(194,151,66),False)]:texture(name,c,g)
elements=[]
def box(a,b,t,rotation=None):
 e={'from':a,'to':b,'faces':{f:{'texture':'#'+t,'uv':[0,0,16,16]} for f in ['north','south','west','east','up','down']}}
 if rotation:e['rotation']=rotation
 elements.append(e)
for x in [1,12]:
 for z in [2,11]:box([x,0,z],[x+3,3,z+3],'colony_iron');box([x+.25,2,z+.25],[x+2.75,4,z+2.75],'colony_brass')
box([.5,2,1.5],[15.5,3.25,14.5],'colony_iron');box([1,3.25,2],[15,10,14],'colony_oak')
for y in [3.5,9.5]:box([.75,y,1.75],[15.25,y+.65,14.25],'colony_brass')
for x in [1,14]:
 box([x,3,1.6],[x+.8,10,2.4],'colony_iron');box([x,3,13.6],[x+.8,10,14.4],'colony_iron')
box([.25,10,1],[15.75,11.25,15],'colony_iron');box([.5,11.25,1.25],[15.5,11.75,14.75],'colony_brass');box([1,11.75,1.75],[15,12,14.25],'colony_blue')
# Enamel city seal and raised lock on the front.
box([5.5,4.2,1.4],[10.5,9.2,1.75],'colony_brass');box([5.75,4.45,1.2],[10.25,8.95,1.4],'colony_crest')
box([7.2,9,1.1],[8.8,11.4,1.6],'colony_brass');box([7.6,9.3,.9],[8.4,10.4,1.1],'colony_iron');box([7.9,9.1,.85],[8.1,9.8,.9],'colony_oak')
for x in [2.25,12.25]:
 box([x,5.8,1.3],[x+1.5,7.8,1.75],'colony_brass');box([x-.5,6.3,.9],[x+2,6.8,1.3],'colony_iron')
# Rivets and side straps.
for x in [1.2,3.5,12.5,14.2]:
 for y in [3.65,9.65]:box([x,y,1.45],[x+.45,y+.45,1.75],'colony_brass')
for z in [4,10.5]:
 box([.8,3.5,z],[1.15,10,z+.75],'colony_iron');box([14.85,3.5,z],[15.2,10,z+.75],'colony_iron')
 box([2,10.5,z],[14,11,z+.5],'colony_brass')
# Open account book with separate pages and a cloth bookmark.
box([2,12,4],[8.6,12.3,10.8],'colony_oak');box([2.3,12.3,4.3],[5,12.85,10.5],'colony_paper');box([5.2,12.3,4.3],[8.3,12.85,10.5],'colony_paper');box([5,12.35,4.1],[5.25,13,10.8],'colony_brass')
box([7,12.85,9],[7.5,12.95,11.25],'colony_blue')
for z in [5.2,6.4,7.6,8.8]:
 box([2.8,12.85,z],[4.5,12.9,z+.1],'colony_oak');box([5.7,12.85,z],[7.8,12.9,z+.1],'colony_oak')
# Tray of coins, rolled export charter, and inkwell.
box([9.8,12,3.1],[14,12.4,6.8],'colony_oak')
for x,z,steps in [(10.4,3.7,2),(12.2,3.7,3),(11.3,5.2,1)]:
 for i in range(steps):box([x,12.4+i*.3,z],[x+1.1,12.7+i*.3,z+1.1],'colony_coin')
box([9.7,12,9],[14.4,12.65,10.7],'colony_paper');box([9.4,12,8.8],[10.1,12.85,10.9],'colony_paper');box([14.2,12,8.8],[14.9,12.85,10.9],'colony_paper');box([11.7,12.7,9.3],[12.4,12.85,10.2],'colony_blue')
box([11.1,12,11.8],[12.7,13.1,13.4],'colony_iron');box([11.45,13.1,12.15],[12.35,13.4,13.05],'colony_brass');box([11.8,13.35,12.4],[12,15,12.6],'colony_oak');box([12,13.9,12.35],[12.6,15.2,12.65],'colony_paper',{'origin':[12,13.9,12.5],'axis':'z','angle':-22.5})
textures={k:'realmeconomy:block/'+k for k in ['colony_oak','colony_brass','colony_iron','colony_blue','colony_paper','colony_crest','colony_coin']};textures['particle']=textures['colony_oak']
model={'textures':textures,'elements':elements,'display':{'gui':{'rotation':[28,225,0],'translation':[0,0,0],'scale':[.8,.8,.8]},'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]},'fixed':{'rotation':[0,180,0],'scale':[.7,.7,.7]},'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2,0],'scale':[.4,.4,.4]},'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,0,0],'scale':[.5,.5,.5]}}}
write(Path('assets/realmeconomy/models/block/colony_coffer.json'),model)
write(Path('assets/realmeconomy/models/item/colony_coffer.json'),{'parent':'realmeconomy:block/colony_coffer'})
write(Path('assets/realmeconomy/blockstates/colony_coffer.json'),{'variants':{f'facing={f}':{'model':'realmeconomy:block/colony_coffer','y':r} for f,r in [('north',0),('east',90),('south',180),('west',270)]}})
write(Path('data/realmeconomy/recipe/colony_coffer.json'),{'type':'minecraft:crafting_shaped','category':'misc','pattern':['GBG','PCP','IPI'],'key':{'G':{'item':'minecraft:gold_ingot'},'B':{'item':'minecraft:writable_book'},'P':{'item':'minecraft:dark_oak_planks'},'C':{'item':'minecraft:chest'},'I':{'item':'minecraft:iron_ingot'}},'result':{'id':'realmeconomy:colony_coffer','count':1}})
write(Path('data/realmeconomy/loot_table/blocks/colony_coffer.json'),{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'realmeconomy:colony_coffer'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
p=BASE/'data/minecraft/tags/block/mineable/axe.json';v=json.loads(p.read_text()) if p.exists() else {'replace':False,'values':[]}
if 'realmeconomy:colony_coffer' not in v['values']:v['values'].append('realmeconomy:colony_coffer')
write(Path('data/minecraft/tags/block/mineable/axe.json'),v)
print('Original colony coffer:',len(elements),'elements; 7 original pixel textures')
