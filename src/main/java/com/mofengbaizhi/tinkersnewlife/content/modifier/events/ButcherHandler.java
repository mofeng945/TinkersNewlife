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
    private static final int INTERVAL = 20;

    private ButcherHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        if (wearer.tickCount % INTERVAL != 0) return;
        if (!hasButcher(wearer.getMainHandItem())) return;
        if (!(wearer.level() instanceof ServerLevel server)) return;

        AABB box = wearer.getBoundingBox().inflate(RADIUS);
        for (LivingEntity mob : wearer.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (mob == wearer || !mob.isAlive()) continue;
            if (!isIllagerOrVillager(mob)) continue;
            // 远离且不攻击佩戴者：清除其目标并导航离开
            if (mob instanceof net.minecraft.world.entity.Mob m) {
                if (m.getTarget() == wearer) m.setTarget(null);
                m.getNavigation().moveTo(wearer, -1.0D);
            }
        }
    }

    private static boolean isIllagerOrVillager(LivingEntity e) {
        return e instanceof AbstractIllager || e instanceof Witch || e instanceof Villager;
    }

    private static boolean hasButcher(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ButcherModifier.ID) > 0;
    }
}
