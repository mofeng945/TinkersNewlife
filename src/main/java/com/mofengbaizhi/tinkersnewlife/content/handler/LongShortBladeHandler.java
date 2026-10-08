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

        // ⭐ 主手是刀、副手空 ⇒ 补一把伙伴刀（形态相反 ✓）
        if (off.isEmpty()) {
            ItemStack pair = main.copy();
            pair.setCount(1);
            LongShortBladeItem.markPair(pair, true);
            LongShortBladeItem.setForm(pair, LongShortBladeItem.isLong(main)
                    ? LongShortBladeItem.FORM_SHORT : LongShortBladeItem.FORM_LONG);
            player.setItemInHand(InteractionHand.OFF_HAND, pair);
        }
        // ⚠ 副手有别的东西 ⇒ 不动它 ✗（玩家自己放的东西优先 ✓）
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
            if (longForm) {
                // ⭐ 长刀：左右交替 —— 长刀优先（奇数次）＋ 短刀（偶数次）✓
                boolean nextIsLong = NEXT_IS_LONG.getOrDefault(player.getUUID(), Boolean.TRUE);
                NEXT_IS_LONG.put(player.getUUID(), !nextIsLong);
                LongShortBladeItem.setFeverBoth(player,
                        LongShortBladeItem.readFeverBoth(player) + LongShortBladeItem.FEVER_PER_HIT);

                // ⭐⭐ **交替的"挥动动作"**（⚠ 用户实测两轮：「双刀交替攻击挥动动作没有做出来」✗／「还是没左右挥动」✗）
                //   —— ⚠ 我第一版只做了交替的**伤害** ✗；第二版补 `player.swing(hand, true)` 也**没用** ✗
                //   ⇒ ⭐ **两层原因**（都实测过 ✓）：
                //     ① ⭐ 原版 `LivingEntity#swing(hand, updateSelf)` 有**闸门** ✗：
                //        `if (!swinging || swingTime >= 挥动时长/2 || swingTime < 0) { …才真的挥… }`
                //        ⇒ ⭐ 攻击时**主手已经在挥** ✗ ⇒ 副手那一次被**整段吞掉** ✗
                //        ⇒ ⭐ 所以这里**直接自己发动画包** ✓（`ClientboundAnimatePacket`：主手 ＝ 0 ✓ 副手 ＝ 3 ✓）
                //          绕过那个闸门 ✓（⚠ 副作用只有"重新起手"✓ 正是我们要的交替 ✓）；
                //     ② ⭐ 原版 `HumanoidModel` 挥动时按 `getMainArm()` 挑手臂 ✗
                //        ⇒ ⭐ 光发包**还是会挥同一只手** ✗ ⇒ 另一半在
                //          {@code HumanoidSpinPoseMixin} 里**自己把左臂转起来** ✓（见那里 ✓）。
                net.minecraft.world.InteractionHand swingHand =
                        nextIsLong ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                if (player.level() instanceof ServerLevel swingLevel) {
                    swingLevel.getChunkSource().broadcastAndSend(player,
                            new net.minecraft.network.protocol.game.ClientboundAnimatePacket(
                                    player, swingHand == InteractionHand.MAIN_HAND ? 0 : 3));
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
