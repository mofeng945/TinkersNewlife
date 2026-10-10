package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;

/**
 * 匠魂工具安全操作辅助类
 */
public final class ToolHelper {

    private ToolHelper() {}

    /**
     * 安全地获取 ToolStack
     * <p>
     * 只有在物品是匠魂可修改工具（实现了 IModifiable）时，才会构造 ToolStack，
     * 否则返回 null，从而避免 "non-modifiable tool" 警告。
     *
     * @param stack 物品栈
     * @return ToolStack 实例，或 null（如果 stack 为空、不是匠魂工具、或构造失败）
     */
    @Nullable
    public static ToolStack getToolStack(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof IModifiable)) return null;
        return ToolStack.from(stack);
    }

    /**
     * 查询<b>未损坏</b>匠魂工具上某修饰符的等级 —— <b>本模组"损坏即失效"的统一闸口</b>。
     * <p>
     * 用户口径：<b>工具 / 盔甲一旦损坏（TCon 的 {@code tic_broken}），本模组的特性一律不生效</b>。
     * 各特性的 {@code has(...)} / {@code levelOf(...)} 查询一律改走这里（不再直接用
     * {@link #getToolStack(ItemStack)}），因此在<b>少数几处查询层</b>就统一把"损坏"挡掉，
     * 而不需要去动上百个效果类。
     *
     * @param tool 已解析的 ToolStack（可为 null）
     * @param id   修饰符 id
     * @return 等级；工具为 null / 已损坏 / 没有该修饰符时返回 <b>0</b>
     */
    public static int getActiveModifierLevel(@Nullable IToolStackView tool, ModifierId id) {
        if (tool == null || id == null) return 0;
        if (tool.isBroken()) return 0;
        return tool.getModifierLevel(id);
    }

    /**
     * 查询匠魂工具上某修饰符的等级 —— <b>无视破损（{@code tic_broken}）状态</b>。
     * <p>
     * ⚠️ <b>只给"专门用来修破损工具"的逻辑用</b>：当前唯一调用者是灵魂修复
     * {@code SoulRepairHandler}（破损工具必须先读到 soul_repair 等级才谈得上修它）。
     * 其余一切特性一律继续走 {@link #getActiveModifierLevel}（用户口径：破损即失效）。
     * <p>
     * 源码依据：TCon 的 {@code IToolContext.getModifierLevel(ModifierId)}
     * 就是 {@code getModifiers().getLevel(id)} —— 纯 NBT 读取、<b>完全不看 tic_broken</b>
     * （{@code TConstruct-1.20.1-3.11.2.166-sources.jar} →
     * {@code slimeknights/tconstruct/library/tools/nbt/IToolContext.java:96-98}）。
     * 即"破损即失效"是本模组在 {@link #getActiveModifierLevel} 里<b>自己加的闸口</b>，
     * 不是 TCon 的行为。
     *
     * @param tool 已解析的 ToolStack（可为 null）
     * @param id   修饰符 id
     * @return 等级；工具为 null / 没有该修饰符时返回 0（<b>破损不影响</b>）
     */
    public static int getModifierLevelIgnoringBroken(@Nullable IToolStackView tool, ModifierId id) {
        if (tool == null || id == null) return 0;
        return tool.getModifierLevel(id);
    }

    /**
     * 从攻击伤害源获取攻击者使用的匠魂战斗工具。
     * <p>
     * 统一处理近战（主手）与弹射物（弓/弩/标枪等，经 {@link ProjectileWeaponHelper}）两条路径，
     * 并附带 isBroken 与 stats 检查。用于各战斗 Handler 的 {@code onLivingAttack} 事件，
     * 消除重复的"取武器→校验"样板代码。
     *
     * @param source 伤害来源（LivingAttackEvent.getSource()）
     * @param player 攻击者玩家
     * @return 可用的 ToolStack，或 null（无武器 / 非匠魂工具 / 已损坏 / 无有效统计）
     */
    @Nullable
    public static ToolStack getCombatTool(DamageSource source, Player player) {
        if (source.getDirectEntity() instanceof Projectile projectile) {
            return getCombatTool(projectile, player);
        }
        // 悠悠球：发射后玩家手中已无该工具，从球实体携带的完整工具栈读取（特性才能正常触发）
        if (source.getDirectEntity() instanceof YoYoEntity yoYo) {
            return getValidTool(yoYo.getReturnStack());
        }
        /*
         * ⭐⭐§832／§833 用户实测：「**流血效果会自动触发一次手中武器的特性，导致出现超级大数字**」✗
         * （§833 更正：流血**是匠魂本体的** {@code tconstruct:bleeding} ✓）
         *
         * 根因是下面原来那一行**无条件兜底** ✗：
         *   `return getValidTool(player.getMainHandItem());`
         * ⇒ 任何走到这里的伤害都被当成"主手武器的命中" ✗ ⇒ 本模组所有挂在命中上的特性
         *   （悚怖钢的衰弱/失明/凋零+刀光、龙钢三系、破法、人屠、真穿、兵士佩刀分段…）
         *   被**每一次二次伤害**各触发一次 ✗ ⇒ 数字被反复放大 ＝ 用户看到的"超级大数字" ✓。
         *
         * ── §833 关键更正（血的事实 ✓ 从匠魂 jar 反编译实读 ✓）────────────────────────
         * 匠魂的流血**不是**"没有来源实体"的效果伤害 ✗ —— 它**把玩家挂在伤害源上** ✓：
         *   {@code BleedingEffect.applyEffectTick}: {@code Entity credit = entity.getKillCredit();}
         *   ⇒ {@code TinkerDamageTypes.source(ra, BLEEDING, credit)}
         *   ⇒ {@code source(ra,key,e)} 内部就是 {@code source(ra,key,e,e)}
         *   ⇒ {@code new DamageSource(holder, e, e)}
         * ⇒ 流血的 {@code getEntity()} **和** {@code getDirectEntity()} **都是"最后打它的那个玩家"** ✗✗
         * ⇒ 所以**只查 directEntity 是不够的** ✗（§832 那版就漏了这一点 ✓ 这次补上 ✓）。
         *   ⚠ 匠魂所有"二次伤害"都是这个形状：{@code tconstruct:bleeding}／{@code piercing}／{@code spiny}／
         *   {@code entangled}／{@code shock}／{@code self_destruct}／{@code knightmetal}／
         *   {@code fluid_*_melee}／{@code smeltery_*}／{@code explosion_melee}… ✓
         *   （对照证据 ✓：匠魂自己的 {@code TinkerTags.DamageTypes.MODIFIER_WHITELIST} 只收
         *    {@code minecraft:mob_attack} / {@code mob_attack_no_aggro} ＋暮色森林几个 ✗ 一个 {@code tconstruct:} 都没有 ✓
         *    ⇒ **匠魂自己也不把 {@code tconstruct:} 命名空间的伤害当成"可触发工具特性的攻击"** ✓ 同口径 ✓）
         *
         * ── 两道闸门（合起来＝"这次真的是玩家用主手武器本体打出来的吗" ✓）──────────────
         * ① {@code getDirectEntity() != player} ⇒ 不是玩家本体直接造成 ⇒ 不算 ✗
         *    （挡掉：无来源实体的中毒/凋零/饥饿、召唤物爆炸、别模组的间接伤害… ✓
         *      它们原本会被算成"玩家主手命中" ✗）
         * ② {@link #isTinkersSecondaryDamage} ⇒ 匠魂本体自己的二次伤害（含流血）⇒ 不算 ✗
         *    ⚠ 只对**近战这一条路**生效 ✗：弹射物（弓/弩/标枪/匠魂投掷工具 ✓）在 §94 就已经
         *      转给 {@link #getCombatTool(Projectile, Player)} 了 ✓ 那一层**不做**此排除 ✓
         *      （真·弹射命中确实该吃武器特性 ✓）。
         * ⚠ 口径变化（如实说明 ✓）：以玩家为来源但**没有直接实体**的伤害、以及匠魂的二次伤害，
         *   从此**不会**再触发"武器上的"命中特性 ✓（本模组术式走自己的 {@code applyCurseCoreTraits} ✓ 不受影响 ✓；
         *   唐横刀分段的补刀**复用同一次伤害源** ✓ 是 {@code player_attack} ✓ 照旧触发 ✓）。
         */
        if (source.getDirectEntity() != player) {
            return null;
        }
        if (isTinkersSecondaryDamage(source)) {
            return null;
        }
        return getValidTool(player.getMainHandItem());
    }

    /**
     * ⭐⭐§833：这次伤害是不是<b>匠魂本体的"二次伤害"</b>（流血 / 穿刺 / 尖刺 / 缠绕 / 电击 / 流体效果 / 熔炉…）。
     *
     * <p>判法用<b>伤害类型命名空间</b> ✓：匠魂自己那一整套伤害类型**全部**放在 {@code tconstruct:} 下
     * （{@code bleeding}／{@code piercing}／{@code spiny}／{@code entangled}／{@code shock}／{@code self_destruct}／
     * {@code knightmetal}／{@code fluid_*_melee}／{@code smeltery_*}／{@code explosion_melee}… ✓），
     * 而它**真正的本体近战**用的是原版的 {@code minecraft:player_attack} ✓（不是自定义类型 ✓）。
     * ⇒ 凡 {@code tconstruct:} 命名空间 ⇒ 一律是"攻击带出来的二次效果" ⇒ 不算武器命中 ✓。
     *
     * <p>这么做还有个好处：匠魂以后**新增**二次伤害类型，这里**自动**跟着排掉 ✓ 不用维护名单 ✓。
     * ⚠ 不依赖任何匠魂类 ✗（只看命名空间字符串 ✓）⇒ 即使匠魂那边类名/字段变了也不会崩 ✓。
     *
     * @param source 伤害源
     * @return true ＝ 匠魂二次伤害，不该触发武器命中特性
     */
    public static boolean isTinkersSecondaryDamage(DamageSource source) {
        ResourceLocation id = source.typeHolder().unwrapKey()
                .map(key -> key.location()).orElse(null);
        return id != null && "tconstruct".equals(id.getNamespace());
    }

    /**
     * 从弹射物获取攻击者使用的匠魂战斗工具。
     * <p>
     * 用于各战斗 Handler 的 {@code onProjectileImpact} 事件。
     *
     * @param projectile 弹射物实体
     * @param player     攻击者玩家
     * @return 可用的 ToolStack，或 null
     */
    @Nullable
    public static ToolStack getCombatTool(Projectile projectile, Player player) {
        ItemStack weapon = ProjectileWeaponHelper.getProjectileWeapon(projectile, player);
        if (weapon.isEmpty()) {
            weapon = player.getMainHandItem();
        }
        return getValidTool(weapon);
    }

    /**
     * 获取有效的匠魂工具（安全获取 + 未损坏 + 有有效统计）。
     */
    @Nullable
    private static ToolStack getValidTool(ItemStack weapon) {
        ToolStack tool = getToolStack(weapon);
        if (tool == null) return null;
        if (tool.isBroken()) return null;
        if (tool.getStats().getContainedStats().isEmpty()) return null;
        return tool;
    }

    // ============================================================
    //  咒力核心兜底（术式/领域攻击时主手无武器，材料特性位于核心上）
    // ============================================================

    /**
     * 解析攻击者携带指定修饰符的匠魂战斗工具——<b>玩家与怪物通用</b>。
     * <ul>
     *   <li>玩家：近战/弹射双路径 + 无武器时咒力核心兜底</li>
     *   <li>非玩家（怪物/随从等）：仅检查主手物品是否为匠魂工具且含任一修饰符
     *       （怪物没有弹射武器与咒力核心，只查主手近战）</li>
     * </ul>
     * 供各攻击词条 Handler 把 {@code instanceof Player} 判定放宽为 {@code instanceof LivingEntity}
     * 后调用，使持匠魂武器的怪物也能触发命中效果。
     *
     * @param source   伤害来源（LivingAttackEvent/LivingDamageEvent 的 getSource()；非玩家时仅用主手，可为 null）
     * @param attacker 攻击者实体（玩家或怪物，须在服务端调用）
     * @param ids      需要匹配的修饰符（命中其一即可）
     * @return 携带任一指定修饰符的 ToolStack；主手与核心均无时返回主手解析结果（可能为 null）
     */
    @SafeVarargs
    @Nullable
    public static ToolStack getCombatToolWith(DamageSource source, LivingEntity attacker, ModifierId... ids) {
        if (attacker == null) return null;
        if (attacker instanceof Player player) {
            return getToolWithModifier(player, getCombatTool(source, player), ids);
        }
        // 非玩家：只查主手（怪物近战武器）
        return getValidToolWithModifier(attacker.getMainHandItem(), ids);
    }

    /**
     * 解析攻击者携带指定修饰符的匠魂工具（弹射物路径）——<b>玩家与怪物通用</b>。
     * 玩家走弹射武器解析 + 咒力核心兜底；非玩家（理论上怪物不用匠魂远程）回退查主手。
     *
     * @param projectile 弹射物实体
     * @param attacker   攻击者实体（玩家或怪物，须在服务端调用）
     * @param ids        需要匹配的修饰符（命中其一即可）
     * @return 携带任一指定修饰符的 ToolStack；无则 null
     */
    @SafeVarargs
    @Nullable
    public static ToolStack getCombatToolWith(Projectile projectile, LivingEntity attacker, ModifierId... ids) {
        if (attacker == null) return null;
        if (attacker instanceof Player player) {
            return getToolWithModifier(player, getCombatTool(projectile, player), ids);
        }
        return getValidToolWithModifier(attacker.getMainHandItem(), ids);
    }

    /**
     * 解析玩家携带指定修饰符的匠魂工具：给定的主工具携带则直接使用，
     * 否则兜底取佩戴的咒力核心；两者均无时返回主工具（可能为 null）。
     *
     * @param player  玩家（须在服务端调用）
     * @param primary 主手/弹射等路径解析出的工具（可为 null）
     * @param ids     需要匹配的修饰符（命中其一即可）
     */
    @SafeVarargs
    @Nullable
    public static ToolStack getToolWithModifier(Player player, ToolStack primary, ModifierId... ids) {
        if (hasAny(primary, ids)) return primary;
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return primary;
        // ⭐ 损坏的核心不能兜底（与 getValidTool 同一口径：损坏即失效）
        ToolStack coreTool = getValidTool(core);
        if (hasAny(coreTool, ids)) return coreTool;
        return primary;
    }

    /** 工具是否携带任一指定修饰符（等级 &gt; 0） */
    private static boolean hasAny(ToolStack tool, ModifierId[] ids) {
        if (tool == null || ids.length == 0) return false;
        for (ModifierId id : ids) {
            if (ToolHelper.getActiveModifierLevel(tool, id) > 0) return true;
        }
        return false;
    }

    /** 物品是有效匠魂工具且含任一指定修饰符才返回，否则 null */
    @SafeVarargs
    @Nullable
    private static ToolStack getValidToolWithModifier(ItemStack stack, ModifierId... ids) {
        ToolStack tool = getValidTool(stack);
        if (hasAny(tool, ids)) return tool;
        return null;
    }
}
