package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.DragonStaffTrait;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DragonStaffHandler {

    // NBT 键
    private static final ResourceLocation KEY_SLOTS = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff_slots");
    private static final ResourceLocation KEY_OWNER = new ResourceLocation(TinkersNewlife.MOD_ID, "staff_owner");

    // 常量
    private static final int SKILL_XP_COST = 88;
    private static final int SEARCH_RADIUS = 16;
    private static double tamingChance(LivingEntity dragon) {
        double healthFraction = (double) dragon.getHealth() / dragon.getMaxHealth();
        return Math.min(1.0, 0.20 + (1.0 - Math.max(0.20, healthFraction)));
    }
    private static final int TAMING_DURATION_TICKS = 60;
    private static final int INTERACT_COOLDOWN_TICKS = 20;
    private static final int HEART_PARTICLE_COUNT = 30;
    private static final int ANGRY_PARTICLE_COUNT = 30;

    private static final Map<UUID, TamingTask> TAMING_TASKS = new ConcurrentHashMap<>();
    private static final ModifierId DRAGON_STAFF_ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, DragonStaffTrait.MODIFIER_ID));

    // ---------- 内部类 ----------
    private static class TamingTask {
        final Player player;
        final ToolStack tool;
        final Entity target;
        final Vec3 playerAnchor;
        final Vec3 dragonAnchor;
        final Level level;
        final ItemStack staff;
        int remainingTicks;
        TamingTask(Player player, ToolStack tool, Entity target, int duration) {
            this.player = player;
            this.tool = tool;
            this.target = target;
            this.playerAnchor = player.position();
            this.dragonAnchor = target.position();
            this.level = player.level();
            this.staff = player.getMainHandItem();
            this.remainingTicks = duration;
        }
    }

    private static final ResourceLocation PIT_END = TinkersNewlife.prefix("dragon_pit_end");
    private static final String PIT_WINNER = "TnlDragonPitWinner";

    public static boolean canStartPit(Player player) {
        var tool = ToolHelper.getToolStack(player.getMainHandItem());
        return tool != null && com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.isActive(tool);
    }

    public static int pitRemaining(Player player) {
        var tool = ToolHelper.getToolStack(player.getMainHandItem());
        return tool == null ? 0 : (int)Math.max(0, Math.min(1200,
                pitEnd(tool) - player.level().getGameTime()));
    }

    private static long pitEnd(ToolStack tool) {
        return tool.getPersistentData().get(PIT_END) instanceof net.minecraft.nbt.LongTag end ? end.getAsLong() : 0;
    }

    private static boolean pitLocked(ToolStack tool) { return pitEnd(tool) > 0; }

    public static void startPit(net.minecraft.server.level.ServerPlayer player) {
        var tool = getTool(player);
        if (tool == null || !isOwner(tool, player)) return;
        var slots = getSlots(tool);
        if (!pitLocked(tool) && canStartPit(player) && !TAMING_TASKS.containsKey(player.getUUID())
                && eligibleWinner(slots) >= 0) {
            tool.getPersistentData().put(PIT_END, net.minecraft.nbt.LongTag.valueOf(player.level().getGameTime() + 1200));
            tool.updateStack(player.getMainHandItem());
        } else {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.dragon_pit.unavailable"), true);
        }
        com.mofengbaizhi.tinkersnewlife.network.dragon.DragonCards.open(player, slots, true);
    }

    private static int eligibleWinner(ListTag slots) {
        int winner = -1, count = 0;
        double best = -1;
        for (int i = 0; i < slots.size(); i++) {
            var entry = slots.getCompound(i);
            if (entry.getInt("state") != 1 || entry.contains(REVIVE_AT)) continue;
            var data = entry.getCompound("data");
            if (!data.contains("Attributes", Tag.TAG_LIST) || data.getFloat("Health") <= 0) return -1;
            int stage = Math.min(5, Math.max(0, data.getInt("AgeTicks")) / 600000 + 1);
            double score = DragonPitPower.score(stage, stat(data, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH),
                    stat(data, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE),
                    stat(data, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR));
            if (score > best) { best = score; winner = i; }
            count++;
        }
        return count >= 2 ? winner : -1;
    }

    private static double stat(CompoundTag data, net.minecraft.world.entity.ai.attributes.Attribute attr) {
        return com.mofengbaizhi.tinkersnewlife.network.dragon.DragonCards.attribute(data, attr);
    }

    @SubscribeEvent public static void settlePit(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof net.minecraft.server.level.ServerPlayer player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.DragonStaffItem)) return;
        var tool = ToolHelper.getToolStack(stack);
        if (tool == null || !pitLocked(tool) || !isOwner(tool, player) || pitRemaining(player) > 0) return;
        var roster = getSlots(tool);
        int winner = canStartPit(player) ? eligibleWinner(roster) : -1;
        tool.getPersistentData().remove(PIT_END);
        if (winner >= 0) {
            var survivor = roster.getCompound(winner);
            var type = ResourceLocation.tryParse(survivor.getString("type"));
            var entityType = type == null ? null : com.mofengbaizhi.tinkersnewlife.util.SafeRegistry.entityType(type);
            try {
                if (entityType == null || !(entityType.create(player.level()) instanceof LivingEntity dragon)) {
                    winner = -1;
                } else {
                    dragon.load(survivor.getCompound("data"));
                    int participants = 0;
                    for (int i = 0; i < roster.size(); i++) if (canSupport(roster.getCompound(i))) participants++;
                    com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.awardWin(dragon, participants);
                    CompoundTag updated = new CompoundTag();
                    dragon.saveWithoutId(updated);
                    updated.putString("id", type.toString());
                    survivor.put("data", updated);
                    survivor.putBoolean(PIT_WINNER, true);
                }
            } catch (RuntimeException failure) {
                winner = -1;
                TinkersNewlife.LOGGER.warn("Could not resolve dragon pit winner; roster preserved", failure);
            }
        }
        if (winner >= 0) {
            for (int i = roster.size() - 1; i >= 0; i--) {
                if (i != winner && canSupport(roster.getCompound(i))) roster.remove(i);
            }
            setSlots(player, tool, roster);
        }
        tool.updateStack(stack);
        com.mofengbaizhi.tinkersnewlife.network.dragon.DragonCards.open(player, roster, true);
    }

    // ---------- 基础 NBT 操作 ----------
    private static ListTag getSlots(IToolStackView tool) {
        Tag tag = tool.getPersistentData().get(KEY_SLOTS);
        return tag instanceof ListTag ? (ListTag) tag : new ListTag();
    }

    private static void setSlots(Player player, ToolStack tool, ListTag slots) {
        tool.getPersistentData().put(KEY_SLOTS, slots);
        tool.rebuildStats();
        if (player.level() instanceof ServerLevel level) com.mofengbaizhi.tinkersnewlife.content.modifier.DragonHusbandryModifier.refreshReleased(tool, level, slots);
    }

    // ---------- 主人绑定 ----------
    private static void ensureOwner(IToolStackView tool, Player player) {
        if (!tool.getPersistentData().contains(KEY_OWNER)) {
            tool.getPersistentData().putString(KEY_OWNER, player.getUUID().toString());
        }
    }

    private static boolean isOwner(IToolStackView tool, Player player) {
        String ownerStr = tool.getPersistentData().getString(KEY_OWNER);
        return !ownerStr.isEmpty() && ownerStr.equals(player.getUUID().toString());
    }

    @Nullable
    private static ToolStack getTool(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IModifiable)) return null;
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return null;
        if (ToolHelper.getActiveModifierLevel(tool, DRAGON_STAFF_ID) <= 0) return null;
        if (!player.level().isClientSide) ensureOwner(tool, player);
        return tool;
    }

    /**
     * TNL1310 用户口径：「把驯龙杖的冷却走正常工具的 cooldown」。
     *
     * <p>以前是**自己记 NBT 时间戳**（`last_interact_time`）＋ 冷却中往物品栏上方刷一句
     * "技能冷却中，还需 %d tick" ✗；现在改成**原版物品冷却**：
     * {@link Player#getCooldowns()}{@code .addCooldown(item, ticks)} ⇒ 热键栏那格自带扫光 ✓
     * （和普通工具有冷却时的表现完全一致 ✓ 服务端会自己同步给客户端 ✓）
     * ⇒ 冷却中**不再发聊天提示**（原版冷却本身就是可见的 ✓）。
     */
    private static boolean checkCooldown(ItemStack stack, Player player) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        player.getCooldowns().addCooldown(stack.getItem(), INTERACT_COOLDOWN_TICKS);
        return true;
    }
    // Right-click a dragon to store it; sneaking right-click releases one at the existing release location.
    private static void interact(Player player, Entity target) {
        ToolStack tool = getTool(player);
        if (tool == null || TAMING_TASKS.containsKey(player.getUUID())) return;
        if (!isOwner(tool, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_owner"), true);
            return;
        }
        if (player.isShiftKeyDown() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.mofengbaizhi.tinkersnewlife.network.dragon.DragonCards.open(sp, getSlots(tool));
            return;
        }
        if (!pitLocked(tool) && target != null && checkCooldown(player.getMainHandItem(), player)) {
            tryRecycle(player, tool, target);
            tool.updateStack(player.getMainHandItem());
        }
    }

    @SubscribeEvent public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && !player.level().isClientSide && player.isShiftKeyDown()) interact(player, null);
    }

    @SubscribeEvent public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || (!player.isShiftKeyDown() && !isDragon(event.getTarget())) || getTool(player) == null) return;
        if (!player.level().isClientSide) interact(player, event.getTarget());
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
    }

    @SubscribeEvent public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || getTool(player) == null) return;
        if (!player.level().isClientSide) interact(player, null);
        event.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
    }

    @SubscribeEvent public static void onAttackDragon(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        Entity target = com.mofengbaizhi.tinkersnewlife.util.TruePierce.resolveVictim(event.getTarget());
        if (!(target instanceof TamableAnimal dragon) || !isDragon(dragon) || !dragon.isOwnedBy(player)) return;
        ToolStack tool = getTool(player);
        if (tool == null) return;
        event.setCanceled(true);
        if (player.level().isClientSide || !isOwner(tool, player) || !checkCooldown(player.getMainHandItem(), player)) return;
        try {
            int current = ((Number)dragon.getClass().getMethod("getCommand").invoke(dragon)).intValue();
            int next = Math.floorMod(current + 1, 3);
            dragon.getClass().getMethod("setCommand", int.class).invoke(dragon, next);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.dragon_command." + next), true);
        } catch (ReflectiveOperationException failure) {
            TinkersNewlife.LOGGER.debug("Cannot change Ice and Fire dragon command", failure);
        }
    }

    // ---------- 回收（增强版：只能回收活龙） ----------
    public static void recallWarDragon(net.minecraft.server.level.ServerPlayer player, TamableAnimal dragon) {
        if (warRoster(player) == null || dragon.isVehicle() || dragon.isPassenger()) return;
        var tool = getTool(player);
        if (tool != null && checkCooldown(player.getMainHandItem(), player)) {
            tryRecycle(player, tool, dragon);
            tool.updateStack(player.getMainHandItem());
        }
    }

    private static void tryRecycle(Player player, ToolStack tool, Entity target) {
        if (!isDragon(target)) {
            return;
        }

        // 检查是否是活龙
        if (!isAliveDragon(target)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.dead_dragon_cannot_recycle"), true);
            return;
        }

        // 必须是驯服且主人为当前玩家
        if (!(target instanceof TamableAnimal tamable) || !tamable.isTame()) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_your_dragon"), true);
            return;
        }
        if (!player.getUUID().equals(tamable.getOwnerUUID())) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_your_dragon"), true);
            return;
        }

        ListTag slots = getSlots(tool);
        UUID uuid = target.getUUID();
        boolean found = false;

        for (int i = 0; i < slots.size(); i++) {
            CompoundTag entry = slots.getCompound(i);
            if (entry.getString("uuid").equals(uuid.toString())) {
                CompoundTag data = new CompoundTag();
                com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.stripReleaseBonus(tamable);
                DragonSpirit.mark(tamable, false);
                DragonViltrum.mark(tamable, false);
                DragonFrenzy.mark(tamable, false);
                target.save(data);
                if (com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.isWinner(tamable)) entry.putBoolean(PIT_WINNER, true);
                entry.put("data", data);
                entry.putInt("state", 1);
                if (target.hasCustomName()) {
                    entry.putString("name", target.getCustomName().getString());
                }
                entry.putString("dimension", target.level().dimension().location().toString());
                entry.putInt("posX", target.getBlockX());
                entry.putInt("posY", target.getBlockY());
                entry.putInt("posZ", target.getBlockZ());
                found = true;
                break;
            }
        }

        if (!found) {
            int level = ToolHelper.getActiveModifierLevel(tool, DRAGON_STAFF_ID);
            int maxSlots = DragonStaffTrait.getMaxSlots(level);
            if (slots.size() >= maxSlots) {
                player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.slots_full"), true);
                return;
            }
            CompoundTag entry = new CompoundTag();
            CompoundTag data = new CompoundTag();
            com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.stripReleaseBonus(tamable);
            DragonSpirit.mark(tamable, false);
            DragonViltrum.mark(tamable, false);
            DragonFrenzy.mark(tamable, false);
            target.save(data);
            if (com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.isWinner(tamable)) entry.putBoolean(PIT_WINNER, true);
            entry.putString("uuid", uuid.toString());
            entry.putString("type", ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString());
            String name = target.hasCustomName() ? target.getCustomName().getString() : target.getName().getString();
            entry.putString("name", name);
            entry.put("data", data);
            entry.putInt("state", 1);
            entry.putString("dimension", target.level().dimension().location().toString());
            entry.putInt("posX", target.getBlockX());
            entry.putInt("posY", target.getBlockY());
            entry.putInt("posZ", target.getBlockZ());
            slots.add(entry);
        }

        target.remove(Entity.RemovalReason.DISCARDED);
        setSlots(player, tool, slots);
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.recycled"), true);
    }

    // ---------- 释放 ----------
    private static boolean safeWarDeployment(ServerLevel level, Entity dragon) {
        var box = dragon.getBoundingBox();
        return box.minY >= level.getMinBuildHeight() && box.maxY <= level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(box)
                && level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                        BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                && level.noCollision(dragon, box) && !level.containsAnyLiquid(box)
                && level.getBlockCollisions(dragon, box.move(0, -.1, 0)).iterator().hasNext();
    }

    private static final ModifierId WAR = new ModifierId(TinkersNewlife.prefix("dragon_war"));

    public static ListTag warRoster(net.minecraft.server.level.ServerPlayer player) {
        var tool = getTool(player);
        return tool != null && isOwner(tool, player) && !pitLocked(tool)
                && !TAMING_TASKS.containsKey(player.getUUID()) && ToolHelper.getActiveModifierLevel(tool, WAR) > 0
                ? getSlots(tool) : null;
    }

    public static void releaseSelected(net.minecraft.server.level.ServerPlayer player, UUID selected) {
        releaseSelected(player, selected, null);
    }

    public static void releaseSelected(net.minecraft.server.level.ServerPlayer player, UUID selected, BlockPos destination) {
        if (destination != null && warRoster(player) == null) return;
        ToolStack tool = getTool(player);
        if (tool == null || pitLocked(tool) || !isOwner(tool, player) || TAMING_TASKS.containsKey(player.getUUID()) || !checkCooldown(player.getMainHandItem(),player)) return;
        ListTag slots = getSlots(tool);
        for (int i = slots.size() - 1; i >= 0; i--) {
            CompoundTag entry = slots.getCompound(i);
            if (canSupport(entry) && selected.toString().equals(entry.getString("uuid"))) {
                CompoundTag data = entry.getCompound("data");
                ResourceLocation typeId = new ResourceLocation(entry.getString("type"));
                EntityType<?> type = com.mofengbaizhi.tinkersnewlife.util.SafeRegistry.entityType(typeId);
                if (type == null) {
                    player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.release_fail_type"), true);
                    return;
                }
                Entity dragon = type.create(player.level());
                if (dragon == null) {
                    player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.release_fail_create"), true);
                    return;
                }
                try {
                    dragon.load(data);
                } catch (Exception e) {
                    player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.release_fail_data"), true);
                    return;
                }
                boolean wasFull = dragon instanceof LivingEntity living && living.getHealth() >= living.getMaxHealth() - .001f;
                String slotName = entry.getString("name");
                if (!slotName.isEmpty() && !dragon.hasCustomName()) {
                    dragon.setCustomName(Component.literal(slotName));
                }
                BlockPos spawnPos = destination != null ? destination : player.blockPosition().relative(player.getDirection(), 3);
                if (destination == null && !player.level().getBlockState(spawnPos).isAir() && !player.level().getBlockState(spawnPos).canBeReplaced()) {
                    spawnPos = player.blockPosition().above(2);
                }
                dragon.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                if (dragon instanceof LivingEntity livingDragon) {
                    com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.stripReleaseBonus(livingDragon);
                     float healthRatio = livingDragon.getHealth() / Math.max(1f, livingDragon.getMaxHealth());
                        com.mofengbaizhi.tinkersnewlife.content.modifier.DragonHusbandryModifier.applyOnRelease(tool, livingDragon, slots.size());
                        com.mofengbaizhi.tinkersnewlife.content.modifier.DragonKinshipModifier.applyOnRelease(tool, player, livingDragon);
                    com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.applyOnRelease(livingDragon, entry.getBoolean(PIT_WINNER));
                     livingDragon.setHealth(Math.min(1f, healthRatio) * livingDragon.getMaxHealth());
                }
                if (destination != null && !safeWarDeployment(player.serverLevel(), dragon)) return;
                if (!player.level().addFreshEntity(dragon)) {
                    player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.release_fail_spawn"), true);
                    return;
                }
                if (dragon instanceof LivingEntity livingDragon) {
                    DragonGrowthNest.markReleased(livingDragon, ToolHelper.getActiveModifierLevel(tool, GROWTH));
                    DragonFrenzy.mark(livingDragon, ToolHelper.getActiveModifierLevel(tool, FRENZY) > 0);
                    DragonViltrum.mark(livingDragon, ToolHelper.getActiveModifierLevel(tool, DragonViltrum.ID) > 0);
                    DragonSpirit.mark(livingDragon, ToolHelper.getActiveModifierLevel(tool, new ModifierId(TinkersNewlife.prefix("dragon_spirit"))) > 0);
                    if (ToolHelper.getActiveModifierLevel(tool, NEST) > 0) DragonGrowthNest.applyNestRelease(livingDragon, wasFull);
                }
                entry.putInt("state", 0);
                entry.putString("dimension", player.level().dimension().location().toString());
                entry.putInt("posX", dragon.getBlockX());
                entry.putInt("posY", dragon.getBlockY());
                entry.putInt("posZ", dragon.getBlockZ());
                setSlots(player, tool, slots);
                tool.updateStack(player.getMainHandItem());
                player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.released"), true);
                spawnReleaseParticles(dragon);
                return;
            }
        }
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.no_stored_dragon"), true);
    }

    // ---------- 驯服技能 ----------
    public static void executeSkill(Player player) {
        if (player.level().isClientSide) return;
        ToolStack tool = getTool(player);
        if (tool == null) return;
        if (!com.mofengbaizhi.tinkersnewlife.content.unlock.FlyingSwordUnlock.canTameDragons(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.dragon_staff.manual_required"), true);
            return;
        }
        if (!isOwner(tool, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_owner"), true);
            return;
        }
        if (pitLocked(tool) || TAMING_TASKS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.taming_in_progress"), true);
            return;
        }
        if (player.experienceLevel < SKILL_XP_COST) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.no_xp", SKILL_XP_COST), true);
            return;
        }
        int level = ToolHelper.getActiveModifierLevel(tool, DRAGON_STAFF_ID);
        int maxSlots = DragonStaffTrait.getMaxSlots(level);
        if (getSlots(tool).size() >= maxSlots) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.slots_full"), true);
            return;
        }
        List<Entity> dragons = findWildDragons(player);
        if (dragons.isEmpty()) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.no_dragon"), true);
            return;
        }
        if (!checkCooldown(player.getMainHandItem(), player)) return;
        Entity target = dragons.get(player.level().random.nextInt(dragons.size()));
        player.giveExperienceLevels(-SKILL_XP_COST);
        TamingTask task = new TamingTask(player, tool, target, TAMING_DURATION_TICKS);
        TAMING_TASKS.put(player.getUUID(), task);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && target instanceof LivingEntity dragon) {
            com.mofengbaizhi.tinkersnewlife.network.DragonTameVisual.start(sp, dragon);
        }
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.taming_start"), true);
    }

    // ---------- Tick 处理 ----------
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID, TamingTask>> it = TAMING_TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TamingTask> e = it.next();
            TamingTask task = e.getValue();
            if (!task.player.isAlive() || !task.target.isAlive() || task.player.level() != task.level
                    || task.target.level() != task.level || task.player.getMainHandItem() != task.staff
                    || task.tool.isBroken() || hasOwner(task.target)) {
                finishVisual(task, false);
                it.remove();
                continue;
            }
            task.remainingTicks--;
            holdStill(task.player, task.playerAnchor);
            holdStill(task.target, task.dragonAnchor);
            if (task.remainingTicks <= 0) {
                boolean success = task.target instanceof LivingEntity dragon
                        && task.player.level().random.nextDouble() < tamingChance(dragon);
                if (success) success = handleSuccess(task.player, task.tool, task.target);
                else handleFailure(task.player, task.target);
                finishVisual(task, success);
                it.remove();
            }
        }
    }

    private static void holdStill(Entity entity, Vec3 anchor) {
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof net.minecraft.world.entity.Mob mob) mob.getNavigation().stop();
        if (entity.position().distanceToSqr(anchor) > 1.0E-6) {
            if (entity instanceof net.minecraft.server.level.ServerPlayer sp) sp.connection.teleport(anchor.x, anchor.y, anchor.z, sp.getYRot(), sp.getXRot());
            else entity.setPos(anchor.x, anchor.y, anchor.z);
        }
    }

    private static void finishVisual(TamingTask task, boolean success) {
        if (task.player instanceof net.minecraft.server.level.ServerPlayer sp && task.target instanceof LivingEntity dragon) {
            com.mofengbaizhi.tinkersnewlife.network.DragonTameVisual.end(sp, dragon, success);
        }
    }

    // ---------- 辅助方法 ----------
    private static List<Entity> findWildDragons(Player player) {
        AABB aabb = new AABB(player.blockPosition()).inflate(SEARCH_RADIUS);
        return player.level().getEntitiesOfClass(Entity.class, aabb,
                e -> isDragon(e) && isAliveDragon(e) && !hasOwner(e) && TAMING_TASKS.values().stream().noneMatch(task -> task.target == e) && e.isAlive() && !e.isRemoved());
    }

    private static boolean handleSuccess(Player player, ToolStack tool, Entity target) {
        if (!setOwner(target, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.taming_fail_owner"), true);
            spawnAngryParticles(target);
            applyEffect(target, MobEffects.DAMAGE_BOOST, 30, 1);
            return false;
        }
        String dragonName = target.hasCustomName() ? target.getCustomName().getString() : generateDragonName(target.level());
        ListTag slots = getSlots(tool);
        CompoundTag entry = new CompoundTag();
        entry.putString("uuid", target.getUUID().toString());
        entry.putString("type", ForgeRegistries.ENTITY_TYPES.getKey(target.getType()).toString());
        entry.putString("name", dragonName);
        CompoundTag data = new CompoundTag();
        target.save(data);
        entry.put("data", data);
        entry.putInt("state", 0);
        entry.putString("dimension", target.level().dimension().location().toString());
        entry.putInt("posX", target.getBlockX());
        entry.putInt("posY", target.getBlockY());
        entry.putInt("posZ", target.getBlockZ());
        slots.add(entry);
        setSlots(player, tool, slots);
        if (target instanceof LivingEntity dragon) DragonGrowthNest.markReleased(dragon, ToolHelper.getActiveModifierLevel(tool, GROWTH));
        tool.updateStack(player.getMainHandItem());
        if (!target.hasCustomName()) target.setCustomName(Component.literal(dragonName));
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.success"), true);
        spawnHeartParticles(target);
        applyEffect(target, MobEffects.REGENERATION, 10, 1);
        return true;
    }

    private static void handleFailure(Player player, Entity target) {
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.failed"), true);
        spawnAngryParticles(target);
        applyEffect(target, MobEffects.DAMAGE_BOOST, 30, 1);
    }

    // ---------- 玩家登录/切换维度 ----------
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        var task = TAMING_TASKS.remove(event.getEntity().getUUID());
        if (task != null) finishVisual(task, false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            checkAndCleanSlots(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide) {
            checkAndCleanSlots(event.getEntity());
        }
    }

    private static final ModifierId BROTHERHOOD = new ModifierId(TinkersNewlife.prefix("dragon_brotherhood"));
    private static final ModifierId NEST = new ModifierId(TinkersNewlife.prefix("dragon_nest"));
    private static final ModifierId FRENZY = new ModifierId(TinkersNewlife.prefix("dragon_frenzy"));
    private static final ModifierId GROWTH = new ModifierId(TinkersNewlife.prefix("dragon_growth"));
    private static final String REVIVE_AT = "TnlDragonReviveAt";
    private record Brotherhood(Player owner, ItemStack stack, ToolStack tool, ListTag roster, CompoundTag entry) {}

    private static Brotherhood brotherhood(Entity entity) {
        if (!(entity instanceof TamableAnimal dragon) || !isDragon(dragon) || dragon.getOwnerUUID() == null
                || !(entity.level() instanceof ServerLevel level)) return null;
        var owner = level.getServer().getPlayerList().getPlayer(dragon.getOwnerUUID());
        if (owner == null) return null;
        var inventory = new java.util.ArrayList<>(owner.getInventory().items);
        inventory.add(owner.getOffhandItem());
        for (var stack : inventory) {
            if (!(stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.DragonStaffItem)) continue;
            var tool = ToolHelper.getToolStack(stack);
            if (tool == null || !isOwner(tool, owner) || ToolHelper.getActiveModifierLevel(tool, BROTHERHOOD) <= 0) continue;
            var roster = getSlots(tool);
            for (int i = 0; i < roster.size(); i++) {
                var entry = roster.getCompound(i);
                if (entry.getInt("state") == 0 && entry.getString("uuid").equals(entity.getStringUUID())) {
                    return new Brotherhood(owner, stack, tool, roster, entry);
                }
            }
        }
        return null;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void shareDragonDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getAmount() > 0) || !Float.isFinite(event.getAmount()) || event.getEntity().level().isClientSide) return;
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()) {
            var bond = brotherhood(attacker);
            if (bond != null) {
                double attack = 0;
                int count = 0;
                for (int i = 0; i < bond.roster.size(); i++) {
                    var entry = bond.roster.getCompound(i);
                    if (!canSupport(entry)) continue;
                    attack += stat(entry.getCompound("data"), net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    count++;
                }
                if (count > 0) event.setAmount(event.getAmount() + (float)(attack / count * .5));
            }
        }
        if (event.getSource().getEntity() == null || event.getSource().getEntity() == event.getEntity()) return;
        var bond = brotherhood(event.getEntity());
        if (bond == null || pitLocked(bond.tool)) return;
        java.util.List<CompoundTag> supporters = new java.util.ArrayList<>();
        for (int i = 0; i < bond.roster.size(); i++) if (canSupport(bond.roster.getCompound(i))) supporters.add(bond.roster.getCompound(i));
        if (supporters.isEmpty()) return;
        float share = event.getAmount() * .60f / supporters.size();
        float absorbed = 0;
        long time = bond.owner.level().getGameTime();
        for (var entry : supporters) {
            var data = entry.getCompound("data");
            float hp = data.getFloat("Health");
            absorbed += Math.min(hp, share);
            data.putFloat("Health", Math.max(0, hp - share));
            if (hp <= share) entry.putLong(REVIVE_AT, time + 72000);
        }
        event.setAmount(Math.max(0, event.getAmount() - absorbed));
        bond.tool.getPersistentData().put(KEY_SLOTS, bond.roster);
        bond.tool.updateStack(bond.stack);
    }

    @SubscribeEvent public static void tickStoredDragons(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof net.minecraft.server.level.ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        long now = player.level().getGameTime();
        var inventory = new java.util.ArrayList<>(player.getInventory().items);
        inventory.add(player.getOffhandItem());
        for (var stack : inventory) {
            if (!(stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.DragonStaffItem)) continue;
            var tool = ToolHelper.getToolStack(stack);
            if (tool == null || !isOwner(tool, player) || pitLocked(tool)) continue;
            var roster = getSlots(tool);
            boolean changed = false;
            for (int i = 0; i < roster.size(); i++) {
                var entry = roster.getCompound(i);
                if (entry.getInt("state") != 1) continue;
                var data = entry.getCompound("data");
                float max = (float)stat(data, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (entry.contains(REVIVE_AT)) {
                    if (now < entry.getLong(REVIVE_AT)) continue;
                    entry.remove(REVIVE_AT);
                    data.putFloat("Health", max);
                    changed = true;
                } else if (ToolHelper.getActiveModifierLevel(tool, NEST) > 0 && canSupport(entry) && data.getFloat("Health") < max) {
                    changed |= DragonGrowthNest.healStored(data, max, 20);
                }
            }
            if (changed) {
                tool.getPersistentData().put(KEY_SLOTS, roster);
                tool.updateStack(stack);
                com.mofengbaizhi.tinkersnewlife.network.dragon.DragonCards.syncStoredHealth(player, stack, roster);
            }
        }
    }

    private static boolean canSupport(CompoundTag entry) {
        return entry.getInt("state") == 1 && !entry.contains(REVIVE_AT) && entry.getCompound("data").getFloat("Health") > 0;
    }

    private static boolean recallFallen(TamableAnimal dragon) {
        var bond = brotherhood(dragon);
        if (bond == null) return false;
        com.mofengbaizhi.tinkersnewlife.content.modifier.DragonPitModifier.stripReleaseBonus(dragon);
        CompoundTag data = new CompoundTag();
        DragonSpirit.mark(dragon, false);
        DragonViltrum.mark(dragon, false);
        DragonFrenzy.mark(dragon, false);
        dragon.saveWithoutId(data);
        data.putString("id", bond.entry.getString("type"));
        data.putShort("DeathTime", (short)0);
        data.putBoolean("ModelDead", false);
        data.putInt("DeathStage", 0);
        data.putFloat("Health", 0);
        bond.entry.put("data", data);
        bond.entry.putInt("state", 1);
        bond.entry.putLong(REVIVE_AT, dragon.level().getGameTime() + 72000);
        // Roster changed during combat; abort a pending pit rather than admit an unconfirmed entrant.
        bond.tool.getPersistentData().remove(PIT_END);
        setSlots(bond.owner, bond.tool, bond.roster);
        bond.tool.updateStack(bond.stack);
        dragon.discard();
        return true;
    }

    // ---------- 死亡清理 ----------
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!isDragon(entity)) return;
        if (!(entity instanceof TamableAnimal tamable)) return;
        if (!tamable.isTame()) return;
        if (recallFallen(tamable)) { event.setCanceled(true); return; }
        UUID ownerId = tamable.getOwnerUUID();
        if (ownerId == null) return;
        Player player = entity.level().getPlayerByUUID(ownerId);
        if (player == null) return;
        clearDragonFromSlots(player, entity.getUUID());
    }

    // ---------- 安全清理 ----------
    private static void checkAndCleanSlots(Player player) {
        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        ResourceLocation currentDim = level.dimension().location();

        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof IModifiable)) continue;
            // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || ToolHelper.getActiveModifierLevel(tool, DRAGON_STAFF_ID) <= 0) continue;
            ListTag slots = getSlots(tool);
            boolean changed = false;
            for (int i = slots.size() - 1; i >= 0; i--) {
                CompoundTag entry = slots.getCompound(i);
                if (entry.getInt("state") != 0) continue;
                String dimStr = entry.getString("dimension");
                if (dimStr.isEmpty() || !dimStr.equals(currentDim.toString())) continue;
                int x = entry.getInt("posX");
                int y = entry.getInt("posY");
                int z = entry.getInt("posZ");
                BlockPos pos = new BlockPos(x, y, z);
                if (!serverLevel.isLoaded(pos)) continue;
                UUID uuid;
                try {
                    uuid = UUID.fromString(entry.getString("uuid"));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Entity entity = serverLevel.getEntity(uuid);
                // A missing UUID may have moved to an unloaded chunk; keep its last card.
                if (entity != null && !entity.isAlive()) {
                    slots.remove(i);
                    changed = true;
                }
            }
            if (changed) {
                setSlots(player, tool, slots);
                tool.updateStack(stack);
            }
        }
    }

    private static void clearDragonFromSlots(Player player, UUID dragonUuid) {
        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof IModifiable)) continue;
            // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || ToolHelper.getActiveModifierLevel(tool, DRAGON_STAFF_ID) <= 0) continue;
            ListTag slots = getSlots(tool);
            boolean changed = false;
            for (int i = slots.size() - 1; i >= 0; i--) {
                CompoundTag entry = slots.getCompound(i);
                if (entry.getInt("state") == 0) {
                    if (entry.getString("uuid").equals(dragonUuid.toString())) {
                        slots.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                setSlots(player, tool, slots);
                tool.updateStack(stack);
            }
        }
    }

    // ---------- 名字生成 ----------
    private static String generateDragonName(Level level) {
        List<String> prefixes = getListFromLanguage(level, "dragonstaff.name.prefix");
        List<String> roots = getListFromLanguage(level, "dragonstaff.name.root");
        List<String> suffixes = getListFromLanguage(level, "dragonstaff.name.suffix");
        List<String> appearances = getListFromLanguage(level, "dragonstaff.name.appearance");
        if (prefixes.isEmpty() || roots.isEmpty() || suffixes.isEmpty() || appearances.isEmpty()) {
            return "Dragon";
        }
        RandomSource random = level.random;
        return prefixes.get(random.nextInt(prefixes.size())) + "·" +
                roots.get(random.nextInt(roots.size())) +
                suffixes.get(random.nextInt(suffixes.size())) + "·" +
                appearances.get(random.nextInt(appearances.size()));
    }

    private static List<String> getListFromLanguage(Level level, String key) {
        String raw = Component.translatable(key).getString();
        if (raw == null || raw.isEmpty()) return List.of();
        String[] parts = raw.split("[,，、|\\s]+");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        return list;
    }

    // ---------- 粒子效果 ----------
    private static void applyEffect(Entity target, net.minecraft.world.effect.MobEffect effect, int seconds, int amplifier) {
        if (target instanceof TamableAnimal) {
            ((TamableAnimal) target).addEffect(new MobEffectInstance(effect, seconds * 20, amplifier));
        }
    }

    private static void spawnHeartParticles(Entity target) {
        if (!(target.level() instanceof ServerLevel server)) return;
        Vec3 head = target.position().add(0, target.getBbHeight() + 0.5, 0);
        for (int i = 0; i < HEART_PARTICLE_COUNT; i++) {
            double dx = (target.level().random.nextDouble() - 0.5) * 1.2;
            double dy = (target.level().random.nextDouble() - 0.5) * 1.2;
            double dz = (target.level().random.nextDouble() - 0.5) * 1.2;
            server.sendParticles(ParticleTypes.HEART, head.x + dx, head.y + dy, head.z + dz, 0, 0.1, 0.1, 0.1, 0);
        }
    }

    private static void spawnAngryParticles(Entity target) {
        if (!(target.level() instanceof ServerLevel server)) return;
        Vec3 head = target.position().add(0, target.getBbHeight() + 0.5, 0);
        for (int i = 0; i < ANGRY_PARTICLE_COUNT; i++) {
            double dx = (target.level().random.nextDouble() - 0.5) * 1.2;
            double dy = (target.level().random.nextDouble() - 0.5) * 1.2;
            double dz = (target.level().random.nextDouble() - 0.5) * 1.2;
            server.sendParticles(ParticleTypes.ANGRY_VILLAGER, head.x + dx, head.y + dy, head.z + dz, 0, 0.1, 0.1, 0.1, 0);
        }
        for (int i = 0; i < 10; i++) {
            server.sendParticles(ParticleTypes.SMOKE, head.x, head.y + 0.5, head.z, 0, 0.3, 0.3, 0.3, 0);
        }
    }

    private static void spawnReleaseParticles(Entity entity) {
        if (!(entity.level() instanceof ServerLevel server)) return;
        Vec3 pos = entity.position().add(0, entity.getBbHeight() / 2, 0);
        for (int i = 0; i < 20; i++) {
            double dx = (entity.level().random.nextDouble() - 0.5) * 2;
            double dy = (entity.level().random.nextDouble() - 0.5) * 2;
            double dz = (entity.level().random.nextDouble() - 0.5) * 2;
            server.sendParticles(ParticleTypes.FIREWORK, pos.x + dx, pos.y + dy, pos.z + dz, 0, 0.1, 0.1, 0.1, 0.1);
        }
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y, pos.z, 20, 0.5, 0.5, 0.5, 0.1);
    }

    // ---------- 龙识别与死亡状态 ----------
    private static boolean isDragon(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id != null && "iceandfire".equals(id.getNamespace()) &&
                id.getPath().contains("dragon") && !id.getPath().contains("multipart");
    }

    /**
     * 通过反射获取龙的死亡阶段（deathStage）
     * 0 = 活龙，>0 为死亡阶段
     */
    private static boolean isModelDead(Entity dragon) {
        try {
            Method method = dragon.getClass().getMethod("isModelDead");
            return (boolean) method.invoke(dragon);
        } catch (Exception e) {
            // 反射失败，保守处理：视为活龙（不排除）
            return false;
        }
    }

    /**
     * 判断龙是否为活龙（deathStage == 0）
     */
    private static boolean isAliveDragon(Entity entity) {
        if (!isDragon(entity)) return false;
        return !isModelDead(entity);
    }

    

    private static boolean hasOwner(Entity entity) {
        if (entity instanceof TamableAnimal tamable) {
            return tamable.isTame() && tamable.getOwnerUUID() != null;
        }
        // ⭐ 非 TamableAnimal 的龙（如冰火传说 multipart 外的其他实体）：
        // 通过持久数据判断主人，避免整个实体 NBT 序列化（昂贵）。
        var persistentData = entity.getPersistentData();
        return persistentData.contains("Owner") || persistentData.contains("ownerUUID");
    }

    private static boolean setOwner(Entity entity, Player player) {
        if (!(entity instanceof TamableAnimal tamable)) return false;
        try {
            tamable.setTame(true);
            tamable.setOwnerUUID(player.getUUID());
            tamable.setOrderedToSit(false);
            trySetOwnerViaReflection(entity, player);
            boolean ok = tamable.isTame() && tamable.getOwnerUUID() != null;
            // ⭐ 成就「驯龙者」（§626）：收服成功即发放（只对玩家发放 ✓ 仆从不算 ✗）
            if (ok && player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.mofengbaizhi.tinkersnewlife.content.curse.AchievementHandler.onDragonTamed(sp);
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private static void trySetOwnerViaReflection(Entity entity, Player player) {
        try {
            Method m = entity.getClass().getMethod("setOwnerId", UUID.class);
            m.invoke(entity, player.getUUID());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
        try {
            Method m = entity.getClass().getMethod("setOwner", Player.class);
            m.invoke(entity, player);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
    }
}