package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 强化·灵魂修复：服务端每 20 tick 扫描<b>玩家</b>主手/副手/盔甲与<b>其诡厄仆从</b>的
 * 匠魂装备；若带 {@code soul_repair} 强化，则消耗 {@code max(1, 6-等级)} 点灵魂能量
 * 恢复 1 点耐久，并有 {@code 5%×等级} 概率额外恢复 1 点耐久。
 * <ul>
 *   <li>玩家自身：扣玩家灵魂（原逻辑）。</li>
 *   <li>诡厄仆从（僵尸/骷髅等，dark_metal 护甲词条也带 soul_repair）：扣<b>主人</b>灵魂
 *       （仆从没有灵魂槽），主人非玩家/灵魂不足则不修复。</li>
 * </ul>
 * 灵魂不足（&lt; cost）时本次不修复、也不消耗能量。耐久改动走
 * {@code ToolStack.getDamage/setDamage/updateStack}。
 * ⚠️ 教训（规则 33）：必须先判 {@code tool.getDamage() > 0}（耐久未满）再扣灵魂。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoulRepairHandler {

    private static final ModifierId SOUL_REPAIR = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "soul_repair"));
    private static final Random RANDOM = new Random();

    /** 玩家自身装备修复（原逻辑，规则 33：先查耐久缺口再扣灵魂） */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        if (sp.level().getGameTime() % 20 != 0) return;
        repairEquipment(sp, repairCandidates(sp));
    }

    /**
     * 仆从装备修复：goety 仆从（僵尸/骷髅等）穿上带 soul_repair 的暗金属护甲/武器时，
     * 每 20 tick 自动修复——灵魂扣<b>主人</b>（玩家）。
     */
    @SubscribeEvent
    public static void onServantTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (entity instanceof Player) return;              // 玩家走 onTick
        if (entity.level().getGameTime() % 20 != 0) return;
        if (!(entity instanceof Mob mob)) return;
        if (!GoetyBridge.isGoetyServant(mob)) return;      // 只处理诡厄仆从
        LivingEntity owner = GoetyBridge.getServantOwner(mob);
        if (!(owner instanceof ServerPlayer master)) return; // 扣主人灵魂

        // 仆从身上任一匠魂装备（主副手+盔甲）带 soul_repair 才值得做槽位扫描
        if (!hasSoulRepairAnywhere(mob)) return;
        repairEquipment(master, repairCandidates(mob));
    }

    /** 主/副手 + 4 盔甲槽中是否至少有一个带 soul_repair 的匠魂装备（快速预筛） */
    private static boolean hasSoulRepairAnywhere(Mob mob) {
        for (ItemStack stack : repairCandidates(mob)) {
            if (stack.isEmpty()) continue;
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || tool.isBroken()) continue;
            if (tool.getModifierLevel(SOUL_REPAIR) > 0) return true;
        }
        return false;
    }

    /** 通用修复：扣 payer 灵魂，修 target 的候选装备 */
    private static void repairEquipment(ServerPlayer payer, List<ItemStack> candidates) {
        for (ItemStack stack : candidates) {
            if (stack.isEmpty()) continue;
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || tool.isBroken()) continue;
            int lv = tool.getModifierLevel(SOUL_REPAIR);
            if (lv <= 0) continue;
            if (tool.getDamage() <= 0) continue;                     // 耐久已满：不修也不耗灵魂
            int cost = Math.max(1, 6 - lv);
            if (SoulEnergyBridge.getSouls(payer) < cost) continue;   // 灵魂不足：不修不耗
            if (!SoulEnergyBridge.decreaseSouls(payer, cost)) continue;

            int repair = 1;
            if (RANDOM.nextFloat() < 0.05f * lv) repair += 1;        // 5%×等级 概率额外 1 点
            int newDmg = Math.max(0, tool.getDamage() - repair);
            tool.setDamage(newDmg);
            tool.updateStack(stack);
        }
    }

    /** 待修复装备候选：主手/副手 + 4 个盔甲槽（护甲也可自动修复） */
    private static List<ItemStack> repairCandidates(LivingEntity wearer) {
        List<ItemStack> list = new ArrayList<>(6);
        list.add(wearer.getMainHandItem());
        list.add(wearer.getOffhandItem());
        wearer.getArmorSlots().forEach(list::add);
        return list;
    }
}
