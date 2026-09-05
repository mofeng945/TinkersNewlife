package com.mofengbaizhi.tinkersnewlife.content.item;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.Polarice3.Goety.common.items.magic.DarkWand;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.goety.ModularStaffGoety;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 模块化魔杖 · 诡厄"真法杖"形态（折中方案：仅在安装了诡厄巫法时注册，物品 id 与普通魔杖完全一致，
 * 不做硬依赖——未装诡厄的环境注册 {@link ModularStaffItem}，本类不会被加载）。
 *
 * <p>实现 {@link IWand} 并复刻诡厄真法杖的两块核心：
 * <ul>
 *   <li><b>施法管线</b>：use/onUseTick/releaseUsing/finishUsingItem/getUseDuration/getUseAnimation
 *       （以及 useOn/interactLivingEntity）全部委托给内部 {@link DarkWand} 实例——诡厄原生施法
 *       （灵魂消耗、长吟唱蓄力、冷却、粒子/音效）逐字节一致，客户端与服务端都走同一条原生路径，
 *       不再需要"换手拿真法杖"的桥接。</li>
 *   <li><b>聚晶存储</b>：{@link #initCapabilities} 在匠魂工具能力之外挂上诡厄的
 *       {@link SoulUsingItemHandler}（真法杖同款 1 格聚晶槽，{@code IWand.getFocus} 硬性要求该类型），
 *       装备中的聚晶由服务端 {@link #mirrorEquippedFocus} 写入并随物品栈同步，诡厄的
 *       当前聚晶 HUD / 冷却图标（CurrentFocusGui）无需任何自定义代码即可原生显示。</li>
 * </ul>
 *
 * <p>⚠ <b>严禁在物品注册期创建任何 Item 实例</b>：Item 构造器会把自身登记为 ITEMS 注册表的
 * intrusive holder（须由后续注册认领），未注册的 Item 实例会在注册表冻结时报
 * "Some intrusive holders were not registered" 崩溃。因此 {@link DarkWand} 委托必须懒加载，
 * {@link #getSpellType()} 也只返回枚举常量。
 *
 * <p>注意：本类直接引用诡厄类，只能在诡厄存在时被加载/实例化（注册分支已按 ModList 判定；
 * 其它宿主类调用本类静态方法前必须先过 {@code ModList.isLoaded("goety")} 或
 * {@code Class.forName("...IWand")} 守卫）。
 */
public class GoetyStaffItem extends ModularStaffItem implements IWand {

    /** 诡厄施法委托实例：懒加载，首次实际施法（注册表冻结后的运行时）才创建 */
    @Nullable
    private DarkWand goetyWand;

    private DarkWand goetyWand() {
        if (goetyWand == null) {
            goetyWand = new DarkWand();
        }
        return goetyWand;
    }

    public GoetyStaffItem(Properties properties) {
        super(properties);
    }

    // ================= IWand =================

    @Override
    public SpellType getSpellType() {
        // 与 DarkWand() 无参构造一致（NONE）。绝不在此创建实例：
        // 注册期 Goety 可能在任何时刻查询 IWand 的 getSpellType，new Item 会泄漏 intrusive holder
        return SpellType.NONE;
    }

    // ================= 原版/匠魂使用管线（巫法模式 → 诡厄原生；铁魔法模式 → 匠魂/铁魔法） =================

    private static boolean inGoetyMode(ItemStack stack) {
        return !stack.isEmpty() && ModularStaffGoety.getMode(stack) == ModularStaffGoety.MODE_GOETY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (inGoetyMode(stack)) {
            // 委托 DarkWand.use：它读取手持物品栈 = 本魔杖，并从魔杖自带槽取聚晶，
            // 完整原生施法（含 startUsingItem 长吟唱），双端一致
            return goetyWand().use(level, player, hand);
        }
        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return inGoetyMode(stack) ? goetyWand().getUseDuration(stack) : super.getUseDuration(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return inGoetyMode(stack) ? goetyWand().getUseAnimation(stack) : super.getUseAnimation(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int timeLeft) {
        if (inGoetyMode(stack)) {
            goetyWand().onUseTick(level, entity, stack, timeLeft);
        } else {
            super.onUseTick(level, entity, stack, timeLeft);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (inGoetyMode(stack)) {
            goetyWand().releaseUsing(stack, level, entity, timeLeft);
        } else {
            super.releaseUsing(stack, level, entity, timeLeft);
        }
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (inGoetyMode(stack)) {
            // 诡厄 useOn：RecallFocus/触媒/方块型法术等右键方块逻辑与真法杖一致
            return goetyWand().useOn(context);
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (inGoetyMode(stack)) {
            // 诡厄 interactLivingEntity：CommandFocus 仆从管理/触媒法术等右键实体逻辑与真法杖一致
            return goetyWand().interactLivingEntity(stack, player, target, hand);
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (inGoetyMode(stack)) {
            return goetyWand().finishUsingItem(stack, level, entity);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    // ================= 能力：匠魂工具能力 + 诡厄聚晶槽 =================

    /**
     * 匠魂 {@code ModifiableItem} 覆写了 initCapabilities（返回 ToolCapabilityProvider），
     * 会盖掉 {@link IWand} 的默认实现，因此这里手动组合：ITEMS_HANDLER → 诡厄聚晶槽，
     * 其余 → 匠魂工具能力。
     */
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new StaffCaps(stack, nbt);
    }

    private static final class StaffCaps implements ICapabilityProvider {
        private final ToolCapabilityProvider tinkerCaps;
        private final SoulUsingItemHandler focusHandler;
        private final LazyOptional<IItemHandler> holder;

        StaffCaps(ItemStack stack, @Nullable CompoundTag initNbt) {
            this.tinkerCaps = new ToolCapabilityProvider(stack);
            // 诡厄真法杖同款聚晶槽：内容随物品栈 tag（getShareTag 默认写入 "cap"）同步/存档
            this.focusHandler = new SoulUsingItemHandler(stack);
            // 磁盘/旧数据恢复：tag（或加载时传入的 nbt）里已有 "cap"（镜像落盘或网络写入）时喂回处理器
            CompoundTag tag = stack.getTag() != null ? stack.getTag() : initNbt;
            if (tag != null && tag.contains("cap", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                try {
                    focusHandler.deserializeNBT(tag.getCompound("cap"));
                } catch (Throwable ignored) {
                }
            }
            this.holder = LazyOptional.of(() -> (IItemHandler) focusHandler);
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ITEM_HANDLER) {
                return holder.cast();
            }
            return tinkerCaps.getCapability(cap, side);
        }
    }

    // ================= 服务端：聚晶镜像 =================

    /**
     * 服务端：把玩家"装备中聚晶"（聚晶包 idx 指向的聚晶）镜像写入魔杖自带槽。
     * 巫法模式 → 写聚晶；铁魔法模式 → 清空（避免诡厄当前聚晶 HUD 在铁魔法模式误显示）。
     * 内容相同则跳过；写入后把槽内栈原位替换为副本以强制广播（客户端拿到新 "cap"）。
     *
     * @return 是否实际发生了写入
     */
    public static boolean mirrorEquippedFocus(ServerPlayer player, ItemStack staff) {
        if (player == null || staff == null || staff.isEmpty()) return false;
        if (!(staff.getItem() instanceof GoetyStaffItem)) return false;
        ItemStack focus = ModularStaffGoety.equippedFocusOf(player, staff);
        try {
            SoulUsingItemHandler handler = SoulUsingItemHandler.get(staff);
            ItemStack current = handler.getSlot();
            if (ItemStack.isSameItemSameTags(current, focus)) return false;
            handler.extractItem();
            if (!focus.isEmpty()) {
                handler.insertItem(focus.copy());
            }
            // 直接落盘到魔杖 tag：即使尚未广播/客户端未同步，存档也不丢聚晶
            staff.getOrCreateTag().put("cap", handler.serializeNBT());
            // 槽内换入副本，确保服务端广播检测到栈变化（原栈 tag 已被原地修改，但广播比较依赖对象/内容差异，
            // 稳妥起见替换为新对象再放回原槽）
            replaceInInventory(player, staff);
            return true;
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[魔杖·真法杖] 聚晶镜像写入失败：", t);
            return false;
        }
    }

    /** 把魔杖栈换回玩家背包原槽（主手/副手），触发容器广播 */
    private static void replaceInInventory(ServerPlayer player, ItemStack staff) {
        try {
            InteractionHand hand = null;
            if (player.getMainHandItem() == staff) {
                hand = InteractionHand.MAIN_HAND;
            } else if (player.getOffhandItem() == staff) {
                hand = InteractionHand.OFF_HAND;
            }
            if (hand != null) {
                player.setItemInHand(hand, staff.copy());
            }
        } catch (Throwable ignored) {
        }
    }
}
