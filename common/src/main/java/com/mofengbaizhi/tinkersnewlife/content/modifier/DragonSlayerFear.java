package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.WeakHashMap;

/** Give affected AI a movement goal rather than teleporting it or changing its faction/target. */
final class DragonSlayerFear extends Goal {
    private static final WeakHashMap<Mob,WeakReference<DragonSlayerFear>> GOALS=new WeakHashMap<>();
    private final Mob mob;
    private WeakReference<LivingEntity> threat=new WeakReference<>(null);
    private int expires, nextPath;
    private Path path;
    private DragonSlayerFear(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE));}
    static void refresh(LivingEntity wearer,long levels){
        for(Mob m:wearer.level().getEntitiesOfClass(Mob.class,wearer.getBoundingBox().inflate(10),m->m!=wearer&&m.isAlive()&&!m.isNoAi())){
            if(!DragonSlayerProtection.fearEligible(levels,m.getHealth(),m.distanceToSqr(wearer),false) || StarfallArrow.tnl$isBoss(m))continue;
            var ref=GOALS.get(m);var goal=ref==null?null:ref.get();
            if(goal==null){goal=new DragonSlayerFear(m);m.goalSelector.addGoal(-1,goal);GOALS.put(m,new WeakReference<>(goal));}
            var old=goal.threat.get();
            if(old==null || old==wearer || m.tickCount>=goal.expires || !old.isAlive() || old.level()!=m.level() || m.distanceToSqr(wearer)<m.distanceToSqr(old)){
                goal.threat=new WeakReference<>(wearer);goal.expires=m.tickCount+11;
            }
        }
    }
    private boolean afraid(){if(mob.tickCount>=expires || mob.getHealth()>20)return false;var e=threat.get();return e!=null&&e.isAlive()&&!e.isSpectator()&&e.level()==mob.level()&&mob.distanceToSqr(e)<=100;}
    private Path escape(LivingEntity e){
        var origin=mob.position();double initial=origin.distanceToSqr(e.position());
        for(int i=0;i<5;i++){
            Vec3 dest;
            if(mob instanceof PathfinderMob pm && i<2)dest=DefaultRandomPos.getPosAway(pm,12,5,e.position());
            else {double a=Math.atan2(origin.z-e.getZ(),origin.x-e.getX())+(mob.getRandom().nextDouble()-.5)*1.7;dest=origin.add(10*Math.cos(a),0,10*Math.sin(a));}
            if(dest==null || dest.distanceToSqr(e.position())<=initial)continue;
            Path candidate=mob.getNavigation().createPath(BlockPos.containing(dest),1);
            if(candidate!=null && candidate.getEndNode()!=null && Vec3.atCenterOf(candidate.getEndNode().asBlockPos()).distanceToSqr(e.position())>initial+1)return candidate;
        }return null;
    }
    @Override public boolean canUse(){if(!afraid()||mob.tickCount<nextPath)return false;nextPath=mob.tickCount+10;path=escape(threat.get());return path!=null;}
    @Override public boolean canContinueToUse(){return afraid()&&!mob.getNavigation().isDone();}
    @Override public void start(){mob.getNavigation().moveTo(path,1.3);}
    @Override public void stop(){mob.getNavigation().stop();path=null;}
}
