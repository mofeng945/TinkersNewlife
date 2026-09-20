package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.ConscienceItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 饰品·<b>心</b>的兜底与善恶值存储 ✓。
 *
 * <h2>善恶值（权威在玩家持久数据 ✓ 初始 0 ✓ 用户口径）</h2>
 * <ul>
 *   <li>{@link #getAlignment} / {@link #setAlignment}：<b>−50 ~ +50</b> 的整数 ✓
 *       （正 = 善 ✓ 负 = 恶 ✓ 与用户给的"进度条 −50%~+50%"一一对应 ✓）；</li>
 *   <li>物品 NBT 里的 {@code tn_alignment} 是<b>镜像</b>（每 20 tick 由本类刷新 ✓），
 *       权威值永远只在这一处 ⇒ 亚波伦把「心」收走再补回来也不会丢进度 ✓✓。</li>
 * </ul>
 *
 * <h2>自动装备 + 不可卸下（模仿七咒之戒并拓展 ✓）</h2>
 * <ul>
 *   <li>玩家登录：若<b>从未装备过</b>（持久标记缺失 ✓）⇒ 自动放进「心」槽 ✓ 并写下标记 ✓；</li>
 *   <li>之后每 20 tick 兜底：槽里不是「心」就补回 ✓（{@code canUnequip=false} 挡玩家 ✓
 *       这一层挡"绕过 curios API 直接写槽"的第三方 ✓ 例如亚波伦的「饰品储存水晶」✓）。</li>
 * </ul>
 *
 * <p>⚠ 槽位访问用的是**反汇编实证过**的 curios API 路径 ✓：
 * {@code CuriosApi.getCuriosInventory} → {@code getCurios()}（{@code Map<String, ICurioStacksHandler>}）
 * → {@code getStacks()}（{@code IDynamicStackHandler}）→ {@code getStackInSlot / setStackInSlot} ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceHandler {

    private ConscienceHandler() {}

    /** 我们自定义的饰品槽 id（「心」✓ 显示名走 {@code curios.identifier.heart} ✓） */
    public static final String SLOT_ID = "heart";

    /** 进度条映射：50 = 0%（善恶值 −50..+50 ⇒ 条上 0..100 ✓） */
    public static final int BAR_ZERO = 50;
    public static final int ALIGNMENT_MIN = -50;
    public static final int ALIGNMENT_MAX = 50;

    private static final String KEY_ALIGNMENT = "tn_conscience";        // 权威：玩家持久数据 ✓
    private static final String KEY_EVER_EQUIPPED = "tn_conscience_ever";
    private static final String KEY_MIRROR = "tn_alignment";            // 镜像：物品 NBT ✓

    // ============================================================
    //  善恶值读写
    // ============================================================

    /** 当前善恶值（−50 恶 ~ +50 善 ✓ 初始 0 ✓） */
    public static int getAlignment(Player player) {
        if (player == null) return 0;
        return player.getPersistentData().getInt(KEY_ALIGNMENT);
    }

    /** 直接设值（会夹在 −50~+50 ✓）—— 第二期的 12 条增减规则都走这里 ✓ */
    public static void setAlignment(Player player, int value) {
        if (player == null) return;
        player.getPersistentData().putInt(KEY_ALIGNMENT,
                Math.max(ALIGNMENT_MIN, Math.min(ALIGNMENT_MAX, value)));
    }

    /** 增减（第二期用 ✓） */
    public static void addAlignment(Player player, int delta) {
        setAlignment(player, getAlignment(player) + delta);
    }

    /** 是否曾经自动装备过（用于"任何存档打开时未装备过就自动装备" ✓） */
    public static boolean hasEverEquipped(Player player) {
        return player != null && player.getPersistentData().getBoolean(KEY_EVER_EQUIPPED);
    }

    // ============================================================
    //  物品 NBT 镜像（给条/染色/物品栏颜色读 ✓）
    // ============================================================

    public static int mirrorOf(ItemStack stack, int fallback) {
        if (stack == null || stack.isEmpty()) return fallback;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY_MIRROR) ? tag.getInt(KEY_MIRROR) : fallback;
    }

    private static void writeMirror(ItemStack stack, int alignment) {
        stack.getOrCreateTag().putInt(KEY_MIRROR, alignment + BAR_ZERO);
    }

    // ============================================================
    //  自动装备 / 兜底补回
    // ============================================================

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ensure(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 20 != 0) return;                 // 每秒检查一次 ✓ 够用且便宜 ✓
        ensure(sp);
    }

    /** 保证「心」在槽里（不在就补一个 ✓）并把善恶值镜像写进去 ✓ */
    public static void ensure(ServerPlayer player) {
        int alignment = getAlignment(player);
        ItemStack existing = getHeartStack(player);
        if (existing != null && existing.getItem() instanceof ConscienceItem) {
            writeMirror(existing, alignment);
            if (!hasEverEquipped(player)) {
                player.getPersistentData().putBoolean(KEY_EVER_EQUIPPED, true);
            }
            return;
        }
        ItemStack heart = new ItemStack(ModItems.CONSCIENCE.get());
        writeMirror(heart, alignment);
        setHeartStack(player, heart);
        if (!hasEverEquipped(player)) {
            player.getPersistentData().putBoolean(KEY_EVER_EQUIPPED, true);
        }
    }

    // ============================================================
    //  槽位访问（curios API ✓ 已在反汇编里核对过方法链 ✓）
    // ============================================================

    private static ICurioStacksHandler heartHandler(ServerPlayer player) {
        try {
            var handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            if (handler == null) return null;
            return handler.getCurios().get(SLOT_ID);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 槽里的东西（拿不到就返回 null ✓） */
    public static ItemStack getHeartStack(ServerPlayer player) {
        try {
            ICurioStacksHandler h = heartHandler(player);
            if (h == null) return null;
            IDynamicStackHandler stacks = h.getStacks();
            return stacks.getSlots() > 0 ? stacks.getStackInSlot(0) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setHeartStack(ServerPlayer player, ItemStack stack) {
        try {
            ICurioStacksHandler h = heartHandler(player);
            if (h == null) return;
            IDynamicStackHandler stacks = h.getStacks();
            if (stacks.getSlots() > 0) stacks.setStackInSlot(0, stack);
        } catch (Throwable ignored) {
        }
    }
}
