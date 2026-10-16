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

# TNL970 新增：GLSL 与 json 交叉对账（这类错以前只能等启动才知道）
import glob as _g, io as _io, json as _json, os as _os, re as _re
bad2 = 0
for _j in sorted(_g.glob("cursed/src/main/resources/assets/tinkersnewlife/shaders/core/*.json")):
    _d = _json.loads(_io.open(_j, encoding="utf-8").read())
    _js = set(x["name"] for x in _d.get("uniforms", []))
    _jattr = set(_d.get("attributes", []))
    for _ext in (".vsh", ".fsh"):
        _p = _j[:-5] + _ext
        if not _os.path.exists(_p):
            continue
        _src = _io.open(_p, encoding="utf-8").read()
        _in = set(_re.findall(r"^\s*in\s+\w+\s+(\w+)\s*;", _src, _re.M))
        _out = set(_re.findall(r"^\s*out\s+\w+\s+(\w+)\s*;", _src, _re.M))
        _uni = {x for x in _re.findall(r"^\s*uniform\s+\w+\s+(\w+)\s*;", _src, _re.M)
                if not x.startswith("Sampler")}   # TNL1190 Sampler0/1/2 are declared by the
        # engine itself; the json lists them under "samplers", not "uniforms", so the old
        # "- {Sampler0}" exemption flagged a perfectly valid Sampler1 as missing.
        _errs = []
        if _ext == ".vsh" and _in and _in != _jattr:
            _errs.append("in %s != json attributes %s" % (sorted(_in), sorted(_jattr)))
        _miss = _uni - _js
        if _miss:
            _errs.append("GLSL 用了但 json 没声明的 uniform: %s" % sorted(_miss))
        if _re.search(r"vec2\s+\w+\s*=\s*vUv\s*\.\s*[xyzw]*z", _src):
            _errs.append("对 vec2 用了 .z/.w")
        if _errs:
            bad2 += 1
            print("  [FAIL] %s" % _os.path.basename(_p))
            for _e in _errs:
                print("        - " + _e)
print("  === GLSL 对账：bad %d ===" % bad2)

# TNL975 新增：抓"用过但没声明"/"重复声明"这两类（今晚各翻过一次车）
import glob as _g2, io as _io2, os as _os2, re as _re2
bad3 = 0
KEY = ["neb", "neb2", "deep", "cloud", "col", "starMask", "localUv", "uv", "suv", "g", "cell", "ru"]
for _f in sorted(_g2.glob("cursed/src/main/resources/assets/tinkersnewlife/shaders/core/*.fsh")):
    _lines = _io2.open(_f, encoding="utf-8").read().split("\n")
    errs = []
    for _v in KEY:
        _decl = None
        _use = None
        for _i, _l in enumerate(_lines):
            _s = _l.split("//")[0]
            if _decl is None and _re2.search(r"\b(float|vec2|vec3|vec4|int)\s+" + _v + r"\s*(=|;)", _s):
                _decl = _i
            if _decl is None and _re2.search(r"\b" + _v + r"\b", _s):
                _use = _i
        if _decl is None and _use is not None:
            errs.append("用了 %s 但从没声明（首见于第 %d 行）" % (_v, _use + 1))
    _seen = {}
    for _i, _l in enumerate(_lines):
        _s = _l.split("//")[0]
        _m = _re2.search(r"^\s*(float|vec2|vec3|vec4|int)\s+(\w+)\s*(=|;)", _s)
        if _m:
            _nm = _m.group(2)
            _ind = len(_l) - len(_l.lstrip())
            if _nm in _seen and _seen[_nm][0] == _ind:
                errs.append("重复声明 %s（第 %d 行与第 %d 行同缩进）" % (_nm, _seen[_nm][1] + 1, _i + 1))
            _seen[_nm] = (_ind, _i)
    if errs:
        bad3 += 1
        print("  [FAIL] %s" % _os2.path.basename(_f))
        for _e in errs:
            print("        - " + _e)
print("  === 低级错误检查：bad %d ===" % bad3)

# TNL1051 two hard checks that would have caught tonight's three GLSL accidents
import glob as _g3, io as _io3, os as _os3, re as _re3
bad4 = 0
for _f in sorted(_g3.glob("cursed/src/main/resources/assets/tinkersnewlife/shaders/core/*.fsh")
                 + _g3.glob("imagination/src/main/resources/assets/tinkersnewlife/shaders/core/*.fsh")
                 + _g3.glob("cursed/src/main/resources/assets/tinkersnewlife/shaders/core/*.vsh")
                 + _g3.glob("imagination/src/main/resources/assets/tinkersnewlife/shaders/core/*.vsh")):
    _raw = _io3.open(_f, encoding="utf-8").read()
    # strip comments first: the escaped Chinese in our comments used to look like calls
    _src = "\n".join(l.split("//")[0] for l in _raw.split("\n"))
    _errs = []
    if _src.count("{") != _src.count("}"):
        _errs.append("brace mismatch: %d open vs %d close" % (_src.count("{"), _src.count("}")))
    _defined = set(_re3.findall(r"^\s*(?:float|vec2|vec3|vec4|int|void|mat4)\s+(\w+)\s*\(", _src, _re3.M))
    _defined |= {"main"}
    _called = set(_re3.findall(r"\b(\w+)\s*\(", _src))
    _builtin = {"if", "for", "while", "return", "switch", "texture", "length", "normalize", "dot",
                "cross", "mix", "clamp", "smoothstep", "step", "fract", "floor", "ceil", "abs",
                "sin", "cos", "tan", "atan", "asin", "acos", "pow", "sqrt", "exp", "log", "min",
                "max", "mod", "radians", "degrees", "distance", "reflect", "refract", "vec2",
                "vec3", "vec4", "mat2", "mat3", "mat4", "float", "int", "discard", "cosh", "sign"}
    _missing = sorted(x for x in _called - _defined - _builtin if not x.startswith(("gl_", "Sampler")))
    if _missing:
        _errs.append("called but never defined: %s" % _missing)
    if _errs:
        bad4 += 1
        print("  [FAIL] %s" % _os3.path.basename(_f))
        for _e in _errs:
            print("        - " + _e)
print("  === hard GLSL checks: bad %d ===" % bad4)
