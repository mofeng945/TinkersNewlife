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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

public class ModMenus {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModMenus.class);

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TinkersNewlife.MOD_ID);

    // ===== 量子背包 =====
    public static final RegistryObject<MenuType<BagContainer>> BAG_CONTAINER =
            MENUS.register("bag_container",
                    () -> IForgeMenuType.create((windowId, inv, data) -> {
                        UUID uuid = data.readUUID();
                        int level = data.readInt();
                        byte[] bytes = data.readByteArray();
                        CompoundTag tag = readNBT(bytes);
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

                        CompoundTag tag = readNBT(bytes);

                        // 客户端创建临时 Handler，不缓存、不持久化
                        SilentGloveHandler handler = SilentGloveHandler.createClient(vaultUUID, tag);
                        return new SilentGloveContainer(windowId, inv, vaultUUID, handler);
                    })
            );

    /**
     * 从字节数组读取 NBT；失败时记录日志（不再静默吞掉，便于排查数据损坏问题）。
     *
     * @return 读取到的 NBT，或 null（数据为空/损坏）
     */
    private static CompoundTag readNBT(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return NbtIo.read(dis);
        } catch (IOException e) {
            LOGGER.error("[TinkersNewlife] 读取容器数据 NBT 失败（数据可能损坏）: {}", e.toString());
            return null;
        }
    }
}
