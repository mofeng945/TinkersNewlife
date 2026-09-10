package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.content.modifier.ButcherModifier;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/** 特性·人屠：佩戴时灾厄生物(含女巫)与村民远离且不攻击你，灵魂获取×2。 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ButcherHandler {

    private static final double RADIUS = 32.0;
    private static final double FLEE_DISTANCE = 16.0;
    private static final int INTERVAL = 20;

    private ButcherHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        if (wearer.tickCount % INTERVAL != 0) return;
        if (!hasButcherOnEquipment(wearer)) return;
        if (!(wearer.level() instanceof ServerLevel server)) return;

        AABB box = wearer.getBoundingBox().inflate(RADIUS);
        for (LivingEntity mob : wearer.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (mob == wearer || !mob.isAlive()) continue;
            // 睡觉中的生物（如床上村民）不受影响，避免被从床上赶起来
            if (mob.isSleeping()) continue;
            if (!isIllagerOrVillager(mob)) continue;
            // 不入屠范围目标：清除对佩戴者的仇恨，并朝远离佩戴者的方向主动逃跑
            if (mob instanceof net.minecraft.world.entity.Mob m) {
                if (m.getTarget() == wearer) m.setTarget(null);
                avoidWearer(m, wearer);
            }
        }
    }

    /** 让 mob 朝远离佩戴者的方向逃跑（正速度沿相反向量移动，避免负速度寻路的不可靠性） */
    private static void avoidWearer(net.minecraft.world.entity.Mob m, LivingEntity wearer) {
        double dx = m.getX() - wearer.getX();
        double dz = m.getZ() - wearer.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0e-4) return;              // 与佩戴者几乎重合，无明确逃离方向
        double fx = m.getX() + (dx / len) * FLEE_DISTANCE;
        double fz = m.getZ() + (dz / len) * FLEE_DISTANCE;
        m.getNavigation().moveTo(fx, m.getY(), fz, 1.0D);
    }

    private static boolean isIllagerOrVillager(LivingEntity e) {
        return e instanceof AbstractIllager || e instanceof Witch || e instanceof Villager;
    }

    private static boolean hasButcher(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ButcherModifier.ID) > 0;
    }

    /** 佩戴者（主/副手 + 盔甲槽）上是否有人屠特性（材料特性位于佩戴的头甲上） */
    private static boolean hasButcherOnEquipment(LivingEntity wearer) {
        if (hasButcher(wearer.getMainHandItem())) return true;
        if (hasButcher(wearer.getOffhandItem())) return true;
        for (ItemStack stack : wearer.getArmorSlots()) {
            if (hasButcher(stack)) return true;
        }
        return false;
    }
}
