#!/usr/bin/env python3
"""Original Realm Markets pixel art and native multi-block models. Requires Pillow.

South is the front. A stall is 32x32x16 units, split at x=16/y=16 into
four strictly local model parts. The inventory model is the same geometry,
recentered at (8,8,8). No custom renderer or external artwork is used.
"""
from pathlib import Path
from copy import deepcopy
import json
import math
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/realmmarkets'
TEX = ASSETS / 'textures/block'
TEX.mkdir(parents=True, exist_ok=True)
RNG = random.Random(487621)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def shade(rgb, n):
    return tuple(max(0, min(255, c + n)) for c in rgb) + (255,)


def image(base, size=32):
    return Image.new('RGBA', (size, size), base + (255,))


def save(name, im):
    im.save(TEX / (name + '.png'))


# Hand-selected broken grain strokes, not per-pixel noise or blurry gradients.
for name, base, vertical in [('oak', (137, 96, 58), False),
                              ('oak_post', (121, 79, 47), True),
                              ('oak_dark', (75, 49, 33), False),
                              ('oak_light', (164, 121, 72), False)]:
    im = image(base); d = ImageDraw.Draw(im)
    for line in range(3, 32, 7):
        if vertical:
            d.line((line, 0, line, 31), fill=shade(base, -19))
            d.line((line + 1, 0, line + 1, 31), fill=shade(base, 10))
        else:
            d.line((0, line, 31, line), fill=shade(base, -24))
            d.line((0, line + 1, 31, line + 1), fill=shade(base, 13))
    for _ in range(22):
        x, y = RNG.randrange(1, 29), RNG.randrange(1, 29)
        length = RNG.randrange(2, 8)
        d.line((x, y, x if vertical else min(31, x+length), min(31, y+length) if vertical else y), fill=shade(base, RNG.choice([-13, 8])))
    if not vertical:
        d.line((10, 18, 14, 18, 16, 19, 13, 20, 10, 19), fill=shade(base, -24))
        d.line((12, 19, 14, 19), fill=shade(base, 11))
    save(name, im)

for name, base in [('crimson', (134, 48, 47)), ('sage', (81, 110, 76)), ('cream', (231, 213, 168))]:
    im = image(base); d = ImageDraw.Draw(im)
    for y in (1, 15, 29):
        d.line((0, y, 31, y), fill=shade(base, -9))
        for x in range(1, 32, 4):
            d.line((x, y+1, x+1, y+1), fill=shade(base, 15))
    for y in (7, 23):
        for x in range(2, 32, 8):
            d.point((x,y), fill=shade(base, -5)); d.point((x+1,y), fill=shade(base, 5))
    save(name, im)

for name, base in [('brass', (183, 139, 61)), ('iron', (65, 68, 61)),
                   ('apple', (161, 47, 41)), ('apple_top', (184, 62, 43)),
                   ('pear', (196, 153, 53)), ('cabbage', (84, 123, 61)),
                   ('leaf', (62, 103, 54)), ('stem', (77, 60, 33)),
                   ('sack', (177, 153, 103))]:
    im = image(base,16); d = ImageDraw.Draw(im)
    if name in ('brass','iron'):
        d.line((0,0,15,0), fill=shade(base, 30));d.line((0,0,0,15), fill=shade(base,21))
        d.line((0,15,15,15), fill=shade(base,-31));d.line((15,0,15,15), fill=shade(base,-24))
        for x,y in [(3,3),(12,3),(3,12),(12,12)]:d.point((x,y), fill=shade(base,-25))
    elif name in ('cabbage', 'leaf'):
        d.line((2,12,12,3), fill=shade(base, 27));d.line((6,9,3,4), fill=shade(base,17))
        d.line((8,7,12,9),fill=shade(base,17));d.line((10,5,7,2),fill=shade(base,-15))
    elif name == 'sack':
        for y in (2,6,10,14):d.line((0,y,15,y), fill=shade(base,-9))
        for x in (3,11):d.line((x,0,x,15), fill=shade(base,8))
    else:
        d.rectangle((2,2,5,4),fill=shade(base,18));d.line((1,12,13,12),fill=shade(base,-14))
        d.point((10,5),fill=shade(base,-6))
    save(name,im)

# Front sign is a coin-and-leaf insignia, deliberately free of tiny fake text.
im=image((74,49,34));d=ImageDraw.Draw(im)
d.rectangle((1,1,30,30),fill=(156,114,62,255));d.rectangle((3,3,28,28),fill=(224,204,153,255))
d.rectangle((4,4,27,27),outline=(184,151,90,255))
d.ellipse((8,6,23,21),fill=(110,81,38,255));d.ellipse((9,6,23,20),fill=(201,157,63,255))
d.line((12,8,19,8), fill=(244,207,112,255));d.line((11,9,11,16),fill=(236,193,93,255))
d.line((16,9,16,18),fill=(125,90,37,255));d.line((13,11,19,11),fill=(125,90,37,255));d.line((13,16,19,16),fill=(125,90,37,255))
d.line((8,25,23,22),fill=(66,100,52,255));d.rectangle((10,22,12,24),fill=(94,128,64,255));d.rectangle((18,24,21,25),fill=(94,128,64,255))
for x,y in [(2,2),(29,2),(2,29),(29,29)]:d.point((x,y),fill=(85,65,37,255))
save('market_sign',im)

TEXTURES={n:'realmmarkets:block/'+n for n in ['oak','oak_post','oak_dark','oak_light','cream','brass','iron','apple','apple_top','pear','cabbage','leaf','stem','sack','market_sign']}
TEXTURES['particle']=TEXTURES['oak']


def geometry(color):
    elements=[]
    def box(a,b,t,name,*,up=None,south=None,rotation=None):
        a=list(a);b=list(b)
        dx,dy,dz=[b[i]-a[i] for i in range(3)]
        sizes={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
        faces={side:{'uv':[0,0,round(min(16,w),4),round(min(16,h),4)],'texture':'#'+t} for side,(w,h) in sizes.items()}
        if up:faces['up']['texture']='#'+up
        if south:faces['south']={'uv':[0,0,16,16],'texture':'#'+south}
        e={'name':name,'from':a,'to':b,'faces':faces}
        if rotation:e['rotation']=rotation
        elements.append(e)
    # Four structural posts, carved feet, iron collars and brass pins.
    for x in (1.1,29.0):
        for z,height in ((1.15,31.2),(13.0,25.7)):
            box((x-.35,0,z-.25),(x+2.45,1.0,z+2.45),'oak_dark','broad_post_foot')
            box((x,1,z),(x+2.1,height,z+2.1),'oak_post','hewn_upright')
            for y in (1.35,11.05,height-1.45):
                box((x-.09,y,z-.09),(x+2.19,y+.5,z+2.19),'iron','iron_collar')
                box((x+.78,y+.1,z+2.18),(x+1.3,y+.4,z+2.31),'brass','collar_pin')
            box((x-.2,height-.6,z-.15),(x+2.3,height+.1,z+2.25),'oak_dark','post_cap')
    # Frame and slatted counter: wide golden lip, quieter darker boards beneath.
    box((2.1,2.1,2.0),(30.0,3.35,3.25),'oak_dark','rear_stretcher')
    box((2.1,2.1,13.7),(30.0,3.35,15.0),'oak_dark','front_stretcher')
    for x in (2.0,28.6):box((x,2.25,2.0),(x+1.4,3.25,14.6),'oak_dark','side_stretcher')
    for y in (3.6,6.35,9.1):
        box((2.9,y,13.75),(29.1,y+2.45,14.65),'oak','front_tongue_and_groove')
    for x in (3.4,27.9):
        box((x,3.5,14.64),(x+.7,11.7,14.95),'oak_dark','front_vertical_strap')
        for y in (4.0,10.8):box((x+.2,y,14.95),(x+.5,y+.3,15.04),'brass','strap_rivet')
    box((.55,11.7,2.6),(31.45,12.25,15.8),'oak_dark','counter_lower_shadow')
    box((.35,12.25,2.4),(31.65,13.25,15.95),'oak_light','wide_countertop',up='oak')
    box((.4,12.7,15.92),(31.6,13.05,16),'brass','counter_fine_brass_lip')
    # Recessed stalls at the rear keep the counter visually open for transactions.
    for x0,x1,kind in ((3.7,11.1,'apple'),(12.2,19.6,'pear'),(20.7,28.1,'cabbage')):
        box((x0,13.26,4.4),(x1,13.75,12.7),'oak_dark','produce_tray_base')
        for z in (4.4,12.15):box((x0,13.7,z),(x1,15.6,z+.55),'oak_light','produce_tray_slatted_rim')
        for x in (x0,x1-.55):box((x,13.7,4.6),(x+.55,15.6,12.35),'oak','produce_tray_side')
        # Deliberate rows, with a few larger pieces in the middle to form a pile.
        for row,z in enumerate((6.15,8.65,10.9)):
            for col in range(3):
                x=x0+1.0+col*2.25; y=14.2+(0.55 if row==1 and col==1 else 0)
                if kind=='apple':
                    box((x,y,z),(x+1.9,y+1.6,z+1.7),kind,'red_apple_body',up='apple_top')
                    box((x+.3,y-.25,z+.25),(x+1.6,y+1.95,z+1.4),kind,'red_apple_shoulders')
                    box((x+.85,y+1.95,z+.65),(x+1.1,y+2.35,z+.9),'stem','apple_stem')
                elif kind=='pear':
                    box((x,y,z),(x+1.8,y+1.3,z+1.7),kind,'golden_pear_body')
                    box((x+.25,y+1.3,z+.25),(x+1.55,y+2.0,z+1.45),kind,'golden_pear_neck')
                    box((x+.65,y+2.0,z+.6),(x+1.1,y+2.35,z+1.05),'stem','pear_stem')
                else:
                    box((x,y,z),(x+1.95,y+1.8,z+1.85),kind,'cabbage_head')
                    box((x-.15,y+.35,z+.3),(x+2.1,y+1.5,z+1.55),'leaf','outer_cabbage_leaves')
                    box((x+.35,y+1.8,z+.35),(x+1.55,y+2.15,z+1.45),kind,'cabbage_crown')
    # Barrels and folded sacks underneath the counter are genuine modelled shapes.
    bx,bz=5.1,6.3
    box((bx,0.5,bz+.5),(bx+5.6,7.2,bz+5.1),'oak_post','barrel_middle')
    box((bx+.5,.5,bz),(bx+5.1,7.2,bz+5.6),'oak_post','barrel_round_shoulders')
    for y in (1.1,5.8):
        box((bx-.1,y,bz+.45),(bx+5.7,y+.65,bz+5.15),'iron','barrel_hoop_across')
        box((bx+.45,y,bz-.1),(bx+5.15,y+.65,bz+5.7),'iron','barrel_hoop_sides')
    box((bx+.5,7.2,bz+.5),(bx+5.1,7.55,bz+5.1),'oak_light','barrel_lid')
    box((21.8,.3,6.0),(27.0,4.9,11.3),'sack','grain_sack_bottom')
    box((22.4,4.9,6.5),(26.4,7.1,10.8),'sack','grain_sack_shoulders')
    box((23.35,7.1,7.5),(25.45,7.75,9.8),'oak_dark','tied_sack_neck')
    box((23.5,7.75,7.6),(25.3,8.6,9.6),'sack','tied_sack_top')
    # The hanging sign is shallow and fully inside the front face of the footprint.
    for x in (11.9,19.5):box((x,9.6,15.15),(x+.35,12.1,15.5),'iron','sign_hanger')
    box((11.2,5.6,15.0),(20.8,10.8,15.6),'oak_dark','framed_coin_sign',south='market_sign')
    # High back beam and front support sit immediately beneath the cloth.
    box((.65,29.9,1.3),(31.35,31.15,3.1),'oak_dark','rear_roof_beam')
    box((.6,24.8,13.3),(31.4,25.9,15.1),'oak_dark','front_awning_beam')
    # Eight alternating strips share a physically sloped cloth surface. All
    # rotated corners remain inside the actual upper block's 0..16 volume.
    for stripe in range(8):
        x0,x1=stripe*4,(stripe+1)*4;texture='cream' if stripe%2 else color
        box((x0,27.85,1.0),(x1,28.55,15.0),texture,'sloping_canvas_stripe',rotation={'origin':[16,28.2,8],'axis':'x','angle':22.5,'rescale':False})
        box((x0,25.15,14.75),(x1,25.8,15.75),texture,'awning_stitched_front_edge')
        box((x0,23.95,15.15),(x1,25.35,15.6),texture,'scalloped_valance_top')
        box((x0+.5,23.6,15.15),(x1-.5,24.1,15.6),texture,'scalloped_valance_step')
        box((x0+1.2,23.4,15.15),(x1-1.2,23.7,15.6),texture,'scalloped_valance_tip')
        # Tiny stitched cream piping on each colored scallop edge.
        if stripe%2==0:box((x0+.55,23.62,15.61),(x1-.55,23.8,15.69),'cream','contrasting_scallop_stitch')
    # Canopy ties and muted brass end caps give the silhouette a finished edge.
    for x in (1.35,29.25):
        box((x,24.25,15.62),(x+1.1,25.25,15.8),'brass','canopy_tie_plate')
    # Restrained ivy hugs only one back upright, rather than obscuring merchandise.
    for i,(x,y,z) in enumerate(((.7,5.2,2.8),(.65,8.8,2.85),(.8,17.5,2.75),(.55,20.0,2.8))):
        box((x,y,z),(x+1.7,y+1.2,z+.35),'leaf','little_ivy_leaf')
        box((x+.6,y-.5,z+.05),(x+.9,y+1.4,z+.2),'stem','ivy_stalk')
    return elements


counts={}
for color in ('crimson','sage'):
    name=color+'_stall';elements=geometry(color);textures={**TEXTURES,color:'realmmarkets:block/'+color}
    parts=[[] for _ in range(4)]
    for e in elements:
        # Boxes crossing a part boundary are sliced with UV continuity. The only
        # rotated elements are roof strips wholly in an upper part.
        for part in range(4):
            ox=(part%2)*16;oy=(part//2)*16
            a=[max(e['from'][0],ox),max(e['from'][1],oy),e['from'][2]]
            b=[min(e['to'][0],ox+16),min(e['to'][1],oy+16),e['to'][2]]
            if any(b[i]<=a[i] for i in range(3)):continue
            n=deepcopy(e)
            n['from']=[round(a[0]-ox,4),round(a[1]-oy,4),a[2]]
            n['to']=[round(b[0]-ox,4),round(b[1]-oy,4),b[2]]
            if 'rotation' in n:
                n['rotation']['origin'][0]-=ox;n['rotation']['origin'][1]-=oy
            # Recompute exact UV segment for split faces from the source element.
            ex,ey,ez=e['from'];fx,fy,fz=e['to']
            for side,face in n['faces'].items():
                if side=='south' and e['name']=='framed_coin_sign':
                    face['uv']=[(a[0]-ex)/(fx-ex)*16,(fy-b[1])/(fy-ey)*16,(b[0]-ex)/(fx-ex)*16,(fy-a[1])/(fy-ey)*16]
                elif side in ('north','south'):
                    su=min(1,16/(fx-ex));sv=min(1,16/(fy-ey))
                    face['uv']=[(a[0]-ex)*su,(fy-b[1])*sv,(b[0]-ex)*su,(fy-a[1])*sv]
                elif side in ('east','west'):
                    su=min(1,16/(fz-ez));sv=min(1,16/(fy-ey))
                    face['uv']=[(a[2]-ez)*su,(fy-b[1])*sv,(b[2]-ez)*su,(fy-a[1])*sv]
                else:
                    su=min(1,16/(fx-ex));sv=min(1,16/(fz-ez))
                    face['uv']=[(a[0]-ex)*su,(a[2]-ez)*sv,(b[0]-ex)*su,(b[2]-ez)*sv]
                face['uv']=[round(q,4) for q in face['uv']]
            for point in (n['from'],n['to']):assert all(0<=v<=16 for v in point),(name,part,n)
            parts[part].append(n)
    for part,els in enumerate(parts):
        write(ASSETS/f'models/block/{name}_part{part}.json',{'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':True,'textures':textures,'elements':els})
    variants={}
    for direction,rotation in [('south',0),('west',90),('north',180),('east',270)]:
        for part in range(4):variants[f'facing={direction},part={part}']={'model':f'realmmarkets:block/{name}_part{part}',**({'y':rotation} if rotation else {})}
    write(ASSETS/f'blockstates/{name}.json',{'variants':variants})
    item=deepcopy(elements)
    for e in item:
        for p in ('from','to'):
            e[p][0]-=8;e[p][1]-=8
        if 'rotation' in e:
            e['rotation']['origin'][0]-=8;e['rotation']['origin'][1]-=8
    write(ASSETS/f'models/item/{name}.json',{'parent':'minecraft:block/block','render_type':'minecraft:cutout','ambientocclusion':True,'textures':textures,'elements':item,'display':{
        'gui':{'rotation':[25,215,0],'translation':[0,.4,0],'scale':[.47,.47,.47]},
        'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[.25,.25,.25]},
        'fixed':{'rotation':[0,180,0],'translation':[0,0,0],'scale':[.4,.4,.4]},
        'firstperson_righthand':{'rotation':[0,35,0],'translation':[0,0,0],'scale':[.3,.3,.3]},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2,0],'scale':[.25,.25,.25]}}})
    write(RES/f'data/realmmarkets/recipe/{name}.json',{'type':'minecraft:crafting_shaped','category':'decorations','pattern':['WWW','PBP','S S'],'key':{
        'W':{'item':'minecraft:'+('red' if color=='crimson' else 'green')+'_wool'},'P':{'tag':'minecraft:planks'},'B':{'item':'minecraft:barrel'},'S':{'item':'minecraft:stick'}},'result':{'id':'realmmarkets:'+name,'count':1}})
    write(RES/f'data/realmmarkets/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'realmmarkets:'+name}],'conditions':[
        {'condition':'minecraft:block_state_property','block':'realmmarkets:'+name,'properties':{'part':'0'}},{'condition':'minecraft:survives_explosion'}]}]})
    counts[name]={'unsplitCuboids':len(elements),'partCuboids':[len(e) for e in parts],'itemFaces':len(elements)*6,'partFaces':[len(e)*6 for e in parts]}
write(RES/'data/minecraft/tags/block/mineable/axe.json',{'replace':False,'values':['realmmarkets:crimson_stall','realmmarkets:sage_stall']})
for protection in ('wither_immune', 'dragon_immune'):
    write(RES/f'data/minecraft/tags/block/{protection}.json',{'replace':False,'values':['realmmarkets:crimson_stall','realmmarkets:sage_stall']})
write(RES/'pack.mcmeta',{'pack':{'pack_format':34,'description':'Realm Markets — original hand-painted medieval stalls'}})
print(json.dumps(counts,indent=2))
