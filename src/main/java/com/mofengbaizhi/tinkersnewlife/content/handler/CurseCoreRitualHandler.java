package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.BaTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.BaseTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.KaiTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.ZaoKaiTechnique;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 咒力核心获取仪式（多方块结构）
 * <p>
 * 结构（量器为 tconstruct:seared_ingot_gauge「焦黑材料量器」，可存 48 锭流体）：
 * - 最中间：焦黑材料量器；量器正上方：格赫罗斯矿石
 * - 量器四周隔一格（距矿石 2 格）分别放置 石头 / 铁块 / 金块 / 钻石块（任意方位）
 * - 这四个方块上方各放一个灵魂灯笼
 * - 量器内需填充 ≥6 锭（864 mB）的对应材料流体（焦黑熔石/熔融铁/熔融金/熔融钻石）
 * <p>
 * 空手右键格赫罗斯矿石发动：仪式持续 5 秒（量器按流体颜色发射信标光束，
 * 四面灯笼顶端向矿石发射其下方方块破碎粒子连线，密度较高）。
 * 完成时消耗 6 锭材料流体 + 玩家 50 级经验，在格赫罗斯矿石上生成咒力核心：
 * 咒力总量/输出等级各随机 1-5，随机附一个术式与一个领域。
 * 流体不足 6 锭或结构不完整时静默不发动。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CurseCoreRitualHandler {

    /** 仪式时长：5 秒 */
    private static final int RITUAL_TICKS = 5 * 20;
    /** 所需/消耗材料流体：6 锭 */
    private static final int FLUID_UNITS = 6;
    /** 1 锭 = 144 mB */
    private static final int MB_PER_UNIT = 144;
    private static final int REQUIRED_MB = FLUID_UNITS * MB_PER_UNIT; // 864
    /** 消耗玩家经验等级 */
    private static final int XP_LEVELS = 50;
    /** 材料方块距量器的水平距离（隔一格） */
    private static final int STRUCTURE_DISTANCE = 2;

    /** 进行中的仪式：矿石位置 → 仪式数据 */
    private static final Map<BlockPos, RitualData> RITUALS = new ConcurrentHashMap<>();

    /** 材料方块 → 对应材料流体（焦黑熔石/熔融铁/熔融金/熔融钻石） */
    private static final Map<Block, Fluid> MATERIAL_FLUIDS = new HashMap<>();
    /** 材料流体 → 信标光束颜色（服务端无 FluidType 颜色接口，固定映射） */
    private static final Map<Fluid, Integer> MATERIAL_FLUID_COLORS = new HashMap<>();
    private static Block gaugeBlock = null;

    static {
        MATERIAL_FLUIDS.put(Blocks.STONE, fluid("tconstruct:seared_stone"));
        MATERIAL_FLUIDS.put(Blocks.IRON_BLOCK, fluid("tconstruct:molten_iron"));
        MATERIAL_FLUIDS.put(Blocks.GOLD_BLOCK, fluid("tconstruct:molten_gold"));
        MATERIAL_FLUIDS.put(Blocks.DIAMOND_BLOCK, fluid("tconstruct:molten_diamond"));
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:seared_stone"), 0x9a9a9a);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_iron"), 0xe8e8e8);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_gold"), 0xffd75f);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_diamond"), 0x6ee7ff);
    }

    private CurseCoreRitualHandler() {}

    private static Fluid fluid(String id) {
        return ForgeRegistries.FLUIDS.getValue(ResourceLocation.tryParse(id));
    }

    // ============================================================
    //  发动：空手右键格赫罗斯矿石
    // ============================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        if (player == null || player.level().isClientSide) return;
        if (!player.getMainHandItem().isEmpty()) return; // 必须空手
        Level level = player.level();
        BlockPos orePos = event.getPos();
        if (!level.getBlockState(orePos).is(ModBlocks.GHELOTH_ORE.get())) return;
        if (RITUALS.containsKey(orePos)) return;

        // 结构 + 流体校验（不满足 → 静默）
        RitualStart start = validate(level, orePos);
        if (start == null) return;
        event.setCanceled(true);

        if (player instanceof ServerPlayer sp) {
            if (sp.experienceLevel < XP_LEVELS) {
                sp.displayClientMessage(Component.translatable("message.tinkersnewlife.ritual.no_xp", XP_LEVELS), true);
                return;
            }
            RITUALS.put(orePos, new RitualData(sp.getUUID(), level.dimension(), orePos,
                    start.gaugePos, start.fluid, start.color, start.lanterns, start.materialStates));
        }
    }

    /** 结构 + 流体校验；不满足返回 null（静默） */
    private static RitualStart validate(Level level, BlockPos orePos) {
        // 1. 量器在矿石正下方
        BlockPos gaugePos = orePos.below();
        if (gaugeBlock == null) {
            gaugeBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse("tconstruct:seared_ingot_gauge"));
        }
        if (gaugeBlock == null || !level.getBlockState(gaugePos).is(gaugeBlock)) return null;

        // 2. 四周隔一格：石头/铁块/金块/钻石块（恰好各一个，任意方位）+ 上方灵魂灯笼
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        Set<Block> found = new HashSet<>();
        List<BlockPos> lanterns = new ArrayList<>();
        List<BlockState> materialStates = new ArrayList<>();
        for (Direction dir : dirs) {
            BlockPos mPos = orePos.offset(dir.getStepX() * STRUCTURE_DISTANCE, -1, dir.getStepZ() * STRUCTURE_DISTANCE);
            BlockState mState = level.getBlockState(mPos);
            if (!MATERIAL_FLUIDS.containsKey(mState.getBlock())) return null;
            found.add(mState.getBlock());
            if (!level.getBlockState(mPos.above()).is(Blocks.SOUL_LANTERN)) return null;
            lanterns.add(mPos.above());
            materialStates.add(mState);
        }
        if (found.size() != 4) return null;

        // 3. 量器内 ≥6 锭的对应材料流体（静默）
        if (level.getBlockEntity(gaugePos) == null) return null;
        IFluidHandler handler = level.getBlockEntity(gaugePos)
                .getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
        if (handler == null) return null;
        for (Fluid fluid : MATERIAL_FLUIDS.values()) {
            if (fluid == null) continue;
            FluidStack sim = handler.drain(new FluidStack(fluid, REQUIRED_MB), IFluidHandler.FluidAction.SIMULATE);
            if (sim.getAmount() >= REQUIRED_MB) {
                int color = MATERIAL_FLUID_COLORS.getOrDefault(fluid, 0xffffff);
                return new RitualStart(gaugePos, fluid, color, lanterns, materialStates);
            }
        }
        return null;
    }

    // ============================================================
    //  仪式进行：5 秒粒子 + 完成结算
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || RITUALS.isEmpty()) return;

        for (Map.Entry<BlockPos, RitualData> entry : new ArrayList<>(RITUALS.entrySet())) {
            BlockPos orePos = entry.getKey();
            RitualData data = entry.getValue();
            ServerLevel level = server.getLevel(data.dimension);
            if (level == null) {
                RITUALS.remove(orePos);
                continue;
            }

            // 粒子
            spawnParticles(level, orePos, data);

            data.ticksLeft--;
            if (data.ticksLeft > 0) continue;

            // ⭐ 完成：消耗流体 + 经验，生成咒力核心
            RITUALS.remove(orePos);
            complete(server, level, orePos, data);
        }
    }

    /** 仪式粒子：量器按流体颜色发射信标光束 + 四灯笼向矿石发射材质破碎粒子连线 */
    private static void spawnParticles(ServerLevel level, BlockPos orePos, RitualData data) {
        // 信标光束：量器上方垂直彩色光柱
        int r = (data.color >> 16) & 0xFF;
        int g = (data.color >> 8) & 0xFF;
        int b = data.color & 0xFF;
        double bx = orePos.getX() + 0.5;
        double bz = orePos.getZ() + 0.5;
        for (int i = 0; i < 14; i++) {
            double y = data.gaugePos.getY() + 1 + i * 0.8;
            level.sendParticles(ParticleTypes.ENTITY_EFFECT, bx, y, bz, 1, r / 255f, g / 255f, b / 255f, 1.0f);
        }
        // 灯笼 → 矿石连线：材质破碎粒子（密度稍大：每灯笼每 tick 5 粒）
        Vec3 oreCenter = new Vec3(orePos.getX() + 0.5, orePos.getY() + 1.3, orePos.getZ() + 0.5);
        for (int li = 0; li < data.lanterns.size(); li++) {
            BlockPos lan = data.lanterns.get(li);
            BlockState mState = data.materialStates.get(li);
            Vec3 from = new Vec3(lan.getX() + 0.5, lan.getY() + 1.2, lan.getZ() + 0.5);
            for (int k = 0; k < 5; k++) {
                Vec3 p = from.lerp(oreCenter, k / 4.0);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, mState),
                        p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 完成：消耗 6 锭流体 + 50 级经验，在矿石上生成随机咒力核心 */
    private static void complete(MinecraftServer server, ServerLevel level, BlockPos orePos, RitualData data) {
        // 消耗材料流体 6 锭
        if (level.getBlockEntity(data.gaugePos) != null) {
            IFluidHandler handler = level.getBlockEntity(data.gaugePos)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
            if (handler != null) {
                handler.drain(new FluidStack(data.fluid, REQUIRED_MB), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        // 消耗 50 级经验
        ServerPlayer player = server.getPlayerList().getPlayer(data.playerId);
        if (player != null) {
            player.giveExperienceLevels(-XP_LEVELS);
        }
        // 生成咒力核心（随机总量/输出 1-5 + 随机术式 + 随机领域），掉在矿石上方
        ItemStack core = generateCurseCore(level.random);
        Vec3 drop = new Vec3(orePos.getX() + 0.5, orePos.getY() + 1.5, orePos.getZ() + 0.5);
        level.addFreshEntity(new ItemEntity(level, drop.x, drop.y, drop.z, core));
    }

    /** 生成随机咒力核心：咒力总量/输出 1-5、随机术式、随机领域 */
    private static ItemStack generateCurseCore(RandomSource random) {
        ItemStack base = new ItemStack(ModItems.CURSE_CORE.get());
        ToolStack tool = ToolStack.from(base);
        if (tool == null) return base;

        int total = 1 + random.nextInt(5);   // 咒力总量 1-5
        int output = 1 + random.nextInt(5);  // 咒力输出 1-5
        // ⭐ 特性已自带 1 级，只需再补 (等级-1) 级
        tool.addModifier(Modifiers.CURSE_TOTAL.getId(), total - 1);
        tool.addModifier(Modifiers.CURSE_OUTPUT.getId(), output - 1);

        // 随机术式（解/捌/灶·开）
        BaseTechnique[] techniques = {
                KaiTechnique.INSTANCE, BaTechnique.INSTANCE, ZaoKaiTechnique.INSTANCE
        };
        tool.addModifier(techniques[random.nextInt(techniques.length)].getModifierId(), 1);

        // 随机领域（坐杀搏徒/无量空处/伏魔御厨子）
        ModifierId[] domains = {
                Modifiers.ZUOSHA_BOTU.getId(), Modifiers.WULIANG_KONGCHU.getId(), Modifiers.FUMO_YUCHUZI.getId()
        };
        tool.addModifier(domains[random.nextInt(domains.length)], 1);

        return tool.createStack();
    }

    // ============================================================
    //  数据
    // ============================================================

    private static class RitualStart {
        final BlockPos gaugePos;
        final Fluid fluid;
        final int color;
        final List<BlockPos> lanterns;
        final List<BlockState> materialStates;

        RitualStart(BlockPos gaugePos, Fluid fluid, int color,
                    List<BlockPos> lanterns, List<BlockState> materialStates) {
            this.gaugePos = gaugePos;
            this.fluid = fluid;
            this.color = color;
            this.lanterns = lanterns;
            this.materialStates = materialStates;
        }
    }

    private static class RitualData {
        final UUID playerId;
        final ResourceKey<Level> dimension;
        final BlockPos gaugePos;
        final Fluid fluid;
        final int color;
        final List<BlockPos> lanterns;
        final List<BlockState> materialStates;
        int ticksLeft;

        RitualData(UUID playerId, ResourceKey<Level> dimension, BlockPos orePos,
                   BlockPos gaugePos, Fluid fluid, int color,
                   List<BlockPos> lanterns, List<BlockState> materialStates) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.gaugePos = gaugePos;
            this.fluid = fluid;
            this.color = color;
            this.lanterns = lanterns;
            this.materialStates = materialStates;
            this.ticksLeft = RITUAL_TICKS;
        }
    }
}
