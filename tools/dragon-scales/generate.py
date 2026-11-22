"""Create the original dragon-scale sprite used by TConstruct's grey_to_sprite transformer.
The overlapping scale coordinates wrap at 16px, so the texture tiles on armor trims as well as small parts.
Never replace an existing, artist-edited image.
"""
from pathlib import Path
from io import BytesIO
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'common/src/main/resources/assets/tinkersnewlife/textures/generator/dragon_scales.png'
# Enamel families: ember/copper, glacial green-blue and lightning violet. Three stepped shades per scale.
SWATCHES = [
    ('562834','aa493d','ed9460'), ('4a3540','a36642','ecc27d'),
    ('183f4f','367f84','8cdbca'), ('203657','477ca6','a9d6eb'),
    ('39234f','78569d','d6aeed'), ('322846','67557a','b59cbd'),
]
ROWS = ((0,1,2,3),(1,2,3,4),(2,3,4,5),(4,5,0,1))
img = Image.new('RGBA',(16,16),'#302636')
# Staggered four-pixel-wide plates. A dark point and two facet highlights distinguish adjacent scales.
for y in range(16):
    row, v = divmod(y,4)
    for x in range(16):
        col,u = divmod((x+2*(row%2))%16,4)
        shades=SWATCHES[ROWS[row][col]]
        mask=((1,2,1,0),(2,1,1,0),(0,1,1,0),(-1,0,0,-1))[v][u]
        if mask>=0: img.putpixel((x,y),tuple(bytes.fromhex(shades[mask]))+(255,))
encoded=BytesIO(); img.save(encoded,format='PNG')
OUT.parent.mkdir(parents=True,exist_ok=True)
if OUT.exists():
    if OUT.read_bytes()!=encoded.getvalue(): raise SystemExit(f'Refusing to overwrite existing sprite {OUT}')
else: OUT.write_bytes(encoded.getvalue())
print(f'{OUT.name}: {img.size}, {len(img.getcolors())} distinct opaque colors')

# A broad, stepped kite-shaped plate; gray levels are the TConstruct part shading inputs.
rows = ['................','.....oooooo.....','...oohhhhlloo...','..ohhhgggllmmo..',
        '..ohhggglllmmo..','..ohgghhlllmoo..','..ogghhlllmmmo..','...ogggllmmmo...',
        '...ohhggllmmo...','....ohgglmmo....','....ohgllmmo....','.....oglmmo.....',
        '......olmo......','.......oo.......','................','................']
shades = {'o':63,'m':102,'l':140,'g':178,'h':216}
mask = Image.new('RGBA',(16,16)); pattern = mask.copy(); baked = mask.copy()
for y,row in enumerate(rows):
    assert len(row) == 16
    for x,ch in enumerate(row):
        if ch=='.': continue
        grey = shades[ch]; mask.putpixel((x,y),(grey,grey,grey,255))
        pattern.putpixel((x,y),(grey,grey,grey,255))
        tint = {63:112,102:150,140:181,178:212,216:235}[grey]
        baked.putpixel((x,y),tuple((c*tint)//255 for c in img.getpixel((x,y))[:3])+(255,))
for png, rel in ((mask,'item/tool/parts/shield_plating.png'),(pattern,'gui/tinker_pattern/shield_plating.png'),(baked,'item/tool/parts/shield_plating_tinkersnewlife_dragon_scales.png')):
    p=ROOT/'common/src/main/resources/assets/tinkersnewlife/textures'/rel
    raw=BytesIO();png.save(raw,format='PNG');p.parent.mkdir(parents=True,exist_ok=True)
    if p.exists() and p.read_bytes()!=raw.getvalue():raise SystemExit(f'Refusing to overwrite existing sprite {p}')
    if not p.exists():p.write_bytes(raw.getvalue())
