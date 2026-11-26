package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.*;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.modifiers.hook.combat.*;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public final class BiotoxicityModifier extends NoLevelsModifier implements MeleeDamageModifierHook,MeleeHitModifierHook {
    private static final ResourceLocation WORM=new ResourceLocation("iceandfire","deathworm");
    @Override protected void registerHooks(ModuleHookMap.Builder b){super.registerHooks(b);b.addHook(this,ModifierHooks.MELEE_DAMAGE,ModifierHooks.MELEE_HIT);}
    private static MobType group(LivingEntity e){return WORM.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())) ? MobType.UNDEFINED : e.getMobType();}
    private static boolean active(IToolStackView tool){return IntegrationLoader.isIceAndFire()&&!tool.isBroken()&&tool.hasTag(TinkerTags.Items.MELEE);}
    @Override public float getMeleeDamage(IToolStackView tool,ModifierEntry entry,ToolAttackContext ctx,float base,float amount){
        var e=ctx.getLivingTarget();if(e==null||!active(tool))return amount;
        return amount+(group(e)==MobType.ARTHROPOD?1:2);
    }
    @Override public void afterMeleeHit(IToolStackView tool,ModifierEntry entry,ToolAttackContext ctx,float damage){
        var e=ctx.getLivingTarget();if(e==null||e.level().isClientSide||damage<=0||!active(tool))return;
        var type=group(e);if(type!=MobType.ARTHROPOD&&type!=MobType.UNDEAD)e.addEffect(new MobEffectInstance(MobEffects.POISON,200,0),ctx.getAttacker());
    }
}
