package com.mofengbaizhi.tinkersnewlife.worldgen;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoBossPresence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class StarwellSummoning {
    static final ResourceKey<Structure> ARENA = ResourceKey.create(Registries.STRUCTURE,new ResourceLocation("tinkersnewlife","starwell_arena"));
    private record Entry(UUID caller,long start) {}
    private static final Map<ServerLevel,Map<BlockPos,Entry>> pending=new HashMap<>();
    private StarwellSummoning() {}
    static BlockPos floorAt(ServerLevel level,BlockPos pos){
        if(level.dimension()!=Level.END)return null;
        var start=level.structureManager().getStructureWithPieceAt(pos,ARENA);
        if(start.isValid())for(var piece:start.getPieces())if(piece instanceof StarwellArenaPiece arena)return arena.altarSurface();
        return null;
    }
    public static void afterEating(ServerPlayer player){
        var level=player.serverLevel();
        if(level.getDragonFight()==null||!level.getDragonFight().hasPreviouslyKilledDragon())return;
        var floor=floorAt(level,player.blockPosition());
        if(floor==null||player.position().distanceToSqr(Vec3.atBottomCenterOf(floor))>400||MomoBossPresence.blocked(level,Vec3.atBottomCenterOf(floor),null))return;
        var entries=pending.computeIfAbsent(level,k->new HashMap<>());
        if(entries.keySet().stream().anyMatch(p->p.distSqr(floor)<=256*256))return;
        var beacon=floor.below(12);if(!level.getBlockState(beacon).is(Blocks.BEACON))return;
        var missing=new ArrayList<BlockPos>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var pos=beacon.offset(x,-1,z);var state=level.getBlockState(pos);
            if(state.isAir())missing.add(pos);else if(!state.is(BlockTags.BEACON_BASE_BLOCKS))return;
        }
        for(var pos:missing)level.setBlockAndUpdate(pos,Blocks.IRON_BLOCK.defaultBlockState());
        entries.put(floor,new Entry(player.getUUID(),level.getGameTime()));
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel level)pending.remove(level);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level))return;
        var entries=pending.get(level);if(entries==null)return;
        entries.entrySet().removeIf(row->{
            var floor=row.getKey();var c=Vec3.atBottomCenterOf(floor);long age=level.getGameTime()-row.getValue().start();
            if(!level.hasChunkAt(floor)||MomoBossPresence.blocked(level,c,null))return true;
            // Vanilla beacon updates its base every 80 ticks. Leave time for the beam before the blast.
            if(age==90){
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,c.x,c.y+1,c.z,1,0,0,0,0);
                level.playSound(null,floor,SoundEvents.GENERIC_EXPLODE,SoundSource.HOSTILE,2,0.8f);
                for(var p:level.getEntitiesOfClass(ServerPlayer.class,new AABB(floor).inflate(12),p->!p.isSpectator()&&p.position().distanceToSqr(c)<=144)){
                    var out=new Vec3(p.getX()-c.x,0,p.getZ()-c.z);if(out.lengthSqr()<0.001)out=new Vec3(1,0,0);
                    out=out.normalize().scale(1.3);p.push(out.x,.65,out.z);p.hurtMarked=true;
                }
            }
            if(age>=90&&age<110&&age%2==0){double r=1+(age-90)*.6;
                for(int i=0;i<24;i++){double a=2*Math.PI*i/24;level.sendParticles(ParticleTypes.SONIC_BOOM,c.x+r*Math.cos(a),c.y+.6,c.z+r*Math.sin(a),1,0,0,0,0);}}
            if(age<110)return false;
            var boss=ModEntities.KTULU_MOMO.get().create(level);if(boss==null)return true;
            boss.moveTo(c.x,c.y,c.z,0,0);if(!level.noCollision(boss,boss.getBoundingBox()))return true;
            if(level.addFreshEntity(boss)){var p=level.getPlayerByUUID(row.getValue().caller());if(p!=null&&!p.isCreative()&&!p.isSpectator())boss.setTarget(p);}
            return true;
        });if(entries.isEmpty())pending.remove(level);
    }
}
