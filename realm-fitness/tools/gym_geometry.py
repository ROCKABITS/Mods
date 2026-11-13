"""Original industrial gym collection. Geometry is authored in Minecraft model pixels.

The supplied moodboards inform material contrast and believable proportions only.
No source image, texture, logo or mesh is sampled or traced.
"""
from dataclasses import dataclass
from pathlib import Path
import json, math, random
from PIL import Image, ImageDraw

PALETTE={
    'frame':(46,60,65), 'edge':(79,98,103), 'steel':(162,183,182),
    'rubber':(26,32,35), 'pad':(46,83,80), 'pad_edge':(74,113,106),
    'stitch':(179,166,185), 'copper':(171,119,69), 'plate':(32,43,48),
    'bag':(111,43,60), 'bag_edge':(144,67,79), 'screen':(13,32,35),
    'light':(113,217,180), 'red':(183,67,64), 'mesh':(49,68,70),
}

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n')

def textures(assets):
    folder=assets/'textures/block/gym';folder.mkdir(parents=True,exist_ok=True)
    for name,c in PALETTE.items():
        rng=random.Random('realm-fitness-collection-2:'+name)
        image=Image.new('RGB',(64,64),c);draw=ImageDraw.Draw(image)
        # Clustered pixel shading instead of per-pixel static/noise.
        for _ in range(95):
            x,y=rng.randrange(64),rng.randrange(64);w,h=rng.randrange(2,9),rng.randrange(1,5)
            d=rng.choice((-10,-7,-4,4,7,10))
            draw.rectangle((x,y,min(63,x+w),min(63,y+h)),fill=tuple(max(0,min(255,v+d)) for v in c))
        if name in ('frame','edge','steel','copper'):
            for yy in range(3,64,16):
                draw.line((1,yy,62,yy),fill=tuple(min(255,v+19) for v in c))
                draw.line((1,yy+1,62,yy+1),fill=tuple(max(0,v-12) for v in c))
        if name in ('pad','bag'):
            for xx in (2,29,34,61):
                draw.line((xx,0,xx,63),fill=tuple(max(0,v-18) for v in c))
                for yy in range(2,64,5):draw.line((xx+1,yy,xx+1,yy+2),fill=tuple(min(255,v+36) for v in c))
        if name=='rubber':
            for yy in range(0,64,4):draw.rectangle((0,yy,63,yy+1),fill=(43,50,52))
        if name=='mesh':
            for xx in range(0,64,4):
                for yy in range(0,64,4):draw.rectangle((xx,yy,xx+1,yy+1),fill=(16,26,29))
        if name=='screen':
            draw.rectangle((1,1,62,62),outline=(43,72,71),width=2)
            for x in range(7,58,9):
                draw.rectangle((x,35-rng.randrange(4,23),x+4,47),fill=(103,194,162))
            draw.line((7,53,55,53),fill=(97,164,153),width=2)
            draw.text((8,7),'RF / 02',fill=(172,217,200))
        image.save(folder/(name+'.png'))
    # Original end-cap plate art: concentric octagonal rims, grip recesses and kg label.
    im=Image.new('RGB',(64,64),PALETTE['plate']);d=ImageDraw.Draw(im)
    for inset,color in [(1,(73,91,94)),(4,(37,49,55)),(8,(59,76,78)),(10,(28,39,44))]:
        points=[(16+inset//2,inset),(47-inset//2,inset),(63-inset,16+inset//2),(63-inset,47-inset//2),(47-inset//2,63-inset),(16+inset//2,63-inset),(inset,47-inset//2),(inset,16+inset//2)]
        d.polygon(points,fill=color)
    for box in [(10,24,15,40),(49,24,54,40)]:d.rounded_rectangle(box,radius=2,fill=(13,24,27))
    d.rectangle((26,26,38,38),fill=(133,151,148));d.rectangle((29,29,35,35),fill=(24,35,39))
    d.text((24,13),'20',fill=(177,187,179));d.text((25,44),'RF',fill=(131,151,145));im.save(folder/'plate_face.png')

@dataclass
class Part:
    lo:tuple
    hi:tuple
    material:str
    rotation:dict|None=None
    solid:bool=True
    face:str|None=None
    motion:str=""

def box(x,y,z,X,Y,Z,material='frame',solid=True,rotation=None,face=None):return Part((x,y,z),(X,Y,Z),material,rotation,solid,face)
def rot(x,y,z,axis,angle):return {'origin':[x,y,z],'axis':axis,'angle':angle,'rescale':False}

def cushion(x,y,z,w,h,d):
    # Stepped upholstered bevel, raised seam and a small piping edge.
    return [box(x,y,z,x+w,y+h-1,z+d,'pad_edge'),box(x+1,y+h-1,z+1,x+w-1,y+h,z+d-1,'pad'),
            box(x+1,y+h+.02,z+1,x+w-1,y+h+.12,z+1.25,'stitch',False),
            box(x+1,y+h+.02,z+d-1.25,x+w-1,y+h+.12,z+d-1,'stitch',False)]

def feet(width,depth):
    p=[]
    for x in (2,width-6):
        p += [box(x,0,2,x+4,1,8,'rubber'),box(x,0,depth-8,x+4,1,depth-2,'rubber'),
              box(x+.4,1,3,x+3.6,3,depth-3,'frame'),box(x+.36,2.65,2.96,x+3.64,3.04,depth-2.96,'edge',False)]
    return p

def fasteners(parts,positions):
    for x,y,z in positions:
        parts.extend([box(x-.6,y-.6,z-.15,x+.6,y+.6,z+.2,'steel',False),box(x-.25,y-.1,z-.2,x+.25,y+.1,z-.16,'rubber',False)])

def plate(x,y,z,thickness=2.2):
    # Seven non-overlapping bands form an octagonal weight disc on the X axle.
    parts=[]
    bands=[(-6,-5,2.5),(-5,-3,4.5),(-3,3,6),(3,5,4.5),(5,6,2.5)]
    for low,high,r in bands:parts.append(box(x,y+low,z-r,x+thickness,y+high,z+r,'plate',True))
    # Individually mapped face planes make the printed rim continuous across the bands.
    for side in (x-.03,x+thickness+.03):
        for low,high,r in bands:
            parts.append(box(side,y+low,z-r,side+.012,y+high,z+r,'plate_face',False,face=f'disc:{y}:{z}'))
    parts.append(box(x-.3,y-1.2,z-1.2,x+thickness+.3,y+1.2,z+1.2,'steel'))
    return parts

def moving(parts,group):
    for part in parts:part.motion=group
    return parts

def bench():
    p=feet(32,48)
    p += [box(9,6,10,23,7.3,15,'rubber'),box(13,3,5,19,6,43),box(13.05,5,9,18.95,11,13,'steel'),box(13.05,5,34,18.95,12,38,'steel')]
    p += cushion(9,11,4,14,4,12)+cushion(9,12,18,14,4,25)
    # A separate raised head pad and back spine give this bench its own silhouette.
    p += [box(10,16,35,22,18,43,'pad_edge',rotation=rot(16,16,35,'x',-22.5)),box(11,18,36,21,18.3,42,'pad',False,rot(16,16,35,'x',-22.5))]
    for x in (4,25):
        p += [box(x,3,35,x+3,31,39),box(x-.3,28,34.5,x+3.3,29,39.5,'edge'),box(x+.9,7,34.8,x+2.1,26,35,'rubber',False)]
        for yy in range(10,27,4):p.append(box(x+.9,yy,34.6,x+2.1,yy+1,34.85,'steel',False))
    p += [box(3,27,33,9,29,37,'steel'),box(23,27,33,29,29,37,'steel')]
    p += moving([box(1,29.5,35,31,31,36.5,'steel'),box(11,29.45,34.9,21,31.05,36.6,'rubber')]+plate(.4,30.25,35.75,2.4)+plate(29.2,30.25,35.75,2.4),'bar')
    fasteners(p,[(5.5,8,34.7),(26.5,8,34.7),(5.5,27,34.7),(26.5,27,34.7)])
    return p

def treadmill():
    p=feet(32,48)
    p += [box(2,2,1,30,5,47),box(6,5,4,26,6.2,43,'rubber'),box(1.97,3.98,1.98,6.02,7,46.02,'steel'),box(25.98,3.98,1.98,30.03,7,46.02,'steel')]
    for zz in range(6,43,4):p.append(box(6,6.21,zz,26,6.28,zz+.5,'edge',False))
    for x in (2.4,26.4):
        p += [box(x,6,36,x+3,28,39),box(x-.2,25,19,x+3.2,27,38,'edge'),box(x-.35,24.8,18,x+3.35,27.2,28,'rubber')]
    p += [box(3,27,34,29,31,44,'frame'),box(4,31,34,28,33,43,'edge'),box(8,28,33.65,24,30.5,33.9,'screen',False),box(5,28.3,33.6,7,30,33.95,'copper',False),box(25,28.3,33.6,27,30,33.95,'red',False),box(8,33.02,35,24,33.1,42,'screen',False)]
    p += [box(5,5,1.5,27,6.5,3,'rubber'),box(7,6.5,2,9,6.7,3,'copper',False),box(23,6.5,2,25,6.7,3,'copper',False),box(3,2,1,29,4,1.2,'mesh',False)]
    fasteners(p,[(3,5,1),(29,5,1),(4,25,35.8),(28,25,35.8)])
    return p

def punching_bag():
    p=feet(32,32)
    p += [box(4,2,24,28,5,29),box(24,4,23,28,43,28),box(10,40,9,28,44,27),box(12,43.7,10,27,44,27,'edge',False)]
    for y in range(6,26,2): # Gusset made of stepped metal, distinct from the moodboard's A-frame.
        z=22-(y-6)*.45;p.append(box(24,y,z,27,y+2,z+4,'edge'))
    for y in range(35,41,2):p.append(box(14,y,13,16,y+1.4,15,'steel'))
    p += [box(10,12,8,20,34,20,'bag'),box(8,14,10,22,32,18,'bag'),box(9,13,9,21,33,19,'bag'),box(10,11,10,20,12,18,'bag_edge'),box(10,34,10,20,35,18,'bag_edge')]
    for yy in (15,30):p += [box(8.8,yy,8.8,21.2,yy+1,19.2,'bag_edge',False)]
    p += [box(12,23,7.85,18,28,8,'rubber',False),box(13,24,7.7,17,27,7.85,'stitch',False),box(13,25,7.5,17,26,7.7,'bag_edge',False)]
    fasteners(p,[(25,8,22.8),(25,36,22.8),(22,42,8.8)])
    return p

def pullup():
    p=feet(32,32)
    for x in (2,27):
        p += [box(x,3,21,x+3,43,25),box(x-.2,10,20.8,x+3.2,12,25.2,'steel'),box(x-.2,35,20.8,x+3.2,37,25.2,'steel'),box(x,39,7,x+3,43,24)]
    p += [box(3,39,6,29,41.5,9,'steel'),box(4,38.8,5.8,11,41.7,9.2,'rubber'),box(21,38.8,5.8,28,41.7,9.2,'rubber'),box(5,35,22,27,38,25),box(12,35.1,21.8,20,37.8,22,'copper',False),box(8,4,10,24,7,18,'edge'),box(9,7,11,23,7.5,17,'rubber')]
    for x in (3,28):
        for yy in range(16,33,4):p.append(box(x,yy,20.8,x+1,yy+1,21,'copper',False))
    fasteners(p,[(3.5,6,20.8),(28.5,6,20.8),(3.5,37.5,20.8),(28.5,37.5,20.8)])
    return p

def rower():
    p=feet(32,48)
    p += [box(12,3,3,20,6,43,'edge'),box(15,6,4,17,7.2,30,'steel')]
    p += moving([box(10,7,6,22,9,14,'frame')]+cushion(9,9,5,14,4,11),'seat')
    p += [box(5,5,28,27,9,45),box(7,9,30,25,22,43,'frame'),box(10,22,32,22,24,41,'edge'),box(8,11,29.8,24,20,30,'mesh',False),box(9,11,43,23,21,43.2,'mesh',False)]
    for x in (9,18):p += [box(x,7,22,x+5,10,29,'rubber',True,rot(x+2.5,8,25,'x',-22.5)),box(x,10.1,24,x+5,10.5,25,'copper',False,rot(x+2.5,8,25,'x',-22.5))]
    p += moving([box(15.5,13,14,16.5,14,34,'steel',False)],'cable')
    p += moving([box(8,12.4,13,24,14.6,15,'steel'),box(8,12,12.6,13,15,15.4,'rubber'),box(19,12,12.6,24,15,15.4,'rubber')],'handle')
    p += [box(15,21,36,17,32,39,'edge'),box(10,30,34,22,35,40),box(11,30.8,33.8,21,34,34,'screen',False)]
    fasteners(p,[(6,6,27.8),(26,6,27.8),(8,17,29.7),(24,17,29.7)])
    return p

def squat():
    p=feet(32,32)
    for x in (3,26):
        p += [box(x,3,19,x+3,44,23),box(x-.4,40,18.6,x+3.4,42,23.4,'edge'),box(x+.8,9,18.8,x+2.2,35,19,'rubber',False)]
        for yy in range(10,36,3):p.append(box(x+.8,yy,18.6,x+2.2,yy+1,18.85,'steel',False))
        p += [box(x,14,4,x+3,17,22,'steel'),box(x-.3,13.97,3.97,x+3.3,17.3,11,'rubber')]
    p += [box(4,41,20,28,44,23),box(5,37,18.9,27,39,19.1,'copper',False),box(2,29,17,9,31,22,'edge'),box(23,29,17,30,31,22,'edge'),box(7,1,5,25,2.4,19,'rubber')]
    p += moving([box(0,31.1,18.3,32,32.5,19.7,'steel'),box(11,31,18.2,21,32.6,19.8,'rubber')]+plate(.4,31.8,19,2.4)+plate(29.2,31.8,19,2.4),'bar')
    fasteners(p,[(4.5,6,18.8),(27.5,6,18.8),(4.5,39,18.8),(27.5,39,18.8)])
    return p

BUILDERS={'bench':bench,'run':treadmill,'box':punching_bag,'pull':pullup,'row':rower,'squat':squat}
SIZES={'bench':(2,3,3),'run':(2,3,3),'box':(2,2,3),'pull':(2,2,3),'row':(2,3,3),'squat':(2,2,3)}

def element(part,base=(0,0,0),scale=1):
    lo=[(v-b)*scale for v,b in zip(part.lo,base)];hi=[(v-b)*scale for v,b in zip(part.hi,base)]
    faces={}
    for face in ('north','south','east','west','up','down'):
        a,b=({'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)})[face]
        u=(part.lo[a]%32)/4;v=(part.lo[b]%32)/4;du=(part.hi[a]-part.lo[a])/4;dv=(part.hi[b]-part.lo[b])/4
        if u+du>16:u=0
        if v+dv>16:v=0
        uv=[u,v,min(16,u+du),min(16,v+dv)]
        if part.material in ('screen',):uv=[0,0,16,16]
        if part.face and part.face.startswith('disc:') and face in ('east','west'):
            _,cy,cz=part.face.split(':');cy,cz=float(cy),float(cz)
            uv=[(part.lo[2]-cz+6)/12*16,(cy+6-part.hi[1])/12*16,(part.hi[2]-cz+6)/12*16,(cy+6-part.lo[1])/12*16]
        faces[face]={'texture':'#'+part.material,'uv':uv}
    result={'from':lo,'to':hi,'faces':faces}
    if part.rotation:result['rotation']={**part.rotation,'origin':[(v-b)*scale for v,b in zip(part.rotation['origin'],base)]}
    return result

def model(elements):return {'ambientocclusion':True,'textures':{**{m:'realmfitness:block/gym/'+m for m in [*PALETTE,'plate_face']},'particle':'realmfitness:block/gym/frame'},'elements':elements}

def bounds(part):
    if not part.rotation:return list(part.lo),list(part.hi)
    axis='xyz'.index(part.rotation['axis']);o=part.rotation['origin'];a=math.radians(part.rotation['angle']);c,s=math.cos(a),math.sin(a);corners=[]
    for x in (part.lo[0],part.hi[0]):
      for y in (part.lo[1],part.hi[1]):
       for z in (part.lo[2],part.hi[2]):
        p=[x-o[0],y-o[1],z-o[2]];i,j=[n for n in range(3) if n!=axis];p[i],p[j]=p[i]*c-p[j]*s,p[i]*s+p[j]*c;corners.append([p[n]+o[n] for n in range(3)])
    return [min(p[n] for p in corners) for n in range(3)],[max(p[n] for p in corners) for n in range(3)]

def build_machines(assets,root):
    textures(assets);collisions={};active_collisions={};manifest={}
    for name,builder in BUILDERS.items():
        parts=builder();w,d,h=SIZES[name];manifest[name]={'width':w,'depth':d,'height':h,'parts':len(parts)}
        cells={i:[] for i in range(18)};active_cells={i:[] for i in range(18)};collision={i:[] for i in range(18)};active_collision={i:[] for i in range(18)}
        for group in sorted({p.motion for p in parts if p.motion}):write(assets/f"models/block/{name}_{group}.json",model([element(p,scale=.5) for p in parts if p.motion==group]))
        for part in parts:
            # Rotated cushions belong to their pivot cell and can overhang its visual boundary.
            if part.rotation:
                xx=min(w-1,max(0,int(part.rotation['origin'][0]//16)));zz=min(d-1,max(0,int(part.rotation['origin'][2]//16)));yy=min(h-1,max(0,int(part.rotation['origin'][1]//16)))
                cell=xx+zz*2+yy*6;value=element(part,(xx*16,yy*16,zz*16));cells[cell].append(value)
                if not part.motion:active_cells[cell].append(value)
            else:
                for yy in range(h):
                  for zz in range(d):
                   for xx in range(w):
                    base=(xx*16,yy*16,zz*16);lo=tuple(max(a,b) for a,b in zip(part.lo,base));hi=tuple(min(a,b+16) for a,b in zip(part.hi,base))
                    if all(a<b for a,b in zip(lo,hi)):
                        cell=xx+zz*2+yy*6;value=element(Part(lo,hi,part.material,solid=part.solid,face=part.face),base);cells[cell].append(value)
                        if not part.motion:active_cells[cell].append(value)
            if part.solid:
                lo,hi=bounds(part)
                for yy in range(h):
                  for zz in range(d):
                   for xx in range(w):
                    base=(xx*16,yy*16,zz*16);a=[max(v,b)-b for v,b in zip(lo,base)];b=[min(v,q+16)-q for v,q in zip(hi,base)]
                    if all(v<u for v,u in zip(a,b)):
                        cell=xx+zz*2+yy*6;collision[cell].append(a+b)
                        if not part.motion:active_collision[cell].append(a+b)
        for i,items in cells.items():
            write(assets/f'models/block/{name}_part_{i}.json',model(items));write(assets/f'models/block/{name}_part_{i}_working.json',model(active_cells[i]))
        # Existing 0.1 worlds keep their original two-block footprint until recolocated.
        for half,label in [(0,'lower'),(1,'upper')]:
            items=[]
            for part in parts:
                compact=(.48,.64,16/(d*16+.5));lo=tuple(v*q for v,q in zip(part.lo,compact));hi=tuple(v*q for v,q in zip(part.hi,compact))
                a=max(lo[1],half*16);b=min(hi[1],(half+1)*16)
                if a<b:
                    lp=Part((lo[0],a,lo[2]),(hi[0],b,hi[2]),part.material)
                    items.append(element(lp,(0,half*16,0)))
            write(assets/f'models/block/{name}_{label}.json',model(items))
        item=model([element(p,scale=.5) for p in parts]);item['display']={'gui':{'rotation':[24,225,0],'translation':[0,-2,0],'scale':[.64,.64,.64]},'ground':{'scale':[.3,.3,.3]},'fixed':{'scale':[.6,.6,.6]},'thirdperson_righthand':{'rotation':[75,45,0],'scale':[.3,.3,.3]},'firstperson_righthand':{'rotation':[0,45,0],'scale':[.3,.3,.3]},'firstperson_lefthand':{'rotation':[0,225,0],'scale':[.3,.3,.3]}}
        write(assets/f'models/item/{name}.json',item)
        multipart=[]
        for f,angle in [('north',0),('east',90),('south',180),('west',270)]:
            for half in ('lower','upper'):multipart.append({'when':{'expanded':'false','facing':f,'half':half},'apply':{'model':f'realmfitness:block/{name}_{half}','y':angle}})
            for i in range(18):
                for working in (False,True):multipart.append({'when':{'expanded':'true','facing':f,'part':str(i),'working':str(working).lower()},'apply':{'model':f'realmfitness:block/{name}_part_{i}'+('_working' if working else ''),'y':angle}})
        write(assets/f'blockstates/{name}.json',{'multipart':multipart})
        # A single anchor owns the drop in both layouts.
        write(root/f'data/realmfitness/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'realmfitness:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'realmfitness:'+name,'properties':{'half':'lower','part':'0'}}]}]})
        collisions[name]=collision;active_collisions[name]=active_collision
    write(assets/'geometry/collisions.json',collisions);write(assets/'geometry/collisions_working.json',active_collisions);write(assets/'geometry/collection.json',manifest)
    return manifest
