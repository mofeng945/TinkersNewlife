package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LifeLampRingItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 七咒之戒的**第一诅咒**在"命灯指轮"面前的提示改写。
 *
 * <p>戴着命灯指轮看七咒之戒时，第一条诅咒（{@code tooltip.enigmaticlegacy.cursedRing4}：
 * "使受到的<b>任何</b>来源的伤害加倍"）会被划掉、变灰，并在后面补一句绿色的
 * "但慈悲使你的身躯变得无比坚韧" —— 与真实的数值行为一致（见 {@code LifeLampRingHandler}：
 * 它会在 HIGHEST 记原值、LOWEST 还原，把这条诅咒抵消掉）。
 *
 * <p>实现要点：那一行的文本里带着 § 颜色代码（EL 自己写的），直接加样式会被 § 覆盖，
 * 所以先把 § 代码**剥掉**再用自己的样式重建，颜色/删除线才真的生效。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CursedRingTooltipHandler {

    private CursedRingTooltipHandler() {
    }

    /** EL 的七咒之戒 */
    private static final String CURSED_RING = "enigmaticlegacy:cursed_ring";
    /** EL 第一诅咒那一行的 lang 键 */
    private static final String FIRST_CURSE_KEY = "tooltip.enigmaticlegacy.cursedRing4";

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!isCursedRing(event.getItemStack())) return;
        if (!LifeLampRingItem.isWorn(player)) return;      // 没戴命灯指轮 → 提示照旧
        // 「心」恶意 ≥20% ⇒ 命灯指轮的七咒解除**已被阻塞** ✓ ⇒ 这里也不能再改写提示 ✓
        // 一律用**客户端镜像**读善恶值 ✓（权威值在服务端持久数据 ✗ 客户端拿不到 ✓ 见 mirrorAlignment ✓）
        if (com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler.mirrorAlignment(player)
                <= com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceThresholdHandler.LAMP_AT) return;

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
