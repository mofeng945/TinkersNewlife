package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 让诡厄巫法的「灵魂修补器」（Soul Mender，方块 {@code goety:soul_mender}）能修补匠魂工具/盔甲。
 * <p>
 * goety 原生修补器只认原版 {@code isDamageableItem} 物品（读写 NBT {@code Damage} 字段），
 * 匠魂耐久走 ToolStack，放进去会被当「不可修物品」弹出或白扣灵魂。本 handler 软依赖接管：
 * <ul>
 *   <li><b>放入</b>：玩家右击 soul_mender 且主手持<b>可用且耐久未满的匠魂工具/盔甲</b>
 *       （与 goety 原生一致，放入不看笼子）→ cancel goety 原交互，消耗主手 1 个，
 *       在修补器中心生成一个<b>{@link Display.ItemDisplay} 展示实体</b>承载该物品。
 *       ItemDisplay 无碰撞/无重力/无 AI：不会被方块挤飞、不参与物理，随区块存档保存
 *       不丢物品，并自动把物品渲染同步给<b>所有玩家</b>（物品浮在修补器内）。</li>
 *   <li><b>修补</b>：服务端每 0.5 秒对登记中的修补器——若正下方有 CursedCage 且有灵魂则
 *       扣 {@code soulMenderCost} 灵魂（反射 {@code getSouls/decreaseSouls}），修 ToolStack 1 点耐久，
 *       并通过 {@code getSlot(0).set()} 写回展示实体（客户端同步可见）；
 *       笼子缺失/灵魂不足 → 悬浮等待不修。</li>
 *   <li><b>取回</b>：玩家右击 soul_mender（空手或同主手）时若该修补器有自己放下的展示物
 *       → 取出物品归还背包/主手，并移除展示实体。修完也可直接右键取回。</li>
 *   <li><b>修完/方块被拆</b>：取出物品转为普通可拾取掉落物（真实 ItemEntity，物理正常）。</li>
 * </ul>
 * 软依赖：goety 未装 / 目标方块不是 soul_mender 时全链路自然失效；无任何 goety import。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoulMenderTinkerHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoulMenderTinkerHandler.class);

    /** goety 方块注册 id */
    private static final ResourceLocation GOETY_SOUL_MENDER =
            ResourceLocation.fromNamespaceAndPath("goety", "soul_mender");
    private static final ResourceLocation GOETY_CURSED_CAGE =
            ResourceLocation.fromNamespaceAndPath("goety", "cursed_cage");

    /** 展示实体 persistentData 标记 */
    private static final String TAG_OWNER = "tnl_mender_owner";      // UUID 字符串：放下的玩家
    private static final String TAG_MENDER_POS = "tnl_mender_pos";   // 修补器 BlockPos（long[]）
    private static final String TAG_FIXING = "tnl_mender_fixing";    // true=正在修

    /** 每个维度：修补器位置 → 展示实体 UUID（避免每 tick 全维度扫描） */
    private static final Map<ResourceKey<Level>, Map<BlockPos, UUID>> ACTIVE =
            new HashMap<>();

    /** 展示尺寸缩放（用户要求 0.6） */
    private static final float DISPLAY_SCALE = 0.6f;

    /** Display#setTransformation 是私有方法（1.20.1 无公开入口），反射调用（SRG 名，dev/prod 均可用） */
    private static final Method SET_TRANSFORMATION =
            ObfuscationReflectionHelper.findMethod(Display.class, "m_269214_", Transformation.class);

    private static Map<BlockPos, UUID> activeFor(Level level) {
        return ACTIVE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
    }

    /** 设置展示实体变换：scale=0.6 + 绕 Y 轴旋转（模仿 goety 修补器内旋转效果） */
    private static void setDisplaySpin(Display display, float angleDeg) {
        try {
            Quaternionf rotation = Axis.YP.rotationDegrees(angleDeg);
            Transformation t = new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE), rotation);
            SET_TRANSFORMATION.invoke(display, t);
        } catch (Exception e) {
            LOGGER.error("[SoulMender] setTransformation failed", e);
        }
    }

    // =====================================================================
    //  右击 soul_mender：放入优先 → 取回兜底
    //  (① 主手持可修匠魂工具 → 放入/替换修复；② 否则若有自己的悬浮物 → 取回)
    //  与 goety 原生一致：放入不要求笼子，笼子只在每 tick 修复时要求。
    // =====================================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        if (!isGoetyBlock(event.getLevel(), pos, GOETY_SOUL_MENDER)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        ItemStack held = player.getMainHandItem();
        ToolStack tool = ToolHelper.getToolStack(held);
        boolean repairable = tool != null
                && !tool.isBroken()
                && tool.getDamage() > 0        // 耐久未满才需要修
                && held.getMaxDamage() > 0;    // 有耐久概念

        // ① 放入优先：主手是可修匠魂工具 → 放进修补器
        if (repairable) {
            // 该修补器已有自己放的悬浮物 → 先自动取回，再放新的（右键=把手上这把放上去）
            Display.ItemDisplay existing = findFloating(serverLevel, pos, player.getUUID());
            if (existing != null) {
                takeBack(serverLevel, pos, player, existing, false);
            }

            // cancel goety 原交互（它不认匠魂）
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);

            // 消耗主手 1 个（非创造），生成展示实体
            ItemStack drop = held.copy();
            drop.setCount(1);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            Display.ItemDisplay display = spawnDisplayItem(serverLevel, pos, drop, player);
            if (display != null) {
                activeFor(serverLevel).put(pos, display.getUUID());
            }
            serverLevel.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.8F);

            // 供能提示（不阻断放入；笼子补上即自动开始修）
            BlockPos cagePos = pos.below();
            if (!isGoetyBlock(serverLevel, cagePos, GOETY_CURSED_CAGE)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[灵魂修补器] 工具已放入修复中，但正下方没有灵魂笼——放上「诅咒之笼」（含灵魂）后开始修复。"));
            } else if (cageGetSouls(serverLevel, cagePos) <= 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[灵魂修补器] 工具已放入修复中，但下方灵魂笼内没有灵魂。"));
            }
            return;
        }

        // ② 取回兜底：主手不可放（空手/非匠魂/已满耐久）且有自己的悬浮物 → 归还
        Display.ItemDisplay found = findFloating(serverLevel, pos, player.getUUID());
        if (found != null) {
            takeBack(serverLevel, pos, player, found, true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // ③ 匠魂工具但放不进去：给出原因提示（不 cancel，避免干扰 goety 原交互）
        if (tool != null) {
            if (tool.isBroken()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[灵魂修补器] 工具已损坏，无法放入修复。"));
            } else if (tool.getDamage() <= 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[灵魂修补器] 工具耐久已满，无需修复。"));
            } else if (held.getMaxDamage() <= 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§7[灵魂修补器] 该物品没有耐久，无需修复。"));
            }
        }
    }

    /** 取出展示物里的物品：先归还背包/主手，成功则移除展示实体；背包满则原地掉落可拾取。playSound=true 时播拾取音 */
    private static void takeBack(ServerLevel level, BlockPos pos, Player player,
                                 Display.ItemDisplay display, boolean playSound) {
        ItemStack stack = display.getSlot(0).get();
        if (player.getInventory().add(stack)) {
            display.discard();
            activeFor(level).remove(pos);
        } else {
            // 背包放不下：物品转为普通掉落物（物理正常可拾取），展示实体移除
            display.discard();
            activeFor(level).remove(pos);
            ItemEntity drop = new ItemEntity(level,
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
            drop.setPickUpDelay(0);
            level.addFreshEntity(drop);
        }
        if (playSound) {
            level.playSound(null, pos.getX(), pos.getY(), pos.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    // =====================================================================
    //  服务端每 tick 管理：定时修补（每 0.5 秒）
    // =====================================================================

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;

        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            // 每 tick：展示物旋转动画（模仿 goety 修补器内旋转；goety 每 tick 转 3°）
            spinFloating(serverLevel);
            // 每 10 tick（0.5s）补登记 + 修复（对应 soulMenderSeconds≈0.5）
            if (tickCounter % 10 == 0) {
                rescanNearby(serverLevel);   // 重启/重进后补登记玩家附近的展示实体
                tickLevelFloating(serverLevel);
            }
        }
    }

    /** 每 tick：展示物旋转动画（模仿 goety 每 tick 转 3°）+ 修补中蓝焰粒子/火焰环境音（与 goety tick() 同频判定） */
    private static void spinFloating(ServerLevel level) {
        Map<BlockPos, UUID> map = activeFor(level);
        if (map.isEmpty()) return;
        float angle = (level.getGameTime() % 360L) * 3.0f;   // goety: 3°×(tick%360)
        long gameTime = level.getGameTime();
        boolean particleTick = gameTime % 20L == 0;          // goety makeWorkParticles: 每 20 tick
        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            BlockPos menderPos = entry.getKey();
            Display.ItemDisplay display = resolveDisplay(level, entry.getValue());
            if (display == null || !display.isAlive()) {
                it.remove();
                continue;
            }
            if (!display.getPersistentData().getBoolean(TAG_FIXING)) {
                it.remove();
                continue;
            }
            setDisplaySpin(display, angle);

            // 仅当修补器本体还在、正下方有笼子且有魂（正在修）才冒粒子/播火焰音
            if (!isGoetyBlock(level, menderPos, GOETY_SOUL_MENDER)) continue;
            BlockPos cagePos = menderPos.below();
            if (!isGoetyBlock(level, cagePos, GOETY_CURSED_CAGE)) continue;
            int cost = menderCost();
            if (cageGetSouls(level, cagePos) < cost) continue;

            if (particleTick) {
                workParticles(level, menderPos);   // 每 20 tick 冒 6 个 SOUL_FIRE_FLAME
            }
            // 火焰环境音：每 tick 1/24 概率（复刻 goety work() 的 FIRE_AMBIENT）
            if (level.random.nextInt(24) == 0) {
                float pitch = level.random.nextFloat() + (level.random.nextFloat() * 0.7F + 0.3F);
                level.playSound(null, menderPos, SoundEvents.FIRE_AMBIENT,
                        SoundSource.BLOCKS, 1.0F, pitch);
            }
        }
    }

    /** 玩家上线/靠近后补登记：扫描每个在线玩家附近的展示实体（重启后 ACTIVE 表是空的） */
    private static void rescanNearby(ServerLevel level) {
        Map<BlockPos, UUID> map = activeFor(level);
        for (net.minecraft.server.level.ServerPlayer sp : level.players()) {
            AABB box = sp.getBoundingBox().inflate(48.0);
            for (Display.ItemDisplay d : level.getEntitiesOfClass(Display.ItemDisplay.class, box)) {
                CompoundTag tag = d.getPersistentData();
                if (!tag.getBoolean(TAG_FIXING)) continue;
                long[] posArr = tag.getLongArray(TAG_MENDER_POS);
                if (posArr.length != 3) continue;
                BlockPos pos = new BlockPos((int) posArr[0], (int) posArr[1], (int) posArr[2]);
                if (!map.containsKey(pos)) {
                    map.put(pos, d.getUUID());
                }
            }
        }
    }

    private static void tickLevelFloating(ServerLevel level) {
        Map<BlockPos, UUID> map = activeFor(level);
        if (map.isEmpty()) return;

        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            BlockPos menderPos = entry.getKey();
            Display.ItemDisplay display = resolveDisplay(level, entry.getValue());
            if (display == null || !display.isAlive()) {
                it.remove();   // 展示物没了（被拆/卸载）
                continue;
            }
            CompoundTag tag = display.getPersistentData();
            if (!tag.getBoolean(TAG_FIXING)) {
                it.remove();
                continue;
            }

            ItemStack stack = display.getSlot(0).get();
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || tool.isBroken() || tool.getDamage() <= 0) {
                // 修完 → 完成粒子 + 取出物品转为普通掉落物（可拾取），移除展示实体
                it.remove();
                finishParticles(level, menderPos);
                display.discard();
                ItemEntity drop = new ItemEntity(level,
                        menderPos.getX() + 0.5, menderPos.getY() + 1.1, menderPos.getZ() + 0.5, stack);
                drop.setPickUpDelay(0);
                level.addFreshEntity(drop);
                continue;
            }

            // 正下方方块已不是修补器本体（被拆/替换）→ 取出物品掉落
            if (!isGoetyBlock(level, menderPos, GOETY_SOUL_MENDER)) {
                it.remove();
                display.discard();
                ItemEntity drop = new ItemEntity(level,
                        menderPos.getX() + 0.5, menderPos.getY() + 1.1, menderPos.getZ() + 0.5, stack);
                drop.setPickUpDelay(0);
                level.addFreshEntity(drop);
                continue;
            }

            // 笼子供能不足/笼子缺失 → 保留悬浮等待（等笼子补上或玩家取回），不扣魂不修
            BlockPos cagePos = menderPos.below();
            if (!isGoetyBlock(level, cagePos, GOETY_CURSED_CAGE)) continue;
            int cost = menderCost();
            if (cageGetSouls(level, cagePos) < cost) continue;

            // 扣灵魂 + 修 1 点，写回展示实体（客户端物品渲染同步更新）
            cageDecreaseSouls(level, cagePos, cost);
            tool.setDamage(tool.getDamage() - 1);
            tool.updateStack(stack);
            display.getSlot(0).set(stack);
            // 粒子/火焰音由每 tick 的 spinFloating 统一判定（与 goety tick() 同频），这里只修复
        }
    }

    // =====================================================================
    //  粒子效果（复刻 goety SoulMenderBlockEntity.makeWorkParticles / finishParticles）
    // =====================================================================

    /** 修补中粒子：方块内随机 6 个 SOUL_FIRE_FLAME（蓝焰），goety 在每 20 tick 冒一次 */
    private static void workParticles(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < 6; i++) {
            double x = pos.getX() + level.random.nextDouble();
            double y = pos.getY() + level.random.nextDouble();
            double z = pos.getZ() + level.random.nextDouble();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** 完成粒子：中心 1 个 LARGE_SMOKE + 方块内随机 6 处各 SOUL_FIRE_FLAME + SMOKE */
    private static void finishParticles(ServerLevel level, BlockPos pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 6; i++) {
            double x = pos.getX() + level.random.nextDouble();
            double y = pos.getY() + level.random.nextDouble();
            double z = pos.getZ() + level.random.nextDouble();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // =====================================================================
    //  展示实体 生成 / 查找
    // =====================================================================

    /** 在修补器中心生成 ItemDisplay 展示实体承载待修物品（无碰撞无重力，客户端自动渲染给所有玩家） */
    private static Display.ItemDisplay spawnDisplayItem(ServerLevel level, BlockPos menderPos, ItemStack stack, Player owner) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.getSlot(0).set(stack);
        // 位置 = 修补器方块内部中心偏上（视觉同 goety：物品浮在方块里）
        display.setPos(menderPos.getX() + 0.5, menderPos.getY() + 0.45, menderPos.getZ() + 0.5);
        CompoundTag tag = display.getPersistentData();
        tag.putBoolean(TAG_FIXING, true);
        tag.putString(TAG_OWNER, owner.getUUID().toString());
        tag.putLongArray(TAG_MENDER_POS, new long[]{menderPos.getX(), menderPos.getY(), menderPos.getZ()});
        level.addFreshEntity(display);
        return display;
    }

    /** 按登记表找该修补器的展示实体；登记缺失时（服务器重启/区块卸载后）回退扫描修补器附近实体恢复 */
    private static Display.ItemDisplay findFloating(ServerLevel level, BlockPos menderPos, UUID ownerUuid) {
        Map<BlockPos, UUID> map = activeFor(level);
        UUID uuid = map.get(menderPos);
        Display.ItemDisplay cached = uuid == null ? null : resolveDisplay(level, uuid);
        if (cached != null && cached.isAlive()) {
            return cached;
        }
        // 回退扫描：修补器周围 2 格内找带标记的展示实体（重启/卸载后登记丢失也能取回/继续修）
        AABB box = new AABB(menderPos).inflate(2.0);
        List<Display.ItemDisplay> found = level.getEntitiesOfClass(Display.ItemDisplay.class, box,
                d -> d.isAlive() && d.getPersistentData().getBoolean(TAG_FIXING)
                        && ownerUuid.toString().equals(d.getPersistentData().getString(TAG_OWNER)));
        if (found.isEmpty()) {
            return null;
        }
        Display.ItemDisplay display = found.get(0);
        map.put(menderPos, display.getUUID());
        return display;
    }

    private static Display.ItemDisplay resolveDisplay(ServerLevel level, UUID uuid) {
        if (uuid == null) return null;
        var e = level.getEntity(uuid);
        return e instanceof Display.ItemDisplay d ? d : null;
    }

    // =====================================================================
    //  goety 软依赖反射通道
    // =====================================================================

    private static boolean isGoetyBlock(Level level, BlockPos pos, ResourceLocation id) {
        if (level == null || pos == null) return false;
        var key = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return key != null && id.equals(key);
    }

    private static int menderCost() {
        return readConfigInt("com.Polarice3.Goety.config.MainConfig", "SoulMenderCost", 1);
    }

    /** 反射读 goety 静态 ConfigValue 当前值；读不到用默认 */
    private static int readConfigInt(String className, String fieldName, int fallback) {
        try {
            Class<?> clazz = Class.forName(className);
            var field = clazz.getField(fieldName);
            Object cfg = field.get(null);
            if (cfg instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> cv) {
                Object v = cv.get();
                if (v instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static int cageGetSouls(Level level, BlockPos pos) {
        try {
            var be = level.getBlockEntity(pos);
            if (be == null) return 0;
            java.lang.reflect.Method m = be.getClass().getMethod("getSouls");
            Object v = m.invoke(be);
            return v instanceof Number n ? n.intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void cageDecreaseSouls(Level level, BlockPos pos, int amount) {
        try {
            var be = level.getBlockEntity(pos);
            if (be == null) return;
            java.lang.reflect.Method m = be.getClass().getMethod("decreaseSouls", int.class);
            m.invoke(be, amount);
        } catch (Throwable ignored) {
        }
    }
}
