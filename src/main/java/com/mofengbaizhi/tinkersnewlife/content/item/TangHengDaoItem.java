package com.mofengbaizhi.tinkersnewlife.content.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * <b>唐横刀</b>（§808）—— 国风气息浓郁的武器 ✓ 修长刀身 ＋ 灵活技法 ✓（赞助武器 ✓）。
 *
 * <h2>部件（5 个，全部复用匠魂标准件 ⇒ 不需要自定义部件配方 ✓）</h2>
 * 小型剑刃 {@code tconstruct:small_blade} ＋ 宽刃 {@code tconstruct:broad_blade} ＋
 * 大板 {@code tconstruct:large_plate} ＋ 坚韧套环 {@code tconstruct:tough_binding} ＋
 * 坚韧手柄 {@code tconstruct:tough_handle} ✓（见 {@code tool_definitions/tang_heng_dao.json} ✓）。
 *
 * <h2>面板（用户口径 ✓）</h2>
 * 基础：耐久 <b>80</b> / 伤害 <b>3</b> / 攻速 <b>2.8</b> / 挖掘 <b>0</b> ✓；
 * 倍率：耐久 ×<b>1.1</b> / 攻击 ×<b>1.2</b> / 攻速 ×<b>1.2</b> / 挖掘 ×<b>0.1</b> ✓
 * （都写在 tool_definition 的 {@code base_stats} 与 {@code multiply_stats} 里 ✓ 本类不重复 ✓）。
 *
 * <h2>自带词条「兵士佩刀」（无等级 ✓）</h2>
 * <ul>
 *   <li><b>实体交互距离 +1 格</b> ✓ —— 用 Forge 的 {@code forge:entity_reach}（够生物 ✓ 不是够方块 ✗）
 *       ⚠ 属性名必须写对：本仓 §807 就踩过 {@code forge:reach_distance} 不存在导致"静默不生效" ✗；</li>
 *   <li>4 格内按距离追加伤害 ＋ 灰色刀光 ✓ —— 在 {@code SoldiersSaberHandler} 里按"手上工具带不带该词条"触发 ✓
 *       （用词条判断而不是硬认这把刀 ⇒ 以后别的工具挂同一个词条也能吃 ✓）。</li>
 * </ul>
 */
public class TangHengDaoItem extends ModifiableItem {

    public static final ToolDefinition TANG_HENG_DAO_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "tang_heng_dao"));

    /** 固定 UUID ⇒ 幂等 ✓ 不会越叠越多 ✓ */
    private static final UUID REACH_UUID = UUID.fromString("c7d1e2f3-a4b5-4c6d-8e9f-0a1b2c3d4e5f");
    /** 兵士佩刀：实体交互距离 +1 格 ✓（用户口径） */
    private static final double REACH_BONUS = 1.0D;

    public TangHengDaoItem(Properties properties) {
        super(properties, TANG_HENG_DAO_DEFINITION);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create(super.getAttributeModifiers(slot, stack));
        if (slot == EquipmentSlot.MAINHAND) {
            map.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_UUID, "Tang Hengdao Entity Reach",
                            REACH_BONUS, AttributeModifier.Operation.ADDITION));
        }
        return map;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.tang_heng_dao.reach", (int) REACH_BONUS));
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.tang_heng_dao.saber"));
    }
}
