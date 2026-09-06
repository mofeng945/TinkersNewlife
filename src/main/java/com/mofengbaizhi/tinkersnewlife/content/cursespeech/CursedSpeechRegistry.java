package com.mofengbaizhi.tinkersnewlife.content.cursespeech;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 咒言术词库（服务端+客户端共享定义）。
 * <p>
 * 每句咒言由 6 段组成：咏叹词 + 敬称 + 对象 + 祈求语 + 核心义 + 结谢语。
 * 段落按稀有度分级：rarity 0..5，越高越稀有（残卷开出概率越低）。
 */
public final class CursedSpeechRegistry {

    private CursedSpeechRegistry() {}

    public enum Part { EXCLAMATION, HONORIFIC, TARGET, PRAYER, CORE, THANKS }

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

    /** 对象附带效果 id */
    public static final String OBJ_WUZU = "wuzu";
    public static final String OBJ_IDIOT = "idiot";
    public static final String OBJ_CTHULHU = "cthulhu";
    public static final String OBJ_ELYSIA = "elysia";
    public static final String OBJ_NYA = "nya";
    public static final String OBJ_SHUB = "shub";
    public static final String OBJ_YOG = "yog";

    /** 一段咒言词条 */
    public record Word(String id, Part part, int rarity, String langKey) {}

    private static final Map<String, Word> ALL = new LinkedHashMap<>();
    private static final Map<Part, List<Word>> BY_PART = new LinkedHashMap<>();

    private static void reg(String id, Part part, int rarity) {
        Word w = new Word(id, part, rarity, "word.tinkersnewlife." + id);
        ALL.put(id, w);
        BY_PART.computeIfAbsent(part, p -> new ArrayList<>()).add(w);
    }

    static {
        // 咏叹词（消耗排序：雑鱼>嘛>啊>呐>哦>啊呀 → rarity 递增、消耗系数递减）
        reg("zako",  Part.EXCLAMATION, 0); // 雑鱼
        reg("ma",    Part.EXCLAMATION, 1); // 嘛
        reg("ah",    Part.EXCLAMATION, 2); // 啊（初始）
        reg("na",    Part.EXCLAMATION, 3); // 呐（初始）
        reg("oh",    Part.EXCLAMATION, 4); // 哦（初始）
        reg("ahya",  Part.EXCLAMATION, 5); // 啊呀
        // 敬称（强度排序：愚蠢的<尊敬的<崇高的<无上的<无瑕的<敬颂的<不可名状的）
        reg("stupid",    Part.HONORIFIC, 0);
        reg("respected", Part.HONORIFIC, 1); // 尊敬的（初始）
        reg("noble",     Part.HONORIFIC, 2); // 崇高的
        reg("supreme",   Part.HONORIFIC, 3); // 无上的
        reg("flawless",  Part.HONORIFIC, 4); // 无瑕的
        reg("hallowed",  Part.HONORIFIC, 5); // 敬颂的
        reg("unnameable",Part.HONORIFIC, 5); // 不可名状的
        // 对象
        reg("wuzu",  Part.TARGET, 0); // 咒之祖巫（初始）
        reg("idiot", Part.TARGET, 0); // 智力残缺大哥哥
        reg("cthulhu", Part.TARGET, 2); // 克图露
        reg("elysia",  Part.TARGET, 2); // 爱莉希雅
        reg("nya",     Part.TARGET, 5); // 奈亚拉托提普（仅不可名状敬称）
        reg("shub",    Part.TARGET, 5); // 莎布尼古拉斯（仅不可名状敬称）
        reg("yog",     Part.TARGET, 5); // 犹格索托斯（仅不可名状敬称）
        // 祈求语（时长排序：命令你<请你<求你<请您<求您<祈求您<拜求您）
        reg("order",    Part.PRAYER, 0); // 命令你
        reg("please",   Part.PRAYER, 1); // 请你（初始）
        reg("beg",      Part.PRAYER, 1); // 求你（初始）
        reg("please2",  Part.PRAYER, 2); // 请您
        reg("beg2",     Part.PRAYER, 3); // 求您
        reg("implore",  Part.PRAYER, 4); // 祈求您
        reg("worship",  Part.PRAYER, 5); // 拜求您
        // 核心义
        reg("heal",     Part.CORE, 0); // 降下疗愈（初始）
        reg("weak",     Part.CORE, 1); // 使人虚弱（初始）
        reg("bind",     Part.CORE, 2); // 禁锢行动（初始）
        reg("blind",    Part.CORE, 2); // 附加失明
        reg("burn",     Part.CORE, 2); // 点燃他
        reg("sick",     Part.CORE, 2); // 反胃他
        reg("freeze",   Part.CORE, 3); // 冻结他
        reg("thunder",  Part.CORE, 4); // 降下雷霆
        reg("attack",   Part.CORE, 4); // 攻击他
        reg("knock",    Part.CORE, 3); // 甩飞他
        // 结谢语（反噬削减排序：这是本小姐的赏赐<我赞美您<无比的感谢您<我永远信仰您<奉献一切与您）
        reg("reward",  Part.THANKS, 0); // 这是本小姐的赏赐
        reg("praise",  Part.THANKS, 1); // 我赞美您（初始）
        reg("thanks",  Part.THANKS, 2); // 无比的感谢您
        reg("faith",   Part.THANKS, 4); // 我永远信仰您
        reg("devote",  Part.THANKS, 5); // 奉献一切与您
    }

    public static Word get(String id) {
        return ALL.get(id);
    }

    public static List<Word> all() {
        return new ArrayList<>(ALL.values());
    }

    public static List<Word> of(Part part) {
        return new ArrayList<>(BY_PART.getOrDefault(part, List.of()));
    }

    public static boolean exists(String id) {
        return ALL.containsKey(id);
    }
}
