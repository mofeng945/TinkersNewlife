package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.*;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileHitModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.*;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class CopperArrowModifier extends NoLevelsModifier implements ProjectileHitModifierHook {
    private static final ModifierId ID=new ModifierId(new ResourceLocation("tinkersnewlife","copper_arrow"));
    private static final ResourceLocation FEATHER=new ResourceLocation("iceandfire","stymphalian_feather");
    private static final String STARTED="tnlCopperArrowLaunched", GLIDE="tnlCopperArrowGlide", SPLIT="tnlCopperArrowBurst", FRAGMENT="tnlCopperArrowFragment";
    private static boolean copper(Projectile p){return ModList.get().isLoaded("iceandfire") && p instanceof ModifiableArrow a
            && !a.getDisplayTool().isEmpty() && ToolStack.from(a.getDisplayTool()).getModifierLevel(ID)>0;}
    @Override protected void registerHooks(ModuleHookMap.Builder b){super.registerHooks(b);b.addHook(this,ModifierHooks.PROJECTILE_HIT);}

    // Read actual ammo and actual launch speed, after the launcher computes charge/inaccuracy.
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void launch(EntityJoinLevelEvent e){
        if(e.loadedFromDisk() || e.getLevel().isClientSide || !(e.getEntity() instanceof ModifiableArrow a))return;
        var tag=a.getPersistentData();var v=a.getDeltaMovement();double speed=v.length();
        if(tag.getBoolean(STARTED)||!Double.isFinite(speed)||speed<1e-6||!copper(a))return;
        tag.putBoolean(STARTED,true);
        a.setDeltaMovement(v.scale((speed+2)/speed));
        if(!a.isNoGravity()){tag.putBoolean(GLIDE,true);a.setNoGravity(true);}
    }
    public static void glideTick(AbstractArrow a){
        if(a.level().isClientSide||!a.getPersistentData().getBoolean(GLIDE))return;
        var v=a.getDeltaMovement().scale(.94);
        if(v.lengthSqr()<=.7*.7 || !ModList.get().isLoaded("iceandfire")){endGlide(a);return;}
        a.setDeltaMovement(v);
    }
    private static void endGlide(AbstractArrow a){if(a.getPersistentData().getBoolean(GLIDE)){a.getPersistentData().remove(GLIDE);a.setNoGravity(false);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void ground(ProjectileImpactEvent e){
        if(e.getProjectile().level().isClientSide||e.getRayTraceResult().getType()!=HitResult.Type.BLOCK)return;
        if(e.getProjectile() instanceof AbstractArrow a){
            if(a.getPersistentData().getBoolean(FRAGMENT)){e.setCanceled(true);a.discard();}
            else endGlide(a);
        }
    }
    @Override public boolean onProjectileHitEntity(ModifierNBT modifiers,ModDataNBT data,ModifierEntry modifier,
            Projectile projectile,EntityHitResult hit,@Nullable LivingEntity shooter,@Nullable LivingEntity target,boolean notBlocked){
        if(!notBlocked||target==null||!(projectile.level() instanceof ServerLevel level)||!copper(projectile))return false;
        var tag=projectile.getPersistentData();if(tag.getBoolean(SPLIT))return false;
        var type=ForgeRegistries.ENTITY_TYPES.getValue(FEATHER);if(type==null)return false;
        tag.putBoolean(SPLIT,true);
        Vec3 center=target.position().add(0,target.getBbHeight()*.5,0);
        var box=target.getBoundingBox();
        for(int i=0;i<8;i++){
            double angle=i*Math.PI/4;Vec3 dir=new Vec3(Math.cos(angle),0,Math.sin(angle));
            if(!(type.create(level) instanceof AbstractArrow feather))break;
            // Start outside the struck entity, without placing feathers through nearby walls.
            var start=center.add(dir.scale(Math.min((box.getXsize()*.5+.32)/Math.max(1e-6,Math.abs(dir.x)),(box.getZsize()*.5+.32)/Math.max(1e-6,Math.abs(dir.z)))));
            if(level.clip(new ClipContext(center,start,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,feather)).getType()!=HitResult.Type.MISS)continue;
            feather.setPos(start);feather.setOwner(projectile.getOwner());feather.pickup=AbstractArrow.Pickup.DISALLOWED;
            feather.getPersistentData().putBoolean(FRAGMENT,true);
            feather.shoot(dir.x,.08,dir.z,1.4f,0);level.addFreshEntity(feather);
        }
        return false;
    }
}
