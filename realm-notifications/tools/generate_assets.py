#!/usr/bin/env python3
"""Original native pixel artwork. No external images, fonts or downloaded assets."""
from pathlib import Path
from PIL import Image, ImageDraw
import random

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/realmnotifications'
OUT=ROOT/'textures/gui';OUT.mkdir(parents=True,exist_ok=True)
atlas=Image.new('RGBA',(288,32),(0,0,0,0))
palettes=[('#72521e','#d5a747','#f9db83'),('#765224','#dcb350','#fff0ae'),('#38533b','#6e9c5e','#dce8b6'),('#494060','#9181b3','#eee2ff'),('#27584b','#5b9c87','#d9eee0'),('#72521e','#cc9c45','#ffe6a2'),('#494060','#9181b3','#eee2ff'),('#375b58','#72a19a','#e0f0db'),('#743d35','#bb6650','#ffe0ba')]
for n,(dark,mid,light) in enumerate(palettes):
    im=Image.new('RGBA',(32,32),(0,0,0,0));d=ImageDraw.Draw(im)
    # Angular wax perimeter, lower shadow and a raised interior stamping plate.
    points=[(10,2),(20,2),(23,5),(27,6),(28,12),(30,16),(27,21),(26,26),(20,27),(16,30),(11,27),(6,26),(5,21),(2,17),(4,11),(5,6),(10,5)]
    d.polygon([(x,y+1) for x,y in points],fill='#392f22');d.polygon(points,fill=dark)
    d.ellipse((5,4,26,25),fill=mid);d.arc((5,4,26,25),185,300,fill=light,width=2)
    d.ellipse((8,7,23,23),fill=dark);d.ellipse((9,8,22,21),fill=mid)
    d.line((9,23,15,26,20,24),fill=dark,width=2)
    if n==0: # embossed coin
        d.ellipse((11,9,20,20),outline=light,width=2);d.line((15,9,15,20),fill=light,width=2);d.line((12,12,18,12),fill=light)
    elif n==1: # four-point reward star
        d.polygon([(16,7),(18,12),(23,14),(18,16),(16,22),(13,17),(8,14),(13,12)],fill=dark)
        d.polygon([(16,7),(18,12),(22,14),(17,15),(15,21),(14,16),(9,14),(14,12)],fill=light)
    elif n==2: # contract parchment
        d.rectangle((10,8,21,21),fill=dark);d.rectangle((10,7,20,20),fill=light)
        for y in (11,14,17):d.line((12,y,18,y),fill=mid,width=1)
        d.rectangle((18,18,21,21),fill='#b15e47')
    elif n==3: # crown / election
        d.polygon([(8,10),(12,13),(16,8),(19,13),(23,10),(21,19),(10,19)],fill=light)
        d.rectangle((11,20,21,21),fill=light);d.rectangle((15,15,17,17),fill=mid)
    elif n==4: # market stall
        d.rectangle((9,12,22,21),fill=light);d.rectangle((11,15,20,19),fill=mid)
        d.polygon([(8,12),(11,7),(21,7),(24,12)],fill=light)
        d.line((13,8,12,12),fill=mid,width=2);d.line((19,8,20,12),fill=mid,width=2)
    elif n==5: # purse salary
        d.polygon([(12,7),(21,7),(19,11),(23,16),(22,21),(10,21),(8,16),(14,11)],fill=light)
        d.rectangle((12,11,20,12),fill=dark);d.rectangle((15,15,17,18),fill=mid)
    elif n==6: # ledger and clasp
        d.rectangle((9,7,21,22),fill=light);d.rectangle((11,8,20,21),fill=mid)
        d.rectangle((14,11,19,12),fill=light);d.rectangle((14,15,19,16),fill=light)
        d.rectangle((19,15,23,18),fill=dark);d.rectangle((20,16,22,17),fill=light)
    elif n==7: # envelope
        d.rectangle((8,10,23,20),fill=light);d.line((8,10,16,16,23,10),fill=mid,width=2)
        d.rectangle((14,15,17,18),fill=dark)
    else:
        d.polygon([(16,7),(24,21),(7,21)],fill=light);d.rectangle((15,12,16,16),fill=dark);d.point((16,19),fill=dark)
    atlas.alpha_composite(im,(n*32,0))
atlas.save(OUT/'seals.png')

im=Image.new('RGBA',(128,128),'#172a24');d=ImageDraw.Draw(im)
rng=random.Random(221)
for _ in range(380):
    x=rng.randrange(3,125);y=rng.randrange(3,125);d.rectangle((x,y,x+1,y+1),fill=rng.choice(['#1e332b','#233a2e','#243b2e']))
d.rectangle((3,3,124,124),outline='#98763d',width=2);d.rectangle((7,7,120,120),outline='#d4b371',width=1)
for x,y,dx,dy in [(10,10,1,1),(117,10,-1,1),(10,117,1,-1),(117,117,-1,-1)]:
    d.line((x,y,x+dx*15,y),fill='#e3c47f',width=2);d.line((x,y,x,y+dy*15),fill='#e3c47f',width=2)
    d.rectangle((x+dx*4,y+dy*4,x+dx*4+2,y+dy*4+2),fill='#aa8a4c')
# Folded parchment envelope, substantial pixel bevels and a gold notification bell.
d.polygon([(23,44),(105,44),(110,96),(25,100)],fill='#101b15')
d.rectangle((20,38,105,93),fill='#917346');d.rectangle((23,35,102,90),fill='#e8d4a6')
d.polygon([(23,35),(63,65),(102,35)],fill='#fff0c9');d.line((23,35,63,66,102,35),fill='#ac8c56',width=2)
d.polygon([(23,90),(51,63),(63,70),(74,63),(102,90)],fill='#d6bb83')
d.line((25,89,51,64),fill='#f9e6b9',width=2);d.line((75,65,100,89),fill='#b49a65',width=2)
d.ellipse((47,57,76,86),fill='#6b3528');d.ellipse((48,56,75,83),fill='#ac5840');d.arc((50,58,73,81),180,290,fill='#dd8c5c',width=2)
# Golden bell suspended over the envelope corner.
d.rectangle((91,19,95,22),fill='#e1b957');d.polygon([(87,25),(99,25),(103,31),(103,47),(108,51),(80,51),(85,46),(85,32)],fill='#715121')
d.polygon([(87,24),(98,24),(101,30),(101,45),(105,49),(81,49),(86,44),(86,30)],fill='#dcac47')
d.line((88,27,88,44,84,47),fill='#ffe39a',width=3);d.rectangle((82,49,105,51),fill='#edc66f');d.rectangle((90,53,96,56),fill='#e1b957')
d.rectangle((60,62,63,75),fill='#f4cc86');d.rectangle((56,65,67,68),fill='#f4cc86')
im.save(ROOT/'icon.png')
print('Generated original9 wax-seal icons and128px mod icon.')
