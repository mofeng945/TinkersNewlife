"""Create the 64px layered scale tile and its shield part at NEW asset paths."""
from io import BytesIO
from pathlib import Path
from PIL import Image

tex = Path(__file__).resolve().parents[2] / 'common/src/main/resources/assets/tinkersnewlife/textures'
colors = [(189,86,75),(194,130,72),(75,156,132),(83,144,182),(148,100,172),(114,103,143)]
base=Image.new('RGBA',(64,64))
for y in range(64):
    row,v=divmod(y,8)
    for x in range(64):
        col,u=divmod((x+4*(row%2))%64,8)
        tone=colors[(col+row+(col//3))%len(colors)]
        depth=7-abs(u-3.5)*.72-v
        shade=.38 if depth<0 else .55 if depth<1 else .76 if u>=5 else .97
        if v in (1,2) and 1<=u<=4: shade=1.19
        if (u,v)==(2,1):shade=1.44
        base.putpixel((x,y),tuple(min(255,round(c*shade)) for c in tone)+(255,))
def store(im,path):
    buf=BytesIO();im.save(buf,format='PNG');dst=tex/path
    if dst.exists() and dst.read_bytes()!=buf.getvalue():raise SystemExit('Refusing to overwrite: '+str(dst))
    if not dst.exists():dst.write_bytes(buf.getvalue())
store(base,'generator/dragon_scales_64.png')
mask=Image.open(tex/'item/tool/parts/shield_plating.png').convert('RGBA').resize((64,64),Image.Resampling.NEAREST)
store(mask,'item/tool/parts/shield_plating_64.png');baked=Image.new('RGBA',mask.size)
for y in range(64):
    for x in range(64):
        g,_,_,alpha=mask.getpixel((x,y));tint={63:112,102:150,140:181,178:212,216:235}.get(g,255)
        if alpha:baked.putpixel((x,y),tuple(c*tint//255 for c in base.getpixel((x,y))[:3])+(alpha,))
store(baked,'item/tool/parts/shield_plating_64_tinkersnewlife_dragon_scales.png');print('64px base and shield: verified')
