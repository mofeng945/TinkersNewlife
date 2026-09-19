package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.armor.ModifyDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 巫师套装特性·<b>魔力护盾</b>（<b>无等级</b> ✓ 按件叠加 ✓）—— 内建在四件巫师套上
 * （走工具定义的 {@code tconstruct:traits} 模块 ✓ 与材料无关 ✓）。
 *
 * <p>每 1 件：<b>魔法伤害 −10%</b>（多件链乘 ✓）、<b>生命上限 +2</b>（可叠加 ✓）；
 * 只要穿着<b>任意一件</b>：自身身上<b>一切增益与减益的持续时间减半</b>（<b>不可叠加</b> ✓ 用户口径 ✓）。
 *
 * <h2>只削魔法（用户最终口径 ✓）</h2>
 * 判定直接复用「导魔」的 {@link MagicConductionModifier#isMagicDamage} ✓，
 * 也就是只削这三类：
 * <ol>
 *   <li><b>原版魔法</b>：{@code witch_resistant_to} 标签 = {@code magic}（药水）、
 *       {@code indirect_magic}（唤魔者尖牙）、{@code sonic_boom}（音爆）、{@code thorns}（荆棘反伤）✓；</li>
 *   <li><b>铁魔法</b>九学派法术：其伤害类型消息 id 全是 {@code *_magic}（fire_magic / eldritch_magic …✓）
 *       ⇒ 被"消息 id 含 magic"这条兜住 ✓（⚠ 它登记的 {@code neoforge:is_magic} 标签在 Forge 1.20.1 下
 *       不生效 ✗ 所以不能只靠标签 ✓）；</li>
 *   <li><b>诡厄巫法</b>法术：{@code forge:is_magic} 标签里的 8 个类型 ✓ + {@code goety.*} 命名空间兜底 ✓。</li>
 * </ol>
 * 其余<b>一律不减</b>：物理 ✓ 虚空 ✓ 穿透 ✓ 火焰 / 岩浆 / 闪电（原版）✓ 坠落 ✓ 爆炸 ✓
 * 凋零 / 中毒 / 龙息 ✓ 溺水 / 冰冻 ✓ 饥饿 ✓ 等 ✓。
 *
 * <h2>仍然保留的两条"一票否决"（覆盖兜底判定的误伤 ✓）</h2>
 * {@link #isPhysicalDamage}（{@code tinkersnewlife:is_physical} + {@code goety:physical} 标签 +
 * 近战兜底 ✓）与 {@link #ignoresShield}（虚空 / 穿透 / 规则级 ✓）会在魔法判定<b>之前</b>先拦一道 ✓
 * —— 因为魔法判定里有 {@code goety.*} 这种<b>按命名空间</b>的粗兜底 ✗，
 * 会把诡厄的物理类（如 {@code goety:summon} ✓ 它自带在 {@code goety:physical} 里 ✓）误判成魔法 ✗。
 *
 * <p>减伤走 TCon 的护甲钩子 {@link ModifierHooks#MODIFY_DAMAGE} ⇒ 与「导魔」
 * {@link MagicConductionModifier} 同一套，多件<b>逐件链乘</b>（4 件 = 1−0.9⁴ ≈ 34.4% ✓）✓。
 *
 * <p>属性的维持（生命上限）在 {@code content.modifier.events.ManaShieldHandler}；
 * 减时长的注入点在 {@code mixin.ManaShieldEffectMixin}（{@code MobEffectInstance.duration}
 * 是 private 且无 setter ✗）。
 */
public class ManaShieldTrait extends Modifier implements TooltipModifierHook, ModifyDamageModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "mana_shield"));

    /** 每件的非物理减伤（逐件链乘 ✓） */
    public static final double REDUCTION_PER_PIECE = 0.10D;

    /** 每件的生命上限（用户口径：+2 ✓ 可叠加 ✓） */
    public static final int HEALTH_PER_PIECE = 2;

    /** 增益/减益时长倍率（不可叠加 ✓ 只看"有没有穿" ✓） */
    public static final double EFFECT_DURATION_MULTIPLIER = 0.5D;

    /** 物理伤害标签（我们自己的 ✓ 可在数据包里增删 ✓） */
    private static final TagKey<DamageType> TN_PHYSICAL =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "is_physical"));

    /** 诡厄巫法自带的物理标签（软依赖 ✓ 没装就是空标签 ✓ 不会崩 ✓） */
    private static final TagKey<DamageType> GOETY_PHYSICAL =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("goety", "physical"));

    /**
     * <b>护盾挡不住</b>的伤害标签（虚空 / 穿透 / 规则级 ✓ 用户口径 ✓ 可数据包改 ✓）：
     * 原版 {@code out_of_world}（掉出世界 = 虚空 ✓）与 {@code generic_kill}（/kill ✓）、
     * 本模组 {@code true_pierce}（穿透 / 真伤 ✓）、诡厄巫法 {@code voided}、
     * 枪械的 {@code bullet_void*}、莱特兰扩充的 {@code void_eye} ✓。
     */
    private static final TagKey<DamageType> TN_IGNORES_SHIELD =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "ignores_mana_shield"));

    /**
     * 已经被我们减半过的实例（判"这个时长是不是减半后的"✓ 防"重加刷新"时一路塌到 1 tick ✗）。
     *
     * <p>⚠ 必须按<b>对象身份</b>判 ✗ 不能用 {@code HashSet/WeakHashMap}：
     * {@code MobEffectInstance} 重写了 {@code hashCode}，而且<b>把 duration 算进去了</b> ✗ ⇒
     * 存进去之后时长每 tick 都在变 ⇒ 哈希槽也变 ⇒ 再也查不回来 ✗（守卫等于没写 ✓）。
     * 这里用"弱引用 + 定长环"：身份比较 ✓ 不阻止回收 ✓ 不会无界增长 ✓。
     */
    private static final java.util.Deque<java.lang.ref.WeakReference<MobEffectInstance>> HALVED =
            new java.util.ArrayDeque<>();

    /** 这个实例是不是我们刚减半过的那一个（按引用身份 ✓） */
    private static synchronized boolean alreadyHalved(MobEffectInstance instance) {
        java.util.Iterator<java.lang.ref.WeakReference<MobEffectInstance>> it = HALVED.iterator();
        while (it.hasNext()) {
            MobEffectInstance value = it.next().get();
            if (value == null) {
                it.remove();                       // 已被回收 ⇒ 顺手清掉 ✓
            } else if (value == instance) {
                return true;
            }
        }
        return false;
    }

    /** 记下"这个实例已经减半过了"（弱引用 ✓ 环长封顶 ✓） */
    private static synchronized void markHalved(MobEffectInstance instance) {
        HALVED.addLast(new java.lang.ref.WeakReference<>(instance));
        while (HALVED.size() > 512) HALVED.removeFirst();
    }

    /** 效果固定 ⇒ 显示名不带等级 ✓ */
    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.MODIFY_DAMAGE, ModifierHooks.TOOLTIP);
    }

    // ============================================================
    //  减伤（TCon 护甲钩子 ✓ 每件各调一次 ⇒ 自然链乘 ✓）
    // ============================================================

    @Override
    public float modifyDamageTaken(IToolStackView tool, ModifierEntry modifier, EquipmentContext context,
                                   EquipmentSlot slotType, DamageSource source, float amount,
                                   boolean isDirectDamage) {
        if (amount <= 0.0F) return amount;
        // ⭐ 用户口径（最新）：护盾**只削魔法** —— 原版魔法 ✓ 铁魔法九学派法术 ✓ 诡厄巫法法术 ✓
        //   其余一律不减（物理 / 虚空 / 穿透 / 火焰 / 坠落 / 爆炸 / 凋零 / 中毒 …✓）
        if (isPhysicalDamage(source)) return amount;              // 物理 ⇒ 不削 ✓
        if (ignoresShield(source)) return amount;                 // 虚空 / 穿透 / 规则级 ⇒ 不削 ✓
        if (!MagicConductionModifier.isMagicDamage(source)) return amount;   // 不是魔法 ⇒ 不削 ✓
        return amount * (float) (1.0D - REDUCTION_PER_PIECE);
    }

    /**
     * 这次伤害是不是<b>护盾一律挡不住</b>的那几类（用户口径：物理 ✓ 虚空 ✓ 穿透 ✓）——
     * 物理由 {@link #isPhysicalDamage} 单独判，这里管**虚空 / 穿透 / 规则级** ✓。
     *
     * <p>判定顺序：
     * <ol>
     *   <li>我们的 {@code tinkersnewlife:ignores_mana_shield} 标签（<b>可数据包改</b> ✓）；</li>
     *   <li>{@link DamageTypeTags#BYPASSES_INVULNERABILITY} —— 原版 {@code out_of_world}（虚空 ✓）
     *       与 {@code generic_kill}（/kill ✓）本身就在这个标签里 ✓，本模组 {@code true_pierce}
     *       也往它里面贴了（见 {@code data/minecraft/tags/damage_type/bypasses_invulnerability.json} ✓）⇒
     *       「原版虚空」与「穿透」到这里就已经被挡住了 ✓；</li>
     *   <li>{@code TruePierce.isTruePierce} —— 本模组真伤判定（含诡厄巫法那支真伤源 ✓）；</li>
     *   <li>虚空兜底：伤害类型 id 的 <b>path</b> 是 {@code void*} / {@code *_void} / {@code *_void_*}
     *       ⇒ 覆盖"别的 mod 的虚空伤害但没打标签"的情况 ✓（{@code goety:voided}、
     *       {@code tacz:bullet_void}、{@code l2complements:void_eye} …✓）。</li>
     * </ol>
     */
    public static boolean ignoresShield(@Nullable DamageSource source) {
        if (source == null) return false;
        try {
            if (source.is(TN_IGNORES_SHIELD)) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (com.mofengbaizhi.tinkersnewlife.util.TruePierce.isTruePierce(source)) return true;
        } catch (Throwable ignored) {
        }
        try {
            String id = source.getMsgId();
            if (id != null && !id.isEmpty()) {
                String s = id.toLowerCase();
                int colon = s.indexOf(':');
                String path = colon < 0 ? s : s.substring(colon + 1);
                if (path.startsWith("void") || path.contains("_void")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 这次伤害算"物理"吗（判定顺序见类注释 ✓） */
    public static boolean isPhysicalDamage(@Nullable DamageSource source) {
        if (source == null) return false;
        try {
            if (source.is(TN_PHYSICAL)) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (source.is(GOETY_PHYSICAL)) return true;
        } catch (Throwable ignored) {
        }
        // 兜底：近战/接触（直接来源是生物 ✓）且不是魔法 ⇒ 物理 ✓（覆盖没打标签的 mod 近战 ✓）
        try {
            return source.getDirectEntity() instanceof LivingEntity
                    && !MagicConductionModifier.isMagicDamage(source);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ============================================================
    //  时长减半（由 mixin 在 LivingEntity#addEffect 里调用 ✓ 只在服务端 ✓）
    // ============================================================

    /**
     * 如果 {@code entity} 身上穿着带魔力护盾的甲，就把这次要施加的效果<b>时长减半</b> ✓。
     *
     * <p>⚠ 为什么返回<b>新实例</b>而不是改原实例：{@code MobEffectInstance.duration} 是
     * <b>private 且没有 setter</b> ✗（1.20.1 源码确认 ✓），而 mixin 的 {@code @ModifyVariable}
     * 可以<b>换掉传入的对象</b> ✓ ⇒ 用公开的 6 参构造复制一份、只改时长 ✓（原实例不动 ⇒
     * 调用方若复用同一个实例反复 addEffect 也<b>不会叠加减半</b> ✓）。
     * 代价：{@code hiddenEffect} 与 {@code factorData} 这两项<b>没有公开 getter</b> ✗ 会丢
     * （Vanilla 里极少用 ✓ 只影响"隐藏效果"与时长混合曲线 ✓）。
     */
    public static MobEffectInstance halveDurationIfShielded(LivingEntity entity, MobEffectInstance instance) {
        if (entity == null || instance == null) return instance;
        // ⚠ 只减一次：客户端收到的已经是减半后的值 ✗ 在客户端再减就成 1/4 ✗
        if (entity.level().isClientSide) return instance;
        if (instance.isInfiniteDuration()) return instance;     // 无限时长不动 ✓（还是无限 ✓）
        if (countWorn(entity) <= 0) return instance;            // 没穿 ⇒ 原样 ✓
        if (alreadyHalved(instance)) return instance;           // 已经减半过 ⇒ 不再减 ✓
        int duration = instance.getDuration();
        if (duration <= 1) return instance;
        int halved = Math.max(1, (int) Math.round(duration * EFFECT_DURATION_MULTIPLIER));
        MobEffectInstance out = new MobEffectInstance(instance.getEffect(), halved, instance.getAmplifier(),
                instance.isAmbient(), instance.isVisible(), instance.showIcon());
        markHalved(out);
        return out;
    }

    // ============================================================
    //  提示（动态 ✓ 只一行 ✓ 用户要求"别一大串静态描述" ✓）
    // ============================================================

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        int pieces = countWorn(player);
        // 链乘后的**实际**总减伤 ✓（4 件 ≈ 34% 而不是 40% ✓ 免得提示和手感对不上 ✗）
        int percent = (int) Math.round((1.0D - Math.pow(1.0D - REDUCTION_PER_PIECE, pieces)) * 100.0D);
        tooltip.add(Component.translatable("modifier.tinkersnewlife.mana_shield.tip",
                pieces, percent, pieces * HEALTH_PER_PIECE));
    }

    // ============================================================
    //  查询工具（结算器用 ✓ 与「刻印」「魔力涌动」同款 ✓）
    // ============================================================

    /** 该物品是否带魔力护盾 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ID) > 0;
    }

    /** 身上穿了几件带魔力护盾的盔甲（0~4 ✓） */
    public static int countWorn(@Nullable LivingEntity entity) {
        if (entity == null) return 0;
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (has(entity.getItemBySlot(slot))) n++;
        }
        return n;
    }
}
