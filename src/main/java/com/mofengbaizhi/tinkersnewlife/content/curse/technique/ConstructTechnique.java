package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenConstructScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 术式「构筑术式」：
 * <p>
 * 顺转（术式键 C）：开关「无限弹药模式」。开启后，手持会从背包请求弹药的武器
 * （原版弓 / 弩、匠魂弓弩等 {@link ProjectileWeaponItem}，以及 TACZ 枪械——反射识别）时，
 * 每当背包中对应弹药不足就消耗咒力凝结出箭矢 / 子弹，让玩家无需囤积弹药。
 * <p>
 * 反转（反转键 F）：打开「拟造物品栏」——从所有有合成配方的物品中挑选一件，
 * 服务端按其珍贵程度与威力换算咒力消耗并立即扣除，然后开始「拟造」：
 * 1 咒力 = 1 tick 的构筑时间（期间移动速度减半）；完成即获得一个 60 秒后
 * 自动消散的临时物品；构筑期间再次按 F 无效、受到攻击会被打断（咒力不返还）。
 */
public final class ConstructTechnique extends BaseTechnique {

    public static final ConstructTechnique INSTANCE = new ConstructTechnique();

    /** 无限弹药模式持久数据键 */
    private static final String KEY_AMMO_MODE = "tinkersnewlife.construct_ammo_mode";
    /** 弹药保持量：背包中该类弹药低于此值时凝结补足 */
    private static final int AMMO_TARGET = 8;
    /** 弹药补给检查间隔（tick） */
    private static final int AMMO_CHECK_INTERVAL = 2;
    /** 临时拟造物到期 NBT 键（值为到期 gameTime） */
    public static final String KEY_TEMP_UNTIL = "tinkersnewlife.construct_temp_until";
    /** 临时拟造物存在时长（60 秒） */
    public static final int TEMP_TICKS = 1200;
    /** 临时物到期检查间隔（tick） */
    private static final int TEMP_CHECK_INTERVAL = 10;

    /** 反转拟造中：目标物品注册名（持久数据键） */
    private static final String KEY_FORGE_ITEM = "tinkersnewlife.construct_forge_item";
    /** 反转拟造中：完成时刻 gameTime（持久数据键） */
    private static final String KEY_FORGE_END = "tinkersnewlife.construct_forge_end";
    /** 拟造减速属性修饰符 UUID（固定） */
    private static final UUID FORGE_SLOW_UUID = UUID.fromString("7f3a9c1e-2b4d-4f6a-8c9e-0a1b2c3d4e5f");

    /** 已放置在地上的拟造方块（到期自动消失）；服务端主线程访问 */
    private static final List<PlacedTempBlock> PLACED_TEMPS = new ArrayList<>();

    /** 掉落地上的拟造物实体（到期自动消散）；服务端 tick 线程访问 */
    private static final Set<net.minecraft.world.entity.item.ItemEntity> TRACKED_TEMP_ITEMS =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private ConstructTechnique() {
        super(Modifiers.CONSTRUCT.getId());
    }

    // ============================================================
    //  顺转（C）：无限弹药模式开关
    // ============================================================

    @Override
    public void onKeyPress(ServerPlayer player) {
        boolean on = player.getPersistentData().getBoolean(KEY_AMMO_MODE);
        if (on) {
            player.getPersistentData().putBoolean(KEY_AMMO_MODE, false);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.construct.ammo_off"), true);
        } else {
            player.getPersistentData().putBoolean(KEY_AMMO_MODE, true);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.construct.ammo_on"), true);
        }
    }

    /** 顺转模式是否开启 */
    public static boolean isAmmoModeOn(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY_AMMO_MODE);
    }

    /** 登出/死亡：关闭顺转模式并清除拟造状态（咒力已扣不返还，未完成物品作废） */
    public static void cleanup(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_AMMO_MODE, false);
        var data = player.getPersistentData();
        if (data.contains(KEY_FORGE_END)) {
            data.remove(KEY_FORGE_END);
            data.remove(KEY_FORGE_ITEM);
            applySlow(player, false);
        }
    }

    // ============================================================
    //  反转（F）：打开拟造物品栏（熔断期间禁止）
    // ============================================================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        // ⭐ 术式熔断期间不允许打开拟造物品 GUI
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        // ⭐ 拟造进行中不允许再次使用反转
        if (isForging(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.construct.forging"), true);
            return;
        }
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenConstructScreen());
    }

    // ============================================================
    //  服务端 tick 驱动（由主类对所有在线玩家调用）
    // ============================================================

    /**
     * 每 tick：顺转模式弹药补给（间隔检查）+ 反转拟造进度驱动 + 拟造物禁止合成 + 临时物到期清理。
     */
    public static void tickServer(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        // 顺转：无限弹药补给
        if (isAmmoModeOn(player) && player.isAlive() && !player.isRemoved()
                && now % AMMO_CHECK_INTERVAL == 0) {
            INSTANCE.tickAmmo(player);
        }
        // 反转：拟造进度驱动（每 tick，逐玩家判断很轻量）
        if (player.isAlive() && !player.isRemoved()) {
            INSTANCE.tickForge(player, now);
        }
        // ⭐ 拟造物不能参与合成：把合成台输入格里的临时物踢回背包（触发结果重算为空）
        if (player.isAlive() && !player.isRemoved() && now % 2 == 0) {
            ejectTempFromCrafting(player);
        }
        // 反转：临时物到期清理
        if (player.isAlive() && !player.isRemoved() && now % TEMP_CHECK_INTERVAL == 0) {
            sweepTemps(player, now);
        }
    }

    /**
     * 拟造物禁止合成：若玩家打开的是合成台（3×3）或随身合成（2×2），
     * 扫描输入格中的拟造物并移回背包（输入变化会触发结果重算，产物不会生成）。
     */
    private static void ejectTempFromCrafting(ServerPlayer player) {
        if (!(player.containerMenu instanceof CraftingMenu)
                && !(player.containerMenu instanceof InventoryMenu)) {
            return;
        }
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            Slot slot = player.containerMenu.slots.get(i);
            if (!isCraftInputSlot(slot)) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !isTemp(stack)) continue;
            // 踢回背包（背包满则掉落），并清空该输入格
            ItemStack leftover = stack.copy();
            if (!player.getInventory().add(leftover) && !leftover.isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                        player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), leftover);
                drop.setPickUpDelay(0);
                player.serverLevel().addFreshEntity(drop);
            }
            slot.set(ItemStack.EMPTY);
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.construct.no_craft"), true);
        }
    }

    /** 是否为合成输入格（原版合成台/随身合成的 craftSlots 容器） */
    private static boolean isCraftInputSlot(Slot slot) {
        return slot.container instanceof net.minecraft.world.inventory.CraftingContainer;
    }

    /** 是否带拟造物标记 */
    private static boolean isTemp(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(KEY_TEMP_UNTIL);
    }

    // ============================================================
    //  反转拟造（耗时机制）
    // ============================================================

    /** 是否正在拟造 */
    public static boolean isForging(ServerPlayer player) {
        return player.getPersistentData().contains(KEY_FORGE_END);
    }

    /**
     * 每 tick 驱动：拟造中 → 施加半速；到期 → 发放临时物并解除状态。
     */
    private void tickForge(ServerPlayer player, long now) {
        var data = player.getPersistentData();
        if (!data.contains(KEY_FORGE_END)) return;
        long end = data.getLong(KEY_FORGE_END);
        if (end <= now) {
            finishForge(player);
            return;
        }
        // 拟造中：移动速度减半
        applySlow(player, true);
        // 每 20 tick 播一点构筑粒子
        if (now % 20 == 0) {
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.3, 0.4, 0.3, 0);
        }
    }

    /** 拟造完成：按记录发放临时物，清除状态并恢复速度 */
    private static void finishForge(ServerPlayer player) {
        var data = player.getPersistentData();
        String itemId = data.getString(KEY_FORGE_ITEM);
        data.remove(KEY_FORGE_END);
        data.remove(KEY_FORGE_ITEM);
        applySlow(player, false);
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return;
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putLong(KEY_TEMP_UNTIL, player.serverLevel().getGameTime() + TEMP_TICKS);
        Component original = stack.getHoverName();
        stack.setHoverName(Component.translatable("item.tinkersnewlife.construct.prefix").append(original));
        boolean added = player.getInventory().add(stack);
        if (!added) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            drop.setPickUpDelay(0);
            player.serverLevel().addFreshEntity(drop);
        }
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.6, 0.5, 0.05);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.construct.forged", stack.getHoverName(), TEMP_TICKS / 20), false);
    }

    /** 拟造被打断（受击）：清除状态与减速，咒力不返还 */
    public static void interruptForge(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains(KEY_FORGE_END)) return;
        data.remove(KEY_FORGE_END);
        data.remove(KEY_FORGE_ITEM);
        applySlow(player, false);
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.5, 0.4, 0);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.construct.interrupted"), true);
    }

    /** 拟造期间移动速度减半（enable=true 施加，false 移除） */
    private static void applySlow(ServerPlayer player, boolean enable) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        AttributeModifier mod = new AttributeModifier(FORGE_SLOW_UUID,
                "construct_forge_slow", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (enable) {
            if (!attr.hasModifier(mod)) {
                attr.addTransientModifier(mod);
            }
        } else if (attr.hasModifier(mod)) {
            attr.removeModifier(mod);
        }
    }

    // ============================================================
    //  弹药凝结
    // ============================================================

    /**
     * 检查手持武器需要的弹药，不足则凝结补足（费用按凝结数量 × 单价）。
     */
    private void tickAmmo(ServerPlayer player) {
        ItemStack weapon = findAmmoWeapon(player);
        if (weapon.isEmpty()) return;
        // TACZ 枪械：弹药为带 AmmoId 的 AmmoItem，走专用补给
        if (isTaczGun(weapon.getItem())) {
            tickTaczAmmo(player, weapon);
            return;
        }
        Item ammoItem = resolveAmmoItem(player, weapon);
        if (ammoItem == null) return;
        // 统计背包中现有同类弹药（主背包 36 格 + 副手）
        int have = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ammoItem)) {
                have += stack.getCount();
            }
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ammoItem)) {
            have += off.getCount();
        }
        if (have >= AMMO_TARGET) return;
        int need = AMMO_TARGET - have;
        // 单价：箭矢按 (1-亲和/100)×(1+输出×0.5)，最低 1
        int unitCost = ammoUnitCost(player);
        long totalCost = (long) need * unitCost;
        if (totalCost > Integer.MAX_VALUE) return;
        // 支付（创造/无限免费；不足自动关闭模式避免每 tick 刷屏）
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, totalCost) < 0) {
            turnOffNoCurse(player);
            return;
        }
        ItemStack add = new ItemStack(ammoItem, need);
        giveToPlayer(player, add, need);
    }

    /** 单发/单支弹药单价（TACZ 子弹略贵） */
    private static int ammoUnitCost(ServerPlayer player) {
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        return Math.max(1, (int) Math.ceil((1.0 - affinity / 100.0) * (1 + output * 0.5)));
    }

    private static void turnOffNoCurse(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_AMMO_MODE, false);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.construct.ammo_no_curse"), true);
    }

    /** 加入背包；背包满则剩余部分掉落脚下 */
    private static void giveToPlayer(ServerPlayer player, ItemStack add, int particleCount) {
        boolean added = player.getInventory().add(add);
        if (!added && !add.isEmpty()) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), add);
            drop.setPickUpDelay(0);
            player.serverLevel().addFreshEntity(drop);
        }
        if (player.level().isClientSide) return;
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.2, player.getZ(),
                Math.max(1, particleCount / 2), 0.3, 0.3, 0.3, 0);
    }

    /**
     * TACZ 枪械弹药补给：解析该枪需求的弹药类型（gunId → GunData.ammoId），
     * 背包中匹配弹药（AmmoItem + NBT AmmoId）不足弹匣容量时，凝结对应弹药。
     */
    private void tickTaczAmmo(ServerPlayer player, ItemStack gun) {
        // 解析枪需求
        ResourceLocation gunId = taczGetGunId(gun);
        if (gunId == null) return;
        Object gunData = taczGetGunData(gunId);
        if (gunData == null) return;
        ResourceLocation ammoId = taczGunDataAmmoId(gunData);
        if (ammoId == null) return;
        int magSize = taczGunDataMagSize(gunData);
        if (magSize <= 0) magSize = 30;
        // 统计背包中匹配弹药数量（同 item + 同 AmmoId）
        int have = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isTaczMatchingAmmo(stack, ammoId)) {
                have += stack.getCount();
            }
        }
        ItemStack off = player.getOffhandItem();
        if (isTaczMatchingAmmo(off, ammoId)) {
            have += off.getCount();
        }
        // 弹匣容量作为补给目标；已满则不动作
        int target = Math.max(1, magSize);
        if (have >= target) return;
        int need = Math.min(target - have, 64); // 单次凝结不超过弹药堆叠上限，余量下个 tick 再补
        long totalCost = (long) need * ammoUnitCost(player);
        if (totalCost > Integer.MAX_VALUE) return;
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, totalCost) < 0) {
            turnOffNoCurse(player);
            return;
        }
        ItemStack ammo = taczBuildAmmo(ammoId, need);
        if (ammo.isEmpty()) return;
        giveToPlayer(player, ammo, need);
    }

    /** 找到手持/副手中"会从背包请求弹药"的武器；没有则返回空栈 */
    private static ItemStack findAmmoWeapon(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isAmmoRequesting(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isAmmoRequesting(off)) return off;
        return ItemStack.EMPTY;
    }

    /** 是否为弹药请求型武器：原版弓/弩、匠魂弓弩（ProjectileWeaponItem 子类）或 TACZ 枪械 */
    private static boolean isAmmoRequesting(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof ProjectileWeaponItem) return true;
        return isTaczGun(item);
    }

    /**
     * 解析弓弩武器请求的箭矢类型（TACZ 枪械走 {@link #tickTaczAmmo} 专用补给）。
     * 原版弓只认普通箭；弩/匠魂弓弩优先保持玩家背包已有的箭头类型。
     */
    private static Item resolveAmmoItem(ServerPlayer player, ItemStack weapon) {
        Item item = weapon.getItem();
        // 原版弓：仅普通箭
        if (item instanceof net.minecraft.world.item.BowItem) {
            return Items.ARROW;
        }
        // 弩 / 匠魂弓弩：优先与背包已有箭头保持一致
        Item pref = preferredArrow(player);
        return pref != null ? pref : Items.ARROW;
    }

    /** 背包中玩家已持有的第一种箭头（minecraft:arrows tag），无则 null */
    private static Item preferredArrow(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ItemTags.ARROWS)) {
                return stack.getItem();
            }
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ItemTags.ARROWS)) {
            return off.getItem();
        }
        return null;
    }

    // ============================================================
    //  TACZ 反射（无编译依赖，缺失/失败静默跳过）
    //  TACZ 1.1.x：枪械(IGun)从背包请求弹药——弹药物品为统一的 AmmoItem，
    //  其 NBT "AmmoId" 与枪的 gunId→GunData.ammoId 匹配才可装填。
    // ============================================================

    private static Class<?> taczIGunClass;
    private static Method taczGetGunId;
    private static Class<?> taczTimelessApiClass;
    private static Method taczGetCommonGunIndex;
    private static Method taczIndexGetGunData;
    private static Method taczGunDataGetAmmoId;
    private static Method taczGunDataGetAmmoAmount;
    private static Class<?> taczAmmoBuilderClass;
    private static Method taczAmmoBuilderCreate;
    private static Method taczAmmoBuilderSetId;
    private static Method taczAmmoBuilderSetCount;
    private static Method taczAmmoBuilderBuild;
    private static Method taczAmmoGetAmmoId; // IAmmo.getAmmoId(ItemStack) 读 AmmoItem 的弹药 id

    private static boolean isTaczGun(Item item) {
        try {
            if (taczIGunClass == null) {
                taczIGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            }
            return taczIGunClass.isInstance(item);
        } catch (Throwable t) {
            return false;
        }
    }

    /** IGun.getGunId(ItemStack) → gunId RL（this=枪 Item，参数=枪栈） */
    private static ResourceLocation taczGetGunId(ItemStack gun) {
        try {
            if (taczIGunClass == null) taczIGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            if (taczGetGunId == null) taczGetGunId = taczIGunClass.getMethod("getGunId", ItemStack.class);
            Object rl = taczGetGunId.invoke(gun.getItem(), gun);
            return rl instanceof ResourceLocation loc ? loc : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** TimelessAPI.getCommonGunIndex(gunId) → CommonGunIndex（Optional.get） */
    private static Object taczGetGunIndex(ResourceLocation gunId) {
        try {
            if (taczTimelessApiClass == null) taczTimelessApiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            if (taczGetCommonGunIndex == null) {
                taczGetCommonGunIndex = taczTimelessApiClass.getMethod("getCommonGunIndex", ResourceLocation.class);
            }
            Object optional = taczGetCommonGunIndex.invoke(null, gunId);
            if (optional instanceof java.util.Optional<?> opt && opt.isPresent()) {
                return opt.get();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** CommonGunIndex.getGunData() → GunData */
    private static Object taczGetGunData(ResourceLocation gunId) {
        try {
            Object index = taczGetGunIndex(gunId);
            if (index == null) return null;
            if (taczIndexGetGunData == null) {
                taczIndexGetGunData = index.getClass().getMethod("getGunData");
            }
            return taczIndexGetGunData.invoke(index);
        } catch (Throwable t) {
            return null;
        }
    }

    /** GunData.getAmmoId() → 该枪需求的弹药类型 RL */
    private static ResourceLocation taczGunDataAmmoId(Object gunData) {
        try {
            if (taczGunDataGetAmmoId == null) {
                taczGunDataGetAmmoId = gunData.getClass().getMethod("getAmmoId");
            }
            Object rl = taczGunDataGetAmmoId.invoke(gunData);
            return rl instanceof ResourceLocation loc ? loc : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** GunData.getAmmoAmount() → 弹匣容量 */
    private static int taczGunDataMagSize(Object gunData) {
        try {
            if (taczGunDataGetAmmoAmount == null) {
                taczGunDataGetAmmoAmount = gunData.getClass().getMethod("getAmmoAmount");
            }
            Object v = taczGunDataGetAmmoAmount.invoke(gunData);
            return v instanceof Number n ? n.intValue() : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 判断某物品是否为该弹药类型的 TACZ 弹药（AmmoItem，NBT AmmoId 匹配；this=弹药 Item，参数=栈） */
    private static boolean isTaczMatchingAmmo(ItemStack stack, ResourceLocation ammoId) {
        if (stack.isEmpty()) return false;
        try {
            Class<?> iAmmo = Class.forName("com.tacz.guns.api.item.IAmmo");
            if (!iAmmo.isInstance(stack.getItem())) return false;
            if (taczAmmoGetAmmoId == null) taczAmmoGetAmmoId = iAmmo.getMethod("getAmmoId", ItemStack.class);
            Object rl = taczAmmoGetAmmoId.invoke(stack.getItem(), stack);
            return ammoId.equals(rl);
        } catch (Throwable t) {
            return false;
        }
    }

    /** AmmoItemBuilder.create().setId(ammoId).setCount(n).build() → 凝结的弹药物品 */
    private static ItemStack taczBuildAmmo(ResourceLocation ammoId, int count) {
        try {
            if (taczAmmoBuilderClass == null) {
                taczAmmoBuilderClass = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
            }
            if (taczAmmoBuilderCreate == null) taczAmmoBuilderCreate = taczAmmoBuilderClass.getMethod("create");
            if (taczAmmoBuilderSetId == null) taczAmmoBuilderSetId = taczAmmoBuilderClass.getMethod("setId", ResourceLocation.class);
            if (taczAmmoBuilderSetCount == null) taczAmmoBuilderSetCount = taczAmmoBuilderClass.getMethod("setCount", int.class);
            if (taczAmmoBuilderBuild == null) taczAmmoBuilderBuild = taczAmmoBuilderClass.getMethod("build");
            Object builder = taczAmmoBuilderCreate.invoke(null);
            taczAmmoBuilderSetId.invoke(builder, ammoId);
            taczAmmoBuilderSetCount.invoke(builder, Math.max(1, count));
            Object built = taczAmmoBuilderBuild.invoke(builder);
            return built instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    // ============================================================
    //  拟造（服务端入口，由 PacketConstructSelect 调用）
    // ============================================================

    /**
     * 校验并开始拟造临时物品：咒力立即扣除（不返还），按 1 咒力 = 1 tick
     * 进入拟造状态，期间移动减半、受击打断；完成后发放 60 秒临时物。
     *
     * @return 0 开始拟造；1 无此术式/物品无效；2 无合成配方；3 咒力不足；
     *         5 熔断；6 已有拟造进行中
     */
    public static int forge(ServerPlayer player, String itemId) {
        if (!Modifiers.CONSTRUCT.getId().equals(com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler.getSelectedTechniqueId(player))) {
            return 1;
        }
        // ⭐ 熔断期间禁止拟造（与反转键一致）
        if (CursePowerHelper.isBurnout(player)) {
            return 5;
        }
        // ⭐ 已有拟造进行中 → 拒绝再次开始
        if (isForging(player)) {
            return 6;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return 1;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return 1;
        // 必须有至少一个合成配方（服务端权威校验）
        if (!hasCraftingRecipe(player, item)) return 2;
        // 换算费用：珍贵程度（稀有度）× 威力（攻击/护甲/耐久）
        int cost = computeCost(player, item);
        if (cost <= 0) return 2;
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            return 3;
        }
        // 拟造耗时 = 咒力数（1 咒力 = 1 tick），即时扣除咒力
        var data = player.getPersistentData();
        data.putString(KEY_FORGE_ITEM, itemId);
        data.putLong(KEY_FORGE_END, player.serverLevel().getGameTime() + cost);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.construct.forge_start", stackName(item), cost,
                (cost + 19) / 20), false);
        return 0;
    }

    private static Component stackName(Item item) {
        return new ItemStack(item).getHoverName();
    }

    /** 物品是否至少是一个合成配方的产物（含 shaping/shapeless/special 的输出） */
    public static boolean hasCraftingRecipe(ServerPlayer player, Item item) {
        return player.serverLevel().getRecipeManager().getAllRecipesFor(
                        net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                .anyMatch(r -> {
                    ItemStack out = r.getResultItem(player.serverLevel().registryAccess());
                    return !out.isEmpty() && out.is(item);
                });
    }

    // ============================================================
    //  费用换算：珍贵程度 × 威力
    // ============================================================

    /**
     * 拟造费用：
     * <pre>
     * 基础 = 稀有度分（普通1 / 少见3 / 稀有6 / 史诗12）＋ 方块 0、材料/食物 1
     * 威力加成 = 攻击 ×4 ＋ 护甲(防御+韧性) ×3 ＋ 耐久/300（封顶 20）
     * 最终 = 上限(3, ceil(分数 × (1-亲和/100) × (1 + 输出×0.2)))
     * </pre>
     */
    public static int computeCost(ServerPlayer player, Item item) {
        return computeCost(CursePowerHelper.getCurseAffinity(player),
                CursePowerHelper.getCurseOutputLevel(player), item);
    }

    /**
     * 拟造费用（纯函数，服务端权威扣费 / 客户端界面预估共用）：
     * <pre>
     * 基础 = 稀有度分（普通1 / 少见3 / 稀有6 / 史诗12）＋ 方块 0、材料/食物 1
     * 威力加成 = 攻击 ×4 ＋ 护甲(防御+韧性) ×3 ＋ 耐久/300（封顶 20）
     * 最终 = 上限(3, ceil(分数 × (1-亲和/100) × (1 + 输出×0.2)))
     * </pre>
     */
    public static int computeCost(int affinity, int output, Item item) {
        try {
            ItemStack probe = new ItemStack(item);
            Rarity rarity = item.getRarity(probe);
            // ⚠ 不用 switch-on-enum：javac 会为枚举 switch 生成合成 SwitchMap 类，
            // 经 reobf/热重载后可能触发 IncompatibleClassChangeError → 用 if-else
            double base;
            if (rarity == Rarity.EPIC) {
                base = 12;
            } else if (rarity == Rarity.RARE) {
                base = 6;
            } else if (rarity == Rarity.UNCOMMON) {
                base = 3;
            } else {
                base = 1;
            }
            double score = base;
            if (!(item instanceof BlockItem)) {
                score += 1; // 非方块的基础材料分
            }
            // 威力：攻击
            var attrs = item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND);
            double attack = attrs.get(Attributes.ATTACK_DAMAGE).stream()
                    .mapToDouble(m -> m.getAmount()).sum();
            score += attack * 4.0;
            // 威力：护甲
            if (item instanceof ArmorItem armor) {
                score += (armor.getDefense() + armor.getToughness()) * 3.0;
            }
            // 威力：耐久（工具/武器/护甲）
            int maxDamage = item.getMaxDamage(probe);
            if (maxDamage > 0) {
                score += Math.min(20.0, maxDamage / 300.0);
            }
            double cost = Math.max(3.0, Math.ceil(score * (1.0 - affinity / 100.0) * (1.0 + output * 0.2)));
            return (int) Math.min(Integer.MAX_VALUE, cost);
        } catch (Throwable t) {
            // GUI 逐行预览时个别异常物品不阻塞整个界面
            return 3;
        }
    }

    // ============================================================
    //  临时物到期清理
    // ============================================================

    /** 扫描玩家背包与当前打开的容器菜单中的拟造物，移除到期的（防放容器躲避清理） */
    private static void sweepTemps(ServerPlayer player, long now) {
        boolean removed = false;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.hasTag()) continue;
            long until = stack.getTag().getLong(KEY_TEMP_UNTIL);
            if (until > 0 && until <= now) {
                inv.setItem(i, ItemStack.EMPTY);
                removed = true;
            }
        }
        // 玩家正打开的容器（箱子等）中如果有过期的拟造物，一并清除
        if (player.containerMenu != null) {
            for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
                if (slot.container == inv) continue; // 跳过玩家背包槽（上面已清）
                ItemStack stack = slot.getItem();
                if (stack.isEmpty() || !stack.hasTag()) continue;
                long until = stack.getTag().getLong(KEY_TEMP_UNTIL);
                if (until > 0 && until <= now) {
                    slot.set(ItemStack.EMPTY);
                    removed = true;
                }
            }
        }
        if (removed) {
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.4, 0.5, 0.4, 0);
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.construct.expired"), true);
        }
    }

    /** 拟造物掉落地上的 ItemEntity：限制 lifespan，60 秒内自然消散（与背包清理互补） */
    private static final int ITEM_ENTITY_LIFESPAN_CAP = 72000;

    /** 拟造物掉落地上的 ItemEntity：限制 lifespan，60 秒内自然消散（与背包清理互补） */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID,
            bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
    public static class ConstructEvents {

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onItemEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide) return;
            if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)) return;
            ItemStack stack = item.getItem();
            if (stack.isEmpty() || !stack.hasTag()) return;
            long until = stack.getTag().getLong(KEY_TEMP_UNTIL);
            if (until <= 0) return;
            long now = event.getLevel().getGameTime();
            if (until <= now) {
                // 已经过期：不让它进入世界
                event.setCanceled(true);
                return;
            }
            // 加入跟踪集：服务端每 tick 精确按 gameTime 到期移除（不依赖实体 tick/lifespan）
            item.lifespan = (int) Math.min(until - now + 20, ITEM_ENTITY_LIFESPAN_CAP);
            TRACKED_TEMP_ITEMS.add(item);
        }

        /** 拟造中受击 → 打断拟造（咒力已扣，不返还） */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (event.getEntity() instanceof ServerPlayer sp) {
                interruptForge(sp);
            }
        }

        /** 玩家放置拟造方块 → 记录到期时间，到期自动移除 */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onBlockPlaced(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent event) {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            ItemStack held = sp.getMainHandItem();
            long until = held.hasTag() ? held.getTag().getLong(KEY_TEMP_UNTIL) : 0;
            if (until <= 0) {
                held = sp.getOffhandItem();
                until = held.hasTag() ? held.getTag().getLong(KEY_TEMP_UNTIL) : 0;
            }
            if (until <= 0) return;
            // 记录该位置与该方块形态，到期后仅当仍是该方块时移除（避免误删被替换的方块）
            PLACED_TEMPS.add(new PlacedTempBlock(level, event.getPos(), event.getPlacedBlock(), until));
        }

        /** 服务端每 tick：到期拟造物实体消散 + 拟造方块移除 */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
            if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            // 掉落地上的拟造物实体：按各自世界的 gameTime 到期消散
            if (!TRACKED_TEMP_ITEMS.isEmpty()) {
                Iterator<net.minecraft.world.entity.item.ItemEntity> eit = TRACKED_TEMP_ITEMS.iterator();
                while (eit.hasNext()) {
                    net.minecraft.world.entity.item.ItemEntity item = eit.next();
                    if (item == null || item.isRemoved() || !item.isAlive()) {
                        eit.remove();
                        continue;
                    }
                    var itemLevel = item.level();
                    if (itemLevel.isClientSide || !(itemLevel instanceof ServerLevel slevel)) {
                        eit.remove();
                        continue;
                    }
                    ItemStack stack = item.getItem();
                    long until = stack.hasTag() ? stack.getTag().getLong(KEY_TEMP_UNTIL) : 0;
                    if (until <= 0 || until <= slevel.getGameTime()) {
                        // 到期（或标记丢失）→ 让物品实体消散
                        slevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                                item.getX(), item.getY() + 0.3, item.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                        item.discard();
                        eit.remove();
                    }
                }
            }
            if (PLACED_TEMPS.isEmpty()) return;
            Iterator<PlacedTempBlock> it = PLACED_TEMPS.iterator();
            while (it.hasNext()) {
                PlacedTempBlock p = it.next();
                ServerLevel level = p.level;
                if (level == null || !level.isLoaded(p.pos)) continue;
                long now = level.getGameTime();
                if (now < p.expireUntil) continue;
                // 到期：仅当该位置仍旧是当初放置的方块时移除
                BlockState current = level.getBlockState(p.pos);
                if (current.getBlock() == p.state.getBlock()) {
                    level.levelEvent(2001, p.pos, net.minecraft.world.level.block.Block.getId(current));
                    level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 3);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                            p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 10,
                            0.3, 0.3, 0.3, 0.02);
                }
                it.remove();
            }
        }
    }

    /** 一个已放置的拟造方块记录 */
    private static final class PlacedTempBlock {
        final ServerLevel level;
        final BlockPos pos;
        final BlockState state;
        final long expireUntil;

        PlacedTempBlock(ServerLevel level, BlockPos pos, BlockState state, long expireUntil) {
            this.level = level;
            this.pos = pos.immutable();
            this.state = state;
            this.expireUntil = expireUntil;
        }
    }
}
