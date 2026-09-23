package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.energy.AmbientEnergySources;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorage;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <b>魔力台座</b>的方块实体：把"台座上的水晶物品 + 紧邻的水晶方块"都当作充能目标，
 * 每秒从 {@link AmbientEnergySources 所有启用的环境能量来源} 取一份 EE（<b>各源相加</b> ✓ §545）灌进去 ✓。
 *
 * <h2>充能节奏（为什么是"每秒结算"）</h2>
 * 速率的单位是 <b>EE/秒</b>（配置 {@code elder_crystal.pedestal_charge_max_per_second} ✓ 以及 §545 起
 * 植物/燃料/灵魂/玩家四条各自的数值 ✓），所以每 {@value #SETTLE_INTERVAL} tick 结算一次；但速率常常是小数
 * （例：默认参数下亮度 12 ⇒ 0.1 EE/秒、亮度 14 ⇒ 0.033 EE/秒 ✓）⇒ 用
 * <b>double 蓄水池 {@code pool}</b> 累积，够 1 点才写进水晶 ✓
 * —— 免得"0.033/秒"被 int 取整成 0、永远充不进电 ✗（用户点名要求 ✓）。
 *
 * <h2>灌给谁（顺序固定 ✓ 可解释 ✓）</h2>
 * <ol>
 *   <li><b>台座上的水晶物品</b>优先（容量 1000 ✓ 与 {@code ElderCrystalStorage.CRYSTAL_CAPACITY} 同源 ✓）；</li>
 *   <li>装不下的余量依次喂给<b>紧邻的水晶方块</b>（{@link #NEIGHBOURS}：上 → 下 → 北 → 南 → 西 → 东 ✓
 *       容量 4000 ✓ 走 {@link ElderCrystalBlockEntity#absorb(int)} 这个 §519 就留好的接口 ✓）；</li>
 *   <li>全都满了 ⇒ 台座<b>直接停</b>（不留蓄水、不产能量 ✓ 用户口径"满了停止"✓）。</li>
 * </ol>
 *
 * <h2>表现（只在**真的充进去**时）</h2>
 * <ul>
 *   <li>冷色粒子 {@code END_ROD} / {@code SNOWFLAKE} 交替 ✓ <b>密度 ∝ 速率</b>（越快越密 ✓）；</li>
 *   <li>紫水晶风铃 {@code AMETHYST_BLOCK_CHIME}，音量固定很小、音高略随速率 ✓ 每 {@value #SOUND_INTERVAL} tick 一次；</li>
 *   <li>两者都受配置开关控制 ✓（{@code pedestal_particles} / {@code pedestal_sound} ✓）。</li>
 * </ul>
 * <p>⚠ 台座<b>不发光</b>（{@code lightLevel} 恒 0）是<b>故意的</b>：若它自己发光，就会把自己上方那格的
 * 亮度抬起来 ⇒ 按"亮度越低越快"的规则反过来拖慢自己（自反馈 ✗）。所以"正在充能"的视觉反馈
 * 交给粒子与音效 ✓，而不是光 ✓。
 *
 * <h2>同步（客户端要画那颗悬浮的水晶）</h2>
 * 只同步 {@code Crystal} 这一个物品栈 ✓：改动时 {@code sendBlockUpdated} + 覆写
 * {@link #getUpdatePacket()}（{@code Level#sendBlockUpdated} 内部会取它，默认实现返回 null ⇒ 不发包 ✗）；
 * 区块加载时走 {@link #getUpdateTag()} ✓。粒子/音效是服务端 {@code sendParticles/playSound} 下发的 ✓
 * 所以这里不需要第二个网络通道 ✓。
 * <p>§525 补记：<b>"取走/清空"与"放上去"走的是同一条路</b>（{@link #sync()}）——区别只有一条：
 * 取走时<b>无条件</b>发（见 {@link #takeCrystal()}），因为"变成空"也必须让客户端知道 ✓
 * （原来写成 {@code if (!taken.isEmpty()) sync()} ⇒ 那条分支本身没错，但它把"清空"的同步绑在了
 * 返回值上；现在改成无条件，语义更直白 ✓）。
 */
public class ElderManaPedestalBlockEntity extends BlockEntity implements EeStorage {

    /** 台座上那颗水晶的存档键（物品栈整包存 ✓ 含它自己的 {@code EE} ✓） */
    public static final String KEY_CRYSTAL = "Crystal";

    /** §553 缓存（EE）的存档键 */
    public static final String KEY_CACHE = "Cache";

    /** §557 "外部送来、还没灌进水晶"的暂存（EE）的存档键 */
    public static final String KEY_BUFFER = "EeBuffer";

    /**
     * 紧邻水晶方块的喂养顺序（**固定顺序** ⇒ 玩家可预期 ✓ 也免得每 tick 遍历 6 个方向再排序 ✗）。
     * <p>上/下优先于四邻：贴在台座正上方或正下方的那块"离水晶最近" ✓。
     */
    private static final Direction[] NEIGHBOURS = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    /** 结算周期（tick）—— 速率的单位就是 EE/秒 ⇒ 20 tick 正好一秒 ✓ */
    private static final int SETTLE_INTERVAL = 20;

    /** 音效节拍（tick）：40 = 每 2 秒一声（很轻，不吵闹 ✓） */
    private static final int SOUND_INTERVAL = 40;

    /**
     * §600 充能时的同步节拍（tick）：<b>4 = 5 Hz</b>。
     * <p>原来（§555）是 2（10 Hz ✗）——那一版是为了"HUD 进度实时变化"✓，代价是**每 2 tick 就发一个
     * 方块实体更新包** ✗（包里含台座上那件水晶的 NBT ✓）⇒ 10 台座同时充能 ≈ 十几~几十 KB/s ✗。
     * 5 Hz 的观感依然是"连续在涨"✓（比原版熔炉那种 1 Hz 顺滑得多 ✓）而包量**减半** ✓。
     */
    private static final int SYNC_INTERVAL = 4;

    /** 满速时每这么多 tick 放一颗粒子（越慢越稀 ⇒ 密度 ∝ 速率 ✓） */
    private static final int PARTICLE_PERIOD_AT_FULL_RATE = 2;

    /** 粒子节拍的下限（速率极低时最稀的节拍 = 每秒一颗 ✓） */
    private static final int PARTICLE_PERIOD_MAX = SETTLE_INTERVAL;

    /** 音效参数（音量刻意小：这是"轻微音效"✓ 不抢环境音 ✓） */
    private static final float SOUND_VOLUME = 0.25F;
    private static final float SOUND_PITCH_MIN = 1.0F;
    private static final float SOUND_PITCH_RANGE = 0.6F;

    /** 台座上的水晶（空 = 没有 ✓ 始终最多 1 个 ✓） */
    private ItemStack crystal = ItemStack.EMPTY;

    /** 蓄水池：够 1.0 才写进水晶（支持小数速率 ✓ 不丢小数 ✓ 见类注释）；**不存档** ✓（重启后从 0 重新攒，最多丢不到 1 EE ✓ 无害） */
    private double pool = 0.0D;

    /**
     * §553 <b>缓存</b>：没地方灌（没水晶 / 水晶满了）时把产出的 EE 先攒在这里 ✓
     * <p>上限 = <b>一颗水晶的量</b> {@link ElderCrystalStorage#CRYSTAL_CAPACITY}（1000 EE ✓ 用户口径）；
     * 放上水晶后每 tick 放出 {@link #CACHE_FLUSH_PER_TICK} ⇒ 满缓存约 2 秒灌完 ✓。
     * <p>持久化到 NBT ✓（走远/重启不丢 ✓）。
     */
    private double cache = 0.0D;

    /** 缓存上限 = 一颗水晶 = 1000 EE ✓（与水晶容量同一个常量 ✓ 不写裸数字 ✗） */
    private static final double CACHE_CAP = ElderCrystalStorage.CRYSTAL_CAPACITY;

    /** 每 tick 从缓存里放出的上限（25 ⇒ 1000 EE 约 2 秒 ✓ 看得见但不拖沓 ✓） */
    private static final int CACHE_FLUSH_PER_TICK = 25;

    /**
     * §557 <b>「外来 EE」暂存</b>：别的方块（抽取方块 / 转化器）通过 {@link EeStorage#insertEe}
     * 送进来的 EE 先落在这里 ✓，由 {@link #tick} 按 §553 那条"有地方就放出"的同一套逻辑
     * 灌给水晶 / 水晶方块 ✓。
     *
     * <p>为什么不直接写进 {@link #cache} ✗：{@code cache} 的语义是"<b>这台座自己产出的</b>、
     * 只是暂时没地方灌的 EE" ✓，与"外面送来的"混在一起后，台座满时"到底是谁在积压"就说不清了 ✗。     * 分开两个字段 ⇒ 出口那一段（{@code hasSpaceForEe()} ⇒ {@code distribute()}）可以<b>共用</b> ✓
     * 不必写第二套灌注逻辑 ✓。
     *
     * <p>上限同样是 <b>一颗水晶</b>（{@link #CACHE_CAP}）✓ 满了就拒收（{@code insertEe} 如实返回 0）✓
     * —— 这样上游（抽取方块）自己会停，不会出现"偷偷吞掉别人的电" ✗。
     * <p>持久化到 NBT ✓（走远/重启不丢 ✓ 与 cache 同一口径 ✓）。
     */
    private double eeBuffer = 0.0D;

    /** 每 tick ++（粒子/音效节拍用 ✓） */
    private int ticks = 0;

    /** 距上次结算的 tick 数 */
    private int settleTicks = 0;

    /** 每秒 ++ 的来源总量（EE/秒；表现层用它决定粒子密度/音高 ✓）—— §545 起是**所有启用来源之和** ✓ */
    private double lastRate = 0.0D;

    /**
     * <b>「待结算 EE」缓冲</b>（§545 ③ 灵魂死亡用 ✓）。
     *
     * <p>死亡是<b>离散事件</b>（每秒可能来十几笔、也可能一笔没有 ✗）而台座是<b>每秒结算一次</b> ⇒
     * 中间必须有个池子 ✓：{@code SoulDeathEnergySource} 在死亡那一刻按距离找台座、把
     * {@code 最大生命 / 40} 记进这里 ✓，台座下一秒 {@code settle()} 时<b>一次性领走</b> ✓。
     *
     * <p>⚠ 缓冲<b>不存档</b>（重启丢不到 1 秒的量 ✓ 与"植物凋灵度内存态"同一口径 ✓）。
     * <p>⚠ 上限 {@value #PENDING_CAP}：万一有个台座再也 tick 不到（未加载/被拆），
     * 缓冲也不会无限长大 ✗ —— 满了就<b>丢弃超额部分</b> ✓（宁可少给几 EE，也不要内存泄漏 ✗）。
     */
    private double pendingEnergy = 0.0D;

    /** 待结算缓冲的上限（EE；见 {@link #pendingEnergy} ✓） */
    private static final double PENDING_CAP = 10_000.0D;

    /** 上一次结算**真的充进去了**吗（粒子/音效的把关 ✓ 不充就不演 ✗） */
    private boolean charging = false;

    public ElderManaPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELDER_MANA_PEDESTAL.get(), pos, state);
    }

    // ============================================================
    //  台座上的水晶：查 / 放 / 取
    // ============================================================

    /** 台座上的水晶（可能为空栈 ✓ 直接读不要改它 —— 要改请走 {@link #setCrystal} ✓） */
    /**
     * §555 台座上那件**是不是我们收的水晶**（水晶物品 ✓ 或水晶方块物品 ✓）。
     * <p>两种都用同一套"台座上悬浮一件"的表现 ✓ 区别只在容量：水晶 **1000**、水晶方块 **4000** ✓。
     */
    public static boolean heldCrystal(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL.get())
                || stack.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get()));
    }

    /** §555 台座上那件的**剩余容量**（不是水晶 ⇒ 0 ✓） */
    private int heldSpace() {
        return heldCap(crystal) - heldEe(crystal);
    }

    /** §555 那件的容量（方块物品 4000 / 其余按水晶 1000 ✓） */
    private static int heldCap(ItemStack s) {
        return s.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get())
                ? ElderCrystalStorage.BLOCK_CAPACITY : ElderCrystalStorage.CRYSTAL_CAPACITY;
    }

    /** §555 那件里已存的 EE（按类型读 ✓） */
    private static int heldEe(ItemStack s) {
        return s.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get())
                ? ElderCrystalStorage.getBlockItemEe(s) : ElderCrystalStorage.getCrystalEe(s);
    }

    /** §555 往台座上那件里加 EE（按类型写 ✓）；返回实际加进去多少 ✓ */
    private int addHeld(int amount) {
        if (amount <= 0 || crystal.isEmpty()) return 0;
        if (crystal.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get())) {
            int cur = ElderCrystalStorage.getBlockItemEe(crystal);
            int add = Math.min(Math.max(0, ElderCrystalStorage.BLOCK_CAPACITY - cur), amount);
            if (add > 0) ElderCrystalStorage.setBlockItemEe(crystal, cur + add);
            return add;
        }
        return ElderCrystalStorage.addCrystalEe(crystal, amount);
    }

    public ItemStack getCrystal() {
        return crystal;
    }

    public boolean hasCrystal() {
        return !crystal.isEmpty();
    }

    /** 台座是否还有地方可充（台座上的水晶没满 **或** 至少有一块紧邻的水晶方块没满 ✓） */
    public boolean hasSpaceForEe() {
        if (!crystal.isEmpty() && heldSpace() > 0) {     // §555 类型感知：水晶物品 1000 / 水晶方块 4000 都算 ✓
            return true;
        }
        if (level == null) return false;
        for (Direction d : NEIGHBOURS) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof ElderCrystalBlockEntity target
                    && target.getSpace() > 0) {
                return true;
            }
        }
        return false;
    }

    /** 放一颗水晶上去（会就地存下来并同步给客户端 ✓；调用方负责从玩家手里扣掉那一颗 ✓） */
    public void setCrystal(ItemStack stack) {
        ItemStack next = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copy();
        if (!next.isEmpty()) next.setCount(1);   // 台座上永远只有一个 ✓
        crystal = next;
        setChanged();      // §601 补：原来只 sync 不标脏 ✗ ⇒ 放上去就存档退出会丢那一颗（自动化更是每件都要存 ✓）
        sync();
    }

    /**
     * 把台座上的水晶取下来（返回空栈 = 本来就没有 ✓）；<b>返回的那个栈从此归调用方所有</b> ✓
     * （调用方负责塞进背包 / 掉在地上 —— 见 {@code ElderManaPedestalBlock#use}）。
     *
     * <p>⚠ §525 两条纪律，缺一条就会重现"悬浮水晶不消失 / 取不下 / 放不上去"✗：
     * <ol>
     *   <li><b>先清字段、再 {@link #sync()}（顺序不能反 ✓）</b>——反了就会把"台座上还有水晶"那一版
     *       发出去 ⇒ 客户端 BER 照旧接着画 ✗；</li>
     *   <li><b>无条件 sync</b>（哪怕本来就没有水晶）——"空了"这件事必须一定到达客户端 ✓：
     *       {@link #getUpdateTag()} 在水晶为空时<b>不写</b> {@code Crystal} 键，
     *       客户端 {@link #load(CompoundTag)} 读到"没这个键"就把字段置成 {@code EMPTY} ✓
     *       （⇒ 与"台座被拆/区块重载"走的是同一条数据路 ✓ 不会出现两条口径）；</li>
     *   <li>清空**不依赖**"交给玩家"是否成功 ✓ —— 那是 {@code use} 里清空<b>之后</b>才做的事，
     *       成败都不影响台座已经空了 ✓（清空放前面 ⇒ 不可能出现"玩家拿到一份、台座上还留一份"的复制 ✗）。</li>
     * </ol>
     */
    public ItemStack takeCrystal() {
        ItemStack taken = crystal;
        crystal = ItemStack.EMPTY;
        setChanged();      // §601 补：同上（"变空"也必须写进存档 ✓）
        sync();
        return taken;
    }

    // ============================================================
    //  「待结算 EE」缓冲（§545 ③ 灵魂死亡用 ✓）
    // ============================================================

    /**
     * 记一笔"待结算 EE"（由 {@code SoulDeathEnergySource} 在生物死亡的那一刻调用 ✓）。
     *
     * @param ee 这一笔的量（{@code 最大生命 / 40} ✓）；非正数直接忽略 ✓
     * @return 真的记进去了多少（可能因为 {@link #PENDING_CAP} 而少记/记不进去 ✓）
     */
    public double addPendingEnergy(double ee) {
        if (!(ee > 0.0D)) return 0.0D;
        double room = PENDING_CAP - pendingEnergy;
        if (room <= 0.0D) return 0.0D;
        double added = Math.min(ee, room);
        pendingEnergy += added;
        return added;
    }

    /** 看一眼缓冲里还有多少（<b>不清空</b> ✓ tooltip / 调试用 ✓） */
    public double peekPendingEnergy() {
        return pendingEnergy;
    }

    /**
     * <b>领走</b>缓冲里的全部 EE（领完清零 ✓）。
     * <p>⚠ 只在台座每秒结算那一次（{@code simulate == false}）被调用 ✓ ——
     * 查速率绝不会把这笔账吞掉 ✗（{@link com.mofengbaizhi.tinkersnewlife.content.energy.SoulDeathEnergySource} ✓）。
     */
    public double drainPendingEnergy() {
        double drained = pendingEnergy;
        pendingEnergy = 0.0D;
        return drained;
    }

    // ============================================================
    //  每 tick：结算 + 表现
    // ============================================================

    /** 方块注册用的 ticker（见 {@code ElderManaPedestalBlock#getTicker} ✓ 只在服务端挂 ✓） */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ElderManaPedestalBlockEntity be) {
        if (level instanceof ServerLevel server) be.tick(server, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        ticks++;
        settleTicks++;
        if (settleTicks >= SETTLE_INTERVAL) {
            settleTicks = 0;
            settle(level, pos);
        }
        // §552 均摊：把"这一秒拿到的总量"分成 SETTLE_INTERVAL 份，每 tick 灌一份 ✓
        //      ⇒ 观感是**平滑地一点点涨**（原先是每秒一次性灌一波 ✗）；
        //      ⚠ 来源的副作用（扣燃料/涨凋灵度/灵魂账）**仍只在 settle() 里每秒发生一次** ✓
        //        所以绝不会被算快 20 倍 ✗。
        if (lastRate > 0.0D) {
            // §569 关键修复：**先把这一 tick 的产量记进 pool** —— 原来只在"有地方灌"那一支里加 ✗ ⇒ 没水晶时 pool 永远是 0 ⇒ 缓存永远不涨 ✗
            pool += lastRate / SETTLE_INTERVAL;
            if (!hasSpaceForEe()) {
                // 没地方灌 ⇒ **不丢**，整池收进缓存（上限 = 一颗水晶 ✓ 满了就真的停 ✗）
                cache = Math.min(CACHE_CAP, cache + pool);
                pool = 0.0D;
                charging = cache < CACHE_CAP;
            } else {
                if (pool >= 1.0D) {
                    int amount = (int) Math.floor(pool);
                    pool -= amount;
                    int accepted = distribute(amount);
                    if (accepted < amount) pool = 0.0D;
                }
                charging = true;      // 有来源在产 ⇒ 粒子照演（原先是"这一秒灌进去了才演" ✗ 均摊后大多数 tick 都不到 1 点 ✗）
            }
        }
        // §553 有地方灌 + 缓存里有货 ⇒ 每 tick 放出一批（不满 1 点就等下一 tick ✓）
        if (cache >= 1.0D && hasSpaceForEe()) {
            int amount = (int) Math.min(Math.floor(cache), (double) CACHE_FLUSH_PER_TICK);
            if (amount > 0) {
                int accepted = distribute(amount);
                cache -= accepted;
                if (accepted < amount) cache = 0.0D;   // 装不下 ⇒ 剩下的不囤（与"满了就停"一致 ✓）
                charging = true;
            }
        }
        // §557 外来 EE（抽取方块 / 转化器送进来的）走**同一条**出口：有地方灌就放出去 ✓
        //      ⇒ 与"台座自己产的"共用 distribute()，不存在第二套灌注逻辑 ✓
        //      ⚠ 与 cache 的区别只有一处：这里送出的是**别人的**电，所以**不**参与 settle() 的停车判定 ✗
        //        （台座满时上游会通过 insertEe 的返回值自己停 ✓ 见 eeBuffer 的注释）。
        if (eeBuffer >= 1.0D && hasSpaceForEe()) {
            int amount = (int) Math.min(Math.floor(eeBuffer), (double) CACHE_FLUSH_PER_TICK);
            if (amount > 0) {
                int accepted = distribute(amount);
                eeBuffer -= accepted;
                if (accepted < amount) eeBuffer = 0.0D;   // 与 cache 同口径：装不下就不囤 ✓（上游会继续送 ✓）
                charging = true;
            }
        }
        if (charging) showCharging(level, pos);
    }

    // ============================================================
    //  §557 EeStorage：让"抽取方块 / 转化器"这类外部方块能读/取台座的 EE
    //  ⚠ 只**新增**入口 ✓ 台座原有的水晶交互（放/取/充能）一行都没动 ✗
    // ============================================================

    /**
     * 台座对外暴露的 EE = <b>§553 的缓存 + §557 的外来暂存</b>。
     * <p>不包含"台座上方那颗水晶物品里的 EE" ✗ —— 那件东西属于玩家（挖掉台座会原样掉出来 ✓
     * 见 {@code ElderManaPedestalBlock#getDrops}），让抽取方块从台座里把玩家放的水晶抽干，
     * 是这次需求里没有的语义 ✗（要抽水晶里的电就直接把<b>水晶方块</b>摆在抽取方块旁边 ✓ 那条路是通的 ✓）。
     */
    @Override
    public int getEe() {
        return (int) Math.floor(cache + eeBuffer);
    }

    /** 台座能暂存的上限 = <b>一颗水晶</b>（{@link #CACHE_CAP}；cache 与外来暂存各算一份 ⇒ ×2 ✓） */
    @Override
    public int getCapacity() {
        return (int) (CACHE_CAP * 2);
    }

    @Override
    public int insertEe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        double room = CACHE_CAP - eeBuffer;
        if (room <= 0.0D) return 0;
        int accepted = (int) Math.min(Math.floor(room), (double) amount);
        if (accepted <= 0) return 0;
        if (!simulate) {
            eeBuffer += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public int extractEe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        // ⚠ 先抽"外来暂存"、再抽"自己的缓存" ✓ —— 反过来会让台座自己攒的那份先被搬走 ✗
        int fromBuffer = (int) Math.min(Math.floor(eeBuffer), (double) amount);
        int left = amount - fromBuffer;
        int fromCache = left <= 0 ? 0 : (int) Math.min(Math.floor(cache), (double) left);
        int taken = fromBuffer + fromCache;
        if (taken <= 0) return 0;
        if (!simulate) {
            eeBuffer -= fromBuffer;
            cache -= fromCache;
            setChanged();
        }
        return taken;
    }

    /**
     * 一秒一次的结算（§545 多源版）：问注册表要"<b>所有启用来源之和</b>" ⇒ 再加上待结算的灵魂 ⇒
     * 攒进蓄水池 ⇒ 够 1 点就灌给目标 ✓
     *
     * <h2>与 §545 之前的三点不同</h2>
     * <ol>
     *   <li><b>来源从"一条"变成"全部相加"</b>：
     *       原来是 {@code AmbientEnergySources.selected().eePerSecond(level, pos)}，
     *       现在是 {@code AmbientEnergySources.eePerSecond(level, pos, false)} ✓
     *       （{@code false} = 允许真扣燃料 / 真涨植物凋灵度 ✓ 见接口注释 ✓）；</li>
     *   <li><b>加上"待结算 EE"</b>：{@code SoulDeathEnergySource} 攒在台座上的那笔账在这一秒一起发放 ✓
     *       （⚠ 它<b>按自己的开关</b>判定有没有记过账 ✓ 这里只看池子里有没有东西 ⇒ 即便玩家随后把
     *       {@code soul_death} 关掉/从清单里去掉，<b>已经攒下的也照发</b> ✓ 不会凭空吞掉 ✓）；</li>
     *   <li><b>停的条件从"速率为 0"放宽成"速率与池子都为 0"</b> ⇒ 修掉了一个真实的丢账窗口 ✗：
     *       原来是 {@code if (!(rate > 0) ...) { pool = 0; return; }}`，那一刻池子里若正好躺着灵魂，
     *       就会连着 {@code pool} 一起被清掉 ✗（例如台座旁刚好亮到 {@code light_level} 不产 EE、
     *       但刚死了一只怪 ✓ 那笔账就没了 ✓）。现在两种来源分开判 ✓。</li>
     * </ol>
     * <p>§552 起：这里**只**问来源、**只**记速率（{@code lastRate}）；真正的"灌进水晶"由 {@code tick()} 每 tick 均摊 1/20 ✓（高速率下仍是每 tick 灌整数点 ✓ 不浪费 ✓）。
     */
    private void settle(ServerLevel level, BlockPos pos) {
        // ① 先看"有没有地方可灌" —— 满了就整车停：**不**问来源、**不**扣燃料、**不**涨凋灵度 ✓
        //    （一箭三雕：不浪费玩家的燃料 ✓ 不白杀植物 ✓ 也省掉最贵的两次范围扫描 ✓）
        if (cache >= CACHE_CAP) {
            // §553 缓存满了 ⇒ 整车停：不问来源、不扣燃料、不涨凋灵度 ✓（缓存留着 ✓ 不清 ✗）
            lastRate = 0.0D;
            charging = false;
            return;
        }

        // ② 所有启用来源之和（simulate=false ⇒ 允许真扣燃料 / 真涨凋灵度 ✓ 每秒**只此一次** ✓）
        //    ⚠ 台座自己读配置，而不是只靠来源清单：这样"清单写错/一条都没启用"时池子里的账也照发 ✓
        double rate = AmbientEnergySources.eePerSecond(level, pos, false);
        // ③ 灵魂缓冲（离散事件的账 ✓ 只在 **这一步** 领走 ⇒ 查速率绝不会吞掉它 ✓）
        double pending = drainPendingEnergy();

        // NaN（`!(x > 0)` 同时盖住 NaN ✓）/ 非正 ⇒ 当 0 ✓
        if (!(rate > 0.0D)) rate = 0.0D;
        if (!(pending > 0.0D)) pending = 0.0D;

        double gained = rate + pending;
        lastRate = gained;                       // 表现层用的是"这一秒实际拿到多少" ✓ 含灵魂那一笔 ✓

        if (gained <= 0.0D) {                    // 这一秒真的一无所得 ⇒ 不留蓄水、不演粒子 ✓
            pool = 0.0D;
            charging = false;
            return;
        }

        // §552 不再"这一秒一次灌完" ✗ ⇒ 只把总量记进 lastRate ✓，真正的灌入交给 tick() 每 tick 均摊 1/20 ✓
        //      （蓄水池 pool 的语义不变：不足 1 点就一直攒着 ✓ 不浪费 ✓）
        charging = true;
    }

    /**
     * 把 {@code amount} 点 EE 依次灌给：台座上的水晶物品 → 紧邻的水晶方块 ✓
     *
     * @return 实际灌进去的量（0 = 全满/没目标 ✓）
     */
    private int distribute(int amount) {
        int left = amount;

        // ① 台座上的那件优先（§555：**水晶物品和水晶方块都收** ✓）
        if (left > 0 && !crystal.isEmpty()) {
            int added = addHeld(left);
            if (added > 0) {
                left -= added;
                setChanged();                        // 物品栈存在方块实体里 ⇒ 必须标脏才写进存档 ✓
                if (ticks % SYNC_INTERVAL == 0) sync();   // §600 **每 4 tick（5 Hz）同步一次** ⇒ HUD 依旧连续在涨 ✓ 包量减半 ✓
            }                                        //      （原来只在"放/取"时同步 ✗ ⇒ HUD 一直显示旧值 ✗）
        }

        // ② 紧邻的水晶方块（上/下/四邻，固定顺序 ✓ 走 §519 留好的 absorb 接口 ✓）
        if (left > 0 && level != null) {
            for (Direction d : NEIGHBOURS) {
                if (left <= 0) break;
                if (level.getBlockEntity(worldPosition.relative(d)) instanceof ElderCrystalBlockEntity target) {
                    left -= target.absorb(left);
                }
            }
        }

        return amount - left;
    }

    /** 冷色粒子 + 轻微风铃（只在 {@link #charging} 为真时调用 ✓ 密度 ∝ 速率 ✓） */
    private void showCharging(ServerLevel level, BlockPos pos) {
        if (ModConfig.pedestalParticles()) {
            int period = particlePeriod();
            if (ticks % period == 0) {
                // END_ROD / SNOWFLAKE 交替 ⇒ 白蓝色的"冷星/霜"观感 ✓
                ParticleOptions particle = (ticks % (period * 2) == 0) ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE;
                level.sendParticles(particle,
                        pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                        1,                                    // 颗数
                        0.22D, 0.12D, 0.22D,                   // 散布
                        0.01D);                               // 速度（几乎不动 ⇒ 像悬浮的星尘 ✓）
            }
        }
        if (ModConfig.pedestalSound() && ticks % SOUND_INTERVAL == 0) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    SOUND_VOLUME, soundPitch());
        }
    }

    /** 粒子节拍：速率越高越密（满速 ⇒ {@value #PARTICLE_PERIOD_AT_FULL_RATE} tick 一颗 ✓） */
    private int particlePeriod() {
        double fullRate = ModConfig.pedestalChargeMaxPerSecond();
        if (fullRate <= 0.0D || lastRate <= 0.0D) return PARTICLE_PERIOD_MAX;
        double period = PARTICLE_PERIOD_AT_FULL_RATE * (fullRate / lastRate);
        return Mth.clamp((int) Math.round(period), PARTICLE_PERIOD_AT_FULL_RATE, PARTICLE_PERIOD_MAX);
    }

    /** 音高：速率越高越清亮（很小的浮动，听上去只是"活着的"✓） */
    private float soundPitch() {
        double fullRate = ModConfig.pedestalChargeMaxPerSecond();
        if (fullRate <= 0.0D) return SOUND_PITCH_MIN;
        double ratio = Mth.clamp(lastRate / fullRate, 0.0D, 1.0D);
        return SOUND_PITCH_MIN + (float) ratio * SOUND_PITCH_RANGE;
    }

    // ============================================================
    //  §601 自动化：把"台上那一件水晶"暴露成标准物品容器
    //  ⚠ 只**新增**这一个 capability ✗ 台座原有的水晶交互（右键放/取、充能、吸收方块）一行都没动 ✗
    // ============================================================

    /**
     * 物品容器视图（六面都通 ✓）：漏斗 / 管道 / 其它模组可以直接<b>塞水晶</b>、也可以<b>取走</b> ✓。
     * <p>⚠ 非 final（{@link #reviveCaps()} 要重建 ✓ Forge 的标准写法 ✓）。
     */
    private LazyOptional<net.minecraftforge.items.IItemHandler> itemHolder =
            LazyOptional.of(() -> new com.mofengbaizhi.tinkersnewlife.content.menu.ElderManaPedestalItemHandler(this));

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        // §601 物品容器：**六面都给**（自动化要的就是"随便哪面都能接"✓）
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            return itemHolder.cast();
        }
        return super.getCapability(cap, side);   /* §570 摘掉台座只读 FE 面（只走本模组 EE ✓） */
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHolder.invalidate();
    }

    /** §601 区块重载会走这里（invalidate 之后必须重建 ⇒ 否则重载后自动化就"看不见"容器了 ✗） */
    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHolder = LazyOptional.of(
                () -> new com.mofengbaizhi.tinkersnewlife.content.menu.ElderManaPedestalItemHandler(this));
    }

    // ============================================================
    //  同步 / 存档
    // ============================================================

    /** 改了台座上的水晶 ⇒ 标脏 + 发包（客户端渲染器要立刻看到 ✓） */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
            // §556 双保险（用户实测：HUD 数值一直不刷新 ✗ ⇒ 说明上面那条路没把 BE 数据送到客户端 ✗）：
            //     直接给附近玩家发一次"方块实体数据包" ✓ —— 这是原版就有、且不依赖区块标记的可靠路径 ✓
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                var pkt = getUpdatePacket();
                if (pkt != null) {
                    for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
                        if (sp.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                                worldPosition.getZ() + 0.5D) < 64.0D * 64.0D) {
                            sp.connection.send(pkt);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!crystal.isEmpty()) tag.put(KEY_CRYSTAL, crystal.save(new CompoundTag()));
        if (cache > 0.0D) tag.putDouble(KEY_CACHE, cache);       // §553 缓存持久化 ✓
        if (eeBuffer > 0.0D) tag.putDouble(KEY_BUFFER, eeBuffer); // §557 外来 EE 暂存也持久化 ✓
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // ⚠ 这里**直接**赋值、不走 setCrystal：load 在客户端也会被调用（见 handleUpdateTag），
        //   而 setCrystal 会 sendBlockUpdated ⇒ 客户端无谓发包/递归 ✗
        crystal = tag.contains(KEY_CRYSTAL) ? ItemStack.of(tag.getCompound(KEY_CRYSTAL)) : ItemStack.EMPTY;
        cache = tag.contains(KEY_CACHE) ? tag.getDouble(KEY_CACHE) : 0.0D;   // §553 读回缓存 ✓
        eeBuffer = tag.contains(KEY_BUFFER) ? tag.getDouble(KEY_BUFFER) : 0.0D;  // §557 读回外来暂存 ✓
        if (!crystal.isEmpty()) crystal.setCount(1);
    }

    /** 区块加载时同步给客户端（水晶的初始显示 ✓） */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        // §526 无论空不空都写这个键 ⇒ 客户端 load() 一定会把『已取下』读成空栈 ✓
        //      （原来只在非空时写：虽然 load() 的 else 分支也会置空 ✓，但显式写更不容易被后人改坏 ✓）
        tag.put(KEY_CRYSTAL, crystal.isEmpty() ? new CompoundTag() : crystal.save(new CompoundTag()));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    /**
     * 运行中改动时的同步包。
     * <p>⚠ 必须覆写：{@code Level#sendBlockUpdated} 内部调的就是它，而
     * {@code BlockEntity} 的默认实现返回 {@code null}（= 什么都不发）✗ —— 不覆写的话
     * 放上去的水晶要等到重新加载区块才看得见 ✗。
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
