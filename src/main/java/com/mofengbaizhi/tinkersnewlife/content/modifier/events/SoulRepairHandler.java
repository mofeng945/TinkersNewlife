package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;
import java.util.Random;

/**
 * 强化·灵魂修复：服务端每 20 tick（{@link TickEvent.Phase#END}）扫描玩家主手/副手
 * 持有的匠魂工具；若装 {@code soul_repair} 强化，则消耗 {@code max(1, 6-等级)} 点灵魂能量
 * 恢复 1 点耐久，并有 {@code 5%×等级} 概率额外恢复 1 点耐久。
 * <p>灵魂不足（&lt; cost）时本次不修复、也不消耗能量。耐久改动走
 * {@code ToolStack.getDamage/setDamage/updateStack}（与 FlyingSwordItem 一致）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoulRepairHandler {

    private static final ModifierId SOUL_REPAIR = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "soul_repair"));
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        if (sp.level().getGameTime() % 20 != 0) return;   // 每 20 tick 一次

        for (ItemStack stack : repairCandidates(sp)) {
            if (stack.isEmpty()) continue;
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || tool.isBroken()) continue;
            int lv = tool.getModifierLevel(SOUL_REPAIR);
            if (lv <= 0) continue;
            if (tool.getDamage() <= 0) continue;                     // 耐久已满：不修也不耗灵魂

            int cost = Math.max(1, 6 - lv);
            if (SoulEnergyBridge.getSouls(sp) < cost) continue;      // 灵魂不足：不修不耗
            if (!SoulEnergyBridge.decreaseSouls(sp, cost)) continue;

            int repair = 1;
            if (RANDOM.nextFloat() < 0.05f * lv) repair += 1;        // 5%×等级 概率额外 1 点
            int newDmg = Math.max(0, tool.getDamage() - repair);
            tool.setDamage(newDmg);
            tool.updateStack(stack);
        }
    }

    /** 待修复装备候选：主手/副手 + 4 个盔甲槽（护甲也可自动修复） */
    private static java.util.List<ItemStack> repairCandidates(ServerPlayer sp) {
        java.util.List<ItemStack> list = new java.util.ArrayList<>(6);
        list.add(sp.getMainHandItem());
        list.add(sp.getOffhandItem());
        sp.getArmorSlots().forEach(list::add);
        return list;
    }
}
