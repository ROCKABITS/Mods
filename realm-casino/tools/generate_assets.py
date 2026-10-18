#!/usr/bin/env python3
"""Reproducible original pixel artwork and native Realm Casino furniture.

No imported artwork: every texture, ornament and mesh is authored here. Models
are real Java Edition cuboids, with slot halves clipped to their own 16³ cells.
"""
from copy import deepcopy
from pathlib import Path
import json
import math
import random
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/realmcasino"
TEX = ASSETS / "textures/block"
TEX.mkdir(parents=True, exist_ok=True)
RNG = random.Random(1762026)
GOLD = (181, 133, 56, 255)
LIGHT = (242, 213, 143, 255)
DEEP = (91, 59, 29, 255)
GREEN = (23, 69, 52, 255)
INK = (17, 28, 27, 255)
CREAM = (241, 225, 184, 255)
RED = (138, 45, 46, 255)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def tint(base, amount):
    return tuple(max(0, min(255, v + amount)) for v in base[:3]) + (255,)


def noise(size, base, amount=3):
    im = Image.new("RGBA", size)
    im.putdata([tint(base, RNG.randint(-amount, amount)) for _ in range(size[0] * size[1])])
    return im


def save(name, im):
    im.save(TEX / (name + ".png"))


def crown(d, x, y, s=1, gold=GOLD):
    for a, b, c, e in ((0, 2, 2, 7), (2, 4, 4, 8), (4, 0, 6, 8),
                       (6, 4, 8, 8), (8, 2, 10, 7), (1, 7, 9, 9)):
        d.rectangle((round(x+a*s), round(y+b*s), round(x+c*s), round(y+e*s)), fill=gold)
    d.line((round(x+2*s), round(y+8*s), round(x+8*s), round(y+8*s)), fill=LIGHT, width=max(1, round(s)))


def diamond(d, cx, cy, r, fill=RED):
    d.polygon(((cx, cy-r), (cx+r, cy), (cx, cy+r), (cx-r, cy)), fill=fill)


def heart(d, x, y, s=1, fill=RED):
    d.polygon([(x+0*s,y+2*s),(x+1*s,y),(x+3*s,y),(x+4*s,y+1*s),
               (x+5*s,y),(x+7*s,y),(x+8*s,y+2*s),(x+8*s,y+4*s),
               (x+4*s,y+8*s),(x,y+4*s)], fill=fill)


def star(d, x, y, r=5, fill=LIGHT):
    pts=[]
    for i in range(8):
        a=i*math.pi/4-math.pi/2; radius=r if i%2==0 else r*.30
        pts.append((round(x+math.cos(a)*radius),round(y+math.sin(a)*radius)))
    d.polygon(pts,fill=fill)


def digits(d, x, y, value, fill=CREAM):
    glyphs={"0":["111","101","101","101","111"],"1":["010","110","010","010","111"],
            "2":["111","001","111","100","111"],"3":["111","001","111","001","111"],
            "4":["101","101","111","001","001"],"5":["111","100","111","001","111"],
            "6":["111","100","111","101","111"],"7":["111","001","010","010","010"],
            "8":["111","101","111","101","111"],"9":["111","101","111","001","111"]}
    for i,c in enumerate(str(value)):
        for yy,row in enumerate(glyphs[c]):
            for xx,pixel in enumerate(row):
                if pixel=="1":d.point((x+i*4+xx,y+yy),fill=fill)


# Quiet walnut grain lets bevels and gold inlays define the silhouette.
for name, base in (("walnut", (73, 45, 31)), ("walnut_dark", (39, 27, 23)),
                   ("walnut_top", (98, 62, 38))):
    im=noise((32,32),base,3); d=ImageDraw.Draw(im)
    for y in (0,15,31):
        d.line((0,y,31,y),fill=tint(base,-18))
        if y<31:d.line((0,y+1,31,y+1),fill=tint(base,11))
    for _ in range(40):
        x,y=RNG.randrange(32),RNG.randrange(2,30)
        d.line((x,y,min(31,x+RNG.randrange(2,10)),y),fill=tint(base,RNG.choice((-12,9))))
    d.arc((15,5,27,11),0,300,fill=tint(base,-19))
    save(name,im)

im=noise((32,32),GOLD,3);d=ImageDraw.Draw(im)
for y in (0,8,16,24):
    d.line((0,y,31,y),fill=LIGHT);d.line((0,y+6,31,y+6),fill=(130,85,35,255))
save("brass",im)
for name,base in (("felt",GREEN),("wine",(84,30,39)),("black",INK)):
    im=noise((32,32),base,2);d=ImageDraw.Draw(im)
    for y in range(0,32,2):
        for x in range(y%4,32,4):d.point((x,y),fill=tint(base,6))
    save(name,im)
im=noise((32,32),(49,34,26),2);d=ImageDraw.Draw(im)
d.rectangle((1,1,30,30),outline=DEEP);d.rectangle((3,3,28,28),outline=GOLD)
for x,y in ((6,6),(25,6),(6,25),(25,25)):
    diamond(d,x,y,2,GOLD);d.point((x,y),fill=LIGHT)
for side in (-1,1):
    for i in range(5):
        x,y=16+side*(3+i*2),17-i
        d.line((x,y,x-side*2,y-2),fill=GOLD)
        d.point((x,y-2),fill=LIGHT)
crown(d,11,11,.9)
save("carved_panel",im)

# Jewel surfaces include pixel facets, rather than flat saturated squares.
for name,base in (("emerald",(22,113,74)),("ruby",(153,43,50)),("amber",(212,144,37))):
    im=Image.new("RGBA",(16,16),tint(base,-30));d=ImageDraw.Draw(im)
    d.polygon(((3,1),(12,1),(15,5),(15,11),(11,15),(4,15),(0,11),(0,5)),fill=base)
    d.polygon(((3,2),(12,2),(10,5),(5,5),(2,10),(1,5)),fill=tint(base,55))
    d.polygon(((5,5),(10,5),(12,10),(8,13),(3,10)),fill=tint(base,18))
    d.line((5,5,10,5,12,10),fill=tint(base,75))
    save(name,im)

# The marquee is an original embroidered triple-crown seal on wine velvet.
im=noise((64,32),(64,26,35),2);d=ImageDraw.Draw(im)
d.rectangle((1,1,62,30),outline=DEEP);d.rectangle((3,3,60,28),outline=GOLD)
d.line((6,5,57,5),fill=(205,167,95,255))
crown(d,23,7,1.7)
for x in (13,51):
    star(d,x,16,5,GOLD);d.point((x,16),fill=LIGHT)
for x in (7,57):
    for y in (9,23):diamond(d,x,y,1,LIGHT)
save("marquee",im)

# Reel faces are legible from across a room. Pale ivory bands and dark cavities
# make the three physical windows unmistakable even when the player UI is shut.
for name,kind in (("reel_crown","crown"),("reel_gem","gem"),("reel_star","star")):
    im=noise((32,64),CREAM,1);d=ImageDraw.Draw(im)
    for y in range(64):
        shade=round(-26*abs(y-31.5)/31.5)
        d.line((0,y,31,y),fill=tint(CREAM,shade))
    for y in (4,58):d.line((3,y,28,y),fill=(183,157,108,255))
    if kind=="crown":crown(d,6,23,1.8)
    elif kind=="gem":
        diamond(d,16,32,11,(25,109,77,255));diamond(d,16,31,7,(74,158,112,255))
        d.line((16,21,12,30,16,40),fill=(166,221,160,255))
    else:
        star(d,16,32,12,GOLD);star(d,16,32,7,LIGHT)
    save(name,im)

for name,base in (("chip_red",(142,41,48)),("chip_green",(30,109,79)),("chip_blue",(44,77,127)),("chip_black",(37,40,40))):
    im=noise((32,32),base,2);d=ImageDraw.Draw(im)
    d.ellipse((1,1,30,30),fill=tint(base,7),outline=CREAM,width=2)
    d.ellipse((5,5,26,26),outline=GOLD)
    d.ellipse((8,8,23,23),outline=tint(base,30))
    for angle in range(0,360,60):
        a=math.radians(angle)
        x,y=16+round(math.cos(a)*13),16+round(math.sin(a)*13)
        d.rectangle((x-1,y-1,x+1,y+1),fill=CREAM)
    crown(d,11,11,.9,CREAM)
    save(name,im)
    edge=noise((16,16),base,2);ed=ImageDraw.Draw(edge)
    for x in (1,7,13):ed.rectangle((x,1,x+1,14),fill=CREAM)
    for y in (0,7,15):ed.line((0,y,15,y),fill=DEEP if y!=7 else GOLD)
    save(name+"_edge",edge)
im=Image.new("RGBA",(16,16),CREAM);d=ImageDraw.Draw(im)
for y in (1,6,11):d.line((0,y,15,y),fill=(150,111,55,255))
for x in (1,6,11):d.line((x,0,x,15),fill=(235,216,169,255))
save("chip_edge",im)

for name,kind in (("card_ace","ace"),("card_king","king"),("card_back","back")):
    im=noise((32,48),CREAM,1);d=ImageDraw.Draw(im)
    d.rectangle((0,0,31,47),outline=(142,111,70,255));d.rectangle((2,2,29,45),outline=GOLD)
    if kind=="back":
        d.rectangle((4,4,27,43),fill=(51,38,42,255),outline=GOLD)
        for y in range(7,42,5):
            for x in range(7,26,5):diamond(d,x,y,2,(118,69,65,255))
        d.rectangle((8,16,23,31),fill=(82,32,43,255),outline=GOLD)
        crown(d,11,19,.9)
    elif kind=="ace":
        # Hand-pixelled corner A, a large red diamond and inverted lower mark.
        d.line((5,10,7,5,9,10),fill=RED);d.line((6,8,8,8),fill=RED)
        diamond(d,7,14,2,RED);diamond(d,16,25,8,RED)
        d.line((23,37,25,42,27,37),fill=RED);d.line((24,39,26,39),fill=RED)
    else:
        d.line((5,5,5,11),fill=INK);d.line((5,8,9,5),fill=INK);d.line((6,8,9,11),fill=INK)
        d.rectangle((8,14,23,35),fill=(62,89,65,255),outline=GOLD)
        crown(d,10,17,1.2);d.rectangle((13,29,19,33),fill=GOLD)
    save(name,im)

# A standard European roulette wheel. Numbered pockets use tiny engraved marks
# in block art; the exact numbers and fair server rules are in the full-size UI.
im=Image.new("RGBA",(128,128));d=ImageDraw.Draw(im)
d.ellipse((2,2,125,125),fill=(42,29,23,255),outline=DEEP,width=3)
d.ellipse((5,5,122,122),fill=GOLD,outline=LIGHT,width=2)
for i in range(37):
    a=-90+i*360/37;b=-90+(i+1)*360/37
    fill=GREEN if i==0 else (RED if i%2 else (26,31,29,255))
    d.pieslice((9,9,118,118),a,b,fill=fill,outline=DEEP)
    mid=math.radians((a+b)/2)
    x,y=64+math.cos(mid)*47,64+math.sin(mid)*47
    d.rectangle((round(x)-1,round(y)-1,round(x)+1,round(y)+1),fill=CREAM)
d.ellipse((27,27,100,100),fill=GOLD,outline=LIGHT,width=2)
d.ellipse((31,31,96,96),fill=(93,58,35,255),outline=DEEP,width=2)
d.ellipse((39,39,88,88),fill=(120,78,43,255),outline=GOLD,width=2)
for a in range(0,360,45):
    r=math.radians(a);d.line((64+math.cos(r)*9,64+math.sin(r)*9,64+math.cos(r)*24,64+math.sin(r)*24),fill=GOLD,width=2)
d.ellipse((56,56,71,71),fill=GOLD,outline=LIGHT)
d.ellipse((59,59,68,68),fill=(217,176,94,255))
save("roulette_wheel",im)

im=noise((64,128),GREEN,2);d=ImageDraw.Draw(im)
d.rectangle((1,1,62,126),outline=GOLD)
d.rectangle((5,4,58,19),outline=CREAM);digits(d,30,9,0)
reds={1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36}
for row in range(12):
    for col in range(3):
        n=row*3+col+1;x=5+col*18;y=20+row*8
        d.rectangle((x,y,x+17,y+7),fill=RED if n in reds else INK,outline=(179,165,112,255))
        # Numerals are actual drawn glyphs in this generated tiny map.
        digits(d,x+(7 if n<10 else 5),y+1,n)
d.line((5,118,58,118),fill=GOLD)
save("roulette_layout",im)

im=noise((128,128),GREEN,2);d=ImageDraw.Draw(im)
d.rectangle((1,1,126,126),outline=GOLD,width=2)
d.arc((13,31,114,144),190,350,fill=(189,173,113,255),width=2)
d.arc((25,42,102,129),190,350,fill=(75,112,72,255),width=2)
crown(d,49,14,2.7)
# Subtle engraved suited symbols and three physical card placement fields.
for x,y in ((28,38),(99,38)):diamond(d,x,y,3,GOLD)
for x,y in ((26,81),(55,94),(85,81)):
    d.rounded_rectangle((x,y,x+17,y+27),radius=3,outline=(145,143,91,255),width=1)
d.text((43,57),"BLACKJACK",fill=(224,204,143,255))
save("blackjack_felt",im)

# One shared atlas keeps the physical animated reels and the menu symbols
# visually identical. The server owns these eight immutable symbol indices.
atlas=Image.new("RGBA",(128,16))
for index in range(8):
    glyph=Image.new("RGBA",(16,16));d=ImageDraw.Draw(glyph)
    if index==0:
        crown(d,2,3,1.1);d.rectangle((7,11,8,12),fill=RED)
    elif index in (1,2,3):
        base=((166,46,58,255),(54,94,177,255),(35,133,87,255))[index-1]
        d.polygon(((4,2),(11,2),(14,6),(14,10),(8,15),(1,10),(1,6)),fill=tint(base,-30))
        d.polygon(((4,3),(11,3),(13,7),(8,13),(2,7)),fill=base)
        d.polygon(((4,3),(11,3),(9,6),(5,6),(2,7)),fill=tint(base,70))
        d.polygon(((5,6),(9,6),(11,9),(8,13),(4,9)),fill=tint(base,20))
        d.line((5,6,9,6,11,9),fill=tint(base,100))
    elif index==4:
        d.rectangle((7,1,9,2),fill=DEEP);d.rectangle((6,2,10,3),fill=GOLD)
        d.polygon(((5,3),(11,3),(12,5),(12,9),(14,11),(14,12),(2,12),(2,11),(4,9),(4,5)),fill=GOLD)
        d.line((5,5,5,9,4,10),fill=LIGHT,width=2)
        d.rectangle((3,12,13,13),fill=LIGHT);d.rectangle((7,14,9,15),fill=DEEP)
    elif index==5:
        d.ellipse((1,1,14,14),fill=GOLD);d.ellipse((2,2,13,13),fill=LIGHT)
        d.ellipse((7,-1,17,10),fill=(0,0,0,0));star(d,12,10,2,CREAM)
    elif index==6:
        d.line((3,2,2,10,4,13,11,13,13,10,12,2),fill=DEEP,width=4)
        d.line((3,2,3,10,5,12,10,12,12,10,12,2),fill=GOLD,width=3)
        d.line((3,3,3,9,5,11,10,11),fill=LIGHT)
        for x,y in ((3,5),(3,8),(6,12),(9,12),(12,5),(12,8)):d.point((x,y),fill=DEEP)
        d.rectangle((1,1,5,3),fill=LIGHT);d.rectangle((10,1,14,3),fill=LIGHT)
    else:
        d.line((5,10,7,3,10,2,11,10),fill=(68,119,54,255),width=1)
        d.polygon(((7,3),(9,0),(13,1),(10,4)),fill=(72,140,64,255))
        d.ellipse((1,8,8,15),fill=(111,30,43,255));d.ellipse((8,7,15,14),fill=(151,38,55,255))
        d.rectangle((3,9,4,10),fill=(236,119,111,255));d.rectangle((10,8,11,9),fill=(248,133,116,255))
    atlas.alpha_composite(glyph,(index*16,0))
save("symbols",atlas)

TEXTURES={p.stem:"realmcasino:block/"+p.stem for p in sorted(TEX.glob("*.png"))}
TEXTURES["particle"]="realmcasino:block/walnut"
ELEMENTS=[]


def box(start,end,texture,name,*,up=None,north=None,south=None,east=None,west=None,rotation=None,full=False):
    dx,dy,dz=(end[i]-start[i] for i in range(3));faces={}
    for face in ("north","south","east","west","up","down"):
        w,h=(dx,dz) if face in ("up","down") else (dx if face in ("north","south") else dz,dy)
        faces[face]={"uv":[0,0,16 if full else min(16,w),16 if full else min(16,h)],"texture":"#"+texture}
    for face,override in (("up",up),("north",north),("south",south),("east",east),("west",west)):
        if override:faces[face]={"uv":[0,0,16,16],"texture":"#"+override}
    e={"name":name,"from":start,"to":end,"faces":faces}
    if rotation:e["rotation"]=rotation
    ELEMENTS.append(e)


def border(x0,y,z0,x1,z1,thickness=.2,texture="brass",name="gold_inlay"):
    box([x0,y,z0],[x1,y+.12,z0+thickness],texture,name+"_front")
    box([x0,y,z1-thickness],[x1,y+.12,z1],texture,name+"_back")
    box([x0,y,z0+thickness],[x0+thickness,y+.12,z1-thickness],texture,name+"_left")
    box([x1-thickness,y,z0+thickness],[x1,y+.12,z1-thickness],texture,name+"_right")


def leg(x,z):
    box([x,0,z],[x+2.3,.5,z+2.3],"walnut_dark","carved_foot_plinth")
    box([x+.16,.5,z+.16],[x+2.14,1,z+2.14],"walnut","carved_foot_bevel")
    box([x+.43,1,z+.43],[x+1.87,9.9,z+1.87],"walnut","turned_walnut_leg")
    box([x+.26,1.4,z+.26],[x+2.04,1.72,z+2.04],"brass","leg_lower_brass_collar")
    box([x+.26,7.8,z+.26],[x+2.04,8.16,z+2.04],"brass","leg_upper_brass_collar")
    box([x+.61,2.9,z+.38],[x+1.69,6.7,z+.46],"brass","leg_front_fluting")
    box([x+.38,2.9,z+.61],[x+.46,6.7,z+1.69],"brass","leg_side_fluting")
    box([x+.2,8.2,z+.2],[x+2.1,10.6,z+2.1],"walnut_dark","carved_leg_capital")


def table_base():
    for x in (1,12.7):
        for z in (1,12.7):leg(x,z)
    for x in (1.55,13.35):
        box([x,3.0,2.3],[x+1.1,3.65,13.7],"walnut_dark","side_stretcher")
        box([x,8.6,2.1],[x+1.1,10.85,13.9],"walnut","side_apron")
    box([2,3.05,7.45],[14,3.75,8.55],"walnut_dark","cross_stretcher")
    for z in (1.65,13.45):
        box([2,8.5,z],[14,10.6,z+.9],"walnut_dark","front_back_apron")
        box([4.05,8.8,z-.04],[11.95,10.4,z+.94],"carved_panel","carved_gilt_apron",north="carved_panel",south="carved_panel")
    box([.25,10.6,.25],[15.75,11.05,15.75],"walnut_dark","table_lower_moulding")
    box([.5,11.05,.5],[15.5,11.85,15.5],"walnut","solid_walnut_top",up="walnut_top")
    box([.22,11.85,.22],[15.78,12.16,15.78],"brass","fine_brass_table_lip")
    box([.45,12.16,.45],[15.55,12.52,15.55],"walnut_dark","upper_bevel")
    for z in (.15,15.72):box([.7,11.35,z],[15.3,11.5,z+.12],"brass","fine_edge_gold_line")
    for x in (.15,15.72):box([x,11.35,.7],[x+.12,11.5,15.3],"brass","fine_side_gold_line")
    for x,z in ((1,1),(13.8,1),(1,13.8),(13.8,13.8)):
        box([x,12.52,z],[x+1.2,12.66,z+1.2],"brass","corner_gilded_mount")
        box([x+.36,12.66,z+.36],[x+.84,12.85,z+.84],"emerald","corner_emerald_rivet",full=True)


def chip_stack(x,y,z,texture,count=4):
    for i in range(count):
        dy=y+i*.24
        box([x+.12,dy,z],[x+1.68,dy+.16,z+1.8],texture+"_edge","poker_chip_edge",full=True)
        box([x,dy,z+.12],[x+1.8,dy+.16,z+1.68],texture+"_edge","poker_chip_octagonal_bevel",full=True)
        box([x+.12,dy+.16,z+.12],[x+1.68,dy+.23,z+1.68],texture,"poker_chip_crest",up=texture)


def model(elements):
    return {"parent":"minecraft:block/block","render_type":"minecraft:cutout","ambientocclusion":True,"textures":TEXTURES,"elements":elements}


def split(elements,minimum):
    out=[]
    for old in elements:
        lo=max(minimum,old["from"][1]);hi=min(minimum+16,old["to"][1])
        if hi<=lo:continue
        e=deepcopy(old);e["from"][1]=round(lo-minimum,5);e["to"][1]=round(hi-minimum,5)
        if "rotation" in e:e["rotation"]["origin"][1]-=minimum
        height=old["to"][1]-old["from"][1]
        for side in ("north","south","east","west"):
            uv=old["faces"][side]["uv"]
            t=(old["to"][1]-hi)/height;b=(old["to"][1]-lo)/height
            e["faces"][side]["uv"]=[uv[0],round(uv[1]+(uv[3]-uv[1])*t,5),uv[2],round(uv[1]+(uv[3]-uv[1])*b,5)]
        if lo>old["from"][1]:del e["faces"]["down"]
        if hi<old["to"][1]:del e["faces"]["up"]
        assert all(0<=v<=16 for p in (e["from"],e["to"]) for v in p),e["name"]
        out.append(e)
    return out


def publish(name,elements,tall=False):
    if tall:
        for half,offset in (("lower",0),("upper",16)):
            write(ASSETS/f"models/block/{name}_{half}.json",model(split(elements,offset)))
        variants={f"facing={f},half={h}":{"model":f"realmcasino:block/{name}_{h}",**({"y":a} if a else {})}
                  for f,a in (("north",0),("east",90),("south",180),("west",270)) for h in ("lower","upper")}
    else:
        assert all(0<=v<=16 for e in elements for p in (e["from"],e["to"]) for v in p)
        write(ASSETS/f"models/block/{name}.json",model(elements))
        variants={f"facing={f}":{"model":f"realmcasino:block/{name}",**({"y":a} if a else {})}
                  for f,a in (("north",0),("east",90),("south",180),("west",270))}
    write(ASSETS/f"blockstates/{name}.json",{"variants":variants})
    scale=.45 if tall else .72
    item=model(elements)
    item["display"]={
        "gui":{"rotation":[28,225,0],"translation":[0,-3.5 if tall else 0,0],"scale":[scale]*3},
        "ground":{"translation":[0,1,0],"scale":[.22 if tall else .35]*3},
        "fixed":{"rotation":[0,180,0],"translation":[0,-4 if tall else 0,0],"scale":[scale]*3},
        "firstperson_righthand":{"rotation":[0,35,0],"translation":[1,-2,0],"scale":[.35 if tall else .55]*3},
        "firstperson_lefthand":{"rotation":[0,225,0],"translation":[1,-2,0],"scale":[.35 if tall else .55]*3},
        "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,1.5,0],"scale":[.27 if tall else .42]*3},
        "thirdperson_lefthand":{"rotation":[75,225,0],"translation":[0,1.5,0],"scale":[.27 if tall else .42]*3}}
    write(ASSETS/f"models/item/{name}.json",item)
    conditions=[{"condition":"minecraft:survives_explosion"}]
    if tall:conditions.insert(0,{"condition":"minecraft:block_state_property","block":f"realmcasino:{name}","properties":{"half":"lower"}})
    write(RES/f"data/realmcasino/loot_table/blocks/{name}.json",{"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":f"realmcasino:{name}"}],"conditions":conditions}]})
    print(name,len(elements),"original cuboids")


# Crown cabinet: a broad stepped plinth, tapered pilasters, sculpted crown
# marquee, three separate mechanical reels, tactile brass lever and payout tray.
ELEMENTS=[]
box([1,0,2],[13.7,.6,14.4],"walnut_dark","slot_broad_plinth")
box([1.3,.6,2.3],[13.4,1.2,14.1],"walnut","slot_plinth_bevel")
box([1,1.2,2],[13.7,1.55,14.4],"brass","slot_plinth_brass_step")
box([1.55,1.55,2.55],[13.15,4.1,13.85],"walnut_dark","slot_sturdy_base")
box([2,3.8,3],[12.7,24.8,13.2],"walnut","slot_cabinet")
box([2.55,5.1,2.88],[12.15,13.65,3.05],"carved_panel","lower_ornate_door",north="carved_panel")
for x in (1.8,11.95):
    box([x,3.75,2.6],[x+1.05,25.6,4.2],"walnut_dark","cabinet_front_pilaster")
    box([x+.35,5,2.45],[x+.7,24.4,2.6],"brass","gilt_vertical_flute")
    for y in (4.2,12.7,23.8):box([x-.1,y,2.45],[x+1.15,y+.38,4.35],"brass","pilaster_collar")
box([1.5,13.5,1.7],[13.25,13.9,5.0],"walnut_dark","control_shelf_lower")
box([1.35,13.9,1.55],[13.4,14.3,5.0],"brass","control_shelf_brass_bevel")
box([1.65,14.3,1.9],[13.1,14.65,4.9],"walnut","control_shelf_upper")
box([3.8,14.65,2.25],[7.8,14.8,4.3],"wine","velvet_control_pad",up="wine")
box([4.5,14.8,2.7],[6.9,15.03,3.8],"brass","large_spin_button_rim")
box([4.78,15.03,2.83],[6.62,15.25,3.67],"emerald","emerald_spin_button",up="emerald")
box([9.35,14.65,2.5],[11.3,14.83,4.2],"brass","coin_slot_plate")
box([10.12,14.83,2.82],[10.48,14.87,3.9],"black","coin_slot")
# Deep recessed payout bay, polished lip and one little loose gold chip.
box([3.6,6,2.4],[11.15,9.45,2.9],"black","payout_dark_recess")
box([3.25,5.75,.95],[11.5,6.15,3.7],"brass","payout_tray_floor")
box([3.25,6.15,.95],[11.5,6.65,1.25],"brass","payout_tray_front_lip")
for x in (3.25,11.15):box([x,6.15,1.25],[x+.35,7.3,3.7],"brass","payout_tray_side")
box([5.3,6.15,1.6],[6.8,6.32,2.95],"amber","loose_gold_token",up="chip_red")
# The entire reel display is framed by a thick dimensional brass bezel.
box([2.65,16.0,2.5],[12.1,23.5,3],"black","reel_window_shadow")
box([2.85,16.35,2.38],[11.9,23.15,2.65],"brass","reel_outer_bezel")
box([3.15,16.65,2.25],[11.6,22.85,2.45],"black","reel_deep_inset")
for x,texture in ((3.5,"reel_crown"),(6.22,"reel_gem"),(8.94,"reel_star")):
    box([x,17.05,2.1],[x+2.25,22.45,2.28],texture,"ivory_mechanical_"+texture,north=texture)
    for y in (17.05,22.22):box([x-.1,y,1.99],[x+2.35,y+.2,2.2],"brass","reel_individual_top_bottom_trim")
    for edge in (x-.12,x+2.27):box([edge,17.25,1.99],[edge+.1,22.2,2.2],"brass","reel_individual_side_trim")
box([2.72,19.32,1.85],[3.38,19.72,2.3],"ruby","left_payline_pointer",full=True)
box([11.2,19.32,1.85],[11.88,19.72,2.3],"ruby","right_payline_pointer",full=True)
for x in (3,6.1,9.2,12.25):box([x,23.5,2.25],[x+.3,23.82,2.55],"amber","amber_indicator",full=True)
# Rear cabinet has an actual brass inset service-door hinge and vent ribs.
box([3.1,5.2,13.2],[11.6,22.9,13.34],"walnut_dark","rear_service_panel")
for x in (3.45,11):
    for y in (7,20):box([x,y,13.34],[x+.4,y+1.1,13.52],"brass","rear_service_hinge")
for y in (17.5,18.5,19.5):box([5,y,13.35],[9.7,y+.23,13.43],"black","rear_vent")
for x in (2.05,12.38):box([x,5,4.2],[x+.22,23.8,12.6],"wine","side_velvet_inlay")
# Layered side marquetry and restrained gilded rivets make the freestanding
# cabinet attractive from every facing, rather than only in a front screenshot.
for x in (1.96,12.63):
    box([x,9.3,5.45],[x+.13,14.2,11.15],"carved_panel","crowned_side_marquetry",east="carved_panel",west="carved_panel")
    for z in (4.5,12.1):
        box([x,5.2,z],[x+.15,23.4,z+.16],"brass","side_fine_gilt_border")
    for y in (5.2,23.2):
        box([x,y,4.5],[x+.15,y+.16,12.26],"brass","side_horizontal_gilt_border")
    for y in (6.2,22.3):
        for z in (5.2,11.5):
            box([x-.04,y,z],[x+.19,y+.35,z+.35],"brass","side_burnished_rivet")
for x in (3.45,11.45):
    for y in (10.3,12.4):
        box([x,y,2.76],[x+.35,y+.35,2.94],"brass","door_corner_rosette")
box([7.15,10.1,2.65],[7.6,11.15,2.9],"brass","lower_door_lock_plate")
box([7.3,10.35,2.6],[7.45,10.9,2.66],"black","lower_door_keyhole")
for y in (8.6,15.3):
    box([3.35,y,13.35],[11.35,y+.14,13.48],"brass","rear_panel_gold_divider")
for x in (3.15,11.4):
    box([x,5.3,13.35],[x+.13,22.8,13.48],"brass","rear_panel_gilt_border")
box([1.65,24.55,2.3],[13.05,25.1,13.6],"brass","marquee_base_cornice")
box([1.35,25.1,2.0],[13.35,25.7,13.9],"walnut_dark","marquee_lower_bevel")
box([1.9,25.7,2.6],[12.8,29.1,6.6],"walnut","crown_marquee_cabinet")
box([2.2,26,2.35],[12.5,28.85,2.65],"marquee","wine_embroidered_marquee",north="marquee")
for x in (1.7,12.7):
    box([x,25.4,2.25],[x+.3,29.35,6.9],"brass","marquee_gilt_endcap")
    box([x-.13,28.95,3.1],[x+.43,29.7,4.2],"amber","marquee_amber_finial",full=True)
box([1.35,29.1,2.2],[13.35,29.5,7.1],"brass","marquee_top_cornice")
for x in (2.35,4.3,6.25,8.2,10.15,12.1):
    box([x,25.83,2.18],[x+.22,26.05,2.4],"amber","marquee_small_lamplight",full=True)
# Stepped five-prong crown, fully contained in the upper block.
box([4.4,29.5,3.4],[10.3,30.05,5.4],"brass","crown_band")
for x,y,w in ((4.5,31.1,.8),(5.3,30.5,1),(6.3,31.85,2.1),(8.4,30.5,1),(9.4,31.1,.8)):
    box([x,30.05,3.65],[x+w,y,5.15],"brass","crown_prong")
box([6.95,30.35,3.45],[7.75,31.25,3.68],"ruby","crown_centre_ruby",full=True)
# Lever axle, segmented handle and tactile red knob on cabinet's right.
box([12.7,17.6,7.2],[14.15,19.8,9.6],"brass","lever_mount")
box([14.15,18.1,7.7],[14.9,19.3,9.1],"walnut_dark","lever_axle")
box([14.55,19.0,8.1],[15.0,23.3,8.55],"brass","lever_stem")
box([14.17,23.1,7.73],[15.38,24.25,8.94],"ruby","ruby_lever_knob",full=True)
box([14.38,24.25,7.94],[15.17,24.55,8.73],"ruby","lever_knob_bevel",full=True)
publish("slot_machine",ELEMENTS,True)

# Roulette combines a physically sculpted octagonal wheel with an ivory ball,
# small spindle, numbered betting mat and stacks of octagonal coloured chips.
ELEMENTS=[];table_base()
box([1.6,12.52,1.6],[14.4,12.64,14.4],"felt","roulette_green_playing_surface")
border(1.65,12.65,1.65,14.35,14.35,.13)
box([1.7,12.7,1.7],[8.3,12.83,14.25],"felt","numbered_betting_mat",up="roulette_layout")
box([8.5,12.66,3.25],[14.8,12.95,9.55],"walnut_dark","wheel_octagonal_base")
box([8.95,12.66,2.8],[14.35,12.95,10.0],"walnut_dark","wheel_octagonal_base_bevel")
box([8.55,12.95,3.3],[14.75,13.22,9.5],"brass","wheel_outer_brass_lip")
box([8.95,12.95,2.9],[14.35,13.22,9.9],"brass","wheel_outer_brass_corner")
box([8.8,13.22,3.3],[14.5,13.37,9.5],"walnut","wheel_visible_numbered_face",up="roulette_wheel")
box([9.15,13.2,3.05],[14.15,13.34,9.75],"walnut","wheel_octagonal_face_bevel",up="roulette_wheel")
box([10.5,13.36,4.9],[12.8,13.6,7.2],"walnut_top","wheel_centre_raised_hub",up="walnut_top")
box([11.2,13.6,5.6],[12.1,14.35,6.5],"brass","wheel_spindle_base")
box([11.48,14.35,5.88],[11.82,14.9,6.22],"brass","wheel_spindle_finial")
box([9.9,14.18,5.9],[13.4,14.37,6.2],"brass","wheel_turning_crossbar_x")
box([11.5,14.18,4.3],[11.8,14.37,7.8],"brass","wheel_turning_crossbar_z")
# The one ivory ball is drawn by the native block-entity renderer so its
# position follows the committed server result; no second static ball is baked.
for x,z,t,c in ((8.7,11.1,"chip_red",5),(10.85,11.65,"chip_green",7),(12.9,11.0,"chip_black",4)):
    chip_stack(x,12.68,z,t,c)
publish("roulette_table",ELEMENTS)

# Blackjack has a stepped horseshoe rail rather than a flat green cube. Two
# face-up ivory cards, an ornate face-down dealer deck and an inset chip rack
# give each side a distinct silhouette.
ELEMENTS=[];table_base()
box([1.5,12.52,1.5],[14.5,12.7,14.5],"felt","blackjack_embroidered_felt",up="blackjack_felt")
border(1.55,12.71,1.55,14.45,14.45,.12)
for x0,x1,z0,z1 in ((1,15,13.4,14.7),(1,2.2,4,13.4),(13.8,15,4,13.4),(2.2,4,2.2,4),(12,13.8,2.2,4),(4,12,1.2,2.5)):
    box([x0,12.7,z0],[x1,13.15,z1],"wine","padded_horseshoe_player_rail")
    box([x0,13.15,z0],[x1,13.26,z1],"wine","rounded_leather_rail_crown")
    # Narrow inset brass running below the burgundy padded edge.
    box([x0,12.64,z0],[x1,12.74,z1],"brass","rail_lower_gold_binding")
for x,z,t,c in ((2.8,10.8,"chip_red",5),(5.0,11.4,"chip_blue",3),(11.1,10.7,"chip_black",6)):
    chip_stack(x,12.76,z,t,c)
box([2.7,12.78,5.2],[5.3,12.9,9.1],"card_ace","player_faceup_diamond_ace",up="card_ace",full=True)
box([5.0,12.92,4.7],[7.6,13.04,8.6],"card_king","player_faceup_king",up="card_king",full=True)
box([9.3,12.72,3.6],[12.9,13.1,8.2],"walnut_dark","dealer_card_shoe")
box([9.6,13.1,3.9],[12.6,13.23,7.9],"brass","card_shoe_gold_rim")
box([9.8,13.23,4.1],[12.4,13.9,7.7],"card_ace","dealer_cream_deck_edges")
box([9.8,13.9,4.1],[12.4,14.04,7.7],"card_back","dealer_crowned_card_back",up="card_back",full=True)
for y in (13.37,13.55,13.73):box([9.78,y,4.08],[12.42,y+.05,4.12],"walnut_top","individual_card_deck_edges")
box([3.4,12.76,3.0],[7.7,12.94,4.1],"brass","dealer_chip_rack_base")
for x in (3.7,4.75,5.8,6.85):
    box([x,12.94,3.15],[x+.7,13.3,3.95],"chip_red" if x<5 else "chip_green","dealer_chip_rack_tokens",up="chip_red" if x<5 else "chip_green")
publish("blackjack_table",ELEMENTS)

for name,pattern,keys in (
    ("slot_machine",["GAG","RER","WWW"],{"G":"gold_ingot","A":"amethyst_shard","R":"redstone","E":"emerald","W":"dark_oak_planks"}),
    ("roulette_table",["GCG","WEW","WWW"],{"G":"gold_ingot","C":"compass","W":"dark_oak_planks","E":"green_wool"}),
    ("blackjack_table",["PGP","WEW","WWW"],{"P":"paper","G":"gold_ingot","W":"dark_oak_planks","E":"green_wool"})):
    write(RES/f"data/realmcasino/recipe/{name}.json",{"type":"minecraft:crafting_shaped","category":"misc","pattern":pattern,"key":{k:{"item":"minecraft:"+v} for k,v in keys.items()},"result":{"id":"realmcasino:"+name,"count":1}})
    write(RES/f"data/realmcasino/advancement/recipes/decorations/{name}.json",{
        "parent":"minecraft:recipes/root","criteria":{
            "has_gold":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":["minecraft:gold_ingot"]}]}},
            "has_the_recipe":{"trigger":"minecraft:recipe_unlocked","conditions":{"recipe":"realmcasino:"+name}}},
        "requirements":[["has_gold","has_the_recipe"]],"rewards":{"recipes":["realmcasino:"+name]}})
write(RES/"data/minecraft/tags/block/mineable/axe.json",{"replace":False,"values":["realmcasino:"+n for n in ("slot_machine","roulette_table","blackjack_table")]})
write(RES/"pack.mcmeta",{"pack":{"pack_format":34,"description":"Realm Casino — original walnut, brass, velvet and jewel artwork"}})

# A crisp 256px list icon: embroidered emerald frame, crowned velvet cabinet,
# ivory reels, diamond card and chips. This is original pixel art at native128.
icon=noise((128,128),(17,31,27),2);d=ImageDraw.Draw(icon)
d.rectangle((3,3,124,124),fill=(38,27,23,255),outline=DEEP,width=2)
d.rectangle((6,6,121,121),fill=(21,50,38,255),outline=GOLD,width=2)
d.rectangle((10,10,117,117),outline=(51,75,48,255))
for x,y in ((14,14),(113,14),(14,113),(113,113)):star(d,x,y,4,GOLD)
d.rectangle((27,43,100,108),fill=(23,21,20,255),outline=DEEP,width=3)
d.rectangle((32,40,95,102),fill=(70,41,29,255),outline=GOLD,width=2)
d.rectangle((34,38,93,54),fill=(87,31,43,255),outline=GOLD,width=2)
crown(d,43,18,4)
d.rectangle((36,59,91,86),fill=INK,outline=GOLD,width=2)
for x,kind in ((39,"crown"),(57,"gem"),(75,"star")):
    d.rectangle((x,62,x+13,82),fill=CREAM)
    if kind=="crown":crown(d,x+2,68,.85)
    elif kind=="gem":diamond(d,x+7,72,6,(32,115,77,255))
    else:star(d,x+7,72,6,GOLD)
d.rectangle((37,90,91,94),fill=GOLD);d.rectangle((57,95,72,100),fill=(31,112,79,255),outline=LIGHT)
d.rectangle((97,58,101,82),fill=GOLD);d.rectangle((94,53,104,63),fill=RED,outline=LIGHT)
d.polygon(((17,85),(42,81),(47,113),(22,117)),fill=GOLD)
d.polygon(((20,87),(40,84),(44,111),(24,114)),fill=CREAM)
diamond(d,32,99,7,RED)
for x,y,base in ((86,105,(39,108,78,255)),(99,109,RED),(77,114,(43,70,112,255))):
    d.ellipse((x-9,y-4,x+9,y+4),fill=base,outline=CREAM)
    d.ellipse((x-6,y-3,x+6,y+3),outline=GOLD)
icon.resize((256,256),Image.Resampling.NEAREST).save(ASSETS/"icon.png")
print(len(TEXTURES)-1,"original material textures, 3 craftable models and 256px icon")
