package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChildOfTheStarsHandler {

    private static final ModifierId CHILD_OF_THE_STARS = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "child_of_the_stars")
    );

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // ⭐ 混沌之流的嵌套段（1 物理 + 每学派 1 段）不再逐段吃"群星之子 ×2^级" ✗
        //    否则 段数 × 2^级 = 指数级膨胀 ✗（见 util/DamagePipeline）
        if (com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.skipNested()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide) return;
        LivingEntity target = event.getEntity();
        if (target == attacker) return;

        // ✅ 统一获取攻击工具：玩家近战/弹射/悠悠球+咒力核心兜底；怪物只查主手
        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), attacker, CHILD_OF_THE_STARS);
        if (tool == null) return;

        int level = ToolHelper.getActiveModifierLevel(tool, CHILD_OF_THE_STARS);
        if (level <= 0) return;

        float originalDamage = event.getAmount();
        float multipliedDamage = (float) (originalDamage * Math.pow(2, level));
        event.setAmount(multipliedDamage);
    }
}