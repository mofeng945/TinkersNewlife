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

    /**
     * 我们自定义的饰品槽 id（「心」✓ 显示名走 {@code curios.identifier.tinkersnewlife_heart} ✓）。
     *
     * <p>⚠ <b>绝不能叫 {@code heart}</b> ✗：星月遗物已经用数据包占了这个 id
     * （{@code data/celestial_artifacts/curios/slots/heart.json} ✓ size 1 ✓），
     * 同名会共用同一个槽 ⇒ 卸不下来的「心」会把它的 4 个心脏饰品全挤掉 ✗（已修 ✓ 见备忘录 §426）。
     */
    public static final String SLOT_ID = "tinkersnewlife_heart";

    /** 进度条映射：50 = 0%（善恶值 −50..+50 ⇒ 条上 0..100 ✓） */
    public static final int BAR_ZERO = 50;
    public static final int ALIGNMENT_MIN = -50;
    public static final int ALIGNMENT_MAX = 50;

    private static final String KEY_ALIGNMENT = "tn_conscience";        // 权威：玩家持久数据 ✓
    private static final String KEY_EVER_EQUIPPED = "tn_conscience_ever";
    private static final String KEY_MIRROR = "tn_alignment";            // 镜像：物品 NBT ✓
    /** §1112 玩家「开始算天数」的那一游戏日 ✓（上限成长从这天起算 ✓） */
    private static final String KEY_START_DAY = "tn_conscience_start_day";
    /** §1112 每过一个游戏日，上下限各涨的百分点 ✓（1 点 ＝ 1% ✓） */
    public static final int CAP_PER_DAY = 2;
    /** §1112 上下限的封顶 ＝ 50 ✓（用户口径：涨到 50% 为止 ✓） */
    public static final int CAP_MAX = ALIGNMENT_MAX;

    // ============================================================
    //  善恶值读写
    // ============================================================

    /**
     * §818 <b>善恶系统总开关</b>（配置 {@code conscience.enabled} ✓ 默认开 ✓）。
     * <p>读配置失败一律<b>当作开启</b> ✓（保守 ✓ 不要因为读配置出错就把系统静默关掉 ✗）。
     */
    public static boolean isEnabled() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSCIENCE_ENABLED.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * §1112 <b>这名玩家当前的善恶上下限</b>（用户口径：「**初始善恶值上下限为 0，每过一游戏日加 2% 上下限，
     * 直到 50% 为止**」✓）。
     *
     * <p>口径说明 ✓：「每过一游戏日」按**玩家自己**算 ✓（不是世界天数 ✗）——
     * 否则老世界里的新玩家一进来就顶满 50 ✗ 与"**初始**为 0"矛盾 ✗。
     * 起算日存在玩家持久数据 {@code tn_conscience_start_day} ✓（第一次被问到就写入 ✓）。
     *
     * <p>⚠ <b>老存档兼容</b>（这次最容易伤到老玩家的一点 ✓）：第一次初始化起算日时，
     * 若该玩家**已经有**善恶值 ⇒ 把起算日**回填**成「当前日 − ⌈|值| ÷ 2⌉」✓
     * ⇒ 他的上限**立刻 ≥ 自己现有的值** ✓ ⇒ **绝不会**因为新规则被削掉已有进度 ✓。
     */
    public static int capOf(Player player) {
        if (player == null || player.level() == null) return CAP_MAX;
        try {
            long day = player.level().getDayTime() / 24000L;
            net.minecraft.nbt.CompoundTag data = player.getPersistentData();
            if (!data.contains(KEY_START_DAY)) {
                int cur = Math.abs(data.getInt(KEY_ALIGNMENT));
                long back = (cur + CAP_PER_DAY - 1L) / CAP_PER_DAY;      // ⌈|值| / 2⌉ ✓
                data.putLong(KEY_START_DAY, day - back);
            }
            long days = Math.max(0L, day - data.getLong(KEY_START_DAY));
            return (int) Math.min((long) CAP_MAX, days * CAP_PER_DAY);
        } catch (Throwable t) {
            return CAP_MAX;        // 任何异常 ⇒ 退回原口径 ✓ 不把玩家锁死在 0 ✗
        }
    }

    /** 当前善恶值（上限随天数成长 ✓ −50 恶 ~ +50 善 ✓ 初始 0 ✓）；§818 系统关掉时一律按 <b>0</b> ✓ */
    public static int getAlignment(Player player) {
        if (player == null || !isEnabled()) return 0;
        int raw = player.getPersistentData().getInt(KEY_ALIGNMENT);
        // §1112 上限随天数成长 ⇒ 读出来的值也按**当前上限**夹一次 ✓
        //   （老存档里超过新上限的会被压到新上限 ⇒ 伤害/生命/阈值效果一并收窄 ✓ 口径一致 ✓）
        int cap = capOf(player);
        return Math.max(-cap, Math.min(cap, raw));
    }

    /**
     * §508 **别人眼中的善恶值**（用户口径：「双向认知阻碍面具……佩戴者的善恶值……都将对外视为 0（自己看不是 0）」✓）。
     *
     * <p>⚠⚠ <b>§675 用户口径：伏诛赐死（处刑域）是这条规则的例外，它不吃面具</b> ——
     * 用户原话：「<b>伏诛赐死不要被面具骗过</b>」✓。
     * ⇒ {@code ExecutionDomain} 判定"有没有罪"的两处（开庭 / 裁决）必须读<b>真值</b>：
     * 直接调 {@link #getAlignment} ✓，<b>不要</b>调本方法 ✗ —— 否则戴面具的恶人会被判无罪 ✗。
     * <p>（§674 我曾把处刑域也按"第三方"改成走本方法，被用户否掉 ✓；
     * 这段注释就是<b>为了不让下一个人再把它改回去</b> ✗ —— 改之前先看这里 ✓。）
     *
     * @param target   被观察的玩家
     * @param observer 观察者：<b>自己 / 系统内部</b>传 {@code null} 或 {@code target} ⇒ 真实值 ✓；
     *                 任何<b>第三方</b>（指令、NPC、生物、别的玩家…）传它自己 ⇒ 戴面具者一律视为 <b>0</b> ✓
     *                 （⚠ 处刑域<b>不在</b>这个名单里 ⇒ 见上面 §675 ✓）
     */
    public static int alignmentAsSeenBy(Player target, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity observer) {
        if (target == null) return 0;
        if (observer == null || observer == target) return getAlignment(target);
        if (com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem.isWorn(target)) return 0;
        return getAlignment(target);
    }

    /** 直接设值（会夹在**当前上下限**内 ✓ —— §1112 起上下限随天数成长 ✓）—— 12 条增减规则都走这里 ✓ */
    public static void setAlignment(Player player, int value) {
        if (player == null) return;
        if (!isEnabled()) return;              // §818 系统关掉 ⇒ 不记分 ✓（所有规则自然失效 ✓）
        // §1112 ⚠ 不能再写死 ±50 ✗ —— 上限由 capOf 给（初始 0 ✓ 每游戏日 +2 ✓ 封顶 50 ✓）
        int cap = capOf(player);
        int clamped = Math.max(-cap, Math.min(cap, value));
        if (clamped == getAlignment(player)) return;
        player.getPersistentData().putInt(KEY_ALIGNMENT, clamped);
        // 一变就同步最大生命修饰符 ✓（第二期口径：最大生命 ×(1 + 善恶%) ✓）
        ConscienceAlignmentHandler.onAlignmentChanged(player);
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

    /**
     * <b>镜像善恶值</b>：直接读玩家「心」槽里那枚物品的 NBT 镜像 ✓。
     *
     * <p>为什么需要它：权威值在<b>服务端</b>玩家持久数据里 ✓ 而<b>客户端拿不到</b> ✗
     * （ForgeData 不走同步 ✗）⇒ 凡是<b>客户端</b>要用的地方（物品提示 ✓ HUD 图标 ✓ 七咒之戒提示改写 ✓）
     * 一律读这个镜像 ✓（Curios 会把饰品槽同步到客户端 ✓）⇒ 最多 1 秒延迟 ✓。
     *
     * <p>⚠ 游戏逻辑（伤害/属性/阈值效果）仍然一律用服务端权威的 {@link #getAlignment} ✓ 不用这个 ✓。
     */
    public static int mirrorAlignment(Player player) {
        if (player == null) return 0;
        try {
            ItemStack heart = CuriosApi.getCuriosInventory(player).resolve()
                    .map(h -> h.getCurios().get(SLOT_ID))
                    .map(h -> h.getStacks().getSlots() > 0 ? h.getStacks().getStackInSlot(0) : ItemStack.EMPTY)
                    .orElse(ItemStack.EMPTY);
            if (!(heart.getItem() instanceof ConscienceItem)) return 0;
            return mirrorOf(heart, BAR_ZERO) - BAR_ZERO;
        } catch (Throwable ignored) {
            return 0;
        }
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
        clearForeignHearts(player);          // 先清掉混在别人槽里的「心」✗（历史存档会残留 ✓）
        if (!isEnabled()) {
            // §818 关掉善恶系统 ⇒ **不佩戴「心」** ✓（槽里已经有的话也收掉 ✓ —— 用户口径）
            ItemStack slot = getHeartStack(player);
            if (slot != null && slot.getItem() instanceof ConscienceItem) {
                setHeartStack(player, ItemStack.EMPTY);
            }
            // 顺便把当年加上去的属性修饰符按"善恶 0"对齐回去 ✓ 不留残留 ✗
            ConscienceAlignmentHandler.refreshMaxHealth(player);
            ConscienceAlignmentHandler.refreshAttackDamage(player);
            return;
        }
        int alignment = getAlignment(player);
        ConscienceAlignmentHandler.refreshMaxHealth(player);   // 最大生命 ×(1+善恶%) 兜底对齐 ✓（登录/重生/换维度 ✓）
        ConscienceAlignmentHandler.refreshAttackDamage(player); // 攻击伤害独立乘区 ×(1−善恶%) 同上 ✓
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

    /**
     * 清掉混在**别人的饰品槽**里的「心」✓。
     *
     * <p>为什么必须我们来做：{@code canUnequip=false} ⇒ <b>玩家自己拖不出来</b> ✗。
     * 历史原因：早期我们把槽位 id 误设成 {@code heart} ✗ 与星月遗物撞车 ✓
     * ⇒ 已装过旧版的存档里，那枚「心」正卡在它的「星月-心」槽里 ✓
     * （而且会把它的 4 个心脏饰品全占掉 ✗）。这一层保证换 id 后**自动清干净** ✓ 不丢善恶值 ✓
     * （权威值在玩家持久数据里 ✓ 补一枚新的即可 ✓）。
     */
    private static void clearForeignHearts(ServerPlayer player) {
        try {
            var handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            if (handler == null) return;
            for (var entry : handler.getCurios().entrySet()) {
                if (SLOT_ID.equals(entry.getKey())) continue;            // 自己的槽不动 ✓
                IDynamicStackHandler stacks = entry.getValue().getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack s = stacks.getStackInSlot(i);
                    if (!s.isEmpty() && s.getItem() == ModItems.CONSCIENCE.get()) {
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
