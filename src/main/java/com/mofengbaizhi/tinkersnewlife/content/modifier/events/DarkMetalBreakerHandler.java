package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Random;

/**
 * 词条·破法（黑暗金属工具部件自带）：
 * <ul>
 *   <li>攻击命中（造成伤害时）给予目标衰弱 I，持续 {@code 2×等级} 秒；</li>
 *   <li>击杀"带魔法抗性"的生物（身上有原版抗性提升 DAMAGE_RESISTANCE 效果）时，
 *       45% 概率额外掉落 1 个绿宝石。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DarkMetalBreakerHandler {

    private static final ModifierId BREAKER = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "dark_metal_breaker"));
    private static final Random RANDOM = new Random();

    /** 攻击造成伤害后：给目标挂衰弱 I（时长 2×等级 秒） */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Player player)) return;
        LivingEntity target = event.getEntity();

        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), player, BREAKER);
        if (tool == null) return;
        int lv = tool.getModifierLevel(BREAKER);
        if (lv <= 0) return;

        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 2 * lv, 0));
    }

    /** 击杀带魔法抗性（抗性提升效果）的生物：45% 概率额外掉 1 绿宝石 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Player player)) return;
        LivingEntity target = event.getEntity();

        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), player, BREAKER);
        if (tool == null) return;
        if (tool.getModifierLevel(BREAKER) <= 0) return;
        if (!hasMagicResistance(target)) return;
        if (RANDOM.nextFloat() < 0.45f) {
            target.level().addFreshEntity(new ItemEntity(target.level(),
                    target.getX(), target.getY() + 0.5, target.getZ(),
                    new ItemStack(Items.EMERALD)));
        }
    }

    /** "带魔法抗性"判定：身上有原版抗性提升（DAMAGE_RESISTANCE）效果 */
    private static boolean hasMagicResistance(LivingEntity entity) {
        return entity.hasEffect(MobEffects.DAMAGE_RESISTANCE);
    }
}
