package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * 让诡厄巫法的仆从能使用匠魂武器（右键装备，原生交互体验）。
 * <p>
 * 诡厄原生的给仆从装备逻辑只认 {@code SwordItem}/{@code AxeItem}/{@code TridentItem}，
 * 匠魂工具（{@code ModifiableItem} 体系）不是这些子类，玩家右键仆从时会被诡厄直接跳过。
 * 本 handler 在 {@code PlayerInteractEvent.EntityInteract}（早于诡厄 mobInteract）拦截：
 * 玩家主手持<b>匠魂近战武器</b>（有 {@link ToolStats#ATTACK_DAMAGE}）右键<b>自己的诡厄仆从</b>
 * （{@code Owned} 子类且 {@code getTrueOwner()==玩家}，经 GoetyBridge 反射）→ 把武器装备到仆从主手，
 * 旧武器掉地、消耗玩家 1 个，并 cancel 事件阻止诡厄的 instanceof 拒绝分支。
 * <p>
 * 软依赖：goety 未装时 GoetyBridge 安全返回 false，本 handler 自然失效，不报错。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServantWeaponEquipHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity servant)) return;

        // 目标必须是玩家自己的诡厄仆从
        if (!GoetyBridge.isGoetyServant(servant)) return;
        LivingEntity owner = GoetyBridge.getServantOwner(servant);
        if (owner != player) return;

        // 玩家主手必须是匠魂近战武器
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        ToolStack tool = ToolHelper.getToolStack(held);
        if (tool == null || tool.isBroken()) return;
        // 近战判定：攻击伤害统计 > 0（匠魂近战工具都有；远程/工具类 attack_damage 为 0 不接受）
        if (tool.getStats().get(ToolStats.ATTACK_DAMAGE) <= 0) return;
        // 不给已经拿同款武器的仆从重复装备
        if (ItemStack.isSameItemSameTags(servant.getMainHandItem(), held)) return;

        // 装备：旧武器掉地，新武器放主手
        ItemStack old = servant.getMainHandItem();
        if (!old.isEmpty()) {
            servant.level().addFreshEntity(new ItemEntity(servant.level(),
                    servant.getX(), servant.getY() + 0.5, servant.getZ(), old));
        }
        ItemStack toEquip = held.copy();
        toEquip.setCount(1);
        servant.setItemSlot(EquipmentSlot.MAINHAND, toEquip);
        // 掉落率 0：仆从死亡不掉这把武器（与诡厄原生给武器的处理一致）；诡厄仆从都是 Mob
        if (servant instanceof net.minecraft.world.entity.Mob mob) {
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }

        // 消耗玩家主手 1 个（非创造）
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        servant.level().playSound(null, servant.getX(), servant.getY(), servant.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 阻止 goety 的 mobInteract 再处理（它不认匠魂武器，会走喂食/命令等其他分支）
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
