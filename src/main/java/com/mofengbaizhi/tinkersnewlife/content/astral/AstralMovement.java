package com.mofengbaizhi.tinkersnewlife.content.astral;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Continuous AABB-vs-voxel movement for the projected player only. No noPhysics flag, chunk tickets or teleports. */
public final class AstralMovement {
    private AstralMovement() {}
    // Below the vanilla movement correction thresholds, but above floating point noise at the world border.
    private static final double EPS = 1e-8;
    private static final int MAX_SEGMENTS = 256;

    public static boolean active(Player player) {
        return !player.isSpectator() && !player.isPassenger() && AstralProjection.active(player);
    }

    public static Vec3 collide(Player player, AABB box, Vec3 wanted) {
        if (wanted.lengthSqr() == 0) return wanted;
        Level level = player.level();
        var border = level.getWorldBorder();
        // Never cut across an unchecked, unbounded sweep or load an absent chunk on a movement packet.
        double span = Math.max(Math.abs(wanted.x), Math.max(Math.abs(wanted.y), Math.abs(wanted.z)));
        if (!Double.isFinite(span) || span > MAX_SEGMENTS) return Vec3.ZERO;
        Vec3 rest = new Vec3(limit(wanted.x, border.getMinX()-box.minX, border.getMaxX()-box.maxX), wanted.y,
                limit(wanted.z, border.getMinZ()-box.minZ, border.getMaxZ()-box.maxZ));
        Vec3 done = Vec3.ZERO;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // Each contact eliminates at least one component. Move to first contact, then slide on the face.
        for (int pass=0; pass<3 && rest.lengthSqr()>0; pass++) {
            Hit hit = first(level, box.move(done), rest, cursor);
            if (hit == null) return done.add(rest);
            Vec3 step = rest.scale(hit.time);
            done = done.add(step);
            rest = rest.subtract(step).multiply((hit.axes&1)==0?1:0, (hit.axes&2)==0?1:0, (hit.axes&4)==0?1:0);
        }
        return done;
    }
    private static double limit(double delta,double lo,double hi) {
        return delta < 0 ? Math.max(delta,Math.min(0,lo)) : Math.min(delta,Math.max(0,hi));
    }
    private record Hit(double time,int axes) {}

    private static Hit first(Level level,AABB box,Vec3 d,BlockPos.MutableBlockPos cursor) {
        int count=Math.max(1,(int)Math.ceil(Math.max(Math.abs(d.x),Math.max(Math.abs(d.y),Math.abs(d.z)))));
        Vec3 step=d.scale(1.0/count);
        // Bound the broad phase by actual path length, not by the volume of a long diagonal's enclosing cube.
        for (int i=0;i<count;i++) {
            AABB area=box.move(step.scale(i)).expandTowards(step);
            int y0=Math.max(level.getMinBuildHeight(),Mth.floor(area.minY)), y1=Math.min(level.getMaxBuildHeight()-1,Mth.floor(area.maxY));
            Hit best=null;
            for(int x=Mth.floor(area.minX);x<=Mth.floor(area.maxX);x++) for(int z=Mth.floor(area.minZ);z<=Mth.floor(area.maxZ);z++) {
                boolean loaded=level.hasChunk(x>>4,z>>4);
                for(int y=y0;y<=y1;y++) {
                    if(loaded && !level.getBlockState(cursor.set(x,y,z)).is(Blocks.BEDROCK)) continue;
                    Hit h=contact(box,d,x,y,z);
                    if(h==null)continue;
                    if(best==null||h.time<best.time-EPS) best=h;
                    else if(Math.abs(h.time-best.time)<=EPS) best=new Hit(Math.min(h.time,best.time),h.axes|best.axes);
                }
            }
            if(best!=null && best.time<=(i+1.0)/count+EPS) return best;
        }
        return null;
    }
    private static double enter(double min,double max,double v,int cell) {
        if(v==0)return min<cell+1-EPS&&max>cell+EPS?Double.NEGATIVE_INFINITY:Double.POSITIVE_INFINITY;
        return v>0?(cell-max)/v:(cell+1-min)/v;
    }
    private static double leave(double min,double max,double v,int cell) {
        return v==0?Double.POSITIVE_INFINITY:v>0?(cell+1-min)/v:(cell-max)/v;
    }
    private static Hit contact(AABB b,Vec3 v,int x,int y,int z) {
        double tx=enter(b.minX,b.maxX,v.x,x), ty=enter(b.minY,b.maxY,v.y,y), tz=enter(b.minZ,b.maxZ,v.z,z);
        double t=Math.max(tx,Math.max(ty,tz));
        double end=Math.min(leave(b.minX,b.maxX,v.x,x),Math.min(leave(b.minY,b.maxY,v.y,y),leave(b.minZ,b.maxZ,v.z,z)));
        // A teleport or a sub-epsilon packet may start inside bedrock. Only let it exit toward a
        // nearest face: ignoring the entire block here would let a tiny overlap turn into tunnelling.
        if(t < -EPS && end > 0) {
            double west=b.maxX-x, east=x+1-b.minX, down=b.maxY-y, up=y+1-b.minY, north=b.maxZ-z, south=z+1-b.minZ;
            double shallow=Math.min(Math.min(west,east),Math.min(Math.min(down,up),Math.min(north,south)));
            if((v.x<0&&west<=shallow+EPS)||(v.x>0&&east<=shallow+EPS)||(v.y<0&&down<=shallow+EPS)||(v.y>0&&up<=shallow+EPS)
                    ||(v.z<0&&north<=shallow+EPS)||(v.z>0&&south<=shallow+EPS)) return null;
            int deeper=((v.x>0&&west<=shallow+EPS||v.x<0&&east<=shallow+EPS)?1:0)
                    |((v.y>0&&down<=shallow+EPS||v.y<0&&up<=shallow+EPS)?2:0)
                    |((v.z>0&&north<=shallow+EPS||v.z<0&&south<=shallow+EPS)?4:0);
            return deeper==0?null:new Hit(0,deeper);
        }
        if(t < -EPS || t>=1 || end<=Math.max(0,t)+1e-12) return null;
        int axes=(Math.abs(tx-t)<=1e-12?1:0)|(Math.abs(ty-t)<=1e-12?2:0)|(Math.abs(tz-t)<=1e-12?4:0);
        return new Hit(Math.max(0,t),axes);
    }
}
