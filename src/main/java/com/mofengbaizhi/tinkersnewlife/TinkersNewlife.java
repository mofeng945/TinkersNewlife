package com.mofengbaizhi.tinkersnewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBuy;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdInput;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketProjectionStun;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiOpenGui;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiSelect;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenWuWeiScreen;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHireState;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuWeiTechnique;

import com.mofengbaizhi.tinkersnewlife.content.*;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.YuchuziTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.FuMoYuChuZiDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.JacobsLadderTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ReverseCursedTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenShadowsTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.WuLiangKongChuDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.ZuoShaBoTuDomain;
import com.mofengbaizhi.tinkersnewlife.content.loot.LootModifierSerializers;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import com.mofengbaizhi.tinkersnewlife.content.storage.StorageManager;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdCamera;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketDragonStaffUse;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketOpenBag;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenShikigamiScreen;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketSortBag;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSummonShikigami;
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketSwitchFlyingSwordMode;
import net.minecraft.world.item.ItemStack;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSwitchTechnique;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCurse;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketToggleDomain;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseTechnique;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;

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

    /** 注册服务端→客户端（S2C）网络包 */
    private static <T> void registerClientPacket(Class<T> clazz,
                                                 BiConsumer<T, FriendlyByteBuf> encoder,
                                                 Function<FriendlyByteBuf, T> decoder,
                                                 BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(packetId++, clazz, encoder, decoder, handler,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public TinkersNewlife() {
        // ⭐ 通用配置（古神事件开关 / 咒力核心开关 / 术式与领域公式缩放系数）——config/mofengbaizhi/ 子路径
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.mofengbaizhi.tinkersnewlife.config.ModConfig.SPEC,
                "mofengbaizhi/tinkersnewlife-common.toml");
        // §545：魔力台座的"启用来源清单"会被解析成缓存 ⇒ 配置（重新）加载时要把缓存作废掉 ✓
        // （否则玩家在游戏里改 pedestal_source / 各源开关后，要到重启才生效 ✗）
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (net.minecraftforge.fml.event.config.ModConfigEvent event) ->
                        com.mofengbaizhi.tinkersnewlife.content.energy.AmbientEnergySources.onConfigReload());
        LOGGER.info("初始化 TinkersNewlife 模组...");
        // 注册自定义槽位类型：领域槽（domain）与术式槽（technique），供咒力核心等装备使用
        slimeknights.tconstruct.library.tools.SlotType.init();
        slimeknights.tconstruct.library.tools.SlotType.getOrCreate("domain");
        slimeknights.tconstruct.library.tools.SlotType.getOrCreate("technique");
        slimeknights.tconstruct.library.tools.SlotType.getOrCreate("skill");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 注册各类内容
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        ModFluids.FLUIDS.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUID_BLOCKS.register(modEventBus);
        ModFluids.FLUID_BUCKETS.register(modEventBus);

        ModEffects.EFFECTS.register(modEventBus);
        com.mofengbaizhi.tinkersnewlife.content.ModSounds.SOUNDS.register(modEventBus);

        Modifiers.MODIFIERS.register(modEventBus);

        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        ModEntities.ENTITIES.register(modEventBus);

        ModMenus.MENUS.register(modEventBus);

        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeSerializers.RECIPE_TYPES.register(modEventBus);

        LootModifierSerializers.LOOT_MODIFIERS.register(modEventBus);

        // ⭐ 联动注册总入口：存在性判定（ModList）+ 逐模组模块分派（含铁魔法反射装填）
        // 公共代码不直接引用任何联动类型，各联动类只在对应模组在场时被加载。
        IntegrationLoader.init(modEventBus);

        // §598：万用能量转化器三路联动的"哪家在场"打一行日志 ✓
        //（§557 那套反射适配器 + 它的 probeAll 日志已整体删除 ✗ —— 那行日志里写死的"AE2 未接上"是过期结论 ✗，
        //  现在只剩"模组在不在场"这一个开关 ✓，日志每次启动按实际 ModList 现算 ✓）
        LOGGER.info("[§598] 万用能量转化器联动：Mekanism={} / AE2={} / Create={}",
                IntegrationLoader.isLoaded(IntegrationLoader.MEKANISM),
                IntegrationLoader.isLoaded(IntegrationLoader.AE2),
                IntegrationLoader.isLoaded(IntegrationLoader.CREATE));


        // 强制加载 ModCurios 类，确保其事件订阅生效（特别是槽位注册）
        ModCurios.class.getName();
        LOGGER.info("ModCurios 已强制加载");

        // 注册领域特性：坐杀搏徒、无量空处、伏魔御厨子（通用领域展开键按修饰符匹配）
        DomainRegistry.registerDomain(Modifiers.ZUOSHA_BOTU.getId(), ZuoShaBoTuDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.WULIANG_KONGCHU.getId(), WuLiangKongChuDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.FUMO_YUCHUZI.getId(), FuMoYuChuZiDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.FUZHU_CISI.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.ExecutionDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.TAIZANG_BIANYE.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.TaizangBianyeDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.ZHENYAN_XIANGAI.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.ZhenyanXiangaiDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.QIANHE_YINGYI.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.QianheYingyiDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.TIE_GUAN_GAI_WEI_SHAN.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.TieGuanGaiWeiShanDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.ZI_BI_YUAN_DUN_GUO.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.ZiBiYuanDunGuoDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.DANG_YUN_PING_XIAN.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.DangYunPingXianDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.SHI_BAO_YUE_GONG_DIAN.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.ShiBaoYueGongDianDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.SAN_CHONG_JI_KU.getId(),
                com.mofengbaizhi.tinkersnewlife.content.curse.domain.SanChongJiKuDomain::tryCreate);

        // 注册术式：御厨子（解/捌/灶·开整合）、赤血操术（穿血/百敛/超新星整合）等
        TechniqueHandler.register(YuchuziTechnique.INSTANCE);
        TechniqueHandler.register(BloodManipulationTechnique.INSTANCE);
        TechniqueHandler.register(TenShadowsTechnique.INSTANCE);
        TechniqueHandler.register(BlackBirdTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.SkyManipulationTechnique.INSTANCE);
        TechniqueHandler.register(ProjectionTechnique.INSTANCE);
        TechniqueHandler.register(WuliangWuxianTechnique.INSTANCE);
        TechniqueHandler.register(WuliangCangTechnique.INSTANCE);
        TechniqueHandler.register(JacobsLadderTechnique.INSTANCE);
        TechniqueHandler.register(ReverseCursedTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuWeiTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedEnergyReleaseTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpeechTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.AntiGravityTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenDivideTechnique.INSTANCE);

        // 注册网络包
        registerPacket(PacketDragonStaffUse.class, PacketDragonStaffUse::toBytes, PacketDragonStaffUse::new, PacketDragonStaffUse::handle);
        registerPacket(PacketOpenBag.class, PacketOpenBag::toBytes, PacketOpenBag::new, PacketOpenBag::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultAction.class,
                com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultAction::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultAction::new,
                com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultAction::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultSync.class,
                com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultSync::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultSync::new,
                com.mofengbaizhi.tinkersnewlife.network.tools.PacketVaultSync::handle);
        // 呪蔵存量查询（C2S）与回包（S2C）：准星对准时显示
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketQueryCurseVault.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketQueryCurseVault::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketQueryCurseVault::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketQueryCurseVault::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCurseVault.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCurseVault::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCurseVault::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCurseVault::handle);
        registerPacket(PacketSortBag.class, PacketSortBag::toBytes, PacketSortBag::new, PacketSortBag::handle);
        registerPacket(PacketSwitchFlyingSwordMode.class, PacketSwitchFlyingSwordMode::toBytes, PacketSwitchFlyingSwordMode::new, PacketSwitchFlyingSwordMode::handle);
        registerPacket(PacketToggleDomain.class, PacketToggleDomain::toBytes, PacketToggleDomain::new, PacketToggleDomain::handle);
        registerPacket(PacketUseTechnique.class, PacketUseTechnique::toBytes, PacketUseTechnique::new, PacketUseTechnique::handle);
        // 墨默菜单三选项（自建显式包，不走容器按钮包）
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction.class, com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction::toBytes, com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction::new, com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuAction::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoSoldState.class,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoSoldState::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoSoldState::new,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoSoldState::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuOpen.class,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuOpen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuOpen::new,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuOpen::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseReverseTechnique::handle);
        registerPacket(PacketSwitchTechnique.class, PacketSwitchTechnique::toBytes, PacketSwitchTechnique::new, PacketSwitchTechnique::handle);
        registerPacket(PacketSummonShikigami.class, PacketSummonShikigami::toBytes, PacketSummonShikigami::new, PacketSummonShikigami::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdInput.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdInput::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdInput::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdInput::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetInput.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetInput::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetInput::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetInput::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketNueInput.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketNueInput::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketNueInput::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketNueInput::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetSelect.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetSelect::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetSelect::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPuppetSelect::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketPlantSelect.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPlantSelect::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPlantSelect::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketPlantSelect::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritSelect.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritSelect::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritSelect::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritSelect::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetyAction::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiSelect.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiSelect::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiSelect::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiSelect::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiOpenGui.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiOpenGui::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiOpenGui::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiOpenGui::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBuy.class,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBuy::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBuy::new,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoBuy::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire.class,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire::new,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHire::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketConstructSelect.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketConstructSelect::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketConstructSelect::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketConstructSelect::handle);
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketCursedSpeechSelect.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketCursedSpeechSelect::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketCursedSpeechSelect::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketCursedSpeechSelect::handle);
        // 服务端→客户端
        registerClientPacket(PacketSyncCurse.class, PacketSyncCurse::toBytes, PacketSyncCurse::new, PacketSyncCurse::handle);
        registerClientPacket(PacketOpenShikigamiScreen.class, PacketOpenShikigamiScreen::toBytes, PacketOpenShikigamiScreen::new, PacketOpenShikigamiScreen::handle);
        registerClientPacket(PacketBlackBirdCamera.class, PacketBlackBirdCamera::toBytes, PacketBlackBirdCamera::new, PacketBlackBirdCamera::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenWuWeiScreen.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenWuWeiScreen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenWuWeiScreen::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenWuWeiScreen::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPuppetScreen.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPuppetScreen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPuppetScreen::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPuppetScreen::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPlantScreen.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPlantScreen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPlantScreen::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPlantScreen::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen::handle);
        // 咒灵操术回执：个体"是否在场上"的权威状态（服务端决定成败后同步，客户端只照着改 UI）
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritState.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritState::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritState::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritState::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise::handle);
        // 猸?鏃犱负杞彉锛氬彲鎿嶆帶鍗曚綅锛堝個鍎℃搷鏈殑鍌€鍎?/ 榛戦笩鎿嶆湳鐨勯粦楦燂級"鍘熷湴鎹㈠舰鎬?鐨勫鎴风娓叉煋鍚屾
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketMobDisguise.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketMobDisguise::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketMobDisguise::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketMobDisguise::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen.class,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen::new,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHireState.class,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHireState::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHireState::new,
                com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoHireState::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketProjectionStun.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketProjectionStun::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketProjectionStun::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketProjectionStun::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenConstructScreen.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenConstructScreen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenConstructScreen::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenConstructScreen::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncForge::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenCursedSpeechScreen.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenCursedSpeechScreen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenCursedSpeechScreen::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenCursedSpeechScreen::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCursedChant.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCursedChant::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCursedChant::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCursedChant::handle);

        // §660 伟大白色空间：C2S 确认开门 / S2C 打开「选维度 + 填坐标」界面
        registerPacket(com.mofengbaizhi.tinkersnewlife.network.portal.PacketCreatePortal.class,
                com.mofengbaizhi.tinkersnewlife.network.portal.PacketCreatePortal::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.portal.PacketCreatePortal::new,
                com.mofengbaizhi.tinkersnewlife.network.portal.PacketCreatePortal::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.portal.PacketOpenDimensionPassScreen.class,
                com.mofengbaizhi.tinkersnewlife.network.portal.PacketOpenDimensionPassScreen::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.portal.PacketOpenDimensionPassScreen::new,
                com.mofengbaizhi.tinkersnewlife.network.portal.PacketOpenDimensionPassScreen::handle);

        // 实体属性（式神等生物实体）
        modEventBus.addListener(TinkersNewlife::onRegisterEntityAttributes);

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("TinkersNewlife 模组初始化完成");
    }

    public static ResourceLocation prefix(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /** 注册生物实体属性（式神等） */
    @SubscribeEvent
    public static void onRegisterEntityAttributes(net.minecraftforge.event.entity.EntityAttributeCreationEvent event) {
        event.put(ModEntities.SHIKIGAMI_WOLF.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiWolf.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_PHANTOM.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiPhantom.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_SILVERFISH.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiSilverfish.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_FROG.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiFrog.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_PIG.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiPig.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_RABBIT.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiRabbit.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_GOAT.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiGoat.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_COW.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiCow.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_SHEEP.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiSheep.createAttributes().build());
        event.put(ModEntities.SHIKIGAMI_IRON_GOLEM.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiIronGolem.createAttributes().build());
        event.put(ModEntities.BLACK_BIRD.get(), com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity.createAttributes().build());
        event.put(ModEntities.PUPPET_IRON_GOLEM.get(), com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem.createAttributes().build());
        event.put(ModEntities.PUPPET_SNOW_GOLEM.get(), com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem.createAttributes().build());
        event.put(ModEntities.FLAME_PHANTOM.get(), com.mofengbaizhi.tinkersnewlife.content.entity.FlamePhantom.createAttributes().build());
        event.put(ModEntities.PROJECTION_PHANTOM.get(), com.mofengbaizhi.tinkersnewlife.content.entity.ProjectionPhantomEntity.createAttributes().build());
        event.put(ModEntities.MOMO_MERCHANT.get(), com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant.createAttributes().build());
        event.put(ModEntities.WEAK_POINT.get(), com.mofengbaizhi.tinkersnewlife.content.entity.WeakPointEntity.createAttributes().build());
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

        /** 服务器启动后打印熔炼配方清单，验证万能材料熔化配方是否成功注册 */
        @SubscribeEvent
        @SuppressWarnings({"rawtypes", "unchecked"})
        public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
            try {
                net.minecraft.world.item.crafting.RecipeManager rm = event.getServer().getRecipeManager();
                int total = 0;
                int auto = 0;
                StringBuilder ids = new StringBuilder();
                for (net.minecraft.world.item.crafting.Recipe<?> r :
                        (java.util.Collection<net.minecraft.world.item.crafting.Recipe<?>>)
                                (java.util.Collection<?>) rm.getAllRecipesFor(
                                        slimeknights.tconstruct.library.recipe.TinkerRecipeTypes.MELTING.get())) {
                    total++;
                    if (r instanceof com.mofengbaizhi.tinkersnewlife.content.recipe.AutoMaterialMeltingRecipe) {
                        auto++;
                        ids.append(r.getId()).append(", ");
                    }
                }
                LOGGER.debug("[TinkersNewlife] 熔炼配方总数={}, 万能材料熔化配方数={} [{}]", total, auto, ids);
            } catch (Throwable t) {
                LOGGER.warn("[TinkersNewlife] 打印熔炼配方失败: {}", t.toString());
            }
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                if (event.getServer().getTickCount() % 600 == 0) {
                    StorageManager.getInstance().autoSave();
                    // 手套库脏数据同样由主线程定时落盘（崩溃保护）
                    SilentGloveHandler.saveAllDirty();
                }
                // 投射咒法：速度增益 modifier 维护 + 罚站锁定（每 tick 检查）
                for (net.minecraft.server.level.ServerPlayer p :
                        event.getServer().getPlayerList().getPlayers()) {
                    // 无下限·苍/赫 蓄力粒子
                    com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique
                            .tickChargeParticles((net.minecraft.server.level.ServerLevel) p.level(), p);
                    // 咒力外放 反转激光（每 tick 驱动：伤害间隔/粒子/到期冷却）
                    com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedEnergyReleaseTechnique
                            .tickBlast((net.minecraft.server.level.ServerLevel) p.level(), p);
                    // 构筑术式（每 tick 驱动：顺转弹药凝结 / 反转临时物到期清理）
                    com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.tickServer(p);
                    // 咒言术（每 tick 驱动：咏唱读条/莎布增殖/犹格经验）
                    com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpeechTechnique
                            .tickServer((net.minecraft.server.level.ServerLevel) p.level(), p);
                    // 帕秋莉手册解锁：每 20 tick 扫描核心特性，获得术式/领域即解锁对应章节
                    if (event.getServer().getTickCount() % 20 == 0) {
                        com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueAdvancementHandler.scanAndUnlock(p);
                    }
                    var proj = com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.INSTANCE;
                    // 罚站：钉住位置/视角/速度（服务端权威），到期解除
                    var projData = p.getPersistentData();
                    if (com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.isStunned(p)) {
                        p.setNoGravity(true);
                        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        p.teleportTo(projData.getDouble("tinkersnewlife.projection_stun_x"),
                                projData.getDouble("tinkersnewlife.projection_stun_y"),
                                projData.getDouble("tinkersnewlife.projection_stun_z"));
                        float yaw = projData.getFloat("tinkersnewlife.projection_stun_yaw");
                        float pitch = projData.getFloat("tinkersnewlife.projection_stun_pitch");
                        p.setYRot(yaw);
                        p.setXRot(pitch);
                        p.yBodyRot = yaw;
                        p.yHeadRot = yaw;
                        p.xRotO = pitch;
                    } else {
                        p.setNoGravity(false);
                        // 罚站到期：通知客户端解除输入锁定
                        if (projData.contains("tinkersnewlife.projection_stun_until")) {
                            com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.endStun(p);
                        }
                    }
                    // 速度增益：modifier = 2^层数 - 1（×1 即 +100%），但速度倍率封顶 32 倍，防止过快
                    var attr = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                    if (attr == null) continue;
                    double speedMult = Math.min(32.0,
                            com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.getBuffMultiplier(p));
                    var mod = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                            java.util.UUID.fromString("7a9f2c4e-8b3d-4e5f-9a1c-2d3e4f5a6b7c"),
                            "projection_speed",
                            speedMult - 1.0,
                            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL);
                    if (com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.hasBuff(p)
                            && !attr.hasModifier(mod)) {
                        attr.addTransientModifier(mod);
                    } else if (!com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.hasBuff(p)
                            && attr.hasModifier(mod)) {
                        attr.removeModifier(mod);
                    }
                }
            }
        }

        /** 投射咒法：伤害 ×2^层数（攻击者处于增益）；无下限·无限：低伤抵挡/溢出扣咒力 */
        @SubscribeEvent
        public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            /*
             * §695 定向诊断（临时）：只在「受伤者是开着无下限·无限的玩家」时才打日志 ✓ ——
             * 目的是把用户报的「无下限挡不住伤害」定死：到底是没被调用、被 skipNested 早退、
             * 被咒具穿透、还是 onPlayerDamaged 返回了非 0 ✓。定位完就撤 ✗。
             */
            var wuxianVictim = (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer v
                    && com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.isActive(v))
                    ? v : null;
            // ⭐ 混沌之流改判成法术时会自己重发一次那一发 ⇒ 投射咒法 "×2^层" 不再对重发的那一发再乘一次 ✗
            //    否则同一次命中被乘两遍（数值爆炸）✗（见 util/DamagePipeline）
            if (com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.skipNested()) {
                if (wuxianVictim != null) {
                    TinkersNewlife.LOGGER.info("[无下限·诊断] {} 受击但被 DamagePipeline.skipNested() 提前跳过"
                            + " ⇒ 本次不格挡（伤害 {}）", wuxianVictim.getName().getString(), event.getAmount());
                }
                return;
            }
            var src = event.getSource();
            if (src != null && src.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker
                    && com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.hasBuff(attacker)) {
                event.setAmount(event.getAmount()
                        * (float) com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.getBuffMultiplier(attacker));
            }
            // 无下限·无限：受伤者为开启无限的玩家时，按无限规则结算
            // ⭐ 天逆鉾等可穿透无下限的咒具（ignoresInfinity=true）无视该防御，直接造成伤害
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer victim
                    && com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.isActive(victim)) {
                boolean bypass = false;
                if (src != null && src.getDirectEntity() instanceof net.minecraft.world.entity.player.Player p
                        && com.mofengbaizhi.tinkersnewlife.content.item.CursedToolItem.isHolding(p)) {
                    ItemStack held = p.getMainHandItem();
                    if (held.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.CursedToolItem ct
                            && ct.ignoresInfinity()) {
                        bypass = true;
                    }
                }
                float before = event.getAmount();
                String srcId = src == null ? "null" : src.getMsgId();
                if (!bypass) {
                    float after = com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique
                            .onPlayerDamaged(victim, before);
                    event.setAmount(after);
                    /*
                     * §696：**光把 amount 抹成 0 不够** ✗ ——
                     * 用户实测「近战/远程/爆炸都掉血」，而日志显示我们这边已经 `→ 0.0` ✓
                     * ⇒ 说明后面还有人把 amount 改回来（或者伤害从别的路径落地）✗。
                     * ⇒ 完全格挡时**同时取消事件** ✓：原版 `LivingEntity#hurt` 在
                     *   `ForgeHooks.onLivingHurt` 之后有一句 `if (f <= 0.0F) return false;` ✓，
                     *   而事件被取消同样会让这一下不落地 ✓ —— 双保险 ✓。
                     */
                    if (after <= 0.0F) {
                        event.setCanceled(true);
                    }
                    TinkersNewlife.LOGGER.info("[无下限·诊断] {} 受击（{}）：{} → {}（阈值 {}，核心咒力 {}，总咒力 {}，已取消={}）",
                            victim.getName().getString(), srcId, before, after,
                            com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.getThreshold(victim),
                            com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper.getCurse(victim),
                            com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper.getTotalCurse(victim),
                            event.isCanceled());
                } else {
                    TinkersNewlife.LOGGER.info("[无下限·诊断] {} 受击（{}）：{} 被咒具穿透（ignoresInfinity）⇒ 不格挡",
                            victim.getName().getString(), srcId, before);
                }
            }
        }

        /**
         * §696 诊断（临时）：<b>最低优先级</b>再读一次最终数值 ✓ ——
         * 若我们抹成 0 之后还有人改回来，这里会显示非 0 ✓（用来找出"复活伤害"的那一环）。
         */
        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
        public static void onLivingHurtFinal(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer v)) return;
            if (!com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.isActive(v)) return;
            TinkersNewlife.LOGGER.info("[无下限·诊断·最终] {} 受击（{}）：事件最终 amount = {}（canceled={}）",
                    v.getName().getString(),
                    event.getSource() == null ? "null" : event.getSource().getMsgId(),
                    event.getAmount(), event.isCanceled());
        }

        /**
         * §696 诊断（临时）：{@code LivingDamageEvent}（护甲之后的最后一关）到底有没有触发、数值多少 ✓ ——
         * 若这里出现非 0，说明"格挡之后又被加了回来"；若这里根本不出现，说明伤害不走事件路径 ✗。
         */
        @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
        public static void onLivingDamageFinal(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer v)) return;
            if (!com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.isActive(v)) return;
            TinkersNewlife.LOGGER.info("[无下限·诊断·最终] {} 进入 LivingDamageEvent：amount = {}（canceled={}）",
                    v.getName().getString(), event.getAmount(), event.isCanceled());
        }

        /** 投射咒法：跳跃高度 ×2^层数（封顶 8 倍） */
        @SubscribeEvent
        public static void onLivingJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p
                    && com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.hasBuff(p)) {
                double jumpMult = Math.min(8.0,
                        com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.getBuffMultiplier(p));
                p.setDeltaMovement(p.getDeltaMovement().x,
                        p.getDeltaMovement().y * jumpMult,
                        p.getDeltaMovement().z);
            }
        }

        /**
     * ⭐ 拟造雪傀儡不留雪痕：原版 {@code SnowGolem#aiStep} 的留雪逻辑外层是
     * {@code ForgeEventFactory.getMobGriefingEvent(level, this)} —— 这里对拟造雪傀儡一律 DENY，
     * 既去掉了脚下雪痕，也顺带避免了拟造傀儡乱改地形。
     */
    @SubscribeEvent
    public static void onMobGriefing(net.minecraftforge.event.entity.EntityMobGriefingEvent event) {
        if (!(event.getEntity() instanceof com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem)) return;
        event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    /** 投射咒法：罚站期间无法攻击 */
        @SubscribeEvent
        public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
            var src = event.getSource();
            if (src != null && src.getEntity() instanceof net.minecraft.server.level.ServerPlayer attacker
                    && com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.isStunned(attacker)) {
                event.setCanceled(true);
            }
        }

        /** 投射咒法：罚站期间无法交互（右键方块/物品/实体） */
        @SubscribeEvent
        public static void onPlayerInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p
                    && com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.isStunned(p)) {
                event.setCanceled(true);
            }
        }
    }
}