package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
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

    // ==================== 近战伤害翻倍 ====================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 检查伤害来源是否为玩家
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        // 检查主手工具
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHILD_OF_THE_STARS);
        if (level <= 0) return;

        // 伤害翻倍：每级 ×2
        float originalDamage = event.getAmount();
        float multipliedDamage = (float) (originalDamage * Math.pow(2, level));
        event.setAmount(multipliedDamage);
    }

    // ==================== 弹射物伤害翻倍 ====================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getProjectile().getOwner() instanceof Player player)) return;
        if (target.level().isClientSide) return;

        // 检查主手工具
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;
        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHILD_OF_THE_STARS);
        if (level <= 0) return;

        // 注意：ProjectileImpactEvent 无法直接修改伤害，因为伤害是在弹射物命中后由弹射物本身造成的。
        // 但我们可以尝试在后续的 LivingHurtEvent 中捕获，但弹射物伤害可能不经过玩家的攻击事件。
        // 更好的方式是修改弹射物的伤害属性，但比较复杂。
        // 简单处理：这里不做修改，仅留作占位。
        // 实际上，弹射物造成的伤害会在 LivingHurtEvent 中被捕获，所以上面的 onLivingHurt 也会处理弹射物伤害。
        // 所以此事件留空。
    }
}