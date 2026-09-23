package com.mofengbaizhi.tinkersnewlife.content.menu;

import com.mofengbaizhi.tinkersnewlife.content.ModMenus;
import com.mofengbaizhi.tinkersnewlife.content.block.EeExtractorBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;

/**
 * <b>EE 抽取方块的 GUI 菜单</b>（§558）。
 *
 * <h2>布局（与屏幕 {@code EeExtractorScreen} 用的是同一组常量 ✓ 别再两处各写一份 ✗）</h2>
 * <pre>
 *   y+21         标题条下沿（§604 起标题条高 21 ✓ 仿量子背包 ✓）
 *   y+36         提示行（"放有电的水晶" ✓ 屏幕画 ✓）
 *   y+61         信息行（"缓存 EE：x / 4000" ✓ 屏幕画 ✓）
 *   y+71         分隔线
 *   y+73         "物品栏"标签
 *   y+84  .. 138 玩家背包 3×9
 *   y+142 .. 160 快捷栏
 *   imageHeight = 166
 * </pre>
 *
 * <h2>数据同步（§605 改：原版标准 {@code ContainerData} ✓）</h2>
 * {@code addDataSlots(be.containerData())} ⇒ 服务端每 tick 把真值写进那份数据 ✓ 原版只把**变化过**的推给
 * "正在看这个菜单"的玩家 ✓ 客户端由同步器写**同一份数组** ✓ 屏幕 {@link #cachedEe()} 读它 ✓
 * （不用我们自己写包 ✓ 也不与 §556 的"直发 BE 包"重复 ✓ —— 那个管的是"没开 GUI 时"✓）。
 * <p>⚠ 上一版用的是 4 个手写 {@code DataSlot}（还自己拆高低 16 位 ✗）⇒ 客户端的 {@code set(int)} 是空实现 ✗
 * ⇒ 界面数字**只在放/取水晶时才变** ✗（用户实测 ✓ 见 §605 ✓）。
 *
 * <h2>槽位规则</h2>
 * <ul>
 *   <li>{@code mayPlace}：只收<b>有能量的</b>古老者水晶 / 古老者水晶方块 ✓（走
 *       {@link EeExtractorSlotHandler#isItemValid} 那一份唯一实现 ✓）；</li>
 *   <li>{@code stillValid}：玩家离方块 &gt; 8 格就自动关（原版容器的标准判定 ✓ 照抄原版那两行 ✓）；</li>
 *   <li>{@code quickMoveStack}：Shift 点 = 抽取槽 ⇄ 玩家背包 ✓（抽取槽 → 背包时**先试快捷栏** ✓
 *       与 {@code SilentGloveContainer} 同一套写法 ✓）。</li>
 * </ul>
 *
 * <h2>关闭界面时槽位里那件</h2>
 * {@link #removed} 把抽屉里剩下的东西<b>还给玩家</b> ✓（背包满就掉在脚下 ✓ 绝不凭空消失 ✗）。
 */
public class EeExtractorMenu extends AbstractContainerMenu {

    // ---- 布局常量（屏幕也读这几个 ✓ 唯一定义 ✓）----
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 166;
    /** 抽取槽的坐标（屏幕/菜单同源 ✓） */
    public static final int SLOT_X = 80;
    public static final int SLOT_Y = 33;
    /**
     * 信息行的 Y（屏幕上画"缓存 EE：x / 4000" ✓）。
     * <p>§602 从 64 上移到 <b>61</b> ✓ —— 原来占 64..72 ✗，会顶到下面那条物品栏分隔线（现在是 y=71 ✓）✗；
     * 现在占 61..69 ✓ 与分隔线之间留 1 px ✓ 与"物品栏"标签（73..81 ✓）也不再打架 ✓。
     */
    public static final int INFO_Y = 61;
    public static final int INV_X = 8;
    public static final int INV_Y = 84;
    public static final int HOTBAR_Y = 142;

    /** 抽取槽的槽位总数（只有它一个 ✓ 玩家槽位从 1 开始 ✓） */
    private static final int BE_SLOTS = 1;

    /** 客户端/服务端都可能为 null（区块还没到 / 构造顺序）⇒ 全程判空 ✓ 绝不 NPE ✗ */
    @javax.annotation.Nullable
    private final EeExtractorBlockEntity be;

    private final ContainerLevelAccess access;

    /** §605 GUI 数据（缓存 EE / 缓存上限）✓ 由原版同步器推到客户端 ✓ 屏幕读的就是它 ✓ */
    private final net.minecraft.world.inventory.ContainerData data;

    private EeExtractorMenu(int containerId, Inventory playerInventory,
                            @javax.annotation.Nullable EeExtractorBlockEntity be,
                            ContainerLevelAccess access) {
        super(ModMenus.EE_EXTRACTOR.get(), containerId);
        this.be = be;
        this.access = access;
        // §605 GUI 数据：走原版标准 ContainerData（服务端 = BE 那份，客户端 = 同一份对象，
        //   由原版同步器写 ✓）⇒ 屏幕读它就能逐 tick 看到缓存变化 ✓。
        // ⚠ 客户端 BE 还没同步到时（刚打开界面那一两 tick）用一份等长的空数组兜底 ✓
        //   —— 必须**等长** ✗ 否则数据槽下标会错位 ✓。
        this.data = be != null
                ? be.containerData()
                : new net.minecraft.world.inventory.SimpleContainerData(EeExtractorBlockEntity.DATA_COUNT);
        this.addDataSlots(this.data);
        initSlots(playerInventory);
    }

    /** 服务端构造（由 {@code EeExtractorBlockEntity#createMenu} 调 ✓） */
    public EeExtractorMenu(int containerId, Inventory playerInventory, EeExtractorBlockEntity be) {
        this(containerId, playerInventory, be,
                be == null || be.getLevel() == null
                        ? ContainerLevelAccess.NULL
                        : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos()));
    }

    /**
     * 客户端构造（由 {@link ModMenus} 的 {@code IForgeMenuType} 工厂调 ✓ 多出的
     * {@code BlockPos} 是 {@code NetworkHooks.openScreen(player, provider, pos)} 写进去的附加数据 ✓）。
     * <p>⚠ 客户端的方块实体<b>可能还没同步到</b>（刚打开界面的那一两 tick）⇒ 这时走
     * {@code be == null} 的一条路 ✓ 屏幕读 {@link #cachedEe()} 会回 0 ✓ 等到 BE 到了自然就好 ✓
     * （回 0 而不是乱数 ⇒ 界面最多显示一瞬间的 0 ✓ 不会崩 ✗）。
     */
    public EeExtractorMenu(int containerId, Inventory playerInventory, net.minecraft.core.BlockPos pos) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(pos) instanceof EeExtractorBlockEntity e ? e : null,
                ContainerLevelAccess.create(playerInventory.player.level(), pos));
    }

    private void initSlots(Inventory playerInventory) {
        // ① 抽取槽（1 格 ✓ 直接挂在 BE 的那一个字段上 ✓ 见 EeExtractorSlotHandler）
        if (this.be != null) {
            final EeExtractorSlotHandler handler = new EeExtractorSlotHandler(this.be);
            this.addSlot(new SlotItemHandler(handler, EeExtractorBlockEntity.SLOT_INDEX, SLOT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(@Nonnull ItemStack stack) {
                    return handler.isItemValid(EeExtractorBlockEntity.SLOT_INDEX, stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;                        // 一格一件 ✓
                }
            });
        } else {
            // 客户端 BE 还没读到 ⇒ 放一个"只显示、不可动"的空槽，保证下标与玩家槽位一致 ✓
            this.addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, SLOT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(@Nonnull ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }

        // ② 玩家背包 3×9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        // ③ 快捷栏 9
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (slotIndex < BE_SLOTS) {
            // 抽取槽 → 玩家背包（先快捷栏 ✓ 与 SilentGloveContainer 同款）
            if (!this.moveItemStackTo(stack, BE_SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // 玩家背包 → 抽取槽（不能放 ⇒ moveItemStackTo 原样返回 false ✓ 不报错 ✓）
            if (!this.moveItemStackTo(stack, 0, BE_SLOTS, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** 玩家离方块 &gt; 8 格 ⇒ 关掉（原版容器的标准判定 ✓ 照抄 ✓） */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player,
                com.mofengbaizhi.tinkersnewlife.content.ModBlocks.EE_EXTRACTOR.get());
    }

    /** 抽屉里剩下的那件还给玩家（背包满 ⇒ 掉脚下 ✓ 绝不凭空消失 ✗） */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.be == null) return;
        ItemStack left = this.be.getSlotItem();
        if (left.isEmpty()) return;
        this.be.setSlotItem(ItemStack.EMPTY);
        if (!player.getInventory().add(left)) player.drop(left, false);
    }

    /**
     * 给屏幕读"缓存 EE"用的（§605：走 {@link net.minecraft.world.inventory.ContainerData} ✓
     * 客户端读的是**原版同步器写进来的那份数组** ✓ 所以能逐 tick 变化 ✓）。
     */
    public int cachedEe() {
        return this.data.get(EeExtractorBlockEntity.DATA_EE);
    }

    /** 给屏幕读"缓存上限"用的（数组还没被填过 ⇒ 回配置里的值 ✓ 显示不会空 ✗） */
    public int cacheCapacity() {
        int v = this.data.get(EeExtractorBlockEntity.DATA_CAPACITY);
        return v > 0 ? v : com.mofengbaizhi.tinkersnewlife.config.ModConfig.eeExtractorBuffer();
    }

    /** 本菜单对应的方块实体（客户端可能是 null ✓ 调用方要判空 ✓） */
    @javax.annotation.Nullable
    public EeExtractorBlockEntity blockEntity() {
        return this.be;
    }
}
