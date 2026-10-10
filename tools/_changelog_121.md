
---

## §1292 版本 **1.0.1.21** 更新日志（2026-10-11）

> 本版从 `1.0.1.20` 累加。**只改了 `gradle.properties` 的 `mod_version`** ✓（其余版本相关字段一律不动）。
> ⚠ 以下条目中标注"**未实机验证**"的，是按用户口径实现并通过构建/JSON 校验、但尚未由用户实测的部分。

### 一、咒术 · 真伤管线重构
- **取消"第二次匿名 hurt"**：假人身上显示 3 段伤害数字的根因是合成了一次额外 `hurt`；
  现改为**单次 `hurt` ＋ 缺口直写**，并把客户端同步抑制（`quiet`）收紧为
  "**仅当目标的血量会自行重置**（如假人）才跳过同步" ⇒ 使徒免疫窗口重新可破 ✓。
- `EXTRA_HEALTH_CHANNEL = false`：关闭"扩展血量通道"（RevelationFix 的 `AOGBOGBO…` 访问器在使徒身上返回 666，
  会污染 `startHp`/`dealt` ⇒ 表现为"打使徒不掉血"）✓。
- `forgetEntity(UUID)`：清 `LAST_START_HP / DAMAGE_OWED / SHADOW_HEALTH / PENDING` ✓；
  所有探针统一降为 `LOGGER.debug`（27 info ＋ 2 warn ⇒ 30 debug）✓。

### 二、咒术 · 收回／封印链路加固
- `GourdJailEntity.forcePurge` 改用 `TruePierce.rawSetHealth(living, 0.0F)` ✓。
- `finishSeal` 回滚时还原 `hpBeforePurge` ＋ `cancelPurgeRecheck` ✓。
- 新增 **20 tick 跨 tick 复检**（`schedulePurgeRecheck` / `tickPurgeRecheck`，由 `ServerLevelTickTailMixin` TAIL 驱动）✓。
- `CursedSpiritTechnique.recallReleased` 追加复检 ＋ `forgetEntity` ✓；`ApostlePatch.TRAP_COOLDOWN` 过期清理 ✓；
  `ShikigamiHandler.recallAll` 改名 `estimatedRecallRefund`（原为死代码）✓。

### 三、玩偶（FumoMo）· 舞蹈
- 每个方块实体记住自己的 `discStyle`；新增 `DISC_SOURCES`（按维度＋坐标登记唱片来源）＋ `discStyleNear()`
  （取 **16 格内最近** 的来源）⇒ **两张唱片同时播放时，玩偶跟随最近的那一首** ✓。
- 舞蹈序列：**1 秒一个循环、无停顿**；`ND_TURN = 45°`、`ND_LEAN = 15°`（`ND_LEAN_SIGN = -1`）、
  **转动时上弹** `ND_BOUNCE = 0.022` ✓。⚠ 方向与弹跳幅度**未实机验证**。

### 四、唱片
- 新增唱片 **「Little Wish」**（`music_disc_tell_me`）：音频 `sounds/music/tell_me_tell_me.ogg`、
  `ModSounds.MUSIC_TELL_ME(+_ID)`、`ModItems.MUSIC_DISC_TELL_ME`（`LENGTH_TICKS = 1324`）、
  中英语言键、`music_discs` 物品标签 ✓。
- **贴图**：从桌面那份 14 帧 GIF 逐帧抠背景（只清"本来就是透明"的像素 ＋ 公共外接框防抖）⇒
  **16×224 的 14 帧竖排动画** ＋ `music_disc_tell_me.png.mcmeta`（`frametime: 2`）✓。
  ⚠ 早期版本曾用"四角颜色＋容差泛洪"把鱼身上的**黑色耳机**一起抠掉，已修正 ✓。
- **语言键**：名字 `Little Wish`、描述 `遥 - 塞壬唱片` ✓（与旧唱片 `.desc` 同款"作者 - xx"格式 ✓）。
- **获取方式**：新增战利品修饰 `little_wish_from_fishing`（`minecraft:gameplay/fishing`，`chance 0.01`）⇒
  **钓鱼 1% 额外掉落**（不挤占原版产出）✓；已登记进 `data/forge/loot_modifiers/global_loot_modifiers.json` ✓。
- **JEI**：「朋友的酒＝末地城宝箱 20%」「Little Wish＝钓鱼 1%」两页获取提示 ✓（中英语言键配套 ✓）。
- ⚠ 唱片音量：代码侧与 `sounds.json` 的两条尝试**均无效**（点唱机是流式播放），已**全部回退**，
  以音频文件本身为唯一音量来源 ✓。

### 五、熔炼与材料
- **古代咒术残卷 → 咒力残秽**：`amount` 由 `1` 提升为 **`5`**（mb）✓（`recipes/smeltery/melting/ancient_cursed_scroll.json`）。

### 六、古代咒术残卷 · 刷新与使用
- **刷新规范**：`AncientCursedScrollItem.roll()` 改为**按组成部分**（`CursedSpeechRegistry.Part` 的 6 个槽位）
  逐个判定，**每部分至多一个词条**；命中率逐级 `×0.62` ⇒ **词条越多越稀有**；一个都没中时兜底给第一个咏叹词 ✓。
- **左键直接吟唱**：手持残卷左键攻击目标触发 `onLeftClickEntity` ⇒ 走 `castFromScroll`：
  - 缺槽 ⇒ 该槽 **"最低两个等级"** 里随机补一个词，并先 `CursedSpeechState.learn` 让校验通过 ✓；
  - 咒力消耗按标记 `tnl_scroll_cast` **×5** ✓；
  - **只有真的进入读条**（`CurseChant.isChanting`）才消耗卷轴 ✓。
- **空挥（对空气左键）**：新增 `PacketScrollCast` ＋ 客户端 `ScrollAirSwingHandler`
  （`InputEvent.InteractionKeyMappingTriggered`）⇒ 空挥也会释放 ✓（`ScreenKeyInputMixin` 等旧注入保持不动 ✓）。

### 七、便携工匠站（**本版已撤回**）
- 目标是"手持物品凭空打开匠魂工匠砧 GUI、关闭时把内容存回物品 NBT"。
- 先后尝试 **5 种实现**（游离方块实体／钳制屏幕尺寸／真砧方块＋不可见渲染／延后 10 tick／基岩格借用），
  均能构建成功，但**用户实测仍崩**。
- 根因（**有证据**）：JEI 15.48 的 `GuiEventHandler#onDrawScreenPost` 会把
  `ScreenHelper#getGuiExclusionAreas(screen)` 的每个矩形包成 `ImmutableRect2i`，
  而该方法**只收集已注册的 `IGuiContainerHandler`／`IGlobalGuiHandler` 的 `getGuiExtraAreas()`**（不读屏幕 `xSize`）。
  实测该矩形来自 **Mantle** 的 `JEIPlugin$MultiModuleContainerHandler`（注册于 `MultiModuleScreen` ＝ 工匠站屏幕父类），
  其第三个矩形宽度会在 **`26` 与 `-2` 之间抖动** ⇒ JEI 撞上负数 ⇒ `width must be >= 0` ⇒ 客户端崩。
  ⭐ 也就是说：**JEI 崩溃与"屏幕尺寸算出负值"无关**，而是**某一刻的活值**；
  真正的工匠砧在**空旷处**（或别的维度）布局一次成型，所以不抖、不崩。
- 结论：**本版不发布该功能**。全部实现保留在 git 分支
  **`split-v2-portable-wip`（`d65bd900`）** 与远端 `split-v2-portable` 上，日后可继续做。
- 派生的一条**有用的旁证**：若将来还要做，方向应是"**让屏幕的模块布局一出生就定型**"
  （要么真的用真砧，要么把真砧的方块实体数据完整复制过来），而不是去改屏幕尺寸。

### 八、工程与工具
- 本轮排查新写的诊断探针（`StationScreenDiagnostics`、`JeiExclusionProbe`）**已随回退一并移除** ✓。
- 清理：删掉 `tools\_tmp_*` 临时目录/脚本共 38 项（★不碰任何既有贴图 ✓）。
- 新增 `tools/_add_jei_lang.py`（用 Python 写中文语言键，避开 `pwsh -Command` 的编码坑）✓。

### 验证
- `check-json`：**bad 0**（common 2070 个）✓
- `gradlew singleJar`：**BUILD SUCCESSFUL** ✓
- 产物：`tinkersnewlife-1.0.1.21.jar`（15,632,859 B）✓ 两包一致部署 ✓
- ⚠ **未实机验证**：真伤/封印改动、玩偶新舞蹈、Tell Me Tell Me 的获得与动画贴图、残卷刷新与左键吟唱、
  JEI 两页提示 —— 均待用户实测确认。
