package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 咒具「天逆鉾」：原版武器逻辑（非匠魂工具）。
 * <ul>
 *   <li>基础伤害 24 点，攻速同剑</li>
 *   <li>无限耐久、对亡灵特攻 +6（咒具基类提供）</li>
 *   <li>攻击场上已存在的式神：直接清除（中断式神召唤）</li>
 *   <li>可附上剑类附魔（锋利/火焰附加等）</li>
 *   <li>手持右键领域结界方块：直接破坏该领域；若领域对抗中，双方领域同时崩坏并进入熔断（创造豁免）</li>
 *   <li>无视无下限·无限的防御，直接对施术者造成伤害</li>
 * </ul>
 */
public class TianNiHuoItem extends CursedToolItem {

    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return 0; } // 无限耐久（isDamageable=false 实际不消耗）
        @Override public float getSpeed() { return 6.0F; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public int getLevel() { return 4; }
        @Override public int getEnchantmentValue() { return 18; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public TianNiHuoItem(Properties properties) {
        super(TIER, 24, -2.4F, properties);
    }

    /** 天逆鉾可无视无下限防御 */
    @Override
    public boolean ignoresInfinity() {
        return true;
    }

    /** 命中后：亡灵特攻（基类）+ 清除式神（中断式神召唤） */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (target instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob) {
            target.discard();
            if (!target.level().isClientSide) {
                target.level().broadcastEntityEvent(target, (byte) 20); // 死亡粒子
            }
            if (attacker instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.tinkersnewlife.cursed_tool.shikigami_cleared"), true);
            }
        }
        return result;
    }

    /** 右键领域结界方块 → 破坏领域（对抗中双方同崩） */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        if (level.isClientSide) return InteractionResult.PASS;
        BlockState state = level.getBlockState(context.getClickedPos());
        Block barrier = com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get();
        if (!state.is(barrier)) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer breaker) {
            com.mofengbaizhi.tinkersnewlife.content.curse.DomainRegistry
                    .breakDomainByBarrier(breaker, (ServerLevel) level, context.getClickedPos());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
