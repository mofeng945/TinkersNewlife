# tools/ 工具索引

> 每次清理后请同步本文件（清理记录见 `docs/开发备忘录.md` §609）。
> 本目录里的 `.ps1` 大部分含中文，**必须 UTF-8 带 BOM**，否则本机 PowerShell 5.1 按 ANSI 读会语法报错。

## 一、活着的工作流（常跑，别动）

| 文件 | 干什么 | 什么时候跑 |
| --- | --- | --- |
| `check-json.ps1` | 资源 JSON 严格校验（node JSON.parse） | **每次改动后必跑**，要求 `bad 0` |
| `deploy.ps1` | 构建产物装到两个测试实例（**检测到 MC 在跑就整体拒绝**） | 部署时 |
| `release.ps1` | 一条命令发版：构建 -> 部署 -> 上传 Modrinth | 发版时 |
| `check-book-links.ps1` | 扫帕秋莉手册所有内链，报断链 | 改手册后 |
| `check-book-pages.ps1` | 报"一页放不下"的手册文本页（Patchouli 156px 高） | 改手册后 |
| `check-chaos-flow-gates.ps1` | 静态自检：混沌之流的结算闸门（单次/随机/三连禁令） | 改混沌之流后 |
| `check-life-lamp-gates.ps1` | 静态自检：命灯指轮三道闸门（优先级 + 判定口径） | 改命灯后 |
| `scan-item-tags.ps1` | 扫整合包所有 jar 的物品标签 -> `build\tag-scan\` | 补标签时 |
| `add-color.ps1` | 往 `mantle/colors.json` 加一条材质/特性颜色（防手改事故） | 加新材质时 |
| `cfr.jar` | 反编译工具（查其他模组/匠魂源码用） | 需要读 jar 源码时 |

## 二、会覆盖贴图的生成器（跑之前先备份 / 先 `git status`）

这些脚本的目标路径里有**手绘贴图**。它们既能重生成，也能把画盖掉 —— 跑之前确认你确实要重生成。

| 文件 | 会写哪些贴图 |
| --- | --- |
| `GenElderCrystalFromVanilla.ps1` | 水晶物品/方块/矿石/台座（原版贴图改色；写盘前自动备份到 `tools\backup-block-textures\`，`-Restore` 还原） |
| `gen-ingot-texture.ps1` / `gen-block-texture.ps1` | 锭 / 储存块（拿原版铁锭、铁块改色） |
| `gen-fluid-texture.ps1` / `gen-fluid-from-tcon.ps1` | 流体 still/flowing + `.mcmeta` |
| `gen-wizard-armor-materials.ps1` | 巫师套装"每材料一张"盔甲贴图（12 张） |
| `sync-wizard-base-textures.ps1` | 把 `grey.png` 同步到 hat/robe/mage_leggings/mage_boots 四张底图 |
| `gen-curse-vault-art.ps1` | 呪蔵三张方块贴图（能量条是 24 帧动画）【已归档】 |
| `enhance-guide-book-art.ps1` | 编年史封面（**只加细节不重画**；首次运行自动备份） |
| `make-conscience-shades.ps1` | 「心」的 11 张灰度档图标 + 11 个模型（你重画底图后重跑它） |
| `downscale-durandal-sword.ps1` | 杜兰达尔之剑（从 `tools\art-src\durandal_sword_256.png` 缩绘）【已归档】 |
| `import-reference-sword.ps1` | 参考图 -> 1:1 还原成 `durandal_sword.png` |

## 三、巫师套装底图/UV 维护（手绘底图时用）

| 文件 | 干什么 |
| --- | --- |
| `wizard-atlas-guide.ps1` | 把 128x128 底图放大 8 倍并标出每个方块每个面的矩形（数据源 = `WizardArmorModel.java` 的 `addBox`） |
| `patch-wizard-hat-atlas.ps1` | 往底图补"法帽新加方块"的 UV 区域 |
| `dump-box-faces.ps1` | 把某个方块"源图六面"与"底图六面矩形"打成字符画做肉眼比对 |
| `verify-symmetry.ps1` | 校验左右成对方块在底图上是否**逐像素镜像对称**（独立复核） |

## 四、界面离线预览

| 文件 | 干什么 |
| --- | --- |
| `momo-ui-preview.ps1` | 墨默对话界面离线预览（复刻 `MomoTalkScreen` 的排版数学，不开游戏就能查排版） |
| `Cutout.java` | 墨默立绘抠图（纯色背景 + 从边缘泛洪） |

## 五、一次性工具（已归档 `archive\`，可随时取回）

| 子目录 | 内容 | 前置条件（已不在 / 平时用不上） |
| --- | --- | --- |
| `archive\wizard-armor\` (11) | Blockbench 导入器 / 探针 / 逐面比对：`import-hat-blockbench` `import-robe-texture` `reblit-hat-texture` `probe-*`(5) `map-paint-islands` `wizard-atlas-map` | 用户的 Blockbench 导出（桌面 `wizard_robe.json` 等） |
| `archive\models\` (4) | 模型换算器：`convert-robe-model` `convert-leggings-model` `convert-boots-model` + `gen-fluid-tags`（流体标签一次性生成） | Blockbench 模型 JSON |
| `archive\texture-gen\` (13) | 被取代/用完的生成器：`GenElderCrystalPixelArt`（被 §536 原版改色方案取代）、`gen-elder-crystal-art`、`GenEeNetworkPixelArt`、`GenEeExtractorGui`（GUI 已改纯 fill）、`gen-curse-bottle-art`、`gen-cursed-scroll-art`、`gen-life-lamp-ring-art`、`gen-magic-cloth-texture`、`gen-mask-texture`、`gen-twin-ring-texture`、`fix-fluid-textures`、`downscale-durandal-sword`、`gen-curse-vault-art` | 各自那次任务 |

## 六、2026-09-23 清理时删掉的（§609）

- `GenMomoBubble.java` —— 无复用价值（气泡九宫格切片现在由 GUI 生成脚本同族的 Java 代码负责）
- `read-bbmodel.ps1` —— 读 `.bbmodel` 的一次性工具；**且 Blockbench 自动备份是 `<lz>` 开头的压缩格式，本脚本读了会误导** => 与其留着坑，不如删掉（下次真要用，照那个坑重写更好）

## 七、目录里的非脚本文件

- `art-src\` —— 用户给的参考原图（`durandal_sword_256.png` / `ref_scroll.png` / `guide_book_original.png`），**只读素材，别改**
- `backup\` —— 一次性备份（`wizard_robe_lace.png.bak`，§390 那次事故的保险）
- `backup-block-textures\` —— `GenElderCrystalFromVanilla.ps1` 的自动备份 + `-Restore` 源

## 八、星象仪（planetarium）材质工具（§617）

| 文件 | 干什么 |
| --- | --- |
| `GenPlanetariumArt.java` | 画星象仪的**两层**材质：`base.png`（底盘，固定不变）+ `star_0..7.png`（星象图，随月相）。用法 `java tools/GenPlanetariumArt.java [--force]`（默认**已存在就跳过** ✓ 防覆盖手绘 ✓） |
| `PreviewPlanetarium.java` | 离线预览：把 base 与 8 张星象图**逐张叠合**放大出图，用来验"两层咬不咬合、月相画序对不对"（不开游戏 ✓） |
### 月相几何的**数值验收**（§618 靠它抓到"8 张图整体错位"）

| 文件 | 干什么 |
| --- | --- |
| `VerifyMoonGeometry.java` | 验证**判据**：逐相位打印"期望受光比例 / 实测 / 亮面朝向"，公式对不对先在这里证伪 |
| `AnalyzeMoonPhases.java` | 验证**产物**：量已生成的 `star_*.png`（亮面像素数 + 亮面重心在哪侧）。⚠ 验收对象必须是真实 PNG，不能是公式自证 |
| `DebugMoonMap.java` | 把相位打成字符画（`#`受光 / `.`暗面），一眼看明暗界线在哪 |

用法都是 `java tools/<文件名>.java`（JDK 11+ 单文件运行）。