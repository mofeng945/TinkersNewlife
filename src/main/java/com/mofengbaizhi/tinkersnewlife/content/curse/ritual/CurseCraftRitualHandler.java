package com.mofengbaizhi.tinkersnewlife.content.curse.ritual;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.recipe.CurseCraftRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 咒力合成仪式：结构检测、灯笼上的材料放置/取回、仪式执行与特效。
 *
 * <h2>玩法流程</h2>
 * <ol>
 *   <li>按 {@link CurseCraftStructure} 搭好结构 → 结构成型时播一次雷声；</li>
 *   <li>拿着材料右键石砖墙上方的灯笼 → 材料悬浮在灯笼上（掉落物外观、无碰撞、上下浮动转动）；
 *       再右键同一盏灯笼可取回；</li>
 *   <li>材料齐了之后，把配方指定的古神物品交给格赫罗斯矿石正上方的灯笼 → 仪式开始；</li>
 *   <li>期间所有物品聚拢到矿石上方 3 格，不断冒对应物品颜色的破坏粒子，
 *       并与玩家之间拉出一道深紫色粒子束、每 tick 从玩家身上吸收 10 点咒力；</li>
 *   <li>咒力吸满 → 爆炸特效 + 产物悬浮在矿石上方的灯笼处，右键取下。</li>
 * </ol>
 * 中途玩家离开/咒力不足/再次触碰灯笼 → 仪式中断，材料各自飘回原灯笼（不吞材料）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseCraftRitualHandler {

    /** 悬浮物的持久数据：所属灯笼坐标（long）与角色（material/core/product） */
    public static final String KEY_LANTERN = "tinkersnewlife.curse_craft_lantern";
    public static final String KEY_ROLE = "tinkersnewlife.curse_craft_role";

    public static final String ROLE_MATERIAL = "material";
    public static final String ROLE_CORE = "core";
    public static final String ROLE_PRODUCT = "product";

    /** 玩家离得超过这个距离就中断（方块） */
    private static final double MAX_DISTANCE = 16.0;
    /** 悬浮物坐在灯笼上方多高（灯笼碰撞箱顶面约 y+0.5，必须完全避开） */
    private static final double DISPLAY_DY = 1.15;

    /** 已提示过"结构成型"的结构（维度@坐标），避免反复播雷声 */
    private static final Set<String> ANNOUNCED = ConcurrentHashMap.newKeySet();
    /** 进行中的仪式：维度@矿石坐标 → 仪式 */
    private static final Map<String, Ritual> ACTIVE = new ConcurrentHashMap<>();
    /** 活着的悬浮物：维度@灯笼座标@角色 → 记录（每 10 tick 拉回灯笼上方，绝不漂走） */
    private static final Map<String, Display> DISPLAYS = new ConcurrentHashMap<>();

    private CurseCraftRitualHandler() {
    }

    private static String key(ServerLevel level, BlockPos ore) {
        return level.dimension().location() + "@" + ore.asLong();
    }

    /** 一盏灯笼上的一个悬浮物记录 */
    private record Display(net.minecraft.resources.ResourceKey<Level> dim, BlockPos lantern, UUID id, String role) {
    }

    /** 灯笼附近是否有格赫罗斯矿石（区分"搭歪了"与"只是在乱点灯笼"） */
    private static boolean hasOreNearby(ServerLevel level, BlockPos lantern) {
        for (BlockPos pos : BlockPos.betweenClosed(lantern.offset(-4, -4, -4), lantern.offset(4, 4, 4))) {
            if (level.getBlockState(pos).is(com.mofengbaizhi.tinkersnewlife.content.ModBlocks.GHELOTH_ORE.get())) {
                return true;
            }
        }
        return false;
    }

    /** 结构提示冷却（每 2 秒最多一次） */
    private static boolean hintReady(ServerPlayer player, ServerLevel level) {
        String keyTick = "tinkersnewlife.curse_craft_hint_tick";
        long now = level.getGameTime();
        if (player.getPersistentData().getLong(keyTick) + 40 > now) return false;
        player.getPersistentData().putLong(keyTick, now);
        return true;
    }
    private static net.minecraft.network.chat.MutableComponent msg(String path, Object... args) {
        return Component.translatable("message.tinkersnewlife.curse_craft." + path, args);
    }

    // ============================================================
    //  右键灯笼
    // ============================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!CurseCraftStructure.isLantern(level.getBlockState(pos))) return;

        // 灯笼本身直接退出（避免误拦无关灯笼的交互），但"结构里的灯笼"一律拦下并给反馈
        CurseCraftStructure.Anchor anchor = CurseCraftStructure.findAnchor(level, pos);
        if (anchor == null) {
            // 不属于任何仪式结构：完全不动它（焦黑灯笼本身还是 TCon 的储罐）
            // 只有"附近确实有格赫罗斯矿石但结构不对"时才弱提示一次，避免玩家以为"没反应"
            if (!level.isClientSide && level instanceof ServerLevel sl
                    && event.getEntity() instanceof ServerPlayer sp
                    && hasOreNearby(sl, pos) && hintReady(sp, sl)) {
                sp.displayClientMessage(msg("need_ore").withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos ore = anchor.ore();
        boolean formed = CurseCraftStructure.isFormed(serverLevel, ore, anchor.dy());
        String k = key(serverLevel, ore);

        // 结构成型的一次性提示（雷声）
        if (formed && ANNOUNCED.add(k)) {
            serverLevel.playSound(null, ore, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.2F, 1.0F);
            player.displayClientMessage(msg("formed").withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
        if (!formed) {
            player.displayClientMessage(msg("not_formed").withStyle(ChatFormatting.RED), true);
            // ⭐ 报出第一处不符的方块，省得玩家一格一格对（聊天栏，方便照着改）
            String problem = CurseCraftStructure.firstProblem(serverLevel, ore, anchor.dy());
            if (problem != null) {
                player.displayClientMessage(Component.literal("· " + problem).withStyle(ChatFormatting.GRAY), false);
            }
            return;
        }

        // 仪式进行中：任意灯笼交互视为打断
        if (ACTIVE.containsKey(k)) {
            abort(serverLevel, k, "interrupted");
            return;
        }

        boolean core = pos.equals(anchor.coreLantern());
        if (core) handleCoreLantern(player, serverLevel, ore, pos);
        else handleMaterialLantern(player, serverLevel, pos);
    }

    /** 材料灯笼：放上 / 取回 */
    private static void handleMaterialLantern(ServerPlayer player, ServerLevel level, BlockPos lantern) {
        ItemEntity existing = findDisplay(level, lantern, ROLE_MATERIAL);
        if (existing != null) {
            giveOrDrop(player, existing.getItem().copy());
            existing.discard();
            player.displayClientMessage(msg("material_taken").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.displayClientMessage(msg("hand_empty").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // 别把核心往材料位上放（核心要去矿石正上方那盏灯）
        if (isCoreForAnyRecipe(level, held)) {
            player.displayClientMessage(msg("core_goes_center").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        ItemStack one = held.copyWithCount(1);
        held.shrink(1);
        spawnDisplay(level, lantern, one, ROLE_MATERIAL);
        player.displayClientMessage(msg("material_placed", one.getHoverName()).withStyle(ChatFormatting.GRAY), true);
    }

    /** 核心灯笼：取下产物 / 取回核心 / 投入核心开仪式 */
    private static void handleCoreLantern(ServerPlayer player, ServerLevel level, BlockPos ore, BlockPos lantern) {
        ItemEntity product = findDisplay(level, lantern, ROLE_PRODUCT);
        if (product != null) {
            giveOrDrop(player, product.getItem().copy());
            product.discard();
            player.displayClientMessage(msg("product_taken").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        ItemEntity core = findDisplay(level, lantern, ROLE_CORE);
        if (core != null) {
            giveOrDrop(player, core.getItem().copy());
            core.discard();
            player.displayClientMessage(msg("core_taken").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            player.displayClientMessage(msg("need_core").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        List<ItemStack> materials = collectMaterials(level, ore);
        CurseCraftRecipe recipe = matchRecipe(level, held, materials);
        if (recipe == null) {
            player.displayClientMessage(msg("no_recipe",
                    materials.size(), maxMaterialSlots(level, ore)).withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack one = held.copyWithCount(1);
        held.shrink(1);
        spawnDisplay(level, lantern, one, ROLE_CORE);
        startRitual(level, ore, lantern, player, recipe);
    }

    // ============================================================
    //  配方匹配
    // ============================================================

    private static List<CurseCraftRecipe> recipes(ServerLevel level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeSerializers.CURSE_CRAFT_TYPE.get());
    }

    private static boolean isCoreForAnyRecipe(ServerLevel level, ItemStack stack) {
        for (CurseCraftRecipe recipe : recipes(level)) {
            if (recipe.core().test(stack)) return true;
        }
        return false;
    }

    /**
     * 找匹配配方：核心匹配 + 材料"齐且不多"（材料不必放满 8 个位置）。
     */
    private static CurseCraftRecipe matchRecipe(ServerLevel level, ItemStack core, List<ItemStack> materials) {
        for (CurseCraftRecipe recipe : recipes(level)) {
            if (!recipe.core().test(core)) continue;
            if (recipe.materials().size() != materials.size()) continue;
            if (!consumesAll(recipe.materials(), materials)) continue;
            return recipe;
        }
        return null;
    }

    /** 配方材料能否与被放置的材料一一对应（多的不算） */
    private static boolean consumesAll(List<Ingredient> needed, List<ItemStack> placed) {
        boolean[] used = new boolean[placed.size()];
        for (Ingredient ingredient : needed) {
            boolean found = false;
            for (int i = 0; i < placed.size(); i++) {
                if (used[i] || !ingredient.test(placed.get(i))) continue;
                used[i] = true;
                found = true;
                break;
            }
            if (!found) return false;
        }
        return true;
    }

    private static int maxMaterialSlots(ServerLevel level, BlockPos ore) {
        return CurseCraftStructure.lanternOffsets().size() - 1;   // 9 盏灯笼去掉核心位 = 8 个材料位
    }

    // ============================================================
    //  悬浮物（掉落物外观：无重力、不可拾取、不会消失）
    // ============================================================

    public static ItemEntity spawnDisplay(ServerLevel level, BlockPos lantern, ItemStack stack, String role) {
        // ⚠ 位置：灯笼实体形状的顶面在 y+0.5 左右，若把掉落物生成在灯笼"体内"（y+0.45），
        //   原版碰撞会每 tick 把它往外（多半向上）挤一点，表现为"材料慢慢飘到高空、还取不回来"。
        //   所以生成在灯笼上方 DISPLAY_DY 格，并关掉物理（noPhysics）——永不掉落、永不被挤出。
        BlockPos anchor = lantern.immutable();
        ItemEntity entity = new ItemEntity(level,
                anchor.getX() + 0.5, anchor.getY() + DISPLAY_DY, anchor.getZ() + 0.5, stack.copyWithCount(1));
        entity.setNoGravity(true);
        entity.noPhysics = true;
        entity.setNeverPickUp();
        entity.setUnlimitedLifetime();
        entity.setInvulnerable(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.getPersistentData().putString(KEY_ROLE, role);
        entity.getPersistentData().putLong(KEY_LANTERN, anchor.asLong());
        level.addFreshEntity(entity);
        DISPLAYS.put(displayKey(level, anchor, role), new Display(level.dimension(), anchor, entity.getUUID(), role));
        return entity;
    }

    /** 悬浮物登记键 */
    private static String displayKey(ServerLevel level, BlockPos lantern, String role) {
        return level.dimension().location() + "@" + lantern.asLong() + "@" + role;
    }

    /**
     * 每 10 tick 把登记在册的悬浮物拉回自己那盏灯笼上方。
     * <p>悬浮物是"全息影像"：无重力 + 无碰撞，好处是绝不会被方块挤出去，
     * 代价是任何外力（爆炸、活塞、水、其它模组的推挤）都会把它推走后**再也回不来**。
     * 所以这里定期归位，保证"放上去就一定取得下来"。
     */
    private static void pinDisplays(MinecraftServer server) {
        if (DISPLAYS.isEmpty() || !ACTIVE.isEmpty()) return;   // 仪式进行中由 animate() 接管
        for (java.util.Iterator<Map.Entry<String, Display>> it = DISPLAYS.entrySet().iterator(); it.hasNext(); ) {
            Display display = it.next().getValue();
            ServerLevel level = server.getLevel(display.dim());
            if (level == null) continue;                        // 维度没加载：先留着（区块重新加载后会再次接管）
            net.minecraft.world.entity.Entity found = level.getEntity(display.id());
            if (found == null) continue;                        // 区块未加载或世界刚载入：保留登记
            if (!(found instanceof ItemEntity item) || !item.isAlive()) {
                it.remove();
                continue;
            }
            double x = display.lantern().getX() + 0.5;
            double y = display.lantern().getY() + DISPLAY_DY;
            double z = display.lantern().getZ() + 0.5;
            if (item.position().distanceToSqr(x, y, z) > 1.0E-6) {
                item.setPos(x, y, z);
                item.setDeltaMovement(Vec3.ZERO);
                item.hurtMarked = true;
            }
        }
    }

    /**
     * 某盏灯笼上的悬浮物（按角色）。
     * <p>先按"灯笼坐标键"在较大范围内精确匹配；万一实体被外力挪走，
     * 退化为"取附近最近的同角色悬浮物"，保证一定取得回来（不再出现"放上去拿不下来"）。
     */
    public static ItemEntity findDisplay(ServerLevel level, BlockPos lantern, String role) {
        AABB box = new AABB(lantern).inflate(8.0);
        ItemEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!role.equals(entity.getPersistentData().getString(KEY_ROLE))) continue;
            if (entity.getPersistentData().getLong(KEY_LANTERN) == lantern.asLong()) return entity;
            if (!entity.getPersistentData().contains(KEY_LANTERN)) continue;
            double d = entity.distanceToSqr(lantern.getX() + 0.5, lantern.getY() + DISPLAY_DY, lantern.getZ() + 0.5);
            if (d < best) {
                best = d;
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * 结构内所有材料悬浮物（含正在聚合中的；按角色扫描，不依赖灯笼高度）。
     * <p>按"堆叠数量"展开：万一两个同款材料被原版合并成一个 count&gt;1 的掉落物，
     * 也仍然会被当成 2 份材料参与配方匹配（不会变成"材料数量对不上 → 说没有材料"）。
     */
    private static List<ItemStack> collectMaterials(ServerLevel level, BlockPos ore) {
        List<ItemStack> list = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, CurseCraftStructure.bounds(ore).inflate(24.0),
                e -> ROLE_MATERIAL.equals(e.getPersistentData().getString(KEY_ROLE)))) {
            ItemStack stack = entity.getItem();
            int count = Math.max(1, Math.min(stack.getCount(), 64));
            for (int i = 0; i < count; i++) list.add(stack.copyWithCount(1));
        }
        return list;
    }
    private static List<ItemEntity> allDisplays(ServerLevel level, BlockPos ore) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(ore).inflate(20.0),
                e -> !e.getPersistentData().getString(KEY_ROLE).isEmpty()
                        && e.getPersistentData().contains(KEY_LANTERN));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    // ============================================================
    //  仪式执行
    // ============================================================

    private static final class Ritual {
        final BlockPos ore;
        final BlockPos coreLantern;
        final UUID player;
        final CurseCraftRecipe recipe;
        final int totalTicks;
        int elapsed = 0;

        Ritual(BlockPos ore, BlockPos coreLantern, UUID player, CurseCraftRecipe recipe) {
            this.ore = ore;
            this.coreLantern = coreLantern;
            this.player = player;
            this.recipe = recipe;
            this.totalTicks = Math.max(20, recipe.durationTicks());
        }
    }

    private static void startRitual(ServerLevel level, BlockPos ore, BlockPos coreLantern, ServerPlayer player, CurseCraftRecipe recipe) {
        ACTIVE.put(key(level, ore), new Ritual(ore, coreLantern, player.getUUID(), recipe));
        level.playSound(null, ore, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.7F);
        player.displayClientMessage(msg("started",
                CursePowerHelper.formatAmount(recipe.curse()),
                CursePowerHelper.formatAmount(CurseCraftRecipe.CURSE_PER_TICK)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        // 悬浮物归位（每 10 tick 一次，代价可忽略：只遍历"登记在册的几件物品"）
        if (server.getTickCount() % 10 == 0) pinDisplays(server);
        if (ACTIVE.isEmpty()) return;
        for (Map.Entry<String, Ritual> entry : new ArrayList<>(ACTIVE.entrySet())) {
            String k = entry.getKey();
            Ritual ritual = entry.getValue();
            ServerLevel level = levelOf(server, k);
            if (level == null) {
                ACTIVE.remove(k);
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(ritual.player);
            if (player == null || player.level() != level) {
                abort(level, k, "player_left");
                continue;
            }
            if (player.distanceToSqr(ritual.ore.getX() + 0.5, ritual.ore.getY() + 0.5, ritual.ore.getZ() + 0.5)
                    > MAX_DISTANCE * MAX_DISTANCE) {
                abort(level, k, "too_far");
                continue;
            }
            // 吸咒：核心池 → 封呪瓶 → 呪蔵（不动用灵魂能量，仪式只吃咒力）
            double remaining = CursePowerHelper.spendCurseCascade(player, CurseCraftRecipe.CURSE_PER_TICK);
            if (remaining > 0) {
                abort(level, k, "not_enough_curse");
                continue;
            }
            // 每 20 tick 复检结构：被拆掉就中断（材料各自飘回灯笼）
            if (ritual.elapsed % 20 == 0 && !CurseCraftStructure.isFormed(level, ritual.ore)) {
                abort(level, k, "structure_broken");
                continue;
            }

            ritual.elapsed++;
            animate(level, ritual, player);
            if (ritual.elapsed >= ritual.totalTicks) complete(level, k, ritual, player);
        }
    }

    private static ServerLevel levelOf(MinecraftServer server, String k) {
        String dim = k.substring(0, k.indexOf('@'));
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dim)) return level;
        }
        return null;
    }

    /** 聚拢 + 粒子 + 粒子束 */
    private static void animate(ServerLevel level, Ritual ritual, ServerPlayer player) {
        Vec3 center = CurseCraftStructure.convergePoint(ritual.ore);
        List<ItemEntity> displays = allDisplays(level, ritual.ore);
        // 按"相对矿石的角度"排序后再分配环形位置：每个物品朝自己那一侧聚拢，
        // 轨迹基本是径向的、不会互相穿插（半径 1.4 也保证同款材料不会贴近到自动合并）
        displays.sort(java.util.Comparator.comparingDouble(e -> {
            double a = Math.atan2(e.getZ() - center.z, e.getX() - center.x);
            return a < 0 ? a + Math.PI * 2 : a;
        }));
        int index = 0;
        for (ItemEntity entity : displays) {
            double angle = index * (Math.PI * 2 / Math.max(1, displays.size()));
            Vec3 target = center.add(Math.cos(angle) * 1.4, 0.0, Math.sin(angle) * 1.4);
            Vec3 now = entity.position();
            entity.setPos(now.x + (target.x - now.x) * 0.35,
                    now.y + (target.y - now.y) * 0.35,
                    now.z + (target.z - now.z) * 0.35);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;

            if (ritual.elapsed % 2 == 0) {
                ItemStack stack = entity.getItem();
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack),
                        target.x, target.y, target.z, 2, 0.15, 0.15, 0.15, 0.02);
            }
            index++;
        }

        // 深紫色粒子束：玩家胸口 → 聚合物
        Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
        double distance = from.distanceTo(center);
        int steps = Math.max(4, (int) (distance / 0.3));
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.42F, 0.10F, 0.75F), 1.2F);
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(center, i / (double) steps);
            // 稍微抖动，像流动的咒力
            double jitter = 0.06;
            level.sendParticles(dust,
                    p.x + (level.random.nextDouble() - 0.5) * jitter,
                    p.y + (level.random.nextDouble() - 0.5) * jitter,
                    p.z + (level.random.nextDouble() - 0.5) * jitter,
                    1, 0, 0, 0, 0);
        }
        if (ritual.elapsed % 10 == 0) {
            level.playSound(null, ritual.ore, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.7F, 0.6F);
        }
    }

    /** 完成：爆炸特效 + 产物悬浮在矿石上方灯笼 */
    private static void complete(ServerLevel level, String k, Ritual ritual, ServerPlayer player) {
        ACTIVE.remove(k);
        Vec3 center = CurseCraftStructure.convergePoint(ritual.ore);
        level.explode(null, center.x, center.y, center.z, 2.0F, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z, 40, 0.6, 0.6, 0.6, 0.05);
        level.playSound(null, ritual.ore, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.0F, 1.4F);
        level.playSound(null, ritual.ore, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.BLOCKS, 1.0F, 0.8F);

        // 清掉材料与核心悬浮物
        for (ItemEntity entity : allDisplays(level, ritual.ore)) entity.discard();

        ItemStack result = ritual.recipe.getResultItem(level.registryAccess()).copy();
        spawnDisplay(level, ritual.coreLantern, result, ROLE_PRODUCT);
        player.displayClientMessage(msg("finished", result.getHoverName())
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    /** 中断：物品各自飘回原灯笼 */
    private static void abort(ServerLevel level, String k, String reason) {
        Ritual ritual = ACTIVE.remove(k);
        if (ritual == null) return;
        for (ItemEntity entity : allDisplays(level, ritual.ore)) {
            long lantern = entity.getPersistentData().getLong(KEY_LANTERN);
            BlockPos pos = BlockPos.of(lantern);
            entity.setPos(pos.getX() + 0.5, pos.getY() + DISPLAY_DY, pos.getZ() + 0.5);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;
        }
        level.playSound(null, ritual.ore, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.7F);
        ServerPlayer player = level.getServer() == null ? null
                : level.getServer().getPlayerList().getPlayer(ritual.player);
        if (player != null) {
            player.displayClientMessage(msg("aborted." + reason).withStyle(ChatFormatting.RED), true);
        }
    }

    /** 供外部（如方块被破坏）主动中断：结构范围内所有仪式 */
    public static void abortAllAt(ServerLevel level, BlockPos ore) {
        String k = key(level, ore);
        if (ACTIVE.containsKey(k)) abort(level, k, "interrupted");
    }

    /** 玩家下线/换维度时的清理由 tick 检测负责；这里只暴露查询给 JEI/调试用 */
    public static boolean isActive(ServerLevel level, BlockPos ore) {
        return ACTIVE.containsKey(key(level, ore));
    }
}
