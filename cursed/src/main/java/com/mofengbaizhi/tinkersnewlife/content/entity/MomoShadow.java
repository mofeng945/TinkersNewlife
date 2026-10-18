package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * TNL1282 ★★拉莱耶之影（R'lyeh's Shadow）：墨默的**分身仆从** ✓
 *
 * <p>用户口径：
 * <ul>
 *   <li>召唤 **3 个分身**：两个会主动追逐攻击，最后一个**倾向游走 ＋ 远程攻击**；</li>
 *   <li>外形 = 墨默本体**略带透明、颜色偏黑**的形象 ⇒ 见 {@code MomoShadowRenderer}；</li>
 *   <li>血量 = **墨默本体的一半**；**没有限伤、没有保护**；</li>
 *   <li>**不击杀分身无法对墨默造成伤害** ⇒ 见 {@link KtuluMomo#tnl$shadowShieldActive()}；</li>
 *   <li>分身会被标记为墨默的**仆从**（{@link KtuluMomo#TNL_MINION_TAG}）；</li>
 *   <li>近战/远程 AI **用户后续自己写**，本轮只把**游走**做出来 ✓。</li>
 * </ul>
 *
 * <h3>⚠⚠ TNL1284 为什么**不能**继承 {@link KtuluMomo}</h3>
 * 上一版是 `extends KtuluMomo`（为了复用模型），结果**分身和本体的血量内核串了**：
 * 本体随即出现"一击掉血、一击回满、只有寰宇剑能杀"的假死状态
 * （日志：`TNL1145 momo health resynced to 1677.7` 每次命中刷一次，
 * 而 `a.qF` 的权威值不再下降）。
 * ⇒ 现在分身是**普通的 {@link Monster}**，**完全不碰**本体那套血量内核/限伤/保护/死亡守卫 ✓，
 * 模型改用**原版玩家模型**（本体的渲染器本来就写着"复用商人墨默的模型（玩家模型）"，
 * 所以外形一致）✓。
 */
public class MomoShadow extends Monster {

    /** 分身的角色：两个近战、一个游走＋远程 */
    public enum Role {
        MELEE,
        WANDER
    }

    private Role role = Role.MELEE;
    /** 召唤它的墨默 UUID（"不杀分身不能打本体"靠这个判归属） */
    @Nullable
    private UUID ownerId = null;

    public MomoShadow(EntityType<? extends MomoShadow> type, Level level) {
        super(type, level);
        this.xpReward = 20;          // 分身不是 Boss ⇒ 别给 500
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)          // 真正数值由 tnl$bindTo 设成"本体一半"
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    /** ★不继承任何 Boss Goal（★近战/远程 AI 由用户后续补，游走由技能生成后挂） */
    @Override
    protected void registerGoals() {
    }

    // ==================== 角色 / 归属 ====================

    public Role tnl$role() {
        return this.role;
    }

    public boolean tnl$isWander() {
        return this.role == Role.WANDER;
    }

    @Nullable
    public UUID tnl$ownerId() {
        return this.ownerId;
    }

    /** 由召唤技能调用：记归属 ＋ 打上"墨默仆从"标记 ＋ 血量设为本体的一半 */
    public void tnl$bindTo(KtuluMomo owner, Role role) {
        this.role = role == null ? Role.MELEE : role;
        this.ownerId = owner == null ? null : owner.getUUID();
        if (owner != null) {
            float half = Math.max(1.0F, owner.getMaxHealth() * 0.5F);
            try {
                if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
                    this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(half);
                }
            } catch (Throwable ignored) {
            }
            this.setHealth(half);
        }
        if (!this.getTags().contains(KtuluMomo.TNL_MINION_TAG)) {
            this.addTag(KtuluMomo.TNL_MINION_TAG);
        }
    }

    /** ★用户后续写"远程 AI"时替换这里 ✓（现在只做游走：倾向随机漫步） */
    public void tnl$addWanderAi() {
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
    }

    // ==================== 存档 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TNLShadowRole", this.role.name());
        if (this.ownerId != null) {
            tag.putUUID("TNLShadowOwner", this.ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            this.role = Role.valueOf(tag.getString("TNLShadowRole"));
        } catch (Throwable ignored) {
            this.role = Role.MELEE;
        }
        if (tag.hasUUID("TNLShadowOwner")) {
            this.ownerId = tag.getUUID("TNLShadowOwner");
        }
    }

    /** ★读档/刷怪后把游走 AI 补回来（Goal 不存档） */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        if (this.role == Role.WANDER) {
            this.tnl$addWanderAi();
        }
        return data;
    }
}
