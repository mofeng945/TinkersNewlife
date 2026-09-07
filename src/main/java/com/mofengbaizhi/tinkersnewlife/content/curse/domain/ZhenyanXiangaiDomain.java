package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;
import java.util.UUID;

/**
 * 领域·真赝相爱
 * <p>
 * 领域展开后，施术者进入"借术式"模式：本存档内所有帕秋莉已解锁的术式
 * 都成为可切换/可释放的对象（切换键在解锁术式中轮换）。此效果<b>仅在领域内生效</b>：
 * 领域关闭（手动/咒力耗尽/被破坏/离开后）自动恢复进入领域前的选中术式。
 * 领域本身维持通用困锁墙与咒力消耗。
 */
public class ZhenyanXiangaiDomain extends BaseDomain {
    /** config: 领域 modifier path（系数键） */
    @Override
    protected String configScaleId() { return "zhenyan_xiangai"; }

    private ZhenyanXiangaiDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 25.0);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static ZhenyanXiangaiDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new ZhenyanXiangaiDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.zhenyan_xiangai";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.ZHENYAN_XIANGAI.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        // 进入借术式模式
        TechniqueHandler.enableBorrow(player);
        int unlocked = TechniqueHandler.unlockedTechniques(player).size();
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.zhenyan.open", unlocked), true);
        TinkersNewlife.LOGGER.info("[真赝相爱] {} 展开：可切换 {} 个已解锁术式",
                player.getName().getString(), unlocked);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        // 无额外伤害/控制——领域本身以困锁墙与消耗为代价，效果即"借术式"
        if (player.tickCount % 20 == 0) {
            ServerLevel level = player.serverLevel();
            if (level == null) return;
            level.sendParticles(ParticleTypes.PORTAL,
                    player.getX(), player.getY() + 1.2, player.getZ(),
                    4, 0.4, 0.3, 0.4, 0.0);
        }
    }

    @Override
    public void onClose(ServerPlayer player, String messageKey) {
        // 退出借术式模式：恢复进入领域前的选中术式
        if (player != null && player.isAlive()) {
            TechniqueHandler.disableBorrow(player);
        }
    }
}
