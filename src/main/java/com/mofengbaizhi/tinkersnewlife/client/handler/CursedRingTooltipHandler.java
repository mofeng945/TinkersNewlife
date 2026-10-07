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
 * <h2>① 命灯指轮（原有 ✓）</h2>
 * 戴着命灯指轮看七咒之戒时，**第一诅咒**（{@code tooltip.enigmaticlegacy.cursedRing4}）会被划掉、变灰 ✓
 * 并在后面补一句绿色文案 ✓（`item.tinkersnewlife.life_lamp_ring.blessing` ✓）。
 *
 * <h2>② ⭐ 超越维度 ≥4 ⇒ 七咒全免的提示（用户口径 ✓）</h2>
 * 「全身盔甲的总超越维度等级 **≥ 4** ⇒ 七咒之戒所有诅咒效果被免除 ✓ 且提示被划掉 ＋ 追加粉色文本」✓。
 *
 * <h2>⚠⚠ 本次修的 bug（用户实测 ✓「带上指环之后修改的工具提示被覆盖了」✓）</h2>
 * 原先①那段结尾有个 {@code return;} ✗ ⇒ ⚠ **只要玩家同时戴着命灯指轮** ✗ ⇒
 * ⭐ **② 那段"七行全免"就完全不跑** ✗（用户截图第一行显示的是命灯的绿色祝福 ✓ 正是证据 ✓）。
 * ⇒ 现在改成**两条路都跑** ✓（⚠ 第一行若已被命灯改写 ⇒ ② 不再重复处理它 ✗ 免得叠加两层 ✗）。
 * <p>⚠ 并把 EL 的 {@code _alt} 变体键（{@code cursedRing4_alt} ✓ 戴戒指时可能用另一版文案 ✓）
 * 一并纳入匹配 ✓，免得"戴上之后文案变了 ⇒ 匹配不上 ⇒ 粉色不出现" ✗。
 *
 * <p>⚠ <b>临时诊断</b>：下面会打印七咒之戒提示的**每一行原文**与匹配结果 ✓
 * ⇒ 一次复现就能拿到全部证据 ✓（查清后删 ✗）。
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

    /** 本模组"超越维度"特性 id ✓ */
    private static final ModifierId DIMENSION_ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, BeyondDimensionTrait.ID));

    /** ⚠ 临时诊断开关 ✓（查清就删 ✗） */
    private static final boolean DEBUG_LINES = true;
    private static long lastDebugTick = 0L;

    /**
     * ⭐ 七咒七行 ↔ 免除后的粉色文本（用户给的对照 ✓ 逐条对应 ✓）。
     * <p>{@code {EL 的键, 我们的替换键}} ✓；⚠ 同时登记 EL 的 {@code _alt} 变体 ✓
     * （它在"戴着戒指"时可能换成另一版文案 ✓ 例如 {@code cursedRing4_alt} ✓）。
     */
    private static final String[][] WAIVE_LINES = {
            {"tooltip.enigmaticlegacy.cursedRing4", "tooltip.enigmaticlegacy.cursedRing4_alt",
                    "modifier.tinkersnewlife.transcendent_dimension.waive1"},
            {"tooltip.enigmaticlegacy.cursedRing5", null,
                    "modifier.tinkersnewlife.transcendent_dimension.waive2"},
            {"tooltip.enigmaticlegacy.cursedRing6", null,
                    "modifier.tinkersnewlife.transcendent_dimension.waive3"},
            {"tooltip.enigmaticlegacy.cursedRing7", null,
                    "modifier.tinkersnewlife.transcendent_dimension.waive4"},
            {"tooltip.enigmaticlegacy.cursedRing8", null,
                    "modifier.tinkersnewlife.transcendent_dimension.waive5"},
            {"tooltip.enigmaticlegacy.cursedRing9", null,
                    "modifier.tinkersnewlife.transcendent_dimension.waive6"},
            {"tooltip.enigmaticlegacy.cursedRing10", null,
                    "modifier.tinkersnewlife.transcendent_dimension.waive7"}
    };

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!isCursedRing(event.getItemStack())) return;

        List<Component> lines = event.getToolTip();
        boolean lifeLampRewrote = false;

        // ---- ① 命灯指轮：改写"第一诅咒"那行（原有行为 ✓）----
        if (LifeLampRingItem.isWorn(player)) {
            // 「心」恶意 ≥20% ⇒ 命灯指轮的七咒解除已被阻塞 ⇒ 这里也不改写提示
            if (com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler.mirrorAlignment(player)
                    > com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceThresholdHandler.LAMP_AT) {
                for (int i = 0; i < lines.size(); i++) {
                    if (!strip(lines.get(i).getString()).equals(strip(Component.translatable(FIRST_CURSE_KEY).getString())))
                        continue;
                    MutableComponent struck = Component.literal(strip(lines.get(i).getString()))
                            .withStyle(s -> s.applyFormat(ChatFormatting.DARK_GRAY).withStrikethrough(true));
                    MutableComponent blessing = Component.translatable("item.tinkersnewlife.life_lamp_ring.blessing")
                            .withStyle(s -> s.applyFormat(ChatFormatting.GREEN).withStrikethrough(false));
                    lines.set(i, struck.append(blessing));
                    lifeLampRewrote = true;
                    break;
                }
            }
            // ⚠⚠ 这里**不能 return** ✗ —— 原先就是它导致"同时戴命灯时七行全免完全不生效" ✗
        }

        // ---- ② ⭐ 超越维度 ≥4：七咒全划掉 ＋ 追加粉色 ✓ ----
        int total = totalDimensionLevel(player);
        if (DEBUG_LINES && player.level().getGameTime() - lastDebugTick > 40L) {
            lastDebugTick = player.level().getGameTime();
            for (int i = 0; i < lines.size(); i++) {
                TinkersNewlife.LOGGER.info("[七咒提示诊断] 第{}行 = [{}]", i, strip(lines.get(i).getString()));
            }
            TinkersNewlife.LOGGER.info("[七咒提示诊断] 超越维度总等级={} 戴命灯={} 命灯已改写={}",
                    total, LifeLampRingItem.isWorn(player), lifeLampRewrote);
        }
        if (total < BeyondDimensionTrait.WAIVER_TOTAL_LEVEL) return;

        for (int i = 0; i < lines.size(); i++) {
            String text = strip(lines.get(i).getString());
            if (text.isEmpty()) continue;
            // ⚠ 第一行若已被命灯改写 ⇒ 跳过 ✗（否则会在祝福后面再叠一层 ✗）
            if (lifeLampRewrote && i < lines.size()
                    && strip(lines.get(i).getString()).contains(strip(Component
                    .translatable("item.tinkersnewlife.life_lamp_ring.blessing").getString()))) {
                continue;
            }
            for (String[] pair : WAIVE_LINES) {
                String replacementKey = pair[2];
                if (matches(text, pair[0]) || (pair[1] != null && matches(text, pair[1]))) {
                    MutableComponent struck = Component.literal(text)
                            .withStyle(s -> s.applyFormat(ChatFormatting.DARK_GRAY).withStrikethrough(true));
                    MutableComponent replaced = Component.translatable(replacementKey)
                            .withStyle(s -> s.applyFormat(ChatFormatting.LIGHT_PURPLE).withStrikethrough(false));
                    lines.set(i, struck.append(replaced));
                    break;
                }
            }
        }
    }

    /**
     * 这行是不是那条诅咒 ✓ —— ⚠ 含 {@code %1$s} 的行（护甲效力/对怪伤害 ✓）
     * **不能整句比对** ✗（游戏里那个值是代入后的数字 ✓）⇒ 按**参数前的部分**做前缀匹配 ✓。
     */
    private static boolean matches(String line, String langKey) {
        String base = strip(Component.translatable(langKey).getString());
        if (base.isEmpty()) {
            return false;
        }
        int fmt = base.indexOf('%');
        if (fmt > 0) {
            return line.startsWith(base.substring(0, fmt).trim());
        }
        return line.equals(base);
    }

    /** 客户端扫自己穿的护甲 ✓ 累加"超越维度"等级 ✓ */
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
