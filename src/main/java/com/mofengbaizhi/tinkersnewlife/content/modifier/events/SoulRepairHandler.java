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
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 强化·灵魂修复：服务端每 20 tick 扫描<b>玩家</b>主手/副手/盔甲/<b>饰品栏（Curios）</b>与<b>其诡厄仆从</b>的
 * 匠魂装备；若带 {@code soul_repair} 强化，则消耗 {@code max(1, 6-等级)} 点灵魂能量
 * 恢复 1 点耐久，并有 {@code 5%×等级} 概率额外恢复 1 点耐久。
 * <ul>
 *   <li>玩家自身：扣玩家灵魂（原逻辑）。</li>
 *   <li>诡厄仆从（僵尸/骷髅等，dark_metal 护甲词条也带 soul_repair）：扣<b>主人</b>灵魂
 *       （仆从没有灵魂槽），主人非玩家/灵魂不足则不修复。</li>
 * </ul>
 * 灵魂不足（&lt; cost）时本次不修复、也不消耗能量。耐久改动走
 * {@code ToolDamageUtil.repair}（内部 {@code ToolStack.setDamage}）。
 * ⚠️ 教训（规则 33）：必须先判 {@code tool.getDamage() > 0}（耐久未满）再扣灵魂。
 * <p>
 * ⭐ 破损（{@code tic_broken}）后也能修（用户口径）：灵魂修复是<b>唯一</b>在破损态下仍
 * 读取强化等级的特性 —— 因为 {@link ToolHelper#getActiveModifierLevel} 破损时恒返回 0，
 * 这里改用 {@link ToolHelper#getModifierLevelIgnoringBroken}。修复走
 * {@code ToolDamageUtil.repair}，它内部 {@code ToolStack.setDamage} 在
 * {@code damage < durability} 时会 {@code setBrokenRaw(false)} 顺手摘掉 {@code tic_broken}
 * ⇒ 破损工具被灵魂修一下即可重新拿起使用（但耐久只剩 1~2 点）。
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

    /** 主/副手 + 4 盔甲槽中是否至少有一个带 soul_repair 的匠魂装备（快速预筛；破损的也算） */
    private static boolean hasSoulRepairAnywhere(Mob mob) {
        for (ItemStack stack : repairCandidates(mob)) {
            if (stack.isEmpty()) continue;
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null) continue;
            // ⭐ 破损工具也算：否则破损后预筛直接挡掉，永远走不到修复
            if (ToolHelper.getModifierLevelIgnoringBroken(tool, SOUL_REPAIR) > 0) return true;
        }
        return false;
    }

    /** 通用修复：扣 payer 灵魂，修 target 的候选装备（含<b>破损</b>装备，修好即解除破损） */
    private static void repairEquipment(ServerPlayer payer, List<ItemStack> candidates) {
        List<ItemStack> curioList = curioStacks(payer);
        boolean repairedCurio = false;
        for (ItemStack stack : candidates) {
            if (stack.isEmpty()) continue;
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null) continue;
            // ⭐ 破损（tic_broken）也要能修：getActiveModifierLevel 破损时恒返回 0，
            //    这里必须用"无视破损"的查询，否则破损装备连等级都读不到
            int lv = ToolHelper.getModifierLevelIgnoringBroken(tool, SOUL_REPAIR);
            if (lv <= 0) continue;
            // 无耐久统计的工具（理论不可达：TCon 扣耐久前会查 TinkerTags.Items.DURABILITY）跳过
            if (tool.getStats().getInt(ToolStats.DURABILITY) <= 0) continue;
            // 耐久已满<b>且未破损</b>：不修也不耗灵魂。
            // 注意破损时 ToolStack.getDamage() 返回"满耐久"（ToolStack.java:376-384），
            // 所以破损装备不会命中此条，能继续往下修到解除破损 ✓
            if (tool.getDamage() <= 0 && !tool.isBroken()) continue;
            int cost = Math.max(1, 6 - lv);
            if (SoulEnergyBridge.getSouls(payer) < cost) continue;   // 灵魂不足：不修不耗
            if (!SoulEnergyBridge.decreaseSouls(payer, cost)) continue;

            int repair = 1;
            if (RANDOM.nextFloat() < 0.05f * lv) repair += 1;        // 5%×等级 概率额外 1 点
            // ⭐ TCon 官方修复路径（修复配方也用它）：
            //    内部 ToolStack.setDamage(damage - amount)，damage < durability 时
            //    setBrokenRaw(false) ⇒ 顺手解除 tic_broken（ToolStack.java:404-414）
            ToolDamageUtil.repair(tool, repair);
            tool.updateStack(stack);
            // 身份比较：确认这件是不是饰品栏里的那把（是则稍后回写同步）
            for (ItemStack curio : curioList) {
                if (curio == stack) { repairedCurio = true; break; }
            }
        }
        if (repairedCurio) {
            resyncCurios(payer);
        }
    }

    /** 待修复装备候选：主手/副手 + 4 个盔甲槽 + <b>饰品栏（Curios）全部槽位</b> */
    private static List<ItemStack> repairCandidates(LivingEntity wearer) {
        List<ItemStack> list = new ArrayList<>(12);
        list.add(wearer.getMainHandItem());
        list.add(wearer.getOffhandItem());
        wearer.getArmorSlots().forEach(list::add);
        // ⭐ 饰品栏：灵魂修复在饰品槽里也要生效（脚部飞剑、饰品位上的匠魂工具等）
        list.addAll(curioStacks(wearer));
        return list;
    }

    /** 某个实体饰品栏里的所有物品（没有 Curios / 取不到时返回空表） */
    private static List<ItemStack> curioStacks(LivingEntity wearer) {
        List<ItemStack> list = new ArrayList<>(4);
        try {
            var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(wearer).resolve();
            if (curios.isEmpty()) return list;
            for (var handler : curios.get().getCurios().values()) {
                if (handler == null) continue;
                var stacks = handler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty()) list.add(stack);
                }
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    /**
     * 修复完把饰品栏槽位回写一遍：Curios 只有在 {@code setStackInSlot} 时才会标记 dirty 并同步给客户端，
     * 直接改 ItemStack 的 NBT 客户端看不到耐久条变化。
     */
    private static void resyncCurios(LivingEntity wearer) {
        try {
            var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(wearer).resolve();
            if (curios.isEmpty()) return;
            for (var handler : curios.get().getCurios().values()) {
                if (handler == null) continue;
                var stacks = handler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    stacks.setStackInSlot(i, stacks.getStackInSlot(i));
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
