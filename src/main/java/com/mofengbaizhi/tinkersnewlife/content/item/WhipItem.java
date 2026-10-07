package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipLashEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipPhysics;
import com.mofengbaizhi.tinkersnewlife.content.handler.WhipBlockHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * <b>鞭子</b>（§1053）—— 匠魂工具 ✓，部件 ＝ <b>坚韧手柄 ＋ 大板 ＋ 弓弦</b>（用户口径 ✓，
 * 三个都是本体标准件 ⇒ 不需要任何新图案/铸型 ✓）。
 *
 * <h2>行为（时间轴与数值全部照参照模组 BetterWhips ✓ MIT ✓ 见 {@code LICENSES/BetterWhips-MIT.txt}）</h2>
 * <ul>
 *   <li><b>左键</b>：触发一次抽击 ✓（{@link WhipLashEntity#startLash} ✓）；
 *       伤害不由近战直接给 ✗，而是由鞭身<b>逐段扫掠</b>按段速度折算 ✓
 *       （{@code floor(速度/10) × 0.2} ✓，并按已命中目标数逐次减半 ✓）；</li>
 *   <li><b>右键</b>：<b>收回鞭身 ＋ 举械格挡</b> ✓（§1058 用户口径 ✓）—— 按住右键持续格挡 ✓，
 *       举盾 0.5 秒内挨打（或挨打后 0.5 秒内按右键）算<b>完美格挡</b> ✓；
 *       ⚠ §1118k：原来这里写的是"长按蓄力 → 松手砸地"✗ —— 那套已随右键改格挡删除 ✓（死代码 ✓）；</li>
 *   <li><b>攻击间隔</b>：照它的 {@code attackPeriodTicks} ＝ {@code ceil(攻击冷却)} ✓
 *       ⇒ 起手段／抽击段的 tick 数是<b>跟着攻速属性走</b>的 ✓（攻速越高抽得越快 ✓）。</li>
 * </ul>
 *
 * <p>⭐ §1118o：抽击现在还会吃<b>弓弦系</b>的远程加成 ✓（匠魂 {@code power 力量}／{@code punch 冲击} ＋
 * 原版 <b>力量</b>／<b>冲击</b>／<b>火矢</b> ✓）—— 见本类里 {@code rangedDamageMultiplier} 那一组 ✓。
 */
public class WhipItem extends ModifiableItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:whip} ✓（与 {@code tool_definitions/whip.json} 同名 ✓） */
    public static final ToolDefinition WHIP_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "whip"));

    /** 伤害口径（照它的 {@code LeatherWhipItem.damageForSpeed} ✓）：每满 10 格/秒 = 0.2 伤害 ✓ */
    private static final double DAMAGE_PER_TEN_BLOCKS_PER_SECOND = 0.2D;
    /** 攻击间隔下限 ✓（照它的 {@code MIN_ATTACK_PERIOD_TICKS = 1} ✓） */
    private static final int MIN_ATTACK_PERIOD_TICKS = 1;

    // ============ §1054 用户口径：挥动伤害与速度由匠魂的「攻击力 / 攻速」属性决定 ✓ ============
    /** 基准攻击面板 ＝ 本鞭定义里的基础攻击力 ✓（{@code tool_definitions/whip.json} 的 3.5 ✓） */
    public static final float REFERENCE_PANEL = 3.5F;
    /** 基准攻击间隔（tick ✓）：本鞭基础攻速 1.6 ⇒ ⌈20 / 1.6⌉ ＝ 13 ✓（起手 3 ＋ 抽击 4 的基准 ✓） */
    public static final int REFERENCE_PERIOD_TICKS = 13;

    public WhipItem(Properties properties) {
        super(properties, WHIP_DEFINITION);
    }

    /** 攻击间隔（tick ✓）：照它的实现 ✓ —— 由当前手持物的攻击冷却决定 ✓ */
    public static int attackPeriodTicks(Player player) {
        return Math.max(MIN_ATTACK_PERIOD_TICKS, (int) Math.ceil(player.getCurrentItemAttackStrengthDelay()));
    }

    /**
     * 速度 ⇒ 伤害 ✓：{@code floor(速度/10) × 0.2 × (面板 / 基准面板)} ✓
     * <p>⚠ 用户口径（2026-10-05）：「<b>根据鞭子的攻击和攻速属性来决定鞭子的挥动伤害和速度</b>」✓
     * ⇒ 面板 ＝ 基准 3.5 时与参照口径完全一致 ✓；面板越高越疼 ✓（匠魂材料／改装都能影响 ✓）。
     */
    public static float damageForSpeed(double speedBlocksPerSecond, float panel) {
        double steps = Math.floor(Math.max(0.0D, speedBlocksPerSecond) / 10.0D);
        double panelScale = Mth.clamp(panel / REFERENCE_PANEL, 0.1D, 8.0D);
        return (float) (steps * DAMAGE_PER_TEN_BLOCKS_PER_SECOND * panelScale);
    }

    /** 攻击面板（匠魂 {@code attack_damage} 属性 ✓）—— 读不到（创造栏/未组装 ✓）就退回基准 ✓ */
    public static float attackPanel(ItemStack stack) {
        try {
            float panel = ToolStack.from(stack).getStats().get(ToolStats.ATTACK_DAMAGE);
            return panel > 0.0F ? panel : REFERENCE_PANEL;
        } catch (Throwable ignored) {
            return REFERENCE_PANEL;
        }
    }

    /**
     * ⚠ §1055：<b>不要再拿它去拉长驱动 tick</b> ✗ —— §1054 我就是这么干的 ✗，
     * 结果攻速偏慢的鞭子抽击段被拉到 5~10 tick ✗ ⇒ 手在同一段弧上变慢 ✗ ⇒
     * 用户实测「<b>鞭子挥不远了</b>」✗。
     * <p>正确做法（参照 {@code ArmMotor} 的原公式 ✓）：攻速只决定
     * <b>冷却</b>（{@link #attackPeriodTicks} ✓）与"快攻速 ⇒ 更短的起手/抽击" ✓，
     * 而<b>抽击段永远封顶 4 tick</b> ✓ ⇒ 手始终够快、鞭子始终甩得远 ✓。
     * <p>这个方法只保留"基准倍率"的语义（1.0 ＝ 本鞭基础攻速 ✓），供以后做手感微调参考 ✓。
     */
    public static double swingTimeScale(Player player) {
        return Mth.clamp(attackPeriodTicks(player) / (double) REFERENCE_PERIOD_TICKS, 0.5D, 2.5D);
    }

    // ==================== §1118o 远程加成：抽击也吃"弓弦系"远程效果 ====================

    /**
     * ⭐ §1118o <b>用户口径</b>：「弓弦可以附加远程特性，但是我鞭子抽打吃不到原版的远程效果，能优化吗」✓
     *
     * <h2>为什么以前吃不到（两层原因 ✓ 都查清了）</h2>
     * <ol>
     *   <li><b>装不上</b> ✗：匠魂的远程词条要求工具在 {@code tconstruct:modifiable/ranged} 标签里 ✓，
     *       而长鞭原先只挂了 melee/weapon 等 7 个标签 ✗（见 {@code data/tconstruct/tags/items/modifiable/**} ✓）
     *       ⇒ 已补 {@code .../modifiable/ranged.json} ✓ 把长鞭纳入 ✓；</li>
     *   <li><b>装上也不生效</b> ✗：那些词条的效果走 {@code ProjectileLaunchModifierHook} /
     *       {@code ProjectileHitModifierHook}（<b>弹射物专属</b> ✗）⇒ 抽击根本不经过它们 ✗
     *       ⇒ 由本组方法在<b>抽击结算</b>里补等效效果 ✓。</li>
     * </ol>
     *
     * <h2>映射口径（明确列出 ✓ 便于你调 ✓）</h2>
     * <table border="1">
     *   <tr><th>来源</th><th>效果</th></tr>
     *   <tr><td>匠魂 {@code tconstruct:power}（力量 ✓ 每级）</td><td>抽击伤害 <b>+10%</b> ✓</td></tr>
     *   <tr><td>原版 <b>力量</b>（Power ✓ 每级）</td><td>抽击伤害 <b>+25%</b> ✓（照原版箭的加成比例 ✓）</td></tr>
     *   <tr><td>匠魂 {@code tconstruct:punch}（冲击 ✓ 每级）</td><td>击退 <b>+0.4 格</b> ✓</td></tr>
     *   <tr><td>原版 <b>冲击</b>（Punch ✓ 每级）</td><td>击退 <b>+0.5 格</b> ✓</td></tr>
     *   <tr><td>原版 <b>火矢</b>（Flame ✓ 任意级）</td><td>点燃目标 <b>5 秒</b> ✓（照原版 ✓）</td></tr>
     * </table>
     * <p>⚠ <b>刻意不映射</b> ✗（弹射物专属、对鞭子无意义 ✓ 不硬凑 ✓）：
     * 匠魂 {@code trueshot 神射} ✓ {@code quick_charge 快速装填} ✓ {@code drawback 回拉} ✓
     * {@code impaling 穿刺} ✓ {@code spike} ✓ {@code multishot 多重射击} ✓ 以及裸弓/箭袋/水晶那几类 ✓；
     * 原版 <b>无限</b> ✓ <b>多重射击</b> ✓ <b>穿透</b> ✓ <b>快速装填</b> ✓（无弹药概念 ✓）。
     */
    private static final double TCONSTRUCT_POWER_DAMAGE_PER_LEVEL = 0.10D;
    private static final double VANILLA_POWER_DAMAGE_PER_LEVEL = 0.25D;
    private static final double TCONSTRUCT_PUNCH_KNOCKBACK_PER_LEVEL = 0.4D;
    private static final double VANILLA_PUNCH_KNOCKBACK_PER_LEVEL = 0.5D;
    /** 原版火矢的点燃秒数 ✓（与 {@code FlameEnchantment} 一致 ✓） */
    private static final int VANILLA_FLAME_IGNITE_SECONDS = 5;

    private static final ModifierId TCONSTRUCT_POWER =
            new ModifierId(new ResourceLocation("tconstruct", "power"));
    private static final ModifierId TCONSTRUCT_PUNCH =
            new ModifierId(new ResourceLocation("tconstruct", "punch"));

    /** 匠魂词条等级（破损即失效 ✓ 复用本仓统一口径 ✓；拿不到就 0 ✓ 绝不影响基础伤害 ✓） */
    private static int tconstructLevel(ItemStack stack, ModifierId id) {
        try {
            return ToolHelper.getActiveModifierLevel(ToolStack.from(stack), id);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 原版附魔等级（拿不到就 0 ✓） */
    private static int enchantLevel(ItemStack stack, Enchantment enchantment) {
        try {
            return net.minecraft.world.item.enchantment.EnchantmentHelper
                    .getItemEnchantmentLevel(enchantment, stack);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /**
     * 抽击伤害倍率 ✓ —— 匠魂「力量」＋ 原版「力量」。
     * <p>⚠ 只在<b>命中结算</b>时乘 ✓（不参与 {@link #damageForSpeed} 的"速度→伤害"口径 ✗
     * ⇒ 保证"面板/速度"那套用户口径不变 ✓）。
     */
    public static float rangedDamageMultiplier(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 1.0F;
        }
        double bonus = 0.0D;
        bonus += TCONSTRUCT_POWER_DAMAGE_PER_LEVEL * tconstructLevel(stack, TCONSTRUCT_POWER);
        bonus += VANILLA_POWER_DAMAGE_PER_LEVEL
                * enchantLevel(stack, Enchantments.POWER_ARROWS);
        return (float) (1.0D + bonus);
    }

    /** 抽击击退加成（格 ✓）—— 匠魂「冲击」＋ 原版「冲击」 */
    public static double rangedKnockback(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0D;
        }
        return TCONSTRUCT_PUNCH_KNOCKBACK_PER_LEVEL * tconstructLevel(stack, TCONSTRUCT_PUNCH)
                + VANILLA_PUNCH_KNOCKBACK_PER_LEVEL
                * enchantLevel(stack, Enchantments.PUNCH_ARROWS);
    }

    /** 抽击点燃秒数 ✓ —— 只有原版「火矢」有这一项（匠魂的 fiery 近战本来就生效 ✓ 不重复 ✓） */
    public static int rangedIgniteSeconds(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        // ⚠ 1.20.1 的映射名是 FLAMING_ARROWS ✓（不是 FLAME ✗ —— 编译报错后改的 ✓）
        return enchantLevel(stack, Enchantments.FLAMING_ARROWS) > 0 ? VANILLA_FLAME_IGNITE_SECONDS : 0;
    }

    // ==================== 右键：收回鞭身 / 举械格挡（§1058 ✓） ====================

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;                             // §1058 格挡：一直举着 ✓ 松手才结束 ✓
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // §1060 用户口径：「格挡动画是举盾动画，不是举三叉戟动画」✓ ⇒ 用 UseAnim.BLOCK（举盾姿势 ✓）。
        // ⚠ 但原版 LivingEntity#isBlocking() **只看使用动画** ✗ ⇒ 用 BLOCK 会被原版当成盾牌**全额免伤** ✗，
        //   那样"普通格挡只减 40%"就永远不生效 ✗。
        //   ⇒ 解法：仍然用 BLOCK 姿势 ✓，但在 WhipBlockHandler#onShieldBlock 里
        //     **取消原版那一次盾牌结算**（ShieldBlockEvent ✓）⇒ 伤害交回 LivingHurtEvent 由我们接管 ✓。
        return UseAnim.BLOCK;
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
        boolean startGuard = true;
        if (!level.isClientSide) {
            // §1059 完美格挡后必须松手 ✓：锁住期间**每次**尝试都续锁（见 WhipBlockHandler.noteBlockAttempt ✓）
            // ⇒ 一直按住右键是举不起盾的 ✓，必须松开 ✓
            if (!WhipBlockHandler.canStartBlock(player)) {
                WhipBlockHandler.noteBlockAttempt(player);
                return InteractionResultHolder.pass(stack);
            }
            // §1058 用户口径：右键 ＝ ① 收回没收回的鞭身 ✓ ② 开启格挡 ✓
            WhipLashEntity.retract(player);
            // ③ "挨打后 0.5 秒内举盾"也算完美格挡 ✓（把那一次退回来并全额反射 ✓）
            //    这条一旦成立 ⇒ 内部已解除 use 并上锁 ✓ ⇒ 本次不再举盾 ✓（必须松手重置 ✓）
            if (WhipBlockHandler.onBlockStarted(player)) {
                startGuard = false;
            }
        }
        if (startGuard) {
            player.startUsingItem(hand);          // 开启格挡 ✓（按住右键持续 ✓）
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player) || level.isClientSide) {
            return;
        }
        // §1058 右键已改为格挡 ⇒ 松手就是结束格挡 ✓
        //（原来的"蓄力 → 砸地"不再由右键触发 ✓，实体里的蓄力/释放段与物理里的受力代码都保留 ✓ 以后想用随时可接回 ✓）
        WhipBlockHandler.onBlockEnded(player);
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
        // §1057 冷却 ＝ 攻速（attackPeriodTicks）✓ —— startLash 内部判断冷却并重置攻击冷却 ✓
        WhipLashEntity.startLash(player);
        if (!player.getAbilities().instabuild) {
            ToolDamageUtil.damageAnimated(ToolStack.from(stack), 1, player, player.getUsedItemHand());
        }
        return false;                             // 不取消这次挥击本身 ✓
    }

    /**
     * ⚠ 用户口径（2026-10-05）：「<b>让鞭子左键近战攻击伤害彻底取消</b>」✓
     * ⇒ 返回 {@code true} ＝ <b>取消这次近战命中</b> ✗ —— 鞭子的伤害只由鞭身逐段扫掠给 ✓
     * （见 {@code WhipLashEntity#tickLashDamage} ✓）。
     * <p>挥击动作本身照旧发生 ✓（挥击包与命中包是两条路 ✓）⇒ {@link #onEntitySwing} 仍会触发抽击 ✓。
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) {
        // 打实体时挥击包**不一定**会来 ✓ ⇒ 这里补一次抽击 ✓（冷却判断在 startLash 里 ✓）
        if (!player.level().isClientSide && !isBroken(stack)) {
            WhipLashEntity.startLash(player);
        }
        return true;                              // ⚠ 无论如何都取消这次近战伤害 ✓
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
