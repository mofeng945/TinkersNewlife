package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 灵魂获取增幅处理器：服务端每 tick 记录玩家灵魂能量，检测"本 tick 新增的灵魂"（Goety 攻击/击杀等自增），
 * 按玩家装备（主/副手 + 盔甲槽）上的相关修饰符补发增幅。
 *
 * <h2>§1070 噬魂按<b>诡厄本体附魔</b>的口径重写（用户口径 ✓）</h2>
 * 用户原话：「<b>我要的是模仿诡厄本体噬魂附魔的效果，而不是给自己上灵魂饥饿</b>」✓
 *
 * <p>本体实现（用 cfr 反编译 {@code goety-2.5.57.3} 逐行看过 ✓，不是猜的 ✗）：
 * <ul>
 *   <li>{@code SEHelper.SoulMultiply(entity, source)} ✓：
 *       {@code if ((物理攻击 || 该实体拥有的弹射物) && 主手武器.getEnchantmentLevel(goety:soul_eater) > 0)
 *       multiply = clamp(等级 + 1, 1, 10);} ✓</li>
 *   <li>{@code SEHelper.rawHandleKill(killer, victim, soulEater, source)} ✓：
 *       {@code soulGain = floor(getSoulGiven(victim) × soulEater) × 配置倍率} ✓；
 *       归属：{@code killer} 是玩家 ✓，或 {@code killer} 是 {@code OwnableEntity} 且其主人是玩家 ✓
 *       （<b>宠物/随从击杀也算主人的</b> ✓）。</li>
 * </ul>
 * ⇒ <b>本体噬魂 ＝ 击杀时灵魂获取 ×(等级＋1)</b> ✓（噬魂 I ⇒ ×2 ✓、II ⇒ ×3 ✓、III ⇒ ×4 ✓），
 * <b>而且只对击杀生效</b> ✓（近战 ✓ 或自己射出的弹射物 ✓）。
 *
 * <p>本类与本体<b>数学上等价</b> ✓：那份"基数"由 Goety 自己算 ✓，我们不去重算 ✗，
 * 而是沿用"每 tick 观察灵魂增量"拿到**基数** ✓（＝ 未加成的那一份 ✓），
 * 再按本体倍率补发 {@code 增量 × 等级} ✓ ⇒ 合计即 {@code 基数 × (等级＋1)} ✓
 * （基数与配置倍率都是整数 ✓ ⇒ 与本体 {@code floor(...) × multi} 完全一致 ✓）。
 *
 * <h2>触发条件（照本体 ✓）</h2>
 * <ul>
 *   <li><b>必须是击杀</b> ✓：{@link #onLivingDeath} 记下"算谁头上"（玩家本人 ✓ / 其宠物随从 ✓ /
 *       其射出的弹射物 ✓），随后 {@link #KILL_WINDOW_TICKS} tick 内的灵魂增量才算"击杀所得" ✓；
 *       —— 这正是本轮与旧实现的关键差别 ✓：旧版对**任何来源**的灵魂增量都给 +25% ✗；</li>
 *   <li><b>必须是近战或自己射出的弹射物</b> ✓（对应本体的 {@code physicalAttacks || 自己的弹射物} ✓）：
 *       火焰/摔落/别人的弹射物等间接来源不算 ✓；</li>
 *   <li>噬魂挂在哪里：本体要求"主手武器" ✓；本仓的噬魂是**匠魂强化** ✓（可装在武器 ✓，也可能随材料
 *       出现在头盔等装备上 ✓ 见备忘 132）⇒ 这里沿用"主/副手 ＋ 盔甲槽"全查 ✓，是本仓有意的放宽 ✓。</li>
 * </ul>
 *
 * <p>⚠ <b>关于「灵魂饥饿」（§1071 更正 ✓）</b>：本类**从不主动施加** {@code goety:soul_hunger} ✓，
 * 但**旧实现确实会间接招来它** ✗ —— 补发灵魂时走的是"反射直接写 SEActive 能力值" ✗，
 * 而诡厄 {@code SoulEnergyEvents} 有一条状态规则 ✗：
 * <pre>
 * if (!getSEActive() &amp;&amp; getSoulEnergy() &gt; 0) { 每 tick 挂灵魂饥饿 ✗ ＋ 每 5 tick 抽 1 点灵魂 ✗ }
 * </pre>
 * ⇒ 在**图腾模式**（未开阿卡祭坛）的玩家身上写能力值 ⇒ 触发该规则 ✗
 * （用户症状：拿噬魂杀怪 ⇒ 自己上灵魂饥饿 ✗、翻倍时有时无 ✗）。
 * ⇒ §1071 已把加灵魂改走诡厄自己的入口 {@code SEHelper.increaseSouls} ✓（见 {@code SoulEnergyBridge#addSouls} ✓），
 * 从此不再触发那条规则 ✓。
 *
 * <h2>「人屠」</h2>
 * {@code butcher}（灵魂获取 ×2 ✓）是<b>我们自己的</b>强化 ✓，本轮未改 ✗：仍是"本 tick 自然增量再补一份" ✓，
 * 与噬魂共用同一个基线探测器 ✓（本 tick 只探测一次 ✓、同一次结算 ✓）。
 * 通过把「上次观察基线」更新到补发后的值，避免把本处理器补发的部分再次计入增量（不递归放大 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoulEaterHandler {

    private static final ModifierId SOUL_EATER = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID + ":soul_eater"));
    private static final ModifierId BUTCHER = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID + ":butcher"));

    /** 上次观察到的灵魂能量基线（含本处理器补发部分） */
    private static final Map<UUID, Integer> LAST_SOULS = new HashMap<>();
    /** 玩家 uuid → 最近一次"算在他头上的击杀"的 tick ✓（本体在击杀那一刻结算 ✓ 这里留一个小窗口 ✓） */
    private static final Map<UUID, Integer> LAST_KILL_TICK = new HashMap<>();

    /** 击杀后多久内的灵魂增量算作"这次击杀所得"（tick ✓ 本体同 tick 结算 ✓ 留 5 tick 余量 ✓） */
    public static final int KILL_WINDOW_TICKS = 5;

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        int cur = SoulEnergyBridge.getSouls(sp);
        Integer prev = LAST_SOULS.put(sp.getUUID(), cur);

        // §1072 顺手修一次"旧版遗留的异常状态"（非 SEActive 却能力值 > 0 ⇒ 诡厄会每 tick 挂灵魂饥饿 ✗）：
        //   有图腾就把滞留灵魂搬进图腾并清零能力 ✓；没有就什么都不做 ✓；修好后再调用只是一次反射读 ✓。
        SoulEnergyBridge.repairStuckCapability(sp);

        if (prev == null) return;                       // 首次基线
        int delta = cur - prev;
        if (delta <= 0) return;                         // 仅有增量（获得灵魂）才增幅

        // 人屠：灵魂获取 ×2（把本 tick 自然增量再补一份）—— 我们自己的强化，未改 ✓
        if (modifierLevelOnEquipment(sp, BUTCHER) > 0) {
            SoulEnergyBridge.addSouls(sp, delta);
        }

        // 噬魂（§1070 本体口径 ✓）：只认击杀所得 ✓ 且按 ×(等级＋1) ⇒ 追加 增量 × 等级 ✓
        int lv = modifierLevelOnEquipment(sp, SOUL_EATER);
        if (lv > 0 && justKilled(sp)) {
            SoulEnergyBridge.addSouls(sp, delta * lv);
        }

        // 把基线更新到补发后的值，避免本次补发被下次当作增量
        LAST_SOULS.put(sp.getUUID(), SoulEnergyBridge.getSouls(sp));
    }

    /** 这个玩家最近 {@link #KILL_WINDOW_TICKS} tick 内是否有"算他头上的击杀" ✓ */
    private static boolean justKilled(ServerPlayer sp) {
        Integer at = LAST_KILL_TICK.get(sp.getUUID());
        return at != null && sp.tickCount - at <= KILL_WINDOW_TICKS;
    }

    /**
     * 击杀结算 ✓（§1070）：按本体的"归属"口径找出该记在谁头上 ✓ ——
     * 玩家本人 ✓ / 其宠物或随从（{@link OwnableEntity} ✓ 或诡厄仆从 ✓）✓ / 其射出的弹射物 ✓；
     * 并且**只认"近战或自己射出的弹射物"** ✓（火焰/摔落/他人弹射物等间接来源不算 ✓，照本体 ✓）。
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Player owner = resolveKillOwner(event.getSource());
        if (owner == null) return;
        LAST_KILL_TICK.put(owner.getUUID(), owner.tickCount);
    }

    /** 按本体口径解析"这一击的主人" ✓；不是近战/自家弹射物 或 主人不是玩家 ⇒ null ✓ */
    private static Player resolveKillOwner(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();

        // ① 弹射物：只认"主人就是该实体"的那一发 ✓（本体：projectile.getOwner() == entity ✓）
        if (direct instanceof Projectile projectile) {
            return ownerOf(projectile.getOwner());
        }
        // ② 近战：直接命中的实体就是攻击实体 ✓（本体 physicalAttacks 的等价近似 ✓）
        if (direct != null && direct == attacker) {
            return ownerOf(attacker);
        }
        return null;
    }

    /** 把"击杀者实体"换算成该拿灵魂的玩家 ✓（本人 ✓ / 宠物随从的主人 ✓）；其余 null ✓ */
    private static Player ownerOf(Entity killer) {
        if (killer instanceof Player player) {
            return player;
        }
        if (killer instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player player) {
            return player;                                 // 本体：OwnableEntity 的主人 ✓
        }
        if (killer instanceof LivingEntity living) {
            LivingEntity goetyOwner = GoetyBridge.getServantOwner(living);
            if (goetyOwner instanceof Player player) {
                return player;                             // 诡厄仆从 ✓
            }
        }
        return null;
    }

    /** 玩家装备（主/副手 + 盔甲槽）上指定修饰符的总等级（人屠/噬魂共用） */
    private static int modifierLevelOnEquipment(ServerPlayer sp, ModifierId id) {
        int total = 0;
        for (ItemStack stack : candidateStacks(sp)) {
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool != null) {
                total += ToolHelper.getActiveModifierLevel(tool, id);
            }
        }
        return total;
    }

    private static java.util.List<ItemStack> candidateStacks(ServerPlayer sp) {
        java.util.List<ItemStack> list = new java.util.ArrayList<>(6);
        list.add(sp.getMainHandItem());
        list.add(sp.getOffhandItem());
        sp.getArmorSlots().forEach(list::add);
        return list;
    }
}
