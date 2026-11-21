package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** A visual weapon snapshot, not an obtainable item. Sweep entities only, never raycast blocks. */
public final class SoulToolEcho extends Entity implements net.minecraftforge.entity.IEntityAdditionalSpawnData {
    private static final EntityDataAccessor<ItemStack> APPEARANCE=SynchedEntityData.defineId(SoulToolEcho.class,EntityDataSerializers.ITEM_STACK);
    private UUID owner; private float damage; private double left;
    private final Set<UUID> hit=new HashSet<>();
    public SoulToolEcho(EntityType<? extends SoulToolEcho> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public ItemStack appearance(){return entityData.get(APPEARANCE);}
    public static boolean launch(ServerPlayer player,ItemStack held,float attack,int rank){
        var echo=ModEntities.SOUL_TOOL_ECHO.get().create(player.level());if(echo==null)return false;
        var look=player.getLookAngle().normalize(); echo.setPos(player.getEyePosition());echo.setDeltaMovement(look.scale(1.25));
        echo.owner=player.getUUID();echo.damage=attack;echo.left=5.0*rank;echo.entityData.set(APPEARANCE,held.copyWithCount(1));
        echo.setYRot(player.getYRot());echo.setXRot(player.getXRot());return player.level().addFreshEntity(echo);
    }
    @Override public void tick(){super.tick();Vec3 start=position(),v=getDeltaMovement();
        if(level().isClientSide){double step=Math.min(left,v.length());if(step>0){setPos(start.add(v.normalize().scale(step)));left-=step;}return;}
        Player player=owner==null?null:level().getPlayerByUUID(owner);if(player==null||!player.isAlive()||left<=0||v.lengthSqr()<1e-8){discard();return;}
        double step=Math.min(left,v.length()); Vec3 delta=v.normalize().scale(step),end=start.add(delta);
        for(var target:level().getEntitiesOfClass(LivingEntity.class,getBoundingBox().expandTowards(delta).inflate(.3),e->e!=player&&e.isAlive()&&!e.isSpectator()&&!player.isAlliedTo(e))){
            if(target instanceof Player p&&!player.canHarmPlayer(p))continue;
            var box=target.getBoundingBox().inflate(.15);if((box.contains(start)||box.clip(start,end).isPresent())&&hit.add(target.getUUID()))
                target.hurt(damageSources().mobProjectile(this,player),damage);
        }
        setPos(end);left-=step;if(left<=1e-6)discard();
    }
    @Override public void writeSpawnData(net.minecraft.network.FriendlyByteBuf buf){buf.writeDouble(left);}
    @Override public void readSpawnData(net.minecraft.network.FriendlyByteBuf buf){left=buf.readDouble();}
    @Override protected void defineSynchedData(){entityData.define(APPEARANCE,ItemStack.EMPTY);}
    // Transient projectile; its type is noSave().
    @Override protected void readAdditionalSaveData(CompoundTag nbt){}
    @Override protected void addAdditionalSaveData(CompoundTag nbt){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
