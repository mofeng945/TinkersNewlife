package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 呪蔵能量视觉方块（纯渲染载体）：只用来给 {@code CurseVaultRenderer} 提供"能查到的方块模型"。
 *
 * <h2>为什么不直接用"独立模型"（{@code ModelEvent.RegisterAdditional}）</h2>
 * 第一版走的是那条路：把 {@code block/curse_vault_core} 注册成独立模型，再用
 * {@code ModelManager#getModel(new ModelResourceLocation(id, "standalone"))} 取回来。
 * 实机日志证明<b>取不到</b>：
 * <pre>
 * [呪蔵诊断] core 解析成功=false  spark 解析成功=false
 * [呪蔵] 能量模型未注册或未烘焙：tinkersnewlife:block/curse_vault_core#standalone，笼内能量不会绘制
 * </pre>
 * 于是改用<b>本模组已经验证可行</b>的做法（狱门疆 {@code GourdJailVisualBlock} 就是这么干的）：
 * 注册一个"视觉方块"，它的 blockstate 指向能量模型，渲染时用
 * {@code BlockRenderDispatcher#renderSingleBlock} 画出来。方块本身无物品形态、无碰撞、
 * 无战利品，永远不会在世界里真实生成。
 *
 * <p>需要两个实例：主能量团 {@code curse_vault_core_visual}、环绕火花 {@code curse_vault_spark_visual}。
 */
public class CurseVaultVisualBlock extends Block {

    public CurseVaultVisualBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .noCollission()
                .noOcclusion()
                .noLootTable()
                .instabreak()
                .noParticlesOnBreak()
        );
    }
}
