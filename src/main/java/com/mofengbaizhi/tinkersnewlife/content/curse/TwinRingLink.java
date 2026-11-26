package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.item.RingOfOneMindItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 同心戒·共鸣（服务端）
 *
 * <p>两名玩家各戴上<b>同一对</b>同心戒（同一个成对印记 NBT）时，本类负责把他们连起来：
 * <ul>
 *   <li><b>咒力共享</b>：总量统计与扣费都把同伴的池子算进来
 *       （{@link CursePowerHelper#getTotalCurse} / {@link CursePowerHelper#spendCurseShared}）；</li>
 *   <li><b>术式共享</b>：同伴咒力核心上的术式一并列入"可用术式"
 *       （{@link TechniqueHandler#effectiveTechniques} 取并集）。</li>
 * </ul>
 *
 * <h2>三条硬约束（都是为避免"越界/递归/幽灵数据"）</h2>
 * <ol>
 *   <li><b>只认服务端 & 只认同服在线玩家</b>：同伴必须在 {@code PlayerList} 里；
 *       离线/换服 = 没有共鸣（不缓存、不持久化任何"链接"状态，戴上即生效、摘下即失效）✓；</li>
 *   <li><b>绝不回调"含共享"的口径</b>：这里只调 {@code localXxx}（核心池+瓶+蔵），
 *       否则 A 算 B、B 算 A 会无限递归 ✗；</li>
 *   <li><b>数量不设上限、但绝不叠加自己</b>：{@code findPartner} 跳过自己
 *       （同一对两枚戴在同一个人身上时，"同伴"为空 —— 装备时已被
 *       {@link RingOfOneMindItem#canEquip} 拦住，这里是双保险）。</li>
 * </ol>
 */
public final class TwinRingLink {

    private TwinRingLink() {}

    /**
     * 每 tick 的共鸣解析缓存。
     *
     * <p><b>为什么必须有</b>：同伴解析要扫 curios 槽位（自己 + 在场每名玩家），而
     * {@link CursePowerHelper#getTotalCurse}（→ {@code canPayCurse}）落在<b>领域/术式的每 tick 热路径</b>上
     * —— 不缓存的话，10 人服里每个领域每 tick 就要做几千次槽位读取 ✗。
     * 缓存以<b>服务端全局 tick</b> 为界（不是 {@code level.getGameTime()}：跨维度取值口径不一致），
     * 同一 tick 内只解析一次，跨 tick 必然重算 → 摘下戒指/下线/换对，最迟下一 tick 就生效 ✓（不会被缓存掩盖 ✗）。
     */
    private record Link(@Nullable ServerPlayer partner, int tick) {}

    private static final Map<java.util.UUID, Link> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 找出与 {@code player} 戴着同一对同心戒的<b>另一名在线玩家</b>；没有则 null。
     * <p>客户端/单人无服务器/未戴戒指/同伴离线 → 一律 null（调用方按"没共鸣"处理）。
     */
    @Nullable
    public static ServerPlayer findPartner(Player player) {
        if (!(player instanceof ServerPlayer self)) return null;
        MinecraftServer server = self.getServer();
        if (server == null) return null;
        int now = server.getTickCount();
        Link cached = CACHE.get(self.getUUID());
        if (cached != null && cached.tick() == now) {
            ServerPlayer p = cached.partner();
            return (p != null && p.isAlive()) ? p : null;
        }
        ServerPlayer found = resolvePartner(self, server);
        // 顺手清理过期条目（只在表变大时做，稳态下零开销）
        if (CACHE.size() > 64) CACHE.values().removeIf(l -> l.tick() != now);
        CACHE.put(self.getUUID(), new Link(found, now));
        return found;
    }

    /** 真正的解析（每 tick 至多一次/人）：自己戴的印记 → 在场玩家挨个比对同印记 */
    @Nullable
    private static ServerPlayer resolvePartner(ServerPlayer self, MinecraftServer server) {
        String pair = RingOfOneMindItem.wornPairId(self);
        if (pair == null) return null;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == self || !other.isAlive()) continue;
            if (pair.equals(RingOfOneMindItem.wornPairId(other))) return other;
        }
        return null;
    }

    /** 是否有共鸣（戴着同心戒且同伴在线戴着另一枚） */
    public static boolean isLinked(Player player) {
        return findPartner(player) != null;
    }

    // ============================================================
    //  ⭐ 互为同伴 = 同队豁免（双向免疫对方的术式与领域效果）
    // ============================================================

    /**
     * 两名实体是否戴着<b>同一对</b>同心戒（互为同伴）。
     *
     * <p>与 {@link #findPartner} 的区别：这里只看<b>这两个实体各自戴的印记是否相同</b> ——
     * 不需要它们在线、不需要走玩家列表，因此双端都可用、也不会被"第一匹配"影响
     * （三名玩家同戴一对的极端情况下 {@code findPartner} 只会给出第一个匹配 ✗）。
     *
     * <p>用途：<b>同队豁免</b> —— 戴同一对戒指的两人互相免疫对方的术式与领域效果
     * （术式选敌、领域效果圈选、穿透真伤、伤害事件四层都查它 ✓）。
     */
    public static boolean arePaired(@Nullable net.minecraft.world.entity.LivingEntity a,
                                    @Nullable net.minecraft.world.entity.LivingEntity b) {
        if (a == null || b == null || a == b) return false;
        if (!(a instanceof Player) || !(b instanceof Player)) return false;
        String pa = RingOfOneMindItem.wornPairId(a);
        if (pa == null) return false;
        return pa.equals(RingOfOneMindItem.wornPairId(b));
    }

    // ============================================================
    //  咒力共享
    // ============================================================

    /** 同伴的"本地"咒力总量（核心池 + 佩戴的封呪瓶 + 自己的呪蔵）；无共鸣 = 0 */
    public static double partnerLocalCurse(Player player) {
        ServerPlayer partner = findPartner(player);
        return partner == null ? 0 : CursePowerHelper.localTotalCurse(partner);
    }

    /** 同伴的"本地"咒力上限；无共鸣 = 0 */
    public static double partnerLocalMaxCurse(Player player) {
        ServerPlayer partner = findPartner(player);
        return partner == null ? 0 : CursePowerHelper.localTotalMaxCurse(partner);
    }

    // ============================================================
    //  术式共享
    // ============================================================

    /** 同伴咒力核心上的术式 id（无共鸣/同伴无核心 = 空表；永不返回 null） */
    public static List<ModifierId> partnerTechniques(Player player) {
        ServerPlayer partner = findPartner(player);
        if (partner == null) return Collections.emptyList();
        List<ModifierId> list = TechniqueHandler.techniquesOnCore(partner);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 共鸣指纹：同伴身份 + 同伴核心物品 + 同伴核心上的术式列表。
     * <p>供 {@code TechniqueHandler} 的"核心变更检测"使用 —— 同伴换了核心/换了术式，
     * 自己这边的可用术式集也变了，需要重选并收掉已经不可用的持续术式 ✓。
     * 无共鸣返回空串。
     */
    public static String partnerFingerprint(Player player) {
        ServerPlayer partner = findPartner(player);
        if (partner == null) return "";
        ItemStack core = CursePowerHelper.findEquippedCurseCore(partner);
        if (core.isEmpty()) return "partner:" + partner.getUUID() + "|-";
        StringBuilder sb = new StringBuilder("partner:").append(partner.getUUID()).append('|')
                .append(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(core.getItem()));
        List<ModifierId> list = TechniqueHandler.techniquesOnCore(partner);
        if (list != null) {
            for (ModifierId id : list) sb.append('|').append(id);
        }
        return sb.toString();
    }
}
