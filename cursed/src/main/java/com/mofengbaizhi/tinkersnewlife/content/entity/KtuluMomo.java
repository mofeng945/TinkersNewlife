package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * \u514b\u56fe\u9732\u00b7\u58a8\u9ed8\uff08Ktulu Momo\uff09\u2014\u2014 \u2605\u661f\u8fb0\u9b54\u5973\u00b7\u58a8\u9ed8\u00b7\u514b\u56fe\u9732 \u2713
 *
 * <pre>
 *   \u79f0\u53f7\uff1a\u514b\u56fe\u9732\u00b7\u58a8\u9ed8\uff0c\u4f17\u661f\u4e4b\u795e\u5973\uff0c\u53d7\u4e07\u661f\u52a0\u5195\u8005\uff0c\u661f\u6e0a\u7684\u5723\u5973
 *   \u82f1\u6587\uff1aKtulu Momo / Our Lady of the Myriad Stars /
 *         She Who Is Crowned by the Countless Stars / Holy Maiden of the Starry Abyss
 *   \u8840\u6761\uff1a\u661f\u8fb0\u9b54\u5973\u00b7\u58a8\u9ed8\u00b7\u514b\u56fe\u9732  Momo Ktulu, Astral Priestess
 * </pre>
 *
 * <p>\u2605\u76ee\u524d**\u590d\u7528\u5546\u4eba\u58a8\u9ed8\u7684\u6a21\u578b\u4e0e\u76ae\u80a4**\uff08\u2605\u63a5\u7740\u7ee7\u627f AI \u2713\uff09\uff0c
 * \u76ae\u80a4\u53e6\u5b58\u4e3a**\u9ed1\u767d\u7070\u5ea6\u7248**\uff08\u2605\u4e0d\u52a8\u7528\u6237\u539f\u56fe \u2713\uff09\uff1b
 * \u8840\u6761\u7528**\u9ed8\u8ba4\u6750\u8d28**\uff0c\u989c\u8272\u7528\u767d\uff08\u2605\u9ed1\u767d\u7cfb \u2713\uff09\u3002
 */
public class KtuluMomo extends MomoMerchant {

    /** \u2605\u8840\u6761\uff08\u2605\u670d\u52a1\u7aef BossEvent \u2713\uff09 */
    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.tinkersnewlife.ktulu_momo.bossbar"),
            BossEvent.BossBarColor.WHITE,          // \u2605\u9ed1\u767d\u7cfb\uff1a\u767d \u2713
            BossEvent.BossBarOverlay.PROGRESS);

    @SuppressWarnings("unchecked")
    public KtuluMomo(EntityType<? extends KtuluMomo> type, Level level) {
        super((EntityType<? extends MomoMerchant>) (EntityType<?>) type, level);
        this.xpReward = 500;
    }

    /** \u2605\u5c5e\u6027\uff1a\u9ad8\u8840\u91cf \u002b \u9ad8\u4f24\u5bb3 \u002b \u514d\u51fb\u9000 \u2713 */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 800.0D)
                .add(Attributes.ARMOR, 15.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0D)
                .add(Attributes.ATTACK_DAMAGE, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public boolean canChangeDimensions() {
        return false;   // \u2605Boss \u4e0d\u8ddf\u7740\u7a7f\u7ef4\u5ea6 \u2713
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }
}
