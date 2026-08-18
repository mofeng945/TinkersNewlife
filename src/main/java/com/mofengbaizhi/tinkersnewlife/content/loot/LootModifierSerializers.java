package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class LootModifierSerializers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TinkersNewlife.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_ITEMS =
            LOOT_MODIFIERS.register("add_items", () -> AddItemsModifier.CODEC.get());

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> RANDOM_GLOVE =
            LOOT_MODIFIERS.register("random_glove", () -> RandomGloveModifier.CODEC.get());
}