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
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        // ✅ 统一获取攻击工具（近战/弹射物/悠悠球从球实体读取），主手无武器时兜底取佩戴的咒力核心
        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), player, CHILD_OF_THE_STARS);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHILD_OF_THE_STARS);
        if (level <= 0) return;

        float originalDamage = event.getAmount();
        float multipliedDamage = (float) (originalDamage * Math.pow(2, level));
        event.setAmount(multipliedDamage);
    }
}