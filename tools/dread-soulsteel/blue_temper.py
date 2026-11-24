"""Bake the blue-tempered variant from unmodified part masks. Usage: python blue_temper.py TConstruct.jar"""
from io import BytesIO
from math import sin, tau
from pathlib import Path
from zipfile import ZipFile
import sys
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[2]
res = root / 'common/src/main/resources'
tex = res / 'assets/tinkersnewlife/textures/generator'
old = '_tinkersnewlife_dread_soulsteel.png'
new = '_tinkersnewlife_dread_soulsteel_blue_temper.png'

def save(path, im):
    buf = BytesIO(); im.save(buf, 'PNG'); data = buf.getvalue()
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('xb') as f: f.write(data)
    elif path.read_bytes() != data: raise ValueError(f'Not overwriting edited art: {path}')

# Continuous, directional forge folds. All spatial phases wrap across the 32px period.
steel = ['#375364','#476779','#537586','#618595','#789ba6','#89abb2']
tile = Image.new('RGB',(32,32))
for y in range(32):
    for x in range(32):
        band = (x + y + 1.7*sin(tau*(x-y)/32) + .7*sin(tau*y/16)) % 16
        idx = 0 if band<1.25 else 1 if band<3 else 2 if band<6 else 3 if band<10 else 4 if band<13 else 3
        if 11<band<13 and (x-y)%16<6: idx=5
        tile.putpixel((x,y),tuple(bytes.fromhex(steel[idx][1:])))
# Two tapered, forked wisps; the shadow just below the edge separates the inlay from the steel.
veins=[([(1,22),(5,20),(8,16),(12,15),(16,10)],[(6,18),(8,19),(11,18)]),
       ([(20,28),(22,25),(26,24),(29,20)],[(24,24),(25,21),(27,19)])]
draw=ImageDraw.Draw(tile)
for line,branch in veins:
    draw.line([(x,y+1) for x,y in line],fill='#395967');draw.line(branch,fill='#5896a6');draw.line(line,fill='#75b4c4')
draw.line([(7,17),(8,16),(10,16)],fill='#b6dade');draw.line([(24,25),(26,24)],fill='#9fcbd4')
save(tex/'dread_soulsteel_blue_temper.png',tile)
shades=[(0,42),(63,94),(102,133),(140,168),(178,202),(216,234),(255,255)]
def light(g):
    for (x,a),(y,b) in zip(shades,shades[1:]):
        if g<=y:return round(a+(b-a)*(g-x)/(y-x))
    return 255
samples={}
with ZipFile(sys.argv[1]) as jar:
    for previous in sorted(res.rglob('*'+old)):
        base=Path(str(previous)[:-len(old)]+'.png'); rel=base.relative_to(res).as_posix()
        mask=Image.open(base if base.exists() else BytesIO(jar.read(rel))).convert('RGBA')
        out=Image.new('RGBA',mask.size)
        for y in range(mask.height):
            for x in range(mask.width):
                g,_,_,alpha=mask.getpixel((x,y)); k=light(g)
                if alpha:out.putpixel((x,y),tuple(c*k//255 for c in tile.getpixel((x%32,y%32)))+(alpha,))
        assert mask.getchannel('A').tobytes()==out.getchannel('A').tobytes()
        save(Path(str(previous)[:-len(old)]+new),out)
        if rel.endswith(('/parts/small_blade.png','/parts/large_plate.png','/parts/shield_plating_32.png')):samples[base.stem]=out
assert len(samples)==3
preview=Image.new('RGB',(416,144),'#18232d')
for col,im in enumerate([tile,samples['small_blade'],samples['large_plate'],samples['shield_plating_32']]):
    im=im.convert('RGBA').resize((96,96),Image.Resampling.NEAREST);preview.paste(im,(4+col*104,18),im)
save(root/'docs/dread-soulsteel-blue-temper.png',preview)
