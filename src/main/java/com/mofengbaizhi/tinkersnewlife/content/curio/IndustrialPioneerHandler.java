package com.mofengbaizhi.tinkersnewlife.content.curio;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.IndustrialPioneerCertificateItem;
import com.mofengbaizhi.tinkersnewlife.content.rate.ContainerRateManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>工业开拓之证</b>的属性结算（§738）—— 按"绑定的那个维度"的产率给佩戴者加属性 ✓
 *
 * <h2>规则（用户口径 ✓ 逐条对应）</h2>
 * <ol>
 *   <li><b>每一种物品</b>净产率 ≥ {@link #ITEM_RATE_THRESHOLD}（<b>2000 个/时</b> ✓）
 *       ⇒ 最大生命 <b>+5</b> ✓，合计上限 <b>+200</b>（= 40 种 ✓）；</li>
 *   <li><b>每一种流体</b>净产率 ≥ {@link #FLUID_RATE_THRESHOLD}（<b>20000 mB/时</b> ✓）
 *       ⇒ 移动速度 <b>+0.1</b> ✓，上限 <b>10</b>（= 100 种 ✓）；</li>
 *   <li><b>FE 净存量增长</b>达到 <b>1k / 10k / 1M / 1KM（=1e9）/时</b> ✓
 *       ⇒ 空手攻击 <b>+2.5 / +5 / +10 / +20</b> ✓（<b>取达到的最高档</b> ✓ 不叠加 ✓）。</li>
 * </ol>
 *
 * <h2>⚠ 三处我做了"口径解读"，请核对（都只改常量就行 ✓）</h2>
 * <ol>
 *   <li><b>速度单位</b>：把"0.1 速度加成 / 上限 10 点"读成
 *       <b>每有一种流体 +0.1%（即 0.1"点"，1 点 = 1%）</b> ⇒ 上限 <b>+10%</b> ✓
 *       （用 {@code MULTIPLY_TOTAL = 0.001 × 种数} ✓）。
 *       若你要的是"<b>+0.1 = +10%</b>"（上限 +1000% ✗ 明显过大）或别的刻度，改
 *       {@link #SPEED_PER_FLUID} / {@link #SPEED_CAP} 两个常量即可 ✓；</li>
 *   <li><b>FE 档位是"取最高档"</b> ✓（不是 2.5+5+10+20 累加 ✗）—— 要累加就把
 *       {@link #energyAttack} 改成求和 ✓；</li>
 *   <li><b>"每时"用的窗口 = 最近 1 小时</b>（{@link #WINDOW} = 6 个 10 分钟区间 ✓）
 *       —— 而不是"最近那 10 分钟 ×6" ✗（后者抖得厉害 ✓）。</li>
 * </ol>
 *
 * <h2>其它实现要点</h2>
 * <ul>
 *   <li><b>只认 charm 槽</b> ✓（与物品的 {@code canEquip} 一致 ✓；不戴 / 换槽 ⇒ 立刻撤掉加成 ✓）；</li>
 *   <li>加成用<b>瞬态</b>修饰符（{@code addTransientModifier} ✓ 固定 UUID ⇒ 不会越叠越多 ✓
 *       也不会写进玩家存档 ✗ —— 条件型加成不该永久留档 ✓）；</li>
 *   <li><b>空手攻击</b>：只有主手为空时才挂 ✓（一拿东西就撤 ✓ 放下立刻回来 ✓）；</li>
 *   <li><b>性能</b>：每 20 tick 走一遍在线玩家 ✓；每个维度的加成<b>缓存 5 秒</b>（{@link #BONUS_CACHE} ✓）
 *       —— 产率本身 10 分钟才变一次 ✓ 没必要每 tick 重算 ✓；</li>
 *   <li>加成<b>不等于</b>"实时产率"✗：它跟着我们统计引擎的 10 分钟节奏走 ✓
 *       ⇒ 新农场跑满 1 小时后才会顶到该有的档位 ✓（口径如此 ✓ 如实说明 ✓）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IndustrialPioneerHandler {

    /** 只认这个槽 ✓ */
    public static final String SLOT = "charm";

    // ============ 口径常量（要调就调这里 ✓） ============

    /** 物品阈值：2000 个/时 ✓ */
    public static final double ITEM_RATE_THRESHOLD = 2000.0D;
    /** 每种达标物品给的最大生命 ✓ */
    public static final double HEALTH_PER_ITEM = 5.0D;
    /** 最大生命加成上限 ✓ */
    public static final double HEALTH_CAP = 200.0D;

    /** 流体阈值：20000 mB/时 ✓ */
    public static final double FLUID_RATE_THRESHOLD = 20000.0D;
    /** 每种达标流体给的速度（0.001 = +0.1% ✓ 见类注释的解读 ✓） */
    public static final double SPEED_PER_FLUID = 0.001D;
    /** 速度加成的上限（0.10 = +10% ✓） */
    public static final double SPEED_CAP = 0.10D;

    /** FE 档位（/时 ✓）：1k / 10k / 1M / 1KM(=1e9) ✓ */
    public static final long[] ENERGY_TIERS = {1_000L, 10_000L, 1_000_000L, 1_000_000_000L};
    /** 各档对应的空手攻击 ✓ */
    public static final double[] ENERGY_ATTACK = {2.5D, 5.0D, 10.0D, 20.0D};

    /** "每时"的统计窗口 = 最近 6 个 10 分钟区间 = 1 小时 ✓ */
    public static final int WINDOW = 6;

    // ============ 属性修饰符 UUID（固定 ⇒ 幂等 ✓） ============

    private static final UUID HEALTH_UUID = UUID.fromString("8f1a7c34-5b2e-4d19-9c7a-6e3f0b1d2a41");
    private static final UUID SPEED_UUID = UUID.fromString("1d9b5e77-3a4c-42f8-8b60-7c2d9e4f5a13");
    private static final UUID ATTACK_UUID = UUID.fromString("c47e2f18-9d63-4a05-b1e8-52af6c0d7b92");

    /** 加成缓存（每维度一份 ✓ 5 秒一算 ✓） */
    private static final Map<ResourceKey<Level>, Cached> BONUS_CACHE = new HashMap<>();
    private static final int CACHE_TICKS = 100;

    private IndustrialPioneerHandler() {
    }

    // ============================================================
    //  服务端 tick：每 20 tick 结算一次 ✓
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                update(player, server);
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[工业开拓之证] 结算玩家 {} 失败：{}",
                        player.getName().getString(), t.toString());
            }
        }
    }

    private static void update(ServerPlayer player, MinecraftServer server) {
        ItemStack charm = equippedCharm(player);
        ResourceKey<Level> bound = charm.isEmpty()
                ? null : IndustrialPioneerCertificateItem.boundDimension(charm);
        ServerLevel level = bound == null ? null : server.getLevel(bound);
        if (level == null) {
            clear(player);                                  // 没戴 / 没绑 / 维度取不到 ⇒ 撤掉全部加成 ✓
            return;
        }

        Bonus bonus = cachedBonus(level, server.getTickCount());
        apply(player, Attributes.MAX_HEALTH, HEALTH_UUID, "tnl_pioneer_health",
                bonus.maxHealth(), AttributeModifier.Operation.ADDITION);
        apply(player, Attributes.MOVEMENT_SPEED, SPEED_UUID, "tnl_pioneer_speed",
                bonus.speed(), AttributeModifier.Operation.MULTIPLY_TOTAL);
        // 空手才给攻击加成 ✓
        double attack = player.getMainHandItem().isEmpty() ? bonus.attack() : 0.0D;
        apply(player, Attributes.ATTACK_DAMAGE, ATTACK_UUID, "tnl_pioneer_attack",
                attack, AttributeModifier.Operation.ADDITION);
    }

    /** 取下全部加成 ✓（卸饰品 / 换维度 / 维度没了 都会走到这里 ✓） */
    private static void clear(ServerPlayer player) {
        apply(player, Attributes.MAX_HEALTH, HEALTH_UUID, "tnl_pioneer_health", 0.0D,
                AttributeModifier.Operation.ADDITION);
        apply(player, Attributes.MOVEMENT_SPEED, SPEED_UUID, "tnl_pioneer_speed", 0.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        apply(player, Attributes.ATTACK_DAMAGE, ATTACK_UUID, "tnl_pioneer_attack", 0.0D,
                AttributeModifier.Operation.ADDITION);
    }

    /** 幂等挂/撤一个修饰符 ✓（值没变就什么都不做 ✓） */
    private static void apply(ServerPlayer player, Attribute attribute, UUID uuid, String name,
                              double value, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        if (value == 0.0D) {
            instance.removeModifier(uuid);
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null && existing.getAmount() == value && existing.getOperation() == operation) return;
        instance.removeModifier(uuid);
        instance.addTransientModifier(new AttributeModifier(uuid, name, value, operation));
    }

    // ============================================================
    //  加成计算（对外也开放 ✓ 供以后做展示 ✓）
    // ============================================================

    /** 这一维度当前能给多少加成 ✓（**纯粹读统计引擎** ✓ 不改玩家 ✓） */
    public static Bonus bonus(ServerLevel level) {
        int itemKinds = 0;
        for (double rate : ContainerRateManager.netPerHourAll(level, WINDOW).values()) {
            if (rate >= ITEM_RATE_THRESHOLD) itemKinds++;
        }
        int fluidKinds = 0;
        for (double rate : ContainerRateManager.fluidNetPerHourAll(level, WINDOW).values()) {
            if (rate >= FLUID_RATE_THRESHOLD) fluidKinds++;
        }
        double energyRate = ContainerRateManager.energyNetPerHour(level, WINDOW);
        int tier = 0;
        for (int i = 0; i < ENERGY_TIERS.length; i++) {
            if (energyRate >= (double) ENERGY_TIERS[i]) tier = i + 1;
        }
        return new Bonus(
                Math.min(HEALTH_CAP, HEALTH_PER_ITEM * itemKinds),
                Math.min(SPEED_CAP, SPEED_PER_FLUID * fluidKinds),
                tier == 0 ? 0.0D : ENERGY_ATTACK[tier - 1],
                itemKinds, fluidKinds, energyRate, tier);
    }

    /** 5 秒缓存（产率 10 分钟才变一次 ✓ 没必要每 tick 重算 ✓） */
    private static Bonus cachedBonus(ServerLevel level, long tick) {
        Cached cached = BONUS_CACHE.get(level.dimension());
        if (cached != null && tick - cached.tick() < CACHE_TICKS) return cached.bonus();
        Bonus bonus = bonus(level);
        BONUS_CACHE.put(level.dimension(), new Cached(tick, bonus));
        return bonus;
    }

    /** 戴着的那枚（**只认 charm 槽** ✓ —— 不用 {@code findFirstCurio} ✗ 它会无视槽位类型 ✗） */
    private static ItemStack equippedCharm(ServerPlayer player) {
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var handler = curios.get().getStacksHandler(SLOT);
        if (handler.isEmpty()) return ItemStack.EMPTY;
        IDynamicStackHandler stacks = handler.get().getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.getItem() instanceof IndustrialPioneerCertificateItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 一个维度的加成快照 ✓ */
    public record Bonus(double maxHealth, double speed, double attack,
                        int itemKinds, int fluidKinds, double energyRate, int energyTier) {
    }

    private record Cached(long tick, Bonus bonus) {
    }
}
