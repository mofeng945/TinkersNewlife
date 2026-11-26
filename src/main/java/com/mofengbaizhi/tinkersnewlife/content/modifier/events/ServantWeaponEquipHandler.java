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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableLauncherItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * 让诡厄巫法的仆从能使用/穿戴匠魂装备（右键交互，原生体验）。
 * <p>
 * 诡厄原生的给仆从装备逻辑只认 {@code SwordItem}/{@code AxeItem}/{@code TridentItem} /
 * vanilla {@code ArmorItem}，匠魂工具（{@code ModifiableItem} 体系）不是这些子类
 * （匠魂盔甲虽 extends ArmorItem 但走匠魂自己槽位/耐久，goety equipServantArmor 对其不完整），
 * 玩家右键仆从时会被诡厄直接跳过或处理不当。
 * 本 handler 在 {@code PlayerInteractEvent.EntityInteract}（早于诡厄 mobInteract）拦截：
 * 玩家主手持<b>匠魂装备</b>右键<b>自己的诡厄仆从</b>（{@code Owned} 子类且
 * {@code getTrueOwner()==玩家}，经 GoetyBridge 反射）→ 装备到对应槽位，
 * 旧装备掉地、消耗玩家 1 个，并 cancel 事件阻止诡厄的 instanceof 拒绝分支。
 * <ul>
 *   <li><b>近战</b>（有 {@link ToolStats#ATTACK_DAMAGE}）：任意仆从可装备（僵尸/骷髅都会近战）。</li>
 *   <li><b>远程发射器（弓/弩）</b>（{@link ModifiableLauncherItem}）：只给<b>远程型仆从</b>
 *       （原版接口 {@link RangedAttackMob}——诡厄骷髅系仆从都实现它，僵尸仆从不是）；
 *       射击行为由 {@link ServantBowAttackHandler} 接管。</li>
 *   <li><b>盔甲</b>（{@link ModifiableArmorItem}）：任意能穿甲的仆从可装备；
 *       词条/被动由各 ArmorTrait handler 生效（本 handler 只负责「穿上」）。</li>
 * </ul>
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

        // 玩家主手必须是匠魂装备（近战 / 远程发射器 / 盔甲）
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        ToolStack tool = ToolHelper.getToolStack(held);
        if (tool == null || tool.isBroken()) return;

        boolean isMelee = tool.getStats().get(ToolStats.ATTACK_DAMAGE) > 0;
        boolean isLauncher = held.getItem() instanceof ModifiableLauncherItem;
        boolean isArmor = held.getItem() instanceof ModifiableArmorItem;
        if (!isMelee && !isLauncher && !isArmor) return;

        EquipmentSlot slot;
        if (isArmor) {
            // 弓/弩只给能拿武器的仆从；盔甲按 goety canWearArmor 判定
            if (!GoetyBridge.canServantWearArmor(servant)) return;
            slot = armorSlot(held);
            if (slot == null) return;
        } else {
            if (isLauncher && !(servant instanceof RangedAttackMob)) return; // 远程发射器只能给远程型仆从
            slot = EquipmentSlot.MAINHAND;
        }

        // 不给已经穿/拿同款装备的仆从重复装备
        if (ItemStack.isSameItemSameTags(servant.getItemBySlot(slot), held)) return;

        // 装备：旧装备掉地，新装备放对应槽
        ItemStack old = servant.getItemBySlot(slot);
        if (!old.isEmpty()) {
            servant.level().addFreshEntity(new ItemEntity(servant.level(),
                    servant.getX(), servant.getY() + 0.5, servant.getZ(), old));
        }
        ItemStack toEquip = held.copy();
        toEquip.setCount(1);
        servant.setItemSlot(slot, toEquip);
        // 掉落率 0：仆从死亡不掉这件装备（与诡厄原生处理一致）；诡厄仆从都是 Mob
        if (servant instanceof Mob mob) {
            mob.setDropChance(slot, 0.0F);
        }

        // 消耗玩家主手 1 个（非创造）
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        servant.level().playSound(null, servant.getX(), servant.getY(), servant.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 阻止 goety 的 mobInteract 再处理
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** 匠魂盔甲的装备槽（ModifiableArmorItem extends ArmorItem，按其 Type 映射） */
    private static EquipmentSlot armorSlot(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return switch (armor.getType()) {
                case HELMET -> EquipmentSlot.HEAD;
                case CHESTPLATE -> EquipmentSlot.CHEST;
                case LEGGINGS -> EquipmentSlot.LEGS;
                case BOOTS -> EquipmentSlot.FEET;
            };
        }
        return null;
    }
}
