package com.mofengbaizhi.tinkersnewlife.content.skill;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Apply after vanilla travel/body control. Leave locomotion yaw alone so strafing paths still work. */
public final class MomoFacing {
    private MomoFacing() {}
    public static int pack(Vec3 dir,float fallback) {
        double horizontal=Math.hypot(dir.x,dir.z);
        if (dir.lengthSqr()<1e-8) return -1;
        double yaw=horizontal<1e-4?fallback:Math.toDegrees(Math.atan2(-dir.x,dir.z));
        int y=(int)Math.round(yaw*4)&2047;
        int p=(int)Math.round(Mth.clamp(-Math.toDegrees(Math.atan2(dir.y,horizontal)),-85,85)*4)+340;
        return (y<<10)|p;
    }
    public static void apply(Mob mob,int packed) {
        if(packed<0 || !mob.isAlive())return;
        int y=packed>>>10;if(y>=1024)y-=2048;
        float yaw=y*.25f;
        mob.setYBodyRot(yaw);mob.setYHeadRot(yaw);mob.setXRot(((packed&1023)-340)*.25f);
    }
}
