package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

public class FlyingSwordFootEntity extends Entity {

    private static final EntityDataAccessor<ItemStack> ITEM_STACK =
            SynchedEntityData.defineId(FlyingSwordFootEntity.class, EntityDataSerializers.ITEM_STACK);
    // ✅ 使用 STRING 存储 UUID 字符串，避免 OPTIONAL_UUID 的复杂性
    private static final EntityDataAccessor<String> OWNER_UUID_STRING =
            SynchedEntityData.defineId(FlyingSwordFootEntity.class, EntityDataSerializers.STRING);

    public FlyingSwordFootEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public FlyingSwordFootEntity(Level level, Player owner, ItemStack stack) {
        super(ModEntities.FLYING_SWORD_FOOT.get(), level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setItemStack(stack);
        this.setOwnerUUID(owner.getUUID());
        this.setPos(owner.getX(), owner.getY() - 0.8, owner.getZ());
    }

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(ITEM_STACK, ItemStack.EMPTY);
        this.getEntityData().define(OWNER_UUID_STRING, "");
    }

    public void setItemStack(ItemStack stack) {
        this.getEntityData().set(ITEM_STACK, stack);
    }

    public ItemStack getItemStack() {
        return this.getEntityData().get(ITEM_STACK);
    }

    public void setOwnerUUID(UUID uuid) {
        this.getEntityData().set(OWNER_UUID_STRING, uuid.toString());
    }

    public UUID getOwnerUUID() {
        String uuidStr = this.getEntityData().get(OWNER_UUID_STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        UUID ownerId = this.getOwnerUUID();
        if (ownerId == null) {
            this.discard();
            return;
        }

        Player owner = this.level().getPlayerByUUID(ownerId);
        if (owner == null || !owner.isAlive() || !owner.getAbilities().flying) {
            this.discard();
            return;
        }

        // 检查是否还装备着飞剑
        boolean hasFlyingSword = false;
        var curios = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(owner).resolve();
        if (curios.isPresent()) {
            var slotResult = curios.get().findFirstCurio(stack -> stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem);
            if (slotResult.isPresent()) {
                hasFlyingSword = true;
            }
        }
        if (!hasFlyingSword) {
            this.discard();
            return;
        }

        this.setPos(owner.getX(), owner.getY() - 0.1, owner.getZ());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("OwnerUUID")) {
            String uuidStr = tag.getString("OwnerUUID");
            if (!uuidStr.isEmpty()) {
                try {
                    this.setOwnerUUID(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (tag.contains("ItemStack")) {
            this.setItemStack(ItemStack.of(tag.getCompound("ItemStack")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID ownerId = this.getOwnerUUID();
        if (ownerId != null) {
            tag.putString("OwnerUUID", ownerId.toString());
        }
        tag.put("ItemStack", this.getItemStack().save(new CompoundTag()));
    }
}