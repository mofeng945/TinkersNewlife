package com.mofengbaizhi.tinkersnewlife.content.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.SpearChargeHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * <b>长矛（Spear）</b>—— 把 MC <b>1.21.11《Mounts of Mayhem》</b>的原版长矛移植成匠魂武器（§835）。
 *
 * <h2>为什么是"重写"而不是"搬代码"</h2>
 * 1.20.1 里**没有**这个物品（用户本机 1.21.1 客户端 jar 里连 {@code spear} 条目都是 0 个 ✗，已实查 §835），
 * 而 1.21.11 是 Java 版**最后一个混淆版本** ✗ ⇒ 原版 {@code SpearItem} 的类名/字段都读不出来，
 * 只有**资产与数据**能读（贴图/模型/配方/伤害类型/突进附魔 ✓ 已全部导出到
 * {@code build/tmp-mcsrc/ref1211/} ✓）⇒ 逻辑按官方 wiki 的机制描述在本仓**重写** ✓（数值口径见 §835）。
 *
 * <h2>部件（用户口径 ✓）</h2>
 * 宽刃 {@code tconstruct:broad_blade} ×1 ＋ 坚韧手柄 {@code tconstruct:tough_handle} ×2 ✓
 * （两个手柄各按 {@code scale: 0.5} 计权 ✓ 照匠魂自己镰刀/劈刀那种写法 ✓）
 * ⇒ 全部复用匠魂标准件 ✓ **不需要**自定义部件配方 ✓。
 *
 * <h2>照搬过来的原版特征</h2>
 * <ul>
 *   <li><b>右键长按＝冲锋</b> ✓（用户口径「冲锋按原版右键就好」✓）—— 实现在 {@link SpearChargeHandler} ✓
 *       三阶段：Engaged（伤害＋击退＋可把骑乘者<b>打下马</b>）→ Tired（伤害＋击退）→ Disengaged（只有伤害）✓
 *       之后回到 idle ⇒ 必须松手重来 ✓（照 wiki 描述 ✓）；</li>
 *   <li><b>距离更远</b> ⇒ 实体交互距离 <b>+1.5 格</b>（原版长矛最大 4.5 格 vs 玩家本体 3 格 ✓）；</li>
 *   <li><b>近身打不到</b> ⇒ 冲锋有最小距离 ✓ ＋ 普通攻击在 1.5 格内被取消 ✓
 *       （{@code SpearCombatHandler} ✓ 原版口径是"最小 2 格"，我们取 1.5 格更好上手 ✓ 自定 ✓）；</li>
 *   <li><b>不能挖方块</b> ✓ —— 工具定义里 {@code mining_speed} 基础 0 ＋ 倍率 0.1 ⇒ 怎么配都挖不动 ✓。</li>
 * </ul>
 *
 * <h2>⚠ 故意不写的（用户口径 §812）</h2>
 * 不写 {@code appendHoverText} ✗ —— 用户口径「以后我没说一律不加工具提示」✓。
 */
public class SpearItem extends ModifiableItem {

    public static final ToolDefinition SPEAR_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "spear"));

    /** 固定 UUID ⇒ 幂等 ✓ */
    private static final UUID REACH_UUID = UUID.fromString("d8e2f3a4-b5c6-4d7e-9f01-2a3b4c5d6e7f");
    /** 实体交互距离 +1.5 格（原版长矛 4.5 vs 玩家本体 3.0 ✓） */
    public static final double REACH_BONUS = 1.5D;

    /** 冲锋最小距离（近身打不到 ✓ 原版核心特征；原版写"最小 2 格"，这里取 1.5 更好上手 ✓ 自定 ✓） */
    public static final double MIN_RANGE = 1.5D;
    /** 视线锥角 ±30°（cos30° ≈ 0.866 ✓ 自定 ✓） */
    public static final double AIM_CONE_COS = 0.866D;

    /** 三阶段的分界 tick 数（<b>自定</b> ✗ 原版常量在混淆代码里读不到 ⇒ 见 §835 如实说明 ✓） */
    public static final int ENGAGED_END = 10;
    public static final int TIRED_END = 28;
    public static final int DISENGAGED_END = 50;

    /** 各阶段的"双方接近速度"门槛（格/tick ✓ 自定 ✓）：阶段越高越难触发伤害 ✓ */
    public static final double[] STAGE_MIN_CLOSING = {0.12D, 0.18D, 0.25D};
    /** 各阶段的伤害倍率（乘玩家面板攻击力 ✓ 自定 ✓）：阶段越高越轻 ✓ */
    public static final float[] STAGE_DAMAGE_MULT = {2.2F, 1.6F, 1.0F};
    /** Engaged 阶段达到这个接近速度 ⇒ 把骑乘者打下马 ✓（照 wiki："dismount mounted enemies" ✓） */
    public static final double DISMOUNT_MIN_CLOSING = 0.25D;

    public SpearItem(Properties properties) {
        super(properties, SPEAR_DEFINITION);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create(super.getAttributeModifiers(slot, stack));
        if (slot == EquipmentSlot.MAINHAND) {
            // ⚠ 属性名必须是 forge:entity_reach（够生物 ✓）——本仓 §806 踩过 forge:reach_distance 不存在的坑 ✗
            map.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_UUID, "Spear Entity Reach",
                            REACH_BONUS, AttributeModifier.Operation.ADDITION));
        }
        return map;
    }

    // ============================================================
    //  右键长按＝冲锋（原版口径 ✓ 用户指定 ✓）
    // ============================================================

    /**
     * 长按才能冲锋 ⇒ 使用时长给满 ✓。
     * <p>⭐ 但**匠魂自己的"使用中"词条优先** ✓ —— 实读 {@code ModifiableItem} 字节码确认：
     * 没有任何"使用中"交互词条时 {@code super.getUseDuration} 返回 <b>0</b>、
     * {@code super.getUseAnimation} 返回 {@code UseAnim.NONE} ✓（哨兵是 {@code ModifierEntry.EMPTY} ✓
     * 它的 hook 是空实现 ⇒ 调 {@code super.onUseTick} 不会 NPE ✓ 所以那两处 super 调用保留 ✓）。
     */
    private static final int CHARGE_USE_TICKS = 72000;

    @Override
    public int getUseDuration(ItemStack stack) {
        int ability = super.getUseDuration(stack);
        return ability > 0 ? ability : CHARGE_USE_TICKS;
    }

    /** 原版长矛用的就是矛类持握动作 ✓（1.20.1 有 {@code UseAnim.SPEAR} ✓）；有词条动作时让位 ✓ */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        UseAnim ability = super.getUseAnimation(stack);
        return ability != UseAnim.NONE ? ability : UseAnim.SPEAR;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // ⭐ 先让匠魂自己的能力（格挡/投掷/交互…）有机会消费这次右键 ✗ 我们不抢它的活 ✓
        InteractionResultHolder<ItemStack> base = super.use(level, player, hand);
        if (base.getResult().consumesAction()) return base;
        if (hand != InteractionHand.MAIN_HAND) return base;

        ItemStack stack = player.getItemInHand(hand);
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null || tool.isBroken()) return base;   // 损坏即失效 ✓（与全模组同一口径 ✓）

        player.startUsingItem(hand);
        if (player instanceof ServerPlayer serverPlayer) SpearChargeHandler.begin(serverPlayer);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        super.onUseTick(level, living, stack, remaining);
        if (level.isClientSide) return;                     // 命中判定只在服务端 ✓
        if (!(living instanceof ServerPlayer player)) return;
        SpearChargeHandler.tick(player, stack, getUseDuration(stack) - remaining);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (living instanceof ServerPlayer player) SpearChargeHandler.end(player);
        super.releaseUsing(stack, level, living, timeLeft);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof ServerPlayer player) SpearChargeHandler.end(player);
        super.onStopUsing(stack, entity, count);
    }
}
