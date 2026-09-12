package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenConstructScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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
    /** 反转拟造中：总耗时 tick（进度条用） */
    private static final String KEY_FORGE_TOTAL = "tinkersnewlife.construct_forge_total";
    /** 拟造减速属性修饰符 UUID（固定） */
    private static final UUID FORGE_SLOW_UUID = UUID.fromString("7f3a9c1e-2b4d-4f6a-8c9e-0a1b2c3d4e5f");

    /** 已放置在地上的拟造方块（到期自动消失）；服务端主线程访问 */
    private static final List<PlacedTempBlock> PLACED_TEMPS = new ArrayList<>();

    /** 被右键"安装"的拟造部件（AE2 ME 输入接口等非方块形态，到期摘除）；服务端主线程访问 */
    private static final List<PlacedTempPart> PLACED_TEMP_PARTS = new ArrayList<>();

    /** 右键瞬间的待确认部件放置（2 tick 后对比"新增面"）；服务端主线程访问 */
    private static final List<PendingPartPlace> PENDING_PARTS = new ArrayList<>();

    /** 扫描容器时用的随机数（挑玩家用） */
    private static final java.util.Random RANDOM = new java.util.Random();

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
            data.remove(KEY_FORGE_TOTAL);
            applySlow(player, false);
            // 登出后客户端进度条由下次登录自然清除；此处仍尝试推送（若尚未断开）
            try {
                TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge(0, 0, ""));
            } catch (Throwable ignored) {
            }
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
        // ⭐ 拟造物不能参与任何配方/不能放入其他容器：把非玩家槽里的临时物踢回背包
        if (player.isAlive() && !player.isRemoved() && now % 2 == 0) {
            ejectTempFromContainers(player);
        }
        // 反转：临时物到期清理
        if (player.isAlive() && !player.isRemoved() && now % TEMP_CHECK_INTERVAL == 0) {
            sweepTemps(player, now);
        }
    }

    /**
     * 拟造物禁止进入任何"非玩家自身物品栏"的容器槽（合成台/熔炉/锻造台/切石机/酿造台/模组机器/箱子等）。
     * 把其中发现的拟造物移回背包：输入槽清空会触发菜单结果重算，配方产物不会生成，
     * 从而拟造物无法参与任何配方。
     */
    private static void ejectTempFromContainers(ServerPlayer player) {
        if (player.containerMenu == null) return;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            Slot slot = player.containerMenu.slots.get(i);
            // 跳过玩家自身物品栏/盔甲/副手槽（这些不属于"其他容器"）
            if (slot.container == inv) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !isTemp(stack)) continue;
            // 踢回背包（背包满则掉落），并清空该槽
            ItemStack leftover = stack.copy();
            if (!inv.add(leftover) && !leftover.isEmpty()) {
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

    /** 是否带拟造物标记 */
    private static boolean isTemp(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(KEY_TEMP_UNTIL);
    }

    /** 是否构筑术式拟造物（临时物标记；供领域·三重疾苦判定"手中拟造物"） */
    public static boolean isConstructTemp(ItemStack stack) {
        return isTemp(stack);
    }

    /** 是否远程武器（弹药请求型：原版弓/弩、匠魂弓弩、TACZ/樱花枪；供领域·三重疾苦判定） */
    public static boolean isRangedWeapon(ItemStack stack) {
        return isAmmoRequesting(stack);
    }

    // ============================================================
    //  反转拟造（耗时机制）
    // ============================================================

    /** 是否正在拟造 */
    public static boolean isForging(ServerPlayer player) {
        return player.getPersistentData().contains(KEY_FORGE_END);
    }

    /**
     * 每 tick 驱动：拟造中 → 施加半速并同步进度；到期 → 发放临时物并解除状态。
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
        // 每 tick 推送剩余 tick（进度条毫秒插值本地推进，低频推送会跳变）
        if (now % 2 == 0) {
            syncForge(player, now);
        }
        // 每 20 tick 播一点构筑粒子
        if (now % 20 == 0) {
            player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 0.5, player.getZ(), 2, 0.3, 0.4, 0.3, 0);
        }
    }

    /** 推送拟造进度到客户端（remaining<=0 且 total<=0 表示清除进度条） */
    private static void syncForge(ServerPlayer player, long now) {
        var data = player.getPersistentData();
        long end = data.getLong(KEY_FORGE_END);
        String itemId = data.getString(KEY_FORGE_ITEM);
        long total = data.getLong(KEY_FORGE_TOTAL);
        long remaining = Math.max(0, end - now);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge(remaining, total, itemId));
    }

    /** 拟造完成：按记录发放临时物，清除状态并恢复速度 */
    private static void finishForge(ServerPlayer player) {
        var data = player.getPersistentData();
        String itemId = data.getString(KEY_FORGE_ITEM);
        data.remove(KEY_FORGE_END);
        data.remove(KEY_FORGE_ITEM);
        data.remove(KEY_FORGE_TOTAL);
        applySlow(player, false);
        // 清除客户端进度条
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge(0, 0, ""));
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return;
        // ⭐ 优先用"配方真实产物"作模板：法术卷轴这类产物本身带 NBT，裸 new ItemStack 会变成空壳
        ItemStack stack = sampleResult(player, item);
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
        data.remove(KEY_FORGE_TOTAL);
        applySlow(player, false);
        // 清除客户端进度条
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge(0, 0, ""));
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
        // sakuratinker（樱之匠魂）枪械：补给「工匠子弹」到背包
        if (isSakuraGun(weapon.getItem())) {
            tickSakuraAmmo(player);
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

    /**
     * sakuratinker（樱之匠魂）枪械补给：识别 {@code RevolverItem / LaserGun / ModifiableGunItem}，
     * 向背包凝结「工匠子弹」（sakuratinker:tinker_bullet，匠魂多部件子弹）。
     * 纯类名匹配（不引用该模组类），模组未安装时自动不生效。
     */
    private static final String SAKURA_AMMO_ID = "sakuratinker:tinker_bullet";

    private static boolean isSakuraGun(Item item) {
        String n = item.getClass().getName();
        if (!n.startsWith("com.ssakura49.sakuratinker_tools")) return false;
        return n.contains("Revolver") || n.contains("LaserGun") || n.contains("ModifiableGun");
    }

    private void tickSakuraAmmo(ServerPlayer player) {
        Item ammo = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getValue(new ResourceLocation(SAKURA_AMMO_ID));
        if (ammo == null) return;
        int have = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(ammo)) have += stack.getCount();
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ammo)) have += off.getCount();
        if (have >= AMMO_TARGET) return;
        int need = Math.min(AMMO_TARGET - have, 8); // 单次少量凝结，避免一次建造过多工具
        int unitCost = ammoUnitCost(player);
        long totalCost = (long) need * unitCost * 2L; // 多部件子弹造价更高
        if (totalCost > Integer.MAX_VALUE) return;
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, totalCost) < 0) {
            turnOffNoCurse(player);
            return;
        }
        for (int i = 0; i < need; i++) {
            ItemStack bullet = buildTinkerBullet(ammo);
            if (bullet.isEmpty()) return;
            giveToPlayer(player, bullet, 1);
        }
    }

    /** 构建一枚有效「工匠子弹」：随机 tier1 材料 ×2（small_blade ×2 部件）并重建数据 */
    private static ItemStack buildTinkerBullet(Item ammo) {
        ItemStack stack = new ItemStack(ammo);
        try {
            slimeknights.tconstruct.library.tools.nbt.ToolStack tool =
                    slimeknights.tconstruct.library.tools.nbt.ToolStack.from(stack);
            if (tool != null) {
                java.util.List<slimeknights.tconstruct.library.materials.definition.IMaterial> mats =
                        new java.util.ArrayList<>();
                for (slimeknights.tconstruct.library.materials.definition.IMaterial m :
                        slimeknights.tconstruct.library.materials.MaterialRegistry.getInstance().getAllMaterials()) {
                    if (m.isHidden() || m == slimeknights.tconstruct.library.materials.definition.IMaterial.UNKNOWN) {
                        continue;
                    }
                    if (m.getTier() <= 1) {
                        mats.add(m);
                        if (mats.size() >= 2) break;
                    }
                }
                if (mats.size() >= 2) {
                    tool.setMaterials(slimeknights.tconstruct.library.tools.nbt.MaterialNBT.of(
                            mats.get(0), mats.get(1)));
                    tool.rebuildStats();
                    tool.updateStack(stack);
                }
            }
        } catch (Throwable ignored) {
            // 构建失败回退裸子弹（由游戏/玩家自行判定）
        }
        return stack;
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
        if (isTaczGun(item)) return true;
        return isSakuraGun(item);
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
        data.putLong(KEY_FORGE_TOTAL, cost);
        // 立即推送进度条（start=now, end=now+cost）
        syncForge(player, player.serverLevel().getGameTime());
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

    /**
     * 物品是否有"可产出它的配方"——<b>覆盖所有配方类型</b>（工作台/熔炉/高炉/烟熏/营火/切石机/锻造台，
     * 以及模组自定义的配方类型），并应用配置黑名单。
     */
    public static boolean hasCraftingRecipe(ServerPlayer player, Item item) {
        return constructibleMap(player.serverLevel().getRecipeManager(),
                player.serverLevel().registryAccess()).containsKey(item);
    }

    // ============================================================
    //  可拟造物品表（物品 → 产出它的配方类型集合）+ 黑名单
    // ============================================================

    /** 每个配方管理器一份缓存（集成服务器里客户端/服务端是两个管理器，用 IdentityHashMap 各自缓存） */
    private record ConstructCache(int recipeCount,
                                  Map<Item, java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>>> outputs,
                                  Map<Item, Double> ingredientTerms,
                                  Map<Item, ItemStack> samples,
                                  Map<Item, Double> functionTerms) {}

    private static final Map<net.minecraft.world.item.crafting.RecipeManager, ConstructCache> CONSTRUCT_CACHE =
            new java.util.IdentityHashMap<>();

    /** 原料价值递归深度上限（链子长一点，才能把"高级墨水→卷轴"这类价值传下去） */
    private static final int VALUE_MAX_DEPTH = 3;
    /** 递归传播时的价值上限（防止 9 个一组的那种配方把价格炸飞） */
    private static final double INGREDIENT_VALUE_CAP = 80.0;
    /** 单个原料（标签型可能上百候选）最多取前几个候选估值，避免建表过慢 */
    private static final int VALUE_MAX_CANDIDATES = 8;

    /**
     * 构建"可拟造物品 → 产出它的配方类型集合"。服务端判定与客户端列表<b>共用同一份逻辑</b>，
     * 因此黑名单在两边一致。缓存按（配方管理器 + 配方数量）失效，`/reload` 后自动重建。
     */
    public static Map<Item, java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>>> constructibleMap(
            net.minecraft.world.item.crafting.RecipeManager manager,
            net.minecraft.core.RegistryAccess access) {
        return cacheFor(manager, access).outputs();
    }

    /** 取（必要时重建）某个配方管理器的缓存 */
    private static ConstructCache cacheFor(net.minecraft.world.item.crafting.RecipeManager manager,
                                           net.minecraft.core.RegistryAccess access) {
        java.util.Collection<net.minecraft.world.item.crafting.Recipe<?>> all = manager.getRecipes();
        ConstructCache cached = CONSTRUCT_CACHE.get(manager);
        if (cached != null && cached.recipeCount() == all.size()) return cached;

        // 1) 物品 → 产出它的所有配方（顺便留一份"配方真实产物"作为模板）
        Map<Item, java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> byItem = new java.util.HashMap<>();
        Map<Item, ItemStack> samples = new java.util.HashMap<>();
        for (net.minecraft.world.item.crafting.Recipe<?> recipe : all) {
            try {
                ItemStack out = recipe.getResultItem(access);
                if (out.isEmpty() || out.getItem() == Items.AIR) continue;
                byItem.computeIfAbsent(out.getItem(), k -> new java.util.ArrayList<>()).add(recipe);
                samples.putIfAbsent(out.getItem(), out.copy());
            } catch (Throwable ignored) {
                // CustomRecipe 等取产物需要容器上下文 → 跳过
            }
        }

        // 2) 原料价值项：该物品**各配方原料价值合计的平均值**（多配方取平均）
        Map<Item, Double> values = new java.util.HashMap<>();
        Map<Item, Double> terms = new java.util.HashMap<>();
        for (Map.Entry<Item, java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> e : byItem.entrySet()) {
            double sum = 0;
            for (net.minecraft.world.item.crafting.Recipe<?> r : e.getValue()) {
                sum += ingredientValue(r, byItem, values, new java.util.HashSet<>(), 0);
            }
            terms.put(e.getKey(), sum / e.getValue().size());
        }

        // 3) 物品 → 配方类型集合 + 黑名单过滤
        Map<Item, java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>>> outputs = new java.util.HashMap<>();
        for (Map.Entry<Item, java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> e : byItem.entrySet()) {
            java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>> types = new java.util.HashSet<>();
            for (net.minecraft.world.item.crafting.Recipe<?> r : e.getValue()) types.add(r.getType());
            outputs.put(e.getKey(), types);
        }
        outputs.keySet().removeIf(item -> isBlacklisted(item, outputs.get(item)));

        // 4) "功能价值"项：高功能魔法类物品（法术卷轴 / 聚晶 / 法术书 / 符文 …）自身分极低，
        //    但实际价值远高于同材料的手工品 → 单独加一笔权重
        Map<Item, Double> functionTerms = new java.util.HashMap<>();
        for (Item item : outputs.keySet()) {
            functionTerms.put(item, functionValue(item));
        }

        if (CONSTRUCT_CACHE.size() > 2) CONSTRUCT_CACHE.clear();
        ConstructCache fresh = new ConstructCache(all.size(), outputs, terms, samples, functionTerms);
        CONSTRUCT_CACHE.put(manager, fresh);
        TinkersNewlife.LOGGER.debug("[构筑] 可拟造物品表已重建：{} 项（黑名单后）", outputs.size());
        return fresh;
    }

    // ============================================================
    //  高功能魔法类物品的价值加成
    // ============================================================

    /**
     * 判定并返回一件物品的"功能价值"加成（默认 0）。
     *
     * <p>为什么需要它：法术卷轴 / 聚晶 / 法术书这类东西，<b>稀有度和攻防耐久全是垫底</b>
     * （普通稀有度、非方块、没攻击没护甲、无耐久），按原公式只值最低档的几咒力，
     * 但它们的功能性远超同材料的普通物品。
     *
     * <p>判定方式（两者任一命中即算）：
     * <ol>
     *   <li><b>标签</b>：内置 {@code #curios:scroll} / {@code #curios:spellbook} / {@code #curios:spellstone}
     *       / {@code #irons_spellbooks:school_focus} / {@code #irons_spellbooks:inscribed_rune}，
     *       外加配置 {@code magic_extra_tags}；</li>
     *   <li><b>类名关键词</b>：沿着物品类的继承链与接口看名字，命中 scroll/focus/spellbook/rune/… 任一
     *       （软依赖：只看名字，不 import 任何模组类），外加配置 {@code magic_extra_keywords}。</li>
     * </ol>
     * 加成大小由配置 {@code magic_item_bonus} 决定（默认 40 分，0 = 关闭）。
     */
    private static double functionValue(Item item) {
        double bonus = magicBonus();
        if (bonus <= 0) return 0.0;
        // 标签
        for (String tagText : magicTags()) {
            ResourceLocation tagId = ResourceLocation.tryParse(tagText.startsWith("#") ? tagText.substring(1) : tagText);
            if (tagId == null) continue;
            if (item.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, tagId))) return bonus;
        }
        // 类名关键词
        String[] keywords = magicKeywords();
        Class<?> c = item.getClass();
        while (c != null && c != Object.class) {
            String name = c.getName().toLowerCase(java.util.Locale.ROOT);
            for (String k : keywords) {
                if (!k.isEmpty() && name.contains(k)) return bonus;
            }
            for (Class<?> itf : c.getInterfaces()) {
                String itfName = itf.getName().toLowerCase(java.util.Locale.ROOT);
                for (String k : keywords) {
                    if (!k.isEmpty() && itfName.contains(k)) return bonus;
                }
            }
            c = c.getSuperclass();
        }
        return 0.0;
    }

    /** 内置魔法类关键词（类名/接口名子串，全小写） */
    private static final String[] DEFAULT_MAGIC_KEYWORDS = {
            "scroll", "focus", "spellbook", "grimoire", "tome", "codex",
            "rune", "talisman", "charm", "amulet", "phylactery",
            "wand", "staff", "scepter", "sceptre", "elixir", "incant"
    };

    /** 内置魔法类标签 */
    private static final String[] DEFAULT_MAGIC_TAGS = {
            "#curios:scroll", "#curios:spellbook", "#curios:spellstone",
            "#irons_spellbooks:school_focus", "#irons_spellbooks:inscribed_rune"
    };

    private static double magicBonus() {
        try {
            return Math.max(0.0, com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_MAGIC_BONUS.get());
        } catch (Throwable t) {
            return 40.0;
        }
    }

    private static String[] magicKeywords() {
        try {
            var extra = com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_MAGIC_EXTRA_KEYWORDS.get();
            if (extra == null || extra.isEmpty()) return DEFAULT_MAGIC_KEYWORDS;
            java.util.List<String> all = new java.util.ArrayList<>(java.util.List.of(DEFAULT_MAGIC_KEYWORDS));
            for (String s : extra) {
                if (s != null && !s.isBlank()) all.add(s.trim().toLowerCase(java.util.Locale.ROOT));
            }
            return all.toArray(new String[0]);
        } catch (Throwable t) {
            return DEFAULT_MAGIC_KEYWORDS;
        }
    }

    private static java.util.List<String> magicTags() {
        try {
            var extra = com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_MAGIC_EXTRA_TAGS.get();
            if (extra == null || extra.isEmpty()) return java.util.List.of(DEFAULT_MAGIC_TAGS);
            java.util.List<String> all = new java.util.ArrayList<>(java.util.List.of(DEFAULT_MAGIC_TAGS));
            for (String s : extra) {
                if (s != null && !s.isBlank()) all.add(s.trim());
            }
            return all;
        } catch (Throwable t) {
            return java.util.List.of(DEFAULT_MAGIC_TAGS);
        }
    }

    /** 该物品的"原料价值项"（= 各配方原料价值合计的平均值；没有配方 → 0） */
    public static double ingredientTerm(net.minecraft.world.item.crafting.RecipeManager manager,
                                        net.minecraft.core.RegistryAccess access, Item item) {
        try {
            return cacheFor(manager, access).ingredientTerms().getOrDefault(item, 0.0);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** 一条配方里所有原料的价值合计（标签型原料取候选物品的平均值） */
    private static double ingredientValue(net.minecraft.world.item.crafting.Recipe<?> recipe,
                                          Map<Item, java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> byItem,
                                          Map<Item, Double> valueCache,
                                          java.util.Set<Item> visiting, int depth) {
        double total = 0;
        try {
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) continue;
                ItemStack[] candidates = ingredient.getItems();
                if (candidates == null || candidates.length == 0) continue;
                int limit = Math.min(candidates.length, VALUE_MAX_CANDIDATES);
                double sum = 0;
                for (int i = 0; i < limit; i++) {
                    sum += itemValue(candidates[i].getItem(), byItem, valueCache, visiting, depth + 1);
                }
                total += sum / limit;
            }
        } catch (Throwable ignored) {
        }
        return total;
    }

    /**
     * 单个物品的"价值"：取<b>自身分</b>与<b>原料价值×0.5</b>的较大者。
     * 记忆化 + 深度上限 + 环检测（如"木板 ↔ 原木"互相引用）保证不会递归爆掉。
     */
    private static double itemValue(Item item,
                                    Map<Item, java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> byItem,
                                    Map<Item, Double> valueCache,
                                    java.util.Set<Item> visiting, int depth) {
        Double hit = valueCache.get(item);
        if (hit != null) return hit;
        double base = intrinsicScore(item);
        double value = base;
        var recipes = byItem.get(item);
        if (recipes != null && !recipes.isEmpty() && depth < VALUE_MAX_DEPTH && visiting.add(item)) {
            double sum = 0;
            for (var r : recipes) sum += ingredientValue(r, byItem, valueCache, visiting, depth);
            // ⭐ 不再打 0.5 折：折损会让"多级合成链"上的价值迅速归零（高级墨水就废了）；
            //    改为全额传播 + 上限兜底，环检测与深度上限照旧。
            value = Math.max(base, Math.min(INGREDIENT_VALUE_CAP, sum / recipes.size()));
            visiting.remove(item);
        }
        valueCache.put(item, value);
        return value;
    }

    /**
     * 拟造黑名单判定。配置项格式：
     * <pre>
     *   goety                      整个模组
     *   iceandfire:*               整个模组（等价写法）
     *   minecraft:bedrock          单个物品
     *   #forge:ingots              物品标签
     *   *:*_shard                  通配符（* 匹配任意字符，可写 "*:raw_*" 等）
     *   recipe:minecraft:smelting  凡能被该配方类型产出的物品（也可写 type:...）
     *   // 开头                 注释，忽略
     * </pre>
     * 内置默认名单（{@link #DEFAULT_BLACKLIST}，可用配置 {@code use_default_blacklist} 关闭）会先匹配。
     */
    public static boolean isBlacklisted(Item item,
                                        java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>> types) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        String itemId = id == null ? "" : id.toString();

        // 内置默认：矿石 / 粗矿 / 矿锭 / 矿粒 / 矿粉 / 宝石 / 碎片
        if (useDefaultBlacklist() && matchEntry(DEFAULT_BLACKLIST, item, itemId, types)) return true;

        // 用户配置的额外条目
        java.util.List<? extends String> list;
        try {
            list = com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_BLACKLIST.get();
        } catch (Throwable t) {
            return false;
        }
        return list != null && !list.isEmpty() && matchEntry(list, item, itemId, types);
    }

    /** 内置默认黑名单：跨模组的通用标签 + 通配符兜底（矿石/粗矿/矿锭/矿粒/矿粉/宝石/碎片） */
    private static final java.util.List<String> DEFAULT_BLACKLIST = java.util.List.of(
            // Forge 通用标签（1.20.1 的主流约定，已确认本整合包在用）
            "#forge:ores",            // 各类矿石（含深层/下界/模组矿）
            "#forge:raw_materials",   // 粗矿
            "#forge:ingots",          // 矿锭
            "#forge:nuggets",         // 矿粒
            "#forge:dusts",           // 矿粉
            "#forge:gems",            // 宝石（钻石/绿宝石等）
            // 少数模组用的是 c: 约定，一并兜住
            "#c:ores", "#c:raw_materials", "#c:ingots", "#c:nuggets", "#c:dusts", "#c:gems",
            // 没有通用标签的：用通配符兜底
            "*:*_shard", "*:*_shards",        // 矿碎片
            "*:*_fragment", "*:*_fragments",  // 矿碎片（另一种写法）
            "*:*_dust",                       // 未被 #forge:dusts 收录的矿粉
            "*:*_nugget"                      // 未被 #forge:nuggets 收录的矿粒
    );

    private static boolean useDefaultBlacklist() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_USE_DEFAULT_BLACKLIST.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /** 逐条匹配名单（标签 / 配方类型 / mod / 通配符 / 单物品） */
    private static boolean matchEntry(java.util.List<? extends String> list, Item item, String itemId,
                                      java.util.Set<net.minecraft.world.item.crafting.RecipeType<?>> types) {
        String modId = itemId.contains(":") ? itemId.substring(0, itemId.indexOf(':')) : itemId;
        for (String raw : list) {
            if (raw == null) continue;
            String e = raw.trim();
            if (e.isEmpty() || e.startsWith("//")) continue;

            // 标签：#modid:tag
            if (e.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(e.substring(1));
                if (tagId != null) {
                    var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagId);
                    if (item.builtInRegistryHolder().is(tag)) return true;
                }
                continue;
            }
            // 配方类型：recipe:modid:type / type:modid:type
            if (e.startsWith("recipe:") || e.startsWith("type:")) {
                String typeId = e.substring(e.indexOf(':') + 1);
                ResourceLocation want = ResourceLocation.tryParse(typeId);
                if (want == null || types == null) continue;
                for (net.minecraft.world.item.crafting.RecipeType<?> type : types) {
                    if (want.equals(ForgeRegistries.RECIPE_TYPES.getKey(type))) return true;
                }
                continue;
            }
            // 整个模组：只写 modid
            if (!e.contains(":")) {
                if (e.equalsIgnoreCase(modId)) return true;
                continue;
            }
            // 通配符：* 匹配任意字符（也覆盖 modid:* 与 *:*_shard 这类写法）
            if (e.indexOf('*') >= 0) {
                if (globMatch(e, itemId)) return true;
                continue;
            }
            // 单个物品：modid:item
            if (e.equalsIgnoreCase(itemId)) return true;
        }
        return false;
    }

    /** 简单的 '*' 通配匹配（大小写不敏感） */
    private static boolean globMatch(String pattern, String value) {
        String p = pattern.toLowerCase(java.util.Locale.ROOT);
        String v = value.toLowerCase(java.util.Locale.ROOT);
        String[] parts = p.split("\\*", -1);
        int idx = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (i == 0) {
                if (!v.startsWith(part)) return false;
                idx = part.length();
                continue;
            }
            int found = v.indexOf(part, idx);
            if (found < 0) return false;
            if (i == parts.length - 1 && p.charAt(p.length() - 1) != '*'
                    && found + part.length() != v.length()) {
                return false;
            }
            idx = found + part.length();
        }
        return true;
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
        double term = 0.0;
        try {
            var level = player.serverLevel();
            term = ingredientTerm(level.getRecipeManager(), level.registryAccess(), item);
        } catch (Throwable ignored) {
        }
        double fn = 0.0;
        try {
            var level = player.serverLevel();
            fn = Math.max(0.0, functionValue(item));
        } catch (Throwable ignored) {
        }
        return computeCost(CursePowerHelper.getCurseAffinity(player),
                CursePowerHelper.getCurseOutputLevel(player), item, term, fn);
    }

    /** 兼容旧调用（不含原料项） */
    public static int computeCost(int affinity, int output, Item item) {
        return computeCost(affinity, output, item, 0.0);
    }

    /**
     * 拟造费用（纯函数，服务端权威扣费 / 客户端界面预估共用）：
     * <pre>
     * 自身分 = 稀有度分（普通1 / 少见3 / 稀有6 / 史诗12）＋ 方块 0、材料/食物 1
     *          ＋ 攻击 ×4 ＋ 护甲(防御+韧性) ×3 ＋ 耐久/300（封顶 20）
     * 原料项 = 产出它的各配方"原料价值合计"的**平均值** × 配置权重（多配方取平均）
     * 分数   = 自身分 ＋ 原料项
     * 最终   = round( 上限(3, ceil(分数 × max(0.25, 1-亲和/100) × (1+输出×0.2))) × 配置倍率 )
     * </pre>
     */
    public static int computeCost(int affinity, int output, Item item, double ingredientTerm) {
        return computeCost(affinity, output, item, ingredientTerm, 0.0);
    }

    /** 完整版：自身分 + 原料项×权重 + 功能价值项(魔法类) */
    public static int computeCost(int affinity, int output, Item item, double ingredientTerm, double functionTerm) {
        double score = intrinsicScore(item)
                + Math.max(0.0, ingredientTerm) * ingredientWeight()
                + Math.max(0.0, functionTerm);
        double affinityMul = Math.max(0.25, 1.0 - Math.max(0, affinity) / 100.0);
        double raw = Math.max(3.0, Math.ceil(score * affinityMul * (1.0 + output * 0.2)));
        double cost = raw * costMultiplier();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1.0, Math.round(cost)));
    }

    /** 物品"自身分"：稀有度 + 材料/方块 + 攻击 + 护甲 + 耐久（不含原料项） */
    public static double intrinsicScore(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        String itemId = key == null ? "" : key.toString().toLowerCase(java.util.Locale.ROOT);
        // ① 手动指定价值（优先级最高，直接**替代**计算结果）
        Double override = overrideValue(item, itemId);
        if (override != null) return Math.max(0.0, override);
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
            // ② 品质词加成：不同品质的墨水/精华/结晶这类物品，稀有度普通、无攻防耐久，
            //    档次全写在名字里（common/uncommon/rare/epic/legendary…）→ 按词给分；
            //    这一分会被"原料价值"递归带到下游产物（比如卷轴）上。
            score += tierBonus(itemId);

            // ③ "获取难度"代理：不少高价材料<b>根本没有配方</b>（挖矿/采集/掉落），
            //    配方链估值对它们无能为力，只能靠下面几条代理把价值补回来。
            //    a) 不可堆叠（堆叠上限 1）几乎都是独特之物：鞘翅/图腾/三叉戟…
            try {
                if (item.getMaxStackSize() == 1) {
                    score += uniqueItemBonus();
                }
            } catch (Throwable ignored) {
            }
            //    b) 方块硬度：黑曜石 50、远古残骸 30、铁矿 3、泥土 0.5 → 采起来越费劲越值钱
            if (item instanceof BlockItem blockItem) {
                try {
                    float hardness = blockItem.getBlock().defaultBlockState().getDestroySpeed(null, null);
                    if (hardness > 0) {
                        score += Math.min(hardnessCap(), hardness / 2.0);
                    }
                } catch (Throwable ignored) {
                }
            }
            //    c) 通用标签价值：矿石/粗矿/矿锭/宝石/矿粉…（可配，可叠加但有总上限）
            score += tagValue(item);
            return score;
        } catch (Throwable t) {
            return 1.0;
        }
    }

    // ============================================================
    //  获取难度代理：独特物品 / 方块硬度 / 标签价值
    // ============================================================

    /** 内置标签价值（对所有物品叠加计算；矿石等"没有配方"的材料靠它定价） */
    private static Map<String, Double> DEFAULT_TAG_VALUES() {
        Map<String, Double> m = new java.util.LinkedHashMap<>();
        m.put("#forge:ores", 12.0);
        m.put("#forge:raw_materials", 8.0);
        m.put("#forge:ingots", 10.0);
        m.put("#forge:gems", 25.0);
        m.put("#forge:dusts", 6.0);
        m.put("#forge:nuggets", 2.0);
        m.put("#forge:storage_blocks", 20.0);
        m.put("#minecraft:coals", 5.0);
        return m;
    }

    private static Map<String, Double> tagValueTable;

    private static void ensureTagValues() {
        if (tagValueTable != null) return;
        tagValueTable = DEFAULT_TAG_VALUES();
        try {
            for (String s : com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_TAG_VALUES.get()) {
                String[] kv = splitPair(s);
                if (kv != null && kv[0].startsWith("#")) {
                    tagValueTable.put(kv[0], parseDouble(kv[1], 0));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 标签价值（多个标签相加，但有总上限） */
    private static double tagValue(Item item) {
        ensureTagValues();
        double total = 0;
        for (Map.Entry<String, Double> e : tagValueTable.entrySet()) {
            ResourceLocation tagId = ResourceLocation.tryParse(e.getKey().substring(1));
            if (tagId == null) continue;
            if (item.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, tagId))) {
                total += e.getValue();
            }
        }
        return Math.min(tagValueCap(), total);
    }

    private static double uniqueItemBonus() {
        try {
            return Math.max(0.0, com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_UNIQUE_ITEM_BONUS.get());
        } catch (Throwable t) {
            return 8.0;
        }
    }

    private static double hardnessCap() {
        try {
            return Math.max(0.0, com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_HARDNESS_CAP.get());
        } catch (Throwable t) {
            return 25.0;
        }
    }

    private static double tagValueCap() {
        try {
            return Math.max(0.0, com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_TAG_VALUE_CAP.get());
        } catch (Throwable t) {
            return 60.0;
        }
    }

    // ============================================================
    //  品质词加成 / 手动指定价值
    // ============================================================

    /** 内置品质词 → 分数（对物品 id 做子串匹配） */
    private static final Map<String, Double> DEFAULT_TIER_BONUS = Map.of(
            "uncommon", 6.0,
            "rare", 14.0,
            "epic", 30.0,
            "legendary", 60.0,
            "mythic", 90.0,
            "divine", 90.0,
            "supreme", 120.0,
            "ultimate", 120.0
    );

    private static Map<String, Double> tierBonusTable;
    private static Map<String, Double> overrideTable;
    private static java.util.List<String> overridePatterns;
    private static boolean valueTablesLoaded = false;

    private static void ensureTables() {
        if (valueTablesLoaded) return;
        valueTablesLoaded = true;
        tierBonusTable = new java.util.LinkedHashMap<>(DEFAULT_TIER_BONUS);
        overrideTable = new java.util.LinkedHashMap<>();
        overridePatterns = new java.util.ArrayList<>();
        try {
            for (String s : com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_TIER_BONUS.get()) {
                String[] kv = splitPair(s);
                if (kv != null && !kv[1].isEmpty()) {
                    tierBonusTable.put(kv[0].toLowerCase(java.util.Locale.ROOT), parseDouble(kv[1], 0));
                }
            }
            for (String s : com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_VALUE_OVERRIDES.get()) {
                String[] kv = splitPair(s);
                if (kv == null) continue;
                String k = kv[0];
                double v = parseDouble(kv[1], -1);
                if (v < 0) continue;
                if (k.startsWith("#") || k.indexOf(42) >= 0) {          // 42 = '*'，标签/通配符
                    overridePatterns.add(k + "=" + v);
                } else {
                    overrideTable.put(k.toLowerCase(java.util.Locale.ROOT), v);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 解析 "键=值" */
    private static String[] splitPair(String s) {
        if (s == null) return null;
        String t = s.trim();
        int i = t.indexOf(61);                                          // 61 = '='
        if (i <= 0 || i == t.length() - 1) return null;
        return new String[]{t.substring(0, i).trim(), t.substring(i + 1).trim()};
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** 品质词加成（命中多个词取最高分，不叠加） */
    private static double tierBonus(String itemId) {
        ensureTables();
        double best = 0;
        for (Map.Entry<String, Double> e : tierBonusTable.entrySet()) {
            if (itemId.contains(e.getKey()) && e.getValue() > best) best = e.getValue();
        }
        return best;
    }

    /** 手动指定价值：精确 id → 通配符 → 标签（都没有返回 null，走正常计算） */
    private static Double overrideValue(Item item, String itemId) {
        ensureTables();
        Double exact = overrideTable.get(itemId);
        if (exact != null) return exact;
        for (String entry : overridePatterns) {
            String[] kv = splitPair(entry);
            if (kv == null) continue;
            String pattern = kv[0];
            double v = parseDouble(kv[1], -1);
            if (v < 0) continue;
            if (pattern.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(pattern.substring(1));
                if (tagId != null && item.builtInRegistryHolder().is(TagKey.create(Registries.ITEM, tagId))) return v;
                continue;
            }
            if (globMatch(pattern, itemId)) return v;
        }
        return null;
    }

    /** 配置里的拟造费用倍率（读不到时按默认 10 倍） */
    private static double costMultiplier() {
        try {
            return Math.max(0.0, com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_COST_MULTIPLIER.get());
        } catch (Throwable t) {
            return 10.0;
        }
    }

    /** 取该物品的"配方真实产物"模板（取不到则退回裸 new ItemStack） */
    public static ItemStack sampleResult(ServerPlayer player, Item item) {
        try {
            var level = player.serverLevel();
            ItemStack sample = cacheFor(level.getRecipeManager(), level.registryAccess()).samples().get(item);
            if (sample != null && !sample.isEmpty()) return sample.copy();
        } catch (Throwable ignored) {
        }
        return new ItemStack(item);
    }

    /** 该物品的功能价值项（魔法类加成；客户端列表预览用） */
    public static double functionTerm(net.minecraft.world.item.crafting.RecipeManager manager,
                                      net.minecraft.core.RegistryAccess access, Item item) {
        try {
            return cacheFor(manager, access).functionTerms().getOrDefault(item, 0.0);
        } catch (Throwable t) {
            return functionValue(item);
        }
    }

    /** 配置里的"原料价值项"权重（读不到时默认 0.75） */
    private static double ingredientWeight() {
        try {
            return Math.max(0.0, com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_INGREDIENT_WEIGHT.get());
        } catch (Throwable t) {
            return 0.75;
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

        /**
         * ⭐ 兜底记录：右键用拟造物"安装"的东西<b>不会</b>触发 {@code EntityPlaceEvent}
         * （典型例子：AE2 的 ME 输入接口 / 各种线缆部件 —— 它们不是方块，而是挂在方块上的 part，
         * 由模组自己的网络包处理放置）。
         *
         * <p>为了<b>精确到实例</b>（同一根线缆上可能还挂着玩家正常合成的同款部件，不能删错），
         * 这里在右键瞬间给宿主拍"已占用面"快照，几 tick 后（部件真正装上了）对比出新增面，
         * 只记那一面。
         */
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            if (event.getLevel().isClientSide()) return;
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            ItemStack held = sp.getMainHandItem();
            long until = held.hasTag() ? held.getTag().getLong(KEY_TEMP_UNTIL) : 0;
            if (until <= 0) {
                held = sp.getOffhandItem();
                until = held.hasTag() ? held.getTag().getLong(KEY_TEMP_UNTIL) : 0;
            }
            if (until <= 0 || held.isEmpty()) return;
            Item item = held.getItem();
            net.minecraft.core.BlockPos pos = event.getPos();
            long now = level.getGameTime();
            PartHost host = findPartHost(level, pos);
            int before = host == null ? -1 : occupiedMask(host);
            net.minecraft.core.BlockPos hostPos = host == null ? pos.immutable() : host.pos;
            PENDING_PARTS.add(new PendingPartPlace(level, hostPos, item, until, before, now + 2));
            net.minecraft.core.Direction face = event.getFace();
            if (face != null && host == null) {
                // 也可能在点击面的邻位新拉一根线缆承载部件
                PENDING_PARTS.add(new PendingPartPlace(level, pos.relative(face).immutable(), item, until, -1, now + 2));
            }
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
            if (PLACED_TEMPS.isEmpty() && PLACED_TEMP_PARTS.isEmpty() && SWEEP_QUEUE.isEmpty()) {
                tickContainerSweep(event.getServer());
                return;
            }
            // ⭐ 待确认的部件放置：右键后 2 tick 对比"新增面"，精确锁定拟造部件所在的那一面
            if (!PENDING_PARTS.isEmpty()) {
                long now = 0;
                Iterator<PendingPartPlace> qit = PENDING_PARTS.iterator();
                while (qit.hasNext()) {
                    PendingPartPlace q = qit.next();
                    ServerLevel level = q.level;
                    if (level == null) {
                        qit.remove();
                        continue;
                    }
                    now = level.getGameTime();
                    if (now < q.scanAt) continue;
                    qit.remove();
                    if (!level.isLoaded(q.hostPos)) continue;
                    PartHost host = findPartHostExact(level, q.hostPos);
                    if (host == null) continue;
                    int after = occupiedMask(host);
                    int added = q.sidesBefore < 0 ? 0 : (after & ~q.sidesBefore);
                    if (added == 0) {
                        // 快照失效（新拉线缆 / 没装上）：退化为"该宿主上刚好只有一个同款部件"才认
                        int single = singleMatchingSide(host, q.item);
                        if (single == 0) continue;
                        added = single;
                    }
                    PLACED_TEMP_PARTS.add(new PlacedTempPart(level, host.pos, q.item, q.until, added));
                }
            }
            // ⭐ 拟造"部件"（AE2 ME 输入接口等非方块形态）到期移除：只摘当初记下的那一面
            if (!PLACED_TEMP_PARTS.isEmpty()) {
                Iterator<PlacedTempPart> pit = PLACED_TEMP_PARTS.iterator();
                while (pit.hasNext()) {
                    PlacedTempPart p = pit.next();
                    ServerLevel level = p.level;
                    if (level == null || !level.isLoaded(p.pos)) continue;
                    long now = level.getGameTime();
                    if (now < p.until) continue;
                    // 先按"线缆部件"尝试摘掉（AE2 软依赖，纯反射），失败再按方块处理
                    if (!removeRecordedSides(level, p)) {
                        net.minecraft.world.level.block.state.BlockState cur = level.getBlockState(p.pos);
                        if (p.item instanceof net.minecraft.world.item.BlockItem bi
                                && cur.getBlock() == bi.getBlock()) {
                            level.levelEvent(2001, p.pos, net.minecraft.world.level.block.Block.getId(cur));
                            ejectContainerContents(level, p.pos);
                            level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 3);
                            smoke(level, p.pos);
                        }
                    }
                    pit.remove();
                }
            }
            tickContainerSweep(event.getServer());
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
                    // ⭐ 若方块是容器（箱子/潜影盒等），先把内部物品弹出，避免被直接移除吞掉
                    ejectContainerContents(level, p.pos);
                    level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), 3);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                            p.pos.getX() + 0.5, p.pos.getY() + 0.5, p.pos.getZ() + 0.5, 10,
                            0.3, 0.3, 0.3, 0.02);
                }
                it.remove();
            }
        }

        /**
         * 方块到期消失前，把其容器内容弹出到世界中（掉落物/拟造物掉地同样会到期消散）。
         * 兼容原版 {@link net.minecraft.world.Container} 与 Forge ITEM_HANDLER capability。
         */
        private static void ejectContainerContents(ServerLevel level, BlockPos pos) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
            // 原版容器（箱子/潜影盒/漏斗等）
            if (be instanceof net.minecraft.world.Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (s.isEmpty()) continue;
                    container.setItem(i, ItemStack.EMPTY);
                    spawnItemAt(level, pos, s);
                }
                return;
            }
            // Forge ITEM_HANDLER capability（模组机器/背包类方块）
            var cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null);
            if (cap.isPresent()) {
                cap.ifPresent(handler -> {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack s = handler.extractItem(i, handler.getSlotLimit(i), false);
                        if (!s.isEmpty()) {
                            spawnItemAt(level, pos, s);
                        }
                    }
                });
            }
        }

        private static void spawnItemAt(ServerLevel level, BlockPos pos, ItemStack stack) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            drop.setPickUpDelay(10);
            level.addFreshEntity(drop);
        }

        private static void smoke(ServerLevel level, BlockPos pos) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
        }

        // ============================================================
        //  ⭐ 拟造物进入容器后也会到期消失（防自动化吸取后永久保留）
        // ============================================================

        /** 扫描半径（区块） */
        private static final int SWEEP_RADIUS_CHUNKS = 8;
        /** 每 tick 扫描的区块数（预算式，避免卡服） */
        private static final int SWEEP_CHUNKS_PER_TICK = 2;
        /** 待扫区块队列（围绕某个玩家，轮流换人） */
        private static final java.util.ArrayDeque<net.minecraft.world.level.ChunkPos> SWEEP_QUEUE =
                new java.util.ArrayDeque<>();
        private static java.util.UUID sweepPlayerId;

        /**
         * 漏斗/管道/机器把拟造物吸进容器后，原来那套清理（玩家背包 + 正打开的容器 + 掉落物）就够不着了，
         * 物品会一直留着。这里<b>预算式</b>扫已加载区块的方块实体：每 tick 只处理
         * {@link #SWEEP_CHUNKS_PER_TICK} 个区块，围着在线玩家轮流推进，
         * 把到期拟造物从中抽出来。扫不完也不会卡服。
         */
        private static void tickContainerSweep(MinecraftServer server) {
            if (server == null) return;
            for (int n = 0; n < SWEEP_CHUNKS_PER_TICK; n++) {
                if (SWEEP_QUEUE.isEmpty() && !buildSweepQueue(server)) return;
                net.minecraft.world.level.ChunkPos cp = SWEEP_QUEUE.poll();
                ServerPlayer owner = sweepPlayerId == null ? null
                        : server.getPlayerList().getPlayer(sweepPlayerId);
                if (owner == null) {
                    SWEEP_QUEUE.clear();
                    return;
                }
                ServerLevel level = owner.serverLevel();
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
                if (chunk != null) sweepChunk(level, chunk, level.getGameTime());
            }
        }

        /** 挑一个在线玩家，把它周围的已加载区块排进队列 */
        private static boolean buildSweepQueue(MinecraftServer server) {
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return false;
            ServerPlayer sp = players.get(RANDOM.nextInt(players.size()));
            sweepPlayerId = sp.getUUID();
            ServerLevel level = sp.serverLevel();
            net.minecraft.world.level.ChunkPos center = new net.minecraft.world.level.ChunkPos(sp.blockPosition());
            for (int dx = -SWEEP_RADIUS_CHUNKS; dx <= SWEEP_RADIUS_CHUNKS; dx++) {
                for (int dz = -SWEEP_RADIUS_CHUNKS; dz <= SWEEP_RADIUS_CHUNKS; dz++) {
                    int x = center.x + dx, z = center.z + dz;
                    if (level.getChunkSource().getChunkNow(x, z) != null) {
                        SWEEP_QUEUE.add(new net.minecraft.world.level.ChunkPos(x, z));
                    }
                }
            }
            return !SWEEP_QUEUE.isEmpty();
        }

        /** 扫一个区块里的方块实体：容器 / ITEM_HANDLER capability 里的到期拟造物清除 */
        private static void sweepChunk(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk, long now) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be == null || be.isRemoved()) continue;
                try {
                    if (be instanceof net.minecraft.world.Container container) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            if (isExpiredTemp(container.getItem(i), now)) {
                                container.setItem(i, ItemStack.EMPTY);
                            }
                        }
                        continue;
                    }
                    var cap = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null);
                    if (cap.isPresent()) {
                        var handler = cap.resolve().orElse(null);
                        if (handler == null) continue;
                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack s = handler.getStackInSlot(i);
                            if (isExpiredTemp(s, now)) {
                                handler.extractItem(i, s.getCount(), false);
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // 单个方块实体出问题不影响整体扫描
                }
            }
        }

        private static boolean isExpiredTemp(ItemStack stack, long now) {
            if (stack == null || stack.isEmpty() || !stack.hasTag()) return false;
            long until = stack.getTag().getLong(KEY_TEMP_UNTIL);
            return until > 0 && until <= now;
        }

        // ============================================================
        //  ⭐ 线缆部件（AE2 ME 输入接口这类非方块形态）到期摘除：精确到"哪一面"
        // ============================================================

        /** 带反射句柄的部件宿主（AE2 的 {@code appeng.api.parts.IPartHost}） */
        private static final class PartHost {
            final BlockEntity be;
            final BlockPos pos;
            final java.lang.reflect.Method getPart;
            final java.lang.reflect.Method removeFromSide;

            PartHost(BlockEntity be, Class<?> iface) throws NoSuchMethodException {
                this.be = be;
                this.pos = be.getBlockPos().immutable();
                this.getPart = iface.getMethod("getPart", net.minecraft.core.Direction.class);
                this.removeFromSide = iface.getMethod("removePartFromSide", net.minecraft.core.Direction.class);
            }

            Object part(net.minecraft.core.Direction d) {
                try {
                    return getPart.invoke(be, d);
                } catch (Throwable t) {
                    return null;
                }
            }

            boolean remove(net.minecraft.core.Direction d) {
                try {
                    removeFromSide.invoke(be, d);
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }
        }

        /** 该位置或其六个邻居上的部件宿主（软依赖：找不到 AE2 就是 null） */
        private static PartHost findPartHost(ServerLevel level, BlockPos pos) {
            PartHost h = findPartHostExact(level, pos);
            if (h != null) return h;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                h = findPartHostExact(level, pos.relative(d));
                if (h != null) return h;
            }
            return null;
        }

        private static PartHost findPartHostExact(ServerLevel level, BlockPos pos) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return null;
            Class<?> iface = findInterface(be.getClass(), "appeng.api.parts.IPartHost");
            if (iface == null) return null;
            try {
                return new PartHost(be, iface);
            } catch (Throwable t) {
                return null;
            }
        }

        /** 六个面里哪些被占用（位掩码），用于"新增面"对比 */
        private static int occupiedMask(PartHost host) {
            int mask = 0;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (host.part(d) != null) mask |= 1 << d.ordinal();
            }
            return mask;
        }

        /** 该宿主上"物品匹配且全局唯一"的那一面（0 = 没有或不止一个，无法确定就不动） */
        private static int singleMatchingSide(PartHost host, Item item) {
            int mask = 0;
            int count = 0;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (partItemOf(host.part(d)) == item) {
                    mask |= 1 << d.ordinal();
                    count++;
                }
            }
            return count == 1 ? mask : 0;
        }

        private static Item partItemOf(Object part) {
            if (part == null) return null;
            try {
                Object partItem = part.getClass().getMethod("getPartItem").invoke(part);
                if (partItem instanceof net.minecraft.world.level.ItemLike like) return like.asItem();
            } catch (Throwable ignored) {
            }
            return null;
        }

        /**
         * 到期摘除：<b>只摘当初记录的那一面</b>，并且那一面上还得是同款物品才动。
         * 这样同一根线缆上玩家后来正常合成的同款部件不会被误删。
         *
         * @return true = 按部件处理过了（无论是否真摘掉）
         */
        private static boolean removeRecordedSides(ServerLevel level, PlacedTempPart p) {
            PartHost host = findPartHostExact(level, p.pos);
            if (host == null) return false;
            boolean handled = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if ((p.sidesMask & (1 << d.ordinal())) == 0) continue;
                handled = true;
                if (partItemOf(host.part(d)) != p.item) continue;   // 那一面已被换掉 → 不动
                if (host.remove(d)) {
                    smoke(level, host.pos);
                    TinkersNewlife.LOGGER.info("[构筑] 已摘除到期拟造部件: {} @ {} {}", p.item, host.pos, d);
                }
            }
            return handled;
        }

        /** 在类层次（含接口继承）里找指定名称的接口 */
        private static Class<?> findInterface(Class<?> type, String name) {
            Class<?> c = type;
            while (c != null && c != Object.class) {
                for (Class<?> itf : c.getInterfaces()) {
                    if (name.equals(itf.getName())) return itf;
                    Class<?> deep = findInterface(itf, name);
                    if (deep != null) return deep;
                }
                c = c.getSuperclass();
            }
            return null;
        }
    }

    /** 待确认的部件放置（右键快照 → 2 tick 后对比新增面） */
    private static final class PendingPartPlace {
        final ServerLevel level;
        final BlockPos hostPos;
        final Item item;
        final long until;
        /** -1 = 放置前没有宿主（快照不可用） */
        final int sidesBefore;
        final long scanAt;

        PendingPartPlace(ServerLevel level, BlockPos hostPos, Item item, long until, int sidesBefore, long scanAt) {
            this.level = level;
            this.hostPos = hostPos;
            this.item = item;
            this.until = until;
            this.sidesBefore = sidesBefore;
            this.scanAt = scanAt;
        }
    }

    /** 一个"被右键安装的拟造部件"记录（AE2 线缆部件等非方块形态；sidesMask 精确到哪一面） */
    private static final class PlacedTempPart {
        final ServerLevel level;
        final BlockPos pos;
        final Item item;
        final long until;
        /** 位掩码：Direction.ordinal() 位；哪个面上是这颗拟造部件 */
        final int sidesMask;

        PlacedTempPart(ServerLevel level, BlockPos pos, Item item, long until, int sidesMask) {
            this.level = level;
            this.pos = pos;
            this.item = item;
            this.until = until;
            this.sidesMask = sidesMask;
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
