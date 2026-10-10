package com.mofengbaizhi.tinkersnewlife.content.cursespeech;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家咒言术进度存储：
 * <ul>
 *   <li>已学词条 id（持久数据键 learned）</li>
 *   <li>当前咒言组合：六段 id 数组（持久数据键 chant）</li>
 * </ul>
 * 初始自带：咏叹 啊/呐/哦、敬称 尊敬的、对象 咒之祖巫、祈求 请你/求你、
 * 核心义 降下疗愈/使人虚弱/禁锢行动、结谢语 我赞美您。
 */
public final class CursedSpeechState {

    private CursedSpeechState() {}

    private static final String LEARNED_KEY = "tnl_cursed_learned";
    private static final String CHANT_KEY = "tnl_cursed_chant";

    private static final String[] DEFAULT_CHANT = {
            "ah", "respected", "wuzu", "please", "heal", "praise"
    };
    private static final String[] DEFAULT_LEARNED = {
            // 咏叹
            "ah", "na", "oh",
            // 敬称
            "respected",
            // 对象
            "wuzu",
            // 祈求
            "please", "beg",
            // 核心义
            "heal", "weak", "bind",
            // 结谢语
            "praise"
    };

    /** 首次访问时植入默认学习/组合 */
    private static CompoundTag data(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(LEARNED_KEY)) {
            ListTag list = new ListTag();
            for (String id : DEFAULT_LEARNED) {
                if (CursedSpeechRegistry.exists(id)) {
                    list.add(StringTag.valueOf(id));
                }
            }
            tag.put(LEARNED_KEY, list);
        }
        if (!tag.contains(CHANT_KEY)) {
            ListTag list = new ListTag();
            for (String id : DEFAULT_CHANT) {
                list.add(StringTag.valueOf(id));
            }
            tag.put(CHANT_KEY, list);
        }
        return tag;
    }

    /** 已学词条 id（有序） */
    public static List<String> learned(Player player) {
        ListTag list = data(player).getList(LEARNED_KEY, 8);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String id = list.getString(i);
            if (CursedSpeechRegistry.exists(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 已学某词条？ */
    public static boolean knows(Player player, String id) {
        return learned(player).contains(id);
    }

    /** 学习新词条；返回 true 表示确有新增 */
    public static boolean learn(ServerPlayer player, String id) {
        if (!CursedSpeechRegistry.exists(id)) return false;
        List<String> cur = learned(player);
        if (cur.contains(id)) return false;
        cur.add(id);
        saveLearned(player, cur);
        return true;
    }

    private static void saveLearned(ServerPlayer player, List<String> ids) {
        ListTag list = new ListTag();
        for (String id : ids) {
            if (CursedSpeechRegistry.exists(id)) {
                list.add(StringTag.valueOf(id));
            }
        }
        player.getPersistentData().put(LEARNED_KEY, list);
    }

    /** 当前组合（六段 id，按序）；缺省自动补该段已学第一个 */
    public static String[] chant(Player player) {
        ListTag list = data(player).getList(CHANT_KEY, 8);
        List<String> cur = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            cur.add(list.getString(i));
        }
        String[] out = new String[6];
        CursedSpeechRegistry.Part[] parts = CursedSpeechRegistry.Part.values();
        List<String> learned = learned(player);
        for (int i = 0; i < 6; i++) {
            String id = i < cur.size() ? cur.get(i) : "";
            if (CursedSpeechRegistry.exists(id)) {
                out[i] = id;
            } else {
                // 回退：该段第一个已学词
                CursedSpeechRegistry.Part part = parts[i];
                out[i] = learned.stream()
                        .map(CursedSpeechRegistry::get)
                        .filter(w -> w != null && w.part() == part)
                        .map(CursedSpeechRegistry.Word::id)
                        .findFirst().orElse("");
            }
        }
        return out;
    }

    /** 服务端设置某段组合词 */
    public static void setChantPart(ServerPlayer player, int index, String id) {
        if (index < 0 || index >= 6) return;
        CursedSpeechRegistry.Word w = CursedSpeechRegistry.get(id);
        if (w == null) return;
        if (w.part().ordinal() != index) return; // 段落类别必须匹配
        if (!knows(player, id)) return;
        ListTag list = data(player).getList(CHANT_KEY, 8);
        while (list.size() <= index) {
            list.add(StringTag.valueOf(""));
        }
        list.set(index, StringTag.valueOf(id));
        player.getPersistentData().put(CHANT_KEY, list);
    }
}
