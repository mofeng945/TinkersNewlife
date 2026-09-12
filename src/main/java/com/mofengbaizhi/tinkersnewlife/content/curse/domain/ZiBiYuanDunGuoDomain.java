package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领域·自闭圆顿裹（无为转变之领域）
 * <ul>
 *   <li>领域内除施术者外每个目标被<b>施加一次无为转变</b>：
 *       目标若为生物 → 替换成所选形态的守护式神（认主、永久）；
 *       目标若为玩家 → 本体被强制变形成所选形态（60 秒限时、禁工具）。</li>
 *   <li>形态选择：施术者若已学会无为转变并在形态界面设定了转变对象 → 全体变成该形态；
 *       否则默认随机变成<b>僵尸或骷髅</b>。</li>
 *   <li><b>通用抵抗生效</b>：目标首次进入领域先按（亲和/100+1）×10 tick 抵抗（生物按血量），
 *       抵抗期内不会被转变。</li>
 *   <li><b>新阴流三技巧生效</b>：带技巧且咒力足够的玩家免疫转变（同胎藏遍野，仅伏诛赐死不可挡）。</li>
 *   <li>施术者自己的式神/咒灵/守卫不会被转变（同队豁免）。</li>
 * </ul>
 */
public class ZiBiYuanDunGuoDomain extends BaseDomain {
    /** config: 领域 modifier path（系数键） */
    @Override
    protected String configScaleId() { return "zi_bi_yuan_dun_guo"; }

    /** 转变尝试间隔：5 tick */
    private static final int TRANSFORM_INTERVAL_TICKS = 5;
    /** 默认形态：僵尸 / 骷髅 */
    private static final String FORM_ZOMBIE = "minecraft:zombie";
    private static final String FORM_SKELETON = "minecraft:skeleton";

    /** 本领域实例已转变过的实体（每个目标只转一次，避免反复强控） */
    private final Set<UUID> transformedOnce = ConcurrentHashMap.newKeySet();

    private ZiBiYuanDunGuoDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 60.0);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static ZiBiYuanDunGuoDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new ZiBiYuanDunGuoDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.zi_bi_yuan_dun_guo";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.ZI_BI_YUAN_DUN_GUO.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.zi_bi_yuan_dun_guo.open", radius), true);
        TinkersNewlife.LOGGER.info("[自闭圆顿裹] {} 展开：半径 {}",
                player.getName().getString(), radius);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        if (now % TRANSFORM_INTERVAL_TICKS != 0) return;
        ServerLevel level = player.serverLevel();
        double r = radius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r - 1.5, center.y - r - 1.5, center.z - r - 1.5,
                        center.x + r + 1.5, center.y + r + 1.5, center.z + r + 1.5))) {
            if (e.getUUID().equals(owner)) continue;
            if (e.position().distanceToSqr(center) > r * r) continue;
            if (transformedOnce.contains(e.getUUID())) continue;

            // ⭐ 通用抵抗生效：首次进入先抵抗一会，抵抗期内不转变
            if (registerResistAndCheck(e, now)) continue;

            if (e instanceof ServerPlayer sp) {
                // ⭐ 新阴流三技巧生效：带技巧且咒力足够 → 免疫转变
                if (com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler.isProtected(sp, this)) {
                    continue;
                }
            } else if (e instanceof Mob mob) {
                // 施术者同队（自己的咒灵/守卫）或自己"已调伏"的十影式神不转变
                //（未调伏式神是敌人，照转不误）
                if (CursedSpiritTechnique.isSpiritTeam(mob, player)) continue;
                if (e instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob sm
                        && sm.isTamed() && player.getUUID().equals(sm.getOwnerId())) {
                    continue;
                }
            } else {
                continue; // 非玩家/非生物（盔甲架等）不转变
            }

            // 形态：施术者设定过无为转变对象 → 用他的；否则默认僵尸/骷髅随机
            String form = formFor(player);
            if (form == null) continue;
            boolean ok = false;
            if (e instanceof ServerPlayer target) {
                ok = WuWeiHandler.forceTransformByDomain(target, form);
            } else if (e instanceof Mob mob) {
                ok = WuWeiHandler.transformMobByDomain(player, mob, form);
            }
            if (ok) {
                transformedOnce.add(e.getUUID());
                level.sendParticles(ParticleTypes.SNEEZE,
                        e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                        20, 0.6, 1.0, 0.6, 0.03);
            }
        }
    }

    @Override
    public void onClose(ServerPlayer player, String messageKey) {
        transformedOnce.clear();
        clearResist();
    }

    /** 形态 id：施术者学会无为转变且设定了转变对象 → 用其选中形态；否则默认僵尸/骷髅 */
    private static String formFor(ServerPlayer caster) {
        if (WuWeiHandler.hasTechnique(caster) && WuWeiHandler.hasSelection(caster)) {
            return WuWeiHandler.getSelected(caster);
        }
        return caster.getRandom().nextBoolean() ? FORM_ZOMBIE : FORM_SKELETON;
    }
}
