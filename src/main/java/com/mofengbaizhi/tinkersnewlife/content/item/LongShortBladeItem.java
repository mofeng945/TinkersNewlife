package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.LongShortBladeHandler;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.InvulnerabilityManager;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⭐ §1124 <b>长短刃</b>（用户口径 ✓）—— 一件匠魂工具，手持时**主副手各拿一把** ✓。
 *
 * <h2>形态（NBT {@code lnb_form}）</h2>
 * <ul>
 *   <li>{@link #FORM_LONG} ＝ <b>长刀</b>：主手长刀、副手反握短刀 ✓ —— 攻击**攻速低** ✓
 *       但**左右交替攻击**（长刀优先 ✓）、每次命中 ＋1 fever ✓；**右键＝突刺** ✓（消耗 20 fever ✓、
 *       前送 ＋ 向前冲刺 3 格 ✓ 沿途所有生物吃突刺伤害 ✓ 过程无敌 ✓ 冷却 5 秒 ✓）；</li>
 *   <li>{@link #FORM_SHORT} ＝ <b>短刀</b>：主手短刀、副手反握长刀 ✓ —— 攻击**攻速高、攻击力低** ✓
 *       只在目标血量 &lt; 20% 时由**长刀尝试斩杀处决**（伤害 500% ✓）；
 *       **右键长按＝蓄力** ✓ 松手把短刀**投掷**出去 ✓ 命中处**半径 3 格非破坏爆炸** ✓ 冷却 5 秒 ✓。</li>
 * </ul>
 * ⭐ 两种形态都能用：**Shift ＋ 右键＝杀戮光环** ✓（消耗 50 fever ✓ 双手平举、快速旋转 ✓
 * 对 **1 格**范围内所有敌人造成伤害 ✓ 期间玩家无敌 ✓ 持续 **5 秒** ✓）。
 *
 * <h2>⚠ 已知冲突（如实记录 ✗）</h2>
 * 用户要求的 **F 键左右手互换**与仓库既有的「术式反转」（{@code KeyBindings.REVERSE_TECHNIQUE} 默认也是 F ✓）
 * **撞键** ✗ —— 本模组无法吞掉别的功能的按键 ✓ ⇒ 同时手持术式核心与长短刃时两个都会触发 ✓
 * （⚠ 一般不会同时手持 ✓ 故先这样 ✓ 已在回信中说明 ✓）。
 *
 * <h2>⚠ 实现说明（为什么这么多静态成员）</h2>
 * 形态与 fever 都存在**物品自己的 NBT** 上 ✓（与战镰 {@code chaos_fever} 同一套思路 ✓），
 * 只有"正在放光环的玩家"这种**瞬时状态**才放在按玩家隔离的静态集合里 ✓
 * （⚠ 战镰原先是静态 boolean ✓ 会导致多玩家互相干扰 ✓ 这里从设计上避开 ✗）。
 */
public class LongShortBladeItem extends ModifiableItem {

    /** 工具定义（名字必须与 {@code tool_definitions/long_short_blade.json} 一致 ✓） */
    public static final ToolDefinition LONG_SHORT_BLADE_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "long_short_blade"));

    // ============================================================
    //  NBT 键（⚠ 与数据侧/客户端的口径钉死 ✗）
    // ============================================================

    /** 形态：0 ＝ 长刀 ✓ 1 ＝ 短刀 ✓ */
    public static final String TAG_FORM = "lnb_form";
    /** 大招条（0~100 ✓） */
    public static final String TAG_FEVER = "lnb_fever";
    /** 突刺冷却（tick ✓ 用物品自己的冷却够了 ✗ —— 这里只用它做 HUD 显示 ✓） */
    public static final String TAG_ULTIMATE_END = "lnb_ult_end";
    /** ⭐ 副手那把"伙伴刀"的标记（⚠ 防止对它再做一次互换 ✗） */
    public static final String TAG_PAIR = "lnb_pair";

    /**
     * ⭐⭐ <b>「这一把已经补过伙伴刀了」标记</b>（⚠ 修 dupe 用 ✗ 用户实测 2026-10-09 ✓）。
     *
     * <h2>⚠ 为什么必须有它（⭐ 没有它＝印钞机 ✗）</h2>
     * ⭐ 原逻辑：⭐「主手有刀 ＋ 副手空 ⇒ **补一把**」✗ ⇒ ⚠ 玩家**把副手那把拿走** ✗
     * ⇒ ⭐ 副手又空了 ✗ ⇒ ⭐ **下一 tick 又补一把** ✓ ⇒ ⭐ 拿一把补一把 ⇒ **无限刷物品** ✗✗
     * （⭐ 用户实测原话：「**我拿出来的短刀变成了一把新的长短刀，刷了物品**」✓）。
     * <p>⇒ ⭐ 修法：⭐ **一把武器只补一次** ✗ —— ⭐ 补完就在**主手那把**上打这个标记 ✓
     * ⇒ ⭐ 以后副手再空也**不再补** ✓（⭐ 玩家想再配就自己放一把 ✓ ⭐ 那不算刷 ✓）。
     * <p>⚠ 标记写在 ⭐ **主手栈自己的 NBT** 上 ✗（⭐ 不是内存里的集合 ✓）⇒ ⭐ **重登/换维度都还在** ✓
     * （⭐ 用内存集合的话 ⭐ 重进游戏就能再刷一次 ✓）。
     */
    public static final String TAG_PAIRED_ONCE = "lnb_paired";

    /** ⭐ 这一把**是否已经补过**伙伴刀 ✓（⭐ 补过就不再补 ✗ 见 {@link #TAG_PAIRED_ONCE} ✓） */
    public static boolean isPairedOnce(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_PAIRED_ONCE);
    }

    /** ⭐ 打上"已补过"标记 ✓ */
    public static void markPairedOnce(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.getOrCreateTag().putBoolean(TAG_PAIRED_ONCE, true);
    }
    /**
     * ⭐⭐ <b>交替挥动的同步计数器</b>（值 ＝ 命中次数 ＋1 ✓ 正负号表"这次该挥哪只手"✗）。
     * <p>⚠ 为什么要这个 ✗：⭐ 原版 `LivingEntity#swing()` 有闸门 ✗ ⇒
     * 「攻击时主手已在挥」会把副手那次**吞掉** ✓；⭐ 而且**客户端**收到动画包时
     * **自己的那道闸门同样会吞** ✗（⚠ 我上一版只绕过了服务端 ✓ 所以「左手极少挥动」✗）。
     * ⇒ ⭐ 改用**物品 NBT**（会同步给客户端 ✓）当信号 ✓ ⇒ 客户端渲染层**自己播放**副手挥动 ✓，
     * **完全不经过原版 `swing()`** ✗ ⇒ 谁也吞不掉 ✓。
     */
    public static final String TAG_SWING = "lnb_swing";

    /**
     * ⭐ 记一次"该挥哪只手" ✓ —— 值 ＝ 计数器 ×2 ＋ (副手 ? 1 : 0) ✓
     * （⭐ 用**奇偶**区分手 ✓ 用**大小**区分"第几次"✗ ⇒ 客户端只要发现值变了就播一次 ✓，
     * ⚠ 即使 NBT 同步有滞后 ✓ 也不会漏掉"变过"这个事实 ✓）。
     */
    public static void bumpSwing(Player player, boolean offhand) {
        if (player == null) {
            return;
        }
        try {
            for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
                if (s.getItem() instanceof LongShortBladeItem) {
                    int cur = s.getOrCreateTag().getInt(TAG_SWING);
                    int count = cur / 2;
                    s.getOrCreateTag().putInt(TAG_SWING, count * 2 + 2 + (offhand ? 1 : 0));
                }
            }
        } catch (Throwable ignored) {
            // ⭐ 写信号失败绝不能连累玩法 ✗（大不了那一挥没播 ✓）
        }
    }

    /** ⭐ 客户端读"这一挥是挥副手吗" ✓（值的最低位 ✓） */
    public static boolean swingIsOffhand(ItemStack stack) {
        try {
            CompoundTag tag = stack == null ? null : stack.getTag();
            return tag != null && (tag.getInt(TAG_SWING) & 1) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** ⭐ 客户端读"挥动信号值" ✓（变大就代表又挥了一次 ✓ 用来看"变没变"✗ 不用管具体数 ✓） */
    public static int swingCounter(ItemStack stack) {
        try {
            CompoundTag tag = stack == null ? null : stack.getTag();
            return tag == null ? 0 : tag.getInt(TAG_SWING);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static final int FORM_LONG = 0;
    public static final int FORM_SHORT = 1;

    /** fever 上限 ✓ */
    public static final int FEVER_MAX = 100;
    /** ⭐ 每次交替攻击积攒 1 点 ✓（用户口径 ✓） */
    public static final int FEVER_PER_HIT = 1;
    /** ⭐ 突刺消耗 20 ✓ */
    public static final int FEVER_COST_THRUST = 20;
    /** ⭐ 杀戮光环消耗 50 ✓ */
    public static final int FEVER_COST_ULTIMATE = 50;

    /** 突刺冲刺距离（格 ✓ 用户口径 3 ✓） */
    public static final double THRUST_DISTANCE = 3.0D;
    /** 突刺冷却（tick ✓ 5 秒 ✓） */
    public static final int THRUST_COOLDOWN_TICKS = 100;
    /** 投掷冷却（tick ✓ 5 秒 ✓） */
    public static final int THROW_COOLDOWN_TICKS = 100;
    /** 蓄力所需最短时间（tick ✓ 松手才投掷 ✓） */
    public static final int CHARGE_TICKS_MIN = 10;
    /** 蓄力满（tick ✓） */
    public static final int CHARGE_TICKS_MAX = 25;
    /** 光环持续（tick ✓ 5 秒 ✓ 用户口径 ✓） */
    public static final int ULTIMATE_DURATION_TICKS = 100;
    /** ⭐ 处决阈值（目标血量低于 20% ✓ 用户口径 ✓） */
    public static final float EXECUTE_HP_RATIO = 0.20F;
    /** ⭐ 处决倍率（500% ✓ 用户口径 ✓） */
    public static final float EXECUTE_MULTIPLIER = 5.0F;
    /** 投掷爆炸半径（格 ✓ 用户口径 3 ✓） */
    public static final float THROW_EXPLOSION_RADIUS = 3.0F;
    /**
     * ⭐ 杀戮光环的伤害范围半径（格 ✓）。
     * <p>⚠ 口径变更史（★ 以**最新**那条为准 ✗ 别照旧注释改回去 ✓）：
     * 最初「**1 格**范围内所有敌人」✓ → 2026-10-08 改为 **3 格** ✗ → ⭐ **2026-10-09 改为 2 格** ✓。
     */
    public static final double ULTIMATE_RADIUS = 2.0D;
    /** 光环每 tick 伤害的基础倍率（⚠ 按攻击力百分比算 ✓ 免得固定值太离谱 ✗） */
    public static final float ULTIMATE_DPS_RATIO = 0.35F;

    /** ⭐ 正在放杀戮光环的玩家（按 UUID 隔离 ✓） */
    private static final Set<UUID> ULTIMATE_ACTIVE = ConcurrentHashMap.newKeySet();
    /** ⭐ 光环剩余 tick（⚠ 用 tick 递减而不是线程定时器 ✗ —— 战镰那套用了线程池 ✓ 但这里要"持续 5 秒"的连续判定 ✓） */
    private static final Map<UUID, Integer> ULTIMATE_TICKS = new ConcurrentHashMap<>();

    public LongShortBladeItem(Properties properties) {
        super(properties, LONG_SHORT_BLADE_DEFINITION);
    }

    // ============================================================
    //  形态与 fever（静态读写 ✓ 供 handler/HUD/客户端共用 ✓）
    // ============================================================

    public static int getForm(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? FORM_LONG : tag.getInt(TAG_FORM);
    }

    public static boolean isLong(ItemStack stack) {
        return getForm(stack) != FORM_SHORT;
    }

    public static void setForm(ItemStack stack, int form) {
        stack.getOrCreateTag().putInt(TAG_FORM, form == FORM_SHORT ? FORM_SHORT : FORM_LONG);
    }

    /** 切到另一形态 ✓ */
    public static void toggleForm(ItemStack stack) {
        setForm(stack, isLong(stack) ? FORM_SHORT : FORM_LONG);
    }

    public static boolean isPair(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_PAIR);
    }

    public static void markPair(ItemStack stack, boolean pair) {
        if (pair) {
            stack.getOrCreateTag().putBoolean(TAG_PAIR, true);
        } else if (stack.getTag() != null) {
            stack.getTag().remove(TAG_PAIR);
        }
    }

    public static int getFever(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(FEVER_MAX, tag.getInt(TAG_FEVER)));
    }

    public static void setFever(ItemStack stack, int fever) {
        stack.getOrCreateTag().putInt(TAG_FEVER, Math.max(0, Math.min(FEVER_MAX, fever)));
    }

    public static void addFever(ItemStack stack, int delta) {
        setFever(stack, getFever(stack) + delta);
    }

    /**
     * ⭐⭐ <b>把 fever 同时写进玩家两手的长短刃</b> ✓ —— ⚠ 这是修一个实测隐患 ✗：
     * 副手"伙伴刀"是 {@code main.copy()} 出来的 ✓ 它会**冻结**复制那一刻的 fever ✗，
     * 而 fever 原先**只写主手那把** ✗ ⇒ 玩家按 **F（原版交换主副手）** 之后
     * 新主手拿到的是**陈旧值** ✗ ⇒ ⭐ **fever 会突然跳变/回退** ✗（hud-side 审查时揪出来的 ✓）。
     * <p>⇒ ⭐ 从此**所有增减都必须走这个方法** ✓（加 fever、花 fever、以及创建伙伴刀时同步 ✓）。
     */
    public static void setFeverBoth(Player player, int fever) {
        if (player == null) {
            return;
        }
        try {
            ItemStack main = player.getMainHandItem();
            if (main.getItem() instanceof LongShortBladeItem) {
                setFever(main, fever);
            }
            ItemStack off = player.getOffhandItem();
            if (off.getItem() instanceof LongShortBladeItem) {
                setFever(off, fever);
            }
        } catch (Throwable ignored) {
            // 写 fever 失败绝不能连累玩法 ✗
        }
    }

    /** ⭐ 以主手（或副手）那把为准，读出当前 fever ✓（两手不一致时取**较大**值 ✓ 只用于同步 ✓） */
    public static int readFeverBoth(Player player) {
        int v = 0;
        try {
            ItemStack main = player.getMainHandItem();
            if (main.getItem() instanceof LongShortBladeItem) {
                v = Math.max(v, getFever(main));
            }
            ItemStack off = player.getOffhandItem();
            if (off.getItem() instanceof LongShortBladeItem) {
                v = Math.max(v, getFever(off));
            }
        } catch (Throwable ignored) {
        }
        return v;
    }

    /**
     * ⭐⭐ <b>写"旋转还剩多少 tick"的**客户端可见**信号</b> ✓（= 物品 NBT {@link #TAG_ULTIMATE_END} ✓）。
     * <p>⚠ 为什么不用服务端的静态集合 ✗：⭐ **客户端看不到它** ✗（hud-side 审查时专门提醒过 ✓）
     * —— ⭐ 而**手持物品的 NBT 会同步给客户端** ✓ ⇒ 客户端渲染器读它就知道"该转起来了" ✓，
     * 因此**不需要网络包** ✓。
     */
    public static void setUltimateVisualBoth(Player player, int ticks) {
        if (player == null) {
            return;
        }
        try {
            for (ItemStack s : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
                if (s.getItem() instanceof LongShortBladeItem) {
                    if (ticks <= 0) {
                        if (s.getTag() != null) {
                            s.getTag().remove(TAG_ULTIMATE_END);
                        }
                    } else {
                        s.getOrCreateTag().putInt(TAG_ULTIMATE_END, ticks);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 写信号失败绝不能连累玩法 ✗（只是不转而已 ✓）
        }
    }

    /**
     * ⭐ <b>客户端读"该不该旋转"</b> ✓ —— 只要 {@link #TAG_ULTIMATE_END} **大于 0** 就转 ✓。
     * <p>⚠ 刻意**不看精确剩余值** ✗（物品 NBT 的同步不是每 tick ✓ 会滞后 ✓）⇒ 只看"是否 > 0" ✓。
     * <p>⚠ 客户端与服务端**共用**这个方法 ✓ —— 两端都只读物品栈 ✓ 没有任何跨端状态 ✓。
     */
    public static boolean isUltimateVisual(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof LongShortBladeItem)) {
                return false;
            }
            CompoundTag tag = stack.getTag();
            return tag != null && tag.getInt(TAG_ULTIMATE_END) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 正在放光环吗 ✓ */
    public static boolean isUltimateActive(UUID id) {
        return id != null && ULTIMATE_ACTIVE.contains(id);
    }

    /** 光环剩余 tick（没在放返回 0 ✓） */
    public static int ultimateTicksLeft(UUID id) {
        Integer v = ULTIMATE_TICKS.get(id);
        return v == null ? 0 : v;
    }

    /**
     * ⭐ 写光环剩余 tick ✓（供 {@code LongShortBladeHandler.tickUltimate} 逐 tick 递减 ✓
     * —— ⚠ 映射本身是私有的 ✗ 只有经由这个方法才改得到 ✓ 免得别处乱写 ✗）。
     */
    public static void setUltimateTicks(UUID id, int ticks) {
        if (id == null) {
            return;
        }
        if (ticks <= 0) {
            ULTIMATE_TICKS.remove(id);
        } else {
            ULTIMATE_TICKS.put(id, ticks);
        }
    }

    // ============================================================
    //  右键
    // ============================================================

    /**
     * 右键分派 ✓：
     * <ul>
     *   <li>⭐ <b>Shift ＋ 右键</b> ⇒ 杀戮光环（fever ≥ 50 ✓）；</li>
     *   <li>⭐ <b>长刀形态</b> ⇒ 突刺（fever ≥ 20 ✓ 且不在冷却 ✓）；</li>
     *   <li>⭐ <b>短刀形态</b> ⇒ 开始蓄力（松手投掷 ✓ 见 {@code releaseUsing} ✓）。</li>
     * </ul>
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // ⭐ Shift ＋ 右键 ⇒ 杀戮光环（用户口径 ✓）
        if (player.isShiftKeyDown()) {
            if (getFever(stack) >= FEVER_COST_ULTIMATE && !isUltimateActive(player.getUUID())) {
                // ⭐ 耐久消耗（用户口径 ✓）：⭐ 光环扣 {@link #DURABILITY_ULTIMATE} 点 ✓
                spendDurability(stack, DURABILITY_ULTIMATE, player);
                startUltimate(level, player, stack);
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (isLong(stack)) {
            // ⭐ 长刀：突刺（前送 ＋ 冲刺 3 格 ✓ 沿途伤害 ✓ 无敌 ✓ 冷却 5s ✓）
            if (getFever(stack) < FEVER_COST_THRUST) {
                return InteractionResultHolder.fail(stack);
            }
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }
            setFeverBoth(player, getFever(stack) - FEVER_COST_THRUST);
            // ⭐ 耐久消耗（用户口径 ✓）：⭐ 突刺扣 {@link #DURABILITY_THRUST} 点 ✓
            spendDurability(stack, DURABILITY_THRUST, player);
            thrust(level, player, stack);
            player.getCooldowns().addCooldown(this, THRUST_COOLDOWN_TICKS);
            return InteractionResultHolder.success(stack);
        }

        // ⭐ 短刀：长按蓄力 ⇒ 松手投掷
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ============================================================
    //  ⭐ 技能耐久消耗（用户口径 ✓ 2026-10-09：「给我的几个技能效果都补上耐久消耗」✓）
    // ============================================================

    /** ⭐ 突刺：扣 2 点 ✓ */
    public static final int DURABILITY_THRUST = 2;
    /** ⭐ 杀戮光环：扣 10 点 ✓（⭐ 一次性 ✓ 100 tick 内持续输出 ✓） */
    public static final int DURABILITY_ULTIMATE = 10;
    /** ⭐ 斩杀处决：扣 3 点 ✓ */
    public static final int DURABILITY_EXECUTE = 3;
    /** ⭐ 短刀投掷：扣 2 点 ✓ */
    public static final int DURABILITY_THROW = 2;

    /**
     * ⭐⭐ <b>扣工具耐久</b>（用户口径 ✓ 2026-10-09：「**给我的几个技能效果都补上耐久消耗**」✓）。
     *
     * <h2>⚠ 必须走匠魂自己的 API ✗ 不能自己减 NBT ✓</h2>
     * ⭐ `ToolDamageUtil.damage(tool, n, holder, stack)` ✓ 会自动尊重
     * 「**不毁**」强化／`isUnbreakable`／**已损坏**状态 ✓ 并正确处理**工具损坏那一刻**的逻辑
     * （⭐ 本仓 `FlyingSwordCuriosHandler`、`YoYoEntity` 都是这么扣的 ✓ —— 照现成的来 ✓）；
     * ⚠ 自己写 `tag.putInt("Damage", …)` 会**绕过**这些 ⇒ 强化失效 ✗ 甚至把工具扣坏 ✗。
     *
     * <p>⚠ 任何异常都**不能**抛出去 ✗（本类的调用点在技能入口 ✓ 抛出去会连累整个使用流程 ✓）。
     *
     * @param stack  工具栈 ✓（⭐ 不是手里的那把也没关系 ✓ 只要能从它取到 `ToolStack` ✓）
     * @param amount 扣几点耐久 ✓
     * @param holder 持有者 ✓（⭐ 可空 ✓ 用于部分强化的触发 ✓）
     */
    public static void spendDurability(ItemStack stack, int amount,
                                      @javax.annotation.Nullable net.minecraft.world.entity.LivingEntity holder) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return;
        }
        try {
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || tool.isBroken()) {
                return;
            }
            slimeknights.tconstruct.library.tools.helper.ToolDamageUtil.damage(tool, amount, holder, stack);
        } catch (Throwable ignored) {
            // ⭐ 扣耐久失败绝不能连累技能 ✗
        }
    }

    /** 蓄力用的"使用时长"上限（松手才会走 {@link #releaseUsing} ✓） */
    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // 短刀蓄力：用"弓"的拉弦姿势最接近"蓄力"的手感 ✓（本仓战镰用的是别的姿势 ✗）
        return UseAnim.BOW;
    }

    /**
     * ⭐ 松手 ⇒ 把短刀投掷出去 ✓（用户口径 ✓）——
     * 蓄力不足（&lt; {@link #CHARGE_TICKS_MIN}）直接取消 ✓ 免得点一下就扔 ✗。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        int charged = getUseDuration(stack) - timeLeft;
        if (charged < CHARGE_TICKS_MIN || isLong(stack)) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return;
        }
        float power = Math.min(1.0F, (float) charged / (float) CHARGE_TICKS_MAX);
        player.getCooldowns().addCooldown(this, THROW_COOLDOWN_TICKS);
        // ⭐ 耐久消耗（用户口径 ✓）：⭐ 投掷扣 {@link #DURABILITY_THROW} 点 ✓
        spendDurability(stack, DURABILITY_THROW, player);
        LongShortBladeHandler.throwShortBlade(player, stack, power);
    }

    // ============================================================
    //  突刺
    // ============================================================

    /**
     * ⭐ <b>突刺</b>：长刀前送 ＋ 向面朝方向冲刺 {@link #THRUST_DISTANCE} 格 ✓
     * 沿途**所有生物**受到突刺伤害 ✓ 过程**无敌** ✓。
     * <p>⚠ 位移照战镰那套"逐段试探安全落点"的做法 ✓（{@code FeverHandler} 已实证可用 ✓）——
     * 撞墙就停在最后的安全点 ✓ 而不是穿墙 ✗。
     */
    public static void thrust(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
        if (dir.lengthSqr() < 0.01D) {
            dir = new Vec3(0, 0, 1);
        }

        ToolStack tool = ToolHelper.getToolStack(stack);
        float base = tool == null ? 10.0F : Math.max(1.0F, tool.getStats().get(ToolStats.ATTACK_DAMAGE));
        float thrustDamage = base * 1.6F;   // ⭐ 突刺一击比普通挥砍重 ✓

        // ⭐ 沿途伤害：先按"路径上的所有生物"扫一遍再位移 ✓（⚠ 位移后再扫会漏掉起手位置 ✓）
        Vec3 start = player.position();
        Vec3 end = start.add(dir.scale(THRUST_DISTANCE));
        AABB sweep = new AABB(start, end).inflate(1.2D);
        List<LivingEntity> hit = serverLevel.getEntitiesOfClass(LivingEntity.class, sweep,
                e -> e != player && e.isAlive());
        for (LivingEntity target : hit) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), thrustDamage);
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    1, 0, 0, 0, 0);
        }

        // ⭐⭐ **位移改成"分几 tick 冲"**（用户口径 ✓ 2026-10-09：「**现在突刺看上去是瞬移**」✓）
        //   ⚠ 原来是 ⭐ 一次 `teleportTo` 硬移 3 格 ✗ ⇒ ⭐ 一帧到位 ⇒ **看着就是瞬移** ✗
        //   ⇒ ⭐ 现在只**登记冲刺**（方向 ＋ 剩余 tick ✓）✓
        //     由 {@code LongShortBladeHandler#onPlayerTick} 每 tick 调 {@link #tickThrust} ✓
        //     ⭐ 每 tick 走 `THRUST_DISTANCE / THRUST_TICKS` 格 ⇒ ⭐ 5 tick（0.25 秒）冲完 ✓
        //     同时 ⭐ 每 tick 仍用 {@link #safeStep 逐段试探} ✓ ⇒ ⭐ 照样不穿墙 ✓。
        DASH_DIR.put(player.getUUID(), dir);
        DASH_LEFT.put(player.getUUID(), THRUST_TICKS);
        // ⭐ 起手就先走一小步 ✓（⭐ 不然要等一 tick 才动 ⇒ 手感发滞 ✗）
        stepThrust(serverLevel, player);
        // ⭐ 突刺过程无敌（用户口径 ✓）—— ⚠ 覆盖整个冲刺过程 ＋ 一点余量 ✓
        InvulnerabilityManager.applyInvulnerability(player, THRUST_TICKS + 8);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    // ============================================================
    //  ⭐ 突刺冲刺（分 tick 位移 ✓ 见 {@link #thrust} ✓）
    // ============================================================

    /** ⭐ 冲刺持续几 tick ✓（5 tick ＝ 0.25 秒 ✓ 够看出"冲过去"又不拖沓 ✓） */
    public static final int THRUST_TICKS = 5;

    /**
     * ⭐ 冲刺**每 tick 的水平速度**（格/tick ✓ 用户口径 ✓「做成给玩家**向前的推力**」✓）。
     * <p>⚠ 不是"总距离 ÷ tick 数"那种**硬移** ✗ —— ⭐ 给速度之后玩家会**自己滑一段** ✗
     * （⭐ 原版有地面摩擦 ✓ 而且速度每 tick 被重设 ⇒ ⭐ 实测位移 ≈ `速度 × tick 数` ✓）
     * ⇒ ⭐ 取 ⭐ **0.75** ⇒ ⭐ 5 tick 约 **3.7 格** ✓ 与 {@link #THRUST_DISTANCE} 那 3 格相符 ✓。
     * ⚠ 觉得冲太远/太近 ⇒ ⭐ 只改这一个数 ✓。
     */
    public static final double THRUST_SPEED = 0.75D;

    /** ⭐ 正在冲刺的玩家 ⇒ 方向 ✓ */
    private static final java.util.Map<java.util.UUID, Vec3> DASH_DIR =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 正在冲刺的玩家 ⇒ 还剩几 tick ✓ */
    private static final java.util.Map<java.util.UUID, Integer> DASH_LEFT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 正在突刺冲刺吗 ✓（⭐ 供处理器/HUD 判定 ✓） */
    public static boolean isDashing(Player player) {
        return player != null && DASH_LEFT.getOrDefault(player.getUUID(), 0) > 0;
    }

    /**
     * ⭐ 每 tick 推进一步 ✓ —— 由 {@code LongShortBladeHandler#onPlayerTick} 调 ✓。
     * <p>⚠ 全程都过 {@link #safeStep} ✗ —— ⭐ 冲刺**不能**变成穿墙 ✓。
     */
    public static void tickThrust(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) {
            return;
        }
        int left = DASH_LEFT.getOrDefault(player.getUUID(), 0);
        if (left <= 0) {
            return;
        }
        if (left - 1 <= 0) {
            DASH_LEFT.remove(player.getUUID());
            DASH_DIR.remove(player.getUUID());
        } else {
            DASH_LEFT.put(player.getUUID(), left - 1);
        }
        stepThrust(player.serverLevel(), player);
    }

    /**
     * ⭐ 每 tick 推进一步（⭐ **给速度，不硬移** ✓ —— 用户口径 ✓ 2026-10-09：
     * 「**把突刺做成给玩家向前的推力，而不是瞬移**」✓）。
     *
     * <h2>⚠ 为什么改成"给速度"（⭐ 而且必须在这里给 ✗）</h2>
     * ⚠ 原实现是 ⭐ `player.teleportTo(...)` 硬移 ✗ ⇒ ⭐ 即使分 5 tick，位移也是**一帧一跳** ✗
     * ⇒ 客户端插值出来仍偏"瞬移"✓ ⇒ ⭐ 改成 ⭐ **`setDeltaMovement(向前速度)`** ✓
     * ⭐ 让引擎自己带着玩家走 ✓（⭐ 碰撞/台阶/流体都由原版处理 ✓ 更自然 ✓）。
     * <p>⚠⚠ **时机很重要** ✗：⭐ 玩家的输入会在 ⭐ `Player#travel()` 里**重设速度** ✗
     * ⇒ ⭐ 必须在那**之后**给速度才不会被覆盖 ✓ —— ⭐ 本方法由
     * `LongShortBladeHandler#onPlayerTick` 在 ⭐ **`TickEvent.Phase.END`** 调用 ✓ ⇒ ⭐ 正好在移动之后 ✓
     * ⇒ ⭐ 速度会在**下一 tick** 生效 ✓（⭐ 手感是"持续加速"而非"弹射" ✓）。
     * <p>⚠ 还要 ⭐ `hurtMarked = true` ✗ ⇒ ⭐ 否则服务端改了速度**不告诉客户端** ✗
     * ⭐ 客户端就会位置回弹 ✓（⭐ 经典坑 ✓）。
     */
    private static void stepThrust(ServerLevel level, Player player) {
        Vec3 dir = DASH_DIR.get(player.getUUID());
        if (dir == null) {
            return;
        }
        // ⚠ 前方不安全 ⇒ ⭐ 提前结束冲刺 ✓（⭐ 撞墙就停 ✓ 不硬顶 ✓）
        if (!isSafe(player, player.getX() + dir.x * 0.7D, player.getZ() + dir.z * 0.7D, player.getY())) {
            DASH_LEFT.remove(player.getUUID());
            DASH_DIR.remove(player.getUUID());
            return;
        }
        // ⭐ 给向前的速度 ✓（⭐ Y 用**原本的** ✗ 免得把下落/跳跃顶掉 ✓）
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(dir.x * THRUST_SPEED, motion.y, dir.z * THRUST_SPEED);
        // ⭐ 让客户端也接受这个速度 ✓（⭐ 不写这句客户端会回弹 ✗）
        player.hurtMarked = true;
        player.fallDistance = 0.0F;

        // ⭐⭐ **突刺残影**（用户口径 ✓ 2026-10-09）：
        //   「**服务端在突进过程中每2tick向周围能看到该实体的玩家广播一次当前的位置和朝向**」✓
        //   ⭐ 用 `broadcastAndSend` ✗ —— 它的语义正好是"**追踪该实体的玩家**" ✓
        //     （⭐ 也就是"能看到它的" ✓ ⭐ 不用自己算距离/视锥 ✓）。
        if (player.tickCount % 2 == 0) {
            com.mofengbaizhi.tinkersnewlife.network.tools.PacketThrustGhost.broadcast(level, player);
        }
    }

    /** 逐 0.5 格试探 ✓ 撞到实心方块就停在最后安全点 ✓（照 {@code FeverHandler} 那套 ✓） */
    private static Vec3 safeStep(Player player, Vec3 dir, double distance) {
        double step = 0.5D;
        double lastX = player.getX();
        double lastZ = player.getZ();
        boolean moved = false;
        for (double d = step; d <= distance + 1.0E-6; d += step) {
            double x = player.getX() + dir.x * d;
            double z = player.getZ() + dir.z * d;
            if (!isSafe(player, x, z, player.getY())) {
                break;
            }
            lastX = x;
            lastZ = z;
            moved = true;
        }
        return moved ? new Vec3(lastX, player.getY(), lastZ) : null;
    }

    private static boolean isSafe(Player player, double x, double z, double y) {
        var level = player.level();
        net.minecraft.core.BlockPos foot = new net.minecraft.core.BlockPos(
                net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(y), net.minecraft.util.Mth.floor(z));
        return !level.getBlockState(foot).isSolid() && !level.getBlockState(foot.above()).isSolid();
    }

    // ============================================================
    //  杀戮光环
    // ============================================================

    /**
     * ⭐ <b>杀戮光环</b>（Shift ＋ 右键 ✓ 消耗 50 fever ✓）：
     * 双手平举、刀刃持平、快速旋转 ✓ 对 **1 格**范围内所有敌人持续伤害 ✓ 期间**玩家无敌** ✓ 持续 **5 秒** ✓。
     * <p>⚠ 逐 tick 结算（见 {@code LongShortBladeHandler.tickUltimate} ✓）——
     * 这样"持续 5 秒"是真实时长 ✓ 且能随玩家移动 ✓（⚠ 用线程定时器那套做不到 ✗）。
     */
    public static void startUltimate(Level level, Player player, ItemStack stack) {
        setFeverBoth(player, getFever(stack) - FEVER_COST_ULTIMATE);
        // ⭐⭐ **旋转用的同步信号**：把"还剩多少 tick"写进**物品 NBT** ✓
        //   —— ⚠ 客户端**看不到**服务端的静态集合（hud-side 专门提醒过 ✗）⇒
        //   ⭐ 而**手持物品的 NBT 是同步给客户端的** ✓ ⇒ 客户端渲染器只需读它就能知道"该转了" ✓
        //   （⭐ 这也是 {@link #TAG_ULTIMATE_END} 当初声明却一直没用的原因 —— 现在用上了 ✓）。
        //   ⚠ 判断只依赖"**是否 > 0**" ✗ 不依赖精确剩余值 ✓（物品 NBT 的同步不是每 tick ✓ 会滞后 ✓）。
        setUltimateVisualBoth(player, ULTIMATE_DURATION_TICKS);
        UUID id = player.getUUID();
        ULTIMATE_ACTIVE.add(id);
        ULTIMATE_TICKS.put(id, ULTIMATE_DURATION_TICKS);
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 1.4F, 0.7F);
        }
    }

    /** 结束光环 ✓ */
    public static void stopUltimate(UUID id) {
        ULTIMATE_ACTIVE.remove(id);
        ULTIMATE_TICKS.remove(id);
    }

    /** 长刀：攻速降低（用户口径「此时攻击速度较低」✓） */
    private static final UUID LONG_SPEED_UUID = UUID.fromString("7a1c9e30-1111-4a01-9c01-1a2b3c4d5e01");
    /** 长刀：实体交互距离加成（图鉴那句「长刀：攻击范围更大」✓ 与本类的"攻速低"并不矛盾 ✓ 两者都成立 ✓） */
    private static final UUID LONG_REACH_UUID = UUID.fromString("7a1c9e30-2222-4a02-9c02-1a2b3c4d5e02");
    /** 短刀：攻速提高（用户口径「攻击速度较高」✓） */
    private static final UUID SHORT_SPEED_UUID = UUID.fromString("7a1c9e30-3333-4a03-9c03-1a2b3c4d5e03");
    /** 短刀：攻击力降低（用户口径「但攻击力较低」✓） */
    private static final UUID SHORT_DAMAGE_UUID = UUID.fromString("7a1c9e30-4444-4a04-9c04-1a2b3c4d5e04");

    /** 长刀攻速修正（负值 ＝ 更慢 ✓） */
    public static final double LONG_ATTACK_SPEED_DELTA = -0.6D;
    /** 长刀触及距离加成（格 ✓） */
    public static final double LONG_ENTITY_REACH_BONUS = 1.0D;
    /** 短刀攻速修正（正值 ＝ 更快 ✓） */
    public static final double SHORT_ATTACK_SPEED_DELTA = 0.8D;
    /** 短刀攻击力修正（负值 ＝ 更低 ✓） */
    public static final double SHORT_ATTACK_DAMAGE_DELTA = -2.0D;

    /**
     * ⭐ <b>按形态给属性</b> ✓（用户口径 ✓）：
     * <ul>
     *   <li><b>长刀</b> ⇒ 攻击速度**降低** {@link #LONG_ATTACK_SPEED_DELTA} ✓ 且**实体交互距离 ＋1 格** ✓；</li>
     *   <li><b>短刀</b> ⇒ 攻击速度**提高** {@link #SHORT_ATTACK_SPEED_DELTA} ✓ 且**攻击力降低** {@link #SHORT_ATTACK_DAMAGE_DELTA} ✓。</li>
     * </ul>
     * ⚠ 两种形态用**不同的 UUID** ✗ —— 免得原版把上一次算出来的修饰符缓存住 ✓（换形态后属性不刷新 ✗）。
     * <p>⚠ 实测提醒（本仓 §807 的教训 ✓）：⭐ Forge 1.20.1 里"触及距离"的正确属性名是
     * {@code forge:entity_reach} ✗ **不是** {@code forge:reach_distance} ✓（后者不存在 ⇒ 静默不生效 ✗）。
     */
    @Override
    public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute,
            net.minecraft.world.entity.ai.attributes.AttributeModifier> getAttributeModifiers(
            net.minecraft.world.entity.EquipmentSlot slot, ItemStack stack) {
        var map = com.google.common.collect.ArrayListMultimap
                .<net.minecraft.world.entity.ai.attributes.Attribute,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier>create(
                        super.getAttributeModifiers(slot, stack));
        if (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND) {
            return map;
        }
        try {
            if (isLong(stack)) {
                map.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                LONG_SPEED_UUID, "Long Blade Speed",
                                LONG_ATTACK_SPEED_DELTA,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                map.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                LONG_REACH_UUID, "Long Blade Reach",
                                LONG_ENTITY_REACH_BONUS,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            } else {
                map.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                SHORT_SPEED_UUID, "Short Blade Speed",
                                SHORT_ATTACK_SPEED_DELTA,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
                map.put(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                SHORT_DAMAGE_UUID, "Short Blade Damage",
                                SHORT_ATTACK_DAMAGE_DELTA,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            }
        } catch (Throwable ignored) {
            // ⚠ 属性加成失败绝不能连累物品能用 ✗
        }
        return map;
    }

    // ============================================================
    //  提示（⚠ 用户硬规矩：没说就不加 tooltip ✗ ⇒ 这里**不加** appendHoverText ✓）
    //   ⚠ fever 走 HUD 显示（用户点名 ✓ 见 LongShortBladeHud ✓）。
    // ============================================================
}
