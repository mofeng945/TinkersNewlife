package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.energy.AmbientEnergySources;
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
public class ElderManaPedestalBlockEntity extends BlockEntity {

    /** 台座上那颗水晶的存档键（物品栈整包存 ✓ 含它自己的 {@code EE} ✓） */
    public static final String KEY_CRYSTAL = "Crystal";

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
    public ItemStack getCrystal() {
        return crystal;
    }

    public boolean hasCrystal() {
        return !crystal.isEmpty();
    }

    /** 台座是否还有地方可充（台座上的水晶没满 **或** 至少有一块紧邻的水晶方块没满 ✓） */
    public boolean hasSpaceForEe() {
        if (!crystal.isEmpty() && ElderCrystalStorage.eeOf(crystal) < ElderCrystalStorage.capacityOf(crystal)) {
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
        if (charging) showCharging(level, pos);
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
     * <p>⚠ 高速率（远大于 1 EE/秒）时这里会一次灌进好几点 ✓（{@code floor(pool)} ✓ 不浪费 ✓）。
     */
    private void settle(ServerLevel level, BlockPos pos) {
        // ① 先看"有没有地方可灌" —— 满了就整车停：**不**问来源、**不**扣燃料、**不**涨凋灵度 ✓
        //    （一箭三雕：不浪费玩家的燃料 ✓ 不白杀植物 ✓ 也省掉最贵的两次范围扫描 ✓）
        if (!hasSpaceForEe()) {
            pool = 0.0D;
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

        pool += gained;
        if (pool < 1.0D) {          // 还没攒够 1 点 EE ⇒ 这一秒什么都不做 ✓（不是"没在充"⇒ 也不演 ✗）
            charging = false;
            return;
        }

        int amount = (int) Math.floor(pool);
        pool -= amount;
        int accepted = distribute(amount);
        if (accepted < amount) {
            // 装不下的部分直接丢掉（说明附近全满了 ⇒ 台座"满了就停"✓ 不囤积 ✓）
            pool = 0.0D;
        }
        charging = accepted > 0;
    }

    /**
     * 把 {@code amount} 点 EE 依次灌给：台座上的水晶物品 → 紧邻的水晶方块 ✓
     *
     * @return 实际灌进去的量（0 = 全满/没目标 ✓）
     */
    private int distribute(int amount) {
        int left = amount;

        // ① 台座上的水晶物品优先
        if (left > 0 && !crystal.isEmpty() && ElderCrystalStorage.isCrystal(crystal)) {
            int added = ElderCrystalStorage.addCrystalEe(crystal, left);
            if (added > 0) {
                left -= added;
                setChanged();       // 物品栈存在方块实体里 ⇒ 必须标脏才写进存档 ✓
            }
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
    //  同步 / 存档
    // ============================================================

    /** 改了台座上的水晶 ⇒ 标脏 + 发包（客户端渲染器要立刻看到 ✓） */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!crystal.isEmpty()) tag.put(KEY_CRYSTAL, crystal.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // ⚠ 这里**直接**赋值、不走 setCrystal：load 在客户端也会被调用（见 handleUpdateTag），
        //   而 setCrystal 会 sendBlockUpdated ⇒ 客户端无谓发包/递归 ✗
        crystal = tag.contains(KEY_CRYSTAL) ? ItemStack.of(tag.getCompound(KEY_CRYSTAL)) : ItemStack.EMPTY;
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
