package com.mofengbaizhi.tinkersnewlife.content.astral;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import java.util.*;
import java.util.function.Supplier;

/** Keep a single authoritative inventory, health pool and player identity. Only the anchored body is a proxy. */
@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class AstralProjection {
    private static final String SESSION="tnlAstralAnchor", CLIENT="tnlAstralVisible";
    private static final ModifierId ID=new ModifierId(new ResourceLocation("tinkersnewlife","soul_projection"));
    private static final TicketType<UUID> BODY_TICKET=TicketType.create("tnl_astral_body",UUID::compareTo);
    private AstralProjection(){}
    public static boolean active(Player p){return p.level().isClientSide?p.getPersistentData().getBoolean(CLIENT):p.getPersistentData().contains(SESSION);}
    private static int rank(ServerPlayer p){var tool=ToolHelper.getToolStack(p.getItemBySlot(EquipmentSlot.CHEST));return tool==null||tool.isBroken()?0:Math.min(3,ToolHelper.getActiveModifierLevel(tool,ID));}
    public static void toggle(ServerPlayer p){
        if(active(p)){finish(p);return;}
        int rank=rank(p);if(rank<1||!IntegrationLoader.isIceAndFire()||!p.isAlive()||p.isSpectator()||p.isSleeping()||p.isPassenger())return;
        var level=p.serverLevel();var a=new CompoundTag();var at=p.position();
        a.putDouble("X",at.x);a.putDouble("Y",at.y);a.putDouble("Z",at.z);a.putFloat("Yaw",p.getYRot());a.putFloat("Pitch",p.getXRot());
        a.putString("Dim",level.dimension().location().toString());a.putBoolean("MayFly",p.getAbilities().mayfly);a.putBoolean("Flying",p.getAbilities().flying);
        var body=ModEntities.ASTRAL_BODY.get().create(level);if(body==null)return;body.bind(p);
        if(!level.addFreshEntity(body))return;
        a.putUUID("Body",body.getUUID());p.getPersistentData().put(SESSION,a);
        level.getChunkSource().addRegionTicket(BODY_TICKET,new ChunkPos(p.blockPosition()),2,p.getUUID());
        if(!p.addEffect(new MobEffectInstance(ModEffects.SOUL_PROJECTION.get(),600*rank,rank-1,false,true,true))){finish(p);return;}
        p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.onUpdateAbilities();p.fallDistance=0;
        broadcast(p,true);
    }
    private static void finish(ServerPlayer p){
        var data=p.getPersistentData();if(!data.contains(SESSION))return;var a=data.getCompound(SESSION);data.remove(SESSION);
        var level=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(a.getString("Dim"))));
        if(level!=null){
            var at=new Vec3(a.getDouble("X"),a.getDouble("Y"),a.getDouble("Z"));var chunk=new ChunkPos(BlockPos.containing(at));
            try{
                if(a.hasUUID("Body")){var body=level.getEntity(a.getUUID("Body"));if(body instanceof AstralBodyEntity)body.discard();}
                p.closeContainer();p.stopRiding();
                // The spirit can place blocks around its own anchor. Prefer the anchor, then a nearby open column.
                Vec3 safe=returnPosition(p,level,at);p.teleportTo(level,safe.x,safe.y,safe.z,a.getFloat("Yaw"),a.getFloat("Pitch"));
            }finally{level.getChunkSource().removeRegionTicket(BODY_TICKET,chunk,2,p.getUUID());}
        }
        p.removeEffect(ModEffects.SOUL_PROJECTION.get());p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        p.getAbilities().mayfly=p.isCreative()||p.isSpectator()||a.getBoolean("MayFly");
        p.getAbilities().flying=p.isSpectator()||p.getAbilities().mayfly&&a.getBoolean("Flying");p.onUpdateAbilities();broadcast(p,false);
    }
    private static Vec3 returnPosition(ServerPlayer p,ServerLevel level,Vec3 at){
        var box=p.getDimensions(p.getPose()).makeBoundingBox(at);
        if(level.noCollision(p,box))return at;
        for(int y=0;y<=4;y++)for(int r=0;r<=3;r++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=r)continue;
            if(level.noCollision(p,box.move(x,y,z)))return at.add(x,y,z);
        }
        return at;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!active(p))return;
        if(!p.isAlive()||p.isSpectator()||!p.hasEffect(ModEffects.SOUL_PROJECTION.get())||rank(p)==0){finish(p);return;}
        p.fallDistance=0;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)finish(p);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)finish(p);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void died(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)finish(p);}
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p&&active(p)){e.setCanceled(true);finish(p);}}
    @SubscribeEvent public static void track(PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer target&&active(target))
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),new Sync(target,true));}
    private static void broadcast(ServerPlayer p,boolean on){TinkersNewlife.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new Sync(p,on));}
    public static final class Sync {
        private final UUID player;private final boolean on;
        Sync(Player p,boolean on){player=p.getUUID();this.on=on;}
        public Sync(FriendlyByteBuf buf){player=buf.readUUID();on=buf.readBoolean();}
        public void encode(FriendlyByteBuf buf){buf.writeUUID(player);buf.writeBoolean(on);}
        public void handle(Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->ClientSync.update(player,on)));ctx.setPacketHandled(true);}
    }
    private static final class ClientSync {
        static void update(UUID id,boolean on){var level=net.minecraft.client.Minecraft.getInstance().level;if(level!=null){var p=level.getPlayerByUUID(id);if(p!=null)p.getPersistentData().putBoolean(CLIENT,on);}}
    }
}
