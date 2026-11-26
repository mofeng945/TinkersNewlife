package com.mofengbaizhi.tinkersnewlife.content.block;

/**
 * <b>"我持有转化器核心"</b>（§559）—— 一个<b>零可选模组引用</b>的极简接口。
 *
 * <h2>为什么需要它</h2>
 * 转化器有两种壳：
 * <ul>
 *   <li>{@code EnergyConverterBlockEntity extends BlockEntity}（没装 Create ✓）；</li>
 *   <li>{@code CreateEnergyConverterBlockEntity extends KineticBlockEntity}（装了 Create ✓）。</li>
 * </ul>
 * 而中立的 {@link EnergyConverterModBridges} 需要在"每 tick 那一段"里拿到它们的核心 ✗ ——
 * 它<b>不能</b>去 {@code instanceof CreateEnergyConverterBlockEntity} ✗
 * （那会让这个中立类 import Create 的类型 ⇒ 隔离就白做了 ✗）。
 *
 * <p>⇒ 让两个壳都实现本接口 ✓ 中立类只认它 ✓
 * 于是"哪些类引用可选模组"这件事被彻底钉死：
 * <pre>
 *   引用 Create 的：CreateEnergyConverterBlock / CreateEnergyConverterBlockEntity   （只有这两个 ✓）
 *   引用 Mekanism 的：integration/mekanism/MekanismEnergyBridge（+ §557 的反射版适配器 ✓）
 *   引用 AE2 的：integration/ae2/Ae2GridBridge（+ §557 的反射版适配器 ✓）
 *   其余全部中立 ✓
 * </pre>
 */
public interface ConverterCoreHolder {

    /** 本方块实体持有的转化器核心（状态 + 逻辑 ✓ 永不为 null ✓） */
    EeConverterCore converterCore();
}
