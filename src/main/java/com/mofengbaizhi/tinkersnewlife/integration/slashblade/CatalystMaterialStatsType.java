package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.lib.CatalystMaterialStatsType

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ArmorItem;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatType;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;

/**
 * 「催化（catalyst）」材料统计类型 —— 移植 TiCEX {@code CatalystMaterialStatsType}（MIT）。
 *
 * <p>它<b>没有任何数值</b> ✓（{@code apply} 空实现、tooltip 就是匠魂那句"无属性" ✓）——
 * 它存在的意义是给"催化部件"（如 {@code catalyst_slashblade} ✓）一个**合法的统计类型 id** ✓，
 * 这样那件部件才能被匠魂的部件体系认出来 ✓（TiCEX 原版就是这么用的 ✓）。
 *
 * <p>⚠ <b>与原文的差异（必要适配 ✓）</b>：命名空间从 {@code ticex} 改成 {@code tinkersnewlife} ✓
 * （{@link MaterialStatsId} 用的是本模组 id ✓）。
 *
 * <p>⚠ <b>注册时机</b>：{@link #getOrMakeType(String)} 只是"造对象并缓存" ✓；
 * 真正注册进材料注册表的是 {@link #RegisterStats()} ✓ —— 由主类在
 * {@code FMLCommonSetupEvent} 里调 ✓（与 TiCEX 完全一致 ✓）。
 */
public record CatalystMaterialStatsType(MaterialStatType<?> getType) implements IMaterialStats {

    private static final RecordLoadable<CatalystMaterialStatsType> LOADABLE;
    private static final List<Component> DESCRIPTION;

    private static final HashMap<String, MaterialStatType<CatalystMaterialStatsType>> TYPES;

    public static final MaterialStatType<CatalystMaterialStatsType> SERAM;

    public CatalystMaterialStatsType(MaterialStatType<?> getType) {
        this.getType = getType;
    }

    public static MaterialStatType<CatalystMaterialStatsType> getOrMakeType(String id) {
        if (TYPES.containsKey(id)) {
            return TYPES.get(id);
        } else {
            MaterialStatsId statsId = new MaterialStatsId(TinkersNewlife.MOD_ID, id);
            MaterialStatType<CatalystMaterialStatsType> catalystStatType = new MaterialStatType<
                    CatalystMaterialStatsType
            >(
                    statsId,
                    type -> {
                        return new CatalystMaterialStatsType(type);
                    },
                    LOADABLE
            );

            TYPES.put(id, catalystStatType);

            return catalystStatType;
        }
    }

    public static MaterialStatType<CatalystMaterialStatsType> getOrMakeType(String prefix, ArmorItem.Type armorType) {
        String id = prefix + "_" + armorType.getName();
        if (TYPES.containsKey(id)) {
            return TYPES.get(id);
        } else {
            MaterialStatsId statsId = new MaterialStatsId(TinkersNewlife.MOD_ID, id);
            MaterialStatType<CatalystMaterialStatsType> catalystStatType = new MaterialStatType<
                    CatalystMaterialStatsType
            >(
                    statsId,
                    type -> {
                        return new CatalystMaterialStatsType(type);
                    },
                    LOADABLE
            );

            TYPES.put(id, catalystStatType);

            return catalystStatType;
        }
    }

    /** 把目前造出来的全部催化统计类型注册进材料注册表（幂等由匠魂那边保证 ✓） */
    public static void RegisterStats() {
        for (MaterialStatType<CatalystMaterialStatsType> catalystStatType : TYPES.values()) {
            MaterialRegistry.getInstance().registerStatType(catalystStatType);
        }
    }

    public static Collection<MaterialStatType<CatalystMaterialStatsType>> getAllCatalystStats() {
        return TYPES.values();
    }

    @Override
    public void apply(ModifierStatsBuilder builder, float scale) {
    }

    @Override
    public List<Component> getLocalizedDescriptions() {
        return DESCRIPTION;
    }

    @Override
    public List<Component> getLocalizedInfo() {
        return List.of(IMaterialStats.makeTooltip(TConstruct.getResource("extra.no_stats")));
    }

    @Override
    public MutableComponent getLocalizedName() {
        return IMaterialStats.super.getLocalizedName().withStyle(ChatFormatting.AQUA);
    }

    static {
        LOADABLE = RecordLoadable.create(MaterialStatType.CONTEXT_KEY.requiredField(), CatalystMaterialStatsType::new);
        DESCRIPTION = List.of(Component.empty());
        TYPES = new HashMap<>();
        SERAM = getOrMakeType("seram");
    }
}
