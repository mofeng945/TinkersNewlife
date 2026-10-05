package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * <b>匠魂拔刀剑「katana」</b>（§999 骨架 ＋ §1000 居合斩）。
 *
 * <p>部件（用户口径）：<b>刀鞘 ＋ 刀身 ＋ 坚韧手柄</b> —— 见
 * {@code data/tinkersnewlife/tinkering/tool_definitions/katana.json}（前两个是 §998 新增的自研部件）。
 *
 * <h2>已实现：居合斩（招牌动作 §1000）</h2>
 * <ol>
 *   <li><b>右键长按</b>蓄力（{@code UseAnim.NONE} ⇒ 不借用原版三叉戟那套姿势，与长矛同一处理）；</li>
 *   <li>松开 ⇒ <b>前方扇形</b>范围斩：对视线前方 {@value #RADIUS} 格内、夹角内的活体各造成一次伤害
 *       （伤害 ＝ 工具面板攻击力 × {@value #DAMAGE_MULT}）＋ 朝视线方向<b>前冲</b>一小段
 *       ＋ 挥砍音效与 SWEEP_ATTACK 粒子 ＋ 消耗耐久；</li>
 *   <li><b>蓄满</b>（≥ {@value #FULL_CHARGE_TICKS} tick）⇒ 强化档：范围 {@value #RADIUS_FULL} 格、
 *       伤害 ×{@value #DAMAGE_MULT_FULL}、冲得更远、冷却更长；</li>
 *   <li>冷却：普通 {@value #COOLDOWN_TICKS} tick／满蓄力 {@value #COOLDOWN_TICKS_FULL} tick；</li>
 *   <li>蓄力不足 {@value #MIN_CHARGE_TICKS} tick ⇒ 不触发（避免误触）。</li>
 * </ol>
 *
 * <h2>待做（§996 的 P3/P4）</h2>
 * <ul>
 *   <li><b>P3-2</b>：左键<b>连段</b>（连续命中递增／第 N 段附加斩击）与<b>收刀态</b>区分；</li>
 *   <li><b>P3-3</b>：第一/三人称<b>动画</b>（复用 {@code tnl_anim} ＋ {@code RenderHandEvent} 那套）；</li>
 *   <li><b>P4</b>：与拔刀剑（SlashBlade）深度挂接（挂 {@code BLADESTATE} ＋ 委托它的渲染 ＋ 借它的连段/SA）
 *       ⚠ 前提是 §997 探针证明"它认外来物品"。</li>
 * </ul>
 */
public class KatanaItem extends ModifiableItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:katana}（与 {@code tool_definitions/katana.json} 同名） */
    public static final ToolDefinition KATANA_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "katana"));

    // ── 居合斩参数（手感可调）──────────────────────────────────────────────
    /** 最小蓄力（tick，低于它不触发） */
    private static final int MIN_CHARGE_TICKS = 6;
    /** 满蓄力（tick，达到后走强化档） */
    private static final int FULL_CHARGE_TICKS = 20;
    /** 范围（格，普通／满蓄力） */
    private static final double RADIUS = 3.5D;
    private static final double RADIUS_FULL = 5.0D;
    /** 夹角判据（视线方向与目标方向的点积下限，0.1 ≈ 前方约 168° 的宽扇面） */
    private static final double DOT_MIN = 0.1D;
    /** 伤害倍率（相对工具面板攻击力） */
    private static final float DAMAGE_MULT = 1.0F;
    private static final float DAMAGE_MULT_FULL = 1.4F;
    /** 前冲（格/tick，朝视线方向） */
    private static final double LUNGE = 0.35D;
    private static final double LUNGE_FULL = 0.6D;
    /** 冷却（tick） */
    private static final int COOLDOWN_TICKS = 20;
    private static final int COOLDOWN_TICKS_FULL = 40;

    public KatanaItem(Properties properties) {
        super(properties, KATANA_DEFINITION);
    }

    // ============================================================
    //  居合斩（§1000）
    // ============================================================

    /** 不借用原版三叉戟姿势（与长矛同一处理，否则会叠一层举起旋转） */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ToolStack.from(stack).isBroken()) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player)) return;

        ToolStack tool = ToolStack.from(stack);
        int charge = getUseDuration(stack) - timeLeft;
        if (tool.isBroken() || charge < MIN_CHARGE_TICKS) return;

        // 蓄力进度（0~1）与是否满蓄力
        float ratio = Mth.clamp((charge - MIN_CHARGE_TICKS)
                / (float) (FULL_CHARGE_TICKS - MIN_CHARGE_TICKS), 0.0F, 1.0F);
        boolean full = ratio >= 0.999F;

        if (!level.isClientSide) {
            iaiSlash(level, player, tool, full);
        }
        player.getCooldowns().addCooldown(this, full ? COOLDOWN_TICKS_FULL : COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /** 前方扇形斩：范围伤害 ＋ 前冲 ＋ 音效粒子 ＋ 耐久 */
    private static void iaiSlash(Level level, Player player, ToolStack tool, boolean full) {
        double radius = full ? RADIUS_FULL : RADIUS;
        Vec3 look = player.getLookAngle();

        // 伤害基准：工具面板攻击力（没给就保底 1 点）
        float base = Math.max(1.0F, tool.getStats().get(ToolStats.ATTACK_DAMAGE));
        float damage = base * (full ? DAMAGE_MULT_FULL : DAMAGE_MULT);
        DamageSource source = player.damageSources().playerAttack(player);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !e.isAlliedTo(player) && !e.isSpectator())) {
            Vec3 dir = target.position().subtract(player.position());
            if (dir.lengthSqr() > 1.0E-4D && look.dot(dir.normalize()) < DOT_MIN) continue;   // 只打前方扇面
            target.invulnerableTime = 0;                                                     // 免得被无敌帧吃掉
            if (target.hurt(source, damage)) {
                target.knockback(full ? 0.7D : 0.4D, look.x, look.z);
            }
        }

        // 前冲（朝视线，抬高一点点便于跨格）
        Vec3 lunge = look.scale(full ? LUNGE_FULL : LUNGE);
        player.setDeltaMovement(player.getDeltaMovement().add(lunge.x, 0.05D, lunge.z));
        player.hasImpulse = true;
        player.hurtMarked = true;

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, full ? 0.8F : 1.1F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    player.getX() + look.x * 1.2D, player.getY() + 1.0D, player.getZ() + look.z * 1.2D,
                    full ? 6 : 3, look.x * 0.2D, 0.0D, look.z * 0.2D, 0.0D);
        }
        ToolDamageUtil.damageAnimated(tool, full ? 2 : 1, player, player.getUsedItemHand());
    }
}
