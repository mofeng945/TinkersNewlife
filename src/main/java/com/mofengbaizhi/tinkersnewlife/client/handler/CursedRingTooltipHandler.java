package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LifeLampRingItem;
import com.mofengbaizhi.tinkersnewlife.content.modifier.BeyondDimensionTrait;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;

/**
 * 七咒之戒的提示改写（客户端 ✓）。
 *
 * <h2>① 命灯指轮（原有 ✓ 未动 ✗）</h2>
 * 戴着命灯指轮看七咒之戒时，**第一诅咒**（{@code tooltip.enigmaticlegacy.cursedRing4}：
 * "使受到的任何来源的伤害加倍"）会被划掉、变灰 ✓ 并在后面补一句绿色文案 ✓ ——
 * 与真实数值行为一致 ✓（见 {@code LifeLampRingHandler}：**HIGHEST 记原值 / LOWEST 还原** ✓）。
 *
 * <h2>② ⭐ §1118y 新增：超越维度 ≥4 ⇒ 七咒全免的提示（用户口径 ✓）</h2>
 * 「全身盔甲的总超越维度等级 **≥ 4** 时 ⇒ 七咒之戒所有诅咒效果被免除 ✓ 且工具提示将被**划掉**
 * 并在后面**追加新的粉色文本**」✓ —— 七行与用户的对照表一一对应 ✓
 * （{@code cursedRing4}~{@code cursedRing10} ✓ 共 **7** 条 ✓ 与"七个诅咒"吻合 ✓）。<br>
 * ⚠ 客户端判定 ✓：**直接扫自己身上穿的护甲** ✓（客户端本来就知道自己的装备 ✓）
 * ⇒ **不需要网络同步** ✓ 也不用读服务端数据 ✓。
 *
 * <h2>实现要点（照原有代码的口径 ✓）</h2>
 * EL 那一行的文本里带着 **§ 颜色代码** ✓，直接加样式会被它覆盖 ✗ ⇒ 先把 § 代码**剥掉** ✓
 * 再按自己的样式重建 ✓（灰＋删除线 ✓）✓；替换文本用**粉色**（`LIGHT_PURPLE` ✓）✓。
 * ⚠ 两条含 {@code %1$s} 的行（`cursedRing6` 护甲效力 / `cursedRing7` 对怪伤害 ✓）
 * **不能按整句比对** ✗（游戏里那个值是代入后的数字 ✓）⇒ 按**参数前的部分**做前缀匹配 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CursedRingTooltipHandler {

    private CursedRingTooltipHandler() {
    }

    /** EL 的七咒之戒 */
    private static final String CURSED_RING = "enigmaticlegacy:cursed_ring";
    /** EL 第一诅咒那一行的 lang 键（命灯指轮用 ✓） */
    private static final String FIRST_CURSE_KEY = "tooltip.enigmaticlegacy.cursedRing4";

    /** 本模组"超越维度"特性 id ✓（与 {@code Modifiers} 一致 ✓） */
    private static final ModifierId DIMENSION_ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, BeyondDimensionTrait.ID));

    /**
     * ⭐ 七咒七行 ↔ 免除后的粉色文本（用户给的对照 ✓ 逐条对应 ✓）。
     * <p>{@code {EL 的键, 我们的替换键}} ✓
     */
    private static final String[][] WAIVE_LINES = {
            {"tooltip.enigmaticlegacy.cursedRing4", "modifier.tinkersnewlife.transcendent_dimension.waive1"},
            {"tooltip.enigmaticlegacy.cursedRing5", "modifier.tinkersnewlife.transcendent_dimension.waive2"},
            {"tooltip.enigmaticlegacy.cursedRing6", "modifier.tinkersnewlife.transcendent_dimension.waive3"},
            {"tooltip.enigmaticlegacy.cursedRing7", "modifier.tinkersnewlife.transcendent_dimension.waive4"},
            {"tooltip.enigmaticlegacy.cursedRing8", "modifier.tinkersnewlife.transcendent_dimension.waive5"},
            {"tooltip.enigmaticlegacy.cursedRing9", "modifier.tinkersnewlife.transcendent_dimension.waive6"},
            {"tooltip.enigmaticlegacy.cursedRing10", "modifier.tinkersnewlife.transcendent_dimension.waive7"}
    };

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!isCursedRing(event.getItemStack())) return;

        // ---- ① 命灯指轮：只改写"第一诅咒"一行（原有行为 ✓ 不变 ✗）----
        if (LifeLampRingItem.isWorn(player)) {
            // 「心」恶意 ≥20% ⇒ 命灯指轮的七咒解除**已被阻塞** ✓ ⇒ 这里也不能再改写提示 ✓
            // （一律用**客户端镜像**读善恶值 ✓ 权威值在服务端持久数据 ✗ 客户端拿不到 ✓）
            if (com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler.mirrorAlignment(player)
                    > com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceThresholdHandler.LAMP_AT) {
                String expected = strip(Component.translatable(FIRST_CURSE_KEY).getString());
                List<Component> lines = event.getToolTip();
                for (int i = 0; i < lines.size(); i++) {
                    if (!strip(lines.get(i).getString()).equals(expected)) continue;
                    MutableComponent struck = Component.literal(strip(lines.get(i).getString()))
                            .withStyle(s -> s.applyFormat(ChatFormatting.DARK_GRAY).withStrikethrough(true));
                    MutableComponent blessing = Component.translatable("item.tinkersnewlife.life_lamp_ring.blessing")
                            .withStyle(s -> s.applyFormat(ChatFormatting.GREEN).withStrikethrough(false));
                    lines.set(i, struck.append(blessing));
                    return;
                }
            }
            return;   // 戴了命灯就不再做"超越维度"的全免改写 ✓（免得两套改写打架 ✗）
        }

        // ---- ② ⭐ 超越维度 ≥4：七咒全划掉 ＋ 追加粉色 ✓（用户口径 ✓）----
        if (totalDimensionLevel(player) < BeyondDimensionTrait.WAIVER_TOTAL_LEVEL) return;
        List<Component> lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            String text = strip(lines.get(i).getString());
            if (text.isEmpty()) continue;
            for (String[] pair : WAIVE_LINES) {
                String base = strip(Component.translatable(pair[0]).getString());
                if (base.isEmpty()) continue;
                int fmt = base.indexOf('%');
                String head = fmt > 0 ? base.substring(0, fmt).trim() : base;
                boolean hit = text.equals(base) || (fmt > 0 && text.startsWith(head));
                if (!hit) continue;
                MutableComponent struck = Component.literal(text)
                        .withStyle(s -> s.applyFormat(ChatFormatting.DARK_GRAY).withStrikethrough(true));
                MutableComponent replaced = Component.translatable(pair[1])
                        .withStyle(s -> s.applyFormat(ChatFormatting.LIGHT_PURPLE).withStrikethrough(false));
                lines.set(i, struck.append(replaced));
                break;
            }
        }
    }

    /** 客户端扫自己穿的护甲 ✓ 累加"超越维度"等级 ✓（≥4 即可全免 ✓ 用户口径 ✓） */
    private static int totalDimensionLevel(Player player) {
        int total = 0;
        try {
            for (ItemStack stack : player.getArmorSlots()) {
                if (stack.isEmpty()) continue;
                total += ToolHelper.getActiveModifierLevel(ToolStack.from(stack), DIMENSION_ID);
            }
        } catch (Throwable ignored) {
            return 0;
        }
        return total;
    }

    private static boolean isCursedRing(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem().builtInRegistryHolder().key().location().toString().equals(CURSED_RING);
    }

    /** 去掉 § 颜色代码：否则它会覆盖我们自己加的灰色/删除线 */
    private static String strip(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "").trim();
    }
}
