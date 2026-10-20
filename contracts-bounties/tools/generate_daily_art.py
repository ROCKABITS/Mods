#!/usr/bin/env python3
"""Original parchment/seal pixel artwork and synthesized fanfares. Pillow, NumPy and ffmpeg."""
from pathlib import Path
import math
import subprocess
import tempfile
import wave
import numpy as np
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/contracts'
TEXTURES=ROOT/'textures/gui/daily'
SOUNDS=ROOT/'sounds/daily'
TEXTURES.mkdir(parents=True,exist_ok=True);SOUNDS.mkdir(parents=True,exist_ok=True)
PALETTES={
    'easy':('#A8BDB8','#425C58','#DAE4D7','#EEE3C5'),
    'medium':('#D9B25F','#7B532E','#FFE2A0','#F2DEAC'),
    'hard':('#D7AC62','#58406E','#ECD2FF','#ECD8B0'),
    'all':('#E7C470','#6B3B64','#FFF0AC','#F8E6B9'),
}
for index,(name,(metal,shadow,light,paper)) in enumerate(PALETTES.items()):
    im=Image.new('RGBA',(512,192));d=ImageDraw.Draw(im)
    d.rounded_rectangle((20,19,493,182),8,fill='#18141750')
    d.polygon([(32,24),(478,24),(471,164),(479,173),(35,173),(41,165)],fill=paper)
    rng=np.random.default_rng(404+index)
    for i in range(1400):
        x,y=int(rng.integers(42,470)),int(rng.integers(30,169));delta=int(rng.integers(-6,7));color=tuple(max(0,min(255,int(paper[j:j+2],16)+delta)) for j in (1,3,5))+(255,);d.point((x,y),fill=color)
    for y in (27,169):d.line((37,y,475,y),fill=shadow,width=2)
    for y in (32,164):d.line((45,y,467,y),fill=metal,width=1)
    for x in (37,475):d.line((x,34,x,163),fill=metal,width=1)
    for x in (25,480):
        d.rounded_rectangle((x,17,x+9,183),3,fill=shadow)
        d.rectangle((x+1,21,x+7,178),fill=metal);d.line((x+2,23,x+2,176),fill=light,width=2)
        for y in (15,174):d.rounded_rectangle((x-5,y,x+14,y+10),2,fill=shadow);d.rectangle((x-3,y+1,x+11,y+5),fill=metal);d.line((x-2,y+1,x+10,y+1),fill=light)
    # Hand-drawn illuminated corners, with more royal detail on difficult quests.
    for cx,cy,dx,dy in [(48,40,1,1),(463,40,-1,1),(48,156,1,-1),(463,156,-1,-1)]:
        d.line((cx,cy+12*dy,cx,cy,cx+18*dx,cy),fill=metal,width=2)
        d.polygon([(cx+4*dx,cy+4*dy),(cx+10*dx,cy+7*dy),(cx+7*dx,cy+11*dy)],fill=metal)
        if name in ('hard','all'):
            d.line((cx+2*dx,cy+19*dy,cx+14*dx,cy+14*dy,cx+23*dx,cy+3*dy),fill=shadow,width=1)
            d.rectangle((cx+12*dx,cy+12*dy,cx+12*dx+2,cy+12*dy+2),fill=light)
    if name=='all':
        for x in range(96,420,18):d.polygon([(x,23),(x+3,19),(x+6,23),(x+3,27)],fill=metal)
    im.save(TEXTURES/f'scroll_{name}.png',optimize=True)
    seal=Image.new('RGBA',(96,96));d=ImageDraw.Draw(seal)
    outer=[]
    for i in range(40):
        angle=i*math.pi/20;r=43 if i%2==0 else 39;outer.append((48+int(math.cos(angle)*r),48+int(math.sin(angle)*r)))
    d.polygon(outer,fill=shadow);d.ellipse((9,8,87,86),fill=metal);d.ellipse((12,11,84,83),outline=light,width=2)
    d.ellipse((18,17,78,77),fill=shadow);d.ellipse((21,20,75,74),outline=metal,width=2)
    if name in ('hard','all'):
        d.polygon([(30,40),(35,57),(62,57),(67,39),(56,46),(48,32),(40,46)],fill=metal)
        d.rectangle((36,61,62,65),fill=light)
        for x,y in [(29,37),(47,29),(66,36)]:d.rectangle((x,y,x+3,y+3),fill=light)
        d.polygon([(47,46),(51,50),(47,54),(44,50)],fill='#BE8FE0' if name=='hard' else '#E55E52')
    else:
        d.line((31,48,42,60,64,35),fill=metal,width=6);d.line((31,46,42,56,63,34),fill=light,width=2)
    for x in range(22,75,8):
        dy=int(math.sqrt(max(0,27**2-(x-48)**2)));d.rectangle((x,48+dy,x+2,50+dy),fill=light)
    seal.save(TEXTURES/f'seal_{name}.png',optimize=True)

rates={'easy':([62,69,74],.95,.13),'medium':([62,66,69,74],1.7,.20),'hard':([50,62,66,69,74,78],2.35,.22),'all':([50,57,62,66,69,74,78,81,86],3.2,.21)}
for name,(notes,duration,spacing) in rates.items():
    rate=44100;t=np.arange(int(rate*duration))/rate;audio=np.zeros_like(t)
    for i,note in enumerate(notes):
        local=t-i*spacing;on=np.maximum(local,0);freq=440*2**((note-69)/12)
        envelope=(1-np.exp(-on*110))*np.exp(-on*(3.6 if name=='easy' else 2.6))*(local>=0)
        bell=np.sin(2*np.pi*freq*on)+.28*np.sin(2*np.pi*freq*2*on)+.1*np.sin(2*np.pi*freq*4*on)
        audio+=bell*envelope*.24
    if name in ('hard','all'):
        audio+=(np.sin(2*np.pi*146.832*t)+.2*np.sin(2*np.pi*293.665*t))*(1-np.exp(-t*15))*np.exp(-t*2)*.15
    audio*=np.minimum(1,(duration-t)*30);audio*=.65/max(1,np.max(np.abs(audio)))
    pcm=(np.column_stack((audio,np.roll(audio,int(rate*.009))))*32767).astype('<i2')
    with tempfile.TemporaryDirectory() as temporary:
        wav=Path(temporary)/'fanfare.wav'
        with wave.open(str(wav),'wb') as stream:stream.setnchannels(2);stream.setsampwidth(2);stream.setframerate(rate);stream.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-map_metadata','-1','-c:a','libvorbis','-q:a','5',str(SOUNDS/f'{name}.ogg')],check=True)
import json
(ROOT/'sounds.json').write_text(json.dumps({f'daily_{name}':{'subtitle':f'daily.contracts.sound.{name}','sounds':[{'name':f'contracts:daily/{name}','stream':False}]} for name in PALETTES},indent=2)+'\n')
print('Created eight original pixel-art textures and four synthesized stereo fanfares.')
