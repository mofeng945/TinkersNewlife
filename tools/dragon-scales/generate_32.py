"""Build a 32px scale field using the tones of the installed Ice and Fire item sprites.
The 4-tone silhouette below is drawn for a continuous surface, not cropped inventory icons.
Pass the Ice and Fire jar as argv[1]. Existing hand-edited images are never overwritten.
"""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import json, sys
from PIL import Image

tex=Path(__file__).resolve().parents[2]/'common/src/main/resources/assets/tinkersnewlife/textures'
colors=['sapphire','blue','silver','amythest','red','bronze']
def palette(jar,name):
    model=json.loads(jar.read(f'assets/iceandfire/models/item/dragonscales_{name}.json'))
    ns,path=model['textures']['layer0'].split(':')
    im=Image.open(BytesIO(jar.read(f'assets/{ns}/textures/{path}.png'))).convert('RGBA')
    shades=sorted({c[:3] for c in im.getdata() if c[3]},key=lambda c:.2126*c[0]+.7152*c[1]+.0722*c[2]);return [shades[round(i*(len(shades)-1)/3)] for i in range(4)]
with ZipFile(sys.argv[1]) as jar: palettes=[palette(jar,k) for k in colors]
# Stagger the long axes. A short highlight follows the left ridge and a narrow dark edge tapers into a point.
shape=['.011...','.1320..','013220.','0132210','0132210','.122210','.012210','..01210','...0110','....00.']
families=((0,1,2,0),(0,2,3,1),(1,3,4,2),(2,5,3,0))
# Draw upper rows last so the lower tapered tips remain visible over the row underneath.
# Tile on an unwrapped canvas before cropping to avoid ordering artifacts at the seam.
canvas=Image.new('RGB',(96,96),palettes[1][0]);
for row in range(24,-4,-1):
    for col in range(-2,13):
        pal=palettes[families[row%4][col%4]];sx=8*col+4*(row%2);sy=4*row
        for y,line in enumerate(shape):
            for x,v in enumerate(line):
                if v!='.' and 0<=sx+x<96 and 0<=sy+y<96:canvas.putpixel((sx+x,sy+y),pal[int(v)])
base=canvas.crop((32,32,64,64));assert base.tobytes()==canvas.crop((0,32,32,64)).tobytes()==canvas.crop((32,0,64,32)).tobytes()
def store(im,rel):
    p=tex/rel;buf=BytesIO();im.save(buf,format='PNG');raw=buf.getvalue()
    if p.exists() and p.read_bytes()!=raw:raise SystemExit(f'Refusing to overwrite {p}')
    if not p.exists():p.write_bytes(raw)
store(base,'generator/dragon_scales_32.png');mask=Image.open(tex/'gui/tinker_pattern/shield_plating.png').convert('RGBA').resize((32,32),Image.Resampling.NEAREST);store(mask,'item/tool/parts/shield_plating_32.png');baked=Image.new('RGBA',mask.size)
for y in range(32):
    for x in range(32):
        g,_,_,a=mask.getpixel((x,y));t={63:112,102:150,140:181,178:212,216:235}.get(g,255)
        if a:baked.putpixel((x,y),tuple(c*t//255 for c in base.getpixel((x,y)))+(a,))
store(baked,'item/tool/parts/shield_plating_32_tinkersnewlife_dragon_scales.png');print('32px base: seamless repetition verified; shield mask and baked sprite verified')
