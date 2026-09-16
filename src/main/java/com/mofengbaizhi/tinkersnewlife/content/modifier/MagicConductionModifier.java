package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.armor.ModifyDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 铁魔法联动特性·<b>导魔</b>（材料「奥铁」盔甲自带，<b>有等级</b>）：
 *
 * <p>对受到的<b>全类型魔法伤害</b>（原版 + 诡厄巫法 + 铁魔法等）减伤
 * {@code 5% × (等级 + 1)}；多件盔甲经 TCon 的护甲钩子<b>逐件链乘</b> → 自然"可叠加" ✓。
 *
 * <h2>"什么算魔法伤害"的判定（按优先级）</h2>
 * <ol>
 *   <li>{@link DamageTypeTags#WITCH_RESISTANT_TO 女巫抗性} 标签 —— 原版魔法类（magic / indirect_magic / 音爆…）✓；</li>
 *   <li><b>{@code forge:is_magic}</b> 标签 —— 各 mod 主动登记的魔法类型 ✓
 *       （实测：诡厄巫法登记了 8 个类型、星月核心登记了 2 个 ✓）；</li>
 *   <li>名字里含 {@code magic} 的伤害类型 ✓；</li>
 *   <li>命名空间兜底：{@code irons_spellbooks.*}（铁魔法九学派 ——
 *       ⚠ 它登记的是 <b>neoforge</b> 命名空间的标签，在 Forge 1.20.1 下不生效 ✗ 所以必须按命名空间补 ✓）
 *       与 {@code goety.*}（诡厄巫法法术，其类型名多样，标签只覆盖一部分 ✓）。</li>
 * </ol>
 */
public class MagicConductionModifier extends Modifier implements ModifyDamageModifierHook, TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "magic_conduction"));

    /** forge:is_magic 标签（各 mod 登记的魔法伤害类型） */
    private static final TagKey<DamageType> FORGE_IS_MAGIC =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("forge", "is_magic"));

    private static final int MAX_LEVEL = 3;

    /** 每级减伤系数：5% × (等级 + 1) */
    public static final float REDUCTION_PER_STEP = 0.05F;

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.MODIFY_DAMAGE, ModifierHooks.TOOLTIP);
    }

    @Override
    public float modifyDamageTaken(IToolStackView tool, ModifierEntry modifier, EquipmentContext context,
                                   EquipmentSlot slotType, DamageSource source, float amount,
                                   boolean isDirectDamage) {
        if (amount <= 0.0F || !isMagicDamage(source)) return amount;
        float reduction = REDUCTION_PER_STEP * (modifier.getLevel() + 1);
        return amount * Math.max(0.0F, 1.0F - reduction);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.magic_conduction.tip",
                String.format("%.0f", REDUCTION_PER_STEP * (modifier.getLevel() + 1) * 100)));
    }

    /** 这次伤害是否属于"魔法伤害"（判定顺序见类注释） */
    public static boolean isMagicDamage(DamageSource source) {
        if (source == null) return false;
        try {
            if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (source.is(FORGE_IS_MAGIC)) return true;
        } catch (Throwable ignored) {
        }
        String id = source.getMsgId();
        if (id == null || id.isEmpty()) return false;
        String s = id.toLowerCase();
        return s.contains("magic")
                || s.startsWith("irons_spellbooks.")
                || s.startsWith("goety.");
    }
}
