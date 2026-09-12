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

    // ==================== 无为转变 伪装渲染 ====================
    /** 无为转变·伪装渲染替换（客户端）：把变形玩家渲染成目标生物。与 YSM 等接管玩家渲染的模组冲突时可关闭 */
    public static final ConfigValue<Boolean> WUWEI_DISGUISE_RENDER;

    // ==================== 飞剑流光拖尾 ====================
    /** 飞剑流光拖尾（客户端）：动态条带 + 自写流光着色器 */
    public static final ConfigValue<Boolean> FLYING_SWORD_TRAIL;

    // ==================== 咒术 HUD（咒力进度条） ====================
    // 说明：HUD 的位置/宽度由游戏内拖动界面写入独立文件 config/mofengbaizhi/curse_hud.json
    //       （见 client/hud/CurseHudConfig），不放这里以免与主配置的写回时机打架。

    // ==================== 构筑术式（拟造） ====================
    /** 拟造费用倍率（默认 10.0 = 原价的 10 倍） */
    public static final ConfigValue<Double> CONSTRUCT_COST_MULTIPLIER;
    /** 拟造黑名单：禁止出现在构筑列表里的物品（支持 mod / 物品 / 标签 / 配方类型） */
    public static final ConfigValue<List<? extends String>> CONSTRUCT_BLACKLIST;
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

        // 无为转变·伪装渲染替换（客户端）
        b.push("wuwei_disguise").comment(
                "Client side: render a transformed (Wu Wei) player as the target creature instead of the player model.",
                "Set enable_disguise_render=false if another mod that takes over player rendering",
                "(e.g. YSM / Yes Steve Model) conflicts with the disguise.");
        WUWEI_DISGUISE_RENDER = b.define("enable_disguise_render", true);
        b.pop();

        // 构筑术式（拟造）：费用倍率 + 黑名单
        b.push("construct").comment(
                "Construct technique (Wu Wei 'construct' / fabricate items from recipes).",
                "",
                "cost_multiplier = final curse cost multiplier. 1.0 = original formula, 10.0 = 10x cost (default).",
                "",
                "use_default_blacklist = built-in list: all ores / raw materials / ingots / nuggets / dusts / gems / shards",
                "                          (#forge:ores, #forge:raw_materials, #forge:ingots, #forge:nuggets,",
                "                           #forge:dusts, #forge:gems, #c:* equivalents, and *:*_shard / *:*_dust / *:*_nugget globs).",
                "",
                "magic_item_bonus = extra value score for high-function magic items (spell scrolls, focuses, spellbooks,",
                "                   runes, wands...). They have no attack/armor/durability so the normal formula prices them",
                "                   like dirt. 0 disables the check. Detected by tags (#curios:scroll, #curios:spellbook,",
                "                   #curios:spellstone, #irons_spellbooks:school_focus, #irons_spellbooks:inscribed_rune)",
                "                   or by class/interface name keywords (scroll/focus/spellbook/rune/charm/wand/staff/...).",
                "magic_extra_keywords / magic_extra_tags = your own additions.",
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
        CONSTRUCT_BLACKLIST = b.defineList("blacklist", new java.util.ArrayList<String>(),
                o -> o instanceof String);
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
        b.push("domains").comment("Per-domain formula scale multipliers (1.0 = vanilla values).");
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
}
