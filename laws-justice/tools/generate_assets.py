#!/usr/bin/env python3
"""Original deterministic Laws & Justice pixel textures and native 3D meshes.

All meshes, ornaments and pixels are authored here. No downloaded artwork.
Door geometry follows Minecraft DoorBlock's exact closed/open collision planes;
each rendered half stays inside its own 16-cubed block. Running this script twice
produces byte-identical resources. Pillow is the only development dependency.
"""
from copy import deepcopy
from pathlib import Path
import json
import math
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/lawsjustice'
TEX = ASSETS / 'textures/block'
TEX.mkdir(parents=True, exist_ok=True)
RNG = random.Random(18102026)
IRON = (72, 83, 87)
STEEL = (122, 133, 135)
GOLD = (177, 137, 71)
LIGHT = (230, 204, 145)
DARK = (32, 39, 43)
STONE = (100, 103, 102)
PAPER = (228, 210, 166)
WINE = (104, 39, 48)
INK = (66, 57, 45)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def shade(base, shift):
    return tuple(max(0, min(255, c + shift)) for c in base[:3]) + (255,)


def noise(size, base, amount=3):
    im = Image.new('RGBA', size)
    im.putdata([shade(base, RNG.randint(-amount, amount)) for _ in range(size[0]*size[1])])
    return im


def save(name, im):
    im.save(TEX / (name + '.png'))


def diamond(d, x, y, r, colour):
    d.polygon(((x,y-r),(x+r,y),(x,y+r),(x-r,y)), fill=colour)


def scales(d, cx, cy, s=1, gold=GOLD):
    """Hand-pixelled balance of justice; no text or third-party emblem."""
    def rect(a,b,c,e,fill=gold):
        d.rectangle((round(cx+a*s),round(cy+b*s),round(cx+c*s),round(cy+e*s)),fill=fill)
    rect(-1,-9,1,10)
    rect(-10,-5,10,-4)
    rect(-5,10,5,11)
    rect(-3,8,3,9)
    diamond(d, cx, cy-11*s, max(1,round(2*s)), LIGHT)
    for side in (-1,1):
        for xx in (-3,3):
            d.line((round(cx+side*8*s),round(cy-4*s),round(cx+(side*8+xx)*s),round(cy+3*s)),fill=gold,width=max(1,round(s)))
        rect(side*8-4,3,side*8+4,4)
        rect(side*8-3,5,side*8+3,5)
        rect(side*8-2,6,side*8+2,6)


# Restrained cold iron, burnished bronze, dressed limestone and dark oak.
for name, base in (('iron',IRON), ('iron_dark',DARK), ('steel',STEEL), ('bronze',GOLD)):
    im=noise((32,32),base,3);d=ImageDraw.Draw(im)
    for y in (0,15,31):
        d.line((0,y,31,y),fill=shade(base,-17))
        if y<31:d.line((0,y+1,31,y+1),fill=shade(base,13))
    for _ in range(14):
        x,y=RNG.randrange(31),RNG.randrange(2,30)
        d.line((x,y,min(31,x+RNG.randrange(2,7)),y),fill=shade(base,RNG.choice((-10,9))))
    save(name,im)

for name,base in (('stone',STONE),('stone_dark',(67,73,74))):
    im=noise((32,32),base,4);d=ImageDraw.Draw(im)
    d.rectangle((0,0,31,31),outline=shade(base,-23))
    d.line((1,1,30,1),fill=shade(base,20));d.line((1,1,1,30),fill=shade(base,10))
    for _ in range(28):
        x,y=RNG.randrange(2,30),RNG.randrange(2,30)
        d.line((x,y,min(30,x+2),y),fill=shade(base,RNG.choice((-15,13))))
    d.line((22,25,24,27,28,27),fill=shade(base,-17))
    save(name,im)

for name,base in (('oak',(72,49,37)),('oak_dark',(43,34,29))):
    im=noise((32,32),base,3);d=ImageDraw.Draw(im)
    for x in (0,10,21,31):
        d.line((x,0,x,31),fill=shade(base,-17))
        if x<31:d.line((x+1,1,x+1,30),fill=shade(base,11))
    for _ in range(27):
        x,y=RNG.randrange(2,30),RNG.randrange(30)
        d.line((x,y,x,min(31,y+RNG.randrange(2,8))),fill=shade(base,RNG.choice((-10,9))))
    d.arc((13,8,20,18),35,325,fill=shade(base,-18))
    save(name,im)

for name,base in (('velvet',WINE),('wax',(227,211,168)),('leather',(97,43,39)),('black',(19,25,28))):
    im=noise((16,16),base,2)
    if name=='velvet':
        d=ImageDraw.Draw(im)
        for y in range(1,16,3):
            for x in range(y%2,16,3):d.point((x,y),fill=shade(base,8))
    save(name,im)

im=noise((32,32),IRON,2);d=ImageDraw.Draw(im)
d.rectangle((0,0,31,31),outline=shade(DARK,-5))
d.line((1,1,30,1,30,30),fill=shade(STEEL,19),width=1)
d.rectangle((3,3,28,28),outline=shade(IRON,-12))
for x,y in ((5,5),(26,5),(5,26),(26,26)):
    d.rectangle((x-1,y-1,x+1,y+1),fill=shade(STEEL,10));d.point((x-1,y-1),fill=shade(STEEL,42));d.point((x+1,y+1),fill=shade(DARK,-2))
d.ellipse((12,9,19,16),fill=shade(DARK,-6));d.polygon(((15,14),(17,14),(19,24),(13,24)),fill=shade(DARK,-6))
d.line((12,10,13,9,18,9),fill=shade(IRON,-18))
save('lock_plate',im)

im=noise((32,32),(52,42,37),2);d=ImageDraw.Draw(im)
d.rectangle((1,1,30,30),outline=shade(GOLD,-43));d.rectangle((3,3,28,28),outline=GOLD)
d.rectangle((5,5,26,26),fill=WINE)
# A shield and simple judicial balance unify the controller, locks and icon.
d.polygon(((8,7),(24,7),(24,20),(16,27),(8,20)),fill=shade(GOLD,-15))
d.polygon(((9,8),(23,8),(23,19),(16,25),(9,19)),fill=(53,66,70))
scales(d,16,15,.54)
save('justice_crest',im)

im=noise((64,32),PAPER,2);d=ImageDraw.Draw(im)
d.line((31,0,31,31),fill=(149,119,77));d.line((32,1,32,31),fill=(247,233,196))
for x in (2,34):
    d.rectangle((x,2,x+27,29),outline=(190,164,116))
    for y in (8,12,16,20,24):
        d.line((x+4,y,x+21-RNG.randrange(5),y),fill=(121,102,75))
    d.line((x+4,5,x+16,5),fill=WINE,width=2)
# Wax seal, corner turns, and hand-pixelled flourish, never an unreadable font.
d.ellipse((50,21,58,29),fill=WINE,outline=(160,78,68))
d.line((53,24,55,22,57,24,55,27,53,24),fill=GOLD)
d.polygon(((3,2),(9,2),(3,8)),fill=(245,231,196))
save('ledger_pages',im)

im=noise((32,16),(95,44,39),2);d=ImageDraw.Draw(im)
d.rectangle((1,1,30,14),outline=shade(GOLD,-20));d.rectangle((3,3,28,12),outline=GOLD)
for x in (6,25):diamond(d,x,8,2,LIGHT)
d.line((12,5,20,5,20,11,12,11,12,5),fill=GOLD)
save('ledger_cover',im)

im=noise((32,32),(49,58,62),2);d=ImageDraw.Draw(im)
d.rectangle((1,1,30,30),outline=GOLD);d.rectangle((4,4,27,27),outline=shade(GOLD,-50))
d.line((16,5,16,26),fill=PAPER);d.line((5,16,26,16),fill=PAPER)
d.polygon(((16,5),(20,17),(16,14),(12,17)),fill=GOLD)
d.polygon(((16,26),(12,16),(16,18),(20,16)),fill=(163,75,68))
d.ellipse((13,13,19,19),fill=LIGHT,outline=GOLD)
for x,y in ((7,7),(24,7),(7,24),(24,24)):diamond(d,x,y,1,LIGHT)
save('surveyor_dial',im)

im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
d.polygon(((7,0),(11,6),(12,11),(9,15),(5,14),(3,10),(5,5)),fill=(242,165,61))
d.polygon(((7,4),(9,8),(9,12),(7,14),(5,11),(6,8)),fill=(255,219,113))
d.rectangle((7,10,8,13),fill=(255,242,174))
save('flame',im)

# Keep the original model dictionaries stable when later versions add textures.
BASE_TEXTURES=('black','bronze','flame','iron','iron_dark','justice_crest',
               'leather','ledger_cover','ledger_pages','lock_plate','oak',
               'oak_dark','steel','stone','stone_dark','surveyor_dial','velvet','wax')
TEXTURES={name:'lawsjustice:block/'+name for name in BASE_TEXTURES}
TEXTURES['particle']='lawsjustice:block/iron'
ELEMENTS=[]
COUNTS={}


def box(start,end,texture,name,**overrides):
    assert all(start[i]<end[i] for i in range(3)),name
    dx,dy,dz=(end[i]-start[i] for i in range(3))
    faces={}
    for face in ('north','south','east','west','up','down'):
        w,h=(dx,dz) if face in ('up','down') else (dx if face in ('north','south') else dz,dy)
        key=overrides.get(face,texture)
        full=face in overrides or overrides.get('full',False)
        faces[face]={'uv':[0,0,16 if full else min(16,w),16 if full else min(16,h)],'texture':'#'+key}
    e={'name':name,'from':[round(v,5) for v in start],'to':[round(v,5) for v in end],'faces':faces}
    ELEMENTS.append(e)
    return e


def model(elements):
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':True,'textures':TEXTURES,'elements':elements}


def check_bounds(elements,limit=16):
    for e in elements:
        assert all(0<=v<=limit for p in (e['from'],e['to']) for v in p),e['name']
        assert all(0<=v<=16 for f in e['faces'].values() for v in f['uv']),e['name']


def displays(tall=False):
    scale=.47 if tall else .75
    return {
        'gui':{'rotation':[28,225,0],'translation':[0,-3.7 if tall else 0,0],'scale':[scale]*3},
        'ground':{'translation':[0,1,0],'scale':[.22 if tall else .38]*3},
        'fixed':{'rotation':[0,180,0],'translation':[0,-4 if tall else 0,0],'scale':[scale]*3},
        'firstperson_righthand':{'rotation':[0,35,0],'translation':[0,-2,0],'scale':[.33 if tall else .55]*3},
        'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,-2,0],'scale':[.33 if tall else .55]*3},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,1.5,0],'scale':[.27 if tall else .42]*3},
        'thirdperson_lefthand':{'rotation':[75,225,0],'translation':[0,1.5,0],'scale':[.27 if tall else .42]*3}}


def item(name,elements,tall=False):
    value=model(deepcopy(elements));value['display']=displays(tall)
    write(ASSETS/f'models/item/{name}.json',value)


def loot(name,door=False):
    conditions=[{'condition':'minecraft:survives_explosion'}]
    if door:conditions.insert(0,{'condition':'minecraft:block_state_property','block':'lawsjustice:'+name,'properties':{'half':'lower'}})
    write(RES/f'data/lawsjustice/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'lawsjustice:'+name}],'conditions':conditions}]})


def publish(name,elements):
    check_bounds(elements)
    write(ASSETS/f'models/block/{name}.json',model(elements))
    write(ASSETS/f'blockstates/{name}.json',{'variants':{f'facing={f}':{'model':f'lawsjustice:block/{name}',**({'y':a} if a else {})} for f,a in (('north',0),('east',90),('south',180),('west',270))}})
    item(name,elements);loot(name);COUNTS[name]=len(elements)


def split(elements,minimum):
    out=[]
    for old in elements:
        lo=max(minimum,old['from'][1]);hi=min(minimum+16,old['to'][1])
        if hi<=lo:continue
        e=deepcopy(old);e['from'][1]=round(lo-minimum,5);e['to'][1]=round(hi-minimum,5)
        for side in ('north','south','east','west'):
            uv=old['faces'][side]['uv'];height=old['to'][1]-old['from'][1]
            top=(old['to'][1]-hi)/height;bottom=(old['to'][1]-lo)/height
            e['faces'][side]['uv']=[uv[0],round(uv[1]+(uv[3]-uv[1])*top,5),uv[2],round(uv[1]+(uv[3]-uv[1])*bottom,5)]
        if lo>old['from'][1]:e['faces'].pop('down',None)
        if hi<old['to'][1]:e['faces'].pop('up',None)
        check_bounds([e]);out.append(e)
    return out


def transform(elements,fun,normal_fun):
    """Bake a rigid quarter-turn/mirror, including face directions and UVs."""
    normals={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
    inverse={v:k for k,v in normals.items()};result=[]
    for old in elements:
        e=deepcopy(old)
        corners=[fun((x,y,z)) for x in (old['from'][0],old['to'][0]) for y in (old['from'][1],old['to'][1]) for z in (old['from'][2],old['to'][2])]
        e['from']=[round(min(p[i] for p in corners),5) for i in range(3)]
        e['to']=[round(max(p[i] for p in corners),5) for i in range(3)]
        e['faces']={inverse[normal_fun(normals[k])]:deepcopy(v) for k,v in old['faces'].items()}
        result.append(e)
    return result


# 32-pixel tall barred door. Large genuinely open spaces, forged frame, straps,
# four dimensional hinges, iron collars, lock cage, latch and tiny bronze key.
ELEMENTS=[]
for x in (0,14.55):
    box([x,0,13.35],[x+1.45,32,15.6],'iron_dark','forged_full_height_stile')
    box([x+.23,.4,13.16],[x+1.22,31.6,13.43],'steel','polished_stile_front_bevel')
    box([x+.23,.4,15.57],[x+1.22,31.6,15.78],'iron','stile_back_bevel')
for y,h in ((0,1.3),(7.4,.9),(15.15,1.7),(24,.9),(30.7,1.3)):
    box([1.2,y,13.55],[14.8,y+h,15.45],'iron','forged_horizontal_rail')
    box([1.35,y+.16,13.31],[14.65,y+.34,13.58],'steel','rail_cold_edge')
for x in (3.1,6.1,9.1,12.1):
    box([x,1,14],[x+.8,31,14.95],'iron','square_wrought_iron_bar')
    box([x,1.2,13.83],[x+.21,30.8,14.1],'steel','bar_front_highlight')
    for y in (7.1,14.9,23.7):
        box([x-.16,y,13.65],[x+.96,y+.65,15.3],'iron_dark','bar_joint_collar')
        box([x+.18,y+.2,13.48],[x+.57,y+.54,13.72],'steel','bar_collar_rivet')
# Right-hand hinge is x0 for canonical closed north. Mirror for left hinge.
for y in (3.5,12,20.5,28.2):
    box([0,y,13.05],[1.05,y+1.55,15.95],'iron_dark','barrel_hinge')
    box([.18,y+.1,13],[.82,y+1.45,13.25],'steel','hinge_pin_glint')
    box([.8,y+.45,13.03],[3.45,y+1.05,13.34],'iron','hinge_strap')
    box([2.75,y+.56,13],[3.1,y+.9,13.25],'steel','hinge_strap_rivet')
# Armoured lock on handle side, readable raised keyhole, recessed backplate.
box([11.45,10.2,13.25],[14.5,14.8,15.6],'iron_dark','door_lock_cage')
box([11.7,10.45,13.02],[14.26,14.55,13.34],'lock_plate','door_engraved_lock',north='lock_plate')
box([12.63,12.65,13],[13.22,13.25,13.08],'black','door_keyhole_round')
box([12.8,11.6,13],[13.05,12.8,13.08],'black','door_keyhole_stem')
box([14.2,11.9,14],[15.65,12.5,14.7],'steel','sliding_deadbolt')
for x in (.47,14.93):
    for y in (1.95,8.8,17.7,25.6,29.5):
        box([x,y,13.02],[x+.55,y+.55,13.34],'steel','frame_front_rivet')
        box([x,y,15.6],[x+.55,y+.55,15.88],'iron','frame_rear_rivet')
for y in (1.7,29.3):
    box([5.95,y,13.24],[10.05,y+1.25,13.49],'bronze','door_discreet_bronze_badge')
    box([6.15,y+.18,13.09],[9.85,y+1.07,13.26],'justice_crest','door_balance_seal',north='justice_crest')
DOOR=deepcopy(ELEMENTS)
# Native north: closed z13..16; open LEFT x0..3, RIGHT x13..16.
# The right closed hinge is x0. For a right hinge open clockwise about the
# outer corner: x'=z, z'=16-x. The mirrored left uses x'=16-z,z'=x.
variants={}
for hinge in ('left','right'):
    closed=DOOR if hinge=='right' else transform(DOOR,lambda p:(16-p[0],p[1],p[2]),lambda n:(-n[0],n[1],n[2]))
    opened=transform(closed,lambda p:(p[2],p[1],16-p[0]),lambda n:(n[2],n[1],-n[0])) if hinge=='right' else transform(closed,lambda p:(16-p[2],p[1],p[0]),lambda n:(-n[2],n[1],n[0]))
    for isopen,elements in ((False,closed),(True,opened)):
        for half,offset in (('lower',0),('upper',16)):
            name=f'prison_door_{half}_{hinge}'+('_open' if isopen else '')
            write(ASSETS/f'models/block/{name}.json',model(split(elements,offset)))
            for facing,angle in (('north',0),('east',90),('south',180),('west',270)):
                key=f'facing={facing},half={half},hinge={hinge},open={str(isopen).lower()}'
                variants[key]={'model':'lawsjustice:block/'+name,**({'y':angle} if angle else {})}
write(ASSETS/'blockstates/prison_door.json',{'variants':variants})
# Move the paper-thin door towards the centre for balanced inventory framing.
ITEM_DOOR=deepcopy(DOOR)
for e in ITEM_DOOR:
    e['from'][2]-=6.5;e['to'][2]-=6.5
item('prison_door',ITEM_DOOR,True);loot('prison_door',True);COUNTS['prison_door']=len(DOOR)

# Standalone iron panel which players may repeat into their own prison walls.
# Four forged pickets, open slits, double collars and sculpted rivet heads.
ELEMENTS=[]
for x in (0,14.65):
    box([x,0,6.05],[x+1.35,16,9.95],'iron_dark','bars_outer_wrought_stile')
    box([x+.22,.3,6],[x+1.13,15.7,6.3],'steel','bars_stile_front_chamfer')
    box([x+.22,.3,9.7],[x+1.13,15.7,10],'iron','bars_stile_rear_chamfer')
for y in (0,14.65):
    box([1.15,y,6.45],[14.85,y+1.35,9.55],'iron','bars_strong_horizontal_frame')
    box([1.35,y+.22,6.2],[14.65,y+.5,6.5],'steel','bars_rail_edge_highlight')
for x in (3.05,6.1,9.15,12.2):
    box([x,1.1,7.35],[x+.8,14.9,8.65],'iron','bars_square_picket')
    box([x,1.4,7.18],[x+.22,14.6,7.4],'steel','bars_picket_face')
    for y in (4.35,10.9):
        box([x-.25,y,6.95],[x+1.05,y+.85,9.05],'iron_dark','bars_forged_collar')
        box([x+.08,y+.22,6.75],[x+.72,y+.62,7.01],'steel','bars_collar_rivet_front')
        box([x+.08,y+.22,9.01],[x+.72,y+.62,9.25],'iron','bars_collar_rivet_rear')
for x in (.35,15):
    for y in (1.75,7.7,13.65):
        box([x,y,6],[x+.65,y+.65,6.4],'steel','bars_stile_rivet')
        box([x,y,9.6],[x+.65,y+.65,10],'iron','bars_stile_rivet_rear')
for x in (0,14.4):
    for y in (.8,13.9):box([x,y,6.65],[x+1.6,y+1.3,9.35],'iron','bars_joining_bracket')
publish('prison_bars',ELEMENTS)

# Wall-mounted reinforced deadbolt: layered steel armour, bronze escutcheon,
# deep black keyhole, square staple and hanging rectangular link silhouette.
ELEMENTS=[]
box([3.55,3.5,15.15],[12.45,12.65,16],'iron_dark','lock_wall_mount_backplate')
box([3.85,3.8,14.85],[12.15,12.35,15.3],'iron','lock_mount_chamfer')
box([4.6,4.8,13.35],[11.4,11.8,15.05],'iron_dark','reinforced_lock_body')
box([4.9,5.1,12.95],[11.1,11.5,13.55],'steel','lock_face_steel_bevel')
box([5.2,5.4,12.65],[10.8,11.2,13.05],'lock_plate','lock_engraved_face',north='lock_plate')
# Armoured sides and top take deliberate steps rather than a single cube.
for x in (4.3,11.1):
    box([x,5.15,13.25],[x+.6,11.45,15.2],'iron','lock_armoured_side_cheek')
    box([x+.12,6.2,13.02],[x+.48,10.4,13.32],'bronze','lock_vertical_bronze_inlay')
box([5.1,11.45,13.15],[10.9,12.05,15.15],'iron','lock_top_armour')
box([5.35,11.95,13.35],[10.65,12.4,15.35],'steel','lock_crown_bevel')
for x in (4.25,11.2):
    for y in (4.15,11.65):
        box([x,y,14.57],[x+.6,y+.6,15.1],'steel','lock_mount_bolt')
        box([x+.15,y+.15,14.48],[x+.44,y+.44,14.65],'iron_dark','lock_mount_bolt_slot')
for x in (5.45,10):
    for y in (5.65,10.2):box([x,y,12.42],[x+.55,y+.55,12.85],'bronze','lock_face_bronze_rivet')
box([7.2,8.35,12.35],[8.8,9.95,12.68],'bronze','keyhole_round_escutcheon')
box([7.65,8.78,12.25],[8.35,9.48,12.45],'black','keyhole_round_recess')
box([7.45,6.9,12.35],[8.55,8.7,12.68],'bronze','keyhole_stem_escutcheon')
box([7.8,7.3,12.25],[8.2,8.95,12.45],'black','keyhole_stem_recess')
# Metal staple beneath the mechanism; three cuboids form a genuine open link.
for x in (6.2,9.1):box([x,2.2,14],[x+.7,5.25,14.8],'steel','hanging_lock_link_side')
box([6.2,2.05,14],[9.8,2.75,14.8],'steel','hanging_lock_link_bottom')
box([6.4,4.55,13.9],[9.6,5.25,15.1],'iron','hanging_link_top_staple')
box([7.2,11.2,12.4],[8.8,11.7,12.8],'bronze','lock_small_balance_badge')
publish('reinforced_lock',ELEMENTS)

# Carved administrative cabinet, not a generated prison or a judicial desk.
# Four buttressed stone corners, oak drawer, bronze crest, iron key rack,
# burgundy-bound open ledger, inkwell/quill and a low candle form a rich silhouette.
ELEMENTS=[]
box([.4,0,.4],[15.6,.6,15.6],'stone_dark','control_broad_stone_foot')
box([.7,.6,.7],[15.3,1.15,15.3],'stone','control_chamfered_plinth')
box([1.05,1.15,1.05],[14.95,1.6,14.95],'stone_dark','control_plinth_upper_step')
box([1.85,1.6,1.85],[14.15,9.2,14.15],'oak_dark','control_oak_cabinet_body')
for x in (1,12.75):
    for z in (1,12.75):
        box([x,1.55,z],[x+2.25,2.2,z+2.25],'stone','stone_pillar_lower_cap')
        box([x+.25,2.2,z+.25],[x+2,8.4,z+2],'stone','cabinet_corner_pillar')
        box([x+.4,2.8,z+.12],[x+1.85,7.85,z+.26],'stone_dark','pillar_carved_front_recess')
        box([x+.55,3.05,z+.06],[x+1.7,7.6,z+.16],'stone','pillar_inner_moulding')
        box([x,8.4,z],[x+2.25,9.3,z+2.25],'stone','stone_pillar_upper_cap')
        box([x+.15,8.55,z-.02],[x+2.1,8.78,z+.15],'bronze','pillar_bronze_cap_line')
# Front panel and drawer, fine borders and discreet iron corner mounts.
box([3.2,2.25,1.6],[12.8,7.65,1.86],'oak','front_cabinet_wood_panel')
box([3.5,2.65,1.37],[12.5,7.25,1.66],'oak_dark','front_cabinet_recessed_panel')
box([5.25,3.1,1.15],[10.75,6.9,1.43],'justice_crest','control_balance_crest',north='justice_crest')
for x in (3.4,12.25):
    for y in (2.6,6.75):box([x,y,1.1],[x+.38,y+.38,1.45],'bronze','front_fine_mount_rivet')
box([3.25,7.85,1.55],[12.75,9,1.92],'oak','control_shallow_document_drawer')
box([7.1,8.05,1.15],[8.9,8.7,1.57],'bronze','drawer_handle_backplate')
box([7.35,8.18,.92],[8.65,8.52,1.24],'iron_dark','drawer_pull_handle')
# Left and rear paneled surfaces reward viewing from any angle.
for x in (1.6,14.15):
    box([x,3.0,3.3],[x+.25,7.9,12.7],'oak','cabinet_side_wood_inset')
    for z in (3.5,12.1):box([x-.06,3.15,z],[x+.31,7.75,z+.22],'iron','cabinet_side_vertical_strap')
    for y in (3.15,7.53):box([x-.06,y,3.5],[x+.31,y+.22,12.32],'iron','cabinet_side_horizontal_strap')
box([3.3,2.7,14.15],[12.7,8.8,14.4],'oak','cabinet_rear_dressed_panel')
for x in (3.6,12.1):
    box([x,2.9,14.35],[x+.3,8.5,14.57],'iron_dark','rear_iron_vertical_band')
    for y in (3.2,8):box([x+.04,y,14.5],[x+.26,y+.3,14.75],'steel','rear_band_rivet')
box([.6,9.15,.6],[15.4,9.6,15.4],'stone_dark','control_capital_lower_moulding')
box([.35,9.6,.35],[15.65,10.15,15.65],'stone','control_slab_beveled_edge')
box([.6,10.15,.6],[15.4,10.5,15.4],'stone','control_dressed_top_slab')
for z in (.55,15.25):box([.85,10.16,z],[15.15,10.28,z+.2],'bronze','slab_fine_bronze_inlay')
for x in (.55,15.25):box([x,10.16,.85],[x+.2,10.28,15.15],'bronze','slab_side_bronze_inlay')
# Ledger has an actual ridged spine, thick page edges and central gutter.
box([3.3,10.5,3.25],[12.5,10.72,11.15],'velvet','ledger_velvet_rest')
box([3.55,10.72,3.5],[12.25,11.05,10.9],'leather','ledger_open_leather_binding')
box([3.75,11.05,3.7],[7.75,11.45,10.65],'wax','ledger_left_page_edges')
box([8.05,11.05,3.7],[12.05,11.45,10.65],'wax','ledger_right_page_edges')
for y in (11.16,11.3):
    for x0,x1 in ((3.73,7.75),(8.05,12.07)):box([x0,y,3.68],[x1,y+.045,3.72],'oak','ledger_individual_page_edge')
box([3.75,11.45,3.7],[12.05,11.55,10.65],'ledger_pages','ledger_written_pages',up='ledger_pages')
box([7.77,11.05,3.55],[8.03,11.65,10.85],'leather','ledger_ridged_central_spine')
box([8.15,11.56,7.8],[8.55,11.62,11.9],'velvet','ledger_ribbon_bookmark')
box([8.1,11.62,11.5],[8.6,11.72,12.1],'bronze','ledger_bookmark_weight')
# Rear key rack with three individually distinct keys and open ring heads.
box([2.2,10.5,12.1],[6.55,10.72,14.65],'oak_dark','key_rack_wooden_foot')
for x in (2.45,5.95):box([x,10.72,13.55],[x+.45,14.35,14],'iron_dark','key_rack_upright')
box([2.3,13.8,13.45],[6.5,14.3,14.1],'iron','key_rack_crossbar')
for x,length in ((2.95,1.6),(4.15,2.1),(5.35,1.85)):
    box([x,13.15,13.1],[x+.12,13.8,13.7],'bronze','key_ring_left')
    box([x+.55,13.15,13.1],[x+.67,13.8,13.7],'bronze','key_ring_right')
    box([x,13.68,13.1],[x+.67,13.83,13.7],'bronze','key_ring_top')
    box([x,13.06,13.1],[x+.67,13.21,13.7],'bronze','key_ring_bottom')
    box([x+.27,13.1-length,13.28],[x+.42,13.1,13.53],'bronze','key_long_stem')
    box([x+.33,13.12-length,13.22],[x+.8,13.4-length,13.59],'bronze','key_bit')
# Low candle, drip ridge, separate brass dish and tiny warm pixel flame.
box([11.75,10.5,12.4],[14.55,10.7,15.15],'bronze','candle_broad_dish')
box([12.05,10.7,12.7],[14.25,10.95,14.85],'bronze','candle_dish_raised_ring')
box([12.55,10.95,13.2],[13.75,13.75,14.4],'wax','candle_ivory_wax')
box([12.42,12.85,13.38],[12.65,13.7,13.75],'wax','candle_wax_drip_left')
box([13.57,13.1,13.72],[13.85,13.68,14.05],'wax','candle_wax_drip_right')
box([13.06,13.75,13.71],[13.2,14.05,13.85],'black','candle_wick')
# Crossed cutout flame, bounded at15.8 and almost no volume.
box([12.72,14.02,13.74],[13.52,15.8,13.8],'flame','candle_flame_cross_x',north='flame',south='flame')
box([13.08,14.02,13.36],[13.14,15.8,14.16],'flame','candle_flame_cross_z',east='flame',west='flame')
# Inkwell plus restrained ivory quill in front left corner.
box([1.15,10.5,2.1],[2.75,10.75,3.7],'bronze','inkwell_base')
box([1.3,10.75,2.25],[2.6,11.7,3.55],'iron_dark','inkwell_body')
box([1.48,11.7,2.43],[2.42,11.9,3.37],'bronze','inkwell_rim')
box([1.65,11.9,2.6],[2.25,11.94,3.2],'black','inkwell_ink_surface')
box([1.88,11.91,2.86],[2.02,13.8,3],'wax','ivory_quill_stem')
box([1.52,12.55,2.84],[1.9,13.65,3.02],'wax','quill_left_vane')
box([2,12.95,2.84],[2.3,14.15,3.02],'wax','quill_right_vane')
publish('prison_control',ELEMENTS)

# An ornate, genuinely three-dimensional surveying tool. A short iron/oak rod,
# bronze octagonal compass, compass face and two calibrated ruler flanges.
ELEMENTS=[]
box([7.35,1,7.35],[8.65,12.8,8.65],'oak','selector_oak_staff')
box([7.2,.65,7.2],[8.8,1.6,8.8],'iron_dark','selector_iron_shoe')
box([7.32,.3,7.32],[8.68,.75,8.68],'steel','selector_shoe_bevel')
for y in (2.1,5,8.7,11.8):box([7.12,y,7.12],[8.88,y+.45,8.88],'bronze','selector_calibration_collar')
box([7.18,2.65,7.18],[8.82,7.65,8.82],'leather','selector_leather_grip')
for y in (3,4,5,6,7):box([7.1,y,7.1],[8.9,y+.12,8.9],'iron_dark','selector_grip_binding')
box([4,10.65,6.65],[12,14.65,9.35],'iron_dark','selector_compass_octagon')
box([4.65,10.05,6.65],[11.35,15.25,9.35],'iron_dark','selector_compass_octagon_bevel')
box([4.4,10.95,6.3],[11.6,14.35,6.75],'bronze','selector_compass_front_bezel')
box([5,10.35,6.3],[11,14.95,6.75],'bronze','selector_compass_front_corner_bezel')
box([5.1,10.85,6.15],[10.9,14.45,6.5],'surveyor_dial','selector_compass_inscribed_dial',north='surveyor_dial')
box([7.65,12.2,5.9],[8.35,12.9,6.3],'bronze','selector_compass_central_pivot')
for x in (4.6,10.9):
    for y in (11.1,13.9):box([x,y,6.12],[x+.5,y+.5,6.45],'steel','selector_compass_rivet')
box([3.2,9.3,7.5],[12.8,9.75,8.5],'steel','selector_measuring_crosspiece')
for x in (3.3,5,6.6,9,10.7,12.3):box([x,9.75,7.45],[x+.15,10.15,8.55],'bronze','selector_ruler_tick')
check_bounds(ELEMENTS);item('area_selector',ELEMENTS);COUNTS['area_selector']=len(ELEMENTS)

# Vanilla 1.21.1 data paths are deliberately singular: recipe / loot_table.
recipes=(
    ('prison_door',['INI','IBI','INI'],{'I':'iron_ingot','N':'iron_nugget','B':'iron_bars'},1),
    ('prison_bars',['INI','BIB','INI'],{'I':'iron_ingot','N':'iron_nugget','B':'iron_bars'},4),
    ('reinforced_lock',['NIN','IGI','NIN'],{'I':'iron_ingot','N':'iron_nugget','G':'gold_ingot'},1),
    ('prison_control',['BPL','ISI','SSS'],{'B':'book','P':'paper','L':'lantern','I':'iron_ingot','S':'stone_bricks'},1),
    ('area_selector',['GCG','ISI',' S '],{'G':'gold_ingot','C':'compass','I':'iron_nugget','S':'stick'},1))
for name,pattern,keys,count in recipes:
    write(RES/f'data/lawsjustice/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':'minecraft:'+v} for k,v in keys.items()},'result':{'id':'lawsjustice:'+name,'count':count}})
    write(RES/f'data/lawsjustice/advancement/recipes/decorations/{name}.json',{'parent':'minecraft:recipes/root','criteria':{
        'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}},
        'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'lawsjustice:'+name}}},
        'requirements':[['has_iron','has_recipe']],'rewards':{'recipes':['lawsjustice:'+name]}})
blocks=['lawsjustice:'+n for n in ('prison_door','prison_bars','reinforced_lock','prison_control')]
for tag in ('mineable/pickaxe','needs_stone_tool'):
    write(RES/f'data/minecraft/tags/block/{tag}.json',{'replace':False,'values':blocks})
write(RES/'data/minecraft/tags/block/doors.json',{'replace':False,'values':['lawsjustice:prison_door']})

# Original 256px mod-list icon, pixel-perfect at128 then nearest-neighbour2x:
# a wrought-iron gate and ivory judicial scales, with a burgundy shield.
icon=noise((128,128),(25,32,36),2);d=ImageDraw.Draw(icon)
d.rectangle((3,3,124,124),fill=(48,49,46),outline=(17,23,26),width=2)
d.rectangle((6,6,121,121),fill=(33,40,43),outline=GOLD,width=2)
d.rectangle((10,10,117,117),outline=(80,87,87))
for x,y in ((15,15),(112,15),(15,112),(112,112)):diamond(d,x,y,3,LIGHT)
for x in (24,96):
    d.rectangle((x,24,x+8,106),fill=IRON,outline=STEEL,width=2)
    for y in (31,60,93):d.rectangle((x+2,y,x+5,y+3),fill=LIGHT)
for y in (25,98):d.rectangle((28,y,100,y+6),fill=IRON,outline=STEEL,width=1)
for x in (41,56,71,86):
    d.rectangle((x,31,x+4,98),fill=IRON);d.line((x,32,x,97),fill=STEEL,width=1)
    d.rectangle((x-2,72,x+6,77),fill=DARK,outline=STEEL)
d.polygon(((41,36),(87,36),(87,75),(64,97),(41,75)),fill=(25,29,31),outline=GOLD,width=2)
d.polygon(((45,40),(83,40),(83,73),(64,91),(45,73)),fill=WINE)
scales(d,64,63,1.55,LIGHT)
d.rectangle((32,105,96,109),fill=(21,27,30));d.line((38,106,90,106),fill=GOLD)
icon.resize((256,256),Image.Resampling.NEAREST).save(ASSETS/'icon.png')


# ---------------------------------------------------------------------------
# Version 0.2: court furniture and actual physical documents/restraints.
# Append after all v0.1 artwork (including its icon) to preserve original pixels
# and model JSON byte-for-byte. This separate RNG cannot disturb old artwork.
# ---------------------------------------------------------------------------
RNG=random.Random(2018102026)

for name,base in (('mahogany',(78,43,34)),('mahogany_dark',(40,28,25)),('gold_polished',(193,150,74))):
    im=noise((32,32),base,2);d=ImageDraw.Draw(im)
    if name.startswith('mahogany'):
        for x in (0,15,31):
            d.line((x,0,x,31),fill=shade(base,-19))
            if x<31:d.line((x+1,1,x+1,30),fill=shade(base,12))
        for _ in range(34):
            x,y=RNG.randrange(2,30),RNG.randrange(29)
            d.line((x,y,x,min(31,y+RNG.randrange(2,9))),fill=shade(base,RNG.choice((-12,10))))
        d.arc((7,8,14,18),20,340,fill=shade(base,-20))
    else:
        d.line((0,0,31,0,31,31),fill=LIGHT)
        for y in (7,23):d.line((0,y,31,y),fill=shade(base,15))
        d.line((0,29,31,29),fill=shade(base,-26))
    save(name,im)

im=noise((16,16),(132,38,49),2);d=ImageDraw.Draw(im)
d.polygon(((4,1),(11,1),(15,5),(15,11),(10,15),(4,15),(0,11),(0,5)),fill=(151,44,57))
d.polygon(((4,2),(11,2),(13,6),(9,7),(3,6),(1,10),(1,5)),fill=(205,89,91))
d.polygon(((3,6),(9,7),(12,11),(8,14),(2,10)),fill=(167,52,65))
d.line((3,6,9,7,12,11),fill=(230,115,108))
save('garnet',im)

im=noise((32,32),(129,39,50),2);d=ImageDraw.Draw(im)
d.ellipse((1,1,30,30),fill=(148,44,56),outline=(81,27,38),width=2)
d.ellipse((4,4,27,27),outline=(197,83,84),width=1)
d.ellipse((7,7,24,24),outline=(101,27,40))
scales(d,16,16,.57,(220,172,98))
for x,y in ((5,13),(24,8),(25,22),(9,26)):d.point((x,y),fill=(202,91,89))
save('royal_wax_seal',im)

# A hand-written parchment sheet. Static marks are decoration only; the issued
# target, reasons, dates and signature are rendered from server data by the UI.
im=noise((32,64),PAPER,2);d=ImageDraw.Draw(im)
d.rectangle((0,0,31,63),outline=(157,125,79))
d.line((1,1,30,1),fill=(248,232,190));d.rectangle((3,4,28,59),outline=(187,157,107))
scales(d,16,14,.65,(140,102,52))
d.line((7,25,24,25),fill=(136,83,66));d.line((8,27,23,27),fill=(176,137,91))
for y in (32,36,40,44,48):
    xend=24-RNG.randrange(0,5);d.line((7,y,xend,y),fill=(108,92,66))
    if y in (36,44):d.point((11,y),fill=shade(PAPER,4))
d.line((8,55,10,52,12,56,14,53,16,54,20,52),fill=(97,82,59))
d.line((7,57,21,57),fill=(183,151,103))
d.polygon(((26,59),(30,54),(30,62)),fill=(243,227,187))
save('warrant_parchment',im)

im=noise((64,64),PAPER,2);d=ImageDraw.Draw(im)
d.rectangle((1,1,62,62),outline=(177,147,96));d.rectangle((4,4,59,59),outline=(199,174,126))
scales(d,18,16,.72,(143,105,56))
for y,xx in ((12,33),(17,33),(23,33),(31,10),(37,10),(43,10),(49,10)):
    d.line((xx,y,54-RNG.randrange(7),y),fill=(104,88,63))
d.line((10,55,16,51,18,55,23,52,28,55,36,52),fill=(126,79,62))
d.ellipse((45,45,56,56),fill=(146,42,54),outline=(187,91,83))
d.line((49,50,53,50,51,47,51,54),fill=GOLD)
save('court_parchment',im)

NEW_TEXTURES=('court_parchment','garnet','gold_polished','mahogany','mahogany_dark','royal_wax_seal','warrant_parchment')
TEXTURES={**TEXTURES,**{name:'lawsjustice:block/'+name for name in NEW_TEXTURES}}


def fine_border(x0,y,z0,x1,z1,texture='gold_polished',thickness=.14):
    box([x0,y,z0],[x1,y+.09,z0+thickness],texture,'court_fine_front_inlay')
    box([x0,y,z1-thickness],[x1,y+.09,z1],texture,'court_fine_rear_inlay')
    box([x0,y,z0+thickness],[x0+thickness,y+.09,z1-thickness],texture,'court_fine_left_inlay')
    box([x1-thickness,y,z0+thickness],[x1,y+.09,z1-thickness],texture,'court_fine_right_inlay')


def seal(x,y,z,size=1.5):
    # Two overlapping bevels give the wax seal an octagonal outline.
    box([x+.12,y,z],[x+size-.12,y+.16,z+size],'velvet','court_octagonal_wax_outer')
    box([x,y,z+.12],[x+size,y+.16,z+size-.12],'velvet','court_octagonal_wax_side_bevel')
    box([x+.1,y+.16,z+.1],[x+size-.1,y+.24,z+size-.1],'royal_wax_seal','court_pressed_balance_wax_seal',up='royal_wax_seal')


# A distinct court desk, with four fluted legs, public shield, writing surface,
# sculpted balance scales and a proper T-shaped judge's gavel on a sound block.
ELEMENTS=[]
for x,z in ((1.05,1.05),(12.65,1.05),(1.05,12.65),(12.65,12.65)):
    box([x,0,z],[x+2.3,.45,z+2.3],'stone_dark','desk_stone_foot')
    box([x+.12,.45,z+.12],[x+2.18,.9,z+2.18],'stone','desk_dressed_foot_bevel')
    box([x+.35,.9,z+.35],[x+1.95,7.9,z+1.95],'mahogany','desk_carved_mahogany_leg')
    box([x+.57,2,z+.25],[x+1.73,6.7,z+.39],'mahogany_dark','desk_fluted_leg_recess')
    box([x+.95,2.2,z+.17],[x+1.35,6.5,z+.29],'gold_polished','desk_leg_gold_fluting')
    for y in (1.15,7.35):box([x+.18,y,z+.18],[x+2.12,y+.35,z+2.12],'iron','desk_iron_leg_collar')
    box([x+.08,7.9,z+.08],[x+2.22,8.8,z+2.22],'mahogany_dark','desk_carved_capital')
for x in (1.9,13):
    box([x,2.1,2.3],[x+1.1,2.65,13.7],'mahogany_dark','desk_side_stretcher')
    box([x,7.1,2.2],[x+1.1,8.85,13.8],'mahogany','desk_side_apron')
box([2.4,2.18,7.5],[13.6,2.78,8.5],'iron_dark','desk_cross_stretcher')
# Judge-facing modesty panel: burgundy inset and small royal balance seal.
box([2.9,4,1.7],[13.1,8.85,2.25],'mahogany_dark','desk_front_justice_panel')
box([3.3,4.4,1.5],[12.7,8.5,1.8],'mahogany','desk_raised_front_moulding')
box([3.65,4.75,1.37],[12.35,8.15,1.56],'velvet','desk_burgundy_public_panel')
for x in (3.8,12):box([x,4.85,1.22],[x+.16,8.05,1.43],'gold_polished','desk_public_panel_side_inlay')
for y in (4.9,7.9):box([3.8,y,1.22],[12.16,y+.16,1.43],'gold_polished','desk_public_panel_horizontal_inlay')
box([6.25,4.9,1.12],[9.75,8.05,1.41],'justice_crest','desk_public_balance_shield',north='justice_crest')
for x in (4.4,11.3):
    for y in (5.35,7.3):box([x,y,1.17],[x+.35,y+.35,1.42],'bronze','desk_panel_mounted_rivet')
# Braced sides and rear drawer make the furniture attractive from all facings.
for x in (1.77,13.75):
    box([x,7.35,3],[x+.45,8.45,13],'iron_dark','desk_side_apron_iron_band')
    for z in (3.8,12.1):box([x-.1,7.65,z],[x+.55,8,z+.35],'steel','desk_side_band_rivet')
box([3,7.05,13.5],[13,8.75,14.1],'mahogany','desk_rear_document_drawer')
box([7.1,7.55,14.1],[8.9,8.2,14.33],'bronze','desk_document_drawer_backplate')
box([7.35,7.68,14.33],[8.65,8.02,14.65],'iron_dark','desk_document_drawer_pull')
box([.65,8.75,.65],[15.35,9.1,15.35],'mahogany_dark','desk_lower_cornice')
box([.4,9.1,.4],[15.6,9.42,15.6],'gold_polished','desk_fine_gold_table_lip')
box([.6,9.42,.6],[15.4,9.8,15.4],'mahogany','desk_solid_top_bevel')
box([.85,9.8,.85],[15.15,9.94,15.15],'mahogany_dark','desk_upper_writing_surface')
fine_border(1.05,9.95,1.05,14.95,14.95)
box([1.7,9.95,1.7],[14.3,10.02,9.4],'velvet','desk_burgundy_writing_pad')
fine_border(1.85,10.025,1.85,14.15,9.25,'bronze',.1)
# Detailed paper docket, ribbon and small impressed royal wax seal.
box([2.25,10.03,2.15],[8.25,10.16,8.65],'wax','desk_ivory_docket_stack')
for y in (10.07,10.12):box([2.23,y,2.12],[8.27,y+.025,2.18],'oak','desk_docket_paper_edge')
box([2.25,10.16,2.15],[8.25,10.21,8.65],'court_parchment','desk_written_court_docket',up='court_parchment')
box([7.2,10.22,6.4],[7.55,10.26,9.25],'velvet','desk_wax_seal_ribbon_left')
box([7.7,10.22,6.4],[8.05,10.26,9.1],'velvet','desk_wax_seal_ribbon_right')
seal(6.85,10.27,6.6,1.65)
# Gavel: octagonal sound block, two metal head bands and oak handle.
box([9.7,10.03,2.7],[14,10.22,5.75],'mahogany_dark','gavel_octagonal_sound_block')
box([10.05,10.03,2.4],[13.65,10.22,6.05],'mahogany_dark','gavel_sound_block_corner_bevel')
box([10,10.22,2.85],[13.7,10.35,5.6],'mahogany','gavel_sound_block_top')
fine_border(10.1,10.36,2.95,13.6,5.5,'bronze',.11)
box([10.05,10.47,3.72],[13.65,11.47,4.98],'mahogany','gavel_turned_head')
box([9.85,10.62,3.88],[10.18,11.32,4.82],'mahogany_dark','gavel_left_striking_face')
box([13.52,10.62,3.88],[13.85,11.32,4.82],'mahogany_dark','gavel_right_striking_face')
for x in (10.45,12.8):box([x,10.42,3.67],[x+.35,11.52,5.03],'gold_polished','gavel_polished_head_band')
box([11.55,10.68,4.85],[12.05,11.18,8.45],'mahogany','gavel_oak_handle')
box([11.46,10.59,7.6],[12.14,11.27,8.7],'mahogany_dark','gavel_handle_pommel')
box([11.43,10.56,7.54],[12.17,11.3,7.8],'bronze','gavel_handle_pommel_band')
# The 3D balance has real open space around its pans and chain hangers.
box([6.25,9.97,10.05],[9.75,10.23,13.65],'mahogany_dark','scales_octagonal_base')
box([6.55,9.97,9.75],[9.45,10.23,13.95],'mahogany_dark','scales_base_octagonal_bevel')
box([6.5,10.23,10.3],[9.5,10.49,13.4],'gold_polished','scales_polished_base')
box([7.28,10.49,11.08],[8.72,10.86,12.52],'bronze','scales_base_raised_capital')
box([7.72,10.86,11.52],[8.28,14.8,12.08],'gold_polished','scales_upright_balanced_column')
for y in (11.15,13.9):box([7.56,y,11.36],[8.44,y+.28,12.24],'gold_polished','scales_column_moulded_collar')
box([2.4,14.36,11.5],[13.6,14.7,12.1],'gold_polished','scales_horizontal_balance_beam')
box([2.15,14.26,11.4],[2.65,14.8,12.2],'bronze','scales_left_beam_finial')
box([13.35,14.26,11.4],[13.85,14.8,12.2],'bronze','scales_right_beam_finial')
box([7.45,14.7,11.25],[8.55,15.12,12.35],'gold_polished','scales_central_beam_pivot')
box([7.62,15.12,11.42],[8.38,15.9,12.18],'garnet','scales_garnet_royal_finial',full=True)
for cx in (4.1,11.9):
    # Thin suspended chain segments are geometrical, not a painted texture.
    for dx in (-1.15,1.15):
        for zz in (10.9,12.55):
            box([cx+dx-.065,11.65,zz],[cx+dx+.065,14.45,zz+.13],'bronze','scales_suspended_pan_chain')
            for yy in (12.35,13.35):box([cx+dx-.11,yy,zz-.03],[cx+dx+.11,yy+.15,zz+.16],'gold_polished','scales_small_chain_link')
    box([cx-1.48,11.38,10.72],[cx+1.48,11.55,12.88],'gold_polished','scales_pan_octagonal_rim')
    box([cx-1.65,11.38,10.95],[cx+1.65,11.55,12.65],'gold_polished','scales_pan_rim_corner_bevel')
    box([cx-1.15,11.18,10.98],[cx+1.15,11.38,12.62],'bronze','scales_pan_bowl_middle')
    box([cx-.85,11.04,11.22],[cx+.85,11.18,12.38],'gold_polished','scales_pan_bowl_bottom')
    box([cx-1.22,11.55,11],[cx+1.22,11.62,12.6],'bronze','scales_pan_inner_surface')
publish('justice_desk',ELEMENTS)

# Two hollow, octagonal metal cuffs joined by three physical interlocking links.
# They are a held 3D item; authority always comes from the server's warrant.
ELEMENTS=[]
for cx in (3.5,12.5):
    cy=8.4
    for x0,y0,x1,y1 in ((cx-1.35,cy+1.95,cx+1.35,cy+2.65),
                       (cx-1.35,cy-2.65,cx+1.35,cy-1.95),
                       (cx-2.65,cy-1.35,cx-1.95,cy+1.35),
                       (cx+1.95,cy-1.35,cx+2.65,cy+1.35),
                       (cx-2.05,cy+1.25,cx-1.25,cy+2.05),
                       (cx+1.25,cy+1.25,cx+2.05,cy+2.05),
                       (cx-2.05,cy-2.05,cx-1.25,cy-1.25),
                       (cx+1.25,cy-2.05,cx+2.05,cy-1.25)):
        box([x0,y0,7.15],[x1,y1,8.85],'iron','handcuff_hollow_octagonal_ring')
        box([x0,y0,6.98],[x1,y1,7.2],'steel','handcuff_polished_front_bevel')
    for dx,dy in ((0,2.25),(-2.25,0),(2.25,0),(0,-2.25)):
        box([cx+dx-.18,cy+dy-.18,6.76],[cx+dx+.18,cy+dy+.18,7.08],'steel','handcuff_individual_rivet')
    box([cx-.8,cy+2.15,6.7],[cx+.8,cy+3.25,8.95],'iron_dark','handcuff_reinforced_lock_housing')
    box([cx-.58,cy+2.33,6.48],[cx+.58,cy+3.08,6.79],'bronze','handcuff_lock_keyhole_plate')
    box([cx-.16,cy+2.63,6.37],[cx+.16,cy+2.93,6.53],'black','handcuff_lock_keyhole_round')
    box([cx-.09,cy+2.42,6.37],[cx+.09,cy+2.75,6.53],'black','handcuff_lock_keyhole_stem')
    for dx in (-.53,.35):box([cx+dx,cy+2.55,6.28],[cx+dx+.18,cy+2.73,6.57],'steel','handcuff_lock_housing_rivet')
# Link planes alternate XY and XZ so the short connecting chain has depth.
for index,x in enumerate((6.05,7.45,8.85)):
    if index%2==0:
        for yy in (8.05,9.05):box([x,yy,7.65],[x+1.85,yy+.25,8.05],'steel','handcuff_chain_link_horizontal')
        for xx in (x,x+1.6):box([xx,8.3,7.65],[xx+.25,9.05,8.05],'iron','handcuff_chain_link_vertical')
    else:
        for zz in (7.05,8.45):box([x,8.48,zz],[x+1.85,8.78,zz+.25],'steel','handcuff_chain_link_depth')
        for xx in (x,x+1.6):box([xx,8.48,7.3],[xx+.25,8.78,8.45],'iron','handcuff_chain_link_bridge')
check_bounds(ELEMENTS);item('handcuffs',ELEMENTS);COUNTS['handcuffs']=len(ELEMENTS)
# Inventory framing emphasizes the cuffs' holes rather than their thin edges.
value=json.loads((ASSETS/'models/item/handcuffs.json').read_text())
value['display']['gui']={'rotation':[12,165,-12],'translation':[0,-.9,0],'scale':[.95,.95,.95]}
value['display']['firstperson_righthand']={'rotation':[0,145,-10],'translation':[0,0,0],'scale':[.75,.75,.75]}
value['display']['firstperson_lefthand']={'rotation':[0,215,10],'translation':[0,0,0],'scale':[.75,.75,.75]}
write(ASSETS/'models/item/handcuffs.json',value)

# An actual thin parchment scroll with rolled ends, gold caps, hanging ribbons
# and raised wax. No craftable authorisation: only CourtService issues warrants.
ELEMENTS=[]
box([3,1.5,7.75],[13,14.7,8],'wax','warrant_thin_ivory_parchment')
box([3,1.5,7.69],[13,14.7,7.78],'warrant_parchment','warrant_decorative_front',north='warrant_parchment')
box([3,1.5,7.97],[13,14.7,8.06],'warrant_parchment','warrant_decorative_back',south='warrant_parchment')
for y in (1.1,14.35):
    box([2.6,y,7.2],[13.4,y+.9,8.55],'wax','warrant_rolled_parchment_end')
    box([2.8,y-.12,7.38],[13.2,y+1.02,8.37],'wax','warrant_scroll_end_bevel')
    for x in (2.28,13.25):
        box([x,y+.05,7.27],[x+.47,y+.85,8.48],'bronze','warrant_rolled_end_gold_cap')
        box([x+.08,y+.17,7.12],[x+.39,y+.73,7.38],'gold_polished','warrant_end_cap_front_glint')
# Raised red ribbons remain in the item silhouette even at inventory scale.
for x in (9.95,11.75):
    box([x,1.1,7.22],[x+.65,4.55,7.66],'velvet','warrant_hanging_burgundy_ribbon')
    box([x+.1,.65,7.22],[x+.55,1.15,7.66],'velvet','warrant_ribbon_forked_tail')
box([9.55,3.25,7.05],[12.85,5.75,7.7],'velvet','warrant_octagonal_royal_seal_base')
box([9.85,2.95,7.05],[12.55,6.05,7.7],'velvet','warrant_royal_seal_corner_bevel')
box([9.82,3.2,6.84],[12.58,5.82,7.13],'royal_wax_seal','warrant_impressed_balance_seal',north='royal_wax_seal')
# Three small folds along the paper edge add dimensional parchment highlights.
for y in (4.4,8.7,11.6):box([12.8,y,7.56],[13.13,y+.8,7.82],'wax','warrant_parchment_edge_fold')
check_bounds(ELEMENTS);item('warrant',ELEMENTS);COUNTS['warrant']=len(ELEMENTS)
value=json.loads((ASSETS/'models/item/warrant.json').read_text())
value['display']['gui']={'rotation':[5,170,-8],'translation':[0,0,0],'scale':[.92,.92,.92]}
value['display']['firstperson_righthand']={'rotation':[0,175,-8],'translation':[0,0,0],'scale':[.85,.85,.85]}
value['display']['firstperson_lefthand']={'rotation':[0,185,8],'translation':[0,0,0],'scale':[.85,.85,.85]}
write(ASSETS/'models/item/warrant.json',value)

for name,pattern,keys in (
    ('justice_desk',['GBG','PWP','ISI'],{'G':'gold_ingot','B':'book','P':'paper','W':'dark_oak_planks','I':'iron_ingot','S':'stone_bricks'}),
    ('handcuffs',['INI','NCN','INI'],{'I':'iron_ingot','N':'iron_nugget','C':'chain'})):
    write(RES/f'data/lawsjustice/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':{k:{'item':'minecraft:'+v} for k,v in keys.items()},'result':{'id':'lawsjustice:'+name,'count':1}})
    write(RES/f'data/lawsjustice/advancement/recipes/decorations/{name}.json',{'parent':'minecraft:recipes/root','criteria':{
        'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':['minecraft:iron_ingot']}]}},
        'has_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'lawsjustice:'+name}}},
        'requirements':[['has_iron','has_recipe']],'rewards':{'recipes':['lawsjustice:'+name]}})
for tag in ('mineable/pickaxe','needs_stone_tool'):
    write(RES/f'data/minecraft/tags/block/{tag}.json',{'replace':False,'values':blocks+['lawsjustice:justice_desk']})
print(json.dumps({'original_cuboids':COUNTS,'original_textures':len(TEXTURES)-1,'door_variants':len(variants),'block_model_files':len(list((ASSETS/'models/block').glob('*.json'))),'item_model_files':8,'crafting_recipes':7,'warrant_craftable':False},indent=2))
