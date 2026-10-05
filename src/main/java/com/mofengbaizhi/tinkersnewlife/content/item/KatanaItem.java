package com.mofengbaizhi.tinkersnewlife.content.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlot.Type;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import static slimeknights.tconstruct.library.tools.item.IModifiable.SHINY;
import static slimeknights.tconstruct.library.tools.item.IModifiable.NO_INTERACTION;
import static slimeknights.tconstruct.library.tools.item.IModifiable.DEFER_OFFHAND;
import static slimeknights.tconstruct.library.tools.item.IModifiable.INDESTRUCTIBLE_ENTITY;
import static slimeknights.tconstruct.library.tools.item.IModifiable.RARITY;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.behavior.AttributesModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.behavior.EnchantmentModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.DurabilityDisplayModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.EntityInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InventoryTickModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.SlotStackModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.UsingToolModifierHook;
import slimeknights.tconstruct.library.modifiers.modules.build.RarityModule;
import slimeknights.tconstruct.library.tools.IndestructibleItemEntity;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.display.ToolNameHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.IsEffectiveToolHook;
import slimeknights.tconstruct.library.tools.definition.module.mining.MiningSpeedToolHook;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.helper.ToolHarvestLogic;
import slimeknights.tconstruct.library.tools.helper.TooltipUtil;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerToolActions;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A standard modifiable item which implements melee hooks
 * This class handles how all the modifier hooks and display data for items made out of different materials
 */
public class KatanaItem extends ItemSlashBlade implements IModifiableDisplay {
  /** Tool definition for the given tool */
  private final ToolDefinition toolDefinition;

  /** Max stack size override */
  private final int maxStackSize;

  /** Cached tool for rendering on UIs */
  private ItemStack toolForRendering;

  /** 工具定义 id = tinkersnewlife:katana（与 tool_definitions/katana.json 同名） */
  public static final ToolDefinition KATANA_DEFINITION = ToolDefinition.create(new net.minecraft.resources.ResourceLocation("tinkersnewlife", "katana"));

  @Override
  public ToolDefinition getToolDefinition() {
    return this.toolDefinition;
  }
  public KatanaItem(Properties properties) {
    super(net.minecraft.world.item.Tiers.NETHERITE, 3, -2.4F, properties);
    this.toolDefinition = KATANA_DEFINITION;
    this.maxStackSize = 1;   // 工具不可堆叠（原来由匠魂构造设置，移植后要自己给）
  }

  @Override
  public int getMaxStackSize(ItemStack stack) {
    return stack.isDamaged() ? 1 : maxStackSize;
  }

  /* Basic properties */

  @Override
  public boolean isNotReplaceableByPickAction(ItemStack stack, Player player, int inventorySlot) {
    return true;
  }

  @Nullable
  @Override
  public EquipmentSlot getEquipmentSlot(ItemStack stack) {
    if (stack.is(TinkerTags.Items.HELD_ARMOR)) {
      return EquipmentSlot.OFFHAND;
    }
    return null;
  }

  /* Enchanting */

  @Override
  public boolean isEnchantable(ItemStack stack) {
    return false;
  }

  @Override
  public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
    return false;
  }

  @Override
  public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
    return enchantment.isCurse() && super.canApplyAtEnchantingTable(stack, enchantment);
  }

  @Override
  public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
    return EnchantmentModifierHook.getEnchantmentLevel(stack, enchantment);
  }

  @Override
  public Map<Enchantment,Integer> getAllEnchantments(ItemStack stack) {
    return EnchantmentModifierHook.getAllEnchantments(stack);
  }


  /* Loading */

  @Nullable
  @Override
  public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
    return new ToolCapabilityProvider(stack);
  }

  @Override
  public void verifyTagAfterLoad(CompoundTag nbt) {
    ToolStack.verifyTag(this, nbt, getToolDefinition());
  }

  @Override
  public void onCraftedBy(ItemStack stack, Level worldIn, Player playerIn) {
    ToolStack.ensureInitialized(stack, getToolDefinition());
  }


  /* Display */

  @Override
  public boolean isFoil(ItemStack stack) {
    // we use enchantments to handle some modifiers, so don't glow from them
    // however, if a modifier wants to glow let them
    return ModifierUtil.checkVolatileFlag(stack, SHINY);
  }

  @Override
  public Rarity getRarity(ItemStack stack) {
    return RarityModule.getRarity(stack);
  }


  /* Indestructible items */

  @Override
  public boolean hasCustomEntity(ItemStack stack) {
    // §1011：拔刀剑本体**恒返回 true** ✓（掉落的刀走 BladeItemEntity，靠 onEntityItemUpdate 换实体 ✓），
    // 而匠魂那份只在"不可摧毁工具"时为 true ⇒ 取并集 ✓，否则掉在地上的拔刀剑不是刀的样子 ✗。
    return super.hasCustomEntity(stack) || IndestructibleItemEntity.hasCustomEntity(stack);
  }

  @Nullable
  @Override
  public Entity createEntity(Level world, Entity original, ItemStack stack) {
    // §1011：只有匠魂"不可摧毁工具"才由我们接管掉落实体 ✓；其余交回基类（= 拔刀剑依赖的默认行为 ✓），
    // 否则会把 SlashBlade 换 BladeItemEntity 的流程顶掉 ✗。
    if (IndestructibleItemEntity.hasCustomEntity(stack)) {
      return IndestructibleItemEntity.createFrom(world, original, stack);
    }
    return super.createEntity(world, original, stack);
  }


  /* Damage/Durability */

  @Override
  public boolean isRepairable(ItemStack stack) {
    // handle in the tinker station
    return false;
  }

  @Override
  public boolean isValidRepairItem(ItemStack pToRepair, ItemStack pRepair) {
    return false;
  }

  @Override
  public boolean canBeDepleted() {
    return true;
  }

  @Override
  public int getMaxDamage(ItemStack stack) {
    return ToolDamageUtil.getFakeMaxDamage(stack);
  }

  @Override
  public int getDamage(ItemStack stack) {
    if (!canBeDepleted()) {
      return 0;
    }
    return ToolStack.from(stack).getDamage();
  }

  @Override
  public void setDamage(ItemStack stack, int damage) {
    if (canBeDepleted()) {
      ToolStack.from(stack).setDamage(damage);
    }
  }

  @Override
  public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T damager, Consumer<T> onBroken) {
    ToolDamageUtil.handleDamageItem(stack, amount, damager, onBroken);
    return 0;
  }


  /* Durability display */

  @Override
  public boolean isBarVisible(ItemStack stack) {
    return stack.getCount() == 1 && DurabilityDisplayModifierHook.showDurabilityBar(stack);
  }

  @Override
  public int getBarColor(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityRGB(pStack);
  }

  @Override
  public int getBarWidth(ItemStack pStack) {
    return DurabilityDisplayModifierHook.getDurabilityWidth(pStack);
  }


  /* Attacking */

  @Override
  public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
    // §1020 **完全按 TiCEX 走通的那条路** ✓：本方法就写成"调本体并原样传递它的返回值" ✓
    //   —— TiCEX 的 ModifiableSlashBladeItem 正是这样写的 ✓：
    //     `return stack.getCount() > 1 || this.onEntityInteractLeftClick(...) || super.onLeftClickEntity(stack, player, target);`
    //
    // 为什么这次能成立（前五轮为什么不行 ✗ 都记在 §1011–§1019 ✓）：
    //   · 本体的返回语义有两层含义 ✓：闸门放行时"否决原版那一击"（伤害由本体自己的 AttackManager 结算 ✓），
    //     闸门挡掉时"照常打" ✓ —— 而这套判定依赖 `_onClick` 开关与本体攻击管线 ✓；
    //   · 我们此前**没有本体的攻击管线**（伤害无处可来 ✗）⇒ 要么否决＝打不到 ✗、要么不否决＝每刻重复命中 ✗；
    //   · §1020 补上了三个 mixin ✓（`KatanaAttackHelperMixin`/`KatanaAttackManagerMixin`/`KatanaItemSlashBladeMixin` ✓）
    //     ⇒ 本体的攻击管线**会把伤害按匠魂数值结算** ✓ ⇒ 于是"否决原版那一击"不再意味着没伤害 ✓
    //     ⇒ 可以放心照抄本体/TiCEX 的写法 ✓，连段、技能、击退手感全部回到原版机制 ✓。
    //
    // 匠魂那边要的"击中类修饰符"由 §1020 的两个 mixin 在**本体的伤害结算里**调用 ✓
    //   （比在这里再调一次 TC 的 leftClickEntity 更贴合原版流程 ✓，也避免重复结算 ✗）。
    return super.onLeftClickEntity(stack, player, target);
  }

  @Override
  public Multimap<Attribute,AttributeModifier> getAttributeModifiers(IToolStackView tool, EquipmentSlot slot) {
    return AttributesModifierHook.getHeldAttributeModifiers(tool, slot);
  }

  @Override
  public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
    CompoundTag nbt = stack.getTag();
    if (nbt == null || slot.getType() != Type.HAND) {
      return ImmutableMultimap.of();
    }
    // §1013：**不再并入本体那份属性**。本体那份是"剑基础值 + 精炼/魂魄加成 + 触及距离"，
    // 而基础值已经由匠魂面板给出 ✓ ⇒ 并进来就会把攻击力算两遍 ✗（§1011 那一版有这个隐患 ✓ 已撤）。
    return getAttributeModifiers(ToolStack.from(stack), slot);
  }

  @Override
  public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
    return canPerformAction(stack, TinkerToolActions.SHIELD_DISABLE);
  }


  /* Harvest logic */

  @Override
  public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
    return IsEffectiveToolHook.isEffective(ToolStack.from(stack), state);
  }

  @Override
  public boolean mineBlock(ItemStack stack, Level worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving) {
    // §1013：本体在这里做"破坏方块时的刀效果/耐久" ✓，两边都要跑 ✓
    boolean slashBlade = super.mineBlock(stack, worldIn, state, pos, entityLiving);
    return ToolHarvestLogic.mineBlock(stack, worldIn, state, pos, entityLiving) || slashBlade;
  }

  @Override
  public float getDestroySpeed(ItemStack stack, BlockState state) {
    return stack.getCount() == 1 ? MiningSpeedToolHook.getDestroySpeed(stack, state) : 0;
  }

  @Override
  public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
    return stack.getCount() > 1 || ToolHarvestLogic.handleBlockBreak(stack, pos, player);
  }


  /* Modifier interactions */

  @Override
  public void inventoryTick(ItemStack stack, Level worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
    // §1013：本体在这里维护"蓄力/连段/损坏"等逐帧状态 ✓（被顶掉会导致连段与蓄力不成立 ✗）
    super.inventoryTick(stack, worldIn, entityIn, itemSlot, isSelected);
    InventoryTickModifierHook.heldInventoryTick(stack, worldIn, entityIn, itemSlot, isSelected);
  }

  @Override
  public boolean overrideStackedOnOther(ItemStack held, Slot slot, ClickAction action, Player player) {
    return SlotStackModifierHook.overrideStackedOnOther(held, slot, action, player);
  }

  @Override
  public boolean overrideOtherStackedOnMe(ItemStack slotStack, ItemStack held, Slot slot, ClickAction action, Player player, SlotAccess access) {
    return SlotStackModifierHook.overrideOtherStackedOnMe(slotStack, held, slot, action, player, access);
  }


  /* Right click hooks */

  /** If true, this interaction hook should defer to the offhand */
  protected static boolean shouldInteract(@Nullable LivingEntity player, ToolStack toolStack, InteractionHand hand) {
    IModDataView volatileData = toolStack.getVolatileData();
    if (volatileData.getBoolean(NO_INTERACTION)) {
      return false;
    }
    // off hand always can interact
    if (hand == InteractionHand.OFF_HAND) {
      return true;
    }
    // main hand may wish to defer to the offhand if it has a tool
    return player == null || !volatileData.getBoolean(DEFER_OFFHAND) || player.getOffhandItem().isEmpty();
  }
  
  @Override
  public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
    if (stack.getCount() == 1) {
      ToolStack tool = ToolStack.from(stack);
      InteractionHand hand = context.getHand();
      if (shouldInteract(context.getPlayer(), tool, hand)) {
        for (ModifierEntry entry : tool.getModifierList()) {
          InteractionResult result = entry.getHook(ModifierHooks.BLOCK_INTERACT).beforeBlockUse(tool, entry, context, InteractionSource.RIGHT_CLICK);
          if (result.consumesAction()) {
            return result;
          }
        }
      }
    }
    return InteractionResult.PASS;
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    ItemStack stack = context.getItemInHand();
    if (stack.getCount() == 1) {
      ToolStack tool = ToolStack.from(stack);
      InteractionHand hand = context.getHand();
      if (shouldInteract(context.getPlayer(), tool, hand)) {
        for (ModifierEntry entry : tool.getModifierList()) {
          InteractionResult result = entry.getHook(ModifierHooks.BLOCK_INTERACT).afterBlockUse(tool, entry, context, InteractionSource.RIGHT_CLICK);
          if (result.consumesAction()) {
            return result;
          }
        }
      }
    }
    return InteractionResult.PASS;
  }

  @Override
  public InteractionResult interactLivingEntity(ItemStack stack, Player playerIn, LivingEntity target, InteractionHand hand) {
    ToolStack tool = ToolStack.from(stack);
    if (shouldInteract(playerIn, tool, hand)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        InteractionResult result = entry.getHook(ModifierHooks.ENTITY_INTERACT).afterEntityUse(tool, entry, playerIn, target, hand, InteractionSource.RIGHT_CLICK);
        if (result.consumesAction()) {
          return result;
        }
      }
    }
    return InteractionResult.PASS;
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level worldIn, Player playerIn, InteractionHand hand) {
    ItemStack stack = playerIn.getItemInHand(hand);
    // ⚠ §1013：**必须先问拔刀剑本体**。ItemSlashBlade#use 会登记 R_CLICK 输入指令、推进连段 ✓，
    // 需要时还会 player.startUsingItem(hand) 进入"使用中"状态 —— **蓄力→释放斩这条线全靠它** ✓。
    // 移植匠魂时把这句 super 丢了 ✗ ⇒ 右键完全没反应 ✗（实测症状）。
    InteractionResultHolder<ItemStack> slashBlade = super.use(worldIn, playerIn, hand);
    if (slashBlade.getResult().consumesAction()) {
      return slashBlade;
    }
    if (stack.getCount() > 1) {
      return InteractionResultHolder.pass(stack);
    }
    ToolStack tool = ToolStack.from(stack);
    if (shouldInteract(playerIn, tool, hand)) {
      for (ModifierEntry entry : tool.getModifierList()) {
        InteractionResult result = entry.getHook(ModifierHooks.GENERAL_INTERACT).onToolUse(tool, entry, playerIn, hand, InteractionSource.RIGHT_CLICK);
        if (result.consumesAction()) {
          return new InteractionResultHolder<>(result, stack);
        }
      }
    }
    // 本体没接受这次右键时它会返回 FAIL ✓ —— 直接照它的结论走（不再回 pass 去干扰它 ✓）
    return slashBlade;
  }

  @Override
  public void onUseTick(Level pLevel, LivingEntity entityLiving, ItemStack stack, int timeLeft) {
    // §1013：本体负责"蓄力期间"的逐帧逻辑 ✓（§1011 之前被我们顶掉了 ✗）
    super.onUseTick(pLevel, entityLiving, stack, timeLeft);
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    // new hook gets called on all actively in use modifiers
    GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
    int duration = hook.getUseDuration(tool, activeModifier);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).onUsingTick(tool, entry, entityLiving, duration, timeLeft, activeModifier);
    }
    // old hook is called on just the main modifier
    hook.onUsingTick(tool, activeModifier, entityLiving, timeLeft);
  }

  @Override
  public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
    if (super.canContinueUsing(oldStack, newStack)) {
      if (oldStack != newStack) {
        GeneralInteractionModifierHook.finishUsing(ToolStack.from(oldStack));
      }
    }
    return super.canContinueUsing(oldStack, newStack);
  }

  @Override
  public ItemStack finishUsingItem(ItemStack stack, Level worldIn, LivingEntity entityLiving) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
    int duration = hook.getUseDuration(tool, activeModifier);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, entry, entityLiving, duration, 0, activeModifier);
    }
    hook.onFinishUsing(tool, activeModifier, entityLiving);
    return stack;
  }

  @Override
  public void releaseUsing(ItemStack stack, Level worldIn, LivingEntity entityLiving, int timeLeft) {
    // §1013：**松开右键 = 拔刀剑的蓄力释放（释放斩/特殊技）** ✓ 全在本体里，必须先让它跑 ✓
    super.releaseUsing(stack, worldIn, entityLiving, timeLeft);
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    GeneralInteractionModifierHook hook = activeModifier.getHook(ModifierHooks.GENERAL_INTERACT);
    int duration = hook.getUseDuration(tool, activeModifier);
    for (ModifierEntry entry : tool.getModifiers()) {
      entry.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, entry, entityLiving, duration, timeLeft, activeModifier);
    }
    hook.onStoppedUsing(tool, activeModifier, entityLiving, timeLeft);
  }

  @Override
  public void onStopUsing(ItemStack stack, LivingEntity entity, int timeLeft) {
    // triggers on scroll away and all that
    ToolStack tool = ToolStack.from(stack);
    UsingToolModifierHook.afterStopUsing(tool, entity, timeLeft);
    GeneralInteractionModifierHook.finishUsing(tool);
  }

  @Override
  public int getUseDuration(ItemStack stack) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    if (activeModifier != ModifierEntry.EMPTY) {
      return activeModifier.getHook(ModifierHooks.GENERAL_INTERACT).getUseDuration(tool, activeModifier);
    }
    return 0;
  }

  @Override
  public UseAnim getUseAnimation(ItemStack stack) {
    ToolStack tool = ToolStack.from(stack);
    ModifierEntry activeModifier = GeneralInteractionModifierHook.getActiveModifier(tool);
    if (activeModifier != ModifierEntry.EMPTY) {
      return activeModifier.getHook(ModifierHooks.GENERAL_INTERACT).getUseAction(tool, activeModifier);
    }
    return UseAnim.NONE;
  }

  @Override
  public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
    return stack.getCount() == 1 && ModifierUtil.canPerformAction(ToolStack.from(stack), toolAction);
  }


  /* Tooltips */

  @Override
  public Component getName(ItemStack stack) {
    return ToolNameHook.getName(getToolDefinition(), stack);
  }

  @Override
  public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    TooltipUtil.addInformation(this, stack, level, tooltip, SafeClientAccess.getTooltipKey(), flag);
  }

  @Override
  public int getDefaultTooltipHideFlags(ItemStack stack) {
    return TooltipUtil.getModifierHideFlags(getToolDefinition());
  }
  

  /* Display */

  @Override
  public ItemStack getRenderTool() {
    if (toolForRendering == null) {
      toolForRendering = ToolBuildHandler.buildToolForRendering(this, this.getToolDefinition());
    }
    return toolForRendering;
  }

  @Override
  public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    // 渲染委托给 SlashBlade 本体：ItemSlashBlade 自带一个客户端扩展，其 getCustomRenderer()
    // 返回 SlashBladeTEISR（用 BLADESTATE 里的 model/texture 画刀身，缺省是
    // slashblade:model/blade.obj + slashblade:model/blade.png）。
    // 移植 ModifiableItem 时这里被换成了匠魂的 ModifiableItemClientExtension（画工具模型），
    // 会让拔刀剑在手里/物品栏里都不是刀的样子，因此改回基类实现。
    super.initializeClient(consumer);
  }


  /* Misc */

  /**
   * Logic to prevent reanimation on tools when properties such as autorepair change.
   * @param oldStack      Old stack instance
   * @param newStack      New stack instance
   * @param slotChanged   If true, a slot changed
   * @return  True if a reequip animation should be triggered
   */
  public static boolean shouldCauseReequip(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    if (oldStack == newStack) {
      return false;
    }
    // basic changes
    if (slotChanged || oldStack.getItem() != newStack.getItem()) {
      return true;
    }

    // if the tool props changed,
    ToolStack oldTool = ToolStack.from(oldStack);
    ToolStack newTool = ToolStack.from(newStack);

    // check if modifiers or materials changed
    if (!oldTool.getMaterials().equals(newTool.getMaterials())) {
      return true;
    }
    if (!oldTool.getModifierList().equals(newTool.getModifierList())) {
      return true;
    }

    // if the attributes changed, reequip
    Multimap<Attribute,AttributeModifier> attributesNew = newStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
    Multimap<Attribute, AttributeModifier> attributesOld = oldStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
    if (attributesNew.size() != attributesOld.size()) {
      return true;
    }
    for (Attribute attribute : attributesOld.keySet()) {
      if (!attributesNew.containsKey(attribute)) {
        return true;
      }
      Iterator<AttributeModifier> iter1 = attributesNew.get(attribute).iterator();
      Iterator<AttributeModifier> iter2 = attributesOld.get(attribute).iterator();
      while (iter1.hasNext() && iter2.hasNext()) {
        if (!iter1.next().equals(iter2.next())) {
          return true;
        }
      }
    }
    // no changes, no reequip
    return false;
  }

  @Override
  public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
    return shouldCauseReequipAnimation(oldStack, newStack, false);
  }

  @Override
  public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
    return shouldCauseReequip(oldStack, newStack, slotChanged);
  }


  /* Helpers */

  /**
   * Creates a raytrace and casts it to a BlockRayTraceResult
   *
   * @param worldIn the world
   * @param player the given player
   * @param fluidMode the fluid mode to use for the raytrace event
   *
   * @return  Raytrace
   */
  public static BlockHitResult blockRayTrace(Level worldIn, Player player, ClipContext.Fluid fluidMode) {
    return Item.getPlayerPOVHitResult(worldIn, player, fluidMode);
  }
}
