package com.mofengbaizhi.tinkersnewlife.content.curse.skill;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.BaseDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.FuMoYuChuZiDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.WuLiangKongChuDomain;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 新阴流三技巧（弥虚葛笼 / 落花之情 / 新阴流·简易领域）——被他人领域包裹时的被动抵御。
 * <p>
 * 触发：玩家佩戴的咒力核心装有技巧特性，且玩家当前被<b>他人领域</b>（非本人领域球内，同维度）包裹。
 * <ul>
 *   <li>共同效果：持续消耗咒力（24/秒）抵御领域效果——无量空处的定身、伏魔御厨子的斩击等
 *       对该玩家无效；咒力耗尽则技巧失效、领域效果照常生效。</li>
 *   <li>弥虚葛笼：绝对抵御（含无量）。代价：自我定身——不能用术式/物品、无法移动。</li>
 *   <li>落花之情：抵御除无量空处定身以外的领域效果（防不住无量）。自身可自由行动但移动较慢。</li>
 *   <li>简易领域：抵御领域效果。代价：不能用术式（咒力维持结界），可移动、可用物品。</li>
 * </ul>
 * 领域对被包裹玩家施加效果前调用 {@link #isProtected} 判定（实时计算，避免 tick 顺序竞态）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkillHandler {

    private SkillHandler() {}

    public enum SkillType { MIXU_GELONG, LUOHUA, JIANYI_LINGYU }

    /** 每秒咒力消耗（三技巧统一） */
    public static final double COST_PER_SECOND = 24.0;

    /** 弥虚自我定身标记（离开领域/技巧失效时据此移除，避免误删其它来源的定身） */
    private static final Set<UUID> SELF_STUN = ConcurrentHashMap.newKeySet();
    /** 上次激活提示：UUID → 技巧类型（用于进出提示一次） */
    private static final Map<UUID, SkillType> LAST = new ConcurrentHashMap<>();

    private static ModifierId skillModifier(SkillType t) {
        return switch (t) {
            case MIXU_GELONG -> Modifiers.MIXU_GELONG.getId();
            case LUOHUA -> Modifiers.LUOHUA.getId();
            case JIANYI_LINGYU -> Modifiers.JIANYI_LINGYU.getId();
        };
    }

    /** 全部已注册技巧修饰符 id（供剥离/槽位配方等遍历；新增技巧自动包含） */
    public static java.util.Set<ModifierId> getAllSkillIds() {
        java.util.Set<ModifierId> out = new java.util.HashSet<>();
        for (SkillType t : SkillType.values()) {
            out.add(skillModifier(t));
        }
        return out;
    }

    /** 该修饰符是否为本模组已注册的技巧 */
    public static boolean isSkill(ModifierId id) {
        for (SkillType t : SkillType.values()) {
            if (skillModifier(t).equals(id)) return true;
        }
        return false;
    }

    /** 佩戴核心上装的技巧（无则 null） */
    public static SkillType skillOn(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return null;
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return null;
        for (SkillType t : SkillType.values()) {
            if (tool.getModifierLevel(skillModifier(t)) > 0) return t;
        }
        return null;
    }

    /** 咒力是否够维持技巧（宽松判：≥ 2 tick 分片；无限模式恒真） */
    private static boolean canAfford(ServerPlayer p) {
        return CursePowerHelper.isCurseInfinite(p)
                || CursePowerHelper.getCurse(p) >= COST_PER_SECOND / 20.0 * 2.0;
    }

    /**
     * 领域效果问询：该玩家是否抵御 source 领域（无量/伏魔在施加效果前调用）。
     * 实时计算（不依赖本类 tick 时序）：被包裹 + 带技巧 + 咒力足够 + 技巧能防该领域。
     * 落花之情防不住无量空处定身；弥虚/简易全防。
     */
    public static boolean isProtected(ServerPlayer player, BaseDomain source) {
        if (DomainRegistry.findEnemyDomain(player) != source) return false;
        // ⭐ 领域对抗中双方领域效果本就暂停 → 技巧不该（也不该被算作）生效
        if (source.isClashing()) return false;
        SkillType t = skillOn(player);
        if (t == null) return false;
        if (!canAfford(player)) return false;
        if (t == SkillType.LUOHUA && source instanceof WuLiangKongChuDomain) return false;
        return true;
    }

    /** 技巧激活期是否禁止使用术式（弥虚葛笼/简易领域禁；落花可用） */
    public static boolean blocksTechnique(ServerPlayer player) {
        BaseDomain enemy = DomainRegistry.findEnemyDomain(player);
        if (enemy == null) return false;
        // 对抗中领域效果暂停 → 技巧不启用，自然也不封锁术式
        if (enemy.isClashing()) return false;
        SkillType t = skillOn(player);
        if (t == null || !canAfford(player)) return false;
        return t == SkillType.MIXU_GELONG || t == SkillType.JIANYI_LINGYU;
    }

    // ==================== 每 tick：扣费 / 自我限制 / 提示 ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BaseDomain enemy = DomainRegistry.findEnemyDomain(p);
            // 对抗中：双方领域效果暂停 → 技巧不启用（也不扣费）
            if (enemy != null && enemy.isClashing()) enemy = null;
            SkillType t = enemy != null ? skillOn(p) : null;
            if (t == null || !canAfford(p)) {
                // 不在他人领域 / 没带技巧 / 咒力不足 → 解除技巧态
                if (SELF_STUN.remove(p.getUUID())) {
                    p.removeEffect(ModEffects.STUN.get());
                }
                SkillType last = LAST.remove(p.getUUID());
                if (last != null) {
                    p.displayClientMessage(Component.translatable(
                            "message.tinkersnewlife.skill.leave", display(last)), true);
                }
                continue;
            }
            // 扣费（无限免费）
            if (!CursePowerHelper.isCurseInfinite(p)) {
                CursePowerHelper.spendCurse(p, COST_PER_SECOND / 20.0);
            }
            // 各技巧的自我限制
            switch (t) {
                case MIXU_GELONG -> {
                    // 自我定身：弥虚葛笼完全不动（物品/攻击/界面由 StunHandler 拦截）
                    p.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 60, 0, false, false));
                    SELF_STUN.add(p.getUUID());
                }
                case LUOHUA -> {
                    // 可动但较慢
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, false));
                }
                case JIANYI_LINGYU -> {
                    // 无自我效果（术式禁用走 blocksTechnique）
                }
            }
            // 进出提示
            if (LAST.get(p.getUUID()) != t) {
                p.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.skill.enter", display(t)), true);
            }
            LAST.put(p.getUUID(), t);
        }
    }

    /** 技巧显示名（modifier 本地化键） */
    private static Component display(SkillType t) {
        return Component.translatable("modifier.tinkersnewlife."
                + skillModifier(t).getPath());
    }
}
