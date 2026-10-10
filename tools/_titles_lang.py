# -*- coding: utf-8 -*-
"""
§1302 咒术系列头衔的语言键（中英各 15 条）。

用户口径（2026-10-11）：显示形态为 [头衔][头衔]…玩家名，超过 3 枚只显示稀有度最高的前三枚。

稀有度（我给的口径）：
  5 传说：特级咒术师
  4 史诗：1级咒术师 / 天与暴君 / 天与咒缚
  3 稀有：2级咒术师 / 咒具师 / 咒骸师 / 世界厌弃之人 / 铁墓危机
  2 优秀：3级咒术师 / 举世皆敌 / 灾厄加身
  1 普通：4级咒术师 / 5级咒术师 / 普通人
"""
import io
import json
import os
import sys

ZH = {
    "ordinary": "普通人",
    "grade5": "5级咒术师",
    "grade4": "4级咒术师",
    "grade3": "3级咒术师",
    "grade2": "2级咒术师",
    "grade1": "1级咒术师",
    "special": "特级咒术师",
    "heaven_tyrant": "天与暴君",
    "heaven_binding": "天与咒缚",
    "tool_smith": "咒具师",
    "puppet_master": "咒骸师",
    "world_abandoned": "世界厌弃之人",
    "enemy_of_all": "举世皆敌",
    "calamity_bearer": "灾厄加身",
    "iron_tomb_crisis": "铁墓危机",
}
EN = {
    "ordinary": "Ordinary",
    "grade5": "Grade 5 Sorcerer",
    "grade4": "Grade 4 Sorcerer",
    "grade3": "Grade 3 Sorcerer",
    "grade2": "Grade 2 Sorcerer",
    "grade1": "Grade 1 Sorcerer",
    "special": "Special Grade Sorcerer",
    "heaven_tyrant": "Heavenly Tyrant",
    "heaven_binding": "Heavenly Restriction",
    "tool_smith": "Cursed Tool Smith",
    "puppet_master": "Corpse Puppeteer",
    "world_abandoned": "Abandoned by the World",
    "enemy_of_all": "Enemy of the World",
    "calamity_bearer": "Calamity Bearer",
    "iron_tomb_crisis": "Iron Tomb Crisis",
}

repo = sys.argv[1] if len(sys.argv) > 1 else "."
files = [
    ("common/src/main/resources/assets/tinkersnewlife/lang/zh_cn.json", ZH),
    ("common/src/main/resources/assets/tinkersnewlife/lang/en_us.json", EN),
]

for rel, table in files:
    path = os.path.join(repo, rel)
    if not os.path.isfile(path):
        print("  skip (missing):", rel)
        continue
    with io.open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    json.loads(text)
    add = []
    for key, val in table.items():
        full = "title.tinkersnewlife." + key
        if '"%s"' % full in text:
            continue
        add.append((full, val))
    if not add:
        print("  exists:", rel)
        continue
    body = text.rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip()
    if not body.endswith(","):
        body += ","
    lines = []
    for i, (k, v) in enumerate(add):
        lines.append('  "%s": "%s"%s' % (k, v, "," if i < len(add) - 1 else ""))
    text = body + "\n" + "\n".join(lines) + "\n}\n"
    json.loads(text)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("  ok:", rel, "added", len(add))
print("done")
