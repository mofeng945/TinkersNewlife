package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * TNL874 \u2605\u2605 \u4ece**\u6e90\u5934**\u62e6"\u6539\u8840" \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u6211\u4e0d\u662f\u5199 dll \u5c31\u662f\u4e3a\u4e86\u963b\u6b62\u8fd9\u79cd\u4e8b\u60c5\u5417" \u2713
 * \u21d2 \u2605\u8bf4\u5f97\u5bf9\uff1a\u2605\u5149\u9760 DLL \u76d1\u63a7\uff0c\u6062\u590d\u53d1\u751f\u5728**\u4e0b\u4e00 tick** \u2717\uff08\u2605\u8840\u6761\u4f1a\u95ea \u2717\uff09\uff1b
 * \u2605\u6df7\u5165 {@code LivingEntity#setHealth} \u5219\u662f\u5728**\u5199\u7684\u90a3\u4e00\u77ac\u95f4**\u5c31\u628a\u503c\u6362\u56de\u6743\u5a01\u503c \u2713\u2713\u3002
 *
 * <p>\u2605\u4e3a\u4f55\u80fd\u9760\uff1a\u2605{@code setHealth} \u662f {@code LivingEntity} **\u81ea\u5df1\u58f0\u660e**\u7684\u65b9\u6cd5 \u2713
 * （\u2605\u4e0d\u662f\u63a5\u53e3/\u7ee7\u627f\u6765\u7684 \u2717\uff09\u21d2 \u2605refmap \u80fd\u6b63\u5e38\u751f\u6210 \u2713\u3002
 * \u2605\u5199\u6cd5\u76f4\u63a5\u7167\u6284 {@code SuperTierEffectMixin}\uff08\u2605\u5df2\u5728\u7528\u7684\u73b0\u6210\u4f8b\u5b50 \u2713\uff09\u3002
 */
@Mixin(LivingEntity.class)
public class KtuluVitalsMixin {

    @ModifyVariable(method = "m_5634_(F)V", at = @At("HEAD"), argsOnly = true, remap = false)
    private float tinkersnewlife$vitalsFloor(float value) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (!(self instanceof KtuluMomo)) {
                return value;
            }
            if (self.level().isClientSide) {
                return value;
            }
            return a.qD((net.minecraft.world.entity.Mob) self, value);
        } catch (Throwable ignored) {
            return value;
        }
    }
}
