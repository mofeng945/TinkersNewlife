package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipLashEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipPhysics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>鞭子</b>（§1053）—— 匠魂工具 ✓，部件 ＝ <b>坚韧手柄 ＋ 大板 ＋ 弓弦</b>（用户口径 ✓，
 * 三个都是本体标准件 ⇒ 不需要任何新图案/铸型 ✓）。
 *
 * <h2>行为（时间轴与数值全部照参照模组 BetterWhips ✓ MIT ✓ 见 {@code LICENSES/BetterWhips-MIT.txt}）</h2>
 * <ul>
 *   <li><b>左键</b>：触发一次抽击 ✓（{@link WhipLashEntity#startLash} ✓）；
 *       伤害不由近战直接给 ✗，而是由鞭身<b>逐段扫掠</b>按段速度折算 ✓
 *       （{@code floor(速度/10) × 0.2} ✓，并按已命中目标数逐次减半 ✓）；</li>
 *   <li><b>右键</b>：长按蓄力（上限 {@link WhipPhysics#RIGHT_CHARGE_TICKS} tick ＝ 3 秒 ✓）
 *       ⇒ 绳子绕手自转甩成一张盘 ✓；松手 ⇒ 14 tick 钟摆式下抽 ＋ 落地<b>冲击波</b> ✓；</li>
 *   <li><b>攻击间隔</b>：照它的 {@code attackPeriodTicks} ＝ {@code ceil(攻击冷却)} ✓
 *       ⇒ 起手段／抽击段的 tick 数是<b>跟着攻速属性走</b>的 ✓（攻速越高抽得越快 ✓）。</li>
 * </ul>
 */
public class WhipItem extends ModifiableItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:whip} ✓（与 {@code tool_definitions/whip.json} 同名 ✓） */
    public static final ToolDefinition WHIP_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "whip"));

    /** 伤害口径（照它的 {@code LeatherWhipItem.damageForSpeed} ✓）：每满 10 格/秒 = 0.2 伤害 ✓ */
    private static final double DAMAGE_PER_TEN_BLOCKS_PER_SECOND = 0.2D;
    /** 攻击间隔下限 ✓（照它的 {@code MIN_ATTACK_PERIOD_TICKS = 1} ✓） */
    private static final int MIN_ATTACK_PERIOD_TICKS = 1;

    public WhipItem(Properties properties) {
        super(properties, WHIP_DEFINITION);
    }

    /** 攻击间隔（tick ✓）：照它的实现 ✓ —— 由当前手持物的攻击冷却决定 ✓ */
    public static int attackPeriodTicks(Player player) {
        return Math.max(MIN_ATTACK_PERIOD_TICKS, (int) Math.ceil(player.getCurrentItemAttackStrengthDelay()));
    }

    /** 速度 ⇒ 伤害（照它 ✓）：{@code floor(速度/10) × 0.2} ✓ */
    public static float damageForSpeed(double speedBlocksPerSecond) {
        double speed = Math.max(0.0D, speedBlocksPerSecond);
        return (float) (Math.floor(speed / 10.0D) * DAMAGE_PER_TEN_BLOCKS_PER_SECOND);
    }

    // ==================== 右键：蓄力 → 砸地 ====================

    @Override
    public int getUseDuration(ItemStack stack) {
        return WhipPhysics.RIGHT_CHARGE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;                       // 起手姿势先借用"拉弓"✓（想换只改这一行 ✓）
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 先让匠魂处理它的交互类改装（有改装吃掉右键就不抢 ✓）
        InteractionResultHolder<ItemStack> tinker = super.use(level, player, hand);
        if (tinker.getResult().consumesAction()) {
            return tinker;
        }
        if (isBroken(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            WhipLashEntity.startCharge(player);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player) || level.isClientSide) {
            return;
        }
        WhipLashEntity.releaseCharge(player);
    }

    // ==================== 左键：抽击 ====================

    /**
     * 玩家"挥了一下手"就会被调用 ✓（原版空挥／打到方块都会触发 ✓）。
     * <p>⚠ 本仓实测过这个钩子确实会触发 ✓（唐横刀当年就用它拿到过左键 ✓）。
     */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (!(entity instanceof Player player) || player.level().isClientSide) {
            return false;
        }
        if (isBroken(stack)) {
            return false;
        }
        // 攻击冷却没好就不甩 ✓（攻速由匠魂的 attack_speed 面板决定 ✓，与 attackPeriodTicks 同源 ✓）
        if (player.getAttackStrengthScale(0.0F) < 0.9F) {
            return false;
        }
        WhipLashEntity.startLash(player);
        if (!player.getAbilities().instabuild) {
            ToolDamageUtil.damageAnimated(ToolStack.from(stack), 1, player, player.getUsedItemHand());
        }
        return false;                             // 不取消这次挥击本身 ✓
    }

    // ==================== 工具数据 ====================

    private static boolean isBroken(ItemStack stack) {
        try {
            return ToolStack.from(stack).isBroken();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
