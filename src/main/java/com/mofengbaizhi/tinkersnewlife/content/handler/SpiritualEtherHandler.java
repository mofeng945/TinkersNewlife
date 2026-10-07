package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.EtherSpiritTrait;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ⭐ §1118y <b>灵性以太</b>的运行时（用户口径 ✓）。
 *
 * <h2>这一段做两件事 ✓</h2>
 * <ol>
 *   <li><b>击杀成长</b> ✓：每次击杀随机挑**一项该工具真正拥有的**属性增长 ✓
 *       —— 耐久 ✓ 伤害 ✓ 攻速 ✓ 挖掘速度 ✓，远程另加 精准度 ✓ 初速度 ✓ 拉弓速度 ✓
 *       （⚠ 近战工具不会"长"到精准度上 ✗：只从 {@code tool.getStats().get(...) > 0} 的项里挑 ✓）；
 *       单次长幅 ＝ <b>0.1%×等级 ~ 1.5%×等级</b> ✓（1 级 0.1%~1.5% ✓ 2 级 0.2%~3% ✓ …✓ 带浮点 ✓）；
 *       **每项上限 1000%** ✓（存 10.0 ✓）；值写进**工具自己的持久化数据** ✓ 并
 *       {@code tool.updateStack(stack)} **回写** ✓（⚠ 不回写等于白写 ✗ 仓库到处都这么写 ✓）。</li>
 *   <li><b>范围 ＋0.5/级</b> ✓：匠魂 {@code ToolStats} 没有"范围" ✗ ⇒ 用 Forge 的
 *       {@code forge:block_reach} / {@code forge:entity_reach} **瞬态属性修饰符** ✓
 *       （固定 UUID ✓ ⇒ 不会越叠越多 ✓ 照 {@code IndustrialPioneerHandler} 的成熟写法 ✓）；
 *       手里（主手/副手）拿着带该特性的工具才加 ✓ 松开即撤 ✓。</li>
 * </ol>
 *
 * <p>⚠ 攻击时"每级 1% 概率挂 5s 虚空之蚀/霜冻/迟缓，或脚下 3×3 冰之火" ✓ **还没做** ✗ —— 下一轮 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpiritualEtherHandler {

    private SpiritualEtherHandler() {
    }

    private static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, EtherSpiritTrait.ID));

    /** 范围修饰符的固定 UUID ✓（主手/副手各一个 ✓ 免得两者互相覆盖 ✓） */
    private static final UUID REACH_BLOCK_UUID = UUID.fromString("6f3a1c2e-1111-4a01-9a01-7e5e10c0a001");
    private static final UUID REACH_ENTITY_UUID = UUID.fromString("6f3a1c2e-1111-4a01-9a01-7e5e10c0a002");

    /** 每 10 tick 刷一次范围 ✓（属性不用每 tick 动 ✓） */
    private static final int REACH_INTERVAL = 10;

    // ------------------------------------------------------------------ 击杀成长

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (event.getEntity().level().isClientSide) {
                return;
            }
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
                return;
            }
            ToolStack tool = ToolHelper.getCombatTool(event.getSource(), player);
            if (tool == null) {
                return;
            }
            int level = ToolHelper.getActiveModifierLevel(tool, ID);
            if (level <= 0) {
                return;
            }
            // ⚠ 回写必须写回"**真正装了这把工具**的那个 ItemStack" ✗ —— 不能想当然用主手：
            // 远程击杀时 {@code getCombatTool} 给的可能是副手/别的格 ✓（主手写错 ⇒ 成长白写 ✗）。
            for (ItemStack stack : player.getHandSlots()) {
                if (stack.isEmpty()) {
                    continue;
                }
                try {
                    ToolStack held = ToolStack.from(stack);
                    if (ToolHelper.getActiveModifierLevel(held, ID) <= 0) {
                        continue;
                    }
                    if (growOne(held, level, player.getRandom())) {
                        // ⚠ 必须回写 ✗ 只 putFloat 不算数 ✓（ToolStack 是栈上的视图 ✓）
                        held.updateStack(stack);
                    }
                    return;
                } catch (Throwable ignored) {
                    // 非匠魂物品 ⇒ 跳过 ✓
                }
            }
        } catch (Throwable ignored) {
            // 任何意外都不该把击杀流程打崩 ✓
        }
    }

    /**
     * 随机给**一项**属性加上一点成长 ✓ 返回是否真的变了 ✓。
     *
     * @param tool  匠魂工具 ✓
     * @param level 特性等级 ✓（决定长幅区间 ✓）
     * @param rand  随机源 ✓
     */
    private static boolean growOne(ToolStack tool, int level, net.minecraft.util.RandomSource rand) {
        ToolDataNBT data = tool.getPersistentData();
        if (data == null) {
            return false;
        }
        // 只挑"这件工具真正拥有"的属性 ✓（近战不会长到精准度上 ✗）
        List<String> candidates = new ArrayList<>(7);
        if (tool.getStats().get(ToolStats.DURABILITY) > 0F && EtherSpiritTrait.canGrow(data, EtherSpiritTrait.KEYS[0])) {
            candidates.add(EtherSpiritTrait.KEYS[0]);
        }
        if (tool.getStats().get(ToolStats.ATTACK_DAMAGE) > 0F && EtherSpiritTrait.canGrow(data, EtherSpiritTrait.KEYS[1])) {
            candidates.add(EtherSpiritTrait.KEYS[1]);
        }
        if (tool.getStats().get(ToolStats.ATTACK_SPEED) > 0F && EtherSpiritTrait.canGrow(data, EtherSpiritTrait.KEYS[2])) {
            candidates.add(EtherSpiritTrait.KEYS[2]);
        }
        if (tool.getStats().get(ToolStats.MINING_SPEED) > 0F && EtherSpiritTrait.canGrow(data, EtherSpiritTrait.KEYS[3])) {
            candidates.add(EtherSpiritTrait.KEYS[3]);
        }
        if (tool.getStats().get(ToolStats.ACCURACY) > 0F && EtherSpiritTrait.canGrow(data, EtherSpiritTrait.KEYS[4])) {
            candidates.add(EtherSpiritTrait.KEYS[4]);
        }
        if (tool.getStats().get(ToolStats.VELOCITY) > 0F && EtherSpiritTrait.canGrow(data, EtherSpiritTrait.KEYS[5])) {
            candidates.add(EtherSpiritTrait.KEYS[5]);
        }
        if (tool.getStats().get(ToolStats.DRAW_SPEED) > 0F && EtherSpiritTrait.canGrow(data, EtherSpiritTrait.KEYS[6])) {
            candidates.add(EtherSpiritTrait.KEYS[6]);
        }
        if (candidates.isEmpty()) {
            return false;   // 七项全满 1000% ✓ 不再长 ✓
        }
        String key = candidates.get(rand.nextInt(candidates.size()));
        double span = EtherSpiritTrait.GROWTH_MAX - EtherSpiritTrait.GROWTH_MIN;
        double delta = (EtherSpiritTrait.GROWTH_MIN + rand.nextDouble() * span) * level;
        float old = EtherSpiritTrait.getGrowth(data, key);
        float value = (float) Math.min(EtherSpiritTrait.MAX_GROWTH, old + delta);
        if (value <= old) {
            return false;
        }
        data.putFloat(EtherSpiritTrait.keyId(key), value);
        return true;
    }

    // ------------------------------------------------------------------ 范围 ＋0.5/级

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }
        if (player.tickCount % REACH_INTERVAL != 0) {
            return;
        }
        try {
            int level = 0;
            for (ItemStack stack : player.getHandSlots()) {
                if (stack.isEmpty()) {
                    continue;
                }
                try {
                    ToolStack tool = ToolStack.from(stack);
                    int l = ToolHelper.getActiveModifierLevel(tool, ID);
                    if (l > level) {
                        level = l;
                    }
                } catch (Throwable ignored) {
                    // 非匠魂物品 ⇒ 跳过 ✓
                }
            }
            double bonus = level * EtherSpiritTrait.REACH_PER_LEVEL;
            applyReach(player, ForgeMod.BLOCK_REACH.get(), REACH_BLOCK_UUID, "tn_spiritual_ether_block_reach", bonus);
            applyReach(player, ForgeMod.ENTITY_REACH.get(), REACH_ENTITY_UUID, "tn_spiritual_ether_entity_reach", bonus);
        } catch (Throwable ignored) {
            // 同上 ✓ 不让 tick 崩 ✓
        }
    }

    /** 加/撤一个瞬态范围修饰符 ✓（固定 UUID ⇒ 幂等 ✓ 不会越叠越多 ✓） */
    private static void applyReach(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                   UUID uuid, String name, double bonus) {
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(uuid);
        if (bonus > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(uuid, name, bonus,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    // ------------------------------------------------------------------ ④ 下半：命中特效

    /**
     * 攻击命中 ✓：**每级 1%** 概率触发一件"小小效果" ✓（用户口径 ✓）：
     * <b>虚空之蚀</b> ✓ <b>霜冻</b> ✓ <b>迟缓</b> ✓ 各 5 秒 ✓，**或**在目标脚下点起 **3×3 的冰之火** ✓
     * （模仿诡厄巫法的幽灵 ✓）。
     *
     * <h2>⚠⚠ 两个必须守住的点（都有出处 ✓）</h2>
     * <ol>
     *   <li><b>虚空之蚀的 id</b> ＝ {@code goety:void_touched} ✓ —— 见本仓 {@code VoidGraceHandler:73}
     *       （虚空金属盔甲就是免疫它 ✓）；</li>
     *   <li>⚠⚠ <b>只许 {@code addEffect} ✗ 绝对不许 {@code removeEffect}</b> ✗ ——
     *       本仓 {@code VoidGraceHandler} 里明确记着：
     *       「**只要有人调 {@code removeEffect(虚空之蚀)}，哪怕身上根本没有它，诡厄也会响一声**」✗✗
     *       ⇒ 所以这里**只施加** ✓ 不解除 ✓（要解除也该由诡厄自己来 ✓）。</li>
     * </ol>
     *
     * <p>⭐ 「霜冻」是**我们自己的效果** ✓（用户口径 ✓「霜冻是我的效果」✓）——
     * {@code ModEffects.FROST} ✓ 注册 id {@code tinkersnewlife:frost} ✓（语言键「§b霜冻」✓），
     * 原本由"寒霜刺骨"({@code BitingFrostHandler}) 在用 ✓ ⇒ 这里直接施加同一个效果 ✓。
     *
     * <p>⚠ 冰之火 ＝ **`goety:ice_bouquet`** ✓（诡厄语言键 {@code entity.goety.ice_bouquet} ＝「冰之火」✓，
     * 幽灵聚晶的描述也写着「在目标脚下……召唤冰之火」✓）⇒ 用**注册表查 id** 取实体类型 ✓
     * （不 import 诡厄的类 ✓ 与 {@code VoidGraceHandler} 同口径 ✓）；没装诡厄就跳过 ✓。
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        try {
            net.minecraft.world.entity.LivingEntity target = event.getEntity();
            if (target.level().isClientSide) {
                return;
            }
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
                return;
            }
            ToolStack tool = ToolHelper.getCombatTool(event.getSource(), player);
            if (tool == null) {
                return;
            }
            int level = ToolHelper.getActiveModifierLevel(tool, ID);
            if (level <= 0) {
                return;
            }
            // 每级 1% ✓
            if (player.getRandom().nextInt(100) >= level) {
                return;
            }
            int roll = player.getRandom().nextInt(4);
            if (roll == 0) {
                applyGoetyEffect(target, "void_touched", EFFECT_SECONDS * 20, 0);
            } else if (roll == 1) {
                // 霜冻 ⇒ **我们自己的**效果 ✓（用户口径 ✓「霜冻是我的效果」✓）
                // ⚠ 反编译/源码核实 ✓：ModEffects.FROST ✓ 注册 id 为 tinkersnewlife:frost ✓（语言键「§b霜冻」✓）
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        com.mofengbaizhi.tinkersnewlife.content.ModEffects.FROST.get(),
                        EFFECT_SECONDS * 20, 0, false, true));
            } else if (roll == 2) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                        EFFECT_SECONDS * 20, 1, false, true));
            } else {
                spawnIceFire(target);
            }
        } catch (Throwable ignored) {
            // 命中特效出问题绝不能影响伤害结算 ✓
        }
    }

    /** 效果持续 ✓ 5 秒（用户口径 ✓） */
    private static final int EFFECT_SECONDS = 5;

    /** 给目标挂一个**诡厄**的效果 ✓（按 id 查注册表 ✓ 没装就跳过 ✓ ⚠ 只加不减 ✗） */
    private static void applyGoetyEffect(net.minecraft.world.entity.LivingEntity target,
                                         String path, int ticks, int amplifier) {
        if (!IntegrationLoader.isLoaded(IntegrationLoader.GOETY)) {
            return;
        }
        try {
            net.minecraft.world.effect.MobEffect effect =
                    net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(
                            new ResourceLocation("goety", path));
            if (effect != null) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, ticks, amplifier, false, true));
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }

    /** 在目标脚下点起 **3×3 的冰之火** ✓（用户口径 ✓ 模仿诡厄幽灵 ✓） */
    private static void spawnIceFire(net.minecraft.world.entity.LivingEntity target) {
        if (!IntegrationLoader.isLoaded(IntegrationLoader.GOETY)) {
            return;
        }
        try {
            net.minecraft.world.entity.EntityType<?> type =
                    net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                            new ResourceLocation("goety", "ice_bouquet"));
            if (type == null) {
                return;
            }
            net.minecraft.server.level.ServerLevel level =
                    (net.minecraft.server.level.ServerLevel) target.level();
            net.minecraft.core.BlockPos base = target.blockPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    net.minecraft.core.BlockPos pos = base.offset(dx, 0, dz);
                    if (!level.getBlockState(pos).isAir()) {
                        continue;   // 只点空气处 ✓ 免得把方块顶掉 ✗
                    }
                    net.minecraft.world.entity.Entity entity = type.create(level);
                    if (entity == null) {
                        continue;
                    }
                    entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                            level.random.nextFloat() * 360.0F, 0.0F);
                    level.addFreshEntity(entity);
                }
            }
        } catch (Throwable ignored) {
            // 同上 ✓
        }
    }
}
