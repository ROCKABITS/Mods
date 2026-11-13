"""Original industrial gym collection. Geometry is authored in Minecraft model pixels.

The supplied moodboards inform material contrast and believable proportions only.
No source image, texture, logo or mesh is sampled or traced.
"""
from dataclasses import dataclass
from pathlib import Path
import json, math, random
from PIL import Image, ImageDraw

PALETTE={
    'frame':(45,47,65), 'edge':(77,80,99), 'steel':(153,166,174),
    'rubber':(23,25,32), 'pad':(87,91,114), 'pad_edge':(55,58,79),
    'stitch':(129,136,155), 'copper':(125,140,151), 'plate':(29,31,42),
    'bag':(118,43,63), 'bag_edge':(155,64,81), 'screen':(18,22,31),
    'light':(172,209,219), 'red':(183,67,64), 'mesh':(55,59,75),
}

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'))+'\n')

def textures(assets):
    folder=assets/'textures/block/gym';folder.mkdir(parents=True,exist_ok=True)
    for name,c in PALETTE.items():
        rng=random.Random('realm-fitness-collection-3:'+name)
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
            for yy in range(0,64,4):draw.rectangle((0,yy,63,yy+1),fill=(37,39,49))
        if name=='mesh':
            for xx in range(0,64,4):
                for yy in range(0,64,4):draw.rectangle((xx,yy,xx+1,yy+1),fill=(19,21,29))
        if name=='screen':
            draw.rectangle((1,1,62,62),outline=(43,72,71),width=2)
            for x in range(7,58,9):
                draw.rectangle((x,35-rng.randrange(4,23),x+4,47),fill=(204,100,98))
            draw.line((7,53,55,53),fill=(129,153,179),width=2)
            draw.text((8,7),'RF / 03',fill=(185,202,216))
        image.save(folder/(name+'.png'))
    # Original end-cap plate art: concentric octagonal rims, grip recesses and kg label.
    im=Image.new('RGB',(64,64),PALETTE['plate']);d=ImageDraw.Draw(im)
    for inset,color in [(1,(74,78,94)),(4,(36,38,51)),(8,(57,61,76)),(10,(26,28,39))]:
        points=[(16+inset//2,inset),(47-inset//2,inset),(63-inset,16+inset//2),(63-inset,47-inset//2),(47-inset//2,63-inset),(16+inset//2,63-inset),(inset,47-inset//2),(inset,16+inset//2)]
        d.polygon(points,fill=color)
    for box in [(10,24,15,40),(49,24,54,40)]:d.rounded_rectangle(box,radius=2,fill=(13,24,27))
    d.rectangle((26,26,38,38),fill=(133,151,148));d.rectangle((29,29,35,35),fill=(24,35,39))
    d.text((25,13),'KG',fill=(164,175,190));d.text((25,44),'RF',fill=(131,145,167));im.save(folder/'plate_face.png')

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
    # One continuous, thin pad with an inset pixel border and raised piping.
    return [box(x,y,z,x+w,y+h,z+d,'pad_edge'),
            box(x+.65,y+h-.15,z+.65,x+w-.65,y+h+.02,z+d-.65,'pad'),
            box(x+.9,y+h+.025,z+.9,x+w-.9,y+h+.07,z+1.12,'stitch',False),
            box(x+.9,y+h+.025,z+d-1.12,x+w-.9,y+h+.07,z+d-.9,'stitch',False)]

def feet(width,depth):
    p=[]
    for x in (3,width-6):
        p += [box(x-.4,0,2,x+3.4,.7,7,'rubber'),box(x-.4,0,depth-7,x+3.4,.7,depth-2,'rubber'),
              box(x,.7,3,x+3,2.2,depth-3),box(x+.2,2.2,3.2,x+2.8,2.4,depth-3.2,'edge',False)]
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

# Contact points in model pixels, consumed by both the mounted entity and the IK rig.
LAYOUT={
 'bench':{'actor':[16,0,10],'pad_top':7,'bar_rest':[16,20.5,30],'bar_work_z':21,'grip_half':6.72,'foot_forward':4.8,'foot_half':3.84,'foot_y':.32},
 'run':{'actor':[16,4.25,20],'belt_top':4.25,'belt_min_z':4,'belt_max_z':43,'stride':4.48},
 'box':{'actor':[15,0,-2],'bag_pivot':[15,40,14],'bag_face_z':7,'punch_y':24},
 'pull':{'actor':[16,8,9],'bar_y':40.5,'bar_z':9,'grip_half':7.5},
 'row':{'actor':[16,0,13],'seat_top':10.5,'seat_stroke':3,'seat_offset':3.2,'hand_rest':[16,14,18],'cable_origin':[16,14,34],'flywheel_center':[16,14,35],'foot_y':7.7,'foot_z':23,'foot_half':4.6},
 'squat':{'actor':[16,1.2,21.5],'bar_rest':[16,27,22],'grip_half':8,'foot_half':4.4,'foot_forward':1.2}
}

def bench():
    p=[]
    for z in (10,30):
        p += [box(8,0,z-1,24,.7,z+2,'rubber'),box(9,.7,z-1,23,2,z+2,'steel'),box(14,2,z,18,4.5,z+2,'frame')]
    p += [box(14,3.5,8,18,5,34)]+cushion(10.5,4.5,8,11,2.5,26)
    for x in (4,26):
        p += [box(x-2,0,25,x+4,.7,37,'rubber'),box(x-1,.7,26,x+3,2,36,'steel'),box(x,2,30.5,x+2,24,33),
              box(x-.2,23.5,30.3,x+2.2,24.3,33.2,'edge'),box(x,18.8,28.2,x+2,20,32,'steel'),box(x,20,28.2,x+2,21.8,29,'steel')]
        for y in range(8,19,3):p.append(box(x+.7,y,30.28,x+1.3,y+.6,30.48,'rubber',False))
    p += [box(5,2,32,27,3.5,34)]
    p += moving([box(.2,20,29.5,31.8,21,30.5,'steel'),box(9,19.95,29.4,23,21.05,30.6,'edge')]+plate(.5,20.5,30,2.2)+plate(29.3,20.5,30,2.2),'bar')
    fasteners(p,[(5,4,30.3),(27,4,30.3),(5,22.5,30.3),(27,22.5,30.3)])
    return p

def treadmill():
    p=[box(2,0,2,30,1,45,'rubber'),box(2,1,1,30,3.5,46),box(5,3.5,3,27,4.25,44,'rubber'),
       box(2,3.5,2,5,5,45,'edge'),box(27,3.5,2,30,5,45,'edge'),box(3,2,1,29,3,1.2,'mesh',False)]
    for z in range(4,43,3):p+=moving([box(5.1,4.26,z,26.9,4.3,z+.35,'edge',False)],'belt')
    for x in (3,27):
        p += [box(x,4,38,x+2,31,40),box(x-.2,27,21,x+2.2,29,39,'edge'),box(x-.3,27,20,x+2.3,29,29,'rubber')]
    p += [box(3,29,37,29,33,42),box(4,33,37.5,28,33.4,41.5,'edge',False),box(8,29.8,36.8,24,32.2,37,'screen',False),
          box(5,30,36.7,6.5,31.5,37,'red',False),box(25.5,30,36.7,27,31.5,37,'light',False),box(6,4,1.5,26,5,3,'rubber')]
    fasteners(p,[(4,6,37.8),(28,6,37.8),(4,26,37.8),(28,26,37.8)])
    return p

def punching_bag():
    p=feet(32,32)+[box(5,2,25,27,4,28),box(4,22,25,27,24,28),box(14,3,25,17,43,28),box(14,40,12,17,43,28),box(14,43,12,17,43.3,28,'edge',False)]
    # Two diagonal tubes form a slim braced stand, with a suspended octagonal bag.
    for x in (4,25):
        p += [box(x,2,4,x+2,31.8,6,'edge',rotation=rot(x+1,2,5,'x',45))]
    bag=[]
    for y in range(35,40):bag.append(box(14.5,y,13.5,15.5,y+.65,14.5,'steel',False))
    bag += [box(10,12,7,20,34,21,'bag'),box(8,14,9,22,32,19,'bag'),box(9,13,8,21,33,20,'bag'),
            box(10,11,9,20,12,19,'bag_edge'),box(10,34,9,20,35,19,'rubber')]
    for y in (14,31):bag += [box(9.9,y,6.9,20.1,y+.7,21.1,'bag_edge',False),box(7.9,y,9,22.1,y+.7,19,'bag_edge',False)]
    bag += [box(12,22,6.8,18,28,7,'rubber',False),box(13,23,6.65,17,27,6.8,'bag_edge',False),box(14,24,6.5,16,26,6.65,'stitch',False)]
    p+=moving(bag,'bag');fasteners(p,[(15.5,5,24.8),(15.5,37,24.8),(15.5,41.5,11.8)])
    return p

def pullup():
    p=feet(32,32)
    for x in (4,26):
        p += [box(x,2,25,x+2,44,27),box(x-.15,43.5,24.85,x+2.15,44.3,27.15,'edge'),box(x,39.5,8,x+2,42,27),
              box(x-.15,8,24.85,x+2.15,9,27.15,'steel'),box(x-.15,34,24.85,x+2.15,35,27.15,'steel')]
    p += [box(5,39.5,8,27,41.5,10,'steel'),box(5,39.35,7.85,11,41.65,10.15,'rubber'),box(21,39.35,7.85,27,41.65,10.15,'rubber'),
          box(6,35,25,26,37,27),box(11,35.4,24.8,21,36.6,25,'edge',False),box(8,2,9,24,3,18,'edge'),box(9,3,10,23,3.4,17,'rubber')]
    fasteners(p,[(5,5,24.8),(27,5,24.8),(5,38,24.8),(27,38,24.8)])
    return p

def rower():
    p=[]
    for z in (5,40):p += [box(4,0,z-1,28,.7,z+2,'rubber'),box(5,.7,z-1,27,2,z+2,'steel')]
    p += [box(13,2,4,19,6,42),box(14,6,4,18,7,31,'steel')]
    p += moving([box(11,7,9,21,8.5,17)]+cushion(10,8.5,8,12,2,10),'seat')
    # Octagonal flywheel housing, grille bands and separate moving spokes.
    for low,high,w in [(4,6,5),(6,9,8),(9,19,10),(19,22,8),(22,24,5)]:
        p.append(box(16-w,low,33,16+w,high,42,'frame'))
    p += [box(7,8,32.7,25,20,33,'mesh',False),box(10,5.7,32.6,22,7,33,'edge',False),box(10,21,32.6,22,22.3,33,'edge',False)]
    fan=[box(15.3,7,32.4,16.7,21,32.55,'steel',False),box(9,13.3,32.4,23,14.7,32.55,'steel',False),box(14,12,32.2,18,16,32.6,'rubber',False)]
    p += moving(fan,'flywheel')
    for x in (8.5,19):p += [box(x,5,21,x+4.5,7.7,27,'rubber'),box(x,7.75,23,x+4.5,8.2,24,'edge',False)]
    p += moving([box(15.65,13.65,18,16.35,14.35,34,'steel',False)],'cable')
    p += moving([box(9,13.4,17.4,23,14.6,18.6,'steel'),box(8.5,13,17,13,15,19,'rubber'),box(19,13,17,23.5,15,19,'rubber')],'handle')
    p += [box(15,23,37,17,29,39,'edge'),box(11,28,35,21,32,39),box(11.5,28.5,34.8,20.5,31.5,35,'screen',False)]
    fasteners(p,[(7,4,39),(25,4,39),(7.5,14,32.6),(24.5,14,32.6)])
    return p

def squat():
    p=feet(32,32)
    for x in (4,26):
        p += [box(x,2,24,x+2,44,27),box(x-.15,43,23.85,x+2.15,44.2,27.15,'edge'),box(x+.7,8,23.8,x+1.3,37,24,'rubber',False),
              box(x,12,7,x+2,14,25,'steel'),box(x-.2,12,6.5,x+2.2,14.3,13,'rubber')]
        for y in range(9,38,3):p.append(box(x+.65,y,23.65,x+1.35,y+.65,23.85,'steel',False))
        p += [box(x-1,25,21,x+3,26.5,26,'steel'),box(x-1,26.5,20.8,x+3,28.5,21.5,'steel')]
    p += [box(5,41.5,25,27,44,27),box(9,42,24.8,23,43,25,'edge',False),box(8,0,6,24,.7,23,'rubber')]
    p += moving([box(.2,26.5,21.5,31.8,27.5,22.5,'steel'),box(11,26.4,21.4,21,27.6,22.6,'rubber')]+plate(.5,27,22,2.2)+plate(29.3,27,22,2.2),'bar')
    fasteners(p,[(5,5,23.8),(27,5,23.8),(5,39,23.8),(27,39,23.8)])
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
    write(assets/'geometry/layout.json',LAYOUT);write(assets/'geometry/collisions.json',collisions);write(assets/'geometry/collisions_working.json',active_collisions);write(assets/'geometry/collection.json',manifest)
    return manifest
