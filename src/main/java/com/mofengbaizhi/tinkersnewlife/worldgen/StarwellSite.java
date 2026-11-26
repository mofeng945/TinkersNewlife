package com.mofengbaizhi.tinkersnewlife.worldgen;

import java.util.Arrays;
import java.util.function.IntBinaryOperator;

/** Coordinates relative to the altar. The unrotated stair faces +Z. Heights are first-air Y. */
final class StarwellSite {
    record Site(int floor, int turns) {}
    static int x(int u,int v,int q) { return switch(q){case 1->-v;case 2->-u;case 3->v;default->u;}; }
    static int z(int u,int v,int q) { return switch(q){case 1->u;case 2->-v;case 3->-u;default->v;}; }
    private static int h(IntBinaryOperator heights,int u,int v,int q) {return heights.applyAsInt(x(u,v,q),z(u,v,q));}
    static Site choose(IntBinaryOperator heights,int bottom) {
        int center=heights.applyAsInt(0,0);
        if(center<bottom+24) return null;
        // Reject central holes and sheer drops before trying to rotate the approach.
        int[] samples=new int[160]; int n=0;
        for(int x=-48;x<=48;x+=8) for(int z=-48;z<=48;z+=8) if(x*x+z*z<=48*48){
            int y=heights.applyAsInt(x,z); if(y<center-13 || y>center+12)return null; samples[n++]=y;
        }
        Arrays.sort(samples,0,n);int floor=samples[n/2];
        if(samples[0]<floor-12 || samples[n-1]>floor+10)return null;
        Site best=null;int bestScore=Integer.MIN_VALUE;
        for(int q=0;q<4;q++){
            // Both rear star points must overhang; the opposite side must have a walk-in approach.
            int voidHits=0;boolean left=false,right=false,ok=true;int slopeCost=0;
            for(int side:new int[]{-1,1})for(int v:new int[]{-72,-80,-85}){
                int y=h(heights,side*(int)Math.round(-v*.4142),v,q);
                if(y<=bottom+1){voidHits++;if(side<0)left=true;else right=true;}
            }
            if(!left || !right)continue;
            for(int v=48;v<=96&&ok;v+=4)for(int u:new int[]{-5,0,5}){
                int y=h(heights,u,v,q); if(y<floor-(v<72?8:3)||y>floor+(v<72?8:3)){ok=false;break;}
                slopeCost+=Math.abs(y-floor);
            }
            if(!ok)continue;
            // The crypt's outer ring must also rest on the island, not span open void.
            for(int i=0;i<32;i++){double a=i*Math.PI/16;int y=h(heights,(int)Math.round(53*Math.cos(a)),(int)Math.round(53*Math.sin(a)),q);if(y<floor-12||y>floor+12){ok=false;break;}}
            if(ok && voidHits*40-slopeCost>bestScore){bestScore=voidHits*40-slopeCost;best=new Site(floor,q);}
        }
        return best;
    }
}
