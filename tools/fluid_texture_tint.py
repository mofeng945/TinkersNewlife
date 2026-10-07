"""§1118t v5 定稿：底图改用【匠魂默认熔融材质】textures/fluid/molten/still.png + flowing.png
（实测饱和度 0.000 = 完全中性，16x784 = 49 帧），再按锭的【紫 + 浅蓝】混色重新上色。

四段渐变（色值全部取自锭贴图的量化统计）：
  深靛 #2D2467 -> 紫 #7157FA -> 浅蓝 #7EFFFF -> 亮青白 #CFFBFF
下半段偏紫、上半段偏浅蓝 => 紫与浅蓝在同一张图上交织 = 用户要的"混色"。
只覆盖我们此前生成的那两个文件（不是用户的画）。
"""
import io
import os
import zipfile
from collections import Counter
from PIL import Image

JAR = r"D:\tex\.minecraft\versions\1.20.1-Forge_47.4.26\mods\[匠魂] TConstruct-1.20.1-3.11.2.166.jar"
ROOT = r"D:\TinkersNewlife\src\main\resources\assets\tinkersnewlife\textures\block"
SRC_STILL = "assets/tconstruct/textures/fluid/molten/still.png"
SRC_FLOW = "assets/tconstruct/textures/fluid/molten/flowing.png"
OUT_STILL = os.path.join(ROOT, "molten_sinister_glow_still.png")
OUT_FLOW = os.path.join(ROOT, "molten_sinister_glow_flowing.png")

C0 = (0x2D, 0x24, 0x67)   # 深靛
C1 = (0x71, 0x57, 0xFA)   # 紫（锭主体）
C2 = (0x7E, 0xFF, 0xFF)   # 浅蓝（锭的辉光）
C3 = (0xCF, 0xFB, 0xFF)   # 亮青白

z = zipfile.ZipFile(JAR)
for n in (SRC_STILL, SRC_FLOW):
    m = n + ".mcmeta"
    if m in z.namelist():
        print("base mcmeta", n, "->", z.read(m).decode("utf-8").replace("\n", " "))

def mix(a, b, t):
    return [int(a[i] + (b[i] - a[i]) * t) for i in range(3)]

def lums_of(im):
    out = []
    for (r, g, b, a) in list(im.getdata()):
        if a > 0:
            out.append((0.299 * r + 0.587 * g + 0.114 * b) / 255.0)
    out.sort()
    return out

def pct(sorted_lums, q):
    if not sorted_lums:
        return 0.5
    i = min(len(sorted_lums) - 1, max(0, int(round(q * (len(sorted_lums) - 1)))))
    return sorted_lums[i]

def ramp(lum, p20, p60, p90):
    """按底图自身的亮度百分位分段：<=p20 深靛->紫；p20~p60 紫（主体）；
    p60~p90 紫->浅蓝；>p90 浅蓝->亮青白。=> 紫与浅蓝各占相当比例（混色）。"""
    if lum <= p20:
        return mix(C0, C1, lum / max(1e-6, p20))
    if lum <= p60:
        return list(C1)
    if lum <= p90:
        return mix(C1, C2, (lum - p60) / max(1e-6, p90 - p60))
    return mix(C2, C3, min(1.0, (lum - p90) / max(1e-6, 1.0 - p90)))

def hue_of(rgb):
    r, g, b = [v / 255.0 for v in rgb]
    mx, mn = max(r, g, b), min(r, g, b)
    if mx == mn:
        return None
    d = mx - mn
    if mx == r:
        h = ((g - b) / d) % 6
    elif mx == g:
        h = (b - r) / d + 2
    else:
        h = (r - g) / d + 4
    return int(h * 60)

def build(src_name, out_path):
    im = Image.open(io.BytesIO(z.read(src_name))).convert("RGBA")
    ls = lums_of(im)
    p20, p60, p90 = pct(ls, 0.20), pct(ls, 0.60), pct(ls, 0.90)
    print("  base luminance percentiles: p20=%.3f p60=%.3f p90=%.3f" % (p20, p60, p90))
    out = Image.new("RGBA", im.size)
    px = []
    for (r, g, b, a) in list(im.getdata()):
        if a == 0:
            px.append((0, 0, 0, 0))
            continue
        lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        c = ramp(lum, p20, p60, p90)
        px.append((c[0], c[1], c[2], a))
    out.putdata(px)
    out.save(out_path, "PNG", optimize=True)
    hue = Counter()
    for (r, g, b, a) in px:
        if a == 0:
            continue
        h = hue_of((r, g, b))
        if h is None:
            hue["gray"] += 1
        elif 240 <= h <= 300:
            hue["purple"] += 1
        elif 170 <= h < 240:
            hue["blue"] += 1
        else:
            hue["other"] += 1
    tot = max(1, sum(hue.values()))
    print("  %-34s %dx%d %dB  purple=%.0f%% blue=%.0f%% other=%.0f%%" % (
        os.path.basename(out_path), im.size[0], im.size[1], os.path.getsize(out_path),
        100.0 * hue["purple"] / tot, 100.0 * hue["blue"] / tot, 100.0 * hue["other"] / tot))
    return im.size

print("rebuild with TConstruct default molten base:")
s1 = build(SRC_STILL, OUT_STILL)
s2 = build(SRC_FLOW, OUT_FLOW)
z.close()
print("still %dx%d (frames=%d) / flowing %dx%d (frames=%d)" % (
    s1[0], s1[1], s1[1] // 16, s2[0], s2[1], s2[1] // 32))
