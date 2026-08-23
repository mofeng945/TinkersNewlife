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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
    private static final ResourceLocation KEY_MODE = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff_mode");
    private static final ResourceLocation KEY_SLOTS = new ResourceLocation(TinkersNewlife.MOD_ID, "dragon_staff_slots");
    private static final ResourceLocation KEY_LAST_INTERACT_TIME = new ResourceLocation(TinkersNewlife.MOD_ID, "last_interact_time");
    private static final ResourceLocation KEY_OWNER = new ResourceLocation(TinkersNewlife.MOD_ID, "staff_owner");

    // 常量
    private static final int SKILL_XP_COST = 200;
    private static final int SEARCH_RADIUS = 16;
    private static final double SUCCESS_CHANCE = 0.5;
    private static final int TAMING_DURATION_TICKS = 60;
    private static final int INTERACT_COOLDOWN_TICKS = 20;
    private static final int HEART_PARTICLE_COUNT = 30;
    private static final int ANGRY_PARTICLE_COUNT = 30;
    private static final int CHAIN_STEPS = 30;

    private static final Map<UUID, TamingTask> TAMING_TASKS = new ConcurrentHashMap<>();
    private static final ModifierId DRAGON_STAFF_ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, DragonStaffTrait.MODIFIER_ID));

    // ---------- 内部类 ----------
    private static class TamingTask {
        final Player player;
        final ToolStack tool;
        final Entity target;
        int remainingTicks;
        TamingTask(Player player, ToolStack tool, Entity target, int duration) {
            this.player = player;
            this.tool = tool;
            this.target = target;
            this.remainingTicks = duration;
        }
    }

    // ---------- 基础 NBT 操作 ----------
    private static int getMode(IToolStackView tool) {
        return tool.getPersistentData().getInt(KEY_MODE);
    }

    private static void toggleMode(IToolStackView tool) {
        tool.getPersistentData().putInt(KEY_MODE, getMode(tool) ^ 1);
    }

    private static ListTag getSlots(IToolStackView tool) {
        Tag tag = tool.getPersistentData().get(KEY_SLOTS);
        return tag instanceof ListTag ? (ListTag) tag : new ListTag();
    }

    private static void setSlots(IToolStackView tool, ListTag slots) {
        tool.getPersistentData().put(KEY_SLOTS, slots);
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
        if (tool.getModifierLevel(DRAGON_STAFF_ID) <= 0) return null;
        ensureOwner(tool, player);
        return tool;
    }

    // ---------- 冷却检查 ----------
    private static boolean checkCooldown(IToolStackView tool, Player player) {
        int lastTime = tool.getPersistentData().getInt(KEY_LAST_INTERACT_TIME);
        int currentTime = (int) (player.level().getGameTime());
        int elapsed = currentTime - lastTime;
        if (elapsed < 0 || elapsed >= INTERACT_COOLDOWN_TICKS) {
            tool.getPersistentData().putInt(KEY_LAST_INTERACT_TIME, currentTime);
            return true;
        } else {
            int remaining = INTERACT_COOLDOWN_TICKS - elapsed;
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.cooldown", remaining), true);
            return false;
        }
    }

    // ---------- 事件处理 ----------
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.isShiftKeyDown()) return;
        ToolStack tool = getTool(player);
        if (tool == null) return;
        if (!isOwner(tool, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_owner"), true);
            return;
        }
        toggleMode(tool);
        player.displayClientMessage(
                Component.translatable("modifier.tinkersnewlife.dragon_staff.mode_switched",
                        getMode(tool) == 0 ? Component.translatable("modifier.tinkersnewlife.dragon_staff.mode.recycle") : Component.translatable("modifier.tinkersnewlife.dragon_staff.mode.release")),
                true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ToolStack tool = getTool(player);
        if (tool == null) return;
        if (!isOwner(tool, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_owner"), true);
            return;
        }
        if (getMode(tool) == 0) {
            if (!checkCooldown(tool, player)) return;
            tryRecycle(player, tool, event.getTarget());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ToolStack tool = getTool(player);
        if (tool == null) return;
        if (!isOwner(tool, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_owner"), true);
            return;
        }
        int mode = getMode(tool);
        if (mode == 1) {
            if (!checkCooldown(tool, player)) return;
            tryRelease(player, tool);
            event.setCanceled(true);
        } else {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.wrong_mode_release"), true);
        }
    }

    // ---------- 回收（增强版：只能回收活龙） ----------
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
                target.save(data);
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
            int level = tool.getModifierLevel(DRAGON_STAFF_ID);
            int maxSlots = DragonStaffTrait.getMaxSlots(level);
            if (slots.size() >= maxSlots) {
                player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.slots_full"), true);
                return;
            }
            CompoundTag data = new CompoundTag();
            target.save(data);
            CompoundTag entry = new CompoundTag();
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
        setSlots(tool, slots);
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.recycled"), true);
    }

    // ---------- 释放 ----------
    private static void tryRelease(Player player, ToolStack tool) {
        ListTag slots = getSlots(tool);
        for (int i = slots.size() - 1; i >= 0; i--) {
            CompoundTag entry = slots.getCompound(i);
            if (entry.getInt("state") == 1) {
                CompoundTag data = entry.getCompound("data");
                ResourceLocation typeId = new ResourceLocation(entry.getString("type"));
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
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
                String slotName = entry.getString("name");
                if (!slotName.isEmpty() && !dragon.hasCustomName()) {
                    dragon.setCustomName(Component.literal(slotName));
                }
                BlockPos spawnPos = player.blockPosition().relative(player.getDirection(), 3);
                if (!player.level().getBlockState(spawnPos).isAir() && !player.level().getBlockState(spawnPos).canBeReplaced()) {
                    spawnPos = player.blockPosition().above(2);
                }
                dragon.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                if (!player.level().addFreshEntity(dragon)) {
                    player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.release_fail_spawn"), true);
                    return;
                }
                entry.putInt("state", 0);
                entry.putString("dimension", player.level().dimension().location().toString());
                entry.putInt("posX", dragon.getBlockX());
                entry.putInt("posY", dragon.getBlockY());
                entry.putInt("posZ", dragon.getBlockZ());
                setSlots(tool, slots);
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
        if (tool == null) {
            return;
        }
        if (!isOwner(tool, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.not_owner"), true);
            return;
        }
        if (TAMING_TASKS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.taming_in_progress"), true);
            return;
        }
        if (player.experienceLevel < SKILL_XP_COST) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.no_xp", SKILL_XP_COST), true);
            return;
        }
        int level = tool.getModifierLevel(DRAGON_STAFF_ID);
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
        Entity target = dragons.get(player.level().random.nextInt(dragons.size()));
        player.giveExperienceLevels(-SKILL_XP_COST);
        TamingTask task = new TamingTask(player, tool, target, TAMING_DURATION_TICKS);
        TAMING_TASKS.put(player.getUUID(), task);
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
            if (!task.player.isAlive() || !task.target.isAlive()) {
                it.remove();
                continue;
            }
            task.remainingTicks--;
            if (task.remainingTicks % 5 == 0) {
                spawnConnectionParticles(task.player.level(), task.player.position(), task.target.position());
            }
            if (task.remainingTicks <= 0) {
                boolean success = task.player.level().random.nextDouble() <= SUCCESS_CHANCE;
                if (success) handleSuccess(task.player, task.tool, task.target);
                else handleFailure(task.player, task.target);
                it.remove();
            }
        }
    }

    // ---------- 辅助方法 ----------
    private static List<Entity> findWildDragons(Player player) {
        AABB aabb = new AABB(player.blockPosition()).inflate(SEARCH_RADIUS);
        return player.level().getEntitiesOfClass(Entity.class, aabb,
                e -> isDragon(e) && isAliveDragon(e) && !hasOwner(e) && e.isAlive() && !e.isRemoved());
    }

    private static void handleSuccess(Player player, ToolStack tool, Entity target) {
        if (!setOwner(target, player)) {
            player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.taming_fail_owner"), true);
            spawnAngryParticles(target);
            applyEffect(target, MobEffects.DAMAGE_BOOST, 30, 1);
            return;
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
        setSlots(tool, slots);
        if (!target.hasCustomName()) target.setCustomName(Component.literal(dragonName));
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.success"), true);
        spawnHeartParticles(target);
        applyEffect(target, MobEffects.REGENERATION, 10, 1);
    }

    private static void handleFailure(Player player, Entity target) {
        player.displayClientMessage(Component.translatable("modifier.tinkersnewlife.dragon_staff.failed"), true);
        spawnAngryParticles(target);
        applyEffect(target, MobEffects.DAMAGE_BOOST, 30, 1);
    }

    // ---------- 玩家登录/切换维度 ----------
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TAMING_TASKS.remove(event.getEntity().getUUID());
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

    // ---------- 死亡清理 ----------
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (!isDragon(entity)) return;
        if (!(entity instanceof TamableAnimal tamable)) return;
        if (!tamable.isTame()) return;
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
            if (tool == null || tool.getModifierLevel(DRAGON_STAFF_ID) <= 0) continue;
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
                if (entity == null || !entity.isAlive()) {
                    slots.remove(i);
                    changed = true;
                }
            }
            if (changed) {
                setSlots(tool, slots);
                tool.updateStack(stack);
            }
        }
    }

    private static void clearDragonFromSlots(Player player, UUID dragonUuid) {
        for (ItemStack stack : player.getInventory().items) {
            if (!(stack.getItem() instanceof IModifiable)) continue;
            // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null || tool.getModifierLevel(DRAGON_STAFF_ID) <= 0) continue;
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
                setSlots(tool, slots);
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

    private static void spawnConnectionParticles(Level level, Vec3 from, Vec3 to) {
        if (!(level instanceof ServerLevel server)) return;
        for (int i = 0; i <= CHAIN_STEPS; i++) {
            double t = (double) i / CHAIN_STEPS;
            Vec3 pos = from.lerp(to, t);
            server.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, 0, 0, 0);
            if (i % 3 == 0) {
                server.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 0.5, pos.z, 1, 0, 0.1, 0, 0);
            }
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

    // ---------- 伤害加成 ----------
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        ToolStack tool = getTool(player);
        if (tool == null) return;
        if (!isOwner(tool, player)) return;

        ListTag slots = getSlots(tool);
        int storedCount = 0;
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag entry = slots.getCompound(i);
            if (entry.getInt("state") == 1) {
                storedCount++;
            }
        }

        float bonus = storedCount * 20.0f;
        if (bonus > 0) {
            event.setAmount(event.getAmount() + bonus);
            if (player.level() instanceof ServerLevel server) {
                Vec3 pos = event.getEntity().position();
                server.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y + 1, pos.z,
                        5, 0.3, 0.3, 0.3, 0.1);
            }
        }
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
        CompoundTag nbt = new CompoundTag();
        entity.save(nbt);
        return nbt.contains("Owner") || nbt.contains("ownerUUID");
    }

    private static boolean setOwner(Entity entity, Player player) {
        if (!(entity instanceof TamableAnimal tamable)) return false;
        try {
            tamable.setTame(true);
            tamable.setOwnerUUID(player.getUUID());
            tamable.setOrderedToSit(false);
            trySetOwnerViaReflection(entity, player);
            return tamable.isTame() && tamable.getOwnerUUID() != null;
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