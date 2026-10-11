package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * §1302 咒术系列头衔。
 *
 * <p>显示形态（用户口径）：{@code [头衔][头衔]…玩家名}，**超过 3 枚只显示稀有度最高的前三枚**。
 * 头衔分两类：
 * <ul>
 *   <li><b>动态</b>：咒术师等级（按"咒力上限"分档：5000/1万/2万/5万/10万）与"普通人"（未佩戴核心）；</li>
 *   <li><b>持有</b>：其余头衔由条件写入玩家持久数据（{@link #KEY_TITLES}，一串头衔 id）。</li>
 * </ul>
 * 稀有度：5 传说 / 4 史诗 / 3 稀有 / 2 优秀 / 1 普通（具体分配见 {@link #ALL}）。
 */
public final class CurseTitles {

    public record Title(String id, int rarity) {
    }

    /** 头衔表（★顺序即 id 顺序 ✓ 稀有度按用户口径我给 ✓） */
    public static final List<Title> ALL = List.of(
            new Title("ordinary", 1),
            new Title("grade5", 1),
            new Title("grade4", 1),
            new Title("grade3", 2),
            new Title("grade2", 3),
            new Title("grade1", 4),
            new Title("special", 5),
            new Title("heaven_tyrant", 4),
            new Title("heaven_binding", 4),
            new Title("tool_smith", 3),
            new Title("puppet_master", 3),
            new Title("world_abandoned", 3),
            new Title("enemy_of_all", 2),
            new Title("calamity_bearer", 2),
            new Title("iron_tomb_crisis", 3));

    /** 玩家持久数据里的头衔 id 列表 */
    public static final String KEY_TITLES = "tnl_titles";
    /** §1305 玩家"手动选中要展示"的头衔（★HUD 调整界面里选 ✓ 空 ⇒ 自动取前 3 ✓） */
    public static final String KEY_SELECTED = "tnl_titles_selected";

    public static java.util.List<String> selected(Player player) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            var data = player.getPersistentData();
            if (data.contains(KEY_SELECTED, 9)) {
                var list = data.getList(KEY_SELECTED, 8);
                for (int i = 0; i < list.size(); i++) {
                    out.add(list.getString(i));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 设置选中（★服务端 ✓ 存持久数据 ＋ 回发同步 ✓）；最多 3 枚 ✓ */
    public static void setSelection(Player player, java.util.List<String> ids) {
        try {
            ListTag list = new ListTag();
            int n = 0;
            for (String id : ids) {
                if (rarityOf(id) == 0 || !active(player).contains(id)) {
                    continue;
                }
                list.add(StringTag.valueOf(id));
                if (++n >= MAX_SHOWN) {
                    break;
                }
            }
            player.getPersistentData().put(KEY_SELECTED, list);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sync(sp);
            }
        } catch (Throwable ignored) {
        }
    }

    /** §1308 "当前实际显示"的头衔 id（★选中优先 ✗ 否则自动前 3 ✓）⇒ 界面据此预勾 ✓ */
    public static List<String> shownIds(Player player) {
        List<String> chosen = selected(player);
        if (!chosen.isEmpty()) {
            return chosen;
        }
        List<String> ids = active(player);
        ids.sort(Comparator.comparingInt(CurseTitles::rarityOf).reversed());
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (out.size() >= MAX_SHOWN) {
                break;
            }
            out.add(id);
        }
        return out;
    }
    /** §1325 持有列表按稀有度降序（★界面顺序更好看 ✗ ⭐ 同稀有度按表序 ✓） */
    /** §1327 当前**生效**的全部头衔（★持有 ＋ ⭐ 动态：等级／普通人／咒骸师 ✓）按稀有度降序 ⇒ 选择界面列全 ✓ */
    public static List<String> sortedActive(Player player) {
        List<String> ids = active(player);
        ids.sort(Comparator.comparingInt(CurseTitles::rarityOf).reversed());
        return ids;
    }

    public static List<String> sortedHeld(Player player) {
        List<String> ids = held(player);
        ids.sort(Comparator.comparingInt(CurseTitles::rarityOf).reversed());
        return ids;
    }
    /** 把"持有 ＋ 选中"同步给客户端（★选择界面要用 ✓） */
    public static void sync(net.minecraft.server.level.ServerPlayer player) {
        try {
            TinkersNewlife.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.mofengbaizhi.tinkersnewlife.network.curse.PacketTitlesSync(sortedActive(player), shownIds(player)));   // §1327 送"当前生效的全部"（★含动态 ✓）
        } catch (Throwable ignored) {
        }
    }
    /** 最多显示几枚 */
    public static final int MAX_SHOWN = 3;

    private CurseTitles() {
    }

    public static int rarityOf(String id) {
        for (Title t : ALL) {
            if (t.id().equals(id)) {
                return t.rarity();
            }
        }
        return 0;
    }

    /** §1328 稀有度 ⇒ 颜色（★5 传说金 ✗ ⭐ 4 史诗紫 ✗ ⭐ 3 稀有蓝 ✗ ⭐ 2 优秀绿 ✗ ⭐ 1 普通白 ✓） */
    public static net.minecraft.ChatFormatting colorOf(String id) {
        return switch (rarityOf(id)) {
            case 5 -> net.minecraft.ChatFormatting.GOLD;
            case 4 -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
            case 3 -> net.minecraft.ChatFormatting.AQUA;
            case 2 -> net.minecraft.ChatFormatting.GREEN;
            default -> net.minecraft.ChatFormatting.WHITE;
        };
    }

    /** §1328 按稀有度上色的头衔组件（★名字前缀用 ✓ 每个头衔各自颜色 ✓） */
    public static net.minecraft.network.chat.Component colored(String id) {
        return Component.translatable("title.tinkersnewlife." + id).withStyle(colorOf(id));
    }

    /** §1328 上色的名字前缀（★[金][紫][蓝]玩家名 ✓） */
    public static net.minecraft.network.chat.Component prefixComponent(Player player) {
        try {
            List<String> ids = shownByRarity(player);
            net.minecraft.network.chat.MutableComponent out = Component.empty();
            for (String id : ids) {
                out.append(Component.literal("[").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                out.append(colored(id));
                out.append(Component.literal("]").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            return out;
        } catch (Throwable t) {
            return Component.empty();
        }
    }

    /** §1328 当前要显示的头衔（★选中优先 ✗ ⭐ 否则自动前 3 ✓）—— 顺序已按稀有度降序 ✓ */
    public static List<String> shownByRarity(Player player) {
        try {
            List<String> chosen = selected(player);
            List<String> nowActive = active(player);
            List<String> ids = new ArrayList<>();
            if (chosen.isEmpty()) {
                ids.addAll(nowActive);
            } else {
                for (String id : chosen) {
                    if (nowActive.contains(id)) {
                        ids.add(id);
                    }
                }
            }
            ids.sort(Comparator.comparingInt(CurseTitles::rarityOf).reversed());
            if (ids.size() > MAX_SHOWN) {
                ids = new ArrayList<>(ids.subList(0, MAX_SHOWN));
            }
            return ids;
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }
    /* ------------------------------------------------------------ 持有（存） */

    public static List<String> held(Player player) {
        List<String> out = new ArrayList<>();
        try {
            var data = player.getPersistentData();
            if (data.contains(KEY_TITLES, 9)) {
                ListTag list = data.getList(KEY_TITLES, 8);
                for (int i = 0; i < list.size(); i++) {
                    out.add(list.getString(i));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void grant(Player player, String id) {
        try {
            if (rarityOf(id) == 0) {
                return;
            }
            List<String> cur = held(player);
            if (cur.contains(id)) {
                return;
            }
            cur.add(id);
            save(player, cur);
        } catch (Throwable ignored) {
        }
    }

    public static void revoke(Player player, String id) {
        try {
            List<String> cur = held(player);
            if (cur.remove(id)) {
                save(player, cur);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void save(Player player, List<String> ids) {
        ListTag list = new ListTag();
        for (String id : ids) {
            list.add(StringTag.valueOf(id));
        }
        player.getPersistentData().put(KEY_TITLES, list);
    }

    /* ------------------------------------------------------------ 动态头衔 */

    /** 咒力上限：总量 × (咒力输出 + 亲和/10) × 500（与工具提示里那条口径一致） */
    public static double maxCurse(Player player) {
        try {
            int total = CursePowerHelper.getCurseTotalLevel(player);
            int output = CursePowerHelper.getCurseOutputLevel(player);
            int affinity = CursePowerHelper.getCurseAffinity(player);
            return total * (output + affinity / 10.0) * 500.0;
        } catch (Throwable t) {
            return 0.0D;
        }
    }

    /** 咒术师等级（★按用户给的分档 ✓）；未佩戴核心 ⇒ ordinary ✓ */
    public static String rankId(Player player) {
        try {
            if (CursePowerHelper.findEquippedCurseCore(player).isEmpty()) {
                return "ordinary";
            }
        } catch (Throwable ignored) {
        }
        double max = maxCurse(player);
        if (max < 5000) {
            return "grade5";
        }
        if (max < 10000) {
            return "grade4";
        }
        if (max < 20000) {
            return "grade3";
        }
        if (max < 50000) {
            return "grade2";
        }
        if (max < 100000) {
            return "grade1";
        }
        return "special";
    }

    /* ------------------------------------------------------------ 显示 */

    /** 所有"当前生效"的头衔 id（★动态 ＋ 持有 ✓） */
    public static List<String> active(Player player) {
        List<String> out = new ArrayList<>(held(player));
        // §1303 动态：装备的核心上带"傀儡操术" ⇒ 咒骸师 ✓
        try {
            var core = CursePowerHelper.findEquippedCurseCore(player);
            if (!core.isEmpty() && CursePowerHelper.getModifierLevel(core,
                    com.mofengbaizhi.tinkersnewlife.content.Modifiers.PUPPET.getId()) > 0
                    && !out.contains("puppet_master")) {
                out.add("puppet_master");
            }
        } catch (Throwable ignored) {
        }
        String rank = rankId(player);
        if (rank != null && !out.contains(rank)) {
            out.add(rank);
        }
        return out;
    }

    /** {@code [头衔][头衔]…}；超过 3 枚取稀有度最高的 3 枚（★同稀有度按头衔表顺序 ✓） */
    public static String prefix(Player player) {
        try {
            List<String> chosen = selected(player);
            List<String> nowActive = active(player);
            List<String> ids = new ArrayList<>();
            if (chosen.isEmpty()) {
                ids.addAll(nowActive);
            } else {
                for (String id : chosen) {
                    if (nowActive.contains(id)) {
                        ids.add(id);
                    }
                }
            }
            ids.sort(Comparator.comparingInt(CurseTitles::rarityOf).reversed());
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (String id : ids) {
                if (shown >= MAX_SHOWN) {
                    break;
                }
                sb.append('[').append(Component.translatable("title.tinkersnewlife." + id).getString()).append(']');
                shown++;
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** §1303 扫已佩戴饰品：七咒之戒 ⇒ 举世皆敌（★id 由神秘遗物语言文件反查确认 ✓） */
    public static void scanWorn(Player player) {
        try {
            var resolved = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (resolved.isEmpty()) {
                return;
            }
            for (var handler : resolved.get().getCurios().values()) {
                var stacks = handler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    var s = stacks.getStackInSlot(i);
                    if (s.isEmpty()) {
                        continue;
                    }
                    var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
                    String key = id == null ? "" : id.toString();
                    // §1304 联动物品 ⇒ 头衔（★id 全部由各 mod 语言文件反查 ✓）
                    if ("enigmaticlegacy:cursed_ring".equals(key)) {
                        grant(player, "enemy_of_all");        // 七咒之戒（神秘遗物）
                    } else if ("celestial_artifacts:catastrophe_scroll".equals(key)) {
                        grant(player, "calamity_bearer");     // 灾厄之册（星月遗物）
                    } else if ("flame_chase_artifacts:hatred_inundate".equals(key)) {
                        grant(player, "iron_tomb_crisis");    // 流溢之恨（星穹饰品）
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onNameFormat(PlayerEvent.NameFormat event) {
            try {
                String pre = prefix(event.getEntity());
                if (!pre.isEmpty()) {
                    event.setDisplayname(Component.literal(pre).append(event.getDisplayname()));
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
