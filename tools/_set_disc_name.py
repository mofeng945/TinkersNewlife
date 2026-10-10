# -*- coding: utf-8 -*-
"""
§1272 唱片 Little Wish 的中英语言键。

用户口径：
    名字 Little Wish ✗ 作者 遥 ✗ 分隔符后面填 塞壬唱片
⇒ 显示名 = "Little Wish"，描述行 = "遥 - 塞壬唱片"（与旧唱片 `.desc` 同款 `作者 - xx` 格式）

⚠ 为什么用 Python 改而不是 PowerShell：
    这里要写中文 ✗ 而 pwsh -Command 的中文会被控制台编码打乱（本仓踩过多次）✓
    Python 直接按 UTF-8 读写，且只做"逐行文本替换" ⇒ 不会重排整个 JSON（diff 最小）✓
"""
import io
import os
import re
import sys

NAME = "Little Wish"
DESC = "遥 - 塞壬唱片"

repo = sys.argv[1]

files = [
    ("common/src/main/resources/assets/tinkersnewlife/lang/zh_cn.json", NAME, DESC),
    ("common/src/main/resources/assets/tinkersnewlife/lang/en_us.json", NAME, DESC),
]
# 旧形态（拆分前）若还在根 src 下也一并处理
alt = "src/main/resources/assets/tinkersnewlife/lang/"
for lg in ("zh_cn", "en_us"):
    p = alt + lg + ".json"
    if os.path.isfile(os.path.join(repo, p)):
        files.append((p, NAME, DESC))

key_name = "item.tinkersnewlife.music_disc_tell_me"
key_desc = key_name + ".desc"

for rel, name, desc in files:
    path = os.path.join(repo, rel)
    if not os.path.isfile(path):
        print("  skip (not found):", rel)
        continue
    with io.open(path, "r", encoding="utf-8") as fh:
        lines = fh.readlines()
    out = []
    seen_name = False
    seen_desc = False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('"' + key_name + '"'):
            indent = line[:len(line) - len(line.lstrip())]
            out.append('%s"%s": "%s",\n' % (indent, key_name, name))
            seen_name = True
            continue
        if stripped.startswith('"' + key_desc + '"'):
            indent = line[:len(line) - len(line.lstrip())]
            out.append('%s"%s": "%s",\n' % (indent, key_desc, desc))
            seen_desc = True
            continue
        out.append(line)
    if not (seen_name and seen_desc):
        print("  !! keys missing in", rel, "name=", seen_name, "desc=", seen_desc)
        continue
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.writelines(out)
    print("  ok:", rel, "->", name, "/", desc)

# 顺手把字幕键也确认一下（不新建、只报告）
for rel, _, _ in files:
    path = os.path.join(repo, rel)
    if not os.path.isfile(path):
        continue
    with io.open(path, "r", encoding="utf-8") as fh:
        txt = fh.read()
    has = "subtitles.tinkersnewlife.music_tell_me" in txt
    print("  subtitle key present in %s: %s" % (rel, has))
print("done")
