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
        if(!DragonSlayerProtection.fearEligible(4,20,100,false) || DragonSlayerProtection.fearEligible(3,20,100,false)
                || DragonSlayerProtection.fearEligible(4,20.01f,100,false) || DragonSlayerProtection.fearEligible(4,20,100.01,false)
                || DragonSlayerProtection.fearEligible(4,20,100,true) || DragonSlayerProtection.fearEligible(4,0,100,false)) throw new AssertionError("fear thresholds");
        System.out.println("20 armor reduction and 6 fear threshold cases passed");
    }
}
