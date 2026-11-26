package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 按注册名"安全"取注册表对象——**没这个类很容易踩 Forge 的默认值陷阱**。
 *
 * <h2>⚠ 陷阱（2026-09-13 实测崩溃）</h2>
 * {@code ForgeRegistries.X.getValue(id)} 对**不存在的 id 不是返回 null**，而是返回该注册表的
 * <b>默认值</b>（源码 {@code ForgeRegistry#getValue}：{@code return ret == null ? this.defaultValue : ret;}）：
 * <ul>
 *   <li>{@code ITEMS} → {@code Items.AIR}：`new ItemStack(AIR)` 的 {@code getCount()} 是 <b>0</b>
 *       （{@code getCount()} 对空栈直接返回 0），塞进创造栏会抛
 *       {@code IllegalArgumentException: The stack count must be 1}；</li>
 *   <li>{@code FLUIDS} → {@code Fluids.EMPTY}（`fluid == null` 判空永远不成立）；</li>
 *   <li>{@code BLOCKS} → {@code Blocks.AIR}。</li>
 * </ul>
 * 联动内容按注册名取用时（模组不在场 → 该注册项根本不存在）**必须**走本类，
 * 与 {@code containsKey} 一起判定，才能得到干净的 {@code null}。
 */
public final class SafeRegistry {

    private SafeRegistry() {
    }

    /** 物品；不存在返回 null（而不是 {@code Items.AIR}） */
    @Nullable
    public static Item item(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
        return item == null || item == Items.AIR ? null : item;
    }

    @Nullable
    public static Item item(String namespace, String path) {
        return item(ResourceLocation.tryParse(namespace + ":" + path));
    }

    /** 流体；不存在返回 null（而不是 {@code Fluids.EMPTY}） */
    @Nullable
    public static Fluid fluid(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Fluid fluid = ForgeRegistries.FLUIDS.containsKey(id) ? ForgeRegistries.FLUIDS.getValue(id) : null;
        return fluid == null || fluid == Fluids.EMPTY ? null : fluid;
    }

    @Nullable
    public static Fluid fluid(String namespace, String path) {
        return fluid(ResourceLocation.tryParse(namespace + ":" + path));
    }

    /** 方块；不存在返回 null（而不是 {@code Blocks.AIR}） */
    @Nullable
    public static Block block(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Block block = ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
        return block == null || block == Blocks.AIR ? null : block;
    }

    @Nullable
    public static Block block(String namespace, String path) {
        return block(ResourceLocation.tryParse(namespace + ":" + path));
    }

    /** 实体类型；不存在返回 null */
    @Nullable
    public static EntityType<?> entityType(@Nullable ResourceLocation id) {
        if (id == null) return null;
        return ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
    }

    /** 音效；不存在返回 null */
    @Nullable
    public static net.minecraft.sounds.SoundEvent sound(@Nullable ResourceLocation id) {
        if (id == null) return null;
        return ForgeRegistries.SOUND_EVENTS.containsKey(id) ? ForgeRegistries.SOUND_EVENTS.getValue(id) : null;
    }
}
