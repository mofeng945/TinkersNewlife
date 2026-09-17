package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>击杀归属记忆</b>：把"这个目标最后一击是谁打的"记下来，供<b>死亡事件回溯归属</b>用。
 *
 * <h2>为什么需要它（用户实测：杀死诡厄巫法使徒没有记录）</h2>
 * 无为转变的形态记录（{@code WuWeiHandler#onKill}）只认
 * {@code LivingDeathEvent.getSource().getEntity() instanceof ServerPlayer} ✗ ——
 * 但本模组**大量伤害源是 entity-less 的**（见 {@link CurseDeath} 的同一段说明）：
 * <ul>
 *   <li>领域直伤、咒言、反转术式兜底…用 {@code damageSources().magic()}（无来源实体 ✗）；</li>
 *   <li><b>穿透/真伤</b>：{@code util/TruePierce} 在"带攻击者的源打不动"时会**换无主源**再打一遍，
 *       这一段的 {@code getEntity()} 就是 null ✗（天逆鉾打受限 Boss 正是这条链 ✗）；</li>
 *   <li>狱门疆、部分处决兜底用 {@code genericKill()}（无来源实体 ✗）。</li>
 * </ul>
 * 于是"被无主伤害收尾"的 Boss（诡厄巫法的使徒/亚波伦这类需要穿透才打得动的）→
 * 死亡事件里根本没有玩家 ⇒ 形态记录永远写不进去 ✗。
 *
 * <h2>做法</h2>
 * 在伤害**发生时**记一笔"谁是这一下的主人"（玩家本人 / 投射物主人 / 玩家拥有的生物 ✓，
 * 见 {@link #playerBehind}），死亡时只要直接来源拿不到玩家，就回查这一笔 ✓
 * （{@link #find}，带 {@value #TTL_TICKS} tick 有效期）。命中链路不变、数值不变，
 * 只补"归属"这一件事 ✓。
 *
 * <p>有效期取 20 秒：Boss 战的收尾一击通常紧跟在玩家自己的一次命中之后 ✓；
 * 太短（如 1 秒）会让"隔几秒才被领域磨死"漏掉 ✗，太长则会让玩家只是路过打了一下的野怪
 * 也被算进形态列表 ✗。
 *
 * <p>与"共享术式"无关：归属只记到**真正动手的那位玩家**头上 ✓，
 * 同心戒共享的一方通过 {@code WuWeiHandler.getRecordedForms} 合并同伴记录来看到 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KillAttribution {

    private KillAttribution() {}

    /** 记忆有效期（tick）= 20 秒 */
    private static final int TTL_TICKS = 400;
    /** 记忆表上限，超了就清一遍过期的（防长期挂机累积） */
    private static final int MAX_ENTRIES = 512;

    private record Entry(UUID attacker, long tick) {}

    /** key = 受击者 UUID，value = 最后一击的主人 + 发生时刻（服务器 gameTime） */
    private static final Map<UUID, Entry> LAST_HIT = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        remember(event.getEntity(), attacker);
    }

    /**
     * 记下"这一下是 {@code attacker}（或其主人）打的"。
     * <p>没有玩家归属（野怪互殴、纯环境伤害）→ 不记 ✓（否则会把无主伤害错记成某个玩家）。
     */
    public static void remember(@Nullable Entity victim, @Nullable Entity attacker) {
        if (victim == null || attacker == null) return;
        if (victim.level().isClientSide) return;
        ServerPlayer owner = playerBehind(attacker);
        if (owner == null) return;
        long now = victim.level().getGameTime();
        LAST_HIT.put(victim.getUUID(), new Entry(owner.getUUID(), now));
        if (LAST_HIT.size() > MAX_ENTRIES) prune(now);
    }

    /** 回查"最后一击的主人"；没有 / 已过期 / 人已下线 → null ✓ */
    @Nullable
    public static ServerPlayer find(@Nullable LivingEntity victim) {
        if (victim == null || victim.level().isClientSide) return null;
        Entry entry = LAST_HIT.get(victim.getUUID());
        if (entry == null) return null;
        long now = victim.level().getGameTime();
        // now < tick：换维度/存档重载导致 gameTime 回退，按过期处理，免得记错人 ✗
        if (now - entry.tick() > TTL_TICKS || now < entry.tick()) {
            LAST_HIT.remove(victim.getUUID(), entry);
            return null;
        }
        MinecraftServer server = victim.level().getServer();
        if (server == null) return null;
        ServerPlayer player = server.getPlayerList().getPlayer(entry.attacker());
        if (player == null) LAST_HIT.remove(victim.getUUID(), entry);   // 人已下线，顺手清掉
        return player;
    }

    /**
     * 伤害源背后的玩家：本人 ✓ / 投射物主人 ✓ / 玩家拥有的生物（驯服宠仆、带主人的召唤物）✓。
     * <p>⚠ **故意不含**"本模组释放体"的归属查询（{@code CursedSpiritTechnique.ownerOfReleased}）：
     * 那个是 O(玩家数 × 记录数) 的查询，放在**每次受击**上太贵 ✗ ——
     * 它只在"死亡且前面几种都查不到"时才试一次（{@code WuWeiHandler#onKill}）✓。
     */
    @Nullable
    public static ServerPlayer playerBehind(@Nullable Entity attacker) {
        if (attacker instanceof ServerPlayer sp) return sp;
        if (attacker instanceof Projectile p && p.getOwner() instanceof ServerPlayer sp) return sp;
        if (attacker instanceof OwnableEntity o && o.getOwner() instanceof ServerPlayer sp) return sp;
        return null;
    }

    /** 忘掉某个目标的归属（"静默移除／主动收回"这类**不算击杀**的死亡必须调它 ✗，否则会被算成玩家的击杀）； */
    public static void forget(@Nullable Entity victim) {
        if (victim != null) LAST_HIT.remove(victim.getUUID());
    }

    private static void prune(long now) {
        LAST_HIT.entrySet().removeIf(e -> now - e.getValue().tick() > TTL_TICKS);
    }
}
