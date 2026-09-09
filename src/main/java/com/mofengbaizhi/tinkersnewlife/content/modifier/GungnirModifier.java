package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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
import java.util.List;

/**
 * 远程特性·冈格尼尔（神灵金远程武器自带，模仿原版 Gungnir）：
 * 远程弹射物为"无视重力的冈格尼尔虚影"，伤害为弹射物原伤害的 80%；
 * 命中给目标震撼效果；命中时（直接命中）震撼更强、伤害更高，并在目标脚下点燃狱火。
 * 见 {@code GungnirHandler}。
 */
public class GungnirModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "gungnir"));

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.gungnir.tip"));
    }
}
