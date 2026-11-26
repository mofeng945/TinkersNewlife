package com.mofengbaizhi.tinkersnewlife.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * TNL1049 the hidden "weakened by the Aeonian Order" marker.
 *
 * Its only job is to carry a number: the effect DURATION is the accumulated tick count.
 * Vanilla syncs duration to the client and counts it down by one every tick, which is
 * exactly the behaviour the design needs, so no packet and no manual decay are required.
 */
public class AeonianMarkEffect extends MobEffect {

    public AeonianMarkEffect() {
        super(MobEffectCategory.NEUTRAL, 0x2A0A3A);
    }
}
