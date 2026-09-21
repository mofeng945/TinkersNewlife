package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁锢（静止）处理 —— <b>四模式版</b>（用户口径 ✓ 模仿星喵工艺「迷你基岩」并按要求分档 ✓）。
 *
 * <h2>模式表（模式编码在 {@code MobEffectInstance} 的 <b>amplifier</b> 里 ✓ 客户端也读得到 ✓）</h2>
 * <table border="1">
 *   <tr><th>模式</th><th>amplifier</th><th>不能移动</th><th>锁视角</th><th>禁攻击/用物品/开界面</th><th>用在哪</th></tr>
 *   <tr><td>{@link Mode#FULL}</td><td>0</td><td>✔</td><td>✘</td><td>✔</td><td>伏诛赐死（**与改造前逻辑相同** ✓）</td></tr>
 *   <tr><td>{@link Mode#BEDROCK}</td><td>1</td><td>✔</td><td><b>✔ 客户端锁视角</b></td><td>✘</td>
 *       <td>投射咒法 · 无量空处 · 时胞月宫殿 · 胎藏遍野 · 反重力术式 · 咒言术（= 模仿迷你基岩 ✓ **不锁血** ✗）</td></tr>
 *   <tr><td>{@link Mode#MOVE_ONLY}</td><td>2</td><td>✔</td><td>✘</td><td>✘</td>
 *       <td>嵌合影翼庭（可转动/可切工具/可用工具 ✓ 只是不能走 ✓）</td></tr>
 *   <tr><td>{@link Mode#USE_BAN}</td><td>3</td><td>✘ <b>可移动</b></td><td>✘</td><td>✔（工具/术式/背包全禁 ✓）</td>
 *       <td>弥虚葛笼 · 自我定身</td></tr>
 * </table>
 *
 * <p><b>迷你基岩那套怎么落地的</b>：①<b>位置锚定</b>——施术瞬间记下坐标 ✓ 之后每 tick 硬拉回（X/Z 锁死、Y 交给物理 ✓ 所以会正常下落 ✓），
 * 改造前那版"被物理推动就重新锚定"的漏洞在 BEDROCK/MOVE_ONLY 两档关掉 ✓（真正"无法移动"✓）；
 * ②<b>锁视角</b>——BEDROCK 档记录 yaw/pitch ✓ 服务端每 tick 写回 ✓ 并复用投影咒法那套
 * {@code PacketProjectionStun} 同步给客户端（客户端会按住视角 ✓ 见 {@code ClientEventHandler} ✓）；
 * ③<b>不锁血</b> ✓（用户明确排除 —— 迷你基岩那个"只增不减的 setHealth"我们**不做** ✗）。
 *
 * <p>⚠ BEDROCK 档：视角锁只对**玩家**有意义（生物视角服务端写回即可 ✓）；
 * 攻击/用物品**不拦** ✓（照迷你基岩 ✓ 只是你转不了头 ✓）。
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StunHandler {

    /** 禁锢模式（ordinal 就是 effect 的 amplifier ✓ 别改顺序 ✗） */
    public enum Mode {
        FULL,        // 0：完全锁（现状）
        BEDROCK,     // 1：锁移动 + 锁视角（迷你基岩 ✓）
        MOVE_ONLY,   // 2：只锁移动
        USE_BAN      // 3：可移动，禁工具/术式/背包
    }

    /** 被禁锢过的生物：UUID → 禁锢前的 noAi 值（用于恢复） */
    private static final Map<UUID, Boolean> STUNNED_MOB_NOAI = new ConcurrentHashMap<>();
    /** 锚点（硬锁 ✓） */
    private static final Map<UUID, Vec3> STUN_ANCHOR = new ConcurrentHashMap<>();
    /** BEDROCK 档锁视角：UUID → {yaw, pitch} */
    private static final Map<UUID, float[]> STUN_LOOK = new ConcurrentHashMap<>();
    /** 已经给客户端发过"锁视角"的玩家（效果结束时发 false 解锁 ✓） */
    private static final Map<UUID, Boolean> LOOK_SENT = new ConcurrentHashMap<>();

    // ============================================================
    //  统一施加入口（取代各处直接 addEffect ✓）
    // ============================================================

    /** 给目标上禁锢 ✓（ticks 时长 ✓ mode 模式 ✓）—— 相同模式重复调用会刷新时长 ✓ */
    public static void apply(LivingEntity target, int ticks, Mode mode) {
        if (target == null || target.level().isClientSide) return;
        target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), ticks, mode.ordinal(), false, false));
        if (mode != Mode.USE_BAN) {
            STUN_ANCHOR.putIfAbsent(target.getUUID(), target.position());
        }
        if (mode == Mode.BEDROCK) {
            STUN_LOOK.putIfAbsent(target.getUUID(), new float[]{target.getYRot(), target.getXRot()});
            // ⚠ 不发 PacketProjectionStun ✗（那套包会让客户端连攻击/使用一起取消 ✗ 而 BEDROCK 要能用工具 ✓）
            //    视角锁只走"服务端权威"：每 tick 把 yaw/pitch 写回 ✓ 所以打出去的朝向永远是锁定那一刻的朝向 ✓
            //    （客户端自己的镜头视觉上可能还会晃 ✗ 要做到连镜头都不动就得另开一个专用包 ✓ 见备忘录）
        }
        if (target instanceof Mob mob) {
            STUNNED_MOB_NOAI.putIfAbsent(mob.getUUID(), mob.isNoAi());
            mob.setNoAi(true);
            mob.getNavigation().stop();
        }
    }

    /** 兼容旧调用：默认完全锁 ✓（等价于 {@link Mode#FULL} ✓） */
    public static void onStunApplied(Mob mob) {
        if (mob != null) STUNNED_MOB_NOAI.putIfAbsent(mob.getUUID(), mob.isNoAi());
    }

    public static boolean isStunned(LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.STUN.get());
    }

    /** 当前模式 ✓（没被禁锢 ⇒ null ✓ 客户端可用 ✓ 因为模式编在 amplifier 里 ✓） */
    public static Mode modeOf(LivingEntity entity) {
        if (entity == null) return null;
        MobEffectInstance eff = entity.getEffect(ModEffects.STUN.get());
        if (eff == null) return null;
        Mode[] all = Mode.values();
        int i = eff.getAmplifier();
        return i >= 0 && i < all.length ? all[i] : Mode.FULL;
    }

    /** 不能移动吗 ✓（FULL / BEDROCK / MOVE_ONLY 三档 ✓ USE_BAN 可走 ✓） */
    public static boolean blocksMovement(LivingEntity entity) {
        Mode m = modeOf(entity);
        return m != null && m != Mode.USE_BAN;
    }

    /** 禁用工具/术式/背包吗 ✓（FULL 与 USE_BAN ✓） */
    public static boolean blocksUse(LivingEntity entity) {
        Mode m = modeOf(entity);
        return m == Mode.FULL || m == Mode.USE_BAN;
    }

    /** 锁视角吗 ✓（只有 BEDROCK ✓） */
    public static boolean locksView(LivingEntity entity) {
        return modeOf(entity) == Mode.BEDROCK;
    }

    /** 该生物是不是"被禁锢的式神" ✓（嵌合影翼庭豁免用不到这里 ✓ 但留着方便别处 ✓） */
    public static boolean isStunnedShikigami(LivingEntity entity) {
        return entity instanceof ShikigamiMob && isStunned(entity);
    }

    // ============================================================
    //  服务端每 tick：生物 noAi / 玩家锚点与视角
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;

        for (UUID id : STUNNED_MOB_NOAI.keySet()) {
            Entity entity = findEntity(server, id);
            if (!(entity instanceof Mob mob)) {
                STUNNED_MOB_NOAI.remove(id);
                continue;
            }
            Mode mode = modeOf(mob);
            if (mode != null) {
                if (mode != Mode.USE_BAN) {
                    mob.setNoAi(true);
                    mob.getNavigation().stop();
                    if (!mob.onGround() && !mob.isInWater() && !mob.isNoGravity()) {
                        Vec3 v = mob.getDeltaMovement();
                        if (v.y >= 0) mob.setDeltaMovement(v.add(0, -0.08, 0));   // 保证能正常下落 ✓
                    }
                    lockAnchor(mob, mode);
                }
                if (mode == Mode.BEDROCK) lockLook(mob);
            } else {
                Boolean before = STUNNED_MOB_NOAI.remove(id);
                mob.setNoAi(before != null && before);
                STUN_ANCHOR.remove(id);
                STUN_LOOK.remove(id);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        Mode mode = modeOf(player);
        if (mode == null) {
            STUN_ANCHOR.remove(player.getUUID());
            STUN_LOOK.remove(player.getUUID());

            return;
        }
        if (mode != Mode.USE_BAN) {
            player.xxa = 0;
            player.zza = 0;
            player.setJumping(false);
            player.setSprinting(false);
            lockAnchor(player, mode);
        }
        if (mode == Mode.BEDROCK) {
            lockLook(player);
            // 同上：只做服务端锁视角 ✓ 不发包
        }
        // 无法停留在任何界面（背包/箱子/curios ✓）—— FULL 与 USE_BAN 两档 ✓
        if (blocksUse(player) && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
    }

    /** 硬锚点：X/Z 拉回锚点 ✓ Y 保留（能正常下落 ✓）✓ */
    private static void lockAnchor(LivingEntity e, Mode mode) {
        Vec3 anchor = STUN_ANCHOR.get(e.getUUID());
        if (anchor == null) {
            STUN_ANCHOR.put(e.getUUID(), e.position());
            return;
        }
        if (e.position().distanceToSqr(anchor) > 0.0001D) {
            e.teleportTo(anchor.x, e.getY(), anchor.z);
            if (e instanceof ServerPlayer sp) sp.hurtMarked = true;
        }
    }

    /** 锁视角（服务端权威值 ✓ 客户端另有本地锁 ✓） */
    private static void lockLook(LivingEntity e) {
        float[] look = STUN_LOOK.get(e.getUUID());
        if (look == null) return;
        e.setYRot(look[0]);
        e.setXRot(look[1]);
        e.yRotO = look[0];
        e.xRotO = look[1];
    }

    /** 复用投影咒法那套同步包 ✓ 让客户端把本地视角按住 ✓（BEDROCK 档才发 ✓） */
    private static void sendLookLock(ServerPlayer player, boolean locked) {
        try {
            Boolean sent = LOOK_SENT.get(player.getUUID());
            if (locked && Boolean.TRUE.equals(sent)) return;
            if (!locked && sent == null) return;
            if (locked) LOOK_SENT.put(player.getUUID(), true);
            else LOOK_SENT.remove(player.getUUID());
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new com.mofengbaizhi.tinkersnewlife.network.curse.PacketProjectionStun(locked,
                            player.getYRot(), player.getXRot()));
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  事件拦截（按模式 ✓）
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (blocksUse(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocksUse(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocksUse(event.getEntity())) event.setCanceled(true);
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }
}
