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
 * 铁魔法联动特性·<b>冷酷</b>（材料「无相冰」自带，有等级）：
 *
 * <p>每次攻击（近战 / 远程 / 法术都算）在玩家伤害结算之后，追加一段
 * <b>冰霜学派法术伤害</b>，大小为 {@code 玩家伤害 × 0.1 × (1 + 等级)}。
 * 结算在 {@code content.modifier.events.FormlessIceHandler}。
 *
 * <p>等级上限 3（TCon 的 Modifier 没有 getMaxLevel 覆写点，在查询处夹住）。
 */
public class ColdBloodedModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "cold_blooded"));

    /** 最高 3 级 */
    private static final int MAX_LEVEL = 3;

    /** 每级追加系数：玩家伤害 × 0.1 × (1 + 等级) */
    public static final double EXTRA_RATIO_PER_LEVEL = 0.1;

    /** 追加伤害的冰霜学派伤害类型（铁魔法） */
    public static final String ICE_DAMAGE_TYPE = "irons_spellbooks:ice_magic";

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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.cold_blooded.tip",
                String.format("%.0f", EXTRA_RATIO_PER_LEVEL * (1 + modifier.getLevel()) * 100)));
    }

    // ============================================================
    //  查询工具
    // ============================================================

    /** 该物品上的冷酷等级（没有则 0） */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tool = ToolHelper.getToolStack(stack);
        return clampLevel(ToolHelper.getActiveModifierLevel(tool, ID));
    }

    /** 玩家身上（主手/副手/护甲）最高的冷酷等级 */
    public static int bestLevel(LivingEntity entity) {
        if (entity == null) return 0;
        int best = levelOf(entity.getMainHandItem());
        best = Math.max(best, levelOf(entity.getOffhandItem()));
        for (ItemStack armor : entity.getArmorSlots()) best = Math.max(best, levelOf(armor));
        return best;
    }

    /** 可读性辅助：带此特性的物品（调试/提示用） */
    public static List<ItemStack> itemsWith(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>(6);
        if (entity == null) return out;
        for (ItemStack s : new ItemStack[]{entity.getMainHandItem(), entity.getOffhandItem()}) {
            if (levelOf(s) > 0) out.add(s);
        }
        for (ItemStack s : entity.getArmorSlots()) if (levelOf(s) > 0) out.add(s);
        return out;
    }
}
