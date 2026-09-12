package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.common.collect.Multimap;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.entity.SlotAccess;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 构筑术式·<b>拟造蓝本</b>（代理物品）。
 *
 * <p>为什么要有这个物品：拟造物以前是"目标物品的真副本"，于是任何模组的配方都可能认它
 * （熔炼成熔融金属、合成、喂机器、上供仪式），而<b>配方匹配只认物品（registry id）、不认 NBT</b>
 * ——想用配置去堵，永远是在追着每一个模组跑。
 *
 * <p>这里换个思路：所有拟造物都是<b>同一个物品 id</b>（本类），目标物品与它自己的 NBT 记在栈里，
 * 然后<b>把整个 Item API 转发给目标物品实例</b>：
 * <ul>
 *   <li>转发时调用的就是目标物品自己的方法，所以它内部的 {@code this} 判定/逻辑全部正常；</li>
 *   <li>目标物品自己的 NBT <b>扁平化</b>放在栈根上（药水、成书、附魔这类靠 NBT 的物品照常工作），
 *       我们只额外加 {@code construct_target} 与 {@code construct_temp_until} 两个键；</li>
 *   <li>因为物品 id 不同、也不在任何 {@code #forge:*} 标签里，<b>所有配方（含流体/熔炼）天然不认它</b>。</li>
 * </ul>
 *
 * <p>已知边界（用户已知悉，遇到再补）：其它模组对"手上这个栈的物品类"做的
 * {@code instanceof} / {@code stack.is(X)} 判定转发不到（例：拟造匠魂工具不会被 TCon 认成工具）。
 */
public class ConstructedBlueprintItem extends Item {

    /** NBT：目标物品注册名 */
    public static final String KEY_TARGET = "tinkersnewlife.construct_target";

    /** 是否为"可食用"变体（原版 {@code isEdible()} 无参、转发不到，只能用另一个物品 id 区分） */
    private final boolean edibleVariant;

    public ConstructedBlueprintItem(Item.Properties properties, boolean edibleVariant) {
        super(properties);
        this.edibleVariant = edibleVariant;
    }

    // ============================================================
    //  创建 / 识别 / 目标解析
    // ============================================================

    /** 用目标物品栈铸造一个蓝本栈（目标 NBT 扁平化 + 我们两个键） */
    public static ItemStack create(ItemStack target, long until) {
        Item item;
        if (target.isEdible()) {
            item = com.mofengbaizhi.tinkersnewlife.content.ModItems.CONSTRUCT_BLUEPRINT_FOOD.get();
        } else {
            item = com.mofengbaizhi.tinkersnewlife.content.ModItems.CONSTRUCT_BLUEPRINT.get();
        }
        ItemStack out = new ItemStack(item, Math.max(1, target.getCount()));
        CompoundTag tag = new CompoundTag();
        if (target.hasTag()) {
            tag.merge(target.getTag().copy());
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(target.getItem());
        tag.putString(KEY_TARGET, id == null ? "minecraft:air" : id.toString());
        tag.putLong(com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.KEY_TEMP_UNTIL, until);
        out.setTag(tag);
        return out;
    }

    /** 是不是拟造蓝本 */
    public static boolean isBlueprint(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ConstructedBlueprintItem;
    }

    /** 蓝本的目标物品；不是蓝本 / 目标失效返回 null */
    @Nullable
    public static Item targetItem(ItemStack stack) {
        if (!isBlueprint(stack)) return null;
        CompoundTag tag = stack.getTag();
        return targetFromTag(tag);
    }

    @Nullable
    private static Item targetFromTag(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(KEY_TARGET, Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_TARGET));
        if (id == null) return null;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR
                || item instanceof ConstructedBlueprintItem) {
            return null;
        }
        return item;
    }

    /** 目标物品的"影子栈"：同 NBT 但换成真物品——用于取模型、调 {@code ItemOverrides} 等只读场景 */
    public static ItemStack shadowOf(ItemStack stack) {
        Item target = targetItem(stack);
        if (target == null) return ItemStack.EMPTY;
        ItemStack shadow = new ItemStack(target, Math.max(1, stack.getCount()));
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            CompoundTag copy = tag.copy();
            copy.remove(KEY_TARGET);
            copy.remove(com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.KEY_TEMP_UNTIL);
            if (!copy.isEmpty()) shadow.setTag(copy);
        }
        return shadow;
    }

    /** 蓝本的目标物品是否护甲（用于护甲外观渲染替换） */
    @Nullable
    public static net.minecraft.world.item.ArmorItem targetArmor(ItemStack stack) {
        Item target = targetItem(stack);
        return target instanceof net.minecraft.world.item.ArmorItem armor ? armor : null;
    }

    /** 渲染用：护甲槽里如果是蓝本，换成"真护甲栈"让原版渲染链正常走 */
    public static ItemStack forArmorRender(ItemStack stack) {
        if (!isBlueprint(stack)) return stack;
        ItemStack shadow = shadowOf(stack);
        return shadow.isEmpty() ? stack : shadow;
    }

    // ============================================================
    //  Item API → 目标物品 转发
    // ============================================================

    @Override
    public Component getName(ItemStack stack) {
        Item target = targetItem(stack);
        Component base = target != null ? target.getName(stack) : super.getName(stack);
        return Component.translatable("item.tinkersnewlife.construct.prefix").append(base);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getDescriptionId(stack) : super.getDescriptionId(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Item target = targetItem(stack);
        if (target != null) target.appendHoverText(stack, level, tooltip, flag);
        super.appendHoverText(stack, level, tooltip, flag);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getTooltipImage(stack) : super.getTooltipImage(stack);
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getRarity(stack) : super.getRarity(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.isFoil(stack) : super.isFoil(stack);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getMaxStackSize(stack) : super.getMaxStackSize(stack);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null && target.isDamageable(stack);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getMaxDamage(stack) : 0;
    }

    @Override
    public int getDamage(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getDamage(stack) : super.getDamage(stack);
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        Item target = targetItem(stack);
        if (target != null) target.setDamage(stack, damage);
        else super.setDamage(stack, damage);
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null && target.isDamaged(stack);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity,
                                                  Consumer<T> onBroken) {
        Item target = targetItem(stack);
        return target != null ? target.damageItem(stack, amount, entity, onBroken) : super.damageItem(stack, amount, entity, onBroken);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null && target.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getBarWidth(stack) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getBarColor(stack) : super.getBarColor(stack);
    }

    @Override
    public boolean isEdible() {
        return edibleVariant || super.isEdible();
    }

    @Override
    public @Nullable FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        Item target = targetItem(stack);
        return target != null ? target.getFoodProperties(stack, entity) : super.getFoodProperties(stack, entity);
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_EAT;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getUseAnimation(stack) : super.getUseAnimation(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getUseDuration(stack) : super.getUseDuration(stack);
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null && target.useOnRelease(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        Item target = targetItem(player.getItemInHand(hand));
        return target != null ? target.use(level, player, hand) : super.use(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Item target = targetItem(context.getItemInHand());
        return target != null ? target.useOn(context) : super.useOn(context);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Item target = targetItem(stack);
        return target != null ? target.onItemUseFirst(stack, context) : super.onItemUseFirst(stack, context);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                 InteractionHand hand) {
        Item item = targetItem(stack);
        return item != null ? item.interactLivingEntity(stack, player, target, hand)
                : super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Item target = targetItem(stack);
        return target != null ? target.finishUsingItem(stack, level, entity)
                : super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        Item target = targetItem(stack);
        if (target != null) target.onUseTick(level, entity, stack, remaining);
        else super.onUseTick(level, entity, stack, remaining);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        Item target = targetItem(stack);
        if (target != null) target.releaseUsing(stack, level, entity, timeLeft);
        else super.releaseUsing(stack, level, entity, timeLeft);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        Item target = targetItem(stack);
        return target != null ? target.getDestroySpeed(stack, state) : super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        Item target = targetItem(stack);
        return target != null ? target.isCorrectToolForDrops(stack, state) : super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        Item target = targetItem(player.getMainHandItem());
        return target != null ? target.canAttackBlock(state, level, pos, player)
                : super.canAttackBlock(state, level, pos, player);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        Item target = targetItem(stack);
        return target != null ? target.mineBlock(stack, level, state, pos, miner)
                : super.mineBlock(stack, level, state, pos, miner);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity victim, LivingEntity attacker) {
        Item target = targetItem(stack);
        return target != null ? target.hurtEnemy(stack, victim, attacker)
                : super.hurtEnemy(stack, victim, attacker);
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, Entity entity) {
        Item target = targetItem(stack);
        return target != null && target.canEquip(stack, slot, entity);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getAttributeModifiers(slot, stack)
                : super.getAttributeModifiers(slot, stack);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null && target.isEnchantable(stack);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getEnchantmentValue(stack) : super.getEnchantmentValue(stack);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        Item target = targetItem(stack);
        return target != null && target.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        Item target = targetItem(stack);
        return target != null && target.isValidRepairItem(stack, repair);
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null && target.isRepairable(stack);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null && target.hasCraftingRemainingItem(stack);
    }

    @Override
    public @Nullable ItemStack getCraftingRemainingItem(ItemStack stack) {
        Item target = targetItem(stack);
        return target != null ? target.getCraftingRemainingItem(stack) : super.getCraftingRemainingItem(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean selected) {
        Item target = targetItem(stack);
        if (target != null) target.inventoryTick(stack, level, entity, slotId, selected);
        else super.inventoryTick(stack, level, entity, slotId, selected);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        Item target = targetItem(stack);
        if (target != null) target.onCraftedBy(stack, level, player);
        else super.onCraftedBy(stack, level, player);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        Item target = targetItem(oldStack);
        if (target != null) return target.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
        return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }

    @Override
    public void verifyTagAfterLoad(CompoundTag tag) {
        Item target = targetFromTag(tag);
        if (target != null) target.verifyTagAfterLoad(tag);
        else super.verifyTagAfterLoad(tag);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        Item target = targetItem(stack);
        return target != null ? target.overrideStackedOnOther(stack, slot, action, player)
                : super.overrideStackedOnOther(stack, slot, action, player);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess access) {
        Item target = targetItem(stack);
        return target != null ? target.overrideOtherStackedOnMe(stack, other, slot, action, player, access)
                : super.overrideOtherStackedOnMe(stack, other, slot, action, player, access);
    }

    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        Item target = targetItem(stack);
        return target != null ? target.getBurnTime(stack, recipeType) : super.getBurnTime(stack, recipeType);
    }

    /** 目标物品的放置逻辑（{@link net.minecraft.world.item.BlockItem} 代理放置用） */
    @Nullable
    public static net.minecraft.world.item.BlockItem targetBlockItem(ItemStack stack) {
        Item target = targetItem(stack);
        return target instanceof net.minecraft.world.item.BlockItem block ? block : null;
    }

    /** 供调试：蓝本的一句话描述 */
    public static String describe(ItemStack stack) {
        Item target = targetItem(stack);
        return target == null ? "<无效蓝本>" : String.valueOf(ForgeRegistries.ITEMS.getKey(target));
    }

}
