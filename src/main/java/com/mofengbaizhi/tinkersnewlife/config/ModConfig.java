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
