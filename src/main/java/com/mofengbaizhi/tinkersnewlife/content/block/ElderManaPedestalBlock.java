package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>魔力台座</b>：把古老者水晶放上去，它就会<b>吸收周围环境能量给水晶充能</b> ✓
 * （用户口径：水晶<b>不会</b>自动充能 ⇒ "必须放在魔力台座上吸收周围能量才充电" ✓）。
 *
 * <h2>怎么用（一句话版）</h2>
 * <ul>
 *   <li><b>放</b>：手持 {@code tinkersnewlife:elder_crystal} 右击台座（台座必须空着 ✓ 一次只放一个 ✓）；</li>
 *   <li><b>取</b>：<b>空手右击</b> 或 <b>潜行右击</b>（潜行时手里拿什么都能取 ✓）⇒ 连同里面的 EE 一起拿回来 ✓；</li>
 *   <li>台座 <b>上方/下方/四邻</b> 的<b>古老者水晶方块</b>也会被一起充能 ✓（容量 4000 ✓）；</li>
 *   <li>充能规律 = <b>亮度越低越快</b>（默认满速 5 EE/秒；公式在 {@code content/energy/LightLevelEnergySource}）✓。</li>
 * </ul>
 *
 * <h2>为什么取回判定是"空手 或 潜行"</h2>
 * 目标是<b>绝不误触</b>又<b>不用记快捷键</b> ✓：
 * <ul>
 *   <li>手里拿着别的东西（方块/食物/法杖…）普通右击 ⇒ <b>什么都不做</b>（{@code PASS}）⇒ 不会因为
 *       "想放个方块"把台座上的水晶顶下来 ✗；</li>
 *   <li>想取回时：手空着最自然（谁都会先空手去摸一下 ✓）；带着东西时按潜行也一定取回 ✓；</li>
 *   <li>台座上已经有水晶、手里又拿着水晶 ⇒ 普通右击<b>不动</b>（不交换、不叠加 ✗ 免得手滑丢电 ✓）。</li>
 * </ul>
 *
 * <h2>挖掉台座：水晶不会丢</h2>
 * 走 {@link #getDrops} 把台座上的水晶（连它 NBT 里的 EE）追加进掉落物 ✓
 * —— 借 loot table 的 {@code LootContextParams.BLOCK_ENTITY} 参数拿方块实体 ✓，
 * 于是<b>玩家挖、爆炸炸</b>都走同一条路 ✓（比只挂 {@code playerWillDestroy} 覆盖面更全 ✓，
 * 而这里<b>不能</b>再挂 {@code playerWillDestroy} ⇒ 玩家挖会掉两份 ✗）。
 *
 * <h2>⚠ 台座刻意不发光</h2>
 * 见 {@link ElderManaPedestalBlockEntity} 的类注释：自己发光会抬高上方那格的亮度 ⇒
 * 按"亮度越低越快"的规则反过来拖慢自己 ✗。"正在充能"的表现交给冷色粒子 + 轻微风铃 ✓。
 */
public class ElderManaPedestalBlock extends BaseEntityBlock {

    public ElderManaPedestalBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .sound(SoundType.AMETHYST)
                .requiresCorrectToolForDrops()   // 已在 minecraft:mineable/pickaxe 标签里 ✓
                .mapColor(MapColor.COLOR_PURPLE));
    }

    // ============================================================
    //  方块实体
    // ============================================================

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 台座本体是普通方块模型 ✓（浮在上面的水晶由 BER 画 ✓ 见 client/renderer/ElderManaPedestalRenderer）
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElderManaPedestalBlockEntity(pos, state);
    }

    /** 服务端每 tick 驱动（充能结算 ✓）；客户端不挂 ticker ✗（数据是服务端权威的 ✓） */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.ELDER_MANA_PEDESTAL.get(),
                ElderManaPedestalBlockEntity::serverTick);
    }

    // ============================================================
    //  放 / 取 水晶
    // ============================================================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElderManaPedestalBlockEntity pedestal)) {
            return InteractionResult.PASS;   // 方块实体没就绪（理论上不会）⇒ 不硬来 ✗
        }
        ItemStack held = player.getItemInHand(hand);

        // ① 取回：空手右击，或潜行右击（手里拿什么都能取 ✓）
        if (pedestal.hasCrystal() && (held.isEmpty() || player.isShiftKeyDown())) {
            if (!level.isClientSide) {
                ItemStack taken = pedestal.takeCrystal();
                if (!taken.isEmpty() && !player.getInventory().add(taken)) {
                    player.drop(taken, false);   // 背包满 ⇒ 掉在脚下，绝不凭空消失 ✗
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ② 放上去：台座空着 + 手里正好拿着水晶（一次只放一个 ✓ 剩下的留在手里 ✓）
        if (!pedestal.hasCrystal() && isElderCrystal(held)) {
            if (!level.isClientSide) {
                ItemStack one = held.copy();
                one.setCount(1);
                pedestal.setCrystal(one);
                held.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 其它情况（台座上有水晶而手里拿着别的东西 等）⇒ 什么都不做 ✓
        return InteractionResult.PASS;
    }

    /** 手里拿着的是古老者水晶**物品**吗（水晶方块物品不放台座 ✗ —— 方块直接摆在地上也能被充能 ✓） */
    private static boolean isElderCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.ELDER_CRYSTAL.get();
    }

    // ============================================================
    //  挖掉：把台座上的水晶一起还回来
    // ============================================================

    /**
     * 掉落 = 战利品表（台座自己）+ 台座上的水晶（连 EE ✓）。
     * <p>{@code getDrops} 在 1.20.1 标了 {@code @Deprecated}，但它依然是"所有破坏途径"（玩家挖 / 爆炸 /
     * 其它模组拆方块）共用的那一个钩子 ✓ 所以继续用它 ✓。
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof ElderManaPedestalBlockEntity pedestal && pedestal.hasCrystal()) {
            // 台座上只可能是一颗古老者水晶物品 ⇒ 整栈搬走（EE 在它的 NBT 里，一点不丢 ✓）
            drops.add(pedestal.getCrystal().copy());
        }
        return drops;
    }
}
