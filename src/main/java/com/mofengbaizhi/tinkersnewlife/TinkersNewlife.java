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
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationHyakurenTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationSupernovaTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BloodManipulationTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.FuMoYuChuZiDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.KaiTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.JacobsLadderTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ReverseCursedTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenShadowsTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.WuLiangKongChuDomain;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ZaoKaiTechnique;
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
import com.mofengbaizhi.tinkersnewlife.network.tools.PacketUseSkill;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketUseTechnique;
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

    /** 注册服务端→客户端（S2C）网络包 */
    private static <T> void registerClientPacket(Class<T> clazz,
                                                 BiConsumer<T, FriendlyByteBuf> encoder,
                                                 Function<FriendlyByteBuf, T> decoder,
                                                 BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(packetId++, clazz, encoder, decoder, handler,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public TinkersNewlife() {
        LOGGER.info("初始化 TinkersNewlife 模组...");
        // 注册自定义槽位类型：领域槽（domain）与术式槽（technique），供咒力核心等装备使用
        slimeknights.tconstruct.library.tools.SlotType.init();
        slimeknights.tconstruct.library.tools.SlotType.getOrCreate("domain");
        slimeknights.tconstruct.library.tools.SlotType.getOrCreate("technique");
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 注册各类内容
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);

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

        LootModifierSerializers.LOOT_MODIFIERS.register(modEventBus);

        IronSpellsReflector.init();


        // 强制加载 ModCurios 类，确保其事件订阅生效（特别是槽位注册）
        ModCurios.class.getName();
        LOGGER.info("ModCurios 已强制加载");

        // 注册领域特性：坐杀搏徒、无量空处、伏魔御厨子（通用领域展开键按修饰符匹配）
        DomainRegistry.registerDomain(Modifiers.ZUOSHA_BOTU.getId(), ZuoShaBoTuDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.WULIANG_KONGCHU.getId(), WuLiangKongChuDomain::tryCreate);
        DomainRegistry.registerDomain(Modifiers.FUMO_YUCHUZI.getId(), FuMoYuChuZiDomain::tryCreate);

        // 注册术式：解、捌、灶·开（后续术式继承 BaseTechnique 后在此登记）
        TechniqueHandler.register(KaiTechnique.INSTANCE);
        TechniqueHandler.register(BaTechnique.INSTANCE);
        TechniqueHandler.register(ZaoKaiTechnique.INSTANCE);
        TechniqueHandler.register(BloodManipulationTechnique.INSTANCE);
        TechniqueHandler.register(BloodManipulationHyakurenTechnique.INSTANCE);
        TechniqueHandler.register(BloodManipulationSupernovaTechnique.INSTANCE);
        TechniqueHandler.register(TenShadowsTechnique.INSTANCE);
        TechniqueHandler.register(BlackBirdTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.INSTANCE);
        TechniqueHandler.register(ProjectionTechnique.INSTANCE);
        TechniqueHandler.register(WuliangWuxianTechnique.INSTANCE);
        TechniqueHandler.register(WuliangCangTechnique.INSTANCE);
        TechniqueHandler.register(JacobsLadderTechnique.INSTANCE);
        TechniqueHandler.register(ReverseCursedTechnique.INSTANCE);
        TechniqueHandler.register(com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuWeiTechnique.INSTANCE);

        // 注册网络包
        registerPacket(PacketUseSkill.class, PacketUseSkill::toBytes, PacketUseSkill::new, PacketUseSkill::handle);
        registerPacket(PacketDragonStaffUse.class, PacketDragonStaffUse::toBytes, PacketDragonStaffUse::new, PacketDragonStaffUse::handle);
        registerPacket(PacketOpenBag.class, PacketOpenBag::toBytes, PacketOpenBag::new, PacketOpenBag::handle);
        registerPacket(PacketSortBag.class, PacketSortBag::toBytes, PacketSortBag::new, PacketSortBag::handle);
        registerPacket(PacketSwitchFlyingSwordMode.class, PacketSwitchFlyingSwordMode::toBytes, PacketSwitchFlyingSwordMode::new, PacketSwitchFlyingSwordMode::handle);
        registerPacket(PacketToggleDomain.class, PacketToggleDomain::toBytes, PacketToggleDomain::new, PacketToggleDomain::handle);
        registerPacket(PacketUseTechnique.class, PacketUseTechnique::toBytes, PacketUseTechnique::new, PacketUseTechnique::handle);
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
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketStaffGoetySync::handle);
        registerClientPacket(com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise.class,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise::toBytes,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise::new,
                com.mofengbaizhi.tinkersnewlife.network.curse.PacketWuWeiDisguise::handle);
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
                LOGGER.info("[TinkersNewlife] 熔炼配方总数={}, 万能材料熔化配方数={} [{}]", total, auto, ids);
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
                if (!bypass) {
                    event.setAmount(com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique
                            .onPlayerDamaged(victim, event.getAmount()));
                }
            }
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