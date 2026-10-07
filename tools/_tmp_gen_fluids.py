"""§1118u 一次生成 4 个新流体的全套资源（贴图从来源物取色 + 换色匠魂默认流体材质 + 标签/blockstate/桶模型/mantle 材质）。

来源物与流体（用户口径）：
  灵质 goety:ectoplasm            -> liquid_ectoplasm    液态灵质   125mb/个
  邪恶精髓 enigmaticlegacy:evil_essence -> liquid_evil    流体邪恶   125mb/个
  虚空回响 goety:void_echo        -> watcher_ectoplasm   守望灵质   （副产物 10mb）
  星尘 enigmaticlegacy:astral_dust -> flowing_stardust   流动星空   125mb/个
底图：匠魂默认熔融材质 assets/tconstruct/textures/fluid/molten/{still,flowing}.png（饱和度 0，最中性）
配色：每个流体从它的来源物贴图量化取主色，再按底图亮度【百分位】自适应用"暗→该色→该色提亮"四段上色。
只新增文件；已存在的同名文件一律跳过（绝不覆盖用户手绘）。
"""
import io
import json
import os
import zipfile
from collections import Counter
import colorsys
from PIL import Image

NL = r"D:\tex\.minecraft\versions\[NL]NewLifestyle崭新世界 V0.1.7\mods"
TC = r"D:\tex\.minecraft\versions\1.20.1-Forge_47.4.26\mods\[匠魂] TConstruct-1.20.1-3.11.2.166.jar"
RES = r"D:\TinkersNewlife\src\main\resources"
BASE_STILL = "assets/tconstruct/textures/fluid/molten/still.png"
BASE_FLOW = "assets/tconstruct/textures/fluid/molten/flowing.png"

def jar_of(pat):
    for f in os.listdir(NL):
        if pat in f and f.endswith(".jar"):
            return os.path.join(NL, f)
    raise SystemExit("jar not found: " + pat)

GOETY = jar_of("goety-2.5.54")
ENIG = jar_of("EnigmaticLegacy-")

# (输出流体名, 来源 jar, 来源贴图, 中文名)
FLUIDS = [
    ("liquid_ectoplasm",   GOETY, "assets/goety/textures/item/ectoplasm.png",       "液态灵质"),
    ("liquid_evil",        ENIG,  "assets/enigmaticlegacy/textures/item/evil_essence.png", "流体邪恶"),
    ("watcher_ectoplasm",  GOETY, "assets/goety/textures/item/void_echo.png",       "守望灵质"),
    ("flowing_stardust",   ENIG,  "assets/enigmaticlegacy/textures/item/astral_dust.png",  "流动星空"),
]

tz = zipfile.ZipFile(TC)
base_still = Image.open(io.BytesIO(tz.read(BASE_STILL))).convert("RGBA")
base_flow = Image.open(io.BytesIO(tz.read(BASE_FLOW))).convert("RGBA")
base_meta = tz.read(BASE_STILL + ".mcmeta").decode("utf-8")
tz.close()
print("base:", base_still.size, base_flow.size, "mcmeta=", base_meta.replace("\n", " "))

def sample_main_color(jar_path, tex_path):
    z = zipfile.ZipFile(jar_path)
    im = Image.open(io.BytesIO(z.read(tex_path))).convert("RGBA")
    z.close()
    px = [(r, g, b) for (r, g, b, a) in list(im.getdata()) if a > 200]
    if not px:
        return (0x88, 0x88, 0x88), im
    tmp = Image.new("RGB", (len(px), 1))
    tmp.putdata(px)
    q = tmp.quantize(colors=6, method=Image.MEDIANCUT).convert("RGB")
    cnt = Counter(q.getdata())
    # 取"出现最多"的那个色，但跳过近白/近黑（高光/描边），更代表物品主体
    for (rgb, n) in cnt.most_common():
        mx, mn = max(rgb), min(rgb)
        if mx > 245 and mn > 245:
            continue
        return rgb, im
    return cnt.most_common(1)[0][0], im

def vary(rgb, f):
    r, g, b = [v / 255.0 for v in rgb]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    v = min(1.0, max(0.0, v * f))
    s = min(1.0, s * (1.0 if f <= 1.0 else 0.85))
    r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
    return (int(r2 * 255), int(g2 * 255), int(b2 * 255))

def lums_of(im):
    out = sorted((0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                 for (r, g, b, a) in list(im.getdata()) if a > 0)
    return out

def pct(ls, q):
    i = min(len(ls) - 1, max(0, int(round(q * (len(ls) - 1)))))
    return ls[i]

def recolor(base, dark, main, bright):
    ls = lums_of(base)
    p20, p60, p90 = pct(ls, 0.20), pct(ls, 0.60), pct(ls, 0.90)
    out = Image.new("RGBA", base.size)
    px = []
    for (r, g, b, a) in list(base.getdata()):
        if a == 0:
            px.append((0, 0, 0, 0))
            continue
        lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        if lum <= p20:
            t = lum / max(1e-6, p20)
            c = [int(dark[i] + (main[i] - dark[i]) * t) for i in range(3)]
        elif lum <= p60:
            c = list(main)
        elif lum <= p90:
            t = (lum - p60) / max(1e-6, p90 - p60)
            c = [int(main[i] + (bright[i] - main[i]) * t) for i in range(3)]
        else:
            c = list(bright)
        px.append((c[0], c[1], c[2], a))
    out.putdata(px)
    return out

def w(rel, text, skip_if_exists=True):
    p = os.path.join(RES, rel)
    if skip_if_exists and os.path.exists(p):
        print("   SKIP (exists):", rel)
        return
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("   write:", rel)

for (name, jar_path, tex, zh) in FLUIDS:
    main_rgb, src_im = sample_main_color(jar_path, tex)
    dark = vary(main_rgb, 0.35)
    bright = vary(main_rgb, 1.35)
    print("%-20s src=%-46s main=#%02X%02X%02X dark=#%02X%02X%02X bright=#%02X%02X%02X"
          % ((name, os.path.basename(tex)) + tuple(main_rgb) + tuple(dark) + tuple(bright)))
    for (base, suffix) in ((base_still, "still"), (base_flow, "flowing")):
        img = recolor(base, dark, main_rgb, bright)
        out_rel = "assets/tinkersnewlife/textures/block/%s_%s.png" % (name, suffix)
        outp = os.path.join(RES, out_rel)
        if os.path.exists(outp):
            print("   SKIP (exists):", out_rel)
        else:
            os.makedirs(os.path.dirname(outp), exist_ok=True)
            img.save(outp, "PNG", optimize=True)
            print("   write:", out_rel, img.size, os.path.getsize(outp), "B")
        w("assets/tinkersnewlife/textures/block/%s_%s.png.mcmeta" % (name, suffix),
          '{\n  "animation": {\n    "frametime": 2\n  }\n}\n')
    w("assets/tinkersnewlife/blockstates/%s_block.json" % name,
      '{\n  "variants": {\n    "": { "model": "tconstruct:block/fluid" }\n  }\n}\n')
    w("assets/tinkersnewlife/models/item/%s_bucket.json" % name,
      '{\n  "parent": "forge:item/bucket_drip",\n  "loader": "tconstruct:fluid_container",\n  "fluid": "tinkersnewlife:%s_still",\n  "flip_gas": false\n}\n' % name)
    w("assets/tinkersnewlife/mantle/fluid_texture/%s.json" % name,
      '{\n  "still": "tinkersnewlife:block/%s_still",\n  "flowing": "tinkersnewlife:block/%s_flowing",\n  "color": "FFFFFFFF",\n  "fogColor": "FF%02X%02X%02X",\n  "fogStart": 0.5,\n  "fogEnd": 6.0\n}\n'
      % (name, name, main_rgb[0], main_rgb[1], main_rgb[2]))
    for tagdir in ("data/forge/tags/fluids", "data/tconstruct/tags/fluids"):
        w("%s/%s.json" % (tagdir, name),
          '{\n  "replace": false,\n  "values": [\n    {\n      "id": "tinkersnewlife:%s_still",\n      "required": false\n    }\n  ]\n}\n' % name)
    print("   -> %s (%s) fog=#%02X%02X%02X" % (name, zh, main_rgb[0], main_rgb[1], main_rgb[2]))
print("ALL DONE")
