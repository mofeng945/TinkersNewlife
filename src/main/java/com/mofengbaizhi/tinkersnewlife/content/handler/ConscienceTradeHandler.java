package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「心」善恶规则·<b>批 4 + 商人口径</b>（备忘录 §440 ✓ 6 条 ✓）。
 *
 * <ul>
 *   <li><b>E6</b> 5s 内获得超过 3 组（&gt;192）<b>同类物品</b>（只按物品 id 聚合 ✓ 用户口径 ✓）⇒ −1% ✓</li>
 *   <li><b>E9</b> 20s 内连续繁殖超过 10 次 ⇒ −5% ✓</li>
 *   <li><b>E10</b> 10 格范围内有<b>村民</b>正在繁殖 ⇒ −5% ✓</li>
 *   <li><b>G9</b> 连续钓鱼超过 5 分钟 ⇒ +5% ✓（连续 = 收钩后 10s 内再次抛钩 ✓ 用户口径 ✓）</li>
 *   <li><b>E1</b> 墨默/流浪商人出现时，<b>没</b>交互查看商品至少一次 ⇒ −1% ✓</li>
 *   <li><b>G1</b> 墨默/流浪商人出现时，与他们<b>交易至少 2 次</b> ⇒ +1% ✓</li>
 * </ul>
 *
 * <p>口径与近似（照实写 ✓）：
 * <ul>
 *   <li>E6 只统计**拾取**（`EntityItemPickupEvent` ✓）与**合成产出**（`ItemCraftedEvent` ✓）✓
 *       机器/漏斗直接塞进背包的不算 ✗（未逐条覆盖 ✓）；</li>
 *   <li>E1/G1 的"出现"= 商人实体**加入世界**且在玩家 32 格内 ✓；结算 = 它**离开世界**（死亡/消失/卸载 ✓）
 *       或超过 30 分钟上限 ✓；"查看过商品" = 玩家开着村民交易界面且该商人在 8 格内 ✓；
 *       交易次数按 `TradeWithVillagerEvent#getAbstractVillager()` 与那一只对号入座 ✓；</li>
 *   <li>商人判定：原版 `WanderingTrader` ✓ 或 **本模组的墨默**（类名 `MomoMerchant` ✓ 按类名判 ✓ 不引编译依赖 ✓）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceTradeHandler {

    private ConscienceTradeHandler() {}

    // ============================================================
    //  E6：5s 内 >3 组同类物品
    // ============================================================

    private record Gain(long tick, int count) {}

    private static final Map<UUID, Map<String, List<Gain>>> GAINS = new HashMap<>();
    private static final int GAIN_WINDOW = 5 * 20;
    private static final int GAIN_LIMIT = 192;                                  // 3 组 ✓

    private static void recordGain(ServerPlayer sp, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        String id = stack.getItem().builtInRegistryHolder().key().location().toString();
        long now = sp.level().getGameTime();
        Map<String, List<Gain>> byId = GAINS.computeIfAbsent(sp.getUUID(), k -> new HashMap<>());
        List<Gain> list = byId.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(new Gain(now, stack.getCount()));
        list.removeIf(g -> now - g.tick() > GAIN_WINDOW);
        int total = 0;
        for (Gain g : list) total += g.count();
        if (total > GAIN_LIMIT) {
            byId.remove(id);                                                    // 结算后清掉这一 id ✓
            ConscienceHandler.addAlignment(sp, -1);
        }
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer sp) recordGain(sp, event.getItem().getItem());
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer sp) recordGain(sp, event.getCrafting());
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E9 / E10：繁殖
    // ============================================================

    private static final Map<UUID, List<Long>> BREED_WINDOW = new HashMap<>();
    private static final int BREED_WINDOW_TICKS = 20 * 20;

    @SubscribeEvent
    public static void onBaby(BabyEntitySpawnEvent event) {
        try {
            Entity child = event.getChild();
            if (child == null || child.level().isClientSide) return;
            long now = child.level().getGameTime();

            // E9：20s 内繁殖 >10 次 ⇒ −5%
            if (event.getCausedByPlayer() instanceof ServerPlayer sp) {
                List<Long> win = BREED_WINDOW.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
                win.add(now);
                win.removeIf(t -> now - t > BREED_WINDOW_TICKS);
                if (win.size() > 10) {
                    win.clear();
                    ConscienceHandler.addAlignment(sp, -5);
                }
            }

            // E10：10 格内有**村民**在繁殖 ⇒ −5%
            if (child instanceof Villager && child.level() instanceof ServerLevel sl) {
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, child.getBoundingBox().inflate(10.0D),
                        x -> x.isAlive() && !x.isSpectator())) {
                    ConscienceHandler.addAlignment(p, -5);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  G9：连续钓鱼 5 分钟
    // ============================================================

    /** 玩家 -> [本次抛钩起点 tick, 已累计 tick, 上次收钩 tick] */
    private static final Map<UUID, long[]> FISH = new HashMap<>();
    private static final Map<UUID, UUID> BOBBER_OWNER = new HashMap<>();
    private static final int FISH_TARGET = 5 * 60 * 20;
    private static final int FISH_CONTINUE_TICKS = 10 * 20;

    @SubscribeEvent
    public static void onBobberJoin(EntityJoinLevelEvent event) {
        try {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof FishingHook hook)) return;
            // ⚠ `getPlayerOwner()` 的返回类型是 `Player` ✗ 不是 ServerPlayer ✗ ⇒ 自己判一下 ✓
            if (!(hook.getPlayerOwner() instanceof ServerPlayer owner)) return;
            long now = event.getLevel().getGameTime();
            BOBBER_OWNER.put(hook.getUUID(), owner.getUUID());
            long[] st = FISH.get(owner.getUUID());
            if (st == null) {
                st = new long[]{now, 0, -100000L};
                FISH.put(owner.getUUID(), st);
            }
            if (now - st[2] > FISH_CONTINUE_TICKS) st[1] = 0;                    // 断连 ⇒ 重新累计 ✓
            st[0] = now;
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onBobberLeave(EntityLeaveLevelEvent event) {
        try {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof FishingHook hook)) return;
            UUID ownerId = BOBBER_OWNER.remove(hook.getUUID());
            if (ownerId == null) return;
            long now = event.getLevel().getGameTime();
            long[] st = FISH.get(ownerId);
            if (st == null) return;
            st[1] += Math.max(0, now - st[0]);                                  // 累计本次 ✓
            st[2] = now;                                                        // 记收钩时刻 ✓
            if (st[1] >= FISH_TARGET) {
                st[1] = 0;
                if (event.getLevel() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof ServerPlayer sp) {
                    ConscienceHandler.addAlignment(sp, +5);                     // G9 ✓
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E1 / G1：墨默 / 流浪商人
    // ============================================================

    private record TraderSession(long startTick, boolean seenGui, int trades) {}

    private static final Map<String, TraderSession> TRADERS = new HashMap<>();   // "玩家|商人" -> 会话
    private static final long TRADER_MAX_TICKS = 30 * 60 * 20;                  // 30 分钟上限 ✓

    private static boolean isTrader(Entity e) {
        if (e instanceof WanderingTrader) return true;
        try {
            return e.getClass().getSimpleName().equals("MomoMerchant");          // 本模组的墨默 ✓ 不引编译依赖 ✓
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String key(UUID player, UUID trader) {
        return player + "|" + trader;
    }

    @SubscribeEvent
    public static void onTraderJoin(EntityJoinLevelEvent event) {
        try {
            if (event.getLevel().isClientSide()) return;
            if (!isTrader(event.getEntity())) return;
            if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
            long now = sl.getGameTime();
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class,
                    event.getEntity().getBoundingBox().inflate(32.0D), x -> x.isAlive())) {
                TRADERS.putIfAbsent(key(p.getUUID(), event.getEntity().getUUID()),
                        new TraderSession(now, false, 0));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 结算：离开世界（死亡/消失/卸载 ✓）或超时 ⇒ 没看过商品 −1% ✓ 交易 ≥2 次 +1% ✓ */
    @SubscribeEvent
    public static void onTraderLeave(EntityLeaveLevelEvent event) {
        try {
            if (event.getLevel().isClientSide()) return;
            if (!isTrader(event.getEntity())) return;
            UUID trader = event.getEntity().getUUID();
            Iterator<Map.Entry<String, TraderSession>> it = TRADERS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, TraderSession> e = it.next();
                if (!e.getKey().endsWith("|" + trader)) continue;
                it.remove();
                settle(event.getLevel(), e.getKey(), e.getValue());
            }
        } catch (Throwable ignored) {
        }
    }

    private static void settle(net.minecraft.world.level.LevelAccessor level, String key, TraderSession session) {
        try {
            if (!(level instanceof ServerLevel sl)) return;
            UUID playerId = UUID.fromString(key.substring(0, key.indexOf('|')));
            if (!(sl.getEntity(playerId) instanceof ServerPlayer sp)) return;
            if (!session.seenGui()) ConscienceHandler.addAlignment(sp, -1);      // E1 ✓
            else if (session.trades() >= 2) ConscienceHandler.addAlignment(sp, +1);   // G1 ✓
        } catch (Throwable ignored) {
        }
    }

    /** 每秒：开着交易界面且商人在 8 格内 ⇒ 标记"看过商品" ✓；顺带清理超时会话 ✓ */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 20 != 0) return;
            long now = sp.level().getGameTime();

            if (sp.containerMenu instanceof MerchantMenu) {
                for (Entity e : sp.level().getEntities(sp, sp.getBoundingBox().inflate(8.0D), ConscienceTradeHandler::isTrader)) {
                    String k = key(sp.getUUID(), e.getUUID());
                    TraderSession s = TRADERS.get(k);
                    if (s != null && !s.seenGui()) TRADERS.put(k, new TraderSession(s.startTick(), true, s.trades()));
                }
            }

            // 超时结算 + 清内存
            Iterator<Map.Entry<String, TraderSession>> it = TRADERS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, TraderSession> e = it.next();
                if (!e.getKey().startsWith(sp.getUUID() + "|")) continue;
                if (now - e.getValue().startTick() > TRADER_MAX_TICKS) {
                    TraderSession s = e.getValue();
                    it.remove();
                    if (!s.seenGui()) ConscienceHandler.addAlignment(sp, -1);
                    else if (s.trades() >= 2) ConscienceHandler.addAlignment(sp, +1);
                }
            }
            if (sp.tickCount % 600 == 0) {
                GAINS.computeIfPresent(sp.getUUID(), (k, v) -> { v.clear(); return null; });
                BREED_WINDOW.computeIfPresent(sp.getUUID(), (k, v) -> {
                    v.removeIf(t -> now - t > BREED_WINDOW_TICKS);
                    return v.isEmpty() ? null : v;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /** 交易：给对应商人会话 +1 次 ✓ */
    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            Entity trader = event.getAbstractVillager();
            if (trader == null) return;
            String k = key(sp.getUUID(), trader.getUUID());
            TraderSession s = TRADERS.get(k);
            if (s != null) TRADERS.put(k, new TraderSession(s.startTick(), s.seenGui(), s.trades() + 1));
        } catch (Throwable ignored) {
        }
    }
}
