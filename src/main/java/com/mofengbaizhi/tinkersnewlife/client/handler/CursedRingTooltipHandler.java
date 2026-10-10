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
 * <h2>⭐ 优先级（用户口径 ✓「**应该比命灯的优先级高**」✓）</h2>
 * <ol>
 *   <li><b>先判「超越维度全免」</b> ✓：全身总等级 **≥ 4** ⇒ ⭐ **整个提示都由我们接管** ✓
 *       （七行**全部**划掉 ＋ 追加粉色 ✓，**包括第一行** ✓）⇒ 直接结束 ✗ 不再走命灯那条 ✓；</li>
 *   <li>否则再判 **命灯指轮** ✓（原有行为 ✓）：戴着命灯时只改写**第一诅咒**那一行 ✓
 *       （划掉 ＋ 绿色祝福 `item.tinkersnewlife.life_lamp_ring.blessing` ✓）。</li>
 * </ol>
 * ⚠ 两者**互斥** ✓ —— 我上一版让命灯先跑、我这边跳过第一行 ✗ ⇒ 用户指出**顺序反了** ✗ ⇒ 现已对调 ✓。
 *
 * <h2>⚠ 之前的两处教训（都记在备忘录 ✓）</h2>
 * ① 命灯分支结尾曾有个 {@code return;} ✗ ⇒ 同时戴命灯时"七行全免"**完全不跑** ✗；
 * ② EL 戴着戒指时第一诅咒可能改用 {@code cursedRing4_alt} 文案 ✓ ⇒ 匹配必须**同时登记 `_alt` 变体** ✓，
 *    且含 {@code %1$s} 的行（护甲效力/对怪伤害 ✓）要按**参数前的前缀**比对 ✗ 不能整句比 ✓。
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
    /** 命灯指轮的绿色祝福文案键 ✓ */
    private static final String LAMP_BLESSING_KEY = "item.tinkersnewlife.life_lamp_ring.blessing";

    /** 本模组"超越维度"特性 id ✓ */
    private static final ModifierId DIMENSION_ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, BeyondDimensionTrait.ID));

    /**
     * ⭐ 七咒七行 ↔ 免除后的粉色文本（用户给的对照 ✓ 逐条对应 ✓）。
     * <p>{@code {EL 的键, EL 的 _alt 变体（可为 null）, 我们的替换键}} ✓
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

        // ---- ① ⭐ 最高优先：超越维度全免（≥4）⇒ 七行全按我们的来 ✓ ----
        if (totalDimensionLevel(player) >= BeyondDimensionTrait.WAIVER_TOTAL_LEVEL) {
            for (int i = 0; i < lines.size(); i++) {
                String text = strip(lines.get(i).getString());
                if (text.isEmpty()) continue;
                for (String[] pair : WAIVE_LINES) {
                    if (matches(text, pair[0]) || (pair[1] != null && matches(text, pair[1]))) {
                        MutableComponent struck = Component.literal(text)
                                .withStyle(s -> s.applyFormat(ChatFormatting.DARK_GRAY).withStrikethrough(true));
                        MutableComponent replaced = Component.translatable(pair[2])
                                .withStyle(s -> s.applyFormat(ChatFormatting.LIGHT_PURPLE).withStrikethrough(false));
                        lines.set(i, struck.append(replaced));
                        break;
                    }
                }
            }
            return;   // ⭐ 全免已接管 ⇒ 不再走命灯那条 ✓
        }

        // ---- ② 其次：命灯指轮（原有行为 ✓ 只在没达到全免时生效 ✓）----
        if (!LifeLampRingItem.isWorn(player)) return;
        // 「心」恶意 ≥20% ⇒ 命灯指轮的七咒解除已被阻塞 ⇒ 这里也不改写提示
        if (com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler.mirrorAlignment(player)
                <= com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceThresholdHandler.LAMP_AT) {
            return;
        }
        String expected = strip(Component.translatable(FIRST_CURSE_KEY).getString());
        for (int i = 0; i < lines.size(); i++) {
            if (!strip(lines.get(i).getString()).equals(expected)) continue;
            MutableComponent struck = Component.literal(strip(lines.get(i).getString()))
                    .withStyle(s -> s.applyFormat(ChatFormatting.DARK_GRAY).withStrikethrough(true));
            MutableComponent blessing = Component.translatable(LAMP_BLESSING_KEY)
                    .withStyle(s -> s.applyFormat(ChatFormatting.GREEN).withStrikethrough(false));
            lines.set(i, struck.append(blessing));
            return;
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
