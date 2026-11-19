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

    // ==================== 善恶系统（「心」） ====================
    // ==================== 咒力核心 ====================
    public static final ConfigValue<Boolean> CURSE_CORE_ENABLED;

    /**
     * ⭐ §1173 启动到主菜单时自动跑一次匠魂材质生成器（⭐ 只补缺失的 ✓）并强制启用生成的资源包 ✓。
     * <p>⭐ 用户口径 ✓：「**这个功能添加到配置文件中可以自由开关，默认开启**」✓
     * （⭐ 配置键 ⭐ {@code auto_part_textures.enable_auto_part_textures} ✓ 默认 `true` ✓）。
     */
    public static final ConfigValue<Boolean> AUTO_PART_TEXTURES;

    // ==================== 拔刀剑物品栏渲染优化（§871） ====================
    /** 总开关：拔刀剑物品栏渲染优化（用户口径 C：缓存 ＋ 拥挤时跳发光层 ✓ 默认开 ✓） */
    /** 同屏超过多少把拔刀剑就不再画"发光层"（省掉每把刀的第二遍几何 ✓ 默认 6 ✓ 0 = 永不跳 ✗） */

    // ==================== Boss 战（§864 用户口径） ====================
    public static final ConfigValue<Boolean> BOSS_FIGHT_ENABLED;
    public static final ConfigValue<Boolean> BOSS_FIGHT_ROLLS_EQUAL_PARTICIPANTS;
    public static final ConfigValue<Boolean> BOSS_FIGHT_GRANT_ADVANCEMENTS;
    public static final ConfigValue<Integer> BOSS_FIGHT_MAX_ROLLS;

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

    // ==================== 物品空标签归一化（§763 建 / §787 按原版纠正方向） ====================
    /**
     * §787（原 §763）：把**空的 CompoundTag `{}` 剥成"无标签"**，让"同款但一个带 `{}`、一个没标签"的物品能堆叠。
     *
     * <p>背景（用户报 + 存档实证 ✓）：诡厄的「诅咒之笼」在走过
     * {@code CursedCageBlock#setPlacedBy}（里面用 {@code ItemStack#getOrCreateTag()} 读
     * {@code BlockEntityTag} ✗）之后会被留下一个**空标签** `{}` ✗，而没走过这条路的同款物品**根本没有标签** ✗；
     * 原版 {@code ItemStack.isSameItemSameTags} 用 {@code Objects.equals} 比**原始 tag** ⇒ {@code {}} 与 {@code null} 不相等 ✗
     * ⇒ 两叠永远合不到一起 ✗（AE2 也是按"物品+NBT+能力"分别建键 ✓ 所以它在 AE2 里也存成两格 ✓）。
     *
     * <p><b>用户口径（2026-09-28 原话）：「原版是不是本来应该是无标签？如果是就按原版来」</b> ✓
     * —— 已核反编译源码（`build/tmp-mcsrc/mcfull/.../ItemStack.java`）：
     * {@code hasTag()}（L507）＝ {@code tag != null && !tag.isEmpty()} ✓（原版就认为 `{}` 等于没标签 ✓）、
     * {@code isSameItemSameTags()}（L459-463）比的是原始 tag ✓、
     * {@code removeTagKey()}（L539-547）／{@code resetHoverName()}（L603-605）**掏空就 `tag = null`** ✓
     * ⇒ 原版语义里"什么都没有"就是 **null** ✓。
     * ⇒ 所以**把 `{}` 剥掉**（回到原版状态 ✓），而不是给所有物品补 `{}` ✗（§763 的旧方向已纠正 ✗）。
     */
    public static final ConfigValue<Boolean> NORMALIZE_ITEM_TAG;

    // ==================== 神秘遗物饰品：不要吃掉夜视（§769） ====================
    /**
     * §769：神秘遗物的「海洋之石」/「寻宝护符」在 {@code curioTick} 里每 tick 调
     * {@code removeNightVisionEffect(Player, int)}，把"等级不够高"（amplifier ≤ max-1）的夜视清掉 ✗；
     * 血族给的夜视正好是 amplifier 0 ⇒ 两边的"清/补"拉锯 ⇒ 夜视一开一关 ✗。
     *
     * <p>开启本项＝拦掉那次移除 ✓（默认 <b>true</b> ✓ 用户要求 ✓）；关掉＝保留神秘遗物的原设计 ✓。
     */
    public static final ConfigValue<Boolean> ENIGMATIC_CURIO_KEEPS_NIGHT_VISION;

    // ==================== 暮色森林迷宫随机源线程安全（§779） ====================
    /**
     * §779：C2ME 的多线程世界生成会让**同一个 {@code TFMaze} 实例**被多个线程同时使用 ✗，
     * 而它的 {@code rand} 是非线程安全的 {@code LegacyRandomSource} ⇒ 原版检测器直接崩 ✗
     * （NL `crash-2026-09-28_19.49.42-server.txt` ✓）。
     * <p>开启本项＝给该随机源套一层同步包装（序列不变 ✓ 竞态消失 ✓，默认 <b>true</b> ✓）。
     */
    public static final ConfigValue<Boolean> TWILIGHT_MAZE_THREAD_SAFE_RANDOM;

    public static final ConfigValue<List<? extends String>> CURSE_AFFINITY_BLACKLIST;
    public static final ConfigValue<Boolean> CONSCIENCE_ENABLED;

    // Forge 的 defineInRange 会通过 comment(String) 自动添加英文前缀；只翻译前缀，不改范围验证。
    private static final class ChineseConfigBuilder extends ForgeConfigSpec.Builder {
        @Override
        public ForgeConfigSpec.Builder comment(String comment) {
            return super.comment(comment.startsWith("Range: ") ? "范围：" + comment.substring("Range: ".length()) : comment);
        }
    }

    public static final ForgeConfigSpec SPEC;
    static {
        ForgeConfigSpec.Builder b = new ChineseConfigBuilder();

        // 古神事件
        b.push("elder_events").comment(
                "每项开关控制对应古神道具的获取和使用事件；关闭后不再触发这些事件。");
        YOG_SOTHOTH_KEY = b.comment("犹格·索托斯之门钥匙事件。").define("enable_yog_sothoth_key", true);
        YELLOW_KING = b.comment("黄衣之王遗物事件。").define("enable_yellow_king", true);
        RLYEH_CALL = b.comment("拉莱耶的呼唤事件。").define("enable_rlyeh_call", true);
        NYARLATHOTEP_DESIRE = b.comment("奈亚拉托提普的欲望事件。").define("enable_nyarlathotep_desire", true);
        b.pop();

        // 咒力核心
        b.push("curse_core").comment("允许制作（仪式）和使用（装备、施术）咒力核心，默认开启。");
        CURSE_CORE_ENABLED = b.define("allow_curse_core_craft_and_use", true);
        b.pop();

        b.push("curse_affinity");
        CURSE_AFFINITY_BLACKLIST = b.comment(
                "命中黑名单的物品不会自动抽取咒力亲和，已有亲和保留。",
                "写法示例：@curios（模组 ID，按注册命名空间匹配）、curios 或 curios:*（命名空间）、curios:ring（完整物品 ID）。",
                "通配符示例：*:*_ring、example:charm_?。* 匹配零个或多个字符，? 匹配一个字符，冒号两侧分别匹配。")
                .defineListAllowEmpty("blacklist", List.<String>of(), entry -> entry instanceof String);
        b.pop();

        b.push("conscience");
        CONSCIENCE_ENABLED = b.comment("「心」的总开关；本项和游戏规则 doConscienceHeart 必须同时为 true 才会启用。")
                .define("enabled", true);
        b.pop();

        // ⭐ §1173 自动跑匠魂材质生成器（用户口径 ✓ 默认开启 ✓ 可关 ✓）
        b.push("auto_part_textures").comment(
                "进入主菜单时自动运行匠魂部件材质生成器，只生成缺失材质，并自动启用生成的资源包。",
                "默认开启；改为 false 可完全禁用。");
        AUTO_PART_TEXTURES = b.define("enable_auto_part_textures", true);
        b.pop();

        // 古老者水晶 · 魔力台座（§545 起：五条来源并行叠加，其中默认开四条 · 亮度默认降到 0.5）
        b.push("elder_crystal").comment(
                "古老者水晶的充能台座。下列五种来源并行叠加，没有总速率上限，每种来源都有独立开关和参数。",
                "light_level（亮度）：台座上方方块越暗越快。速率 = pedestal_charge_max_per_second × (pedestal_light_cap - 亮度) / pedestal_light_cap。",
                "亮度取原版刷怪亮度（0～15，综合天空光和方块光）；不要求夜晚或能看见天空。亮度为 0 时最快，达到上限时停止。",
                "亮度来源默认最快 0.5 EE/秒（30 EE/分钟），充满 1000 EE 约需 33 分钟。亮度上限默认 15；设为 16 时，亮度 15 下仍约有 0.031 EE/秒。",
                "plant（植物）：球形范围内每株植物每秒积 1 点凋灵度，默认每点 0.5 EE。草 5 点、花 20 点、树苗 30 点时消失；作物按花算，排除凋灵玫瑰。重启不保留凋灵度。",
                "tcon_fuel（匠魂燃料）：每秒按匠魂燃料注册表模拟范围内的流体容器，燃烧速度为温度 / 4 mB/刻。",
                "燃尽流体贡献的等效物品数 =（消耗量 / 燃料量）×（燃烧时长 / fuel_ticks_per_item），再乘 fuel_ee_per_item 得到 EE。熔岩每罐约 25 EE/秒，不支持固体燃料。",
                "soul_death（灵魂）：范围内生物死亡（含玩家）按最大生命值 × soul_ee_per_hp 累积 EE，下一次每秒结算时充入。默认 20 点生命值贡献 0.5 EE。",
                "demigod_player（半神之力）：范围内每名玩家贡献 player_ee_per_second，默认每人每秒 0.5 EE，无消耗，可叠加。",
                "pedestal_source 接受逗号分隔的来源 ID：light_level、plant、tcon_fuel、soul_death、demigod_player；默认 * 表示全部。单个 ID 表示只启用该来源；未知 ID 会被忽略并警告一次。",
                "light_enabled / plant_enabled / fuel_enabled / soul_enabled / player_enabled 是独立开关，来源仍须列在 pedestal_source 中。",
                "plant_radius / fuel_radius / soul_radius / player_radius 是球形半径，默认均为 5 格。",
                "plant_ee_per_point 是每点凋灵度提供的 EE（默认 0.5）；fuel_ee_per_item 是每等效物品提供的 EE（默认 0.5）。",
                "fuel_ticks_per_item 默认 10 刻，约等于匠魂最廉价熔化配方的 9～10 刻；增大可减慢充能。",
                "pedestal_particles / pedestal_sound 分别控制充能粒子和紫水晶铃音；粒子密度随总速率变化。",
                "水晶物品容量为 1000 EE，水晶方块为 4000 EE。范围内目标都充满后停止查询来源，不再消耗燃料或使植物凋零。");
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
                "EE 网络：EE 抽取方块与万用能量转化器。",
                "固定换算：1 EE = 1000 FE，1 EU = 4 FE（工业时代 2），1 AE = 2 FE（应用能源 2），10 J = 4 FE（通用机械）。",
                "机械动力换算：FE/t = 45 × RPM / 64；RF、Tesla、uI、FF 按 1:1 走 ForgeCapabilities.ENERGY，无需适配器。",
                "EE 抽取方块（tinkersnewlife:ee_extractor）：右键打开单槽界面，放入已充能的古老者水晶物品或方块。",
                "每刻从槽中抽取到缓存，再按上、下、北、南、西、东顺序向相邻 EE 容器输出。缓存满时停止抽取，不会损失物品中的 EE。",
                "extractor_pull_ee_per_tick / extractor_push_ee_per_tick 分别是每刻抽取、输出的 EE（默认均为 256）；extractor_buffer_ee 是缓存（默认 4000 EE）。",
                "万用能量转化器（tinkersnewlife:energy_converter）单向转为 FE，并通过 IEnergyStorage#receiveEnergy 向相邻方块推送；不会从输出端反抽。",
                "相邻 EE 容器可主动输入（1 EE = 1000 FE）；也可从相邻方块主动抽取 Forge 能量（1:1）。",
                "通用机械：能量线通过 STRICT_ENERGY 输入 J，导热管从右侧通过 HEAT_HANDLER 输入热量。",
                "应用能源 2：从左侧网络节点取 AE，使用 PowerUnits.FE.conversionRatio（官方默认 1 AE = 2 FE）。",
                "机械动力：前侧转轴驱动，按转速换算 FE，并施加 converter_stress_impact（默认 4 SU，设为 0 则无应力消耗）。",
                "上述可选通路随模组安装自动可用，没有单独开关。",
                "converter_output_fe_per_tick 限制总吞吐（默认 64 FE/刻）；converter_input_fe_per_tick 限制直接输入（默认 64 FE/刻）；converter_input_ee_per_tick 限制 EE 输入（默认 512 EE/刻）。",
                "converter_buffer_fe 是内部 FE 缓存（默认 32000）；converter_fe_per_heat 是每单位热量转换的 FE（默认 0.4，0 表示不接收热量）。",
                "converter_heat_sink_capacity 是热容量（默认 64 J/K）；转化器不存热，只接收当刻能转为 FE 的热量。");

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
                "双向认知阻碍面具（头饰）：隐藏玩家头顶名牌，让生物无法锁定佩戴者，并解除已有锁定。",
                "除玩家外的活体均按亡灵处理，包括咒具亡灵加成、反转术式（伤害而非治疗）、雅各布天梯及咒灵操术的捕捉判定。",
                "allow_retaliation：允许被佩戴者攻击的生物还击（默认 true）。使用原版 getLastHurtByMob()，每次攻击刷新，100 刻（5 秒）后清除；设为 false 时不会被锁定。",
                "hide_from_radar：是否从 Xaero 小地图雷达隐藏佩戴者（默认 true）。只过滤雷达实体，不影响原版或 YSM 的角色渲染；设为 false 时只恢复雷达显示。");
        COGNITIVE_MASK_HIDE_FROM_RADAR = b.define("hide_from_radar", true);
        COGNITIVE_MASK_ALLOW_RETALIATION = b.define("allow_retaliation", true);
        b.pop();

        // 无为转变·伪装渲染替换（客户端）
        b.push("wuwei_disguise").comment(
                "客户端：将使用无为转变伪装的玩家渲染为目标生物，而非玩家模型。",
                "若与接管玩家渲染的其他模组（例如 YSM）冲突，可设 enable_disguise_render=false。");
        WUWEI_DISGUISE_RENDER = b.define("enable_disguise_render", true);
        WUWEI_SPEED_SCALE = b.defineInRange("speed_scale", 0.5D, 0.05D, 4.0D);
        WUWEI_EQUIPMENT_RENDER = b.define("render_equipment", true);
        b.pop();

        // 混沌之流：每次攻击随机二选一（物理 / 某一学派的法术），只结算一次（源钻合金 origin_alloy）
        b.push("chaos_flow").comment(
                "源钻合金特性「混沌之流」：每次攻击随机二选一，只结算一次，原始伤害量不变。",
                "物理：原样保留原伤害来源和伤害量。法术：取消原伤害，改为随机学派的法术伤害；学派来自铁魔法注册表，含附属模组学派。",
                "不再分段，避免黑闪（^2.5）、星辰之子（×2^等级）、巫师套装和法杖法强等效果按段重复增幅。",
                "enabled=false 时不再抽取，攻击照常结算物理伤害。");
        CHAOS_FLOW_ENABLED = b.define("enabled", true);
        b.pop();

        // 不可名状效果：客户端观感（撑开视野 + 后处理 + 信号干扰花屏）
        b.push("unnameable").comment(
                "不可名状效果的客户端表现。",
                "fov_multiplier：视野倍率（1.4 表示扩大 40%）。",
                "post_effect：锐化、提高对比度和饱和度，并反转颜色；遇到着色器冲突或不想反色时可关闭。",
                "post_effect_pulse：默认随机间歇显示（约开 2～5 秒、关 0.5～1.5 秒），设为 false 时持续显示；效果结束、死亡或离开世界时立即清除。",
                "glitch：全屏信号干扰，含撕裂、雪花、滚动干扰条和闪光；glitch_intensity 控制强度（1.0 默认、2.0 更强、0 关闭）。",
                "whispers：闪烁低语文字，翻译键为 whisper.tinkersnewlife.*。",
                "whisper_sound：在环境音轨循环播放低语（tinkersnewlife:effect.whispers），效果结束立即停止；whisper_sound_volume 控制音量（0 静音、1.0 默认）。");
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
                "构筑术式：通过配方拟造物品。",
                "cost_multiplier：最终咒力消耗倍率（1.0 为原公式，默认 10.0）。",
                "use_default_blacklist：内置黑名单包含矿石、粗矿、锭、粒、粉、宝石、碎片及流体容器（桶、药水、流体瓶）。",
                "矿物通过 #forge:ores、#forge:raw_materials、#forge:ingots、#forge:nuggets、#forge:dusts、#forge:gems、对应的 #c:* 标签及 *:*_shard / *:*_dust / *:*_nugget 匹配。",
                "容器通过 #forge:buckets、*:*_bucket、*:bucket_*、原版三种药水、*:*_potion、*:*_bottle、*:bottle_* 匹配。",
                "magic_item_bonus：为卷轴、法术书、符文、法杖等缺少常规属性的魔法物品增加估值（0 禁用），按饰品／铁魔法标签或类与接口名中的关键词识别；magic_extra_keywords / magic_extra_tags 可补充识别规则。",
                "tier_bonus：按物品 ID 的品质词加分。内置 uncommon=6、rare=14、epic=30、legendary=60、mythic=90、divine=90、supreme=120、ultimate=120；原料加分也会传到产物。",
                "value_overrides：直接覆盖估值。例如 irons_spellbooks:legendary_ink=200（完整物品 ID）、*_ink=120（通配符）、#forge:gems=25（物品标签）。",
                "无配方物品（采矿、采集、掉落）：tag_values 按标签叠加估值，内置 #forge:ores=12、#forge:raw_materials=8、#forge:ingots=10、#forge:gems=25、#forge:dusts=6、#forge:nuggets=2、#forge:storage_blocks=20、#minecraft:coals=5。",
                "tag_value_cap 限制单物品标签加分；unique_item_bonus 是不可堆叠物品加分；hardness_cap 限制硬度估值（如黑曜石 50 → 25、远古残骸 30 → 15）。",
                "运行 /tinkersnewlife construct lootsuggest 扫描实体掉落与箱子战利品；缓存写入 config/mofengbaizhi/construct/loot_index.json，建议写入同目录 loot_suggestions.toml，可复制进 value_overrides。",
                "loot_source_enabled 允许扫描和自动查表。loot_source_auto_apply 默认关闭；开启后只影响无配方、非方块、可堆叠、没有估值覆盖或标签估值且不在黑名单内的物品。",
                "loot_source_cap 限制战利品估值；loot_source_blacklist 排除可大量刷取的杂物；entity_value_overrides / structure_value_overrides 覆盖难度基础值。",
                "fluid_value_enabled：计入配方的流体原料，每毫桶依次尝试按桶物品价值 / 1000、fluid_tag_values、产出配方估值；兼容匠魂、机械动力和通用流体配方字段及方法。",
                "fluid_tag_values 例：#forge:inks=15 表示每桶 15 点；fluid_value_cap 限制每桶价值。物品和流体通过四轮迭代估值，以处理木板与原木一类循环。",
                "blacklist：额外排除的拟造物品。示例：goety 或 iceandfire:*（整个命名空间）、minecraft:bedrock（单个物品）、#forge:ingots（标签）、*:*_shard（通配符）。",
                "recipe:minecraft:smelting 或 type:minecraft:smelting 排除该配方类型能产出的所有物品。以 // 开头的条目忽略，ID 匹配不区分大小写。");
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
            "@vampirism", "@aquaculture", "@dummmmmmy",
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
                "客户端：使用自定义流光着色器渲染飞剑的动态带状拖尾。设 enable_trail=false 可关闭带状拖尾，保留原有粒子。");
        FLYING_SWORD_TRAIL = b.define("enable_trail", true);
        b.pop();

        // 术式系数：每术式 damage / cost 缩放（1.0 = 原生）
        b.push("techniques").comment("各术式公式的缩放倍率（1.0 表示模组原值）。");
        String[][] techniques = {
                {"yuchuzi", "御厨子"}, {"blood_manipulation", "赤血操术"},
                {"ten_shadows", "十种影法术"}, {"black_bird", "黑鸟操术"},
                {"puppet", "傀儡操术"}, {"plant_manipulation", "植物操术"},
                {"flame_manipulation", "火焰操术"}, {"cursed_spirit", "咒灵操术"},
                {"lightning_manipulation", "雷电操术"}, {"sky_manipulation", "天空操术"},
                {"projection", "投射咒法"}, {"wuliang_wuxian", "无下限·无限"},
                {"wuliang_cang", "无下限·苍"}, {"jacobs_ladder", "雅各布天梯"},
                {"reverse_cursed", "反转术式"}, {"wu_wei", "无为转变"},
                {"cursed_energy_release", "咒力放出"}, {"construct", "构筑术式"},
                {"cursed_speech", "咒言"}, {"anti_gravity", "反重力机构"},
                {"ten_divide", "十划咒法"}
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
                "各领域公式的缩放倍率（1.0 表示模组原值）。",
                "fragment_drop_denominator：领域被破坏时，每个边界方块以 1/N 的概率掉落边界碎片。默认 1000（0.1%，比旧版 1/100 低十倍）；设为 1 可使每个方块都掉落。");
        DOMAIN_FRAGMENT_DROP_DENOMINATOR = b.defineInRange("fragment_drop_denominator", 1000, 1, 1000000);
        String[][] domains = {
                {"zuosha_botu", "坐杀博徒"}, {"wuliang_kongchu", "无量空处"},
                {"fumo_yuchuzi", "伏魔御厨子"}, {"fuzhu_cisi", "伏诛赐死"},
                {"taizang_bianye", "胎藏遍野"}, {"zhenyan_xiangai", "真赝相爱"},
                {"qianhe_yingyi", "嵌合暗翳庭"}, {"tie_guan_gai_wei_shan", "铁棺盖围山"},
                {"zi_bi_yuan_dun_guo", "自闭圆顿裹"}, {"dang_yun_ping_xian", "荡蕴平线"},
                {"shi_bao_yue_gong_dian", "时胞月宫殿"}, {"san_chong_ji_ku", "三重疾苦"}
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

        // §787（原 §763）物品空标签归一化：把**空标签 {} 剥掉**，回到原版"无标签"状态 ✓
        // 用户口径（2026-09-28）：「原版是不是本来应该是无标签？如果是就按原版来」⇒ 源码实核 = 是 ✓
        // ⚠ 修（§763b）：这里原来**多写了一个 `b.pop()`** ✗ ⇒ 触发
        //    `IllegalArgumentException: Attempted to pop 1 elements when we only had: []` ✗
        //    ⇒ 整个模组加载失败（NL 包 crash-2026-09-28_17.36.52-fml.txt ✓）。
        //    上面那个 `b.pop();` 已经是"pop 掉 domains 外层"✓，本节只需 push 一次、pop 一次 ✓。
        b.push("item_tag_normalize").comment(
                "移除空物品标签 {}，恢复原版新物品的无标签状态。",
                "有的模组只想读取却调用 ItemStack#getOrCreateTag()，留下空标签。原版认为空标签和无标签不相等，不能堆叠；应用能源 2 也会把它们分开存储。",
                "true（默认）移除空标签；false 不修改物品 NBT。仅处理玩家背包（每秒检查）、拾取和合成的物品，不修改非空标签、物品能力或堆叠逻辑。",
                "应用能源 2 中已存放的物品需取出一次，才有机会清理原来的空标签。");
        NORMALIZE_ITEM_TAG = b.define("enabled", true);
        b.pop();

        // §769 神秘遗物饰品不要吃掉夜视（用户要求 ✓）
        b.push("enigmatic_curio_keeps_night_vision").comment(
                "神秘遗物的海洋之石或矿工护符会移除低等级夜视，与吸血鬼的夜视（等级索引 0）冲突，引发画面闪烁。",
                "true（默认）阻止移除、保留夜视；false 保留神秘遗物的原行为。");
        ENIGMATIC_CURIO_KEEPS_NIGHT_VISION = b.define("enabled", true);
        b.pop();

        // §779 暮色森林迷宫（TFMaze）随机源线程安全 —— 修 C2ME 多线程世界生成下的竞态崩溃
        b.push("twilight_maze_thread_safe_random").comment(
                "同步访问暮色森林迷宫 TFMaze 的随机源，避免 C2ME 多线程生成世界时因多个线程访问同一 LegacyRandomSource 而崩溃。",
                "true（默认）串行访问，随机序列不变；false 不干预暮色森林，可能再次触发多线程崩溃。");
        TWILIGHT_MAZE_THREAD_SAFE_RANDOM = b.define("enabled", true);
        b.pop();

        // §864 Boss 战：参与者检测 → 每个参与者各 roll 一次奖励 + 击杀成就广播给所有参与者（用户口径 ✓）
        b.push("boss_fight").comment(
                "首领战：检测参战人数，结算时每位参与者抽一次首领战利品，并向全部参与者发放击杀进度。",
                "原版抽取计一次，因此额外抽取 N-1 次；额外战利品掉在地上。以 forge:bosses 实体类型标签识别首领。",
                "对首领造成伤害（宠物和投射物计入主人）或受到首领伤害，即算参战。");
        BOSS_FIGHT_ENABLED = b.define("enabled", true);
        BOSS_FIGHT_ROLLS_EQUAL_PARTICIPANTS = b.comment(
                "true（默认）：总抽取次数等于参战人数。false：保留原版行为，只抽一次。")
                .define("rolls_equal_participants", true);
        BOSS_FIGHT_GRANT_ADVANCEMENTS = b.comment(
                "true（默认）：为每位参战者触发 player_killed_entity 进度（例如击杀使徒或末影龙）。false：仅实际击杀者获得。")
                .define("grant_advancements_to_all", true);
        BOSS_FIGHT_MAX_ROLLS = b.comment(
                "单次首领死亡的额外抽取次数上限。0 表示不限（默认）；例如设为 6 则最多额外抽取 6 次。")
                .define("max_extra_rolls", 0);
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

    /** §779 是否给暮色森林迷宫的随机源套同步包装（配置没就绪 ⇒ true） */
    public static boolean twilightMazeThreadSafeRandom() {
        try {
            return TWILIGHT_MAZE_THREAD_SAFE_RANDOM.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** §769 是否拦掉神秘遗物饰品对夜视的移除（配置没就绪 ⇒ true） */
    public static boolean enigmaticCurioKeepsNightVision() {
        try {
            return ENIGMATIC_CURIO_KEEPS_NIGHT_VISION.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** §787 是否做「空标签归一化」（把空 `{}` 剥成无标签；配置没就绪 ⇒ true） */
    public static boolean normalizeItemTag() {
        try {
            return NORMALIZE_ITEM_TAG.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    // ==================== Boss 战取值助手（§864） ====================

    /** Boss 战支持总开关（配置没就绪 ⇒ true） */
    public static boolean bossFightEnabled() {
        try {
            return BOSS_FIGHT_ENABLED.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 掉落总次数 = 参与人数（配置没就绪 ⇒ true） */
    public static boolean bossFightRollsEqualParticipants() {
        try {
            return BOSS_FIGHT_ROLLS_EQUAL_PARTICIPANTS.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 击杀成就广播给所有参与者（配置没就绪 ⇒ true） */
    public static boolean bossFightGrantAdvancements() {
        try {
            return BOSS_FIGHT_GRANT_ADVANCEMENTS.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** 单次 boss 死亡的额外 roll 上限（0 = 不限 ⇒ 返回 0 ✓） */
    public static int bossFightMaxExtraRolls() {
        try {
            return Math.max(0, BOSS_FIGHT_MAX_ROLLS.get());
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
