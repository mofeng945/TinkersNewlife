package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipLashEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * <b>鞭子</b>（§1047）—— 匠魂工具 ✓，部件 ＝ <b>坚韧手柄 ＋ 大板 ＋ 弓弦</b>（用户口径 ✓）。
 *
 * <h2>为什么这三个部件不用做新配方（用户关心的点 ✓）</h2>
 * 三个都是<b>本体标准部件</b> ✓ ⇒ 玩家已有的图案/铸型直接能用 ✓，不新增任何配方/模具 ✓
 * （同弹弓的路子 ✓）。
 *
 * <h2>行为（模仿 BetterWhips 的手感 ✓ 但代码全部自研 ✗ 未抄它）</h2>
 * <ol>
 *   <li><b>左键挥鞭</b> ✓：生成一条 {@link WhipLashEntity}（相位 = 挥击 ✓）⇒
 *       根部被弹簧朝准星方向甩出去 ✓、运动沿绳传到梢部 ✓（§{@code WhipPhysics} ✓）；
 *       伤害<b>不由近战直接给</b> ✗，而是由鞭身<b>逐段扫掠</b>命中时"按接触点速度"折算 ✓；</li>
 *   <li><b>右键蓄力 → 砸地冲击波</b> ✓：长按 2 秒（{@link #SLAM_CHARGE_TICKS} ✓，上限 3 秒 ✓）后松手 ⇒
 *       生成相位 = 砸地的鞭击 ✓，落地瞬间对周围敌人来一发 AoE ✓；</li>
 *   <li><b>近战直接命中仍然保留</b> ✓：贴脸时左键打到的目标照常吃一次普通近战伤害 ✓
 *       （鞭身那一层再追加 ✓）—— 这样"鞭子打不到人"的挫败感不会出现 ✓；
 *       想改成 BetterWhips 那种"只有鞭身结算"，说一句即可 ✓。</li>
 * </ol>
 *
 * <p>⚠ 所有工具数据都要 {@code ToolStack.from} ✓：读不到（比如创造栏里还没材料 ✓）就当作 0 ✓ 绝不抛错 ✓。
 */
public class WhipItem extends ModifiableItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:whip} ✓（与 {@code tool_definitions/whip.json} 同名 ✓） */
    public static final ToolDefinition WHIP_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "whip"));

    /** 蓄力上限（tick ✓）：3 秒 ✓（同 BetterWhips 的 60 ✓） */
    private static final int MAX_CHARGE_TICKS = 60;
    /** 松手时至少蓄了这么多 tick 才算"砸地" ✓（2 秒 ✓） */
    private static final int SLAM_CHARGE_TICKS = 40;
    /** 蓄力不足时松手 ⇒ 轻轻抽一下 ✓（这个 tick 数以上才算数 ✓） */
    private static final int MIN_FLICK_TICKS = 5;

    /** 挥击寿命（tick ✓）：12 tick ≈ 0.6 秒 ✓ 够甩完一趟 ✓ */
    private static final int LASH_LIFE_TICKS = 12;
    /** 砸地寿命（tick ✓）：比挥击长一点 ✓ 让冲击波与收势都看得见 ✓ */
    private static final int SLAM_LIFE_TICKS = 24;
    /** 面板读不到时的兜底伤害 ✓ */
    private static final float FALLBACK_PANEL = 3.0F;

    public WhipItem(Properties properties) {
        super(properties, WHIP_DEFINITION);
    }

    // ============================================================
    //  右键：蓄力 ⇒ 砸地
    // ============================================================

    @Override
    public int getUseDuration(ItemStack stack) {
        return MAX_CHARGE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;                       // 起手姿势先借用"拉弓"✓（想换姿势只改这一行 ✓）
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 先让匠魂自己处理它的交互类改装（有改装吃掉右键就不抢 ✓）
        InteractionResultHolder<ItemStack> tinker = super.use(level, player, hand);
        if (tinker.getResult().consumesAction()) {
            return tinker;
        }
        if (isBroken(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player) || level.isClientSide) {
            return;
        }
        int charge = getUseDuration(stack) - timeLeft;

        if (charge >= SLAM_CHARGE_TICKS) {
            spawnLash(player, WhipLashEntity.PHASE_SLAM, SLAM_LIFE_TICKS);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.9F, 0.7F);
        } else if (charge >= MIN_FLICK_TICKS) {
            spawnLash(player, WhipLashEntity.PHASE_LASH, LASH_LIFE_TICKS);
        }
    }

    // ============================================================
    //  左键：挥鞭
    // ============================================================

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
        // 攻击冷却没好就不甩 ✓（攻击速度由匠魂的 attack_speed 面板决定 ✓）
        if (player.getAttackStrengthScale(0.0F) < 0.9F) {
            return false;
        }
        spawnLash(player, WhipLashEntity.PHASE_LASH, LASH_LIFE_TICKS);
        ToolDamageUtil.damageAnimated(ToolStack.from(stack), 1, player, player.getUsedItemHand());
        return false;                       // 不取消这次挥击本身 ✓（近战直接命中照旧生效 ✓）
    }

    // ============================================================
    //  工具数据
    // ============================================================

    private static boolean isBroken(ItemStack stack) {
        try {
            return ToolStack.from(stack).isBroken();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 面板攻击力（鞭身伤害的基数 ✓）：读不到就退回兜底值 ✓ */
    private static float panelDamage(ItemStack stack) {
        try {
            float panel = ToolStack.from(stack).getStats().get(ToolStats.ATTACK_DAMAGE);
            return panel > 0.0F ? panel : FALLBACK_PANEL;
        } catch (Throwable ignored) {
            return FALLBACK_PANEL;
        }
    }

    private static void spawnLash(Player player, int phase, int lifeTicks) {
        try {
            WhipLashEntity lash = new WhipLashEntity(player.level(), player, phase, lifeTicks,
                    panelDamage(player.getMainHandItem()));
            player.level().addFreshEntity(lash);
        } catch (Throwable ignored) {
            // 光效/判定失败不影响武器本身 ✓
        }
    }
}
