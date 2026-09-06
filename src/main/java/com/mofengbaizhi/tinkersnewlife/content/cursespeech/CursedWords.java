package com.mofengbaizhi.tinkersnewlife.content.cursespeech;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 咒言术元素注册表（共享：服务端权威判定 / 客户端展示与编辑共用）。
 * <p>
 * 咒言结构：咏叹词 ， 敬称+对象 ， 祈求语+核心义 ， 结谢语 ！
 * 六类元素各自带稀有度与数值档位：
 * <ul>
 *   <li>咏叹词 costMul —— 咒力消耗倍率（越稀有越省）</li>
 *   <li>敬称   powerMul —— 效果强度倍率（越稀有越强）</li>
 *   <li>对象   objectId —— 附带效果（含只能配不可名状敬称的约束）</li>
 *   <li>祈求语 durMul  —— 效果持续时间倍率</li>
 *   <li>核心义 effectId —— 咒言主效果</li>
 *   <li>结谢语 backMul —— 反噬削减倍率（越稀有削得越多）</li>
 * </ul>
 */
public final class CursedWords {

    private CursedWords() {}

    /** 稀有度：0 常见 … 5 传说（残卷刷新权重 = 7 - rarity） */
    public enum Category { EXCLAMATION, HONORIFIC, TARGET, PRAYER, CORE, THANKS }

    /** 单个咒言元素 */
    public record Element(String id, Category category, int rarity,
                          double value, String objectId, String langKey) {
        public boolean usableWith(Element honorific) {
            // 仅 奈亚/莎布/犹格 要求必须配「不可名状的」敬称
            if (objectId == null) return true;
            return switch (objectId) {
                case "nya", "shub", "yog" -> honorific != null && honorific.id.equals("unnameable");
                default -> true;
            };
        }
    }

    /** 对象附带效果 id（施法时按此分派） */
    public static final String OBJ_WUZU = "wuzu";       // 咒之祖巫：提升效果强度
    public static final String OBJ_IDIOT = "idiot";     // 智力残缺大哥哥：反噬增强
    public static final String OBJ_CTHULHU = "cthulhu"; // 克图露：天体秩序之音
    public static final String OBJ_ELYSIA = "elysia";   // 爱莉希雅：霜冻+自疗
    public static final String OBJ_NYA = "nya";         // 奈亚拉托提普
    public static final String OBJ_SHUB = "shub";       // 莎布尼古拉斯
    public static final String OBJ_YOG = "yog";         // 犹格索托斯

    /** 核心义效果 id */
    public static final String FX_HEAL = "heal";
    public static final String FX_WEAK = "weak";
    public static final String FX_BIND = "bind";
    public static final String FX_BLIND = "blind";
    public static final String FX_BURN = "burn";
    public static final String FX_SICK = "sick";
    public static final String FX_FREEZE = "freeze";
    public static final String FX_THUNDER = "thunder";
    public static final String FX_ATTACK = "attack";
    public static final String FX_KNOCK = "knock";

    // ============ 元素表 ============

    private static final Map<String, Element> ALL = new LinkedHashMap<>();

    private static void reg(String id, Category c, int rarity, double value, String objectId, String key) {
        ALL.put(id, new Element(id, c, rarity, value, objectId, key));
    }

    static {
        // 咏叹词（costMul：越大越耗咒力；越稀有越省）
        reg("zako",     Category.EXCLAMATION, 0, 1.6, null, "word.tinkersnewlife.zako");      // 雑鱼
        reg("ma",       Category.EXCLAMATION, 1, 1.4, null, "word.tinkersnewlife.ma");        // 嘛
        reg("ah",       Category.EXCLAMATION, 1, 1.2, null, "word.tinkersnewlife.ah");        // 啊
        reg("na",       Category.EXCLAMATION, 2, 1.0, null, "word.tinkersnewlife.na");        // 呐
        reg("oh",       Category.EXCLAMATION, 3, 0.85, null, "word.tinkersnewlife.oh");       // 哦
        reg("ahya",     Category.EXCLAMATION, 4, 0.7, null, "word.tinkersnewlife.ahya");      // 啊呀

        // 敬称（powerMul：效果强度）
        reg("stupid",    Category.HONORIFIC, 0, 0.6, null, "word.tinkersnewlife.stupid");     // 愚蠢的
        reg("respected", Category.HONORIFIC, 1, 1.0, null, "word.tinkersnewlife.respected");  // 尊敬的
        reg("noble",     Category.HONORIFIC, 2, 1.3, null, "word.tinkersnewlife.noble");      // 崇高的
        reg("supreme",   Category.HONORIFIC, 3, 1.6, null, "word.tinkersnewlife.supreme");    // 无上的
        reg("flawless",  Category.HONORIFIC, 4, 2.0, null, "word.tinkersnewlife.flawless");   // 无瑕的
        reg("revered",   Category.HONORIFIC, 4, 2.4, null, "word.tinkersnewlife.revered");    // 敬颂的
        reg("unnameable",Category.HONORIFIC, 5, 3.0, null, "word.tinkersnewlife.unnameable"); // 不可名状的

        // 对象
        reg("wuzu",    Category.TARGET, 1, 1.0, OBJ_WUZU,    "word.tinkersnewlife.wuzu");
        reg("idiot",   Category.TARGET, 1, 1.0, OBJ_IDIOT,   "word.tinkersnewlife.idiot");
        reg("cthulhu", Category.TARGET, 2, 1.0, OBJ_CTHULHU, "word.tinkersnewlife.cthulhu");
        reg("elysia",  Category.TARGET, 2, 1.0, OBJ_ELYSIA,  "word.tinkersnewlife.elysia");
        reg("nya",     Category.TARGET, 4, 1.0, OBJ_NYA,     "word.tinkersnewlife.nya");
        reg("shub",    Category.TARGET, 5, 1.0, OBJ_SHUB,    "word.tinkersnewlife.shub");
        reg("yog",     Category.TARGET, 5, 1.0, OBJ_YOG,     "word.tinkersnewlife.yog");

        // 祈求语（durMul：持续时间倍率）
        reg("order",    Category.PRAYER, 0, 0.5, null, "word.tinkersnewlife.order");    // 命令你
        reg("please",   Category.PRAYER, 1, 0.7, null, "word.tinkersnewlife.please");   // 请你
        reg("beg",      Category.PRAYER, 1, 0.85, null, "word.tinkersnewlife.beg");     // 求你
        reg("please2",  Category.PRAYER, 2, 1.0, null, "word.tinkersnewlife.please2");  // 请您
        reg("beg2",     Category.PRAYER, 3, 1.3, null, "word.tinkersnewlife.beg2");     // 求您
        reg("pray2",    Category.PRAYER, 4, 1.6, null, "word.tinkersnewlife.pray2");    // 祈求您
        reg("worship2", Category.PRAYER, 5, 2.0, null, "word.tinkersnewlife.worship2"); // 拜求您

        // 核心义
        reg("heal",     Category.CORE, 1, 1.0, FX_HEAL,     "word.tinkersnewlife.heal");
        reg("weak",     Category.CORE, 1, 1.0, FX_WEAK,     "word.tinkersnewlife.weak");
        reg("bind",     Category.CORE, 2, 1.0, FX_BIND,     "word.tinkersnewlife.bind");
        reg("blind",    Category.CORE, 2, 1.0, FX_BLIND,    "word.tinkersnewlife.blind");
        reg("burn",     Category.CORE, 2, 1.0, FX_BURN,     "word.tinkersnewlife.burn");
        reg("sick",     Category.CORE, 2, 1.0, FX_SICK,     "word.tinkersnewlife.sick");
        reg("freeze",   Category.CORE, 3, 1.0, FX_FREEZE,   "word.tinkersnewlife.freeze");
        reg("thunder",  Category.CORE, 3, 1.0, FX_THUNDER,  "word.tinkersnewlife.thunder");
        reg("attack",   Category.CORE, 3, 1.0, FX_ATTACK,   "word.tinkersnewlife.attack");
        reg("knock",    Category.CORE, 3, 1.0, FX_KNOCK,    "word.tinkersnewlife.knock");

        // 结谢语（backMul：反噬倍率，越小削得越多）
        reg("reward",   Category.THANKS, 0, 1.0, null, "word.tinkersnewlife.reward");    // 这是本小姐的赏赐
        reg("praise",   Category.THANKS, 1, 0.8, null, "word.tinkersnewlife.praise");    // 我赞美您
        reg("grateful", Category.THANKS, 2, 0.65, null, "word.tinkersnewlife.grateful"); // 无比的感谢您
        reg("faith",    Category.THANKS, 4, 0.5, null, "word.tinkersnewlife.faith");     // 我永远信仰您
        reg("devote",   Category.THANKS, 5, 0.35, null, "word.tinkersnewlife.devote");    // 奉献一切与您
    }

    public static Element get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static List<Element> of(Category category) {
        List<Element> list = new ArrayList<>();
        for (Element e : ALL.values()) {
            if (e.category() == category) list.add(e);
        }
        return list;
    }

    public static boolean exists(String id) {
        return id != null && ALL.containsKey(id);
    }
}
