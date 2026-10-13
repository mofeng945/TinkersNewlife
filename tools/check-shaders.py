# TNL957 v2：从"能在这套环境里跑起来的真货"里取合法集（★forge 板 + 两个参考模组 ✓）
import io, json, os, re, zipfile, glob
CAND = []
for root in (r"C:\Users\ASUS\.gradle\caches", r"D:\tex\.minecraft\libraries", r"D:\tex\.minecraft\versions"):
    for pat in ("**/client-extra*.jar", "**/*minecraft*assets*.jar", "**/forge-1.20.1-*-universal.jar"):
        CAND += glob.glob(os.path.join(root, pat), recursive=True)
CAND += glob.glob(r"D:\tex\.minecraft\versions\*\mods\*avaritia*.jar")
CAND += glob.glob(r"D:\tex\.minecraft\versions\*\mods\*goeticlegacy*.jar")
legal_attr, legal_types, legal_keys = set(), set(), set()
srcs = []
for j in dict.fromkeys(CAND):
    try:
        z = zipfile.ZipFile(j)
    except Exception:
        continue
    cnt = 0
    for n in z.namelist():
        if re.search(r"shaders/core/[^/]+\.json$", n):
            try:
                d = json.loads(z.read(n).decode("utf-8"))
            except Exception:
                continue
            cnt += 1
            legal_keys |= set(d.keys())
            if "attributes" in d:
                legal_attr.add(tuple(d["attributes"]))
            for u in d.get("uniforms", []):
                if "type" in u:
                    legal_types.add(u["type"])
    if cnt:
        srcs.append((os.path.basename(j), cnt))
    z.close()
print("  取样来源：")
for s, c in srcs[:6]:
    print("    %-46s %d 个 core shader" % (s, c))
print("  合法字段 = %s" % sorted(legal_keys))
print("  合法 uniform 类型 = %s" % sorted(legal_types))
print("  合法 attributes 组合 = %d 种" % len(legal_attr))
# TNL958 新增：★GLSL 低级错误检查（vec2 上不许出现 .z/.w 的 swizzle）
import re as _re
bad = 0
for _f in sorted(glob.glob("cursed/src/main/resources/assets/tinkersnewlife/shaders/core/*.fsh")
                 + glob.glob("cursed/src/main/resources/assets/tinkersnewlife/shaders/core/*.vsh")):
    _src = io.open(_f, encoding="utf-8").read()
    for _m in _re.finditer(r"vUv\s*\.\s*([xyzw]{2,4})", _src):
        if any(c in "zw" for c in _m.group(1)):
            print("  [FAIL] %s 里 vUv（vec2）用了非法 swizzle .%s" % (os.path.basename(_f), _m.group(1)))
            bad += 1

for f in sorted(glob.glob("cursed/src/main/resources/assets/tinkersnewlife/shaders/core/*.json")):
    errs = []
    d = json.loads(io.open(f, encoding="utf-8").read())
    extra = set(d.keys()) - legal_keys
    if extra: errs.append("非法字段 %s" % sorted(extra))
    if "attributes" in d and tuple(d["attributes"]) not in legal_attr:
        errs.append("attributes 组合没在任何真货里出现过：%s" % d["attributes"])
    for u in d.get("uniforms", []):
        if u.get("type") not in legal_types:
            errs.append("uniform 类型非法：%s=%s" % (u.get("name"), u.get("type")))
    vsh = f[:-5] + ".vsh"
    if os.path.exists(vsh):
        decl = re.findall(r"^\s*in\s+\w+\s+(\w+)\s*;", io.open(vsh, encoding="utf-8").read(), re.M)
        if set(decl) != set(d.get("attributes", [])):
            errs.append("vsh in=%s ≠ json attributes=%s" % (decl, d.get("attributes")))
    if errs: bad += 1
    print("  [%s] %s" % ("PASS" if not errs else "FAIL", os.path.basename(f)))
    for e in errs: print("        - " + e)
print("  === 结论：bad %d ===" % bad)
