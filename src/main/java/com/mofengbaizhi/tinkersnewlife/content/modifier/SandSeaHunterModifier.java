package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.astral.AstralProjection;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import java.util.*;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class SandSeaHunterModifier extends Modifier {
    private static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife","sand_sea_hunter"));
    private static final UUID ARMOR_ID = UUID.fromString("09f9aab2-d3ae-42fc-a8c3-b94e8f56e2e1");
    // Transient: never save a forced perspective/pose with the player. Value records whether we own the forced pose.
    private static final Map<ServerPlayer,Boolean> BURROWED=new WeakHashMap<>();
    private static final Set<ServerPlayer> SURFACED=Collections.newSetFromMap(new WeakHashMap<>());
    public static boolean burrowed(ServerPlayer p){return BURROWED.containsKey(p);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void protect(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        if(e.getEntity() instanceof ServerPlayer p && burrowed(p))e.setCanceled(true);
    }
    /** Vanilla has already decided the permitted movement. Inspect the blocked face without changing that result. */
    public static void onCollision(ServerPlayer p,net.minecraft.world.phys.Vec3 wanted,net.minecraft.world.phys.Vec3 allowed){
        if(!burrowed(p))return;
        var box=p.getBoundingBox().move(allowed);
        double dx=wanted.x-allowed.x,dz=wanted.z-allowed.z;
        if(Math.abs(dx)>1e-6 && solidOther(p,box.move(Math.copySign(.002,dx),0,0)) ||
           Math.abs(dz)>1e-6 && solidOther(p,box.move(0,0,Math.copySign(.002,dz)))){
            SURFACED.add(p);exit(p);
        }
    }
    private static boolean solidOther(ServerPlayer p,net.minecraft.world.phys.AABB box){
        var probe=box.deflate(.0001);
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(probe.minX,probe.minY,probe.minZ),BlockPos.containing(probe.maxX,probe.maxY,probe.maxZ))){
            var block=p.level().getBlockState(pos);if(block.isAir()||block.is(Blocks.SAND)||block.is(Blocks.RED_SAND))continue;
            for(var shape:block.getCollisionShape(p.level(),pos,net.minecraft.world.phys.shapes.CollisionContext.of(p)).toAabbs())if(shape.move(pos).intersects(probe))return true;
        }return false;
    }
    public static double bonus(double pieceArmor,int rank){return Math.max(0,pieceArmor)*.03*Math.max(0,rank);}
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        var e=event.getEntity(); if(e.level().isClientSide || !IntegrationLoader.isIceAndFire())return;
        int levels=0; double extra=0;
        for(var slot:EquipmentSlot.values()){
            var stack=e.getItemBySlot(slot);
            if(slot.getType()==EquipmentSlot.Type.HAND ? !stack.is(TinkerTags.Items.SHIELDS) : !stack.is(TinkerTags.Items.WORN_ARMOR)||LivingEntity.getEquipmentSlotForItem(stack)!=slot)continue;
            var tool=ToolHelper.getToolStack(stack); int n=ToolHelper.getActiveModifierLevel(tool,ID); if(n<=0)continue;
            levels+=n; extra+=bonus(tool.getStats().get(ToolStats.ARMOR),n);
        }
        var attr=e.getAttribute(Attributes.ARMOR);
        if(attr!=null){
            if(extra>0&&!e.level().getBiome(e.blockPosition()).is(Tags.Biomes.IS_HOT))extra=0;
            var old=attr.getModifier(ARMOR_ID);
            if(old!=null && old.getAmount()!=extra){attr.removeModifier(ARMOR_ID);old=null;}
            if(old==null&&extra>0)attr.addTransientModifier(new AttributeModifier(ARMOR_ID,"Sand Sea Hunter",extra,AttributeModifier.Operation.ADDITION));
        }
        if(!(e instanceof ServerPlayer p))return;
        BlockPos floor=p.getOnPos();var state=p.level().getBlockState(floor);
        if(!p.isSprinting() || !p.horizontalCollision) SURFACED.remove(p);
        boolean on=!SURFACED.contains(p) && levels>=4 && p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.isSleeping() && !p.getAbilities().flying
                && !AstralProjection.active(p) && p.isSprinting() && p.onGround() && (state.is(Blocks.SAND)||state.is(Blocks.RED_SAND));
        if(on){
            if(!BURROWED.containsKey(p)){
                boolean own=p.getForcedPose()==null; if(own)p.setForcedPose(Pose.SWIMMING);
                BURROWED.put(p,own);sync(p,true);
            }
            if((p.tickCount&1)==0)p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,state),p.getX(),floor.getY()+1.08,p.getZ(),4,.21,.08,.21,.055);
        }else exit(p);
    }
    private static void exit(ServerPlayer p){Boolean owned=BURROWED.remove(p);if(owned==null)return;
        if(owned && p.getForcedPose()==Pose.SWIMMING)p.setForcedPose(null);sync(p,false);
    }
    private static void sync(ServerPlayer p,boolean active){TinkersNewlife.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new Sync(p,active));}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event){if(event.getEntity() instanceof ServerPlayer watcher&&event.getTarget() instanceof ServerPlayer p)TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(()->watcher),new Sync(p,BURROWED.containsKey(p)));}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)exit(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)exit(p);}
    public static final class Sync {
        private final UUID id;private final boolean active;
        Sync(ServerPlayer player,boolean active){this.id=player.getUUID();this.active=active;}
        public Sync(FriendlyByteBuf buf){id=buf.readUUID();active=buf.readBoolean();}
        public void encode(FriendlyByteBuf buf){buf.writeUUID(id);buf.writeBoolean(active);}
        public void handle(Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->com.mofengbaizhi.tinkersnewlife.client.handler.SandBurrowClient.update(id,active)));ctx.setPacketHandled(true);}
    }
}
