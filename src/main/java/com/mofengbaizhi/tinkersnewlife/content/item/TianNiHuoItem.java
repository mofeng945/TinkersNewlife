package com.mofengbaizhi.tinkersnewlife.content.item;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;

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

    /**
     * <b>§773：潜行放行 —— 只对「白色空间传送门」返回 true</b> ✓
     *
     * <h2>为什么必须有它（§772 实证 ✗）</h2>
     * 原版在**潜行且手上物品不"潜行放行"**时会**整段跳过**方块交互 ✗：
     * <pre>
     * // 客户端 MultiPlayerGameMode#useItemOn（服务端 ServerPlayerGameMode 同一条判定）
     * boolean flag  = !main.doesSneakBypassUse(...) || !off.doesSneakBypassUse(...);
     * boolean flag1 = player.isSecondaryUseActive() &amp;&amp; flag;
     * if (... &amp;&amp; !flag1) blockstate.use(...);      // ← flag1 为真 ⇒ 不调用 ✗
     * </pre>
     * 而 §722 的拆门写的是"**必须潜行才拆**"（`WhiteSpacePortalBlock#use` 里的
     * {@code if (!player.isSecondaryUseActive()) return PASS;} ✓）
     * ⇒ 天逆鉾原来没有重写本方法（默认 {@code false} ✗）
     * ⇒ **潜行时 {@code Block#use} 根本不会被调用** ✗ ⇒ 拆门代码永远进不去 ✗
     * ⇒ 用户实测「天逆鉾破坏不了门了」✓（§772 记录 ✓）。
     *
     * <p>⇒ 这里只在"**对着我们自己的门**"时放行 ✓：
     * 潜行 + 右键门 ⇒ 两手都算 bypass（副手空手时 Forge 恒为 true ✓）
     * ⇒ 客户端与服务端的 {@code flag1} 都变 false ✓ ⇒ 走 {@code Block#use} ✓ ⇒ 拆门恢复 ✓；
     * 对着**别的方块**仍返回 false ✓ ⇒ 拿天逆鉾潜行放方块等原版行为一点不变 ✓。
     *
     * <p>⚠ 已知原版边角：若**副手也拿着**一个不 bypass 的物品 ✗，原版要求**双手都 bypass** ⇒ 那时方块那边仍进不去 ✗
     * （原版机制 ✓，所以 §777 起**拆门也在物品侧做了一份** ✓，两边互补 ✓）。
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, net.minecraft.world.level.LevelReader level,
                                      net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {
        try {
            return level.getBlockState(pos)
                    .is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.WHITE_SPACE_PORTAL.get());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 命中后：亡灵特攻（基类）+ 清除式神（中断式神召唤） */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        // ⚠ 只清除「**已调伏**」的式神：未调伏（调伏战中）是敌人，
        //    discard() 不触发 die() ⇒ 调伏结算不会发生 ⇒ 用天逆鉾打调伏战等于白打 ✗。
        //    未调伏的照常吃伤害，让它正常死掉走 {@code ShikigamiBehavior.onDeath} 调伏成功 ✓。
        if (target instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob sm
                && sm.isTamed()) {
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

    /** 右键领域结界方块 → 破坏领域（对抗中双方同崩者）；§777 起也负责拆白色空间传送门 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        if (level.isClientSide) return InteractionResult.PASS;
        BlockState state = level.getBlockState(context.getClickedPos());

        /*
         * §777：**潜行 + 右键「白色空间传送门」⇒ 拆整扇门**（挪到这里做 ✓）。
         *
         * 为什么不在方块那边做 ✗（§776／§777 日志实证 ✓）：
         * 原版在"潜行 + 手上物品不潜行放行"时会**整段跳过** `Block#use` ✗，而且判定要求**双手都放行** ✗：
         *   boolean flag  = !main.doesSneakBypassUse(...) || !off.doesSneakBypassUse(...);
         *   boolean flag1 = player.isSecondaryUseActive() && flag;
         * NL 里玩家**副手拿着拔刀剑** ✗（`reforged_slashblade` ✓ 它的 bypass 是 false ✓）
         * ⇒ 即使主手天逆鉾对门返回 true ✓，`flag1` 仍然为真 ✗ ⇒ `Block#use` 不被调用 ✗
         * ⇒ 方块那边**永远等不到**这次右键 ✗（测试包能拆只是因为那边副手是空的 ✓）。
         * <p>而**物品自己的 `useOn` 在潜行时照常被调用** ✓（§775 日志实证：客户端/服务端都进了 ✓）
         * ⇒ 放在这里就与"副手拿什么"无关 ✓✓。
         * <p>⚠ 两边各拆的逻辑不会重复执行 ✓：若方块那边的 `use` 这次真的被调用了 ✓，
         * 它会先返回 SUCCESS ⇒ 原版**不会再调** `useOn` ✓；反之只有这里会执行 ✓。
         */
        if (context.getPlayer() != null && context.getPlayer().isSecondaryUseActive()
                && state.is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.WHITE_SPACE_PORTAL.get())) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                TinkersNewlife.LOGGER.info("[白色空间传送门] 天逆鉾拆除（潜行·物品侧）：玩家={} {} {}（手={}）",
                        context.getPlayer().getName().getString(), serverLevel.dimension().location(),
                        context.getClickedPos(), context.getHand());
                com.mofengbaizhi.tinkersnewlife.content.portal.WhiteSpaceDimensions
                        .removePortal(serverLevel, context.getClickedPos());
                level.playSound(null, context.getClickedPos(),
                        net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.9F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Block barrier = com.mofengbaizhi.tinkersnewlife.content.ModBlocks.DOMAIN_BARRIER.get();
        if (!state.is(barrier)) return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer breaker) {
            com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry
                    .breakDomainByBarrier(breaker, (ServerLevel) level, context.getClickedPos());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
