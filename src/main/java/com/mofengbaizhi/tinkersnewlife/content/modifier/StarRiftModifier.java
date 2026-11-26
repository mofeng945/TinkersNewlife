package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.helper.ModifierLootingHandler;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

@Mod.EventBusSubscriber(modid=TinkersNewlife.MOD_ID)
public final class StarRiftModifier extends Modifier implements ToolStatsModifierHook {
    private static final ModifierId ID = new ModifierId(TinkersNewlife.prefix("star_rift"));
    @Override protected void registerHooks(ModuleHookMap.Builder builder) { builder.addHook(this, ModifierHooks.TOOL_STATS); }
    @Override public void addToolStats(IToolContext tool, ModifierEntry entry, ModifierStatsBuilder stats) {
        int rank = entry.getLevel();
        if (ToolStats.DURABILITY.supports(tool.getItem())) ToolStats.DURABILITY.multiply(stats, 1 + .05 * rank);
        if (ToolStats.MINING_SPEED.supports(tool.getItem())) ToolStats.MINING_SPEED.multiply(stats, 1 + .08 * rank);
    }
    public static float scale(float damage, int rank) { return damage * (1 + .10f * rank); }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void finalDamage(LivingDamageEvent event) {
        var source = event.getSource();
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0 || !(source.getEntity() instanceof LivingEntity user)) return;
        int rank = weaponRank(source, user);
        if (rank > 0) event.setAmount(scale(event.getAmount(), rank));
    }
    static int weaponRank(net.minecraft.world.damagesource.DamageSource source, LivingEntity user) {
        if (source.getDirectEntity() instanceof Projectile shot) return EntityModifierCapability.getOrEmpty(shot).getLevel(ID);
        if (source.getDirectEntity() == user && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(SoulStrikeModifier.DREAD))) {
            return ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(user.getItemBySlot(ModifierLootingHandler.getLootingSlot(user))), ID);
        }
        return 0;
    }
}
