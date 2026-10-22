#!/usr/bin/env python3
"""Original vector-style Season 8 artwork. Pillow is a build-time art tool only.

Regeneration uses the system DejaVu fonts; all resulting PNGs are checked in.
No downloads, copied game textures, external art or runtime dependencies.
"""
from pathlib import Path
from math import sin, cos, pi
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/seasoneight/art'
OUT.mkdir(parents=True, exist_ok=True)
SERIF = '/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf'
SANS = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'

def metal(mask, top=(248, 225, 175), bottom=(133, 90, 45)):
    gradient = Image.new('RGBA', mask.size)
    d = ImageDraw.Draw(gradient)
    for y in range(mask.height):
        t = y / max(1, mask.height - 1)
        shine = .14 * sin(t * pi * 7)
        color = tuple(max(0, min(255, round(a * (1-t) + b*t + 255*shine))) for a,b in zip(top,bottom))
        d.line((0,y,mask.width,y), fill=color+(255,))
    gradient.putalpha(mask)
    return gradient

size = 1024
crest = Image.new('RGBA',(size,size))
d = ImageDraw.Draw(crest)
# Subdued violet atmospheric halo, behind a crisp engraved bronze medallion.
glow = Image.new('RGBA',crest.size)
gd = ImageDraw.Draw(glow)
gd.ellipse((200,190,824,834), fill=(130,80,178,55))
crest.alpha_composite(glow.filter(ImageFilter.GaussianBlur(85)))
d = ImageDraw.Draw(crest)
d.ellipse((148,148,876,876), fill=(17,14,27,245), outline=(80,59,101,230), width=3)
d.ellipse((166,166,858,858), outline=(179,141,84,255), width=5)
d.ellipse((184,184,840,840), outline=(84,66,79,255), width=2)
d.ellipse((210,210,814,814), outline=(72,52,91,255), width=2)
for i in range(80):
    a = i*pi*2/80
    r0,r1 = (365,382) if i%5==0 else (369,376)
    d.line((512+cos(a)*r0,512+sin(a)*r0,512+cos(a)*r1,512+sin(a)*r1), fill=(155,122,78,220),width=3 if i%5==0 else 2)
for i in range(4):
    a=i*pi/2
    x,y=512+cos(a)*403,512+sin(a)*403
    d.polygon([(x,y-11),(x+8,y),(x,y+11),(x-8,y)],fill=(222,189,123,255))
# Crown silhouette and internal gemstone, drawn specifically for this season.
m=Image.new('L',crest.size);md=ImageDraw.Draw(m)
md.polygon([(348,382),(319,278),(413,327),(462,260),(512,205),(562,260),(611,327),(705,278),(676,382)],fill=255)
md.rounded_rectangle((352,391,672,425),radius=4,fill=255)
for x,y in [(319,274),(512,201),(705,274)]:md.ellipse((x-10,y-10,x+10,y+10),fill=255)
crest.alpha_composite(metal(m))
d=ImageDraw.Draw(crest)
d.polygon([(512,282),(537,315),(512,349),(487,315)],fill=(136,99,169,255),outline=(230,208,170,255),width=3)
d.line((370,402,654,402),fill=(255,227,166,230),width=3)
# Roman eight with fine luminous edges.
m=Image.new('L',crest.size);md=ImageDraw.Draw(m)
font=ImageFont.truetype(SERIF,226)
md.text((512,540),'VIII',font=font,anchor='mm',fill=255,stroke_width=1)
crest.alpha_composite(metal(m))
d=ImageDraw.Draw(crest)
d.line((344,687,680,687),fill=(125,95,69,255),width=2)
font=ImageFont.truetype(SANS,21)
d.text((512,727),'R O C K A B I T S',font=font,anchor='mm',fill=(191,165,204,255))
crest.save(OUT/'crest.png',optimize=True)

word = Image.new('RGBA',(1200,340))
m=Image.new('L',word.size);md=ImageDraw.Draw(m)
font=ImageFont.truetype(SERIF,182)
md.text((22,168),'SEASON',font=font,anchor='lm',fill=255)
# Fit to a precise optical width, then reserve the right-hand side for the eight.
left=m.crop((10,20,870,300)).resize((886,280),Image.Resampling.LANCZOS)
m=Image.new('L',word.size);m.paste(left,(0,10))
word.alpha_composite(metal(m,(239,231,218),(177,160,171)))
m=Image.new('L',word.size);md=ImageDraw.Draw(m)
md.text((1050,170),'8',font=ImageFont.truetype(SERIF,290),anchor='mm',fill=255)
word.alpha_composite(metal(m))
d=ImageDraw.Draw(word)
d.line((12,305,1184,305),fill=(125,95,72,190),width=2)
d.polygon([(590,299),(596,305),(590,311),(584,305)],fill=(223,188,122,255))
word.save(OUT/'wordmark.png',optimize=True)

for lang,text in [('en','P R E P A R I N G   Y O U R   R E A L M'),('pt','A   P R E P A R A R   O   T E U   R E I N O')]:
    im=Image.new('RGBA',(1120,64));dr=ImageDraw.Draw(im)
    dr.text((560,32),text,font=ImageFont.truetype(SANS,26),anchor='mm',fill=(176,157,193,255))
    im.save(OUT/f'loading-{lang}.png',optimize=True)

# The application icon uses a simpler, high-contrast crown + 8 silhouette at small sizes.
icon=Image.new('RGBA',(512,512))
d=ImageDraw.Draw(icon)
d.rounded_rectangle((12,12,500,500),radius=100,fill=(20,15,32,255),outline=(178,139,81,255),width=10)
d.rounded_rectangle((30,30,482,482),radius=87,outline=(82,57,109,255),width=4)
m=Image.new('L',icon.size);md=ImageDraw.Draw(m)
md.polygon([(158,164),(139,96),(206,129),(256,68),(306,129),(373,96),(354,164)],fill=255)
md.rectangle((161,173,351,188),fill=255)
md.text((256,326),'8',font=ImageFont.truetype(SERIF,286),anchor='mm',fill=255)
icon.alpha_composite(metal(m))
icon.save(OUT/'icon.png',optimize=True)
for n in [16,32,48,64,128,256]: icon.resize((n,n),Image.Resampling.LANCZOS).save(OUT/f'icon-{n}.png',optimize=True)
print('Generated',len(list(OUT.glob('*.png'))),'original Season 8 PNGs')
