package com.mofengbaizhi.tinkersnewlife.content.curse.ritual;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationHyakurenTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationSupernovaTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.KaiTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenShadowsTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.JacobsLadderTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ReverseCursedTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ZaoKaiTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuWeiTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique;
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
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.TinkerRecipeTypes;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
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
 * - 咒力核心材质 = 消耗的材料流体对应材料（经匠魂 material_fluid 配方数据关联，
 *   流体无对应材料时静默失败）
 * - 咒力总量/输出等级各随机 1-5，随机附一个术式与一个领域（各占一个术式槽/领域槽）
 * - 30% 概率额外带有术式槽（基础 1 个，最多 3 个）
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
    /** 常见材料流体 → 信标光束颜色（服务端无 FluidType 颜色接口，固定映射） */
    private static final Map<Fluid, Integer> MATERIAL_FLUID_COLORS = new HashMap<>();
    /** 未知流体的默认光束颜色 */
    private static final int DEFAULT_BEAM_COLOR = 0xffffff;
    private static Block gaugeSeared = null;
    private static Block gaugeScorched = null;

    static {
        MATERIAL_FLUIDS.put(Blocks.STONE, fluid("tconstruct:seared_stone"));
        MATERIAL_FLUIDS.put(Blocks.IRON_BLOCK, fluid("tconstruct:molten_iron"));
        MATERIAL_FLUIDS.put(Blocks.GOLD_BLOCK, fluid("tconstruct:molten_gold"));
        MATERIAL_FLUIDS.put(Blocks.DIAMOND_BLOCK, fluid("tconstruct:molten_diamond"));
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:seared_stone"), 0x9a9a9a);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_iron"), 0xe8e8e8);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_gold"), 0xffd75f);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_diamond"), 0x6ee7ff);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_rose_gold"), 0xffb39b);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_copper"), 0xff8c5a);
        MATERIAL_FLUID_COLORS.put(fluid("tconstruct:molten_netherite"), 0x4a3a33);
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
        if (!(player instanceof ServerPlayer sp)) return;
        if (!sp.getMainHandItem().isEmpty()) return; // 必须空手
        Level level = sp.level();
        BlockPos orePos = event.getPos();
        if (!level.getBlockState(orePos).is(ModBlocks.GHELOTH_ORE.get())) return;
        if (RITUALS.containsKey(orePos)) return;

        // 结构 + 流体 + 材料校验（不满足 → 静默）
        RitualStart start = validate(sp, orePos);
        if (start == null) return;
        event.setCanceled(true);

        if (sp.experienceLevel < XP_LEVELS) {
            sp.displayClientMessage(Component.translatable("message.tinkersnewlife.ritual.no_xp", XP_LEVELS), true);
            return;
        }
        RITUALS.put(orePos, new RitualData(sp.getUUID(), level.dimension(), orePos,
                start.gaugePos, start.fluid, start.color, start.material, start.lanterns, start.materialStates));
    }

    /** 结构 + 流体 + 材料校验；不满足返回 null（静默） */
    private static RitualStart validate(ServerPlayer player, BlockPos orePos) {
        Level level = player.level();
        // 1. 量器在矿石正下方（焦黑 seared_ingot_gauge / 焦褐 scorched_ingot_gauge 均可）
        BlockPos gaugePos = orePos.below();
        if (gaugeSeared == null) {
            gaugeSeared = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse("tconstruct:seared_ingot_gauge"));
            gaugeScorched = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse("tconstruct:scorched_ingot_gauge"));
        }
        BlockState gaugeState = level.getBlockState(gaugePos);
        if (gaugeSeared == null || (!gaugeState.is(gaugeSeared) && !gaugeState.is(gaugeScorched))) return null;

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

        // 3. 量器内任意材料流体 ≥6 锭（对应材料流体，静默）
        if (level.getBlockEntity(gaugePos) == null) return null;
        IFluidHandler handler = level.getBlockEntity(gaugePos)
                .getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
        if (handler == null) return null;
        Fluid fluid = null;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack contents = handler.getFluidInTank(tank);
            if (contents.getAmount() >= REQUIRED_MB) {
                fluid = contents.getFluid();
                break;
            }
        }
        if (fluid == null) return null;
        // 4. 读取匠魂 material_fluid 数据结构：流体 → 材料（无对应材料的咒力核心 → 静默）
        IMaterial material = materialFromFluid(player.server, fluid);
        if (material == null || material == IMaterial.UNKNOWN) return null;
        int color = MATERIAL_FLUID_COLORS.getOrDefault(fluid, DEFAULT_BEAM_COLOR);
        return new RitualStart(gaugePos, fluid, color, material, lanterns, materialStates);
    }

    /** 从匠魂 material_fluid 配方数据读取流体对应的材料（输出材料） */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IMaterial materialFromFluid(MinecraftServer server, Fluid fluid) {
        try {
            net.minecraft.world.item.crafting.RecipeType rawType = TinkerRecipeTypes.DATA.get();
            for (net.minecraft.world.item.crafting.Recipe<?> recipe :
                    (java.util.Collection<net.minecraft.world.item.crafting.Recipe<?>>)
                            (java.util.Collection<?>) server.getRecipeManager().getAllRecipesFor(rawType)) {
                if (recipe instanceof MaterialFluidRecipe mfr && mfr.matches(fluid)) {
                    return mfr.getOutput().get();
                }
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[TinkersNewlife] 读取材料流体关联失败: {}", t.toString());
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
        // 生成咒力核心（对应材料 + 随机总量/输出 1-5 + 随机术式/领域，30% 额外术式槽），掉在矿石上方
        ItemStack core = generateCurseCore(level.random, data.material);
        Vec3 drop = new Vec3(orePos.getX() + 0.5, orePos.getY() + 1.5, orePos.getZ() + 0.5);
        level.addFreshEntity(new ItemEntity(level, drop.x, drop.y, drop.z, core));
    }

    /** 生成咒力核心：消耗什么材料就给什么材料；总量/输出 1-5；随机术式与领域；30% 概率额外术式槽（最多 3 个） */
    private static ItemStack generateCurseCore(RandomSource random, IMaterial material) {
        ItemStack base = new ItemStack(ModItems.CURSE_CORE.get());
        ToolStack tool = ToolStack.from(base);
        if (tool == null) return base;

        // 部件材质 = 仪式消耗的材料（消耗什么材料就给什么材料的咒力核心）
        if (material != null && material != IMaterial.UNKNOWN) {
            tool.setMaterials(MaterialNBT.of(material));
            tool.rebuildStats();
        }

        int total = 1 + random.nextInt(5);   // 咒力总量 1-5
        int output = 1 + random.nextInt(5);  // 咒力输出 1-5
        // ⭐ 特性已自带 1 级，只需再补 (等级-1) 级；addModifier 不接受 0 级，>1 时才补
        if (total > 1) {
            tool.addModifier(Modifiers.CURSE_TOTAL.getId(), total - 1);
        }
        if (output > 1) {
            tool.addModifier(Modifiers.CURSE_OUTPUT.getId(), output - 1);
        }

        // 随机术式（解/捌/灶·开）与随机领域（坐杀搏徒/无量空处/伏魔御厨子）
        // ⭐ addModifier 只加修饰符不扣槽位（配方系统才扣），这里手动各消耗一个术式槽/领域槽
        SlotType techniqueSlot = SlotType.getOrCreate("technique");
        SlotType domainSlot = SlotType.getOrCreate("domain");
        BaseTechnique[] techniques = {
                KaiTechnique.INSTANCE, BaTechnique.INSTANCE, ZaoKaiTechnique.INSTANCE,
                BloodManipulationTechnique.INSTANCE, BloodManipulationHyakurenTechnique.INSTANCE,
                BloodManipulationSupernovaTechnique.INSTANCE, TenShadowsTechnique.INSTANCE,
                BlackBirdTechnique.INSTANCE, ProjectionTechnique.INSTANCE, WuliangWuxianTechnique.INSTANCE,
                WuliangCangTechnique.INSTANCE, JacobsLadderTechnique.INSTANCE, ReverseCursedTechnique.INSTANCE,
                WuWeiTechnique.INSTANCE, PuppetTechnique.INSTANCE, PlantManipulationTechnique.INSTANCE,
                FlameManipulationTechnique.INSTANCE, CursedSpiritTechnique.INSTANCE
        };
        tool.addModifier(techniques[random.nextInt(techniques.length)].getModifierId(), 1);
        tool.getPersistentData().addSlots(techniqueSlot, -1);
        ModifierId[] domains = {
                Modifiers.ZUOSHA_BOTU.getId(), Modifiers.WULIANG_KONGCHU.getId(), Modifiers.FUMO_YUCHUZI.getId()
        };
        tool.addModifier(domains[random.nextInt(domains.length)], 1);
        tool.getPersistentData().addSlots(domainSlot, -1);

        // ⭐ 30% 概率额外术式槽（总术式槽最多 3 个，含已消耗的 1 个）
        if (random.nextFloat() < 0.3) {
            int free = tool.getPersistentData().getSlots(techniqueSlot); // 已扣 1，可能为 0
            int extra = Math.min(1 + random.nextInt(2), 2 - free); // 上限：free+extra ≤ 2（总 3）
            if (extra > 0) {
                tool.getPersistentData().addSlots(techniqueSlot, extra);
            }
        }

        // ⭐ 调试：记录产出核心的材质与全部修饰符（含材料特性），便于验证特性是否生效
        ItemStack result = tool.createStack();
        TinkersNewlife.LOGGER.info("[TinkersNewlife] 仪式产出咒力核心（材质 {}）修饰符: {}",
                material != null ? material.getIdentifier() : "unknown",
                tool.getModifierList().stream()
                        .map(e -> e.getId() + "x" + e.getLevel())
                        .toList());
        return result;
    }

    // ============================================================
    //  数据
    // ============================================================

    private static class RitualStart {
        final BlockPos gaugePos;
        final Fluid fluid;
        final int color;
        final IMaterial material;
        final List<BlockPos> lanterns;
        final List<BlockState> materialStates;

        RitualStart(BlockPos gaugePos, Fluid fluid, int color, IMaterial material,
                    List<BlockPos> lanterns, List<BlockState> materialStates) {
            this.gaugePos = gaugePos;
            this.fluid = fluid;
            this.color = color;
            this.material = material;
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
        final IMaterial material;
        final List<BlockPos> lanterns;
        final List<BlockState> materialStates;
        int ticksLeft;

        RitualData(UUID playerId, ResourceKey<Level> dimension, BlockPos orePos,
                   BlockPos gaugePos, Fluid fluid, int color, IMaterial material,
                   List<BlockPos> lanterns, List<BlockState> materialStates) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.gaugePos = gaugePos;
            this.fluid = fluid;
            this.color = color;
            this.material = material;
            this.lanterns = lanterns;
            this.materialStates = materialStates;
            this.ticksLeft = RITUAL_TICKS;
        }
    }
}
