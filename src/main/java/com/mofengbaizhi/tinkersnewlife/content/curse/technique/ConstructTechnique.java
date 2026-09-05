package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenConstructScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 术式「构筑术式」：
 * <p>
 * 顺转（术式键 C）：开关「无限弹药模式」。开启后，手持会从背包请求弹药的武器
 * （原版弓 / 弩、匠魂弓弩等 {@link ProjectileWeaponItem}，以及 TACZ 枪械——反射识别）时，
 * 每当背包中对应弹药不足就消耗咒力凝结出箭矢 / 子弹，让玩家无需囤积弹药。
 * <p>
 * 反转（反转键 F）：打开「拟造物品栏」——从所有有合成配方的物品中挑选一件，
 * 服务端按其珍贵程度与威力换算咒力消耗，拟造一个 60 秒后自动消散的临时物品。
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

    /** TACZ 枪械接口反射缓存 */
    private static Class<?> taczIGunClass;
    private static Method taczGetAmmoId;

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

    /** 登出/死亡：关闭顺转模式（避免重进后仍在耗咒力补给） */
    public static void cleanup(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_AMMO_MODE, false);
    }

    // ============================================================
    //  反转（F）：打开拟造物品栏
    // ============================================================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenConstructScreen());
    }

    // ============================================================
    //  服务端 tick 驱动（由主类对所有在线玩家调用）
    // ============================================================

    /**
     * 每 tick：顺转模式弹药补给（间隔检查）+ 临时拟造物到期清理（间隔检查）。
     */
    public static void tickServer(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        // 顺转：无限弹药补给
        if (isAmmoModeOn(player) && player.isAlive() && !player.isRemoved()
                && now % AMMO_CHECK_INTERVAL == 0) {
            INSTANCE.tickAmmo(player);
        }
        // 反转：临时物到期清理
        if (player.isAlive() && !player.isRemoved() && now % TEMP_CHECK_INTERVAL == 0) {
            sweepTemps(player, now);
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
        // 单价：箭矢/子弹按 (1-亲和/100)×(1+输出×0.5)，最低 1
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int unitCost = Math.max(1, (int) Math.ceil((1.0 - affinity / 100.0) * (1 + output * 0.5)));
        long totalCost = (long) need * unitCost;
        if (totalCost > Integer.MAX_VALUE) return;
        // 支付（创造/无限免费；不足自动关闭模式避免每 tick 刷屏）
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, totalCost) < 0) {
            player.getPersistentData().putBoolean(KEY_AMMO_MODE, false);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.construct.ammo_no_curse"), true);
            return;
        }
        ItemStack add = new ItemStack(ammoItem, need);
        boolean added = player.getInventory().add(add);
        if (!added && !add.isEmpty()) {
            // 背包满：剩余部分掉落脚下
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), add);
            drop.setPickUpDelay(0);
            player.serverLevel().addFreshEntity(drop);
        }
        if (player.level().isClientSide) return;
        player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.2, player.getZ(), need / 2 + 1, 0.3, 0.3, 0.3, 0);
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
     * 解析该武器请求的弹药物品类型。
     * 原版弓只认普通箭；弩/匠魂弓弩优先保持玩家背包已有的箭头类型；
     * TACZ 枪械反射取对应弹药。
     */
    private static Item resolveAmmoItem(ServerPlayer player, ItemStack weapon) {
        Item item = weapon.getItem();
        // TACZ 枪械：反射取弹药 id
        if (isTaczGun(item)) {
            return taczAmmoItem(player, weapon);
        }
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
    // ============================================================

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

    /** 反射调用 IGun#getAmmoId(ItemStack) 获取枪械弹药物品；失败返回 null */
    private static Item taczAmmoItem(ServerPlayer player, ItemStack gun) {
        try {
            if (taczIGunClass == null) {
                taczIGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            }
            if (taczGetAmmoId == null) {
                taczGetAmmoId = taczIGunClass.getMethod("getAmmoId", ItemStack.class);
            }
            Object rl = taczGetAmmoId.invoke(gun);
            if (rl instanceof ResourceLocation loc) {
                Item ammo = ForgeRegistries.ITEMS.getValue(loc);
                return ammo != null && ammo != Items.AIR ? ammo : null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ============================================================
    //  拟造（服务端入口，由 PacketConstructSelect 调用）
    // ============================================================

    /**
     * 校验并拟造临时物品。
     *
     * @return 0 成功；1 无此术式/物品无效；2 无合成配方；3 咒力不足；4 背包已满
     */
    public static int forge(ServerPlayer player, String itemId) {
        if (!Modifiers.CONSTRUCT.getId().equals(com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler.getSelectedTechniqueId(player))) {
            return 1;
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
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putLong(KEY_TEMP_UNTIL, player.serverLevel().getGameTime() + TEMP_TICKS);
        // 加上浅紫色"构筑"前缀，便于辨识临时物
        Component original = stack.getHoverName();
        stack.setHoverName(Component.translatable("item.tinkersnewlife.construct.prefix").append(original));
        boolean added = player.getInventory().add(stack);
        if (!added) {
            // 背包满：丢在脚下（临时物掉地同样受 lifespan 限制，60 秒内自动消散）
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            drop.setPickUpDelay(0);
            player.serverLevel().addFreshEntity(drop);
        }
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.construct.forged", stack.getHoverName(), cost,
                TEMP_TICKS / 20), false);
        return 0;
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

    /** 扫描玩家全部物品栏（含盔甲/副手），移除到期的拟造物 */
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
            long left = until - now;
            if (left <= 0) {
                event.setCanceled(true);
            } else {
                item.lifespan = (int) Math.min(left + 20, ITEM_ENTITY_LIFESPAN_CAP);
            }
        }
    }
}
