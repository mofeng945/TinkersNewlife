package com.mofengbaizhi.tinkersnewlife;

import com.mofengbaizhi.tinkersnewlife.content.*;
import com.mofengbaizhi.tinkersnewlife.content.loot.LootModifierSerializers;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import com.mofengbaizhi.tinkersnewlife.content.storage.StorageManager;
import com.mofengbaizhi.tinkersnewlife.network.PacketDragonStaffUse;
import com.mofengbaizhi.tinkersnewlife.network.PacketOpenBag;
import com.mofengbaizhi.tinkersnewlife.network.PacketSortBag;
import com.mofengbaizhi.tinkersnewlife.network.PacketSwitchFlyingSwordMode;
import com.mofengbaizhi.tinkersnewlife.network.PacketUseSkill;
import com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.file.Path;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Mod(TinkersNewlife.MOD_ID)
public class TinkersNewlife {
    public static final String MOD_ID = "tinkersnewlife";
    public static final Logger LOGGER = LoggerFactory.getLogger(TinkersNewlife.class);

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> "1.0",
            s -> true,
            s -> true
    );

    private static int packetId = 0;

    /**
     * 注册客户端→服务端（C2S）网络包。
     * ⭐ 显式声明 {@code NetworkDirection.PLAY_TO_SERVER}：5 参 registerMessage 的方向为
     * Optional.empty()（无方向校验），显式声明后 Forge 会拒绝服务端误发的包。
     */
    private static <T> void registerPacket(Class<T> clazz,
                                           BiConsumer<T, FriendlyByteBuf> encoder,
                                           Function<FriendlyByteBuf, T> decoder,
                                           BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(packetId++, clazz, encoder, decoder, handler,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public TinkersNewlife() {
        LOGGER.info("初始化 TinkersNewlife 模组...");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册各类内容
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);

        ModFluids.FLUIDS.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUID_BLOCKS.register(modEventBus);
        ModFluids.FLUID_BUCKETS.register(modEventBus);

        ModEffects.EFFECTS.register(modEventBus);

        Modifiers.MODIFIERS.register(modEventBus);

        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        ModEntities.ENTITIES.register(modEventBus);

        ModMenus.MENUS.register(modEventBus);

        LootModifierSerializers.LOOT_MODIFIERS.register(modEventBus);

        IronSpellsReflector.init();


        // 强制加载 ModCurios 类，确保其事件订阅生效（特别是槽位注册）
        ModCurios.class.getName();
        LOGGER.info("ModCurios 已强制加载");

        // 注册网络包
        registerPacket(PacketUseSkill.class, PacketUseSkill::toBytes, PacketUseSkill::new, PacketUseSkill::handle);
        registerPacket(PacketDragonStaffUse.class, PacketDragonStaffUse::toBytes, PacketDragonStaffUse::new, PacketDragonStaffUse::handle);
        registerPacket(PacketOpenBag.class, PacketOpenBag::toBytes, PacketOpenBag::new, PacketOpenBag::handle);
        registerPacket(PacketSortBag.class, PacketSortBag::toBytes, PacketSortBag::new, PacketSortBag::handle);
        registerPacket(PacketSwitchFlyingSwordMode.class, PacketSwitchFlyingSwordMode::toBytes, PacketSwitchFlyingSwordMode::new, PacketSwitchFlyingSwordMode::handle);

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("TinkersNewlife 模组初始化完成");
    }

    public static ResourceLocation prefix(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    // ========== Forge 事件处理 ==========
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            Path worldSaveDir = event.getServer().getWorldPath(LevelResource.ROOT);
            StorageManager.getInstance().initServer(worldSaveDir);
            SilentGloveHandler.initServer(worldSaveDir);
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                if (event.getServer().getTickCount() % 600 == 0) {
                    StorageManager.getInstance().autoSave();
                    // 手套库脏数据同样由主线程定时落盘（崩溃保护）
                    SilentGloveHandler.saveAllDirty();
                }
            }
        }
    }
}