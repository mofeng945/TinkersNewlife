package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「心」善恶规则·<b>批 1</b>：交易 / 村庄 / 日常善行 ✓（用户口径见备忘录 §440 ✓）。
 *
 * <p>本批 9 条（E1/G1「墨默或流浪商人出现」那两条判定最绕 ✓ 单独一批做 ✓）：
 * <ul>
 *   <li><b>E3</b> 破坏<b>村庄以外任何结构</b>内的方块，每 5 个 −1% ✓</li>
 *   <li><b>E7</b> 让村民在 20s 内达到交易上限 −1% ✓</li>
 *   <li><b>E8</b> 5 分钟内连续开启 ≥20 个战利品箱 −5% ✓</li>
 *   <li><b>G3</b> 亲手种植 / 收获作物，<b>每 10 个</b> +1%（用户压过口径 ✓）</li>
 *   <li><b>G4</b> 通过<b>搭建</b>制造铁傀儡 +1% ✓</li>
 *   <li><b>G5</b> 10s 内交易消耗 &gt;2 组绿宝石 +3% ✓</li>
 *   <li><b>G8</b> 亲手制作物品，每 20 次 +1% ✓</li>
 *   <li><b>G10</b> 使用骨粉 20 次 +1% ✓</li>
 *   <li><b>G12</b> 亲手用斧头剥树皮，每 5 个 +1% ✓</li>
 * </ul>
 *
 * <p>口径与技术要点：
 * <ul>
 *   <li>"每 N 个/次"类计数一律存**玩家持久数据** ✓（跨重登不丢 ✓）；时间窗口类只放**内存** ✓（重登丢掉无所谓 ✓）；</li>
 *   <li>"结构内"用 {@code StructureManager#getAllStructuresAt(BlockPos)} 非空 ⇒ 在某个结构里 ✓
 *       再排除 {@link StructureTags#VILLAGE}（村庄那条是 §431 的⑤ ✓ 别重复扣 ✓）；</li>
 *   <li>"战利品箱"沿用 §431 的判定：方块实体 NBT 里**还有 {@code LootTable}** ✓（开过就没了 ⇒ 天然不重复 ✓）；</li>
 *   <li>"搭建铁傀儡"是**近似判定** ✓：铁傀儡加入世界时，16 格内最近的玩家**最近刚放过方块**（100 tick 内 ✓）⇒ 认作它搭的 ✓；</li>
 *   <li>种植判定走 {@code BlockEvent.EntityPlaceEvent} 且落地的是作物/下界疣 ⇒ 准确 ✓（比"右键点了一下"强 ✓）；</li>
 *   <li>全部 `try/catch(Throwable)` ✓ 单条规则出问题也不影响其它规则与本体 ✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceDeedHandler {

    private ConscienceDeedHandler() {}

    // 计数器（持久数据 ✓）
    private static final String K_CRAFT = "tn_deed_craft";        // G8 每 20 次
    private static final String K_BONEMEAL = "tn_deed_bonemeal";  // G10 每 20 次
    private static final String K_STRIP = "tn_deed_strip";        // G12 每 5 个
    private static final String K_FARM = "tn_deed_farm";          // G3 每 10 个
    private static final String K_STRUCT = "tn_deed_struct";      // E3 每 5 个

    /** 每 N 次给一次善恶（per = 次数 ✓ delta = 百分点 ✓） */
    private static void bump(ServerPlayer sp, String key, int per, int delta) {
        CompoundTag d = sp.getPersistentData();
        int n = d.getInt(key) + 1;
        if (n >= per) {
            n -= per;
            ConscienceHandler.addAlignment(sp, delta);
        }
        d.putInt(key, n);
    }

    // ============================================================
    //  G8 亲手制作物品
    // ============================================================

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer sp) bump(sp, K_CRAFT, 20, +1);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  G10 骨粉
    // ============================================================

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (event.getResult() == net.minecraftforge.eventbus.api.Event.Result.DENY) return;   // 没真正生效的不算 ✓
            ItemStack used = event.getStack();
            if (used.isEmpty() || !used.is(Items.BONE_MEAL)) return;
            bump(sp, K_BONEMEAL, 20, +1);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  G12 剥树皮 / G3 种植（都走右键 ✓ 种植用放置事件更准 ✓ 见下）
    // ============================================================

    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
            ItemStack held = event.getItemStack();
            if (held.isEmpty()) return;

            // G12：斧头右键原木 ⇒ 原版就是把皮剥掉 ✓
            if (held.getItem() instanceof AxeItem) {
                BlockState state = sp.level().getBlockState(event.getPos());
                if (state.is(net.minecraft.tags.BlockTags.LOGS)) bump(sp, K_STRIP, 5, +1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** G3 种植：放置下来的方块是作物 / 下界疣 ⇒ 亲手种 ✓（比"右键了一下"准 ✓） */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            BlockState placed = event.getPlacedBlock();
            Block b = placed.getBlock();
            boolean crop = b instanceof CropBlock || b instanceof NetherWartBlock;
            if (crop) {
                bump(sp, K_FARM, 10, +1);
                PLACED_AT.put(sp.getUUID(), sp.level().getGameTime());     // 供 G4 铁傀儡判定 ✓
                return;
            }
            PLACED_AT.put(sp.getUUID(), sp.level().getGameTime());         // 任何放置都记一笔 ✓
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  G3 收获 / E3 结构内挖方块
    // ============================================================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            BlockPos pos = event.getPos();
            BlockState state = level.getBlockState(pos);

            // G3 收获成熟作物 ✓
            if (isRipe(state)) bump(sp, K_FARM, 10, +1);

            // E3 村庄**以外**的任何结构内挖方块 ✓
            boolean insideAnyStructure = !level.structureManager().getAllStructuresAt(pos).isEmpty();
            if (insideAnyStructure && !inVillage(level, pos)) bump(sp, K_STRUCT, 5, -1);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isRipe(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof CropBlock crop) return crop.isMaxAge(state);
        if (b instanceof NetherWartBlock) {
            IntegerProperty age = NetherWartBlock.AGE;
            return state.getValue(age) >= 3;
        }
        return false;
    }

    private static boolean inVillage(ServerLevel level, BlockPos pos) {
        try {
            return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ============================================================
    //  G4 搭建铁傀儡（近似判定 ✓）
    // ============================================================

    private static final Map<UUID, Long> PLACED_AT = new HashMap<>();          // 玩家 -> 上次放置方块的 tick
    private static final Map<UUID, Long> GOLEM_DONE = new LinkedHashMap<>();   // 已结算的铁傀儡（防重复 ✓ 有上限 ✓）

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        try {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof IronGolem golem)) return;
            if (GOLEM_DONE.containsKey(golem.getUUID())) return;
            long now = event.getLevel().getGameTime();

            ServerPlayer nearest = null;
            double best = Double.MAX_VALUE;
            // ⚠ `EntityJoinLevelEvent#getLevel()` 是 `LevelAccessor` ✗ 没有 getPlayers ✗ ⇒ 取实体自己的 level（ServerLevel ✓）
            if (!(event.getEntity().level() instanceof ServerLevel sl)) return;
            for (ServerPlayer p : sl.players()) {
                double d = p.distanceToSqr(golem);
                if (d < best) { best = d; nearest = p; }
            }
            if (nearest == null || best > 16 * 16) return;
            Long placed = PLACED_AT.get(nearest.getUUID());
            if (placed == null || now - placed > 100) return;               // 100 tick 内刚放过方块 ⇒ 认作搭建 ✓

            GOLEM_DONE.put(golem.getUUID(), now);
            while (GOLEM_DONE.size() > 512) {
                Iterator<UUID> it = GOLEM_DONE.keySet().iterator();
                it.next();
                it.remove();
            }
            ConscienceHandler.addAlignment(nearest, +1);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E8 5 分钟内连开 20 个战利品箱
    // ============================================================

    private static final Map<UUID, List<Long>> CHEST_OPENS = new HashMap<>();
    private static final int CHEST_WINDOW_TICKS = 5 * 60 * 20;
    private static final int CHEST_LIMIT = 20;

    @SubscribeEvent
    public static void onOpenLootChest(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            BlockEntity be = level.getBlockEntity(event.getPos());
            if (be == null) return;
            CompoundTag tag = be.saveWithoutMetadata();
            if (!tag.contains("LootTable", Tag.TAG_STRING)) return;          // 只算"还有战利品表的箱子" ✓

            long now = level.getGameTime();
            List<Long> opens = CHEST_OPENS.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
            opens.add(now);
            opens.removeIf(t -> now - t > CHEST_WINDOW_TICKS);
            if (opens.size() >= CHEST_LIMIT) {
                opens.clear();
                ConscienceHandler.addAlignment(sp, -5);
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  G5 交易消耗绿宝石 / E7 村民 20s 内到上限
    // ============================================================

    private static final Map<UUID, List<Long>> EMERALD_WINDOW = new HashMap<>();   // 玩家 -> 每次交易消耗绿宝石的 tick（重复条目代表数量 ✓）
    private static final Map<String, Long> VILLAGER_FIRST_TRADE = new HashMap<>(); // 玩家+村民 -> 首次交易 tick
    private static final int EMERALD_WINDOW_TICKS = 10 * 20;

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            MerchantOffer offer = event.getMerchantOffer();
            if (offer == null) return;
            long now = sp.level().getGameTime();

            // G5：10s 内消耗 > 2 组（128 个）绿宝石 ⇒ +3%（结算后清窗 ✓ 防连续触发 ✓）
            int emeralds = emeraldCount(offer.getCostA()) + emeraldCount(offer.getCostB());
            if (emeralds > 0) {
                List<Long> win = EMERALD_WINDOW.computeIfAbsent(sp.getUUID(), k -> new ArrayList<>());
                for (int i = 0; i < emeralds; i++) win.add(now);
                win.removeIf(t -> now - t > EMERALD_WINDOW_TICKS);
                if (win.size() > 128) {
                    win.clear();
                    ConscienceHandler.addAlignment(sp, +3);
                }
            }

            // E7：20s 内把村民买到全部缺货 ⇒ −1%
            // ⚠ Forge 的 TradeWithVillagerEvent 里访问器叫 `getAbstractVillager()` ✗ 不是 getMerchant ✗
            if (event.getAbstractVillager() instanceof Villager villager) {
                String key = sp.getUUID() + "|" + villager.getUUID();
                long first = VILLAGER_FIRST_TRADE.computeIfAbsent(key, k -> now);
                if (now - first <= 20 * 20 && allOutOfStock(villager)) {
                    VILLAGER_FIRST_TRADE.put(key, now);                       // 重置窗口 ⇒ 同一村民再来一轮才会再扣 ✓
                    ConscienceHandler.addAlignment(sp, -1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static int emeraldCount(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.EMERALD) ? stack.getCount() : 0;
    }

    private static boolean allOutOfStock(Villager villager) {
        boolean any = false;
        for (MerchantOffer offer : villager.getOffers()) {
            any = true;
            if (!offer.isOutOfStock()) return false;
        }
        return any;
    }

    // ============================================================
    //  收尾：清理内存里的窗口数据（防长跑泄漏 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 600 != 0) return;                              // 每 30s 扫一次 ✓
            long now = sp.level().getGameTime();
            CHEST_OPENS.computeIfPresent(sp.getUUID(), (k, v) -> {
                v.removeIf(t -> now - t > CHEST_WINDOW_TICKS);
                return v.isEmpty() ? null : v;
            });
            EMERALD_WINDOW.computeIfPresent(sp.getUUID(), (k, v) -> {
                v.removeIf(t -> now - t > EMERALD_WINDOW_TICKS);
                return v.isEmpty() ? null : v;
            });
            VILLAGER_FIRST_TRADE.entrySet().removeIf(e -> e.getKey().startsWith(sp.getUUID() + "|") && now - e.getValue() > 20 * 20);
            PLACED_AT.computeIfPresent(sp.getUUID(), (k, v) -> now - v > 200 ? null : v);
        } catch (Throwable ignored) {
        }
    }
}
