package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

/**
 * <b>西洋剑（Rapier）</b>—— 移植自**匠魂2**的同名武器 ✓（§943 起）。
 *
 * <h2>匠魂2 原版的五条特征（[MC百科](https://www.mcmod.cn/item/51928.html) 查证 ✓）</h2>
 * <ol>
 *   <li><b>攻击伤害较低</b> ✓ 但 <b>攻速更快</b> ✓ —— 已写进
 *       {@code data/tinkersnewlife/tinkering/tool_definitions/rapier.json} ✓
 *       （{@code attack_damage 3.0 × 0.8} ✓ {@code attack_speed 2.0} ✓＝匕首档 ✓ 见 §945 ✓）；</li>
 *   <li><b>无视目标护甲</b>造成伤害 ✓ —— **阶段 2** 用"**方案 B**"实现 ✓
 *       （1.20.1 的 {@code bypasses_armor} 是**伤害类型标签**驱动的 ✗ 用它会波及所有同类型攻击 ✗
 *       ⇒ 改为在 {@code LivingHurtEvent}（护甲前）与 {@code LivingDamageEvent}（护甲后）之间
 *       **把被护甲减掉的差额补回来** ✓ 只影响本武器 ✓）；</li>
 *   <li>合成 **剑刃 ＋ 手柄 ＋ 十字柄** ✓ —— 匠魂3 没有独立十字柄 ✗ ⇒ 用 {@code tough_handle} 顶位 ✓
 *       （部件真名实查 TC jar ✓ 见 §944 ✓）；</li>
 *   <li><b>右键后跳</b>闪避 ✓ —— **阶段 3**；</li>
 *   <li><b>手持时副手盾牌等无法使用</b> ✓ —— **阶段 4**。</li>
 * </ol>
 *
 * <p>⚠ 当前进度：**阶段 1**（本类 ＋ 注册 ＋ 定义 ＋ 模型 ＋ 占位贴图 ✓）；
 * 阶段 2–5 见 {@code docs/开发备忘录.md} §943 计划 ✓。
 *
 * <p>⚠ 与长矛不同，西洋剑**没有**蓄力/冲锋机制 ✓（匠魂2 那版是纯刺击 ＋ 右键后跳 ✓），
 * 所以本类暂时只是"带定义的可改造工具" ✓ —— 后跳等行为在阶段 3 加 ✓。
 */
public class RapierItem extends ModifiableItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:rapier} ✓（与 {@code tool_definitions/rapier.json} 同名 ✓） */
    public static final ToolDefinition RAPIER_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "rapier"));

    public RapierItem(Properties properties) {
        super(properties, RAPIER_DEFINITION);
    }

    // ============================================================
    //  §949 阶段 3：右键后跳（匠魂2 原版特征之一 ✓）
    //    "右击可以进行一次后跳以躲避伤害" ✓
    // ============================================================

    /** 后退冲量（初速度 ✓ 实机调 ✓ 0.85 大致是"一步半"的距离感 ✓） */
    private static final double BACKSTEP_IMPULSE = 0.85D;
    /** 附带的一点点上抬 ✓（0 = 纯水平 ✓ 0.12 能跳过一格边角 ✓） */
    private static final double BACKSTEP_LIFT = 0.12D;
    /** 后跳给的短暂无敌帧 ✓（tick ✓ 原版受击无敌是 20 ✓ 这里取一半 = 0.5 秒 ✓ 够躲一下 ✓） */
    private static final int BACKSTEP_INVULNERABLE_TICKS = 10;
    /** 后跳冷却 ✓（tick ✓ 1 秒 ✓ 防止无限刷无敌 ✓） */
    public static final int BACKSTEP_COOLDOWN_TICKS = 20;

    /**
     * 右键 = **后跳**（朝视线**反方向**弹开 ✓）。
     *
     * <p>三件事一起做 ✓：<b>冲量</b>（{@code push} 反方向 ＋ 轻微上抬 ✓）、
     * <b>短无敌</b>（{@code invulnerableTime} ✓）、<b>冷却</b>（{@code ItemCooldowns} ✓）。
     *
     * <p>⚠ 细节口径：
     * <ul>
     *   <li><b>潜行右键不动手</b> ✓（留给别的交互/放置 ✓ 否则潜行时没法用副手/方块 ✓）；</li>
     *   <li><b>只在服务端</b>做冲量与冷却 ✓（`level.isClientSide` 挡掉 ✓）—— 但**两端都返回 success** ✓
     *       ⇒ 客户端会正常播"使用"动作且**吃掉这次右键** ✓（这正是阶段 4"副手盾牌用不了"的一半 ✓）；</li>
     *   <li>{@code hurtMarked = true} ✓ 把速度同步给客户端 ✓（不然本地看不到位移 ✓）；</li>
     *   <li>不想"右键变挥砍"所以**不调** {@code swing} ✗。</li>
     * </ul>
     */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown() || player.getCooldowns().isOnCooldown(this)) {
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            player.push(-look.x * BACKSTEP_IMPULSE, BACKSTEP_LIFT, -look.z * BACKSTEP_IMPULSE);
            player.hurtMarked = true;
            player.invulnerableTime = Math.max(player.invulnerableTime, BACKSTEP_INVULNERABLE_TICKS);
            player.getCooldowns().addCooldown(this, BACKSTEP_COOLDOWN_TICKS);
        }
        return net.minecraft.world.InteractionResultHolder.success(stack);
    }
}
