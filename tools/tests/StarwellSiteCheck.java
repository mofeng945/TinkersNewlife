package com.mofengbaizhi.tinkersnewlife.worldgen;

/** Standalone terrain fixtures: javac alongside StarwellSite; no server or world files required. */
public final class StarwellSiteCheck {
    public static void main(String[] args) {
        for(int q=0;q<4;q++){
            int backX=StarwellSite.x(0,-1,q),backZ=StarwellSite.z(0,-1,q);
            var site=StarwellSite.choose((x,z)->x*backX+z*backZ>66?0:68,0);
            if(site==null||site.turns()!=q||site.floor()!=68)throw new AssertionError("wrong entrance rotation: "+q);
        }
        if(StarwellSite.choose((x,z)->68,0)!=null)throw new AssertionError("inland");
        if(StarwellSite.choose((x,z)->0,0)!=null)throw new AssertionError("void");
        if(StarwellSite.choose((x,z)->x*x+z*z<40*40?68:0,0)!=null)throw new AssertionError("undersized island");
        if(StarwellSite.choose((x,z)->z < -65 || x==8&&z==8 ?0:68,0)!=null)throw new AssertionError("crypt hole");
        if(StarwellSite.choose((x,z)->z < -65 || z>75?0:68,0)!=null)throw new AssertionError("no walk-in approach");
        System.out.println("9 Starwell terrain fixtures passed");
    }
}
