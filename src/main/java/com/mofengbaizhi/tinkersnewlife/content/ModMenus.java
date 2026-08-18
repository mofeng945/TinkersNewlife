package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.storage.BagContainer;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveContainer;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TinkersNewlife.MOD_ID);

    // ===== 量子背包 =====
    public static final RegistryObject<MenuType<BagContainer>> BAG_CONTAINER =
            MENUS.register("bag_container",
                    () -> IForgeMenuType.create((windowId, inv, data) -> {
                        UUID uuid = data.readUUID();
                        int level = data.readInt();
                        byte[] bytes = data.readByteArray();
                        CompoundTag tag = null;
                        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
                            tag = NbtIo.read(dis);
                        } catch (IOException ignored) {}
                        if (tag != null) {
                            return new BagContainer(windowId, inv, uuid, level, tag);
                        }
                        return new BagContainer(windowId, inv, uuid, level);
                    })
            );

    // ===== 噤默手套（客户端使用临时 Handler） =====
    public static final RegistryObject<MenuType<SilentGloveContainer>> SILENT_GLOVE_CONTAINER =
            MENUS.register("silent_glove_container",
                    () -> IForgeMenuType.create((windowId, inv, data) -> {
                        UUID vaultUUID = data.readUUID();
                        byte[] bytes = data.readByteArray();

                        CompoundTag tag = null;
                        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
                            tag = NbtIo.read(dis);
                        } catch (IOException ignored) {}

                        // 客户端创建临时 Handler，不缓存、不持久化
                        SilentGloveHandler handler = SilentGloveHandler.createClient(vaultUUID, tag);
                        return new SilentGloveContainer(windowId, inv, vaultUUID, handler);
                    })
            );
}