package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.helper.ToolAttackUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 魔力剑聚晶（goety sword_focus）发射匠魂工具的支持。
 * <ul>
 *   <li>「可发射判定」：数据 tag {@code data/goety/tags/items/magic_sword_shootable.json}
 *       引 {@code #tconstruct:modifiable/melee/weapon} → 全部匠魂近战武器可被 sword_focus 发射。</li>
 *   <li>「发射即扣耐久」：goety 原版对发射物调 vanilla {@code hurtAndBreak}（匠魂工具不响应）；
 *       本 handler 在弹射物加入世界时，对发射者身上的同款匠魂工具走
 *       {@link ToolDamageUtil#damage} 扣 {@link #DURABILITY_COST}=10（覆层/不毁/词条 onDamageTool 生效）。</li>
 *   <li>「命中按武器结算」：{@link ProjectileImpactEvent.ImpactResult#STOP_AT_CURRENT_NO_DAMAGE}
 *       取消 goety 固定伤害，改为用工具完整近战结算——攻击力 + 暴击 + MELEE 词条
 *       （经 {@link ToolAttackUtil#attackEntity}），随后同步耐久回玩家原工具。</li>
 * </ul>
 * 软依赖：goety 未装时实体注册 id 匹配不到，全链路自然失效；本类不 import 任何 goety 类型。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SwordProjectileTinkerHandler {

    /** goety 魔力剑弹射物实体注册 id */
    private static final ResourceLocation GOETY_SWORD = new ResourceLocation("goety", "sword");
    /** 每次发射消耗耐久（与 goety SwordSpell 一致） */
    private static final int DURABILITY_COST = 10;

    private static boolean isGoetySword(Entity entity) {
        if (entity == null) return false;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return key != null && GOETY_SWORD.equals(key);
    }

    /**
     * 弹射物加入世界（= 发射成功）：魔力剑弹射物由 sword_focus 施法产生，
     * 发射的是玩家主手/副手剑的副本——从发射者身上找同款匠魂工具扣耐久。
     */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity entity = event.getEntity();
        if (!isGoetySword(entity)) return;
        if (!(entity instanceof Projectile projectile)) return;

        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player player)) return;

        // 发射者手上的匠魂近战武器（主手魔杖施法，剑在副手或背包；这里遍历找第一把匠魂近战武器）
        ItemStack original = findFirstMeleeTool(player);
        if (original.isEmpty()) return;
        ToolStack tool = ToolHelper.getToolStack(original);
        if (tool == null || tool.isBroken()) return;

        // 走匠魂正常耐久逻辑
        ToolDamageUtil.damage(tool, DURABILITY_COST, player, original);
    }

    /** 玩家主手/副手/背包中第一把未损坏的匠魂近战武器（按攻击伤害 &gt; 0 判定近战） */
    private static ItemStack findFirstMeleeTool(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isMeleeTool(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isMeleeTool(off)) return off;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (isMeleeTool(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isMeleeTool(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null || tool.isBroken()) return false;
        return tool.getStats().get(slimeknights.tconstruct.library.tools.stat.ToolStats.ATTACK_DAMAGE) > 0;
    }

    /**
     * 命中接管：goety:sword 命中实体 → 停住且不造成 goety 原伤害，
     * 改由玩家身上同款匠魂武器做一次完整近战攻击。
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        Projectile projectile = event.getProjectile();
        if (!isGoetySword(projectile)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;

        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player player)) return;

        // 用发射者手上的同款武器结算（弹射物即由它发射，主手/副手/背包找第一把）
        ItemStack original = findFirstMeleeTool(player);
        if (original.isEmpty()) return;
        ToolStack tool = ToolHelper.getToolStack(original);
        if (tool == null || tool.isBroken()) return;

        // 停住弹射物、不造成 goety 原伤害
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        if (projectile.isAlive()) {
            projectile.discard();
        }

        // 用玩家主手临时持有工具副本来攻击（匠魂 attackEntity 从玩家主手读栈结算）
        try {
            ItemStack oldMain = player.getMainHandItem();
            ItemStack attackStack = original.copy();
            player.getInventory().setItem(player.getInventory().selected, attackStack);
            try {
                ToolAttackUtil.attackEntity(attackStack, player, target);
            } finally {
                player.getInventory().setItem(player.getInventory().selected, oldMain);
                // 把副本上的耐久/状态同步回玩家原工具
                syncToolBack(player, attackStack);
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[魔力剑·匠魂] 近战结算异常", t);
        }
    }

    /** 攻击后把临时副本上的耐久写回玩家当前持有的同 id 工具 */
    private static void syncToolBack(Player player, ItemStack temp) {
        ToolStack tempTool = ToolHelper.getToolStack(temp);
        if (tempTool == null) return;
        // 玩家手上找同 id 工具（攻击过程中主手被我们换过又还原，找原来的那把）
        ItemStack current = findFirstMeleeTool(player);
        if (current.isEmpty()) return;
        ToolStack curTool = ToolHelper.getToolStack(current);
        if (curTool == null) return;
        curTool.setDamage(tempTool.getDamage());
        curTool.updateStack(current);
    }
}
