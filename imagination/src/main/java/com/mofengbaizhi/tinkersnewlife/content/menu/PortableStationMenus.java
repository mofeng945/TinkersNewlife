package com.mofengbaizhi.tinkersnewlife.content.menu;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** §1278 便携工匠站的菜单类型注册。 */
public final class PortableStationMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TinkersNewlife.MOD_ID);

    public static final RegistryObject<MenuType<PortableStationMenu>> PORTABLE_STATION =
            MENUS.register("portable_tinker_station",
                    () -> new MenuType<>((net.minecraftforge.network.IContainerFactory<PortableStationMenu>) PortableStationMenu::new, FeatureFlags.VANILLA_SET));

    private PortableStationMenus() {
    }
}