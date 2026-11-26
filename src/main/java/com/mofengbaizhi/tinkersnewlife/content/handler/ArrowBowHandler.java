package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 箭矢发射弓绑定处理器。
 * <p>
 * 在箭矢加入世界时（发射瞬间），把发射它的匠魂弓/弩写入箭矢的持久数据。
 * 这样命中目标时（{@link ProjectileWeaponHelper#getProjectileWeapon}）能读到发射武器，
 * 即使玩家在箭飞行途中已切换手中武器 —— 弓上的战斗特性（龙钢三系、悚怖、注魔等）
 * 就能随箭矢命中正常触发。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArrowBowHandler {

    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        // 只在服务端绑定（持久数据不随实体同步，客户端写入无意义）
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player)) return;

        // 从主手/副手找发射用的匠魂弓/弩
        ItemStack bow = findFiringBow(player);
        if (bow.isEmpty()) return;

        // 写入箭矢持久数据：发射它的弓（完整 ItemStack，含匠魂 NBT）
        arrow.getPersistentData().put(ProjectileWeaponHelper.KEY_FIRING_BOW, bow.save(new CompoundTag()));
    }

    /** 从玩家主手/副手查找发射用的匠魂弓/弩（可修改工具且未损坏） */
    private static ItemStack findFiringBow(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (isFiringBow(mainHand)) return mainHand;

        ItemStack offHand = player.getOffhandItem();
        if (isFiringBow(offHand)) return offHand;

        return ItemStack.EMPTY;
    }

    private static boolean isFiringBow(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && !tool.isBroken();
    }
}
