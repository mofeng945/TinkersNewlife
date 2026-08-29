package com.mofengbaizhi.tinkersnewlife.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 静止效果（无量空处）
 * <p>
 * 携带者每 tick 被冻结：无法移动、跳跃、冲刺；生物 AI 停摆；
 * 玩家打开的任何界面会被立即关闭。攻击/使用物品等由 StunHandler 事件取消。
 * 实际冻结逻辑见 {@code StunHandler}。
 */
public class StunEffect extends MobEffect {

    public StunEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}
