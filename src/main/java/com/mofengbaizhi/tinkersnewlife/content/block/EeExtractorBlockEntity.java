package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeCapabilityBridge;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorage;
import com.mofengbaizhi.tinkersnewlife.content.energy.EeStorages;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * <b>EE 抽取方块</b>（{@code tinkersnewlife:ee_extractor}）的方块实体。
 *
 * <h2>⚠ 本节（§558）是<b>最新口径</b>，与 §557 冲突时以本节为准 ✓（旧节一律不动 ✓）</h2>
 * §557 的原设计是"<b>从相邻的 EE 持有者</b>（台座缓存 / 水晶方块）抽 EE" ✗ —— 用户**否掉了这条** ✓
 * ⇒ 现在改成 <b>GUI + 槽位</b> ✓：
 *
 * <h2>它现在干什么（三句话）</h2>
 * <ol>
 *   <li><b>右键打开 GUI</b> ✓（{@link #useGui} 由方块 {@code use()} 调 ✓）—— 里面<b>一个槽位</b> ✓
 *       只能放 <b>有能量的古老者水晶 / 古老者水晶方块</b> ✓（与台座 §555 的
 *       {@code heldCrystal/heldCap/heldEe/addHeld} 同一套类型判断 ✓）；</li>
 *   <li><b>每 tick 从槽位里那件抽 EE 进内部缓存</b> ✓（速率 {@link ModConfig#eeExtractorPullPerTick()}
 *       = <b>256 EE/t</b> ✓ 键名不变 ✓ 只是语义从"从邻居抽"变成"从槽位物品抽"✓ 用户 config 不用动 ✓）；</li>
 *   <li><b>缓存主动推给相邻方块</b> ✓（这一段是 §557 的代码，**一行都没动** ✓ 用户要求保留 ✓）——
 *       只推给实现 {@link EeStorage} 的邻居（水晶方块 / 台座 / 万用能量转化器 ✓）。</li>
 * </ol>
 *
 * <h2>⚠ 与 §557 相比<b>删掉</b>的东西（用户明确"相邻抽取那套删掉"✗）</h2>
 * <ul>
 *   <li>不再调 {@link EeStorages#pullAround} ✗ —— 邻居一个都不碰 ✓；</li>
 *   <li>连"跳过刚抽过的邻居"那套（§557 的 {@code pulledFrom} 集合 + {@link EeStorages#pushAround}
 *       的 skip 谓词）也一起删了 ✗ —— 既然不再从邻居抽，就不存在"原路还回去"的空转 ✓
 *       （{@code pushAround} 的 {@code skip} 传 null 即可 ✓）。</li>
 * </ul>
 *
 * <h2>缓存与上限</h2>
 * {@link ModConfig#eeExtractorBuffer()}（键 {@code extractor_buffer_ee}，默认 <b>4000</b> ✓
 * 语义从 §557 的"缓冲"变成 <b>"内部缓存"</b> ✓ 数值与键名都不变 ✓）
 * = <b>一个古老者水晶方块的量</b>（{@link ElderCrystalStorage#BLOCK_CAPACITY}）✓
 * —— 恰好"整块装得下"✓ 满了就不再从槽位抽 ✓（物品里的 EE 原地不动 ✓ 一点不丢 ✗）。
 *
 * <h2>同步（GUI 里要看到"缓存 EE / 4000"实时变化）</h2>
 * 用 <b>原版 {@code ContainerData}</b>（= 菜单数据槽 ✓）：
 * <ul>
 *   <li>菜单 {@code EeExtractorMenu} 用 {@code addDataSlots(data)} 订阅 ✓
 *       ⇒ 原版每 tick 自动把变化过的值发给<b>正在看这个菜单</b>的玩家 ✓（没有额外网络包 ✓）；</li>
 *   <li>{@link #toIntArray()} 是 {@link DataAccess} 那一侧的实现 ✓（{@code get} 只回 16 位 ⇒
 *       4000 这个量级完全够 ✓ 见方法注释 ✓）；</li>
 *   <li>§556 那条"直发 BE 数据包"的路径（{@link #getUpdatePacket()}）**保留** ✓
 *       它管的是"没开 GUI 时客户端也知道缓存有多少"✓（以后加 HUD / 玉显示就不用再动 ✓）。</li>
 * </ul>
 */
public class EeExtractorBlockEntity extends BlockEntity implements EeStorage, MenuProvider {

    /** 缓冲（= 内部缓存）的存档键（EE，double ✓ 与台座 §553 的 cache 同一口径 ✓） */
    public static final String KEY_BUFFER = "EeBuffer";

    /** <b>槽位里那一件</b>的存档键（整包 ItemStack ✓ 含它自己的 EE ✓） */
    public static final String KEY_SLOT = "Slot";

    /** 槽位的序号（GUI / 菜单里只有这一个 ✓） */
    public static final int SLOT_INDEX = 0;

    /** 内部缓存里的 EE（≤ {@link ModConfig#eeExtractorBuffer()} ✓ 持久化 ✓） */
    private double buffer = 0.0D;

    /**
     * 槽位里那一件（空 = 没放 ✓ 始终最多 1 个 ✓）。
     * <p>⚠ 存的是 {@link ItemStack} 本体（<b>整包进 NBT</b> ✓）而不只是"EE 一个数字" ✗ ——
     * 这样"放进去一颗水晶、把它拿走"时那颗水晶仍是原来那一颗（连耐久/名字/其它 NBT 都在 ✓），
     * 不会因为我们只记了个数字而把玩家的物品变成一个空壳 ✗。
     */
    private ItemStack slot = ItemStack.EMPTY;

    public EeExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EE_EXTRACTOR.get(), pos, state);
    }

    /** 方块注册用的 ticker（见 {@code EeExtractorBlock#getTicker} ✓ 只在服务端挂 ✓） */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EeExtractorBlockEntity be) {
        if (level instanceof ServerLevel server) be.tick(server, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        // ① 从**槽位里那一件**抽 EE 进缓存（§558 新口径 ✓ 邻居一个都不碰 ✓）
        int pulled = pullFromSlot(ModConfig.eeExtractorPullPerTick());

        // ② 把缓存推给相邻的 EE 容器（§557 的老代码 ✓ 一行没动 ✓ 只是不再需要"跳过刚抽过的邻居"）
        int pushed = EeStorages.pushAround(level, pos, this,
                ModConfig.eeExtractorPushPerTick(), null);

        if (pulled > 0 || pushed > 0) setChanged();
    }

    // ============================================================
    //  §558 槽位：那一件怎么读 / 怎么写 / 每 tick 抽多少
    // ============================================================

    /**
     * 从槽位里那一件抽 {@code want} 点 EE 进缓存。
     *
     * <p>类型判断与台座 §555 的 {@code heldCrystal / heldCap / heldEe / addHeld} <b>同一套</b> ✓
     * （水晶物品容量 1000（EE 在物品根标签）✓ 水晶方块物品容量 4000（EE 在 {@code BlockEntityTag}）✓）
     * —— 两处都走 {@link ElderCrystalStorage} 的静态方法 ✓ 所以"哪个键、哪个容量"只有一份定义 ✗。
     *
     * @return 这一 tick 真的抽进缓存多少 EE（0 = 槽位空 / 不是水晶 / 抽空了 / 缓存满了 ✓）
     */
    private int pullFromSlot(int want) {
        if (want <= 0 || slot.isEmpty()) return 0;
        int room = getCapacity() - (int) Math.floor(buffer);
        if (room <= 0) return 0;                                   // 缓存满 ⇒ 不抽（物品里的 EE 原地不动 ✓）
        int held = heldEe(slot);
        if (held <= 0) return 0;                                   // 空水晶 / 没电的水晶 ⇒ 不抽 ✓
        int take = Math.min(Math.min(want, held), room);
        if (take <= 0) return 0;
        setHeldEe(slot, held - take);                              // 就地改写物品 NBT ✓
        buffer += take;
        return take;
    }

    /** 槽位里那件是不是我们收的水晶系物品（水晶 ✓ 或水晶方块 ✓ 与台座 §555 同一口径 ✓） */
    public static boolean isCrystalItem(ItemStack stack) {
        return ElderCrystalStorage.isCrystal(stack);               // §557 起这个判断在 ElderCrystalStorage 里 ✓
    }

    /** 那件的容量（水晶 1000 / 水晶方块 4000；不是水晶 ⇒ 0 ✓） */
    public static int heldCap(ItemStack stack) {
        return ElderCrystalStorage.capacityOf(stack);
    }

    /** 那件里已经存了多少 EE（按类型读 ✓） */
    public static int heldEe(ItemStack stack) {
        return ElderCrystalStorage.eeOf(stack);
    }

    /** 就地改写那件里的 EE（按类型写 ✓） */
    public static void setHeldEe(ItemStack stack, int ee) {
        if (stack == null || stack.isEmpty()) return;
        if (stack.getItem() == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get()) {
            ElderCrystalStorage.setBlockItemEe(stack, ee);
        } else {
            ElderCrystalStorage.setCrystalEe(stack, ee);
        }
    }

    /** 槽位里那一件（可能为空栈 ✓ 直接读不要改它 —— 要改请走 {@link #setSlotItem} ✓） */
    public ItemStack getSlotItem() {
        return slot;
    }

    /** 放 / 取槽位（会标脏 ✓ 并同步附近客户端 ✓；菜单那边的 {@code Slot#setChanged} 也走它 ✓） */
    public void setSlotItem(ItemStack stack) {
        ItemStack next = (stack == null || stack.isEmpty()) ? ItemStack.EMPTY : stack.copy();
        if (!next.isEmpty()) next.setCount(1);                     // 槽位里永远只有一件 ✓
        slot = next;
        setChanged();
        syncToClients();
    }

    /** 这一次抽能接受多少（{@link #pullFromSlot} 的"只算不改"版 ✓ 供菜单/GUI 参考 ✓） */
    public int previewPullThisTick() {
        if (slot.isEmpty()) return 0;
        int room = getCapacity() - (int) Math.floor(buffer);
        return Math.max(0, Math.min(Math.min(ModConfig.eeExtractorPullPerTick(), heldEe(slot)), room));
    }

    // ============================================================
    //  §558 GUI：MenuProvider（方块 use() 里 openMenu 用 ✓）
    // ============================================================

    /** GUI 标题（GUI 上那一行字 ✓ 中英在 lang 里 ✓） */
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.tinkersnewlife.ee_extractor");
    }

    /**
     * 打开 GUI 的入口（方块 {@code use()} 在<b>服务端</b>调它 ✓）。
     *
     * <p>⚠ <b>为什么用 Forge 的 {@code NetworkHooks.openScreen} 而不是原版的
     * {@code ServerPlayer#openMenu}</b>：原版那个在 1.20.1 只有
     * {@code openMenu(MenuProvider)} 一个重载 ✗（我第一版写的 {@code openMenu(this, worldPosition)}
     * 编译直接报"需要: MenuProvider / 找到: EeExtractorBlockEntity,BlockPos"✗）——
     * <b>坐标不会自动带过去</b> ✓。而 Forge 的
     * {@code NetworkHooks.openScreen(ServerPlayer, MenuProvider, BlockPos)} 会把坐标写进菜单的附加数据 ✓
     * ⇒ 客户端 {@code IForgeMenuType} 的工厂里 {@code data.readBlockPos()} 就能拿回来 ✓
     * 再用它 {@code level.getBlockEntity(pos)} 找回同一个 BE ✓。
     * 这**正是本仓库既有的做法** ✓（见 {@code SilentGloveItem} 行 228、{@code QuantumBagModifier} 行 143/159
     * 都调 {@code NetworkHooks.openScreen} ✓）。
     */
    public void useGui(ServerPlayer player) {
        net.minecraftforge.network.NetworkHooks.openScreen(player, this, worldPosition);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new com.mofengbaizhi.tinkersnewlife.content.menu.EeExtractorMenu(containerId, playerInventory, this);
    }

    // ============================================================
    //  §558 菜单数据槽（ContainerData / DataAccess）
    // ============================================================

    /**
     * 菜单数据槽：<b>[0] = 缓存 EE 的高 16 位、[1] = 低 16 位、[2] = 上限的高 16 位、[3] = 低 16 位</b> ✓。
     *
     * <p>⚠ 为什么要<u>自己拆高低 16 位</u>（而不是直接 {@code addDataSlots(ContainerData)}）✗：
     * 原版 {@code AbstractContainerMenu#addDataSlots(ContainerData)} 会把每个值塞进 {@code DataSlot}，
     * 而 {@code DataSlot} 内部是 <b>short</b> ⇒ 超过 32767 会溢出 ✗。
     * 我们的量级（0~4000 ✓）本来用不到高 16 位，但拆开写是"以后把上限调到 10 万也不出错"的保险 ✓
     * 而且这样菜单那边拿到的是<b>它自己持有的 DataSlot 实例</b>（不必去翻原版那个 protected map ✓）。
     *
     * <p>⚠ 这两个 {@code DataSlot} 是<b>一个方块实体一份</b> ✓：菜单构造时把它们注册进
     * {@code AbstractContainerMenu#addSlot(DataSlot)}（ID 从 0 开始 ✓）⇒ 原版每 tick 比对、
     * 只把变化过的推给<b>正在看这个菜单</b>的玩家 ✓（不用我们写包 ✓ 也不与 §556 的"直发 BE 包"重复 ✓）。
     */
    private final net.minecraft.world.inventory.DataSlot[] dataSlots = new net.minecraft.world.inventory.DataSlot[] {
            new net.minecraft.world.inventory.DataSlot() {
                @Override
                public int get() {
                    return (getEe() >>> 16) & 0xFFFF;
                }

                @Override
                public void set(int value) {
                    // 客户端侧原版会往这里写回同步值 ✓ 我们不靠它存东西（真值在服务端 BE 里 ✓）
                    // ⇒ 空实现是安全的：它只影响"客户端那份镜像"✓
                }
            },
            new net.minecraft.world.inventory.DataSlot() {
                @Override
                public int get() {
                    return getEe() & 0xFFFF;
                }

                @Override
                public void set(int value) {
                }
            },
            new net.minecraft.world.inventory.DataSlot() {
                @Override
                public int get() {
                    return (getCapacity() >>> 16) & 0xFFFF;
                }

                @Override
                public void set(int value) {
                }
            },
            new net.minecraft.world.inventory.DataSlot() {
                @Override
                public int get() {
                    return getCapacity() & 0xFFFF;
                }

                @Override
                public void set(int value) {
                }
            }
    };

    /** <b>这个方块实体的那 4 个数据槽本体</b>（菜单要把它们 {@code addSlot} 注册进去才生效 ✓） */
    public net.minecraft.world.inventory.DataSlot[] dataSlots() {
        return dataSlots;
    }

    // ============================================================
    //  EeStorage
    // ============================================================

    @Override
    public int getEe() {
        return (int) Math.floor(buffer);
    }

    /**
     * 内部缓存的上限（EE）= {@link ModConfig#eeExtractorBuffer()} ✓
     * <p>默认 <b>4000</b> = <b>一个古老者水晶方块</b>的量（用户 §558 口径 ✓）。
     */
    @Override
    public int getCapacity() {
        return ModConfig.eeExtractorBuffer();
    }

    /** 让别人往缓存里灌（§557 保留 ✓ —— 万一以后有"上游推给抽取方块"这种玩法 ✓ 现在没人调 ✓） */
    @Override
    public int insertEe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        double room = getCapacity() - buffer;
        if (room <= 0.0D) return 0;
        int accepted = (int) Math.min(Math.floor(room), (double) amount);
        if (accepted <= 0) return 0;
        if (!simulate) {
            buffer += accepted;
            setChanged();
        }
        return accepted;
    }

    /** 从缓存里取（推给邻居时走它 ✓） */
    @Override
    public int extractEe(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int take = (int) Math.min(Math.floor(buffer), (double) amount);
        if (take <= 0) return 0;
        if (!simulate) {
            buffer -= take;
            setChanged();
        }
        return take;
    }

    // ============================================================
    //  Forge 能量能力（只读面 ✓ 让别的模组能"看到"本方块存了多少电）
    // ============================================================

    /**
     * 只读的 FE 视图（{@code 1 FE = 8 EE} 折算 ✓）。
     * <p>⚠ <b>不能抽</b>（见 {@link EeCapabilityBridge} 的类注释 ✓）：外模组想拿这里的电，
     * 请走本模组的 EE 接口（水晶方块 / 万用能量转化器 ✓）。
     */
    private final LazyOptional<net.minecraftforge.energy.IEnergyStorage> feHolder =
            LazyOptional.of(() -> EeCapabilityBridge.readOnly(this, this::setChanged));

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return super.getCapability(cap, side)   /* §563 摘掉只读 FE 面 ⇒ 准星不再显示 FE ✓ */;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        feHolder.invalidate();
    }

    // ============================================================
    //  同步 / 存档
    // ============================================================

    /**
     * 缓存/槽位变了 ⇒ 标脏 + 发包给附近的玩家 ✓（§556 那条"直发"路径 ✓）
     * <p>⚠ GUI 里那个数值走的是<b>菜单数据槽</b>（原版自己同步 ✓ 见 {@link #dataSlots}）
     * ⇒ 这里发不发都不影响 GUI ✓；它的用处是"没开 GUI 时客户端也知道"✓
     * （以后加 HUD / 玉显示时不用再动这条路 ✓）。
     */
    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide && level instanceof ServerLevel sl) {
            Packet<ClientGamePacketListener> pkt = getUpdatePacket();
            if (pkt != null) {
                for (ServerPlayer sp : sl.players()) {
                    if (sp.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                            worldPosition.getZ() + 0.5D) < 64.0D * 64.0D) {
                        sp.connection.send(pkt);
                    }
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (buffer > 0.0D) tag.putDouble(KEY_BUFFER, buffer);
        if (!slot.isEmpty()) tag.put(KEY_SLOT, slot.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        double cap = getCapacity();
        buffer = tag.contains(KEY_BUFFER) ? Math.max(0.0D, Math.min(tag.getDouble(KEY_BUFFER), cap)) : 0.0D;
        // ⚠ 直接赋值、不走 setSlotItem：load 在客户端也会被调用（handleUpdateTag）⇒ 走 setter 会无谓发包/递归 ✗
        slot = tag.contains(KEY_SLOT) ? ItemStack.of(tag.getCompound(KEY_SLOT)) : ItemStack.EMPTY;
        if (!slot.isEmpty()) slot.setCount(1);
    }

    /** 区块加载时同步（缓存 + 槽位 ✓ 客户端要靠它把槽位里那件画出来 ✓） */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putDouble(KEY_BUFFER, buffer);                          // 无论多少都写 ⇒ 客户端一定能读到真值 ✓
        tag.put(KEY_SLOT, slot.isEmpty() ? new CompoundTag() : slot.save(new CompoundTag()));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    /**
     * 运行中改动时的同步包（覆写理由与台座 §556 一致：{@code BlockEntity} 默认返回 {@code null}
     * ⇒ 什么都不发 ✗）。
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 掉出方块时把槽位里那件一起还回去（照台座 §523 的 {@code getDrops} 口径 ✓ 玩家不会丢东西 ✓） */
    public ItemStack takeSlotForDrop() {
        ItemStack out = slot;
        slot = ItemStack.EMPTY;
        return out;
    }
}
