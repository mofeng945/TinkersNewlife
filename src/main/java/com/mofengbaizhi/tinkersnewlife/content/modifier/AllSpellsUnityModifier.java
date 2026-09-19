package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 铁魔法联动特性·<b>万法归一</b>（材料「魔金」通用自带，<b>有等级</b>）：
 *
 * <ol>
 *   <li><b>法力上限 +100 × 等级</b> —— 走铁魔法 {@code MAX_MANA} 属性（transient 修饰符，
 *       每 10 tick 维持）✓；</li>
 *   <li><b>攻击时自带 5 级「回响打击」</b>（{@code irons_spellbooks:echoing_strikes}）：
 *       无需法力、30 秒冷却 ✓；</li>
 *   <li><b>血量 ≤20% 时触发 3 级「深渊庇佑」</b>（{@code irons_spellbooks:abyssal_shroud}）：
 *       无需法力、200 秒冷却 ✓。</li>
 * </ol>
 *
 * <p><b>「最高等级的单件生效」</b>：法力加成只取身上<b>最高的一件</b>（不叠加），
 * 见 {@link #bestLevel(LivingEntity)} ✓（两个赠送法术的等级是固定的，与等级无关）。
 *
 * <p>结算在 {@code content.modifier.events.MagicGoldHandler} ✓。
 */
public class AllSpellsUnityModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "all_spells_unity"));

    /** 最高 3 级 */
    private static final int MAX_LEVEL = 3;

    /** 每级法力上限加成 */
    public static final double MANA_PER_LEVEL = 100.0D;

    /** 赠送法术：回响打击 */
    public static final String ECHO_SPELL = "irons_spellbooks:echoing_strikes";
    public static final int ECHO_LEVEL = 5;
    public static final int ECHO_COOLDOWN = 45 * 20;

    /** 赠送法术：深渊庇佑 */
    public static final String SHROUD_SPELL = "irons_spellbooks:abyssal_shroud";
    public static final int SHROUD_LEVEL = 2;
    public static final int SHROUD_COOLDOWN = 300 * 20;
    /** 触发血量比例 */
    public static final float TRIGGER_RATIO = 0.2F;

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.all_spells_unity.tip",
                String.format("%.0f", MANA_PER_LEVEL * modifier.getLevel()),
                String.valueOf(ECHO_LEVEL), String.valueOf(ECHO_COOLDOWN / 20),
                String.valueOf(SHROUD_LEVEL), String.valueOf(SHROUD_COOLDOWN / 20)));
    }

    // ============================================================
    //  查询工具（结算器用）
    // ============================================================

    /** 该物品上的等级（没有则 0） */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tool = ToolHelper.getToolStack(stack);
        return clampLevel(ToolHelper.getActiveModifierLevel(tool, ID));
    }

    /** 身上（主手/副手/护甲）带此特性的物品 */
    public static List<ItemStack> itemsWith(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>(6);
        if (entity == null) return out;
        addIf(out, entity.getMainHandItem());
        addIf(out, entity.getOffhandItem());
        for (ItemStack armor : entity.getArmorSlots()) addIf(out, armor);
        return out;
    }

    /** 身上最高的等级（0 = 没有）—— "最高等级的单件生效" ✓ */
    public static int bestLevel(LivingEntity entity) {
        int best = 0;
        for (ItemStack stack : itemsWith(entity)) best = Math.max(best, levelOf(stack));
        return best;
    }

    /** 身上是否带着此特性 */
    public static boolean wornBy(LivingEntity entity) {
        return bestLevel(entity) > 0;
    }

    private static void addIf(List<ItemStack> out, ItemStack stack) {
        if (!stack.isEmpty() && levelOf(stack) > 0) out.add(stack);
    }
}
