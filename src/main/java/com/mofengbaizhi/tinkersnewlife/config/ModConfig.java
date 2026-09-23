package com.mofengbaizhi.tinkersnewlife.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 匠魂新生通用配置（config/mofengbaizhi/tinkersnewlife-common.toml）
 * <ul>
 *   <li>elder_events：各「获得古神物品的事件」开关（Yog-Sothoth 钥匙 / 黄王 / 拉莱耶呼唤 / 奈亚渴望）</li>
 *   <li>curse_core：咒力核心「可制作可使用」总开关（默认开启）</li>
 *   <li>elder_crystal：古老者水晶 · <b>魔力台座</b>的充能规律（亮度越低越快）、可插拔来源、粒子/音效</li>
 *   <li>techniques / domains：各术式与领域公式的缩放系数（默认 1.0，关闭相关事件后仍保留原生）</li>
 * </ul>
 */
public final class ModConfig {

    private ModConfig() {}

    // ==================== 古神事件开关 ====================
    public static final ConfigValue<Boolean> YOG_SOTHOTH_KEY;
    public static final ConfigValue<Boolean> YELLOW_KING;
    public static final ConfigValue<Boolean> RLYEH_CALL;
    public static final ConfigValue<Boolean> NYARLATHOTEP_DESIRE;

    // ==================== 咒力核心 ====================
    public static final ConfigValue<Boolean> CURSE_CORE_ENABLED;

    // ==================== 古老者水晶 · 魔力台座（充能） ====================
    // 用户口径（2026-09-21）：「亮度越低，充能速度越快」——
    // 于是**没有**"必须夜晚 / 必须露天 / 必须能看到天空"这类额外条件 ✓
    // （夜里露天本来就暗、白天在漆黑洞穴里也照样能充 ✓，亮度这一个量已经把两者覆盖了 ✓）。
    //
    // 公式（唯一一处实现在 content/energy/LightLevelEnergySource）：
    //     rate = pedestal_charge_max_per_second × (light_cap − light) / light_cap
    //     light = level.getMaxLocalRawBrightness(台座上方那一格)   // 0~15，方块光+天光综合，与原版刷怪判定同一个量
    // ⇒ 亮度 0 = 满速、亮度 ≥ light_cap = 不充 ✓。
    /** 默认值：台座「亮度 0」时的满速充能（EE/秒）。§545 用户要求从 5.0 降到 0.5（= 30 EE/分钟 ⇒ 一颗 1000 EE 的水晶 ≈ 33 分钟） */
    public static final double PEDESTAL_DEFAULT_CHARGE_MAX_PER_SECOND = 0.5D;
    /** 默认值：亮度达到这个值就完全不充（15 = 原版亮度上限 ⇒ "亮到顶不充"） */
    public static final int PEDESTAL_DEFAULT_LIGHT_CAP = 15;   // §528 用户要求改回 15：亮度 ≥15（火把旁/白天/照明良好的基地）⇒ **完全不充** ✓
    /**
     * 默认值：启用的环境能量来源清单（§545 起支持多条并行 ✓）。
     * <p>{@code "*"} = <b>全部</b>注册在册的来源都启用（= 用户口径的"四条并行叠加"✓ 默认值 ✓）；
     * 也可以写逗号分隔的清单，例如 {@code "light_level,plant"} 只留两条 ✓。
     * <p>⚠ 老配置里写的是单条 id（如 {@code "light_level"}）⇒ 新语义下就是"只启用那一条" ✓ 正好是它原来的行为 ✓ 不会突变 ✗。
     */
    public static final String PEDESTAL_DEFAULT_SOURCE = "*";

    /** §545 ② 植物：默认球半径（格） */
    public static final int PEDESTAL_DEFAULT_PLANT_RADIUS = 5;
    /** §545 ② 植物：默认"1 点凋灵度值多少 EE"（0.5 ⇒ 每株 0.5 EE/秒 ✓ 用户数值 ✓） */
    public static final double PEDESTAL_DEFAULT_PLANT_EE_PER_POINT = 0.5D;
    /** §545 ③ 匠魂燃料：默认球半径（格） */
    public static final int PEDESTAL_DEFAULT_FUEL_RADIUS = 5;
    /** §545 ③ 匠魂燃料：默认"一个物品份值多少 EE"（用户数值 ✓） */
    public static final double PEDESTAL_DEFAULT_FUEL_EE_PER_ITEM = 0.5D;
    /** §545 ③ 匠魂燃料：默认"一个物品份按多少 tick 折算"（10 = TCon 熔炼 recipe 的最小 `time` 档位 ✓） */
    public static final int PEDESTAL_DEFAULT_FUEL_TICKS_PER_ITEM = 10;
    /** §545 ④ 生物死亡：默认球半径（格） */
    public static final int PEDESTAL_DEFAULT_SOUL_RADIUS = 5;
    /** §545 ④ 生物死亡：默认"每点最大生命值多少 EE"（0.025 ⇒ 20 血 = 0.5 EE ✓ 用户数值 = maxHealth / 40 ✓） */
    public static final double PEDESTAL_DEFAULT_SOUL_EE_PER_HP = 0.025D;
    /** §545 ⑤ 玩家汲取：默认球半径（格） */
    public static final int PEDESTAL_DEFAULT_PLAYER_RADIUS = 5;
    /** §545 ⑤ 玩家汲取：默认"每名玩家每秒多少 EE"（用户数值 ✓） */
    public static final double PEDESTAL_DEFAULT_PLAYER_EE_PER_SECOND = 0.5D;

    /** 台座在亮度 0 时的满速充能（EE/秒；越小越慢） */
    public static final ConfigValue<Double> PEDESTAL_CHARGE_MAX_PER_SECOND;
    /** 亮度达到该值就完全不充（默认 15；调大到 16+ ⇒ 亮处也慢充，如 16 时亮度 15 仍有 0.5×(16−15)/16 ≈ 0.031 EE/秒） */
    public static final ConfigValue<Integer> PEDESTAL_LIGHT_CAP;
    /**
     * <b>启用的环境能量来源清单</b>（§545 起是"多条并行"✓）。
     * <p>{@code "*"}（默认）= 全部启用；也可写逗号分隔的 id 清单（{@code "light_level,plant"} ✓）；
     * 未知 id 只 WARN 一次并忽略 ✓ 不会让台座罢工 ✓。
     */
    public static final ConfigValue<String> PEDESTAL_SOURCE;
    /** 充能时放冷色粒子（密度随速率 ✓ 只在**真的充进去**时放 ✓） */
    public static final ConfigValue<Boolean> PEDESTAL_PARTICLES;
    /** 充能时放轻微音效（紫水晶风铃 ✓ 每 2 秒一次 ✓） */
    public static final ConfigValue<Boolean> PEDESTAL_SOUND;

    // ---- §545 四条新来源：每源"开关 + 数值"（不设总上限 ✓ 直接相加 ✓）----
    /** ① 亮度来源总开关（id {@code light_level}） */
    public static final ConfigValue<Boolean> PEDESTAL_LIGHT_ENABLED;
    /** ② 植物来源总开关（id {@code plant}） */
    public static final ConfigValue<Boolean> PEDESTAL_PLANT_ENABLED;
    /** ② 植物：扫描球半径（格） */
    public static final ConfigValue<Integer> PEDESTAL_PLANT_RADIUS;
    /** ② 植物：1 点凋灵度值多少 EE（0.5 ⇒ 每株 0.5 EE/秒 ✓） */
    public static final ConfigValue<Double> PEDESTAL_PLANT_EE_PER_POINT;
    /** ③ 匠魂燃料来源总开关（id {@code tcon_fuel}） */
    public static final ConfigValue<Boolean> PEDESTAL_FUEL_ENABLED;
    /** ③ 匠魂燃料：扫描球半径（格） */
    public static final ConfigValue<Integer> PEDESTAL_FUEL_RADIUS;
    /** ③ 匠魂燃料：一个物品份值多少 EE */
    public static final ConfigValue<Double> PEDESTAL_FUEL_EE_PER_ITEM;
    /** ③ 匠魂燃料：一个物品份按多少 tick 折算（TCon 熔炼配方 `time` 的基准档） */
    public static final ConfigValue<Integer> PEDESTAL_FUEL_TICKS_PER_ITEM;
    /** ④ 生物死亡来源总开关（id {@code soul_death}） */
    public static final ConfigValue<Boolean> PEDESTAL_SOUL_ENABLED;
    /** ④ 生物死亡：判定球半径（格） */
    public static final ConfigValue<Integer> PEDESTAL_SOUL_RADIUS;
    /** ④ 生物死亡：每点最大生命值多少 EE（0.025 ⇒ 20 血 = 0.5 EE） */
    public static final ConfigValue<Double> PEDESTAL_SOUL_EE_PER_HP;
    /** ⑤ 玩家汲取来源总开关（id {@code demigod_player}） */
    public static final ConfigValue<Boolean> PEDESTAL_PLAYER_ENABLED;
    /** ⑤ 玩家汲取：判定球半径（格） */
    public static final ConfigValue<Integer> PEDESTAL_PLAYER_RADIUS;
    /** ⑤ 玩家汲取：每名玩家每秒多少 EE（多人叠加 ✓） */
    public static final ConfigValue<Double> PEDESTAL_PLAYER_EE_PER_SECOND;

    // ==================== §557 EE 网络（抽取方块 + 万用能量转化器） ====================
    // 默认值同样只写在 EE_NET_DEFAULT_* 常量里一处 ✓（不会与 defineInRange 的默认值漂移 ✗）

    /** §557 抽取方块：抽的速率（EE/tick）—— ⚠ §558 起语义 = "每 tick 从**槽位里那件**抽多少" ✓ 键名不变 ✓ */
    public static final int EE_NET_DEFAULT_EXTRACTOR_PULL = 256;
    /** §557 抽取方块：推的速率（EE/tick） */
    public static final int EE_NET_DEFAULT_EXTRACTOR_PUSH = 256;
    /**
     * §557 抽取方块：<b>内部缓存</b>上限（EE）—— ⚠ §558 起语义是"内部缓存"（原来叫"缓冲"✓ 键名不变 ✓）。
     * <p>4000 = <b>一块古老者水晶方块</b>（{@code ElderCrystalStorage.BLOCK_CAPACITY} ✓ 用户 §558 口径 ✓）
     * —— 恰好"整块装得下"✓ 满了就不再从槽位里抽 ✓（物品里的 EE 原地不动 ✓ 一点不丢 ✗）。
     */
    public static final int EE_NET_DEFAULT_EXTRACTOR_BUFFER = 4000;

    /** §557 转化器：FE 池上限 */
    public static final int EE_NET_DEFAULT_CONVERTER_BUFFER_FE = 32000;
    /** §557 转化器：对外吞吐闸门（FE/t；收与推都不能超过它 ✓） */
    public static final int EE_NET_DEFAULT_CONVERTER_OUTPUT_FE = 64;
    /** §557 转化器：直接 Forge Energy 输入这一条的上限（FE/t） */
    public static final int EE_NET_DEFAULT_CONVERTER_INPUT_FE = 64;
    /**
     * §557 转化器：EE 输入这一条的上限（EE/t）。
     * <p>⚠ §589 起 {@code 1 EE = 1000 FE} ⇒ EE 路额度（512 EE/t）远大于产量 ✓ ⇒ **真正限速的是 FE 闸门** ✓（EE 额度留作保险 ✓）
     * 也就是说"三条输入路各自都够把吞吐闸门打满"，但闸门仍然只有一个 ✓ 不会 3 倍 ✓。
     */
    public static final int EE_NET_DEFAULT_CONVERTER_INPUT_EE = 512;

    // ---- §598 三个"防爆表"旋钮（用户口径：数值要可调 ✓ 但默认必须与 §583/§595 实测口径一致 ✓）----

    /**
     * §598 转化器：通用机械（Mekanism）<b>热量 → FE 的汇率</b>（FE / 热量）。
     * <p>0.4 = 用户口径 {@code 10 J = 4 FE} ✓（也是 {@code EnergyUnits.Fe.FE_PER_J} ✓ 两处口径必须一致 ✓）。
     * <p>设为 <b>0</b> ⇒ 热导线缆送进来的热一点不收 ✓（等于单独关掉"热量"这一路 ✓，不影响它的 J 那一路 ✓）。
     */
    public static final double EE_NET_DEFAULT_CONVERTER_FE_PER_HEAT = 0.4D;

    /**
     * §598 转化器：<b>Create 应力消耗</b>（SU；{@code KineticBlockEntity#calculateStressApplied} 的返回值 ✓）。
     * <p>4.0 = §595 定的"小额固定值" ✓ —— 传动杆接上就得吃应力 ✓ 不然就是白嫖动力 ✗。
     */
    public static final double EE_NET_DEFAULT_CONVERTER_STRESS_IMPACT = 4.0D;

    /**
     * §598 转化器：<b>热量槽容量</b>（Mekanism 的 heat capacity，J/K ✓）。
     * <p>⚠ 我们<b>绝不囤热</b> ✗（{@code handleHeat} 只吃"这一 tick 立刻能换成 FE"的那部分 ✓）
     * ⇒ 这个值只影响"通用机械允许多快地往我们这里导热"✓，**不**是热量仓库 ✓。
     */
    public static final double EE_NET_DEFAULT_CONVERTER_HEAT_SINK_CAPACITY = 64.0D;

    /** §557 抽取方块：抽的速率（EE/tick） */
    public static final ConfigValue<Integer> EE_EXTRACTOR_PULL_PER_TICK;
    /** §557 抽取方块：推的速率（EE/tick） */
    public static final ConfigValue<Integer> EE_EXTRACTOR_PUSH_PER_TICK;
    /** §557 抽取方块：缓冲上限（EE） */
    public static final ConfigValue<Integer> EE_EXTRACTOR_BUFFER;
    /** §557 转化器：FE 池上限 */
    public static final ConfigValue<Integer> CONVERTER_BUFFER_FE;
    /** §557 转化器：对外吞吐闸门（FE/t） */
    public static final ConfigValue<Integer> CONVERTER_OUTPUT_FE_PER_TICK;
    /** §557 转化器：直接 Forge Energy 输入上限（FE/t） */
    public static final ConfigValue<Integer> CONVERTER_INPUT_FE_PER_TICK;
    /** §557 转化器：EE 输入上限（EE/t） */
    public static final ConfigValue<Integer> CONVERTER_INPUT_EE_PER_TICK;
    /** §598 转化器：热量汇率（FE / 热量；0 = 不收热 ✓） */
    public static final ConfigValue<Double> CONVERTER_FE_PER_HEAT;
    /** §598 转化器：Create 应力消耗（SU） */
    public static final ConfigValue<Double> CONVERTER_STRESS_IMPACT;
    /** §598 转化器：热量槽容量（Mekanism heat capacity，J/K） */
    public static final ConfigValue<Double> CONVERTER_HEAT_SINK_CAPACITY;

    // ==================== 双向认知阻碍面具 ====================
    /**
     * 佩戴认知阻碍面具的玩家是否从小地图雷达上隐藏（默认开）。
     *
     * <p>实现是 {@code XaeroRadarMixin} 在雷达遍历实体那一处定点过滤 ——
     * <b>玩家身上不带任何状态</b>，所以人（原版 / YSM 等接管渲染的模组）照常可见 ✓。
     * 关掉则雷达会照常显示你（无名字与索敌不到仍由各自的事件层拦住 ✓）。
     */
    public static final ConfigValue<Boolean> COGNITIVE_MASK_HIDE_FROM_RADAR;

    /**
     * 佩戴面具时，**被你打过的怪可以还手**（默认开 ✓）。
     *
     * <p>用户要求："佩戴时怪物不会主动攻击，但受击会还手" ✓。
     * 判定读 MC 自己的 {@code LivingEntity#getLastHurtByMob()} ✓（MC 5 秒后自动清空 ⇒ 天然是"近期" ✓）。
     * 关掉则恢复旧行为：戴着面具谁都锁不住你（包括你打过的怪 ✗）。
     */
    public static final ConfigValue<Boolean> COGNITIVE_MASK_ALLOW_RETALIATION;

    // ==================== 无为转变 伪装渲染 ====================
    /** 无为转变·伪装渲染替换（客户端）：把变形玩家渲染成目标生物。与 YSM 等接管玩家渲染的模组冲突时可关闭 */
    public static final ConfigValue<Boolean> WUWEI_DISGUISE_RENDER;
    /** 无为转变：变形后移动速度倍率（默认 0.5 = 减半，1.0 = 直接用生物速度） */
    public static final ConfigValue<Double> WUWEI_SPEED_SCALE;
    /** 无为转变：伪装期间是否把玩家的盔甲与手持物品画在生物形态上（默认开；人形形态才有效果） */
    public static final ConfigValue<Boolean> WUWEI_EQUIPMENT_RENDER;

    // ==================== 混沌之流（源钻合金 · 铁魔法联动） ====================
    /** 混沌之流是否启用（默认开；关掉后每次攻击都按原来的物理伤害原样结算） */
    public static final ConfigValue<Boolean> CHAOS_FLOW_ENABLED;

    // ==================== 不可名状 · 观感 ====================
    /** 「不可名状」的整屏信号干扰/花屏覆盖层（客户端，默认开） */
    public static final ConfigValue<Boolean> UNNAMEABLE_GLITCH;
    /** 花屏强度倍率（默认 1.0；调大更糊更闪，0 = 只剩后处理与视角晃动） */
    public static final ConfigValue<Double> UNNAMEABLE_GLITCH_INTENSITY;
    /** 「不可名状」的 FOV 倍率（默认 1.4 = 视野拉高 40%） */
    public static final ConfigValue<Double> UNNAMEABLE_FOV_MULTIPLIER;
    /** 「不可名状」的后处理（锐化 + 高对比度 + 高饱和度 + 反转颜色，客户端，默认开） */
    public static final ConfigValue<Boolean> UNNAMEABLE_POST_EFFECT;
    /** 后处理是否"闪断"（默认 true：亮 2~5 秒 / 断 0.5~1.5 秒随机交替，避免反转色一直糊着） */
    public static final ConfigValue<Boolean> UNNAMEABLE_POST_EFFECT_PULSE;
    /** 「不可名状」的屏幕低语文字（客户端，默认开；文字走可翻译键 whisper.tinkersnewlife.*） */
    public static final ConfigValue<Boolean> UNNAMEABLE_WHISPERS;
    /** 「不可名状」的低语音频（客户端，默认开；获得效果时循环播放 tinkersnewlife:effect.whispers，效果结束即停） */
    public static final ConfigValue<Boolean> UNNAMEABLE_WHISPER_SOUND;
    /** 低语音频音量（默认 1.0；音源是"环境音"滑条，所以这里再给一个倍率） */
    public static final ConfigValue<Double> UNNAMEABLE_WHISPER_SOUND_VOLUME;

    // ==================== 噤默手套 ====================
    /**
     * 噤默手套是否"全部静音"。
     *
     * <p>默认 false = <b>只静音生物（敌对/中立）的叫声</b>：环境音、方块音、音乐、其它玩家、以及自己的一切照常。
     * true = 旧行为（只放行一份 id 白名单，其余全静音）—— 整合包里因为别的 mod 会用不同 id 播放
     * 脚步等声音，白名单必然漏，实测表现就是"整个游戏没声音"。
     */
    public static final ConfigValue<Boolean> SILENT_GLOVE_MUTE_ALL;

    // ==================== 飞剑流光拖尾 ====================
    /** 飞剑流光拖尾（客户端）：动态条带 + 自写流光着色器 */
    public static final ConfigValue<Boolean> FLYING_SWORD_TRAIL;

    // ==================== 咒术 HUD（咒力进度条） ====================
    // 说明：HUD 的位置/宽度由游戏内拖动界面写入独立文件 config/mofengbaizhi/curse_hud.json
    //       （见 client/hud/CurseHudConfig），不放这里以免与主配置的写回时机打架。

    // ==================== 领域 ====================
    /** 领域被破坏时，每个结界方块掉落结界碎片的概率分母（默认 1000 = 1/1000，比原来的 1/100 稀十倍） */
    public static final ConfigValue<Integer> DOMAIN_FRAGMENT_DROP_DENOMINATOR;

    // ==================== 构筑术式（拟造） ====================
    /** 拟造费用倍率（默认 10.0 = 原价的 10 倍） */
    public static final ConfigValue<Double> CONSTRUCT_COST_MULTIPLIER;
    /** 拟造黑名单：禁止出现在构筑列表里的物品（支持 mod / 物品 / 标签 / 配方类型） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_BLACKLIST;
    // ---- 拟造物防自动化（拟造物只能存在于主人身上）----
    /** 拟造物掉到地上立即消散（静默：只给烟雾/音效，不给文字提示） */
    /**
     * 拟造物是否使用「拟造蓝本」代理物品（推荐开）。
     * 开启后所有拟造物都是同一个物品 id（目标物品记在 NBT 里），因此<b>任何配方/机器都认不出它</b>；
     * 关闭则退回"目标物品的真副本 + 离手即散/容器清除"的旧行为。
     */
    public static final ConfigValue<Boolean> CONSTRUCT_BLUEPRINT_ENABLED;
    /**
     * 匠魂工具（IModifiable）是否保持"真副本"而不走蓝本（默认 true）：
     * 蓝本转发不了 {@code instanceof IModifiable}，匠魂工具会彻底不能用；
     * 这些物品由"离手即散 / 容器立即清除 / 槽位拒绝"三层防线兜底。
     */
    public static final ConfigValue<Boolean> CONSTRUCT_BLUEPRINT_TCON_TOOLS_LEGACY;
    /** 命中"风险"（依赖自身类型判定的接口/模组）时是否退回真副本（默认 true；设 false 则一律用蓝本） */
    public static final ConfigValue<Boolean> CONSTRUCT_BLUEPRINT_RISKY_LEGACY;
    /** 强制退回真副本的清单：物品id / {@code @模组} / {@code #标签}（支持 * 通配） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_BLUEPRINT_EXEMPT;
    /** 追加的"风险接口"类名（实现即退回真副本，软依赖，模组不在自动忽略） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_BLUEPRINT_RISKY_INTERFACES;
    /** 风险模组清单：这些模组的物品默认退回真副本（默认值按整合包静态扫描结果预置） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_BLUEPRINT_RISKY_MODS;
    /** 服务器启动时自动生成一次兼容报告（config/mofengbaizhi/construct/blueprint_report.txt） */
    public static final ConfigValue<Boolean> CONSTRUCT_BLUEPRINT_REPORT_ON_START;
    /** 物品自带 Forge 能力（能量/流体/存储/饰品等）时是否退回真副本（默认 true） */
    public static final ConfigValue<Boolean> CONSTRUCT_BLUEPRINT_CAPABILITY_LEGACY;
    public static final ConfigValue<Boolean> CONSTRUCT_VANISH_ON_DROP;
    /** 容器/机器里的拟造物立即清除（不等到期）——防止被熔炼等自动化配方加工成真材料 */
    public static final ConfigValue<Boolean> CONSTRUCT_CONTAINER_INSTANT_PURGE;
    /** 禁止把拟造物手动放进"非玩家背包"的容器槽位（Slot#mayPlace 拦截） */
    public static final ConfigValue<Boolean> CONSTRUCT_DENY_CONTAINER_SLOTS;
    /** 禁止诡厄巫法（Goety）仪式祭坛/基座接收拟造物 */
    public static final ConfigValue<Boolean> CONSTRUCT_BLOCK_GOETY_RITUAL;
    /** 每 tick 全局扫描的已加载区块数（0 = 只扫玩家附近的旧逻辑；越大清得越快、开销越高） */
    public static final ConfigValue<Integer> CONSTRUCT_GLOBAL_SWEEP_CHUNKS;
    /** "配方原料价值"项的权重（0 = 关闭该项） */
    public static final ConfigValue<Double> CONSTRUCT_INGREDIENT_WEIGHT;
    /** 是否启用<b>内置默认黑名单</b>（矿石/粗矿/矿锭/矿粒/矿粉/宝石/碎片） */
    public static final ConfigValue<Boolean> CONSTRUCT_USE_DEFAULT_BLACKLIST;
    /** 高功能魔法类物品（法术卷轴/聚晶/法术书/符文…）的额外价值分（0 = 关闭该判定） */
    public static final ConfigValue<Double> CONSTRUCT_MAGIC_BONUS;
    /** 追加的魔法类关键词（按物品类名/接口名匹配，小写子串） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_MAGIC_EXTRA_KEYWORDS;
    /** 追加的魔法类物品标签（如 #curios:spellbook） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_MAGIC_EXTRA_TAGS;
    /** 品质词加成表（"词=分数"，如 legendary=60；覆盖内置同名项） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_TIER_BONUS;
    /** 手动指定物品价值（"物品id=分数"、通配符 "*_ink=200"、标签 "#forge:gems=20"；直接替代计算结果） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_VALUE_OVERRIDES;
    /** 标签价值表（"#forge:gems=25"；可叠加，用于给"没有配方"的采集物定价） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_TAG_VALUES;
    /** 不可堆叠物品的加成分（独特物品，如鞘翅/图腾） */
    public static final ConfigValue<Double> CONSTRUCT_UNIQUE_ITEM_BONUS;
    /** 方块硬度折算成的分数上限（硬度/2，封顶此值） */
    public static final ConfigValue<Double> CONSTRUCT_HARDNESS_CAP;
    /** 标签价值合计上限 */
    public static final ConfigValue<Double> CONSTRUCT_TAG_VALUE_CAP;
    // ---- 掉落来源扫描（缺省证据，不主导价格）----
    /** 是否启用掉落来源扫描（扫描命令本身始终可用） */
    public static final ConfigValue<Boolean> CONSTRUCT_LOOT_SOURCE_ENABLED;
    /** 是否自动应用扫描结果（默认 false：只生成建议文件，人工合并） */
    public static final ConfigValue<Boolean> CONSTRUCT_LOOT_SOURCE_AUTO_APPLY;
    /** 掉落项分数上限 */
    public static final ConfigValue<Double> CONSTRUCT_LOOT_SOURCE_CAP;
    /** 掉落自动加价的黑名单（物品 id / modid） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_LOOT_SOURCE_BLACKLIST;
    /** 实体难度覆盖（"minecraft:wither=120" / "minecraft:wither_skeleton=55"） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_ENTITY_VALUE_OVERRIDES;
    /** 结构难度覆盖（"minecraft:chests/ancient_city=50"） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_STRUCTURE_VALUE_OVERRIDES;

    /** 墨默刷新**维度黑名单**（写维度 id ✓ 例如 allthemodium:mining ✓ 空 = 不排除任何维度 ✓） */
    public static final ConfigValue<List<? extends String>> MOMO_SPAWN_DIMENSION_BLACKLIST;
    // ---- 流体价值（桶代理 + 流标签 + 软依赖适配器）----
    /** 是否启用流体价值层（关闭则配方里的流体投入不计价） */
    public static final ConfigValue<Boolean> CONSTRUCT_FLUID_VALUE_ENABLED;
    /** 流体标签表（"#forge:inks=15" 表示一桶 15 分） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_FLUID_TAG_VALUES;
    /** 单桶流体的价值上限（每 mB = 该值/1000） */
    public static final ConfigValue<Double> CONSTRUCT_FLUID_VALUE_CAP;

    // ==================== 术式/领域缩放系数 ====================
    /** 各术式 modifier id → [damage, cost] 缩放 */
    public static final Map<String, ConfigValue<Double>[]> TECHNIQUE_SCALES = new HashMap<>();
    /** 各领域 modifier id → [radius, damage, cost] 缩放 */
    public static final Map<String, ConfigValue<Double>[]> DOMAIN_SCALES = new HashMap<>();

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        // 古神事件
        b.push("elder_events").comment(
                "Each toggle controls whether the corresponding \"god item\" event handler runs.\n",
                "Turning one off stops that god's item acquisition/use event from triggering.");
        YOG_SOTHOTH_KEY = b.comment("Yog-Sothoth Gate Key events").define("enable_yog_sothoth_key", true);
        YELLOW_KING = b.comment("Yellow King Remnant events").define("enable_yellow_king", true);
        RLYEH_CALL = b.comment("R'lyeh Call events").define("enable_rlyeh_call", true);
        NYARLATHOTEP_DESIRE = b.comment("Nyarlathotep's Desire events").define("enable_nyarlathotep_desire", true);
        b.pop();

        // 咒力核心
        b.push("curse_core").comment("Curse Core: allow crafting (ritual) and using (equipping/techniques). Default on.");
        CURSE_CORE_ENABLED = b.define("allow_curse_core_craft_and_use", true);
        b.pop();

        // 古老者水晶 · 魔力台座（§545 起：五条来源并行叠加，其中默认开四条 · 亮度默认降到 0.5）
        b.push("elder_crystal").comment(
                "Elder Crystal - the Mana Pedestal, the (only) way to charge a crystal.",
                "",
                "FIVE AMBIENT SOURCES, ALL ADDING UP IN PARALLEL. There is NO global cap:",
                "whatever the sources give per second is what you get, and every enabled source is",
                "simply summed. Each source has its own on/off switch and its own numbers.",
                "",
                "  light_level    (default on)  THE DARKER, THE FASTER:",
                "      rate = pedestal_charge_max_per_second * (pedestal_light_cap - light) / pedestal_light_cap",
                "      light = level.getMaxLocalRawBrightness(the block ABOVE the pedestal), 0..15 = combined",
                "              block light + sky light, exactly the value vanilla uses for mob spawning.",
                "              There is deliberately NO 'must be night' and NO 'must see the sky' check.",
                "      light 0 -> full speed; light >= pedestal_light_cap -> 0",
                "  plant          (default on)  sphere r=5; +1 wither point per plant per second, 1 point =",
                "      plant_ee_per_point EE (0.5) => 0.5 EE/s per plant. Cap: grass 5, flower 20, sapling 30",
                "      points, and a plant that reaches its cap VANISHES. Crops count as flowers, wither",
                "      roses are excluded. Wither points are in-memory only (lost on restart).",
                "  tcon_fuel      (default on)  sphere r=5; every Tinker fluid container is simulated once",
                "      per second using Tinker's OWN fuel registry (MeltingFuelLookup): the burn rate is",
                "      temperature/4 mB per tick, and the fuel burned yields",
                "      (burned/amount) * (duration/fuel_ticks_per_item) item-equivalents at",
                "      fuel_ee_per_item EE each. Lava: 25 EE/s per tank. Solid fuel (coal in a heater)",
                "      is NOT supported.",
                "  soul_death     (default on)  sphere r=5; ANY death (players included) banks",
                "      soul_ee_per_hp * maxHealth EE (0.025 => 20 HP = 0.5 EE) and the pedestal pays the",
                "      bank out on its next one-second settle.",
                "  demigod_player (default on)  sphere r=5; every player inside gives player_ee_per_second",
                "      EE (no cost at all, players stack).",
                "",
                "pedestal_source = comma-separated list of source ids to enable, or \"*\" (default) for ALL of",
                "      them. Unknown ids are ignored with a single warning. OLD CONFIGS: a single id such as",
                "      \"light_level\" now means 'enable only that source', which is exactly what it used to do.",
                "      Available ids: light_level, plant, tcon_fuel, soul_death, demigod_player.",
                "pedestal_charge_max_per_second = light-level source: EE per second at light 0",
                "      (default 0.5 = 30 EE per minute, so a full 1000 EE crystal takes about 33 minutes).",
                "pedestal_light_cap = light level at which the light-level source stops (default 15 = the",
                "      vanilla maximum; raise it above 15 for a slow trickle in bright light - e.g. 16 leaves",
                "      ~0.031 EE/s at light 15).",
                "light_enabled / plant_enabled / fuel_enabled / soul_enabled / player_enabled = per-source",
                "      master switches (the source must ALSO be listed in pedestal_source).",
                "plant_radius / fuel_radius / soul_radius / player_radius = sphere radius in blocks",
                "      (all default 5).",
                "plant_ee_per_point = EE per wither point (0.5).",
                "fuel_ee_per_item = EE per item-equivalent (0.5); fuel_ticks_per_item = how many ticks one",
                "      item-equivalent is assumed to take (10; Tinker's melting recipes use 9-10 ticks at the",
                "      low end, so this is the 'cheap recipe' baseline - raise it to slow fuel charging down).",
                "soul_ee_per_hp = EE per point of max health (0.025 => maxHealth/40).",
                "player_ee_per_second = EE per player per second (0.5).",
                "pedestal_particles / pedestal_sound = cold particles / a soft amethyst chime while it",
                "      actually charges (particle density scales with the total rate).",
                "",
                "Caps are per target: a crystal item holds 1000 EE, a crystal block 4000 EE. When everything",
                "in reach is full the pedestal simply stops: no source is queried, so no fuel is burned and no",
                "plant is withered while there is nothing to fill.");
        PEDESTAL_CHARGE_MAX_PER_SECOND = b.defineInRange("pedestal_charge_max_per_second",
                PEDESTAL_DEFAULT_CHARGE_MAX_PER_SECOND, 0.0D, 10000.0D);
        PEDESTAL_LIGHT_CAP = b.defineInRange("pedestal_light_cap", PEDESTAL_DEFAULT_LIGHT_CAP, 1, 30);
        PEDESTAL_SOURCE = b.define("pedestal_source", PEDESTAL_DEFAULT_SOURCE);
        PEDESTAL_PARTICLES = b.define("pedestal_particles", true);
        PEDESTAL_SOUND = b.define("pedestal_sound", true);

        // ① 亮度
        PEDESTAL_LIGHT_ENABLED = b.define("light_enabled", true);

        // ② 植物（凋灵度）
        PEDESTAL_PLANT_ENABLED = b.define("plant_enabled", true);
        PEDESTAL_PLANT_RADIUS = b.defineInRange("plant_radius", PEDESTAL_DEFAULT_PLANT_RADIUS, 0, 32);
        PEDESTAL_PLANT_EE_PER_POINT = b.defineInRange("plant_ee_per_point",
                PEDESTAL_DEFAULT_PLANT_EE_PER_POINT, 0.0D, 1000.0D);

        // ③ 匠魂燃料
        PEDESTAL_FUEL_ENABLED = b.define("fuel_enabled", true);
        PEDESTAL_FUEL_RADIUS = b.defineInRange("fuel_radius", PEDESTAL_DEFAULT_FUEL_RADIUS, 0, 32);
        PEDESTAL_FUEL_EE_PER_ITEM = b.defineInRange("fuel_ee_per_item",
                PEDESTAL_DEFAULT_FUEL_EE_PER_ITEM, 0.0D, 1000.0D);
        PEDESTAL_FUEL_TICKS_PER_ITEM = b.defineInRange("fuel_ticks_per_item",
                PEDESTAL_DEFAULT_FUEL_TICKS_PER_ITEM, 1, 10000);

        // ④ 生物死亡（灵魂）
        PEDESTAL_SOUL_ENABLED = b.define("soul_enabled", true);
        PEDESTAL_SOUL_RADIUS = b.defineInRange("soul_radius", PEDESTAL_DEFAULT_SOUL_RADIUS, 0, 32);
        PEDESTAL_SOUL_EE_PER_HP = b.defineInRange("soul_ee_per_hp",
                PEDESTAL_DEFAULT_SOUL_EE_PER_HP, 0.0D, 1000.0D);

        // ⑤ 玩家汲取（半神之力）
        PEDESTAL_PLAYER_ENABLED = b.define("player_enabled", true);
        PEDESTAL_PLAYER_RADIUS = b.defineInRange("player_radius", PEDESTAL_DEFAULT_PLAYER_RADIUS, 0, 32);
        PEDESTAL_PLAYER_EE_PER_SECOND = b.defineInRange("player_ee_per_second",
                PEDESTAL_DEFAULT_PLAYER_EE_PER_SECOND, 0.0D, 1000.0D);
        b.pop();

        // §557 EE 网络：EE 抽取方块 + 万用能量转化器
        b.push("ee_network").comment(
                "EE network: the EE Extractor and the Universal Energy Converter.",
                "",
                "ALL EXTERNAL ENERGY RATES ARE THE USER'S, HARD-CODED AND FIXED:",
                "    1 EE = 1000 FE         (so 1 FE = 0.001 EE)   // §589",
                "    1 EU = 4 FE            (IC2)",
                "    1 AE = 2 FE            (AE2)",
                "    10 J = 4 FE            (Mekanism; 1 J = 0.4 FE)",
                "    FE/t = (45 * RPM) / 64 (Create; 1 RPM = 0.703125 FE/t)",
                "    RF = Tesla = uI = FF = FE  -- they are all aliases of FE at 1:1, so they need no",
                "    adapter at all: anything exposing ForgeCapabilities.ENERGY goes down the FE path.",
                "",
                "EE EXTRACTOR (tinkersnewlife:ee_extractor): right-click it to open a GUI with ONE slot.",
                "  Put a CHARGED Elder Crystal or Elder Crystal Block in that slot: every tick the block pulls",
                "  EE out of that item into its own cache, and PUSHES the cache to the EE containers next to it.",
                "  Direction order is fixed: up, down, north, south, west, east.",
                "  extractor_pull_ee_per_tick  (default 256) EE pulled from THE ITEM IN THE SLOT each tick.",
                "  extractor_push_ee_per_tick  (default 256) EE pushed to neighbours each tick.",
                "  extractor_buffer_ee         (default 4000 = one crystal block) internal cache. When the cache",
                "      is full the extractor simply stops pulling, so the EE in the item is never destroyed.",
                "",
                "UNIVERSAL ENERGY CONVERTER (tinkersnewlife:energy_converter): ONE-WAY, everything -> FE.",
                "  Input A: EE pushed in by neighbours (the same EeStorage interface) - converted at 1 EE = 1000 FE.",
                "  Input B: Forge Energy pulled from neighbours (ForgeCapabilities.ENERGY) - 1:1.",
                "  Input C: the three optional mods, each through its OWN real mechanism (memo S598):",
                "      Mekanism  -> STRICT_ENERGY capability (its cables push J in) + HEAT_HANDLER on the",
                "                  RIGHT face (its heat conduits push heat in; 1 heat = converter_fe_per_heat FE).",
                "      AE2       -> the grid node takes AE out of the network (LEFT face; rate follows AE2's own",
                "                  PowerUnits.FE.conversionRatio, official default 1 AE = 2 FE).",
                "      Create    -> this block IS a kinetic block, so a shaft on the FRONT face drives it",
                "                  (FE/t = 45 * RPM / 64) and it eats converter_stress_impact SU of stress.",
                "      There are no per-family on/off switches any more: a family works exactly when that mod",
                "      is installed (memo S598 explains why those four switches were deleted).",
                "  Output: FE pushed to neighbours via IEnergyStorage#receiveEnergy (never extracted back).",
                "  converter_output_fe_per_tick (default 64) THE HARD THROUGHPUT GATE: no matter how many input",
                "      paths are connected, this block never moves more than 64 FE per tick in total.",
                "  converter_input_fe_per_tick (default 64) cap on the direct Forge Energy path.",
                "  converter_input_ee_per_tick (default 512) cap on the EE path; since 1 EE = 1000 FE the FE gates are the real limit.",
                "  converter_buffer_fe          (default 32000) FE pool inside the converter.",
                "  converter_fe_per_heat        (default 0.4) FE per unit of Mekanism heat; 0 = take no heat at all.",
                "  converter_stress_impact      (default 4) SU of Create stress this block applies; 0 = free power.",
                "  converter_heat_sink_capacity (default 64) Mekanism heat capacity (J/K) of our heat sink.",
                "      NOTE: we never STORE heat - handleHeat only accepts what it can turn into FE this tick,");

        EE_EXTRACTOR_PULL_PER_TICK = b.defineInRange("extractor_pull_ee_per_tick",
                EE_NET_DEFAULT_EXTRACTOR_PULL, 0, 1_000_000);
        EE_EXTRACTOR_PUSH_PER_TICK = b.defineInRange("extractor_push_ee_per_tick",
                EE_NET_DEFAULT_EXTRACTOR_PUSH, 0, 1_000_000);
        EE_EXTRACTOR_BUFFER = b.defineInRange("extractor_buffer_ee",
                EE_NET_DEFAULT_EXTRACTOR_BUFFER, 1, 100_000_000);
        CONVERTER_BUFFER_FE = b.defineInRange("converter_buffer_fe",
                EE_NET_DEFAULT_CONVERTER_BUFFER_FE, 1, 2_000_000_000);
        CONVERTER_OUTPUT_FE_PER_TICK = b.defineInRange("converter_output_fe_per_tick",
                EE_NET_DEFAULT_CONVERTER_OUTPUT_FE, 0, 1_000_000);
        CONVERTER_INPUT_FE_PER_TICK = b.defineInRange("converter_input_fe_per_tick",
                EE_NET_DEFAULT_CONVERTER_INPUT_FE, 0, 1_000_000);
        CONVERTER_INPUT_EE_PER_TICK = b.defineInRange("converter_input_ee_per_tick",
                EE_NET_DEFAULT_CONVERTER_INPUT_EE, 0, 100_000_000);
        CONVERTER_FE_PER_HEAT = b.defineInRange("converter_fe_per_heat",
                EE_NET_DEFAULT_CONVERTER_FE_PER_HEAT, 0.0D, 1000.0D);
        CONVERTER_STRESS_IMPACT = b.defineInRange("converter_stress_impact",
                EE_NET_DEFAULT_CONVERTER_STRESS_IMPACT, 0.0D, 1024.0D);
        CONVERTER_HEAT_SINK_CAPACITY = b.defineInRange("converter_heat_sink_capacity",
                EE_NET_DEFAULT_CONVERTER_HEAT_SINK_CAPACITY, 1.0D, 1_000_000.0D);
        // §598：§557 留下的 `mekanism_energy_enabled` / `create_rotation_enabled` / `ic2_eu_enabled` /
        //       `ae2_energy_enabled` 四个键**已删** ✗ —— 它们从 §577 起就没人读了 ✓（唯一读者是那套已删的
        //       反射适配器 ✓），而 §592 那次"转速整条路被一个不生效的键静默关掉"就是这类键惹的祸 ✗。
        //       现在"哪家能接"只由**模组在不在场**决定 ✓（`IntegrationLoader.isLoaded` ✓）。
        b.pop();

        // 双向认知阻碍面具（头饰）
        b.push("cognitive_mask").comment(
                "Two-way Cognitive Obstruction Mask (head curio).",
                "",
                "Wearing it always: other players cannot see your name tag, and mobs cannot lock onto you",
                "(both newly attempted locks and locks that already existed before you put it on).",
                "EXCEPT mobs you hit yourself: they are allowed to fight back (see allow_retaliation).",
                "It also makes you treat EVERY living entity except players as UNDEAD: cursed tool",
                "undead bonus, reverse cursed technique (damage instead of heal on undead), Jacob's Ladder",
                "and the Cursed Spirit Technique capture check all take the undead branch.",
                "",
                "allow_retaliation = a mob you hurt may target you back (default true).",
                "Uses vanilla's own getLastHurtByMob(), which vanilla clears after 100 ticks (5 s) and",
                "refreshes on every hit, so it is exactly a 'recently attacked' window.",
                "Set false to go back to the old behaviour: nothing can ever lock onto you.",
                "",
                "hide_from_radar = also hide the wearer from the Xaero's minimap radar (default true).",
                "Implemented by a targeted mixin (XaeroRadarMixin) that filters the radar's entity",
                "iteration - NO state is put on the player, so the body stays visible in vanilla AND",
                "in mods that take over player rendering (YSM / Yes Steve Model) - which is exactly why",
                "the earlier invisibility-flag approach was dropped.",
                "Set false to stay on radars: the name tag and mob targeting are still blocked.");
        COGNITIVE_MASK_HIDE_FROM_RADAR = b.define("hide_from_radar", true);
        COGNITIVE_MASK_ALLOW_RETALIATION = b.define("allow_retaliation", true);
        b.pop();

        // 无为转变·伪装渲染替换（客户端）
        b.push("wuwei_disguise").comment(
                "Client side: render a transformed (Wu Wei) player as the target creature instead of the player model.",
                "Set enable_disguise_render=false if another mod that takes over player rendering",
                "(e.g. YSM / Yes Steve Model) conflicts with the disguise.");
        WUWEI_DISGUISE_RENDER = b.define("enable_disguise_render", true);
        WUWEI_SPEED_SCALE = b.defineInRange("speed_scale", 0.5D, 0.05D, 4.0D);
        WUWEI_EQUIPMENT_RENDER = b.define("render_equipment", true);
        b.pop();

        // 混沌之流：每次攻击随机二选一（物理 / 某一学派的法术），只结算一次（源钻合金 origin_alloy）
        b.push("chaos_flow").comment(
                "Chaos Flow (the Origin Alloy material trait): every attack randomly rolls ONE of two outcomes",
                "and then lands a SINGLE damage instance (the total amount is unchanged):",
                "",
                "  - physical: the original hit is left completely untouched (same damage source, same amount);",
                "  - spell:    the original hit is cancelled and re-sent ONCE as a random spell school's damage",
                "              type (taken from Iron's Spells' school registry, so addon schools count too).",
                "",
                "The hit is NOT split up any more - so there is no per-segment re-amplification",
                "(black flash ^2.5, Child of the Stars x2^level, wizard set, staff spell power, ...)",
                "and no 'segment count x amplification' inflation.",
                "",
                "enabled=false disables the roll entirely (every hit lands normally as physical damage).");
        CHAOS_FLOW_ENABLED = b.define("enabled", true);
        b.pop();

        // 不可名状效果：客户端观感（撑开视野 + 后处理 + 信号干扰花屏）
        b.push("unnameable").comment(
                "The Unnameable effect: client-side visuals.",
                "",
                "fov_multiplier = field of view multiplier while affected (1.4 = +40%).",
                "",
                "post_effect = post-processing shader while affected:",
                "sharpen (unsharp mask) + higher contrast + higher saturation + INVERTED COLORS.",
                "Set false if another shader mod conflicts, or if you dislike inverted colours.",
                "",
                "post_effect_pulse = keep the colour effect INTERMITTENT instead of always-on:",
                "roughly 2-5 seconds on, then 0.5-1.5 seconds off, randomly (default true).",
                "Set false to hold the effect continuously for the whole duration.",
                "(Either way it is ALWAYS removed the moment the effect ends / you die / leave the world.)",
                "",
                "glitch = whole-screen signal interference overlay",
                "(tearing bands + static + a rolling interference bar + occasional flashes);",
                "glitch_intensity scales it: 1.0 = default, 2.0 = much messier, 0 = off.",
                "",
                "whispers = whispered WORDS flickering across the screen (translatable: whisper.tinkersnewlife.*);",
                "whisper_sound = the whispered VOICE AUDIO, looped while the effect lasts and stopped the moment",
                "it ends (sound event tinkersnewlife:effect.whispers, played on the AMBIENT sound channel);",
                "whisper_sound_volume = extra volume multiplier for that audio (0 = silent, 1.0 = default).");
        UNNAMEABLE_FOV_MULTIPLIER = b.defineInRange("fov_multiplier", 1.4D, 1.0D, 2.5D);
        UNNAMEABLE_POST_EFFECT = b.define("post_effect", true);
        UNNAMEABLE_POST_EFFECT_PULSE = b.define("post_effect_pulse", true);
        UNNAMEABLE_WHISPERS = b.define("whispers", true);
        SILENT_GLOVE_MUTE_ALL = b.define("silent_glove_mute_all", false);
        UNNAMEABLE_WHISPER_SOUND = b.define("whisper_sound", true);
        UNNAMEABLE_WHISPER_SOUND_VOLUME = b.defineInRange("whisper_sound_volume", 1.0D, 0.0D, 2.0D);
        UNNAMEABLE_GLITCH = b.define("glitch", true);
        UNNAMEABLE_GLITCH_INTENSITY = b.defineInRange("glitch_intensity", 1.0D, 0.0D, 3.0D);
        b.pop();

        // 构筑术式（拟造）：费用倍率 + 黑名单
        b.push("construct").comment(
                "Construct technique (Wu Wei 'construct' / fabricate items from recipes).",
                "",
                "cost_multiplier = final curse cost multiplier. 1.0 = original formula, 10.0 = 10x cost (default).",
                "",
                "use_default_blacklist = built-in list: all ores / raw materials / ingots / nuggets / dusts / gems / shards,",
                "                        plus all fluid containers (buckets / potions / fluid bottles).",
                "                          (#forge:ores, #forge:raw_materials, #forge:ingots, #forge:nuggets,",
                "                           #forge:dusts, #forge:gems, #c:* equivalents, and *:*_shard / *:*_dust / *:*_nugget globs;",
                "                           buckets: #forge:buckets / *:*_bucket / *:bucket_*;",
                "                           potions: minecraft:potion|splash_potion|lingering_potion / *:*_potion;",
                "                           fluid bottles: *:*_bottle / *:bottle_*).",
                "",
                "magic_item_bonus = extra value score for high-function magic items (spell scrolls, focuses, spellbooks,",
                "                   runes, wands...). They have no attack/armor/durability so the normal formula prices them",
                "                   like dirt. 0 disables the check. Detected by tags (#curios:scroll, #curios:spellbook,",
                "                   #curios:spellstone, #irons_spellbooks:school_focus, #irons_spellbooks:inscribed_rune)",
                "                   or by class/interface name keywords (scroll/focus/spellbook/rune/charm/wand/staff/...).",
                "magic_extra_keywords / magic_extra_tags = your own additions.",
                "",
                "tier_bonus = value score by quality keyword found in the item id. Built-in: uncommon=6 rare=14",
                "             epic=30 legendary=60 mythic=90 divine=90 supreme=120 ultimate=120.",
                "             This is how tiered inks/essences get priced (they are plain common items otherwise),",
                "             and the score propagates into their products through the ingredient term.",
                "value_overrides = hard-set an item value, replaces the computed score. Formats:",
                "             irons_spellbooks:legendary_ink=200   (exact item)",
                "             *_ink=120                            (glob)",
                "             #forge:gems=25                       (item tag)",
                "",
                "--- value of items that have NO recipe (mining / gathering / drops) ---",
                "tag_values  = additive score per item tag. Built-in: #forge:ores=12 #forge:raw_materials=8",
                "              #forge:ingots=10 #forge:gems=25 #forge:dusts=6 #forge:nuggets=2",
                "              #forge:storage_blocks=20 #minecraft:coals=5  (sum is capped by tag_value_cap)",
                "unique_item_bonus = bonus for non-stackable items (stack size 1: elytra, totem, trident...)",
                "hardness_cap      = cap for the block-hardness proxy (obsidian 50 -> 25, ancient debris 30 -> 15)",
                "tag_value_cap     = cap of the summed tag values for one item",
                "",
                "--- loot / structure source scan (evidence only, does NOT lead pricing) ---",
                "Run:  /tinkersnewlife construct lootsuggest     (scans entity drops + chest loot)",
                "It writes config/mofengbaizhi/construct/loot_index.json  (cache)",
                "      and config/mofengbaizhi/construct/loot_suggestions.toml (paste into value_overrides).",
                "loot_source_enabled = allow the scan / the auto-apply lookup",
                "loot_source_auto_apply = false (default) keep it suggestions-only. When true it only affects",
                "                        items with NO recipe, NOT a block, stackable, not covered by",
                "                        value_overrides/tag_values and not in loot_source_blacklist.",
                "loot_source_cap = upper bound of the loot term",
                "loot_source_blacklist = farmable junk (rotten flesh, bone, string...) excluded from auto-apply",
                "entity_value_overrides / structure_value_overrides = difficulty base overrides",
                "",
                "--- fluid value layer (bucket proxy + fluid tags + soft-dependency adapters) ---",
                "Fluids carry a lot of value in some mod chains (e.g. Iron's Spells inks are brewed as fluids).",
                "fluid_value_enabled = count fluid inputs of a recipe. Value per mB comes from, in order:",
                "                      bucket item value / 1000, fluid_tag_values, then the recipe that produces it",
                "                      (reflectively read: MaterialFluidRecipe, Create getFluidIngredients/Results,",
                "                       and a generic FluidStack/FluidIngredient field+method scan).",
                "fluid_tag_values = [#forge:inks=15]  -> a bucket of that fluid is worth 15 points",
                "fluid_value_cap  = worth cap of one bucket (mB value = this / 1000)",
                "The item/fluid values are solved by a 4-pass fixed-point iteration (handles cycles like planks<->logs).",
                "",
                "blacklist = extra entries that must NOT appear in the construct menu. Supported formats:",
                "  goety                 -> whole mod          (bare modid)",
                "  iceandfire:*          -> whole mod          (modid:*)",
                "  minecraft:bedrock     -> single item        (modid:item)",
                "  #forge:ingots         -> item tag           (#modid:tag)",
                "  *:*_shard             -> wildcard glob        (* matches anything)",
                "  recipe:minecraft:smelting -> every item that can be produced by that recipe type",
                "                               (also accepts: type:minecraft:smelting)",
                "Lines starting with // are ignored. Matching is case-insensitive for ids.");
        CONSTRUCT_COST_MULTIPLIER = b.defineInRange("cost_multiplier", 10.0D, 0.0D, 10000.0D);
        CONSTRUCT_INGREDIENT_WEIGHT = b.defineInRange("ingredient_weight", 0.75D, 0.0D, 100.0D);
        CONSTRUCT_USE_DEFAULT_BLACKLIST = b.define("use_default_blacklist", true);
        CONSTRUCT_MAGIC_BONUS = b.defineInRange("magic_item_bonus", 40.0D, 0.0D, 10000.0D);
        CONSTRUCT_MAGIC_EXTRA_KEYWORDS = b.defineList("magic_extra_keywords", new java.util.ArrayList<String>(),
                o -> o instanceof String);
        CONSTRUCT_MAGIC_EXTRA_TAGS = b.defineList("magic_extra_tags", new java.util.ArrayList<String>(),
                o -> o instanceof String);
        CONSTRUCT_TIER_BONUS = b.defineList("tier_bonus", new java.util.ArrayList<String>(),
                o -> o instanceof String);
        CONSTRUCT_VALUE_OVERRIDES = b.defineList("value_overrides", new java.util.ArrayList<String>(),
                o -> o instanceof String);
        CONSTRUCT_TAG_VALUES = b.defineList("tag_values", new java.util.ArrayList<String>(),
                o -> o instanceof String);
        CONSTRUCT_UNIQUE_ITEM_BONUS = b.defineInRange("unique_item_bonus", 8.0D, 0.0D, 10000.0D);
        CONSTRUCT_HARDNESS_CAP = b.defineInRange("hardness_cap", 25.0D, 0.0D, 10000.0D);
        CONSTRUCT_TAG_VALUE_CAP = b.defineInRange("tag_value_cap", 60.0D, 0.0D, 10000.0D);
        CONSTRUCT_LOOT_SOURCE_ENABLED = b.define("loot_source_enabled", true);
        CONSTRUCT_LOOT_SOURCE_AUTO_APPLY = b.define("loot_source_auto_apply", false);
        CONSTRUCT_LOOT_SOURCE_CAP = b.defineInRange("loot_source_cap", 60.0D, 0.0D, 10000.0D);
        CONSTRUCT_LOOT_SOURCE_BLACKLIST = b.defineList("loot_source_blacklist",
                new java.util.ArrayList<String>(java.util.List.of(
                        "minecraft:rotten_flesh", "minecraft:bone", "minecraft:string", "minecraft:arrow",
                        "minecraft:gunpowder", "minecraft:spider_eye", "minecraft:feather", "minecraft:leather",
                        "minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton",
                        "minecraft:cod", "minecraft:salmon", "minecraft:ink_sac", "minecraft:bowl",
                        "minecraft:wheat_seeds", "minecraft:wheat", "minecraft:carrot", "minecraft:potato")),
                o -> o instanceof String);
        CONSTRUCT_ENTITY_VALUE_OVERRIDES = b.defineList("entity_value_overrides",
                new java.util.ArrayList<String>(), o -> o instanceof String);
        CONSTRUCT_STRUCTURE_VALUE_OVERRIDES = b.defineList("structure_value_overrides",
                new java.util.ArrayList<String>(), o -> o instanceof String);
        CONSTRUCT_FLUID_VALUE_ENABLED = b.define("fluid_value_enabled", true);
        CONSTRUCT_FLUID_TAG_VALUES = b.defineList("fluid_tag_values", new java.util.ArrayList<String>(),
                o -> o instanceof String);
        CONSTRUCT_FLUID_VALUE_CAP = b.defineInRange("fluid_value_cap", 500.0D, 0.0D, 100000.0D);
        CONSTRUCT_BLACKLIST = b.defineList("blacklist", new java.util.ArrayList<String>(),
                o -> o instanceof String);
        // ---- 拟造物防自动化 ----
        CONSTRUCT_BLUEPRINT_ENABLED = b.define("blueprint_enabled", true);
        CONSTRUCT_BLUEPRINT_TCON_TOOLS_LEGACY = b.define("blueprint_tcon_tools_legacy", true);
        // 墨默刷新的维度黑名单（用户口径）：留空 = 所有维度都能刷；填维度 id 则那些维度不刷
        MOMO_SPAWN_DIMENSION_BLACKLIST = b.defineList("momo_spawn_dimension_blacklist",
                new java.util.ArrayList<String>(java.util.List.of(
                        "irons_spellbooks:pocket_dimension",
                        "tinkersnewlife:gourd")),
                o -> o instanceof String);
        CONSTRUCT_BLUEPRINT_RISKY_LEGACY = b.define("blueprint_risky_legacy", true);
        CONSTRUCT_BLUEPRINT_EXEMPT = b.defineList("blueprint_exempt",
                new java.util.ArrayList<String>(), o -> o instanceof String);
        CONSTRUCT_BLUEPRINT_RISKY_INTERFACES = b.defineList("blueprint_risky_interfaces",
                new java.util.ArrayList<String>(), o -> o instanceof String);
        CONSTRUCT_BLUEPRINT_RISKY_MODS = b.defineList("blueprint_risky_mods",
                new java.util.ArrayList<String>(java.util.List.of(
                        // —— 依据：对整合包 380 个 jar / 98644 个类做静态扫描（javap 反汇编找 instanceof），
                        //    命中的都是"模组自己代码对其物品类做类型判定"的模组；匠魂/AE2/Curios 走的是接口
                        //    （IModifiable / IPartItem / ICurio），由内置接口清单覆盖 ——
                        "@tconstruct",         // 匠魂：工具/部件（IModifiable，另有 blueprint_tcon_tools_legacy 精细控制）
                        "@create",             // 机械动力：FilterItem/ZapperItem/SandPaperItem/PotatoCannon/SuperGlue/Backtank…
                        "@railways",           // 汽鸣铁道：PaintPitcher/ConductorCap/Handcar…
                        "@ae2",                // 应用能源2：Facade/EncodedPattern/WirelessTerminal/PartItem/MatterCannon…
                        "@extendedae", "@advanced_ae", "@ae2wtlib", "@megacells", "@appliedcreate",
                        "@irons_spellbooks",   // 法术书/卷轴/施法器（Scroll/SpellBook/CastingItem…）
                        "@tacz",               // 永恒枪械：AbstractGunItem/AmmoItem
                        "@twilightforest",     // 暮色森林：巨人镐/奖杯/链条/弓/甲/盾…
                        "@mowziesmobs",        // 撼地护手/乌姆武萨纳面具/长矛/毒牙匕首/吹箭
                        "@alexsmobs",          // 次元切割器/浮木滑板等
                        "@touhoulittlemaid",   // 博丽御币/狐符/女仆床
                        "@sophisticatedcore", "@sophisticatedbackpacks", "@sophisticatedstorage",
                        "@create_vampirism",
                        "@mekanism", "@mekanismtools", "@mekanismgenerators", "@mekanismadditions",
                        "@iceandfire", "@iceandfire_curios",
                        // ⚠ 这里比的是"物品命名空间"，不是 modid：有些修复/扩展模组会把物品注册进被修复模组的命名空间
                        //    （例：RevelationFix 把 goety_revelation:apocalyptium_* 注册进 goety_revelation）。
                        //    写错了不会报错、只会静默失效 —— BlueprintCompat 里另有一份"内置关键项"兜底，
                        //    启动时还会把匹配不到任何已注册命名空间的条目 WARN 出来。
                        "@goety", "@goety_revelation", "@revelationfix", "@goety_cataclysm",
                        "@ending_library",
                        "@l2weaponry", "@l2hostility", "@l2complements",
                        "@farmersdelight", "@apotheosis",
                        "@artifacts", "@relics", "@enigmaticlegacy", "@celestial_artifacts",
                        "@vampirism", "@aquaculture", "@dummmmmmy", "@slashblade",
                        "@simplyswords", "@curseofpandora",
                        // 原版物品不做限制：引擎级判定（护盾格挡/工具动作/鞘翅/护甲槽…）已全部转发到位
                        // 想放开某个模组：删掉对应条目即可（或整体 blueprint_risky_legacy=false）
                        "@nonexistent_placeholder")),
                o -> o instanceof String);
        CONSTRUCT_BLUEPRINT_REPORT_ON_START = b.define("blueprint_report_on_start", true);
        CONSTRUCT_BLUEPRINT_CAPABILITY_LEGACY = b.define("blueprint_capability_legacy", true);
        CONSTRUCT_VANISH_ON_DROP = b.define("vanish_on_drop", true);
        CONSTRUCT_CONTAINER_INSTANT_PURGE = b.define("container_instant_purge", true);
        CONSTRUCT_DENY_CONTAINER_SLOTS = b.define("deny_container_slots", true);
        CONSTRUCT_BLOCK_GOETY_RITUAL = b.define("block_goety_ritual", true);
        CONSTRUCT_GLOBAL_SWEEP_CHUNKS = b.defineInRange("global_sweep_chunks", 8, 0, 256);
        b.pop();

        // 飞剑流光拖尾（客户端）
        b.push("flying_sword").comment(
                "Client side: dynamic ribbon trail with a custom flowing-light shader for flying swords.",
                "Set enable_trail=false to disable the ribbon (the old dust particles stay).");
        FLYING_SWORD_TRAIL = b.define("enable_trail", true);
        b.pop();

        // 术式系数：每术式 damage / cost 缩放（1.0 = 原生）
        b.push("techniques").comment("Per-technique formula scale multipliers (1.0 = vanilla values).");
        String[][] techniques = {
                {"yuchuzi", "Yu Chu Zi"}, {"blood_manipulation", "Blood Manipulation"},
                {"ten_shadows", "Ten Shadows"}, {"black_bird", "Black Bird Manipulation"},
                {"puppet", "Puppet Manipulation"}, {"plant_manipulation", "Plant Manipulation"},
                {"flame_manipulation", "Flame Manipulation"}, {"cursed_spirit", "Cursed Spirit Manipulation"},
                {"lightning_manipulation", "Lightning Manipulation"}, {"sky_manipulation", "Sky Manipulation"},
                {"projection", "Projection Sorcery"}, {"wuliang_wuxian", "Limitless: Infinity"},
                {"wuliang_cang", "Limitless: Blue"}, {"jacobs_ladder", "Jacob's Ladder"},
                {"reverse_cursed", "Reverse Cursed Technique"}, {"wu_wei", "Idle Transfiguration"},
                {"cursed_energy_release", "Cursed Energy Discharge"}, {"construct", "Construction Technique"},
                {"cursed_speech", "Cursed Speech"}, {"anti_gravity", "Anti-Gravity Mechanism"},
                {"ten_divide", "Ratio Technique"}
        };
        for (String[] t : techniques) {
            b.push(t[0]).comment(t[1]);
            @SuppressWarnings("unchecked")
            ConfigValue<Double>[] arr = new ConfigValue[]{
                    b.define("damage_scale", 1.0D),
                    b.define("cost_scale", 1.0D)
            };
            TECHNIQUE_SCALES.put(t[0], arr);
            b.pop();
        }
        b.pop();

        // 领域系数：每领域 radius / damage / cost 缩放
        b.push("domains").comment(
                "Domain formula scale multipliers (1.0 = vanilla values).",
                "",
                "fragment_drop_denominator = when a domain is DESTROYED, each boundary block has a 1/N chance",
                "to drop a Boundary Fragment. Default 1000 (= 0.1%, ten times rarer than the old 1/100).",
                "Set 1 to make every block drop one (for testing / generous packs).");
        DOMAIN_FRAGMENT_DROP_DENOMINATOR = b.defineInRange("fragment_drop_denominator", 1000, 1, 1000000);
        String[][] domains = {
                {"zuosha_botu", "Self-Embodiment of Perfection"}, {"wuliang_kongchu", "Unlimited Void"},
                {"fumo_yuchuzi", "Malevolent Shrine"}, {"fuzhu_cisi", "Execution by Verdict"},
                {"taizang_bianye", "Taizang Field"}, {"zhenyan_xiangai", "Authentic Mutual Love"},
                {"qianhe_yingyi", "Embedded Shadow Court"}, {"tie_guan_gai_wei_shan", "Iron Coffin Mountain"},
                {"zi_bi_yuan_dun_guo", "Self-Enclosed Sphere"}, {"dang_yun_ping_xian", "Horizon of Oscillating Veils"},
                {"shi_bao_yue_gong_dian", "Time Cell Moon Palace"}, {"san_chong_ji_ku", "Triple Suffering"}
        };
        for (String[] d : domains) {
            b.push(d[0]).comment(d[1]);
            @SuppressWarnings("unchecked")
            ConfigValue<Double>[] arr = new ConfigValue[]{
                    b.define("radius_scale", 1.0D),
                    b.define("damage_scale", 1.0D),
                    b.define("cost_scale", 1.0D)
            };
            DOMAIN_SCALES.put(d[0], arr);
            b.pop();
        }
        b.pop();

        SPEC = b.build();
    }

    // ==================== 取值助手 ====================

    /** 术式伤害缩放：给定 modifier path 的 damage_scale（无则 1.0） */
    public static double techniqueDamage(String id) {
        ConfigValue<Double>[] arr = TECHNIQUE_SCALES.get(id);
        return arr == null ? 1.0 : arr[0].get();
    }

    /** 术式咒力消耗缩放 */
    public static double techniqueCost(String id) {
        ConfigValue<Double>[] arr = TECHNIQUE_SCALES.get(id);
        return arr == null ? 1.0 : arr[1].get();
    }

    /** 领域半径缩放 */
    public static double domainRadius(String id) {
        ConfigValue<Double>[] arr = DOMAIN_SCALES.get(id);
        return arr == null ? 1.0 : arr[0].get();
    }

    /** 领域伤害缩放 */
    public static double domainDamage(String id) {
        ConfigValue<Double>[] arr = DOMAIN_SCALES.get(id);
        return arr == null ? 1.0 : arr[1].get();
    }

    /** 领域咒力消耗缩放 */
    public static double domainCost(String id) {
        ConfigValue<Double>[] arr = DOMAIN_SCALES.get(id);
        return arr == null ? 1.0 : arr[2].get();
    }

    // ==================== 古老者水晶 · 魔力台座（取值助手） ====================
    // 全部带 try/catch：配置还没就绪（注册前/客户端早期）时一律走上面的默认常量 ✓
    // ⚠ 这些默认值只写在 PEDESTAL_DEFAULT_* 常量里一处 ⇒ 不会与 defineInRange 的默认值漂移 ✗

    /** 台座亮度 0 时的满速充能（EE/秒；配置没就绪 ⇒ {@link #PEDESTAL_DEFAULT_CHARGE_MAX_PER_SECOND}） */
    public static double pedestalChargeMaxPerSecond() {
        try {
            return Math.max(0.0D, PEDESTAL_CHARGE_MAX_PER_SECOND.get());
        } catch (Throwable ignored) {
            return PEDESTAL_DEFAULT_CHARGE_MAX_PER_SECOND;
        }
    }

    /** 台座完全不充的亮度阈值（配置没就绪 ⇒ {@link #PEDESTAL_DEFAULT_LIGHT_CAP}） */
    public static int pedestalLightCap() {
        try {
            return Math.max(1, PEDESTAL_LIGHT_CAP.get());
        } catch (Throwable ignored) {
            return PEDESTAL_DEFAULT_LIGHT_CAP;
        }
    }

    /**
     * 启用的环境能量来源清单（配置没就绪 ⇒ {@link #PEDESTAL_DEFAULT_SOURCE} = {@code "*"} 全部启用 ✓）。
     * <p>§545 起是"清单"语义 ✓（{@code "*"} / 逗号分隔 id ✓）；解析在
     * {@code AmbientEnergySources}（带缓存 ✓ 配置重载会作废 ✓）。
     */
    public static String pedestalSourceId() {
        try {
            String id = PEDESTAL_SOURCE.get();
            return id == null || id.isBlank() ? PEDESTAL_DEFAULT_SOURCE : id;
        } catch (Throwable ignored) {
            return PEDESTAL_DEFAULT_SOURCE;
        }
    }

    /**
     * 某条来源的<b>总开关</b>（§545：每源一个 ✓）。
     * <p>⚠ 配置还没就绪时一律回 {@code true}（= 默认全开 ✓ 与 {@code define(..., true)} 的默认值一致 ✓）。
     */
    public static boolean energySourceEnabled(String sourceId) {
        if (sourceId == null) return false;
        switch (sourceId) {
            case "light_level":
                return flag(PEDESTAL_LIGHT_ENABLED, true);
            case "plant":
                return flag(PEDESTAL_PLANT_ENABLED, true);
            case "tcon_fuel":
                return flag(PEDESTAL_FUEL_ENABLED, true);
            case "soul_death":
                return flag(PEDESTAL_SOUL_ENABLED, true);
            case "demigod_player":
                return flag(PEDESTAL_PLAYER_ENABLED, true);
            default:
                // 附属模组自己注册的来源没有我们的开关 ⇒ 只要它在清单里就算启用 ✓（不会被我们误关 ✗）
                return true;
        }
    }

    private static boolean flag(ConfigValue<Boolean> value, boolean fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int intOr(ConfigValue<Integer> value, int fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static double doubleOr(ConfigValue<Double> value, double fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    /** §545 ② 植物：扫描球半径（格；配置没就绪 ⇒ {@value #PEDESTAL_DEFAULT_PLANT_RADIUS}） */
    public static int plantRadius() {
        return Math.max(0, intOr(PEDESTAL_PLANT_RADIUS, PEDESTAL_DEFAULT_PLANT_RADIUS));
    }

    /** §545 ② 植物：1 点凋灵度值多少 EE（配置没就绪 ⇒ {@value #PEDESTAL_DEFAULT_PLANT_EE_PER_POINT}） */
    public static double plantEePerPoint() {
        return Math.max(0.0D, doubleOr(PEDESTAL_PLANT_EE_PER_POINT, PEDESTAL_DEFAULT_PLANT_EE_PER_POINT));
    }

    /** §545 ③ 匠魂燃料：扫描球半径（格） */
    public static int fuelRadius() {
        return Math.max(0, intOr(PEDESTAL_FUEL_RADIUS, PEDESTAL_DEFAULT_FUEL_RADIUS));
    }

    /** §545 ③ 匠魂燃料：一个物品份值多少 EE */
    public static double fuelEePerItem() {
        return Math.max(0.0D, doubleOr(PEDESTAL_FUEL_EE_PER_ITEM, PEDESTAL_DEFAULT_FUEL_EE_PER_ITEM));
    }

    /** §545 ③ 匠魂燃料：一个物品份按多少 tick 折算（≥1） */
    public static int fuelTicksPerItem() {
        return Math.max(1, intOr(PEDESTAL_FUEL_TICKS_PER_ITEM, PEDESTAL_DEFAULT_FUEL_TICKS_PER_ITEM));
    }

    /** §545 ④ 生物死亡：判定球半径（格） */
    public static int soulRadius() {
        return Math.max(0, intOr(PEDESTAL_SOUL_RADIUS, PEDESTAL_DEFAULT_SOUL_RADIUS));
    }

    /** §545 ④ 生物死亡：每点最大生命值多少 EE（0.025 ⇒ 20 血 = 0.5 EE） */
    public static double soulEePerHp() {
        return Math.max(0.0D, doubleOr(PEDESTAL_SOUL_EE_PER_HP, PEDESTAL_DEFAULT_SOUL_EE_PER_HP));
    }

    /** §545 ④ 生物死亡：来源总开关（配置没就绪 ⇒ true） */
    public static boolean soulEnabled() {
        return flag(PEDESTAL_SOUL_ENABLED, true);
    }

    /** §545 ⑤ 玩家汲取：判定球半径（格） */
    public static int playerRadius() {
        return Math.max(0, intOr(PEDESTAL_PLAYER_RADIUS, PEDESTAL_DEFAULT_PLAYER_RADIUS));
    }

    /** §545 ⑤ 玩家汲取：每名玩家每秒多少 EE */
    public static double playerEePerSecond() {
        return Math.max(0.0D, doubleOr(PEDESTAL_PLAYER_EE_PER_SECOND, PEDESTAL_DEFAULT_PLAYER_EE_PER_SECOND));
    }

    // ==================== §557 EE 网络（取值助手） ====================
    // 与上面那批同一口径：配置没就绪（注册前 / 客户端早期）时一律回默认常量 ✓ 全 try/catch ✓

    /** §557 抽取方块：每 tick 抽的上限（EE；跨邻居合计 ✓） */
    public static int eeExtractorPullPerTick() {
        return Math.max(0, intOr(EE_EXTRACTOR_PULL_PER_TICK, EE_NET_DEFAULT_EXTRACTOR_PULL));
    }

    /** §557 抽取方块：每 tick 推的上限（EE；跨邻居合计 ✓） */
    public static int eeExtractorPushPerTick() {
        return Math.max(0, intOr(EE_EXTRACTOR_PUSH_PER_TICK, EE_NET_DEFAULT_EXTRACTOR_PUSH));
    }

    /** §557 抽取方块：缓冲上限（EE；≥1 ✓ 0 会让 insertEe 永远拒收 ✗） */
    public static int eeExtractorBuffer() {
        return Math.max(1, intOr(EE_EXTRACTOR_BUFFER, EE_NET_DEFAULT_EXTRACTOR_BUFFER));
    }

    /** §557 转化器：FE 池上限（≥1 ✓） */
    public static int converterBufferFe() {
        return Math.max(1, intOr(CONVERTER_BUFFER_FE, EE_NET_DEFAULT_CONVERTER_BUFFER_FE));
    }

    /** §557 转化器：对外吞吐闸门（FE/t；收与推都受它约束 ✓） */
    public static int converterOutputFePerTick() {
        return Math.max(0, intOr(CONVERTER_OUTPUT_FE_PER_TICK, EE_NET_DEFAULT_CONVERTER_OUTPUT_FE));
    }

    /** §557 转化器：直接 Forge Energy 输入上限（FE/t） */
    public static int converterInputFePerTick() {
        return Math.max(0, intOr(CONVERTER_INPUT_FE_PER_TICK, EE_NET_DEFAULT_CONVERTER_INPUT_FE));
    }

    /** §557 转化器：EE 输入上限（EE/t） */
    public static int converterInputEePerTick() {
        return Math.max(0, intOr(CONVERTER_INPUT_EE_PER_TICK, EE_NET_DEFAULT_CONVERTER_INPUT_EE));
    }

    /**
     * §598 转化器：热量汇率（FE / 热量）。
     * <p>⚠ <b>0 = 明确关掉"热量"这一路</b> ✓（{@code MekanismEnergyBridge.HeatSink} 会一点热都不收 ✓）
     * —— 别写成"返回 0 却还去做除法" ✗ 那样会得到 Infinity ⇒ 一次收进无限热 ✗（防爆表红线 ✓）。
     */
    public static double converterFePerHeat() {
        return Math.max(0.0D, doubleOr(CONVERTER_FE_PER_HEAT, EE_NET_DEFAULT_CONVERTER_FE_PER_HEAT));
    }

    /** §598 转化器：Create 应力消耗（SU；0 = 白嫖动力 ✗ 建议保持 ≥1 ✓） */
    public static double converterStressImpact() {
        return Math.max(0.0D, doubleOr(CONVERTER_STRESS_IMPACT, EE_NET_DEFAULT_CONVERTER_STRESS_IMPACT));
    }

    /** §598 转化器：热量槽容量（J/K ✓ ≥1 ⇒ 别给 0 ✗ 通用机械那边会算不动 ✓） */
    public static double converterHeatSinkCapacity() {
        return Math.max(1.0D, doubleOr(CONVERTER_HEAT_SINK_CAPACITY, EE_NET_DEFAULT_CONVERTER_HEAT_SINK_CAPACITY));
    }

    // §598：原 §557 的四个"这一路是否启用"开关（converterMekanismEnabled / converterCreateEnabled /
    //       converterIc2Enabled / converterAe2Enabled）**已整体删除** ✗ —— 它们唯一的使用者是那套
    //       已删的反射适配器 ✓，留着只会让人以为"关掉键就能断某一路"✗（实际从 §577 起就没读过 ✓）。

    /** 充能时是否放冷色粒子（配置没就绪 ⇒ true） */
    public static boolean pedestalParticles() {
        try {
            return PEDESTAL_PARTICLES.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 充能时是否放音效（配置没就绪 ⇒ true） */
    public static boolean pedestalSound() {
        try {
            return PEDESTAL_SOUND.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 领域被破坏时结界碎片的掉落分母：每个结界方块 1/N 概率掉落。
     *
     * <p>默认 1000（原为 100）；返回 1 表示"每块都掉"。配置没就绪时按默认值处理。
     */
    /** 噤默手套是否全部静音（默认 false：只静音生物叫声；配置没就绪按默认处理） */
    public static boolean silentGloveMuteAll() {
        try {
            return SILENT_GLOVE_MUTE_ALL.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int domainFragmentDropDenominator() {
        try {
            return Math.max(1, DOMAIN_FRAGMENT_DROP_DENOMINATOR.get());
        } catch (Throwable ignored) {
            return 1000;
        }
    }
}
