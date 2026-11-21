import com.mofengbaizhi.tinkersnewlife.util.MomoMercyFormula;

/** Run with javac alongside MomoMercyFormula.java, then java MomoMercyCheck. */
public class MomoMercyCheck {
    static void check(int alignment, int favor, float expected) {
        if (Math.abs(MomoMercyFormula.multiplier(alignment, favor) - expected) > .00001f)
            throw new AssertionError(alignment + "/" + favor);
    }
    public static void main(String[] args) {
        check(0,0,1); check(-50,-50,1); check(25,25,.7f); check(50,-50,.7f); check(0,50,.7f);
        check(50,50,.4f); check(Integer.MAX_VALUE,Integer.MIN_VALUE,.7f);
        for (int a=-60;a<=60;a++) for (int f=-60;f<=60;f++) {
            float m=MomoMercyFormula.multiplier(a,f);
            if(m<.4f || m>1 || m<MomoMercyFormula.multiplier(a+1,f) || m<MomoMercyFormula.multiplier(a,f+1))
                throw new AssertionError("bounds/monotonicity");
        }
        System.out.println("14641 score pairs and 7 reference cases passed");
    }
}
