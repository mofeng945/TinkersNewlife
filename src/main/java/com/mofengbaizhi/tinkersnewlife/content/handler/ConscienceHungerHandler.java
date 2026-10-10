package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「心」善恶规则·<b>批 2</b>：饥饿 / 食物 / 睡眠 / 光明 ✓（备忘乡 §440 ✓ 10 条 ✓）。
 *
 * <ul>
 *   <li><b>E15</b> 10s 内恢复<b>超过五分之四</b>（&gt;16 点）饥饿度 ⇒ −3% ✓</li>
 *   <li><b>E16</b> 饥饿度<b>满 20</b> 时饮用/食用任何东西 ⇒ −1% ✓</li>
 *   <li><b>E17</b> 5s 内吃完<b>一整块蛋糕</b>（7 口 ✓）⇒ −1% ✓</li>
 *   <li><b>E18</b> 维持 <b>100% 饥饿度超过 60s</b> ⇒ −3% ✓</li>
 *   <li><b>E20</b> <b>完全黑暗</b>下连续破坏方块，每 100 个 ⇒ −1% ✓（中途挖到亮处方块 ⇒ 计数清零 ✓ 用户默认口径 ✓）</li>
 *   <li><b>E21</b> <b>露天</b>睡觉 ⇒ −3% ✓</li>
 *   <li><b>E22</b> 用<b>木锄头</b>锄地 ⇒ −1% ✓</li>
 *   <li><b>E23</b> 用<b>木斧头</b>砍树 ⇒ −1% ✓（按"每根原木"算 ✓ 未按"每棵树" ✓ 见备忘录 ✓）</li>
 *   <li><b>E24</b> 连续 <b>3 个晚上</b>都靠睡眠跳过 ⇒ −5% ✓</li>
 *   <li><b>G7</b> 10s 内恢复<b>最多五分之一</b>（≤4 点）且确实吃过东西 ⇒ +5% ✓（与 E15 互补 ✓ 不会同时命中 ✓）</li>
 * </ul>
 *
 * <p>说明：饥饿度用 {@link FoodData#getFoodLevel()}（0~20 ✓）✓；
 * E15/G7 共用同一个"10 秒进食窗口" ✓（窗口到期时结算 ✓ 吃得多扣 ✓ 吃得少且有进食 ⇒ 加 ✓）；
 * E20 的计数存**玩家持久数据** ✓ 挖到亮处方块立刻清零 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceHungerHandler {

    private ConscienceHungerHandler() {}

    private static final String K_DARK_BREAK = "tn_deed_dark_break";   // E20 计数
    private static final int FOOD_WINDOW_TICKS = 10 * 20;

    // ============================================================
    //  E15 / G7：10 秒进食窗口
    // ============================================================

    /** 玩家 -> [窗口起点 tick, 累计恢复点数, 是否进食过, 上一个饥饿度] */
    private static final Map<UUID, long[]> FOOD_WIN = new HashMap<>();
    private static final Map<UUID, Boolean> FOOD_ATE = new HashMap<>();
    private static final Map<UUID, Integer> FOOD_LAST = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer sp)) return;
            long now = sp.level().getGameTime();
            int level = sp.getFoodData().getFoodLevel();

            // 饥饿度变化 ⇒ 记进窗口 ✓
            Integer last = FOOD_LAST.put(sp.getUUID(), level);
            if (last != null && level > last) {
                long[] win = FOOD_WIN.get(sp.getUUID());
                if (win == null || now - win[0] > FOOD_WINDOW_TICKS) {
                    win = new long[]{now, 0};
                    FOOD_WIN.put(sp.getUUID(), win);
                    FOOD_ATE.put(sp.getUUID(), false);
                }
                win[1] += (level - last);
            }

            // 窗口到期 ⇒ 结算 ✓
            long[] win = FOOD_WIN.get(sp.getUUID());
            if (win != null && now - win[0] > FOOD_WINDOW_TICKS) {
                long gained = win[1];
                boolean ate = Boolean.TRUE.equals(FOOD_ATE.get(sp.getUUID()));
                FOOD_WIN.remove(sp.getUUID());
                FOOD_ATE.remove(sp.getUUID());
                if (gained > 16) {
                    ConscienceHandler.addAlignment(sp, -3);            // E15 ✓
                } else if (ate && gained <= 4) {
                    ConscienceHandler.addAlignment(sp, +5);            // G7 ✓
                }
            }

            // E18：100% 饥饿度持续 60s ⇒ −3%（每秒查一次 ✓）
            if (sp.tickCount % 20 == 0) tickFullHunger(sp, level, now);
        } catch (Throwable ignored) {
        }
    }

    private static final Map<UUID, Long> FULL_HUNGER_SINCE = new HashMap<>();

    private static void tickFullHunger(ServerPlayer sp, int level, long now) {
        if (level < 20) {
            FULL_HUNGER_SINCE.remove(sp.getUUID());
            return;
        }
        Long since = FULL_HUNGER_SINCE.get(sp.getUUID());
        if (since == null) {
            FULL_HUNGER_SINCE.put(sp.getUUID(), now);
            return;
        }
        if (now - since > 60 * 20) {
            FULL_HUNGER_SINCE.remove(sp.getUUID());
            ConscienceHandler.addAlignment(sp, -3);
        }
    }

    // ============================================================
    //  E16：满饥饿度还吃/喝
    // ============================================================

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            ItemStack stack = event.getItem();
            boolean consumable = stack.isEdible() || stack.getItem() instanceof net.minecraft.world.item.PotionItem;
            if (!consumable) return;
            // 记一笔"确实吃过"（G7 要用 ✓）
            if (stack.isEdible()) {
                long[] win = FOOD_WIN.get(sp.getUUID());
                if (win != null) FOOD_ATE.put(sp.getUUID(), true);
            }
            if (sp.getFoodData().getFoodLevel() >= 20) {
                ConscienceHandler.addAlignment(sp, -1);                // E16 ✓
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E17：5 秒吃完一整块蛋糕
    // ============================================================

    private static final Map<String, Long> CAKE_START = new HashMap<>();

    @SubscribeEvent
    public static void onCake(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            BlockState state = sp.level().getBlockState(event.getPos());
            if (!(state.getBlock() instanceof CakeBlock)) return;
            int bites = state.getValue(CakeBlock.BITES);
            String key = sp.getUUID() + "|" + event.getPos().asLong();
            long now = sp.level().getGameTime();
            if (bites == 0) {
                CAKE_START.put(key, now);
            } else if (bites >= 6) {
                Long start = CAKE_START.remove(key);
                if (start != null && now - start <= 5 * 20) {
                    ConscienceHandler.addAlignment(sp, -1);            // E17 ✓
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E20：黑暗里连续挖方块 / E22 木锄 / E23 木斧
    // ============================================================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
            if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
            BlockPos pos = event.getPos();
            ItemStack tool = sp.getMainHandItem();

            // E23：木斧砍原木 ⇒ 每根 −1%
            BlockState state = level.getBlockState(pos);
            if (tool.is(Items.WOODEN_AXE) && state.is(BlockTags.LOGS)) {
                ConscienceHandler.addAlignment(sp, -1);
            }

            // E20：完全黑暗（方块光与天空光都为 0 ✓）里连续挖 ⇒ 每 100 个 −1%
            boolean dark = level.getMaxLocalRawBrightness(pos) == 0;
            CompoundTag data = sp.getPersistentData();
            if (!dark) {
                data.putInt(K_DARK_BREAK, 0);                          // 挖到亮处 ⇒ 清零 ✓（用户口径 ✓）
            } else {
                int n = data.getInt(K_DARK_BREAK) + 1;
                if (n >= 100) {
                    n -= 100;
                    ConscienceHandler.addAlignment(sp, -1);
                }
                data.putInt(K_DARK_BREAK, n);
            }
        } catch (Throwable ignored) {
        }
    }

    /** E22：木锄头锄地（右键泥土/草方块 ✓ 近似"锄地"✓ 未落地判定 ✓） */
    @SubscribeEvent
    public static void onHoe(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (event.getHand() != InteractionHand.MAIN_HAND) return;
            ItemStack held = event.getItemStack();
            if (!(held.getItem() instanceof HoeItem) || !held.is(Items.WOODEN_HOE)) return;
            BlockState state = sp.level().getBlockState(event.getPos());
            if (state.is(BlockTags.DIRT)) {
                ConscienceHandler.addAlignment(sp, -1);                // E22 ✓
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E21 露天睡觉 / E24 连续三晚睡过
    // ============================================================

    private static final Map<UUID, Long> LAST_SLEEP_DAY = new HashMap<>();
    private static final Map<UUID, Integer> SLEEP_STREAK = new HashMap<>();

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.level().canSeeSky(event.getPos())) {
                ConscienceHandler.addAlignment(sp, -3);                // E21 露天睡觉 ✓
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (event.wakeImmediately()) return;                       // 没睡过去的不算 ✓
            long day = sp.level().getDayTime() / 24000L;
            Long last = LAST_SLEEP_DAY.put(sp.getUUID(), day);
            int streak = SLEEP_STREAK.getOrDefault(sp.getUUID(), 0);
            streak = (last != null && day - last <= 2) ? streak + 1 : 1;   // 连着两晚之内算连续 ✓
            if (streak >= 3) {
                streak = 0;
                ConscienceHandler.addAlignment(sp, -5);                // E24 ✓
            }
            SLEEP_STREAK.put(sp.getUUID(), streak);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  清内存（防长跑泄漏 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onCleanup(TickEvent.PlayerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 600 != 0) return;
            long now = sp.level().getGameTime();
            FOOD_WIN.computeIfPresent(sp.getUUID(), (k, v) -> now - v[0] > 600 ? null : v);
            CAKE_START.entrySet().removeIf(e -> e.getKey().startsWith(sp.getUUID() + "|") && now - e.getValue() > 200);
        } catch (Throwable ignored) {
        }
    }
}
