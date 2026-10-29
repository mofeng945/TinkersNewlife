package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.IndestructibleModifier;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * TNL1388 <b>旧杜兰达尔工具迁移</b>：把材料自带的 {@code tconstruct:unbreakable}
 * 换成我们自己的 {@code tinkersnewlife:indestructible}。
 *
 * <p>原因见 {@link IndestructibleModifier} 的类注释：本体「不毁」带
 * {@code ModifierRequirementsModule}（下界合金 1 + 加固 V），当材料特性白给时
 * 会让 {@code tool.tryValidate()} 永远报错 ⇒ 工具**无法强化** ✗
 *
 * <p>判定条件（只动真正坏掉的那些）：
 * <ul>
 *   <li>是匠魂工具（有统计）且带 {@code tconstruct:unbreakable}；</li>
 *   <li>且**不满足**"下界合金 ≥ 1 且 加固 ≥ 5"（满足的那把是玩家正常做出来的，不能动 ✓）；</li>
 *   <li>且还没有 {@code tinkersnewlife:indestructible}。</li>
 * </ul>
 * 迁移后：玩法效果一致（永不掉耐久），但不再有任何前置要求 ⇒ 可以正常强化 ✓
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IndestructibleMigrationHandler {

    private IndestructibleMigrationHandler() {
    }

    private static final ModifierId UNBREAKABLE = new ModifierId(new ResourceLocation("tconstruct", "unbreakable"));
    private static final ModifierId NETHERITE = new ModifierId(new ResourceLocation("tconstruct", "netherite"));
    private static final ModifierId REINFORCED = new ModifierId(new ResourceLocation("tconstruct", "reinforced"));

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            try {
                ToolStack tool = ToolStack.from(stack);
                if (tool.getStats().getContainedStats().isEmpty()) {
                    continue;
                }
                int unbreakable = ToolHelper.getActiveModifierLevel(tool, UNBREAKABLE);
                if (unbreakable <= 0) {
                    continue;
                }
                if (ToolHelper.getActiveModifierLevel(tool, IndestructibleModifier.ID) > 0) {
                    continue;
                }
                // 玩家自己正常做出「不毁」的（下界合金 + 加固 V）⇒ 不动 ✓
                boolean hasPrereqs = ToolHelper.getActiveModifierLevel(tool, NETHERITE) >= 1
                        && ToolHelper.getActiveModifierLevel(tool, REINFORCED) >= 5;
                if (hasPrereqs) {
                    continue;
                }
                // ⚠ 材料特性是从「材质 + 定义」重建出来的（TAG_MODIFIERS），
                //   不能像强化那样 removeModifier ✗ ⇒ 直接 rebuildStats() 让新数据生效 ✓
                //   （数据里 materials/traits/durandal.json 和 tool_definitions/durandal_sword.json
                //    已把 tconstruct:unbreakable 换成 tinkersnewlife:indestructible）
                tool.rebuildStats();
                inventory.setItem(i, tool.updateStack(stack));
                TinkersNewlife.LOGGER.info("[杜兰达尔] 已把 {} 上的本体「不毁」换成「百折不断」（可正常强化了）",
                        stack.getDisplayName().getString());
            } catch (Throwable ignored) {
                // 非匠魂物品/任何异常 ⇒ 跳过
            }
        }
    }
}
