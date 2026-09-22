package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 递给 {@link EnergyInputAdapter} 的那一点上下文（§557）。
 *
 * <p>为什么不让适配器自己抓世界：适配器是"纯反射的一小段" ✓ 它只该知道
 * <b>"在哪个世界、哪个坐标、哪几个方向被允许"</b> ✓ —— 于是把它们打包成这个只读记录 ✓
 * 顺便把"哪几家开关开着"的判断也收在这里 ✓（适配器里就不必再认识 {@code ModConfig} ✗）。
 *
 * @param level   世界（服务端 ✓；适配器只在服务端被调用 ✓）
 * @param pos     转化器自己的坐标
 * @param mekanism 通用机械（J）这一路是否启用
 * @param create   Create 转速（RPM）这一路是否启用
 * @param ic2      工业时代（EU）这一路是否启用
 * @param ae2      应用能源（AE）这一路是否启用
 */
public record EnergyConverterContext(Level level, BlockPos pos,
                                     boolean mekanism, boolean create,
                                     boolean ic2, boolean ae2) {

    /** 按当前配置快照构造（转化器每 tick 构造一次 ✓ 很便宜 ✓） */
    public static EnergyConverterContext of(Level level, BlockPos pos) {
        return new EnergyConverterContext(level, pos,
                ModConfig.converterMekanismEnabled(),
                ModConfig.converterCreateEnabled(),
                ModConfig.converterIc2Enabled(),
                ModConfig.converterAe2Enabled());
    }

    /** 通用机械（J）：{@code mekanism_energy_enabled} ✓ */
    public boolean jouleEnabled() {
        return mekanism;
    }

    /** Create（RPM）：{@code create_rotation_enabled} ✓ */
    public boolean rpmEnabled() {
        return create;
    }

    /** 工业时代（EU）：{@code ic2_eu_enabled} ✓ */
    public boolean euEnabled() {
        return ic2;
    }

    /** 应用能源（AE）：{@code ae_energy_enabled} ✓ */
    public boolean aeEnabled() {
        return ae2;
    }
}
