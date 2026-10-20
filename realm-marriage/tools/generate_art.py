#!/usr/bin/env python3
"""Original voxel altar, rings, parchment and synthesized ceremony bells. No external artwork."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,math,random,tempfile,subprocess,wave
import numpy as np
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=ROOT/'assets/realmmarriage'
def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2)+'\n')
palette={'wood':(74,44,40),'marble':(203,192,160),'ivory':(236,217,178),'gold':(199,155,61),'rose':(167,68,99),'leaf':(62,105,73),'opal':(173,225,224),'flame':(255,197,82),'ink':(41,33,35)}
for name,color in palette.items():
 rng=random.Random(name);im=Image.new('RGB',(64,64),color);p=im.load()
 for y in range(64):
  for x in range(64):
   grain=rng.randint(-7,7)+(int(8*math.sin(x*.65+y*.045)) if name=='wood' else int(5*math.sin(x*.18+y*.25)) if name=='marble' else (4 if y%8==0 else 0));p[x,y]=tuple(max(0,min(255,c+grain)) for c in color)
 d=ImageDraw.Draw(im)
 if name=='wood':
  for x in [1,17,35,54]:d.line((x,0,x-2,64),fill=(51,29,30),width=1)
 if name=='ivory':
  for y in [3,59]:d.line((0,y,64,y),fill=(204,169,103),width=2)
 path=ASSETS/'textures/block'/f'{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
def box(a,b,texture,rotation=None):
 value={'from':a,'to':b,'faces':{face:{'texture':'#'+texture} for face in ['north','south','east','west','up','down']}}
 if rotation:value['rotation']=rotation
 return value
lower=[];upper=[];collision=[]
def cube(x,y,z,xx,yy,zz,t,collide=False):
 for elements,dy in [(lower,0),(upper,16)]:
  lo=max(y,dy);hi=min(yy,dy+16)
  if lo<hi:elements.append(box([x,lo-dy,z],[xx,hi-dy,zz],t))
 if collide:collision.append([x,y,z,xx,yy,zz])
cube(.5,0,.5,15.5,1.1,15.5,'marble',True)
cube(1.2,1.1,1.2,14.8,1.7,14.8,'gold')
for x in [1.1,12.1]:
 for z in [1.1,12.1]:
  cube(x,1.1,z,x+2.8,10.8,z+2.8,'wood',True);cube(x-.1,1.5,z-.1,x+2.9,2.2,z+2.9,'gold');cube(x-.1,9.2,z-.1,x+2.9,9.8,z+2.9,'gold')
cube(1.8,5.2,7.1,14.2,6,8.9,'wood',True)
cube(.2,10.3,.3,15.8,12,15.7,'wood',True);cube(0,11.1,0,16,11.7,16,'gold');cube(.3,12,.5,15.7,12.65,15.5,'ivory',True)
cube(4.7,4.2,.25,11.3,12.8,1,'ivory');cube(4.7,4.2,.05,11.3,4.8,.3,'gold')
for x in [5.3,7.3,9.3]:cube(x,3.5,.2,x+.35,4.4,.7,'gold')
for x in [1.1,13.3]:
 cube(x,12,12.4,x+1.6,29.2,14,'wood',True);cube(x-.1,13,12.3,x+1.7,13.7,14.1,'gold');cube(x-.1,28.7,12.3,x+1.7,29.4,14.1,'gold')
for x,y,xx,yy in [(1.7,28.4,4,29.5),(3,29.4,5.6,30.4),(5,30.3,11,31.3),(10.4,29.4,13,30.4),(12,28.4,14.3,29.5)]:cube(x,y,12.4,xx,yy,14,'gold',True)
for cx in [6.1,9.4]:
 for x,y,xx,yy in [(cx-2,23,cx-1.5,26),(cx+1.5,23,cx+2,26),(cx-1.5,22.5,cx+1.5,23),(cx-1.5,26,cx+1.5,26.5)]:cube(x,y,11.7,xx,yy,12.4,'gold')
for x in [2.6,12.3]:
 cube(x-.4,12.7,4,x+1,13.2,5.4,'gold');cube(x,13.2,4.4,x+.6,18,5,'ivory');cube(x+.15,18,4.55,x+.45,18.3,4.85,'ink');cube(x+.02,18.2,4.42,x+.58,19.2,4.98,'flame')
cube(5,12.8,5.5,11,13.1,10.5,'gold');cube(5.4,13.1,5.9,10.6,13.35,10.1,'marble')
for cx in [1.7,14.1]:
 for cy in [19.8,24,28.8]:
  cube(cx-.7,cy-1,11.8,cx+.8,cy+.7,12.5,'leaf')
  for dx,dy in [(-.6,0),(.6,0),(0,-.6),(0,.6)]:cube(max(0,cx+dx-.5),cy+dy-.4,11.45,min(16,cx+dx+.5),cy+dy+.4,12.1,'ivory' if cy!=24 else 'rose')
  cube(cx-.25,cy-.25,11.25,cx+.25,cy+.25,11.6,'gold')
textures={k:'realmmarriage:block/'+k for k in palette};textures['particle']=textures['wood']
for half,elements in [('lower',lower),('upper',upper)]:write(ASSETS/'models/block'/f'wedding_altar_{half}.json',{'render_type':'minecraft:cutout','textures':textures,'elements':elements})
variants={}
for facing,rotation in [('north',0),('east',90),('south',180),('west',270)]:
 for half in ['lower','upper']:variants[f'facing={facing},half={half}']={'model':f'realmmarriage:block/wedding_altar_{half}','y':rotation}
write(ASSETS/'blockstates/wedding_altar.json',{'variants':variants})
itemelements=lower+[dict(e,**{'from':[e['from'][0],e['from'][1]+16,e['from'][2]],'to':[e['to'][0],e['to'][1]+16,e['to'][2]]}) for e in upper]
write(ASSETS/'models/item/wedding_altar.json',{'textures':textures,'elements':itemelements,'display':{'gui':{'rotation':[25,225,0],'translation':[0,-3,0],'scale':[.42,.42,.42]},'ground':{'translation':[0,3,0],'scale':[.3,.3,.3]},'fixed':{'scale':[.45,.45,.45]}}})
def ring(gem):
 e=[]
 for a,b in [([3,5,7],[5,11,9]),([11,5,7],[13,11,9]),([5,3,7],[11,5,9]),([5,11,7],[11,13,9])]:e.append(box(a,b,'gold'))
 for x,y in [(4,4),(11,4),(4,11),(11,11)]:e.append(box([x,y,7],[x+1,y+1,9],'gold'))
 e.append(box([6,12,6.5],[10,15.2,9.5],gem));e.append(box([7,14,6.1],[9,15.6,9.9],gem))
 return {'textures':textures,'elements':e,'display':{'gui':{'rotation':[15,-30,-18],'scale':[.85,.85,.85]},'firstperson_righthand':{'rotation':[0,-30,0],'translation':[1,1,0],'scale':[.5,.5,.5]},'thirdperson_righthand':{'rotation':[0,90,0],'translation':[0,3,1],'scale':[.35,.35,.35]},'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]}}}
for name,gem in [('engagement_ring','opal'),('wedding_ring','rose')]:write(ASSETS/'models/item'/f'{name}.json',ring(gem))
gui=ASSETS/'textures/gui';gui.mkdir(parents=True,exist_ok=True)
im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im)
for cx,cy in [(48,69),(80,59)]:
 d.ellipse((cx-31,cy-30,cx+31,cy+30),outline=(82,48,26),width=13);d.ellipse((cx-29,cy-29,cx+29,cy+29),outline=(211,167,70),width=10);d.arc((cx-28,cy-28,cx+28,cy+28),190,335,fill=(255,234,162),width=4);d.arc((cx-22,cy-22,cx+22,cy+22),10,150,fill=(146,94,38),width=2)
d.polygon([(70,26),(80,15),(92,27),(80,39)],fill=(202,237,230),outline=(243,244,210));d.line((80,16,80,38),fill=(121,184,189),width=2);im.save(gui/'rings.png')
scroll=Image.new('RGBA',(512,160));d=ImageDraw.Draw(scroll);d.rounded_rectangle((13,4,499,156),radius=5,fill=(65,39,46),outline=(214,178,102),width=3);d.rectangle((23,10,489,150),fill=(241,222,180),outline=(175,132,66),width=2);d.rectangle((29,15,483,145),outline=(220,182,107),width=1)
for xx in [10,490]:d.rectangle((xx,1,xx+12,158),fill=(214,175,103),outline=(92,60,39),width=2);d.rectangle((xx+3,3,xx+8,156),fill=(243,217,164))
for xx,flip in [(38,1),(474,-1)]:
 for yy in [25,133]:
  d.line((xx,yy,xx+22*flip,yy),fill=(188,143,69),width=2);d.line((xx,yy,xx,yy+(14 if yy<60 else -14)),fill=(188,143,69),width=2);d.polygon([(xx+6*flip,yy),(xx+11*flip,yy-5),(xx+16*flip,yy),(xx+11*flip,yy+5)],fill=(177,76,98))
rng=random.Random(77)
for _ in range(2200):
 x=rng.randint(31,480);y=rng.randint(18,142);r,g,b,a=scroll.getpixel((x,y));n=rng.randint(-5,5);scroll.putpixel((x,y),(max(0,min(255,r+n)),max(0,min(255,g+n)),max(0,min(255,b+n)),a))
scroll.save(gui/'ceremony_scroll.png')
certificate=Image.new('RGBA',(32,32));d=ImageDraw.Draw(certificate);d.rectangle((5,2,26,28),fill=(238,219,175),outline=(130,90,55));d.rectangle((3,1,27,4),fill=(192,146,74));d.rectangle((3,27,27,30),fill=(192,146,74));d.ellipse((10,7,16,13),outline=(180,135,57),width=2);d.ellipse((15,7,21,13),outline=(180,135,57),width=2)
for y in [16,19,22]:d.line((9,y,22,y),fill=(138,107,73),width=1)
d.ellipse((18,22,25,29),fill=(146,49,76),outline=(215,175,83));(ASSETS/'textures/item').mkdir(parents=True,exist_ok=True);certificate.save(ASSETS/'textures/item/marriage_certificate.png');write(ASSETS/'models/item/marriage_certificate.json',{'parent':'minecraft:item/generated','textures':{'layer0':'realmmarriage:item/marriage_certificate'}})
icon=Image.new('RGB',(512,512),(59,32,44));di=ImageDraw.Draw(icon);di.rounded_rectangle((12,12,499,499),radius=70,fill=(89,44,58),outline=(211,174,93),width=12);di.ellipse((69,75,443,449),fill=(235,215,171),outline=(191,143,70),width=7);icon.paste(im.resize((360,360),Image.Resampling.NEAREST),(76,90),im.resize((360,360),Image.Resampling.NEAREST));icon.save(ASSETS/'icon.png')
write(ROOT/'data/realmmarriage/recipe/wedding_altar.json',{'type':'minecraft:crafting_shaped','pattern':['GRG','WBW','P P'],'key':{'G':{'item':'minecraft:gold_ingot'},'R':{'item':'minecraft:rose_bush'},'W':{'item':'minecraft:white_wool'},'B':{'item':'minecraft:book'},'P':{'tag':'minecraft:planks'}},'result':{'id':'realmmarriage:wedding_altar','count':1}})
write(ROOT/'data/realmmarriage/recipe/engagement_ring.json',{'type':'minecraft:crafting_shaped','pattern':[' D ','G G',' G '],'key':{'G':{'item':'minecraft:gold_ingot'},'D':{'item':'minecraft:diamond'}},'result':{'id':'realmmarriage:engagement_ring','count':1}})
loot=json.loads((ROOT.parents[3]/'realm-titles/src/main/resources/data/realmtitles/loot_table/blocks/royal_seal_desk.json').read_text().replace('realmtitles:royal_seal_desk','realmmarriage:wedding_altar'))
write(ROOT/'data/realmmarriage/loot_table/blocks/wedding_altar.json',loot)
write(ROOT/'data/minecraft/tags/block/mineable/axe.json',{'replace':False,'values':['realmmarriage:wedding_altar']})
sounddefs={};rate=44100
for name,notes,duration in [('countdown',[(0,880,.15)],.2),('opening',[(0,523.25,1.4),(.12,659.25,1.2),(.24,783.99,1.2)],1.7),('objection',[(0,155.56,.9),(.12,164.81,.8),(.24,233.08,.8)],1.3),('married',[(0,523.25,1.1),(.23,659.25,1.2),(.46,783.99,1.4),(.8,1046.5,2),(1.1,523.25,1.8),(1.1,659.25,1.8),(1.1,783.99,1.8)],3.2)]:
 samples=np.zeros(int(rate*duration))
 for start,freq,length in notes:
  n=min(int(rate*length),len(samples)-int(rate*start));t=np.arange(n)/rate;env=(1-np.exp(-t*180))*np.exp(-t*(4 if name=='countdown' else 2.8));bell=(np.sin(t*freq*2*np.pi)+.34*np.sin(t*freq*4.02*np.pi)+.14*np.sin(t*freq*5.98*np.pi))*env;samples[int(rate*start):int(rate*start)+n]+=bell
 samples=samples/(max(1,np.max(np.abs(samples)))*1.15);audio=(np.stack([samples,samples*.97],axis=1)*26000).astype('<i2');out=ASSETS/'sounds/ceremony'/f'{name}.ogg';out.parent.mkdir(parents=True,exist_ok=True)
 with tempfile.TemporaryDirectory() as tmp:
  wav=Path(tmp)/'sound.wav'
  with wave.open(str(wav),'wb') as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(rate);f.writeframes(audio.tobytes())
  subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','4',str(out)],check=True)
 sounddefs[name]={'sounds':[{'name':'realmmarriage:ceremony/'+name,'stream':False}],'subtitle':'marriage.sound.'+name}
write(ASSETS/'sounds.json',sounddefs)
# Keep the collision volumes reproducible from the model's major geometry.
code=ROOT.parents[0]/'java/pt/realmmarriage/block/WeddingAltarBlock.java'
s=code.read_text();start=s.index('        double[][] boxes = {');end=s.index('        VoxelShape[][] result',start);replacement='        double[][] boxes = {\n'+''.join('                {'+', '.join(map(str,b))+'},\n' for b in collision)+'        };\n';code.write_text(s[:start]+replacement+s[end:])
print('Generated original altar:',len(lower),len(upper),'elements, four stereo sounds, rings and parchment.')
