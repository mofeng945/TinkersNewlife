# 巫师套装 · 用 Blockbench 改模型/贴图指南

> 适用范围：本模组（Forge 1.20.1）。所有几何都在
> `src/main/java/com/mofengbaizhi/tinkersnewlife/client/model/WizardArmorModel.java`，
> 颜色表在 `client/model/WizardArmorColors.java`。

---

## 0. 先选你要改什么

| 想改的东西 | 改哪里 | 要不要写代码 |
|---|---|---|
| 只改**花纹/明暗/轮廓细节** | `textures/tinker_armor/wizard_armor/all_grey.png`（64×64） | 不用 ✓ |
| 只改**图标** | `textures/item/wizard_{hat,robe,leggings,boots}_{body,trim,lace,cloth,buckle}.png`（各 16×16） | 不用 ✓ |
| 改**配色映射**（哪一组用哪个材料槽的色） | `WizardArmorModel#renderToBuffer` 里的 `draw(..., index, ...)` | 改数字 ✓ |
| 改**材料色本身** | `mantle/colors.json`（材料色总表）→ 重新生成 `WizardArmorColors.java` | 脚本 ✓ |
| 改**模型形状**（帽檐宽窄、下摆长短、加部件） | `WizardArmorModel#createBodyLayer()` | 要 ✓ |

**关键前提（决定你怎么画贴图）**：盔甲的颜色是**顶点着色**染出来的（每个部件组按它的材料槽取色 ✓），
所以 `all_grey.png` **只需要画"亮灰底 + 暗灰细节/轮廓"** ✓ ——
**不要在底图里画彩色** ✗，否则材料色会叠在彩色上、看起来脏 ✗。
图标同理：`_body/_trim/_lace/_cloth/_buckle` 五张都是**灰度图** ✓，它们在游戏里各自被染成对应槽的材料色 ✓。

---

## 1. 用 Blockbench 建模（推荐流程）

1. 新建项目选 **Java Block/Item**（我们用的就是方块几何 ✓，不是 GeckoLib ✗）。
2. 骨骼命名**必须**用原版同名（本模组的姿态拷贝靠这个 ✓）：
   `head` / `body` / `right_arm` / `left_arm` / `right_leg` / `left_leg`
   （子部件随便起名，例如 `hat_brim`、`robe`、`sleeve_right` ✓）。
3. 建议的层级（与当前实现一致 ✓）：
   ```
   head       ← hat_brim / hat_crown / hat_tip
   body       ← robe
   right_arm  ← sleeve_right        left_arm ← sleeve_left
   right_leg  ← leg_wrap_right_leg / boot_cuff_right_leg
   left_leg   ← leg_wrap_left_leg  / boot_cuff_left_leg
   ```
4. 画完导出 **Java Block/Item Model（.json）** 发我 ✓ —— 我按 `elements` 的 `from/to/rotation/uv`
   逐条翻成 `CubeListBuilder#addBox` + `texOffs` + `PartPose` ✓（坐标换算见 §2 ✓）。

> 为什么不能直接把 Blockbench 的 JSON 丢进资源包：原版/Forge **没有**"从 JSON 烘焙实体模型"的加载器 ✗，
> 本模组也没引入 GeckoLib ✗ ⇒ 几何只能落在 Java 代码里 ✓（这也是匠魂本体盔甲的做法 ✓）。

---

## 2. 坐标与单位换算（最容易踩的坑）

| Blockbench 里 | 代码里 | 说明 |
|---|---|---|
| 立方体 `from` / `to`（像素，Y 向下） | `addBox(x, y, z, w, h, d)` | `x=from.x`、`y=from.y`、`z=from.z`；`w=to.x-from.x`、`h=to.y-from.y`、`d=to.z-from.z` ✓ |
| 立方体 `uv`（贴图左上角像素） | `texOffs(u, v)` | 直接照抄 ✓（一张箱子需要 `(w+d)*2 × (h+d)` 的区域 ✓） |
| 骨骼位置（相对父骨骼） | `PartPose.offset(px, py, pz)` | 单位同样是**像素**（1 像素 = 1/16 格 ✓） |
| 骨骼旋转 | `PartPose.rotation(rx, ry, rz)` | **弧度**（不是角度 ✗）：`Math.toRadians(角度)` ✓ |
| 膨胀 | `new CubeDeformation(值)` | 我们主模型用 `0.5`（盔甲层厚度 ✓），自加部件用 `0.0` ✓ |

**相对谁**：挂在 `head` 下的部件，坐标原点在**头部部件原点**（脖子处 ✓）；
挂在 `right_leg` 下的，原点在**髋部** ✓ ⇒ 靴筒要写 `y≈10` 才会落到脚踝 ✓
（这正是上一版把靴子画到身体上的原因：直接渲染子部件、漏了父变换 ✗，已修 ✓）。

**当前实现的具体数值**（想微调就直接改这些 ✓）：

| 部件 | 归属 | 尺寸 | 位置 |
|---|---|---|---|
| `hat_brim` 帽檐 | head | 8×1×8 @(-4,-1,-4) | `offset(0,-7,0)` |
| `hat_crown` 帽筒 | head | 8×4×8 @(-4,-4,-4) | `offset(0,-7.5,0)` |
| `hat_tip` 锥顶 | head | 4×3×4 @(-2,-3,-2) | `offset(0,-11.5,0)` |
| `robe` 下摆 | body | 8×12×4 @(-4,0,-2) | `offset(0,10,0)` |
| `sleeve_*` 宽袖 | arms | 4×8×4 @(-2,-2,-2) | `offset(∓1,4,0)` |
| `leg_wrap_*` 护腿 | legs | 5×10×5 @(-2.5,0,-2.5) | `offset(0.5,1,0)` |
| `boot_cuff_*` 靴筒 | legs | 6×4×6 @(-3,0,-3) | `offset(0.5,10,0)` |

---

## 3. UV 布局（64×64，四组互不重叠）

```
法帽   cols 0..32   rows 0..30     hat_brim texOffs(0,0)   hat_crown (0,10)   hat_tip (0,23)
法袍   cols 32..64  rows 0..29     robe     texOffs(32,0)  sleeves (32,17) / (48,17)
护腿   cols 0..20   rows 33..48    leg_wrap texOffs(0,33)
靴子   cols 22..46  rows 33..43    boot_cuff texOffs(22,33)
```

- 四组区域**必须互不重叠** ✓（否则一件的贴图会盖到另一件 ✗）。
- 你若想换 128×128 更大画布：改 `LayerDefinition.create(mesh, 128, 128)` ✓ + 同步所有 `texOffs` ✓。
- 想加部件：先给它划一块**新区域** ✓，再在 `createBodyLayer()` 里加箱子 ✓。

---

## 4. 哪个部件用哪个材料槽（决定颜色）

| 部件组 | 槽 | 材料来源 |
|---|---|---|
| 各件主体（帽 / 下摆 / 护腿 / 靴筒） | 0 | 部位镶板（`plating_*`） |
| 宽袖 | 1 | 锁链基底 |
| 下摆 | 2 | 锁链基底（第二块） |
| 护腿系带 | 3 | **法袍系带** |
| 靴口 | 4 | **魔术布料** |

改法：`renderToBuffer` 里 `draw(poseStack, buffer, light, overlay, 槽号, 父部件, 部件...)` 的数字 ✓。
新增部件组时照抄一行即可 ✓（记得传对父部件 ✓，否则又会被画到错误位置 ✗）。

---

## 5. 图标（背包里那 16×16）

- 每件 5 张灰阶图：`wizard_<件>_body / _trim / _lace / _cloth / _buckle` ✓
- 物品模型 `models/item/wizard_<件>.json` 用匠魂的 `tconstruct:tool` 加载器，
  `parts` 里写 `index 0..4` ⇒ **图标上同时显示 5 个槽的材料色** ✓
- 你在 Blockbench 里画好 16×16 灰度图标后，**保持文件名不变**直接替换即可 ✓

---

## 6. 改完怎么生效

```powershell
cd G:\TinkersNewlife
powershell -NoProfile -ExecutionPolicy Bypass -File tools\check-json.ps1
cmd /c ".\gradlew build --console=plain > build\cb.txt 2>&1"   # 看 BUILD SUCCESSFUL
powershell -NoProfile -ExecutionPolicy Bypass -File tools\deploy.ps1   # 游戏运行时会被拒绝
```

进游戏穿上四件看效果；只看图标可以开创造栏翻本模组的那一栏 ✓。

---

## 7. 分工建议（最省你时间的方式）

1. 你在 Blockbench 里把形状/贴图做到满意 ✓（骨骼名按 §1 ✓）；
2. 导出 **Java Block/Item Model** 的 JSON + 贴图 PNG，发给我 ✓；
3. 我把它翻成 `WizardArmorModel` 的代码（含 UV 分区与父部件套用 ✓），构建部署 ✓；
4. 你进游戏看，继续微调 ✓。

> 如果你只想动**贴图**（不动形状），那连代码都不用碰：直接替换 `all_grey.png` 与 20 张图标 PNG 即可 ✓。


---

## 8. 待做：接匠魂生成器到"穿戴外观"（渲染层实施方案）

**背景**：`Item#getArmorTexture` **整件只能给一张贴图** ✗，而我们的法帽有 5 组部件、每组要用
**自己材料槽**那张图（帽檐用锁链基底的生成图、帽筒用魔术布料的生成图…）⇒ 必须**在渲染层逐组换图** ✗。
已完成的前置：`client/renderer/WizardArmorTextures.java`（材料 → `<前缀><材料>armor.png` /
`...leggings.png` 的解析 + 存在性缓存；不存在返回 null ⇒ 退回 `all_grey.png` + 顶点着色 ✓）。

**要写的**：`client/renderer/WizardArmorLayer.java`（`RenderLayer<AbstractClientPlayer, PlayerModel<...>>`），
在 `client/handler/ClientEventHandler` 里用 `EntityRenderersEvent.AddLayers` 挂到
`player` / `humanoid`（含 `armor_stand`）渲染器上 ✓。

**每帧逻辑**：
1. 遍历四个装备槽，挑出 `WizardArmorItem` 的件 ✓；
2. `ToolStack.from(stack)` 取该件 5 个材料槽（反射读，读不到走兜底 ✓）；
3. 按组（帽：hat_*；袍：robe/sleeve_*；护腿：leg_wrap_*/boot_cuff_*；靴：同）逐组：
   - `WizardArmorTextures.materialArmorTexture(prefix, 材料, 是否腿部层)` 取图 ✓；
   - 有图 ⇒ `bufferSource.getBuffer(RenderType.armorCutoutNoCull(tex))` 画该组 ✓（**这时才能换图** ✓）；
   - 无图 ⇒ 用 `all_grey.png` 的 RenderType + 顶点色（`ModelPart#render(..., r,g,b,a)` ✓）画该组 ✓；
4. 姿态：`model.copyPropertiesFrom(playerModel)` 之类把玩家姿态拷进来（同名部件 ✓），
   自加部件是原版部件的子节点 ⇒ 自动跟随 ✓；
5. 第一人称 / 其它类人生物 / 盔甲架：这一层是**附加绘制**，不要动原版层 ⇒
   需要把本模组件的 `getArmorTexture` 指向**透明贴图**以免原版层重复画 ✗（注意失败兜底：万一本层异常，
   盔甲不能整件消失 ⇒ 在 catch 里回退到"让原版层正常画" ✗）。

**验证**：跑生成指令产出 `<前缀><材料>armor.png` 后，只改材料不改模型 ⇒ 外观应立刻跟着变 ✓；
把生成图删掉 ⇒ 应自动回到灰阶 + 顶点着色（外观不坏 ✓）。