package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShortBladeThrowEntity;
import com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.InvulnerabilityManager;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.List;
import java.util.UUID;

/**
 * ⭐ §1124 <b>长短刃的总处理器</b>（用户口径 ✓）。
 *
 * <h2>它负责什么</h2>
 * <ol>
 *   <li>⭐ <b>F 键左右手互换</b>（{@link #requestSwap} ✓ 由客户端按键经网络包调进来 ✓）；</li>
 *   <li>⭐ <b>副手"伙伴刀"维护</b>（{@link #tick} ✓）—— 手持期间**主副手各一把** ✓
 *       哪把在主手由形态决定 ✓（用户口径 ✓）；</li>
 *   <li>⭐ <b>交替攻击与 fever</b>（{@link #onHurt} ✓）：每次命中 ＋1 fever ✓，
 *       ⭐ 长刀形态下**长刀优先、短刀交替**（本模组用"奇偶击"实现 ✓）；</li>
 *   <li>⭐ <b>斩杀处决</b>（同 {@link #onHurt} ✓）：短刀形态且目标血量 &lt; 20% ⇒ 长刀出手 ✓ 伤害 500% ✓；</li>
 *   <li>⭐ <b>杀戮光环逐 tick 结算</b>（{@link #tick} ✓）：1 格范围持续伤害 ✓ 玩家无敌 ✓ 5 秒 ✓；</li>
 *   <li>⭐ <b>短刀投掷</b>（{@link #throwShortBlade} ✓）：生成投射物 ✓ 命中后由投射物自己放**半径 3 格非破坏爆炸** ✓。</li>
 * </ol>
 *
 * <h2>⚠ 刻意做的三件事（都是从本仓既有事故里学的 ✓）</h2>
 * <ul>
 *   <li>⚠ <b>不吞别的功能的按键</b> ✗ —— F 键与「术式反转」撞键 ✓ 本处理器**只管自己这把武器** ✓；</li>
 *   <li>⚠ <b>不用线程定时器</b> ✗ —— 光环走 {@code PlayerTickEvent} 递减 ✓（战镰那套线程池虽可用 ✓
 *       但"持续 5 秒"用 tick 更精确也更安全 ✓）；</li>
 *   <li>⚠ <b>状态按玩家隔离</b> ✓（{@code UUID} 集合 ✓ 不是静态 boolean ✗）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LongShortBladeHandler {

    private LongShortBladeHandler() {
    }

    /** ⭐ 交替攻击的奇偶计数（按玩家 ✓ 长刀优先/短刀交替用 ✓） */
    private static final java.util.Map<UUID, Boolean> NEXT_IS_LONG =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ============================================================
    //  ① F 键互换（客户端按键 → 网络包 → 这里 ✓）
    // ============================================================

    /**
     * ⭐ <b>左右手互换</b>（用户口径 ✓）：「按下 F 左右手互换，长刀反握，短刀手持」✓
     * <p>做法：把**两手的形态对调** ✓ —— 主手那把切成另一形态 ✓ 副手那把同步成相反形态 ✓
     * （⚠ 只允许在**手持长短刃**时生效 ✗ 且对"伙伴刀"不再二次互换 ✓）。
     */
    public static void requestSwap(ServerPlayer player) {
        try {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            boolean mainIsBlade = main.getItem() instanceof LongShortBladeItem;
            boolean offIsBlade = off.getItem() instanceof LongShortBladeItem;
            if (!mainIsBlade && !offIsBlade) {
                return;   // ⭐ 没拿这把武器 ⇒ 什么都不做 ✓（F 让给别的功能 ✓）
            }
            // ⭐ 两手交换物品（真正的"左右手互换"✓）
            if (mainIsBlade && offIsBlade) {
                LongShortBladeItem.toggleForm(main);
                LongShortBladeItem.setForm(off, LongShortBladeItem.isLong(main)
                        ? LongShortBladeItem.FORM_SHORT : LongShortBladeItem.FORM_LONG);
            } else if (mainIsBlade) {
                LongShortBladeItem.toggleForm(main);
            } else {
                LongShortBladeItem.toggleForm(off);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON,
                    SoundSource.PLAYERS, 0.8F, 1.4F);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[长短刃] 互换失败（不影响游戏）：{}", t.toString());
        }
    }

    // ============================================================
    //  ② 每 tick：伙伴刀 ＋ 光环
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // ⭐ 突刺冲刺：分 tick 位移（⚠ 用户口径 ✗「**现在突刺看上去是瞬移**」✓ ⇒ 改成每 tick 走一步 ✓）
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer dashPlayer) {
            LongShortBladeItem.tickThrust(dashPlayer);
        }
        onPlayerTick0(event);
    }

    private static void onPlayerTick0(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        try {
            maintainPair(player);
            tickUltimate(player);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[长短刃] 每 tick 逻辑异常（不影响游戏）：{}", t.toString());
        }
    }

    /**
     * ⭐ <b>维持"主副手各一把"</b> ✓（用户口径 ✓）——
     * 玩家任意一手拿着长短刃 ⇒ 另一手补一把**同物品的伙伴刀**（NBT {@code lnb_pair} ✓ 形态相反 ✓）；
     * 两手都没拿 ⇒ 把伙伴刀清掉 ✓（⚠ 免得留在背包里变成两把 ✗）。
     * <p>⚠ 只动"伙伴刀"（有 {@code lnb_pair} 标记的 ✓），**绝不动玩家自己的物品** ✗。
     */
    private static void maintainPair(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean mainIsBlade = main.getItem() instanceof LongShortBladeItem;
        boolean offIsBlade = off.getItem() instanceof LongShortBladeItem;

        // ⭐⭐⚠⚠ **先清"跑到别处的伙伴刀"**（用户建议 ✓ 2026-10-09：
        //   「**我的建议是短刀不要做真物品或者干脆无法被移动**」✓）
        //   ⭐ 采用"**无法被移动**"这一半 ✗（⭐ 不做成"非真物品"因为那要重写副手攻击逻辑 ✓ 风险太高 ✓）：
        //   ⇒ ⭐ 伙伴刀（带 `lnb_pair` 标记 ✓）**只允许待在副手** ✗
        //     ⭐ 一旦被挪进背包/快捷栏/丢出去 ⇒ ⭐ **下一 tick 立刻清掉** ✓
        //   ⇒ ⭐ 玩家视角就是"**拿不走**" ✓ ✓（⭐ 也彻底断了"拿一把补一把"的路 ✓）。
        //   ⚠ **必须在最前面** ✗ —— ⭐ 否则下面那些分支会先 return ✗ 就漏掉了 ✓。
        purgeStrayPairs(player, off);

        // ⭐⭐ 主手**不是**长短刃 ⇒ ⭐ **伙伴刀必须消失** ✗
        //   ⚠ 用户实测（2026-10-08 ✓）：「第一次拿在手里时确实可以在副手填充另一把刀，
        //     但是**切换物品栏时另一把刀不会消失**」✗ —— 根因就是我原先写的是
        //     "主手空但副手是刀 ⇒ 只把 `lnb_pair` 标记摘掉 ✗ **不删**" ✓
        //     （⚠ 当时怕误删玩家自己的刀 ✗）⇒ 结果伙伴刀**永远留在副手** ✗。
        //   ⇒ ⭐ 正确规则：**伙伴刀只在"手持期间"存在** ✓ —— 但**只清带 `lnb_pair` 标记的那把** ✓
        //     ⚠ **玩家自己放进去的刀（无标记）绝不碰** ✗（这是当时那个顾虑的正确解法 ✓）。
        //   ⭐ 并且顺手扫一遍**背包 36 格** ✗ —— 万一伙伴刀被玩家挪进背包（或换手时挤进去 ✓），
        //     也不能让它变成"白送的第二把" ✗。
        if (!mainIsBlade) {
            removePairs(player, off);
            return;
        }

        // ⭐⭐ 两手都是刀 ⇒ 这是**玩家按 F（原版交换主副手）之后的常态** ✓
        //   ⇒ ⭐ 只**同步形态** ✓ **绝不新建伙伴刀** ✗（⚠ 早先写成"副手不是伙伴刀就补一把" ✗
        //      ⇒ 按一次 F 就会多出一把 ✓ **刷物品** ✗✗ —— 用户点出 F 是原版键位时一并修掉 ✓）。
        if (offIsBlade) {
            int wantOff = LongShortBladeItem.isLong(main)
                    ? LongShortBladeItem.FORM_SHORT : LongShortBladeItem.FORM_LONG;
            if (LongShortBladeItem.getForm(off) != wantOff) {
                LongShortBladeItem.setForm(off, wantOff);
            }
            return;
        }

        // ⭐⭐ 主手是刀、副手空 ⇒ 补一把伙伴刀（形态相反 ✓）—— ⚠⚠ **但一把只补一次** ✗✗
        //   ⚠ 用户实测 dupe（2026-10-09 ✓）：「**当我在背包中试图拿走副手的短刀时，
        //     新的短刀被填充进副手，我拿出来的短刀变成了一把新的长短刀，刷了物品**」✓
        //   ⇒ ⭐ 根因就是**这里不加限制** ✗：⭐ 把副手那把拿走 ⇒ ⭐ 副手空 ⇒ ⭐ 下一 tick **又补一把** ✓
        //     ⇒ ⭐ 拿一把补一把 ⇒ **无限刷** ✓。
        //   ⇒ ⭐ 修法：⭐ 补之前先看**主手那把**有没有 {@link LongShortBladeItem#TAG_PAIRED_ONCE} 标记 ✗
        //     ⭐ 补完立刻打上 ✓ ⇒ ⭐ 以后副手再空也**不补** ✓（⭐ 想再配就自己放 ✓ 那不算刷 ✓）。
        if (off.isEmpty()) {
            ItemStack pair = main.copy();
            pair.setCount(1);
            LongShortBladeItem.markPair(pair, true);
            LongShortBladeItem.setForm(pair, LongShortBladeItem.isLong(main)
                    ? LongShortBladeItem.FORM_SHORT : LongShortBladeItem.FORM_LONG);
            player.setItemInHand(InteractionHand.OFF_HAND, pair);
            // ⚠⚠ **刻意不打 `TAG_PAIRED_ONCE` 标记**（用户口径 ✓ 2026-10-09：
            //   「**你直接改成短刀没手持消失，然后可以补不就行了**」✓）
            //   ⭐ 用户的方案更对 ✗：⭐ **"离开副手就销毁" ＋ "空了就补"** ✓
            //   ⇒ ⭐ 拿走的那把**立刻没了** ✗ ⭐ 补的是**唯一**一把 ✓ ⇒ ⭐ **总量不变 ⇒ 不会刷** ✓
            //   ⚠ 而我上一版加的"只补一次"标记 ✗ ⭐ 会让玩家**再也补不出来** ✓（⭐ 那是另一个极端 ✓）
            //   ⇒ ⭐ 已**取消**那个标记 ✓（⭐ 常量保留但**不再使用** ✓ 见 §1169 ✓）。
        }
        // ⚠ 副手有别的东西 ⇒ 不动它 ✗（玩家自己放的东西优先 ✓）
    }

    /**
     * ⭐⭐ <b>清掉"跑到副手以外"的伙伴刀</b>（用户建议 ✓「**干脆无法被移动**」✓）——
     * ⭐ 伙伴刀（带 {@code lnb_pair} 标记 ✓）**只允许待在副手** ✗：
     * ⭐ 背包 36 格（⭐ 含快捷栏 ✓）里凡是带标记的 ⇒ ⭐ **一律清掉** ✓。
     *
     * <h2>⚠ 为什么这能代替"做成非真物品"</h2>
     * ⭐ 玩家想"拿走"伙伴刀 ⇒ ⭐ 它必然会进背包/被丢出 ✗ ⇒ ⭐ **下一 tick 就没了** ✓
     * ⇒ ⭐ 手感上就是 ⭐ **拿不走** ✓ ✓（⚠ 而且它**不会被复制** ✗ ⭐ 因为复制品也带标记 ⇒ ⭐ 也一起清 ✓）。
     *
     * <p>⚠ **只清带标记的** ✗ —— ⭐ 玩家自己配的真刀（无标记 ✓）**一把都不动** ✓。
     */
    private static void purgeStrayPairs(ServerPlayer player, ItemStack off) {
        try {
            var inv = player.getInventory();
            // ⚠⚠ **手上那两把绝不能清** ✗✗ —— ⭐ 我 §1169 只跳过了副手 ✗
            //   ⇒ ⚠ 用户实测（2026-10-09 ✓）：「**左右手交换时短刀在主手会消失**」✓
            //   ⇒ ⭐ 根因：⭐ 按 F 之后伙伴刀到了**主手** ✗ ⭐ 而主手就是
            //     ⭐ **快捷栏的当前选中格**（`Inventory` 索引 0–8 ✓）
            //     ⇒ ⭐ 它是**在 `Inventory` 里的** ✗ ⇒ ⭐ 被这个循环当"跑到别处"清掉了 ✓ ✓。
            //   ⇒ ⭐ 现在把 ⭐ **两手都排除** ✗：⭐ 副手（索引 40／同一实例 ✓）
            //     ＋ ⭐ 主手（当前选中格／同一实例 ✓）—— ⭐ 四重保险 ✓ 以后换实现也不会漏 ✓。
            ItemStack main = player.getMainHandItem();
            int selected = inv.selected;
            boolean changed = false;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (i == 40 || i == selected || s == off || s == main) {
                    continue;
                }
                if (s.getItem() instanceof LongShortBladeItem && LongShortBladeItem.isPair(s)) {
                    inv.setItem(i, ItemStack.EMPTY);
                    changed = true;
                }
            }
            if (changed) {
                inv.setChanged();
            }
        } catch (Throwable ignored) {
            // ⭐ 清理失败绝不能连累玩法 ✗
        }
    }

    /**
     * ⭐ 清掉**所有带 {@code lnb_pair} 标记**的伙伴刀 ✓（副手 ＋ 背包 36 格 ✓）。
     * <p>⚠ 只清**带标记**的 ✗ —— 玩家自己拿的/放在别处的真刀**一把都不动** ✓。
     */
    private static void removePairs(ServerPlayer player, ItemStack off) {
        try {
            if (off.getItem() instanceof LongShortBladeItem && LongShortBladeItem.isPair(off)) {
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.getItem() instanceof LongShortBladeItem && LongShortBladeItem.isPair(s)) {
                    inv.setItem(i, ItemStack.EMPTY);
                }
            }
            inv.setChanged();
        } catch (Throwable ignored) {
            // 清理失败绝不能连累玩法 ✗
        }
    }

    /**
     * ⭐ <b>杀戮光环逐 tick 结算</b> ✓：对 **1 格**范围内所有敌人造成伤害 ✓
     * 并**每 tick 续无敌** ✓（用户口径：「期间玩家无敌」✓）持续 {@link LongShortBladeItem#ULTIMATE_DURATION_TICKS} ✓。
     */
    private static void tickUltimate(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!LongShortBladeItem.isUltimateActive(id)) {
            return;
        }
        int left = LongShortBladeItem.ultimateTicksLeft(id) - 1;
        if (left <= 0 || !player.isAlive()) {
            LongShortBladeItem.stopUltimate(id);
            // ⭐ 同时清掉"客户端可见的旋转信号" ✗ —— 否则客户端会一直转下去 ✓
            LongShortBladeItem.setUltimateVisualBoth(player, 0);
            return;
        }
        LongShortBladeItem.setUltimateTicks(id, left);
        // ⭐ 每 tick 刷新旋转信号（⚠ 客户端 NBT 同步会滞后 ✓ 所以客户端只看"是否 > 0"✗ 不看精确值 ✓）
        LongShortBladeItem.setUltimateVisualBoth(player, left);

        if (!(player.level() instanceof ServerLevel sl)) {
            return;
        }
        // ⭐ 期间无敌（用户口径 ✓ 每 tick 续 ✓）
        InvulnerabilityManager.applyInvulnerability(player, 6);

        // ⭐ **3 格**范围伤害（⚠ 用户 2026-10-08 明确改为 3 格 ✗ —— 最初口径是 1 格 ✓ 已按新口径改 ✓）；
        //   伤害 = 攻击力的 ULTIMATE_DPS_RATIO 每 tick ✓
        float perTick = 2.0F;
        ItemStack weapon = player.getMainHandItem().getItem() instanceof LongShortBladeItem
                ? player.getMainHandItem() : player.getOffhandItem();
        if (weapon.getItem() instanceof LongShortBladeItem) {
            ToolStack tool = ToolHelper.getToolStack(weapon);
            if (tool != null) {
                perTick = Math.max(1.0F, tool.getStats().get(ToolStats.ATTACK_DAMAGE)
                        * LongShortBladeItem.ULTIMATE_DPS_RATIO);
            }
        }
        final double r = LongShortBladeItem.ULTIMATE_RADIUS;
        AABB box = new AABB(player.getX() - r, player.getY() - 0.5, player.getZ() - r,
                player.getX() + r, player.getY() + player.getBbHeight() + 0.5, player.getZ() + r);
        List<LivingEntity> targets = sl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive());
        for (LivingEntity target : targets) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), perTick);
        }
        // ⭐ 旋转视觉：绕自身撒一圈横扫粒子 ✓（⚠ 半径跟着伤害范围走 ✗ 免得"看着打不到却打到了" ✓）
        for (int i = 0; i < 6; i++) {
            double a = (player.tickCount * 0.6D) + i * (Math.PI / 3.0D);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    player.getX() + Math.cos(a) * r, player.getY() + 1.0D, player.getZ() + Math.sin(a) * r,
                    1, 0, 0, 0, 0);
        }
    }

    // ============================================================
    //  ③ 命中：交替攻击 / fever / 处决
    // ============================================================

    /**
     * ⚠⚠ <b>自递归闸门</b>（⭐ §1118m 龙炎那次**同一个坑** ✗ 我又犯了一次 ✓ 已修 ✓）：
     * 本方法在 {@code LivingHurtEvent} **里面**又调 {@code target.hurt(...)}（交替攻击 ＋ 处决两处 ✓）
     * ⇒ ⭐ 那会**再次触发本方法自己** ✗ ⇒ 无限递归 ⇒ {@code StackOverflowError} ✗
     * ⇒ Forge 去记录这个异常时撞上本包既有的 **log4j `LinkageError` 冲突** ⇒ ⭐ **服务端 tick 崩** ✗✗。
     * <p>⭐ 实测崩溃报告（2026-10-08 22:38 ✓）：
     * <pre>
     * java.lang.LinkageError: loader constraint violation … MessageSupplier
     *   at EventBus.handleException
     *   at ForgeHooks.onLivingHurt
     *   at MeleeHitToolHook.dealDamage → ModifiableItem.onLeftClickEntity
     * </pre>
     * ⇒ ⭐ 结算期间**忽略自身事件** ✓，并用 {@code try/finally} 保证一定复位 ✓；
     * 且整个结算包 {@code try/catch(Throwable)} ✗ —— 异常绝不能逃出事件处理器 ✗。
     */
    private static final ThreadLocal<Boolean> SETTLING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // ⭐ 闸门：自己结算期间产生的事件一律忽略 ✗
        if (Boolean.TRUE.equals(SETTLING.get())) {
            return;
        }
        // ⚠ 匠魂二次伤害（流血等）不算"攻击命中" ⇒ 不攒 fever（照战镰那套 ✓）
        if (ToolHelper.isTinkersSecondaryDamage(event.getSource())) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // ⚠ LivingHurtEvent#getEntity() 的返回类型**本来就是 LivingEntity** ✗
        //   ⇒ 不能写 `instanceof LivingEntity target` 的模式匹配 ✓（那是"表达式类型的子类型"⇒ 非法 ✗
        //      —— projectile-side 替我编译时揪出来的 ✓）
        LivingEntity target = event.getEntity();
        if (target == null) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (!(weapon.getItem() instanceof LongShortBladeItem)) {
            return;
        }
        // ⚠ 光环期间不攒 fever（照战镰口径 ✓ 免得自循环 ✗）
        if (LongShortBladeItem.isUltimateActive(player.getUUID())) {
            return;
        }

        SETTLING.set(Boolean.TRUE);
        try {
            boolean longForm = LongShortBladeItem.isLong(weapon);
            // ⚠⚠ **临时探针**（⭐ 用户报「左手极少挥动」✗ 但四轮修改都无效 ⇒ 不再猜 ✓ 用日志定位 ✓
            //   ⭐ 探针一：`onHurt` 到底有没有被调用到？⭐ 若打三下日志里有三行 ⇒ 服务端没问题 ✓
            //   问题在客户端动画 ✗；⭐ 若一行都没有 ⇒ 服务端闸门就把我们挡在外面了 ✗）。
            TinkersNewlife.LOGGER.info("[长短刃·探针] 命中 form={} 挥={} fever={} 主手={} 副手={}",
                    longForm ? "长刀" : "短刀", longForm ? "待定" : "仅主手",
                    LongShortBladeItem.readFeverBoth(player),
                    weapon.getItem().builtInRegistryHolder().key().location(),
                    player.getOffhandItem().getItem().builtInRegistryHolder().key().location());
            if (longForm) {
                // ⭐ 长刀：左右交替 —— 长刀优先（奇数次）＋ 短刀（偶数次）✓
                boolean nextIsLong = NEXT_IS_LONG.getOrDefault(player.getUUID(), Boolean.TRUE);
                NEXT_IS_LONG.put(player.getUUID(), !nextIsLong);
                LongShortBladeItem.setFeverBoth(player,
                        LongShortBladeItem.readFeverBoth(player) + LongShortBladeItem.FEVER_PER_HIT);

                // ⭐⭐ **交替的"挥动动作"**（用户实测三轮：「挥动动作没有做出来」✗ →「还是没左右挥动」✗ →「**左手极少挥动**」✗）
                //   ⇒ ⭐ **两层原版闸门 ＋ 一条客户端闸门**（都在反编译里确认过 ✓）：
                //     ① 服务端 `LivingEntity#swing()` 有闸门 ✗（主手已在挥 ⇒ 副手那次被吞 ✓）；
                //     ② 原版 `HumanoidModel` 挥动**只按 `getMainArm()` 挑手臂** ✗（发包也还是挥同一只手 ✓）；
                //     ③ ⭐⭐ **客户端收到动画包后自己那道 `swing()` 闸门同样会吞** ✗
                //        ⇒ ⚠ 这就是「左手**极少**挥动」的原因 ✓（偶尔正好 SwingTime 过半才漏进来一次 ✓）。
                //   ⭐⭐ **实测定案（探针 ✓）**：⚠ 物品 NBT **大约每秒才同步一次** ✗
                //     （服务端 `命中` 27 行而客户端 counter 只按秒跳 ✓）
                //     ⇒ 副手每秒才播一次 ＝ 「左手极少挥动」✗
                //   ⇒ ⭐ 改用 **S2C 包**（`PacketSwingOffhand` ✓ 只发给本人 ✓ 零延迟 ✓）。
                if (!nextIsLong) {
                    com.mofengbaizhi.tinkersnewlife.network.tools.PacketSwingOffhand.sendTo(player);
                }

                // ⭐ 短刀那一次：额外一段"短刀伤害"（攻击力较低 ✓ 用户口径 ✓）
                if (!nextIsLong) {
                    ToolStack tool = ToolHelper.getToolStack(weapon);
                    float base = tool == null ? 6.0F : Math.max(1.0F, tool.getStats().get(ToolStats.ATTACK_DAMAGE));
                    float shortHit = base * 0.5F;
                    target.invulnerableTime = 0;
                    target.hurt(player.damageSources().playerAttack(player), shortHit);
                }
                return;
            }

            // ⭐ 短刀：只挥短刀 ✓ 但目标血量 < 20% ⇒ 长刀尝试斩杀处决（500% ✓）
            // ⚠⚠ **短刀形态不积攒 fever** ✗ —— 用户口径（2026-10-08 ✓）：
            //   「**不要让短刀模式也积攒fever啊**」✓
            //   ⭐ 回看用户最初的规格也确实如此：「**左右交替攻击时**，每次攻击会积攒1点fever」✓
            //   ⇒ ⭐ 攒 fever 是**长刀形态交替攻击**专属 ✓（突刺/光环仍是两种形态都能放 ✓
            //     只是"攒"只在长刀 ✓）✓。
            if (target.getHealth() <= target.getMaxHealth() * LongShortBladeItem.EXECUTE_HP_RATIO) {
                ToolStack tool = ToolHelper.getToolStack(weapon);
                float base = tool == null ? 10.0F : Math.max(1.0F, tool.getStats().get(ToolStats.ATTACK_DAMAGE));
                float execute = base * LongShortBladeItem.EXECUTE_MULTIPLIER;
                target.invulnerableTime = 0;
                target.hurt(player.damageSources().playerAttack(player), execute);
                // ⭐ 耐久消耗（用户口径 ✓ 2026-10-09：「给我的几个技能效果都补上耐久消耗」✓）
                //   ⇒ ⭐ 处决扣 {@link LongShortBladeItem#DURABILITY_EXECUTE} 点 ✓
                //   ⚠ 必须走匠魂的 `ToolDamageUtil` ✗（见 `LongShortBladeItem#spendDurability` ✓）。
                LongShortBladeItem.spendDurability(weapon, LongShortBladeItem.DURABILITY_EXECUTE, player);
                // ⭐⭐ **处决时挥动左臂的长刀**（用户口径 ✓ 2026-10-09：
                //   「**然后让触发处决时挥动左臂的长刀**」✓）
                //   ⭐ 处决发生在**短刀形态**下 ✓ ⇒ ⭐ 这次"长刀出手"应该**用左臂挥** ✓
                //     （⭐ 复用交替挥动那条 S2C 通路 ✓ —— 它本来就画左臂 ✓ 见 §1136 ✓）。
                com.mofengbaizhi.tinkersnewlife.network.tools.PacketSwingOffhand.sendTo(player);
                // ⭐⭐ **处决斩击粒子**（用户口径 ✓「样式为**内黑外红**的**横向斩击**，用于在**处决**时应用」✓）
                //   ⚠ **横向**怎么来 ✗：⭐ 不是靠旋转粒子 ✓（粒子的 `roll` 是绕视线轴转 ✗ 控不出"横在世界上"✓）
                //   ⇒ ⭐ 而是**沿"视线的水平垂线"排开一排** ✓ —— 那正好横在玩家面前 ✓；
                //   ⭐ "内黑外红"由**贴图本身**承担 ✓（`textures/particle/execute_slash.png` ✓ 程序化占位 ✓
                //     用户以后手绘替换只换 png ✓ 代码不用动 ✓）。
                if (target.level() instanceof ServerLevel slashLevel) {
                    // ⭐⭐ **一道刀光，从左上往右下滑过去**（用户口径 ✓ 2026-10-09：
                    //   「**就不能从左上往右下滑动吗，类似原版横扫特性？**」✓）
                    //   ⚠⚠ 我前三轮全在做"原地出现／原地长大" ✗ —— ⭐ 而用户要的是**滑动** ✓：
                    //     ⭐ 出生在**起点** ✓ ⭐ 带一个指向**终点**的**真实速度** ✓ ⭐ 同时从小长到大 ✓
                    //     ⇒ ⭐ 看起来就是一刀**划过去** ✓。
                    //   ⚠ 与原版横扫的差别 ✗：⭐ 原版那个 `SweepAttackParticle` 是**固定不动**的一片 ✗
                    //     （所以"横扫"看起来只是"闪一下"✓）—— ⭐ 我们这片**真的会走** ✓。
                    net.minecraft.world.phys.Vec3 look2 = player.getLookAngle();
                    net.minecraft.world.phys.Vec3 right2 =
                            new net.minecraft.world.phys.Vec3(-look2.z, 0.0D, look2.x);
                    if (right2.lengthSqr() < 1.0E-4D) {
                        right2 = new net.minecraft.world.phys.Vec3(1.0D, 0.0D, 0.0D);
                    }
                    right2 = right2.normalize();
                    // ⭐ 刀路方向：水平垂线 ＋ 世界上方按 45° 合成 ✓
                    //   ⭐ 上方分量随机取正负 ⇒ "左上→右下" 或 "左下→右上" ✓
                    double upSign = player.getRandom().nextBoolean() ? 1.0D : -1.0D;
                    double dirX = right2.x * 0.7071D;
                    double dirY = 0.7071D * upSign;
                    double dirZ = right2.z * 0.7071D;
                    // ⭐ 从**起点端**出生 ✓
                    //   ⚠⚠ 实测第四版仍被指出两点 ✗（用户 2026-10-09：
                    //     「**看着还像是在左上方放大没有滑动**」＋「**右下方放大导致沉在地里看不见了**」✓）
                    //     ⇒ ⭐ 两个都是**数值失衡** ✗：
                    //       ① ⭐ **位移被生长压过** ✗ —— 生长从 0.30 涨到 **3.0 格**
                    //          而位移只有约 2 格 ⇒ 眼睛只看到"变大"✓
                    //          ⇒ ⭐ **位移拉长（总 2.4 格）＋ 加速（0.45/tick）** ✓
                    //            且 ⭐ **终点尺寸压到 1.6 格** ✓ ⇒ ⭐ 让"滑"占主导 ✓；
                    //       ② ⭐ **沉进地里** ✗ —— `quadSize` 是**以中心向四周**涨 ✗
                    //          ⇒ 涨到 1.6 格时下半 **0.8 格**扎进地面 ✓
                    //          ⇒ ⭐ **出生高度抬到身体 1.0 倍** ✓ ⇒ ⭐ 留出下半的空间 ✓。
                    double halfLen = 1.20D;   // ⭐ 起点在中心往回 1.2 格 ✓
                    // ⚠ 速度不能按"每 tick 走多远"直算 ✗ —— ⭐ `Particle#move` 每 tick 还会乘
                    //   **0.98 的摩擦** ✓ ⇒ ⭐ 实际总位移 ≈ `速度 × 0.85 / 0.02` ✗
                    //   （⭐ 0.45 会滑出 **4.7 格** ✗ 太远 ✓）⇒ ⭐ 0.28 ⇒ 约 **2.9 格** ✓ 与刀路相称 ✓。
                    double speed = 0.28D;
                    slashLevel.sendParticles(
                            com.mofengbaizhi.tinkersnewlife.content.ModParticles.EXECUTE_SLASH.get(),
                            target.getX() - dirX * halfLen,
                            // ⚠ 抬高：`bbHeight * 1.0` ✓（⭐ 免得长大后的下半扎进地面 ✓）
                            target.getY() + target.getBbHeight() * 1.0D - dirY * halfLen,
                            target.getZ() - dirZ * halfLen,
                            1,
                            // ⭐ 速度 ＝ ⭐ **真实的滑动方向** ✓（⭐ 粒子靠它移动 ✓ 并据 `vy` 的正负决定
                            //   贴图要不要镜像 ✓ 见 `ExecuteSlashParticle` ✓）
                            dirX * speed,
                            dirY * speed,
                            dirZ * speed,
                            0.0D);
                }
                if (player.level() instanceof ServerLevel sl) {
                    sl.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                            SoundSource.PLAYERS, 1.2F, 0.6F);
                }
            }
        } catch (Throwable t) {
            // ⚠ 异常绝不能逃出事件处理器 ✗ —— 逃出去 Forge 会去记录 ⇒ 撞 log4j 冲突 ⇒ tick 崩 ✗✗
            TinkersNewlife.LOGGER.warn("[长短刃] 命中结算异常（已吞掉，不影响游戏）：{}", t.toString());
        } finally {
            SETTLING.set(Boolean.FALSE);
        }
    }

    // ============================================================
    //  ④ 短刀投掷
    // ============================================================

    /**
     * ⭐ 把短刀投掷出去 ✓（用户口径 ✓）—— 由 {@code LongShortBladeItem#releaseUsing} 调 ✓。
     * <p>⚠ 投出去的是**投射物实体**（不是真把物品丢出去 ✗）—— 这样不会出现"武器丢了"的恶果 ✓
     * 命中后由投射物自己放**半径 3 格非破坏爆炸** ✓（{@code Level.ExplosionInteraction.NONE} ✓）。
     */
    public static void throwShortBlade(Player player, ItemStack stack, float power) {
        if (!(player.level() instanceof ServerLevel sl)) {
            return;
        }
        try {
            ShortBladeThrowEntity entity = new ShortBladeThrowEntity(
                    ModEntities.SHORT_BLADE_THROW.get(), player, sl);
            entity.setItem(stack.copy());
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(),
                    0.0F, 1.2F + power * 0.8F, 1.0F);
            sl.addFreshEntity(entity);
            sl.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[长短刃] 投掷失败（不影响游戏）：{}", t.toString());
        }
    }
}
