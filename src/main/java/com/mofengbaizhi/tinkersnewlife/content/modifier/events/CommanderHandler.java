package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;
import java.util.Random;

/**
 * 强化·统御者（占用能力槽）：服务端 {@link LivingHurtEvent}——玩家用带 {@code commander}
 * 强化的武器/工具攻击**非刌民**目标时，半径 8 格内的刌民（{@link AbstractIllager} 子类，
 * 含掠夺者/卫道士/唤魔者等）每个以 {@link #LOCK_CHANCE}（30%）概率优先把攻击目标切换为
 * 玩家正在攻击的那个目标。单级，可洗可剥。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommanderHandler {

    private static final ModifierId COMMANDER = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "commander"));

    /** 命令半径（格） */
    private static final double RADIUS = 8.0;
    /** 每个刌民单次攻击命中的改锁概率 */
    private static final double LOCK_CHANCE = 0.30;
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        // 只指挥攻击"非刌民"目标：玩家在打刌民（如反击）时不去指挥其他刌民同类相残
        if (target instanceof AbstractIllager) return;

        // 玩家使用的匠魂战斗工具（近战/弹射）必须带统御者
        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), player, COMMANDER);
        if (tool == null) return;
        if (tool.getModifierLevel(COMMANDER) <= 0) return;

        // 扫半径 8 格内的刌民
        AABB box = new AABB(player.blockPosition()).inflate(RADIUS);
        List<AbstractIllager> illagers = player.level().getEntitiesOfClass(AbstractIllager.class, box,
                illager -> illager != null && illager.isAlive() && illager != target);
        for (AbstractIllager illager : illagers) {
            if (RANDOM.nextDouble() < LOCK_CHANCE) {
                // 锁定玩家正在攻击的目标；若刌民原本在攻击玩家自身，优先让其转向目标
                illager.setTarget(target);
            }
        }
    }
}
