package com.mofengbaizhi.tinkersnewlife.content.block;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * <b>万用能量转化器的方块 —— 装了机械动力（Create）时注册的那一支</b>（§559）：
 * 它是一个真正的<b>动能方块</b> ⇒ <b>传动杆能侧面接上</b> ✓（用户口径）。
 *
 * <h2>核到的真实类名 / 方法名（出处：{@code libs/create-1.20.1-6.0.8.jar}，javap ✓）</h2>
 * <pre>
 *   com.simibubi.create.content.kinetics.base.KineticBlock extends HorizontalKineticBlock
 *       KineticBlock(BlockBehaviour.Properties)
 *       boolean hasShaftTowards(LevelReader, BlockPos, BlockState, Direction)   ← 默认返回 true ✓
 *   com.simibubi.create.content.kinetics.base.IRotate
 *       Direction.Axis getRotationAxis(BlockState)      ← 必须给一个轴 ✓
 *       boolean hasShaftTowards(...)                     ← KineticBlock 已经给了默认实现 ✓
 * </pre>
 *
 * <h2>⚠ 一个重要的选择：旋转轴用 <b>Y（竖直）</b></h2>
 * 我们这台机器的模型是"立着的"（底台 + 机体 + 天线 ✓ 见 {@code models/block/energy_converter.json}），
 * 而 Create 的传动杆/齿轮是按<b>轴</b>对齐的 ✗ ⇒ 用 Y 轴（竖直）最自然 ✓
 * （相当于"底部或顶部接一根竖直的传动杆/齿轮箱"✓ 与模型不打架 ✓）。
 * <p>⚠ 诚实项：<b>这台机器不产生转速、也不消耗应力</b> ✓ ——
 * 它只是"被带着转"（读转速换 FE ✓ §557 的取舍未变 ✓）。
 * 所以 {@code getGeneratedSpeed()} **保持默认 0** ✓（那是风车/发电机才该覆写的 ✓）。
 *
 * <h2>为什么与普通那一支是两个类</h2>
 * 因为 {@code KineticBlock} 是 Create 的类 ✗ —— 没装 Create 的玩家加载它会
 * {@code NoClassDefFoundError} 崩游戏 ✗✗ ⇒ 只能在 {@code ModList.isLoaded("create")} 时注册它 ✓
 * （见 {@code ModBlocks} 的分派 ✓）。两个类只有"父类 + 传动杆相关的几个方法"不同 ✓
 * 行为逻辑（tick/capability/存档）全在 {@link CreateEnergyConverterBlockEntity} 与
 * 共享的 {@link EeConverterCore} 里 ✓ ⇒ 没有两份会各自漂移的实现 ✗。
 */
public class CreateEnergyConverterBlock extends KineticBlock {

    public CreateEnergyConverterBlock() {
        super(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                .strength(3.5F, 6.0F)
                .sound(net.minecraft.world.level.block.SoundType.METAL)
                .requiresCorrectToolForDrops()   // 已加进 minecraft:mineable/pickaxe 标签 ✓
                .mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN));
    }

    /**
     * 旋转轴 = <b>Y</b>（竖直）✓ 见类注释。
     * <p>{@code IRotate} 要求实现这一个方法 ✓（{@code KineticBlock} 只给了
     * {@code hasShaftTowards} 的默认实现，轴必须我们自己给 ✓）。
     */
    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    /**
     * 传动杆可以从哪几面接上来 ⇒ <b>六面全开</b> ✓（竖直轴 + 六面都算"能接"⇒ 玩家怎么摆都能接上 ✓
     * 这也是"接了就有电"最不容易让人困惑的行为 ✓）。
     * <p>⚠ 覆写的一行：{@code KineticBlock} 的默认实现本来就返回 {@code true} ✓
     * 这里显式写出来是为了"以后有人改默认值时我们不会被顺手改掉" ✓（也就是把它钉死 ✓）。
     */
    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction side) {
        return true;
    }

    // ============================================================
    //  ⚠ 渲染形状必须显式覆写（否则方块是隐形的 ✗）
    // ============================================================
    // ⚠ 原因：原版 {@code Block} 的默认 {@code getRenderShape} 是 {@code INVISIBLE} ✗
    //   而 {@code BaseEntityBlock} 才把默认值改成 {@code MODEL} ✓ —— 我们的父类
    //   {@code KineticBlock} 走的是前者 ✗（它不是 BaseEntityBlock ✗ 见上面的说明）
    //   ⇒ 不覆写这一行的话，转化器在装了 Create 的实例里会**看不见** ✗（只剩碰撞箱 ✓）。
    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        // 还是普通方块模型 ✓（模型/贴图与普通那一支完全共用 ✓ 见 models/block/energy_converter.json ✓）
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    // ============================================================
    //  ⚠⚠ 这里**不能**覆写 newBlockEntity / getTicker（踩坑记录 ✗）
    // ============================================================
    // 我第一版按"BaseEntityBlock 那一套"写了这两个 @Override，javac 直接报
    //   "方法不会覆盖或实现超类型的方法" ✗
    // ——原因：`KineticBlock` 的继承链是
    //   KineticBlock → net.minecraft.world.level.block.Block
    //   （**不是** BaseEntityBlock ✗！）
    // ⇒ 它既没有 `newBlockEntity` 也没有 `getTicker` ✗。
    //
    // ⇒ 于是"方块实体谁来建 / ticker 谁去挂"必须用**其它**办法：
    //   ① 建方块实体：`BlockEntityType.Builder.of(factory, block)` 里的 factory 是我们自己给的 ✓
    //      （见 `ModBlockEntities.CREATE_ENERGY_CONVERTER` 与下面的 `createBlockEntity` ✓）——
    //      ⚠ 注意：原版只有 {@code BaseEntityBlock} 才会自动调 {@code newBlockEntity} ✗，
    //        KineticBlock 不会 ⇒ 必须由 Builder 那一侧的工厂负责 ✓（Forge 的 Builder 就是这样工作的 ✓）；
    //   ② ticker：覆写**方块实体自己的** {@code KineticBlockEntity#tick()}
    //      （Create 自己会在服务端每 tick 调它 ✓）⇒ 见
    //      `CreateEnergyConverterBlockEntity#tick()` ✓。
    //
    // ⚠ 这两处都要靠"注册那一行"与"实体里的 tick"配合 ✓ 不要再试图在方块类里覆写 ✗。

    /**
     * 给 {@code ModBlockEntities} 用的方块实体工厂（**必须由注册那一行显式创建** ✗
     * —— 因为 {@code KineticBlock} 没有 {@code newBlockEntity} 可覆写 ✓ 见上面的说明）。
     */
    public static BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CreateEnergyConverterBlockEntity(pos, state);
    }
}
