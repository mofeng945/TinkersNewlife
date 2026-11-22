import com.mofengbaizhi.tinkersnewlife.content.modifier.DragonSlayerProtection;

public final class DragonSlayerProtectionCheck {
    private static void pair(int h,int c,int l,int f,double normal,double breath){
        for(boolean b:new boolean[]{false,true})if(Math.abs(DragonSlayerProtection.multiplier(h,c,l,f,b)-(b?breath:normal))>1e-6)throw new AssertionError();
    }
    public static void main(String[] args){
        pair(0,0,0,0,1,1);pair(1,0,0,0,.9875,.9875*.95);pair(0,1,0,0,.975,.975*.85);
        pair(0,0,1,0,.975,.975*.90);pair(0,0,0,1,.9875,.9875*.95);
        pair(1,1,1,1,.925,.925*.65);pair(0,5,0,0,.875,.875*.30);pair(20,20,20,20,.5,.15);
        pair(-1,-2,0,0,1,1);pair(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,.5,.15);
        if(!DragonSlayerProtection.fearEligible(4,30,30,100,true,false) || DragonSlayerProtection.fearEligible(4,10,9,100,true,false)
                || DragonSlayerProtection.fearEligible(3,10,40,100,true,false) || DragonSlayerProtection.fearEligible(4,10,40,100.01,true,false)
                || DragonSlayerProtection.fearEligible(4,10,40,100,true,true) || DragonSlayerProtection.fearEligible(4,1,40,1,false,false)
                || DragonSlayerProtection.fearEligible(4,0,40,1,true,false))throw new AssertionError("fear thresholds");
        System.out.println("20 armor and 7 relative-health/hostility checks passed");
    }
}
