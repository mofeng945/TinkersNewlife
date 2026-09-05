package com.mofengbaizhi.tinkersnewlife.content.item;

import com.Polarice3.Goety.api.items.magic.IWand;
import com.Polarice3.Goety.api.magic.SpellType;
import com.Polarice3.Goety.common.items.handler.SoulUsingItemHandler;
import com.Polarice3.Goety.common.items.magic.DarkWand;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.goety.ModularStaffGoety;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
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
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 模块化魔杖 · 诡厄"真法杖"形态（折中方案：仅在安装了诡厄巫法时注册，物品 id 与普通魔杖完全一致，
 * 不做硬依赖——未装诡厄的环境注册 {@link ModularStaffItem}，本类不会被加载）。
 *
 * <p>实现 {@link IWand} 并复刻诡厄真法杖的两块核心：
 * <ul>
 *   <li><b>施法管线</b>：use/onUseTick/releaseUsing/finishUsingItem/getUseDuration/getUseAnimation
 *       （以及 useOn/interactLivingEntity）全部委托给诡厄<b>已注册</b>的真法杖实例
 *       （{@link DarkWand} 或其子类，见 {@link #goetyWand()}）——诡厄原生施法
 *       （灵魂消耗、长吟唱蓄力、冷却、粒子/音效）逐字节一致，客户端与服务端都走同一条原生路径，
 *       不再需要"换手拿真法杖"的桥接。</li>
 *   <li><b>聚晶存储</b>：{@link #initCapabilities} 在匠魂工具能力之外挂上诡厄的
 *       {@link SoulUsingItemHandler}（真法杖同款 1 格聚晶槽，{@code IWand.getFocus} 硬性要求该类型），
 *       装备中的聚晶由服务端 {@link #mirrorEquippedFocus} 写入并随物品栈同步，诡厄的
 *       当前聚晶 HUD / 冷却图标（CurrentFocusGui）无需任何自定义代码即可原生显示。</li>
 * </ul>
 *
 * <p>⚠ <b>严禁在本模组内 new 任何 Item/Block 实例</b>：Item 构造器会把自身登记为 ITEMS 注册表的
 * intrusive holder，注册期创建未注册实例会在冻结时报 "intrusive holders were not registered"，
 * 注册后（运行期）创建任何 Item 都会直接抛 "Registry is already frozen"。
 * 因此 {@link #getSpellType()} 只返回枚举常量，DarkWand 委托也复用诡厄自己注册的实例。
 *
 * <p>注意：本类直接引用诡厄类，只能在诡厄存在时被加载/实例化（注册分支已按 ModList 判定；
 * 其它宿主类调用本类静态方法前必须先过 {@code ModList.isLoaded("goety")} 或
 * {@code Class.forName("...IWand")} 守卫）。
 */
public class GoetyStaffItem extends ModularStaffItem implements IWand {

    /**
     * 诡厄施法委托：直接取诡厄<b>已注册</b>的真法杖实例（DarkWand 或其子类）做纯逻辑搬运。
     * <p>⚠ 绝不能 {@code new DarkWand()}：Item 构造器会向 ITEMS 注册表登记 intrusive holder，
     * 注册期创建=冻结崩溃 "intrusive holders were not registered"，注册后创建=运行期
     * "Registry is already frozen"。诡厄注册的真法杖实例方法只读入参/物品栈，无实例状态依赖，
     * 复用它即可（且已应用 RevelationFix 等 mixin，与真法杖行为完全一致）。
     */
    @Nullable
    private DarkWand goetyWand;

    @Nullable
    private DarkWand goetyWand() {
        if (goetyWand == null) {
            for (net.minecraft.world.item.Item it : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (it instanceof DarkWand dw) {
                    goetyWand = dw;
                    break;
                }
            }
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
            DarkWand wand = goetyWand();
            if (wand != null) {
                InteractionResultHolder<ItemStack> result = wand.use(level, player, hand);
                // 【连发】瞬时法术已放完（消耗动作且未进入引导）→ 请求切下一个聚晶
                if (!level.isClientSide && player instanceof ServerPlayer sp
                        && result.getResult().consumesAction() && !sp.isUsingItem()) {
                    ModularStaffGoety.requestAdvance(sp);
                }
                // 【诊断】定位"蓄力放不出"用，确认后移除
                TinkersNewlife.LOGGER.info("[魔杖·真法杖] use side={} 聚晶={} 法术={} 结果={}",
                        level.isClientSide ? "客户端" : "服务端", focusName(stack), spellName(stack), result.getResult());
                return result;
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        DarkWand wand = inGoetyMode(stack) ? goetyWand() : null;
        return wand != null ? wand.getUseDuration(stack) : super.getUseDuration(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        DarkWand wand = inGoetyMode(stack) ? goetyWand() : null;
        return wand != null ? wand.getUseAnimation(stack) : super.getUseAnimation(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean isSelected) {
        if (inGoetyMode(stack)) {
            DarkWand wand = goetyWand();
            if (wand != null) {
                // 匠魂修饰符的逐 tick 维护先跑，再跑诡厄真法杖的逐 tick 维护：
                // DarkWand.inventoryTick 会把当前聚晶的 "Cast Time"/"Soul Use"/"Shots" 等写进魔杖栈 tag，
                // 并驱动聚晶自身 inventoryTick。⚠ 缺了它 getUseDuration 读不到 "Cast Time" → 0，
                // 蓄力/吟唱/持续类法术放不出来（瞬时法术不依赖这些 tag 所以正常）
                super.inventoryTick(stack, level, entity, slot, isSelected);
                wand.inventoryTick(stack, level, entity, slot, isSelected);
                return;
            }
        }
        super.inventoryTick(stack, level, entity, slot, isSelected);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int timeLeft) {
        DarkWand wand = inGoetyMode(stack) ? goetyWand() : null;
        if (wand != null) {
            wand.onUseTick(level, entity, stack, timeLeft);
            // 【诊断】只打关键点：开始蓄力 + 每 20 tick 一次心跳，确认后移除
            int dur = stack.getUseDuration();
            int elapsed = dur - timeLeft;
            if (elapsed <= 1 || timeLeft % 20 == 0) {
                TinkersNewlife.LOGGER.info("[魔杖·真法杖] 蓄力中 side={} 剩余={}/{} 法术={}",
                        level.isClientSide ? "客户端" : "服务端", timeLeft, dur, spellName(stack));
            }
        } else {
            super.onUseTick(level, entity, stack, timeLeft);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        DarkWand wand = inGoetyMode(stack) ? goetyWand() : null;
        if (wand != null) {
            // 【诊断】松手释放路径，确认后移除
            TinkersNewlife.LOGGER.info("[魔杖·真法杖] 松手释放 side={} 剩余={}/{} 法术={}",
                    level.isClientSide ? "客户端" : "服务端", timeLeft, stack.getUseDuration(), spellName(stack));
            wand.releaseUsing(stack, level, entity, timeLeft);
            // 【连发】引导结束（松手释放）→ 请求切下一个聚晶
            if (!level.isClientSide && entity instanceof ServerPlayer sp) {
                ModularStaffGoety.requestAdvance(sp);
            }
        } else {
            super.releaseUsing(stack, level, entity, timeLeft);
        }
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        DarkWand wand = inGoetyMode(stack) ? goetyWand() : null;
        if (wand != null) {
            // 诡厄 useOn：RecallFocus/触媒/方块型法术等右键方块逻辑与真法杖一致
            return wand.useOn(context);
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        DarkWand wand = inGoetyMode(stack) ? goetyWand() : null;
        if (wand != null) {
            // 诡厄 interactLivingEntity：CommandFocus 仆从管理/触媒法术等右键实体逻辑与真法杖一致
            return wand.interactLivingEntity(stack, player, target, hand);
        }
        return super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        DarkWand wand = inGoetyMode(stack) ? goetyWand() : null;
        if (wand != null) {
            // 【诊断】满蓄释放路径（咏唱走完自动触发），确认后移除
            TinkersNewlife.LOGGER.info("[魔杖·真法杖] 满蓄释放 side={} 法术={}",
                    level.isClientSide ? "客户端" : "服务端", spellName(stack));
            ItemStack result = wand.finishUsingItem(stack, level, entity);
            // 【连发】咏唱走完（满蓄释放）→ 请求切下一个聚晶
            if (!level.isClientSide && entity instanceof ServerPlayer sp) {
                ModularStaffGoety.requestAdvance(sp);
            }
            return result;
        }
        return super.finishUsingItem(stack, level, entity);
    }

    // ================= 能力：匠魂工具能力 + 诡厄聚晶槽 =================

    // ════════════════ 魔杖施法增益（吃魔杖特性/攻击力） ════════════════
    // 诡厄法术强度走玩家 Spell 属性（WandUtil.getStats → ModAttributes.getPotency/Range/…，
    // 反编译实证：SoulBolt 等伤害 = stats.getPotency() + 聚晶附魔加成）。
    // 巫法模式下把法杖匠魂攻击力换算成 Spell 属性，通过服务端 tick 给持有者挂瞬态修饰符
    // （不挂在物品 getAttributeModifiers 上 → 不刷屏物品 tooltip）。
    // ⭐ 换算系数 v1 试平衡：改这里即可整体调
    private static final float POTENCY_PER_DMG = 0.25f;    // 每点法杖攻击 → 威力
    private static final float RANGE_PER_DMG = 0.125f;     // → 范围
    private static final float DURATION_PER_DMG = 0.083f;  // → 持续时间
    private static final float RADIUS_PER_DMG = 0.025f;    // → 半径
    private static final float BURNING_PER_DMG = 0.167f;   // → 灼烧
    private static final float VELOCITY_PER_DMG = 0.0125f; // → 弹速

    private static final java.util.UUID UID_POTENCY =
            java.util.UUID.nameUUIDFromBytes("tnl.staff.goety.potency".getBytes());
    private static final java.util.UUID UID_RANGE =
            java.util.UUID.nameUUIDFromBytes("tnl.staff.goety.range".getBytes());
    private static final java.util.UUID UID_DURATION =
            java.util.UUID.nameUUIDFromBytes("tnl.staff.goety.duration".getBytes());
    private static final java.util.UUID UID_RADIUS =
            java.util.UUID.nameUUIDFromBytes("tnl.staff.goety.radius".getBytes());
    private static final java.util.UUID UID_BURNING =
            java.util.UUID.nameUUIDFromBytes("tnl.staff.goety.burning".getBytes());
    private static final java.util.UUID UID_VELOCITY =
            java.util.UUID.nameUUIDFromBytes("tnl.staff.goety.velocity".getBytes());

    /** 玩家 UUID → 当前已施加的属性值（避免每 tick 重复写） */
    private static final java.util.Map<java.util.UUID, double[]> APPLIED_SPELL_ATTRS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampD(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 按法杖攻击力换算 [威力, 范围, 持续, 半径, 灼烧, 弹速]；无法杖/非巫法模式 → 全 0 */
    private static double[] spellAttrValues(ToolStack tool) {
        double[] zero = {0, 0, 0, 0, 0, 0};
        if (tool == null) return zero;
        if (tool.getModifierLevel(Modifiers.MODULAR_STAFF_MODIFIER.getId()) <= 0) return zero;
        float dmg = tool.getStats().get(ToolStats.ATTACK_DAMAGE);
        if (dmg <= 0) return zero;
        return new double[]{
                clampInt(Math.round(dmg * POTENCY_PER_DMG), 1, 15),
                clampInt(Math.round(dmg * RANGE_PER_DMG), 0, 8),
                clampInt(Math.round(dmg * DURATION_PER_DMG), 0, 6),
                clampD((double) (dmg * RADIUS_PER_DMG), 0.0, 2.0),
                clampInt(Math.round(dmg * BURNING_PER_DMG), 0, 8),
                clampD((double) (dmg * VELOCITY_PER_DMG), 0.0, 0.8),
        };
    }

    /** 服务端每 tick 校正：巫法模式 + 手持真法杖 → 按当前法杖强度刷新 Spell 属性；否则清空 */
    public static void refreshSpellAttrs(net.minecraft.server.level.ServerPlayer player, ItemStack staff) {
        boolean goety = staff != null && !staff.isEmpty()
                && ModularStaffGoety.getMode(staff) == ModularStaffGoety.MODE_GOETY;
        ToolStack tool = goety ? ToolHelper.getToolStack(staff) : null;
        double[] values = spellAttrValues(tool);
        double[] prev = APPLIED_SPELL_ATTRS.get(player.getUUID());
        if (prev != null && java.util.Arrays.equals(prev, values)) return;
        applySpellAttrs(player, values);
        if (isAllZero(values)) {
            APPLIED_SPELL_ATTRS.remove(player.getUUID());
        } else {
            APPLIED_SPELL_ATTRS.put(player.getUUID(), values);
        }
    }

    /** 未持有/非巫法模式时清掉残留修饰符 */
    public static void clearSpellAttrs(net.minecraft.server.level.ServerPlayer player) {
        if (APPLIED_SPELL_ATTRS.remove(player.getUUID()) != null) {
            applySpellAttrs(player, new double[]{0, 0, 0, 0, 0, 0});
        }
    }

    private static boolean isAllZero(double[] v) {
        for (double d : v) if (d != 0) return false;
        return true;
    }

    private static void applySpellAttrs(net.minecraft.server.level.ServerPlayer player, double[] v) {
        double potency = v[0];
        double range = v[1];
        double duration = v[2];
        double radius = v[3];
        double burning = v[4];
        double velocity = v[5];
        try {
            // 通用威力 + 九大流派威力：getPotency 优先读匹配流派的专用属性，全部补上才不落空
            putOrRemove(player, com.Polarice3.Goety.init.ModAttributes.SPELL_POTENCY.get(), potency, UID_POTENCY);
            for (net.minecraftforge.registries.RegistryObject<net.minecraft.world.entity.ai.attributes.Attribute> ro :
                    new net.minecraftforge.registries.RegistryObject[]{
                            com.Polarice3.Goety.init.ModAttributes.ABYSS_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.FROST_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.GEOMANCY_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.NECROMANCY_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.NETHER_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.STORM_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.VOID_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.WILD_POTENCY,
                            com.Polarice3.Goety.init.ModAttributes.WIND_POTENCY}) {
                if (ro.isPresent()) putOrRemove(player, ro.get(), potency, UID_POTENCY);
            }
            putOrRemove(player, com.Polarice3.Goety.init.ModAttributes.SPELL_RANGE.get(), range, UID_RANGE);
            putOrRemove(player, com.Polarice3.Goety.init.ModAttributes.SPELL_DURATION.get(), duration, UID_DURATION);
            putOrRemove(player, com.Polarice3.Goety.init.ModAttributes.SPELL_RADIUS.get(), radius, UID_RADIUS);
            putOrRemove(player, com.Polarice3.Goety.init.ModAttributes.SPELL_BURNING.get(), burning, UID_BURNING);
            putOrRemove(player, com.Polarice3.Goety.init.ModAttributes.SPELL_VELOCITY.get(), velocity, UID_VELOCITY);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[魔杖·真法杖] Spell属性施加失败：", t);
        }
    }

    private static void putOrRemove(net.minecraft.server.level.ServerPlayer player,
                                    net.minecraft.world.entity.ai.attributes.Attribute attr, double value,
                                    java.util.UUID uid) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(uid);
        if (value != 0) {
            inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(uid,
                    "tnl_staff_goety_" + attr.getDescriptionId(), value,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        }
    }

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
        // ⚠ 蓄力/吟唱进行中绝不替换手持栈：LivingEntity.updatingUsingItem 每 tick 校验
        // getItemInHand == useItem，换栈对象会 stopUsingItem() 打断施法；等施法结束的下个窗口再校正
        if (player.isUsingItem()) return false;
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
            // 【诊断】镜像发生写替换时记录（确认后移除）
            TinkersNewlife.LOGGER.info("[魔杖·真法杖] 聚晶镜像写入 聚晶={} 使用中={}", focusName(staff), player.isUsingItem());
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

    // ================= 诊断辅助（定位"蓄力放不出"用，确认后移除） =================

    /** 魔杖自带槽里的聚晶名 */
    private static String focusName(ItemStack staff) {
        try {
            ItemStack f = SoulUsingItemHandler.get(staff).getSlot();
            return f.isEmpty() ? "无聚晶" : f.getHoverName().getString();
        } catch (Throwable t) {
            return "读取失败";
        }
    }

    /** 魔杖自带槽聚晶的法术类名 */
    private static String spellName(ItemStack staff) {
        try {
            ItemStack f = SoulUsingItemHandler.get(staff).getSlot();
            if (f.isEmpty()) return "无聚晶";
            if (f.getItem() instanceof com.Polarice3.Goety.api.items.magic.IFocus focus) {
                com.Polarice3.Goety.api.magic.ISpell spell = focus.getSpell();
                return spell == null ? (f.getHoverName().getString() + "（无法术）")
                        : spell.getClass().getSimpleName();
            }
            return f.getHoverName().getString() + "（非IFocus）";
        } catch (Throwable t) {
            return "读取失败";
        }
    }
}
