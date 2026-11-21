package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.*;
import slimeknights.tconstruct.library.modifiers.hook.behavior.ToolDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.combat.*;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.*;
import java.util.UUID;

@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class FrostAffinityModifier extends Modifier implements ToolDamageModifierHook, ConditionalStatModifierHook, MeleeDamageModifierHook, MeleeHitModifierHook {
    private static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife","frost_affinity"));
    private static final UUID TOUGH = UUID.fromString("02881055-69ab-47da-9d51-2e2d180b9e06"), KNOCK = UUID.fromString("5739527a-0229-4078-b7ac-60fa22e0c0ae");
    private static boolean cold(LivingEntity e) {
        if(e==null || !IntegrationLoader.isIceAndFire())return false;
        var pos=e.blockPosition(); var biome=e.level().getBiome(pos);
        return e.isInPowderSnow || biome.is(Tags.Biomes.IS_COLD) || biome.value().coldEnoughToSnow(pos);
    }
    private static boolean ranged(IToolStackView t){return t.hasTag(TinkerTags.Items.RANGED)||t.hasTag(TinkerTags.Items.STAFFS)||t.getItem() instanceof ModularStaffItem;}
    private static boolean melee(IToolStackView t){return !ranged(t)&&!t.hasTag(TinkerTags.Items.ARMOR)&&!t.hasTag(TinkerTags.Items.SHIELDS)&&(t.hasTag(TinkerTags.Items.MELEE)||t.hasTag(TinkerTags.Items.HARVEST));}
    private static boolean active(ItemStack stack){var t=ToolHelper.getToolStack(stack);return t!=null&&!t.isBroken()&&ToolHelper.getActiveModifierLevel(t,ID)>0;}
    @Override protected void registerHooks(ModuleHookMap.Builder b){super.registerHooks(b);b.addHook(this,ModifierHooks.TOOL_DAMAGE,ModifierHooks.CONDITIONAL_STAT,ModifierHooks.MELEE_DAMAGE,ModifierHooks.MELEE_HIT);}
    @Override public int onDamageTool(IToolStackView t,ModifierEntry m,int n,LivingEntity e){return n>0&&cold(e)&&e.getRandom().nextFloat()<.70f?0:n;}
    @Override public float modifyStat(IToolStackView t,ModifierEntry m,LivingEntity e,FloatToolStat stat,float base,float value){return stat==ToolStats.ACCURACY&&ranged(t)&&cold(e)?value+.05f:value;}
    @Override public float getMeleeDamage(IToolStackView t,ModifierEntry m,ToolAttackContext ctx,float base,float value){return melee(t)&&cold(ctx.getAttacker())?value*1.10f:value;}
    @Override public void afterMeleeHit(IToolStackView t,ModifierEntry m,ToolAttackContext ctx,float damage){var target=ctx.getLivingTarget();if(target!=null&&!target.level().isClientSide&&damage>0&&melee(t)&&cold(ctx.getAttacker()))frost(target,100);}
    private static void frost(LivingEntity e,int ticks){e.addEffect(new MobEffectInstance(ModEffects.FROST.get(),ticks,0));}
    private static boolean mining(LivingEntity e){var t=ToolHelper.getToolStack(e.getMainHandItem());return t!=null&&!t.isBroken()&&ToolHelper.getActiveModifierLevel(t,ID)>0&&melee(t)&&cold(e);}
    @SubscribeEvent public static void speed(PlayerEvent.BreakSpeed e){if(mining(e.getEntity()))e.setNewSpeed(e.getNewSpeed()*1.4f);}
    @SubscribeEvent public static void harvest(PlayerEvent.HarvestCheck e){
        if(e.canHarvest()||!mining(e.getEntity()))return;
        var s=e.getTargetBlock();var tool=e.getEntity().getMainHandItem();
        boolean appropriate=s.is(BlockTags.MINEABLE_WITH_PICKAXE)&&tool.canPerformAction(ToolActions.PICKAXE_DIG)
                ||s.is(BlockTags.MINEABLE_WITH_AXE)&&tool.canPerformAction(ToolActions.AXE_DIG)
                ||s.is(BlockTags.MINEABLE_WITH_SHOVEL)&&tool.canPerformAction(ToolActions.SHOVEL_DIG)
                ||s.is(BlockTags.MINEABLE_WITH_HOE)&&tool.canPerformAction(ToolActions.HOE_DIG);
        if(appropriate&&TierSortingRegistry.isCorrectTierForDrops(Tiers.IRON,s))e.setCanHarvest(true);
    }
    private static int defensivePieces(LivingEntity e){int count=0;for(EquipmentSlot slot:EquipmentSlot.values()){
        var stack=e.getItemBySlot(slot);
        boolean correct=slot.getType()==EquipmentSlot.Type.HAND?stack.is(TinkerTags.Items.SHIELDS):stack.getEquipmentSlot()==slot||LivingEntity.getEquipmentSlotForItem(stack)==slot;
        if(correct&&(stack.is(TinkerTags.Items.ARMOR)||stack.is(TinkerTags.Items.SHIELDS))&&active(stack))count++;
    }return count;}
    private static void sync(AttributeInstance attr,UUID id,double amount){if(attr==null)return;var old=attr.getModifier(id);if(old!=null&&old.getAmount()==amount)return;if(old!=null)attr.removeModifier(id);if(amount>0)attr.addTransientModifier(new AttributeModifier(id,"Frost affinity",amount,AttributeModifier.Operation.ADDITION));}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){var e=event.getEntity();if(e.level().isClientSide || !IntegrationLoader.isIceAndFire())return;
        int pieces=defensivePieces(e);if(pieces>0&&!cold(e))pieces=0;
        sync(e.getAttribute(Attributes.ARMOR_TOUGHNESS),TOUGH,2*pieces);sync(e.getAttribute(Attributes.KNOCKBACK_RESISTANCE),KNOCK,.1*pieces);
    }
    @SubscribeEvent public static void retaliate(LivingHurtEvent event){var e=event.getEntity();if(IntegrationLoader.isIceAndFire()&&!e.level().isClientSide&&event.getAmount()>0&&event.getSource().getEntity() instanceof LivingEntity attacker&&attacker!=e&&defensivePieces(e)>0&&cold(e)&&e.getRandom().nextFloat()<.30f)frost(attacker,60);}
}
