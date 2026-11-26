"""Bake the shield plating and preview from the artist's 32px dragon-scale tile. Never generate or write the tile."""
from pathlib import Path
from PIL import Image
from io import BytesIO

root = Path(__file__).resolve().parents[2]
tex = root / 'src/main/resources/assets/tinkersnewlife/textures'
base = Image.open(tex / 'generator/dragon_scales_32.png').convert('RGBA')
assert base.size == (32, 32), base.size
mask = Image.open(tex / 'item/tool/parts/shield_plating_32.png').convert('RGBA')
assert mask.size == base.size
baked = Image.new('RGBA', base.size)
shading = {63:112, 102:150, 140:181, 178:212, 216:235}
for y in range(32):
    for x in range(32):
        grey, _, _, opacity = mask.getpixel((x, y))
        r,g,b,a = base.getpixel((x, y)); tint = shading.get(grey,255)
        if opacity: baked.putpixel((x,y), (r*tint//255,g*tint//255,b*tint//255,a*opacity//255))
out = tex / 'item/tool/parts/shield_plating_32_tinkersnewlife_dragon_scales.png'
buf = BytesIO(); baked.save(buf, format='PNG')
if out.exists() and out.read_bytes() != buf.getvalue(): raise SystemExit(f'Refusing to overwrite existing texture: {out}')
if not out.exists(): out.write_bytes(buf.getvalue())
tile = Image.new('RGBA',(64,64))
for x in (0,32):
    for y in (0,32): tile.paste(base,(x,y))
tile.resize((384,384),Image.Resampling.NEAREST).save(root / 'docs/dragon-scales-32-preview.png')
