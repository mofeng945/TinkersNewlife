package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.handler.PixieTreasureOutline;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.mining.BlockBreakModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.context.ToolHarvestContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class PixieTreasureModifier extends Modifier implements MeleeHitModifierHook, ProjectileLaunchModifierHook, BlockBreakModifierHook {
    private static final ResourceLocation PIXIE = new ResourceLocation("iceandfire", "pixie");
    private static final ResourceLocation CHARGED_RANK = TinkersNewlife.prefix("pixie_treasure_charged_rank");
    private static final List<ResourceLocation> TABLES = List.of("gameplay/fishing/treasure", "chests/ancient_city",
            "chests/buried_treasure", "chests/nether_bridge", "chests/bastion_treasure", "chests/end_city_treasure")
            .stream().map(ResourceLocation::new).toList();

    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        builder.addHook(this, ModifierHooks.MELEE_HIT);
        builder.addHook(this, ModifierHooks.PROJECTILE_LAUNCH);
        builder.addHook(this, ModifierHooks.PROJECTILE_SHOT);
        builder.addHook(this, ModifierHooks.PROJECTILE_THROWN);
        builder.addHook(this, ModifierHooks.BLOCK_BREAK);
    }

    static boolean isPixie(Entity entity) { return PIXIE.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())); }

    @Override public void afterMeleeHit(IToolStackView tool, ModifierEntry entry, ToolAttackContext context, float damageDealt) {
        // TCon's defaultCooldown() saved this value before performAttack; isFullyCharged() is only >90%.
        if (!context.isProjectile() && damageDealt > 0 && context.getCooldown() >= 1f
                && context.getAttacker() instanceof ServerPlayer player && context.getLivingTarget() != null) {
            reward(player, context.getLivingTarget(), entry.getLevel());
        }
    }

    @Override public void onProjectileLaunch(IToolStackView tool, ModifierEntry entry, LivingEntity shooter, Projectile shot,
                                              @Nullable AbstractArrow arrow, ModDataNBT data, boolean primary) {
        if (shooter instanceof ServerPlayer player && !tool.isBroken() && player.getAttackStrengthScale(0.5f) >= 1f) {
            // Launcher and ammunition invoke distinct hooks on the same shot; combine both ranks once.
            data.putInt(CHARGED_RANK, data.getInt(CHARGED_RANK) + entry.getLevel());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileDamage(LivingDamageEvent event) {
        if (event.getAmount() > 0 && event.getSource().getEntity() instanceof ServerPlayer player
                && event.getSource().getDirectEntity() instanceof Projectile shot) {
            int rank = shot.getCapability(PersistentDataCapability.CAPABILITY).map(data -> data.getInt(CHARGED_RANK)).orElse(0);
            if (rank > 0) reward(player, event.getEntity(), rank);
        }
    }

    private static void reward(ServerPlayer player, LivingEntity target, int rank) {
        if (rank <= 0 || target == player || !IntegrationLoader.isIceAndFire()) return;
        boolean pixie = isPixie(target);
        if (player.getRandom().nextFloat() >= rank * (pixie ? 0.025f : 0.05f)) return;
        if (pixie) {
            ServerLevel level = player.serverLevel();
            int index = player.getRandom().nextInt(TABLES.size());
            var table = level.getServer().getLootData().getLootTable(TABLES.get(index));
            var builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, target.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player).withLuck(player.getLuck());
            if (index == 0) builder.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD));
            var params = builder.create(index == 0 ? LootContextParamSets.FISHING : LootContextParamSets.CHEST);
            int rolls = 2 + player.getRandom().nextInt(3);
            for (int i = 0; i < rolls; i++) table.getRandomItems(params).forEach(target::spawnAtLocation);
        } else {
            var hand = target.getMainHandItem().isEmpty() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack held = target.getItemInHand(hand);
            if (!held.isEmpty()) {
                ItemStack stolen = held.copy();
                target.setItemInHand(hand, ItemStack.EMPTY);
                player.getInventory().add(stolen);
                if (!stolen.isEmpty()) {
                    ItemEntity drop = player.drop(stolen, false);
                    if (drop != null) { drop.setTarget(player.getUUID()); drop.setNoPickUpDelay(); }
                }
            }
            target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 2));
        }
    }

    @Override public void afterBlockBreak(IToolStackView tool, ModifierEntry entry, ToolHarvestContext context) {
        ServerPlayer player = context.getPlayer();
        if (player == null || context.isAOE() || !IntegrationLoader.isIceAndFire() || entry.getLevel() <= 0) return;
        ServerLevel level = context.getWorld();
        Vec3 center = player.position();
        double radius = entry.getLevel() * 5d;
        double squared = radius * radius;
        // Only chunks within the client's view can produce a visible result. getChunkNow never loads a chunk.
        int reach = Math.min((int)Math.ceil(radius / 16d) + 1, level.getServer().getPlayerList().getViewDistance() + 1);
        int cx = player.chunkPosition().x, cz = player.chunkPosition().z;
        List<Long> blocks = new ArrayList<>();
        for (int x = cx - reach; x <= cx + reach; x++) for (int z = cz - reach; z <= cz + reach; z++) {
            var chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk != null) for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                if (pos.distToCenterSqr(center) <= squared) blocks.add(pos.asLong());
            }
        }
        var ids = level.getEntities(player, new AABB(center, center).inflate(Math.min(radius, (reach + 1) * 16d)),
                entity -> entity.isAlive() && entity.distanceToSqr(center) <= squared).stream().map(Entity::getId).toList();
        for (int i = 0; i < Math.max(blocks.size(), ids.size()); i += Glow.BATCH) {
            var positions = blocks.subList(Math.min(i, blocks.size()), Math.min(i + Glow.BATCH, blocks.size()));
            var entities = ids.subList(Math.min(i, ids.size()), Math.min(i + Glow.BATCH, ids.size()));
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Glow(level.dimension().location(),
                    positions.stream().mapToLong(Long::longValue).toArray(), entities.stream().mapToInt(Integer::intValue).toArray()));
        }
    }

    public record Glow(ResourceLocation dimension, long[] positions, int[] entities) {
        private static final int BATCH = 1024;
        public Glow(FriendlyByteBuf buf) { this(buf.readResourceLocation(), buf.readLongArray(null, BATCH), buf.readVarIntArray(BATCH)); }
        public void encode(FriendlyByteBuf buf) { buf.writeResourceLocation(dimension); buf.writeLongArray(positions); buf.writeVarIntArray(entities); }
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PixieTreasureOutline.receive(this)));
            context.setPacketHandled(true);
        }
    }
}
