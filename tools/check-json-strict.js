// 严格 JSON 校验器（由 tools/check-json.ps1 调用）。
//
// ⚠ 为什么必须用严格解析器（node 的 JSON.parse）：
//   PowerShell 的 ConvertFrom-Json / JavaScriptSerializer 都太宽松 ——
//   它们会放过“键名没加引号”（`arcane_cloth: "#777fad"`）这种非法 JSON ✗，
//   而真正读文件的 Mantle(Gson)/Minecraft 是严格的 → 结果是
//   `Failed to load JSON from resource tinkersnewlife:mantle/colors.json`
//   整个 colors.json 全废（所有自定义材料/特性名都退回默认灰）✗。
//   同类漏网的还有“文件开头带 UTF-8 BOM”（严格解析器会直接报错）✗。
//
// ⭐ 2026-09-25 新增语义校验：有序合成必须能放进 3x3。
//   起因：tinkersnewlife:energy_converter 是 3 宽 x 4 行 —— 它靠机械动力(Create)
//   调大了 ShapedRecipe.setCraftingSize 才“加载成功”（所以没有任何解析报错 ✗），
//   但在普通 3x3 工作台里 canCraftInDimensions(3,3) 为 false ⇒ 被过滤
//   ⇒ 玩家永远合不出来 ✗，只有 JEI 刷一条 “There are not enough slots (9) to
//   hold a recipe of this size” 的 ERROR，极易被忽略。
//   ⇒ 光校验“JSON 语法合法”抓不到这类错，必须再校验“配方放不放得进 3x3”。
//
// ⚠ 为什么这段 JS 独立成文件、而不是内联在 .ps1 里：
//   tools/check-json.ps1 是 UTF-8 **带 BOM** 的中文脚本（PS 5.1 需要 BOM 才不会按
//   GBK 读）。但 PS 5.1 在把内联 here-string 写出去时会把其中的中文损坏成 `?`，
//   导致生成的 JS 语法错（实测踩到）。拆成独立文件后，PS 侧只剩纯 ASCII 的
//   `node <file>` 一行，这个编码死角就彻底不存在了。
const fs = require("fs"), path = require("path");
const root = process.argv[2];

let files = [];
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith(".json")) files.push(p);
  }
})(root);

let bad = 0, skipped = 0;
let shaped = 0, oversized = 0;

for (const p of files) {
  const raw = fs.readFileSync(p, "utf8");
  const rel = p.replace(/\\/g, "/").replace(/^.*\/src\/main\/resources\//, "");

  let obj = null;
  try {
    obj = JSON.parse(raw);
  } catch (e) {
    if (/"variants"\s*:\s*\{\s*"/.test(raw)) { skipped++; continue; }   // 空键方块状态：合法
    bad++;
    console.log("  BAD  " + rel + "  ->  " + e.message);
    continue;
  }

  // ---- 语义校验：有序合成必须能放进 3x3 ----
  // 判据取自原版 ShapedRecipe.patternFromJson 的 MAX_WIDTH / MAX_HEIGHT（默认 3）
  // 以及 canCraftInDimensions(3,3)。这里按“玩家能不能在 3x3 里合成”校验，
  // 而不是按“能不能加载” —— 两者在装了机械动力的整合包里会分叉。
  if (obj && obj.type === "minecraft:crafting_shaped" && Array.isArray(obj.pattern)) {
    shaped++;
    const rows = obj.pattern.length;
    const maxW = obj.pattern.reduce((m, s) => Math.max(m, String(s).length), 0);
    if (rows > 3 || maxW > 3) {
      oversized++;
      console.log("  BAD  " + rel + "  ->  有序合成 " + maxW + " 宽 x " + rows +
        " 行，放不进 3x3 工作台（原版会按 canCraftInDimensions(3,3) 过滤掉 ⇒ 永远合不出来）");
    }
  }
}

console.log("checked " + files.length + ", bad " + bad + ", skipped(empty-key blockstates) " + skipped +
  "; crafting_shaped " + shaped + " (超过 3x3 的 " + oversized + " 个)");
// ⚠ 退出码必须把 oversized 也算进去：它虽然没让 JSON 解析失败，
//   但确实是一个"游戏里合不出来"的真缺陷，必须挡住提交。
//   （2026-09-25 自查发现：最初只判 bad > 0 ⇒ 反向测试时它报了 BAD 却仍然 exit 0 ✗）
process.exit((bad > 0 || oversized > 0) ? 1 : 0);
