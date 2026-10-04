# 匠魂长矛 · 第一人称对照包（§919）

这个文件夹是给**在 Blockbench 里做长矛第一人称动画**用的参照物。

## 一句话结论

**1.20.1 的匠魂工具是"平面贴图拼的"，不是方块模型。**
`broad_blade.json` / `tough_handle.json` 里**没有几何**，只有
`"loader": "tconstruct:material"` ＋ 一张贴图 ＋（刀身）一个 `"offset": [-1, 1]`；
几何来自父模型 `forge:item/default`（就是一张平面）⇒ 长矛＝**三张 16×16 贴图叠出来的一张斜向矛**。
游戏里第一人称看到的样子，就是这个文件夹里的 `spear_composed.png` 那张图（旋转缩放之后）。

## 文件清单

| 文件 | 说明 |
|---|---|
| `spear_composed.png` | **合成图**：`handle1` + `handle2` + `head`（head 按 `offset [-1, 1]` 偏移）叠出来的长矛本体 ✓ 动画时用它当贴图 |
| `spear_firstperson.geo.json` | **基岩几何**：一根骨头 `item` ＋ 一张 16×16 平面，枢轴在模型原点 ⇒ **Blockbench 直接导入** |
| `spear_flat_java.json` | 等价的 Java 平面模型（3 层，带 per-face UV）⇒ 想在 "Java Block/Item" 格式里看 / 对 UV 时用 |
| `textures/` | 三张原始贴图（`head` / `handle1` / `handle2`，都是 16×16） |
| `原文件/` | 模型链条原文件：我们的 `models/item/spear.json`、工具定义、以及 TC 的 `base/tall`、`broad_blade`、`tough_handle` |

## 游戏里第一人称到底做了什么变换（关键数字）

`assets/tinkersnewlife/models/item/spear.json` 里写着：

```json
"firstperson_righthand": {
  "rotation": [0, -90, 55],
  "translation": [1.13, 3.2, 1.13],
  "scale": [1.35, 1.35, 1.35]
}
```

左手是 `rotation [0, 90, -55]`，其余相同。

这个变换是**在物品自己的显示阶段**乘上去的（动画之后）—— 之前"和你做的完全不一样"就是因为我把动画直接加在了**手部空间**（这个变换之前）。
§918 已经改成按 `M·R·M⁻¹` 共轭（`M` = 上面这个变换）⇒ **理论上现在 Blockbench 里看到的和游戏里应该一致**。

⚠ 注意这里 `scale` 是 **1.35**（不是原版手持物品常见的 0.68），因为匠魂为了把矛做大专门覆盖了它。

## 怎么用（Blockbench 工作流）

1. Blockbench → 新建 **Bedrock Entity** 工程
2. `File → Import` 导入 `spear_firstperson.geo.json` ⇒ 得到骨头 `item` ＋ 一张 16×16 平面
3. 右边 **Texture** 面板导入 `spear_composed.png`（作为该模型的贴图）
4. 骨头 `item` 的枢轴就是**游戏里的旋转中心**（游戏就是在物品模型原点做旋转的）⇒ 直接对 `item` 做旋转/位移
5. 做完 → 导出动画 → 存成 `spear_first_person.animation.json`（或改名 `spear_first_person.json`）
6. 丢进 `src/main/resources/assets/tinkersnewlife/tnl_anim/` ⇒ 游戏内 **F3+T** 立即生效（不用重编译）
   ⚠ **不要**丢进 `animations/`（那是 GeckoLib 的保留目录，会崩，见 §917）

## 坐标系注意（踩过的坑）

- 基岩 **+Y 朝上**；Java 方块/物品模型 **+Y 朝下** ⇒ 位移 Y 与旋转 X/Z 的符号是反的，加载器已自动换算
- 时间键单位是**秒**（加载器 ×20 换成 tick）；`position` 单位是 **1/16 格**
- 若发现上下/左右反了，可以在动画 json 里加开关（不用重编译，F3+T 生效）：
  `_tnl_flip_rot_x` / `_tnl_flip_rot_z` / `_tnl_flip_pos_y` / `_tnl_flip_pos_z` / `_tnl_model_space`
- 关键帧**只能填数字**（`math.*` 这类 Molang 表达式加载器不会求值）

## 未确证

- 合成图的**层叠顺序**（handle1 → handle2 → head）是按我们模型里 `parts` 的顺序定的，TC 内部若有额外 z 分层，实际观感可能有 1 像素级差别（未实机逐像素比对）
- `head` 的 `offset [-1, 1]` 方向（左/下）是按 TC 的 `broad_blade.json` 字面值套的，未实机确证
