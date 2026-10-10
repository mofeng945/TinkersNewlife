# -*- coding: utf-8 -*-
"""
§1273 给两张唱片补 JEI「获得方式」文本。

- 朋友的酒（music_disc_doll_music）：末地城宝箱 20%
- Little Wish（music_disc_tell_me）  ：钓鱼 1%

⚠ 为什么用 Python 写：这里必须写中文，而 pwsh -Command 的中文会被控制台编码打乱（本仓踩过多次）。
   本脚本按 UTF-8 读写，只往 lang JSON 末尾插两个键（diff 最小，不动既有一行）。
"""
import io
import json
import os
import sys

repo = sys.argv[1]

KEYS = [
    ("jei.tinkersnewlife.music_disc_doll_music",
     "获得方式：末地城的战利品箱里 20% 概率开出"),
    ("jei.tinkersnewlife.music_disc_tell_me",
     "获得方式：钓鱼时有 1% 概率获得"),
]

files = [
    "common/src/main/resources/assets/tinkersnewlife/lang/zh_cn.json",
    "common/src/main/resources/assets/tinkersnewlife/lang/en_us.json",
    "src/main/resources/assets/tinkersnewlife/lang/zh_cn.json",
    "src/main/resources/assets/tinkersnewlife/lang/en_us.json",
]

for rel in files:
    path = os.path.join(repo, rel)
    if not os.path.isfile(path):
        continue
    with io.open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    # 先做一次合法性体检，避免把坏 JSON 写回去
    json.loads(text)
    added = []
    for key, val in KEYS:
        needle = '"' + key + '"'
        if needle in text:
            continue
        added.append((key, val))
    if not added:
        print("  skip (already there):", rel)
        continue
    stripped = text.rstrip()
    assert stripped.endswith("}"), rel
    body = stripped[:-1].rstrip()
    if not body.endswith(","):
        body += ","
    lines = []
    for i, (key, val) in enumerate(added):
        comma = "," if i < len(added) - 1 else ""
        lines.append('  "%s": "%s"%s' % (key, val, comma))
    text = body + "\n" + "\n".join(lines) + "\n}\n"
    json.loads(text)          # 再体检一次
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("  ok:", rel, "added", len(added))
print("done")
