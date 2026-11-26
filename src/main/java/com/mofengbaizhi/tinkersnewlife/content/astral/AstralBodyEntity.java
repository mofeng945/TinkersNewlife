package com.mofengbaizhi.tinkersnewlife.content.astral;

import java.util.EnumMap;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/** Visual anchor only: deliberately not a player, mob, inventory, loot source or chunk ticket. */
public final class AstralBodyEntity extends Entity implements IEntityAdditionalSpawnData {
    @Nullable private UUID ownerId;
    private String profileName = "";
    private byte visibleParts;
    private boolean leftHanded;
    private float headYaw;
    private final EnumMap<EquipmentSlot, ItemStack> appearance = new EnumMap<>(EquipmentSlot.class);

    public AstralBodyEntity(EntityType<? extends AstralBodyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);

    }

    /** Call once, before addFreshEntity. No inventory is moved or given to the substitute. */
    public void bind(ServerPlayer player) {
        if (ownerId != null || player.level() != level()) throw new IllegalStateException("Invalid astral body binding");
        ownerId = player.getUUID();
        profileName = player.getGameProfile().getName();
        moveTo(player.getX(), player.getY(), player.getZ(), player.yBodyRot, player.getXRot());
        headYaw = player.getYHeadRot();
        leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        for (PlayerModelPart part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) visibleParts |= (byte)part.getMask();
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) appearance.put(slot, player.getItemBySlot(slot).copy());
    }

    @Nullable public UUID ownerId() { return ownerId; }
    public String profileName() { return profileName; }
    public boolean shows(PlayerModelPart part) { return (visibleParts & part.getMask()) != 0; }
    public HumanoidArm mainArm() { return leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT; }
    public float headYaw() { return headYaw; }
    public ItemStack visualItem(EquipmentSlot slot) { return appearance.getOrDefault(slot, ItemStack.EMPTY).copy(); }

    @Override public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            ServerPlayer player = ownerId == null ? null : server.getServer().getPlayerList().getPlayer(ownerId);
            if (player == null || !player.isAlive() || player.level() != server || !AstralProjection.active(player)) discard();
        }
    }

    @Override public void writeSpawnData(FriendlyByteBuf buf) {
        buf.writeBoolean(ownerId != null);
        if (ownerId == null) return;
        buf.writeUUID(ownerId);
        buf.writeUtf(profileName, 16);
        buf.writeByte(visibleParts);
        buf.writeBoolean(leftHanded);
        buf.writeFloat(headYaw);
        for (EquipmentSlot slot : EquipmentSlot.values()) buf.writeItem(appearance.getOrDefault(slot, ItemStack.EMPTY));
    }
    @Override public void readSpawnData(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return;
        ownerId = buf.readUUID();
        profileName = buf.readUtf(16);
        visibleParts = buf.readByte();
        leftHanded = buf.readBoolean();
        headYaw = buf.readFloat();
        for (EquipmentSlot slot : EquipmentSlot.values()) appearance.put(slot, buf.readItem());
    }
    @Override protected void defineSynchedData() {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isAttackable() { return !isRemoved(); }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) {
        if (!(level() instanceof ServerLevel server) || isRemoved() || ownerId==null || !(amount>0)) return false;
        // Environmental ticks at the anchor are not attacks. Unowned explosions and projectiles still count.
        if(source.getEntity()==null && source.getDirectEntity()==null && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                && !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE))return false;
        var owner=server.getServer().getPlayerList().getPlayer(ownerId);if(owner==null || owner.level()!=server)return false;
        if(source.getEntity() instanceof net.minecraft.world.entity.player.Player attacker && attacker!=owner && !attacker.canHarmPlayer(owner))return false;
        return AstralProjection.bodyHit(owner,this);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
