package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class IronSpellsReflector {
    private static final Logger LOGGER = LoggerFactory.getLogger(IronSpellsReflector.class);

    private static boolean initialized = false;
    private static boolean ironSpellsPresent = false;

    // 缓存类
    private static Class<?> SPELL_SELECTION_MANAGER_CLASS;
    private static Class<?> SELECTION_OPTION_CLASS;
    private static Class<?> SPELL_DATA_CLASS;
    private static Class<?> ABSTRACT_SPELL_CLASS;
    private static Class<?> CAST_SOURCE_CLASS;
    private static Class<?> MAGIC_DATA_CLASS;

    // 缓存方法
    private static Constructor<?> SPELL_SELECTION_MANAGER_CONSTRUCTOR;
    private static Method SPELL_SELECTION_MANAGER_GET_SELECTION_METHOD;
    private static Field SELECTION_OPTION_SPELL_DATA_FIELD;
    private static Method SELECTION_OPTION_GET_CAST_SOURCE_METHOD;
    private static Method SPELL_DATA_GET_SPELL_METHOD;
    private static Method SPELL_DATA_GET_LEVEL_METHOD;
    private static Method ABSTRACT_SPELL_GET_LEVEL_FOR_METHOD;
    private static Method ABSTRACT_SPELL_GET_MANA_COST_METHOD;
    private static Method ABSTRACT_SPELL_ATTEMPT_INITIATE_CAST_METHOD;
    private static Method MAGIC_DATA_GET_PLAYER_MAGIC_DATA_METHOD;
    private static Method MAGIC_DATA_GET_MANA_METHOD;

    // 常量
    private static Object SPELL_DATA_EMPTY;
    private static Object CAST_SOURCE_SPELLBOOK;

    private static final String MAINHAND = "mainhand";
    private static final String OFFHAND = "offhand";

    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class.forName("io.redspace.ironsspellbooks.IronsSpellbooks");
            ironSpellsPresent = true;

            // 1. SpellSelectionManager
            SPELL_SELECTION_MANAGER_CLASS = Class.forName("io.redspace.ironsspellbooks.api.magic.SpellSelectionManager");
            SPELL_SELECTION_MANAGER_CONSTRUCTOR = SPELL_SELECTION_MANAGER_CLASS.getConstructor(Player.class);
            SPELL_SELECTION_MANAGER_GET_SELECTION_METHOD = SPELL_SELECTION_MANAGER_CLASS.getMethod("getSelection");

            // 2. SelectionOption
            SELECTION_OPTION_CLASS = Class.forName("io.redspace.ironsspellbooks.api.magic.SpellSelectionManager$SelectionOption");
            SELECTION_OPTION_SPELL_DATA_FIELD = SELECTION_OPTION_CLASS.getDeclaredField("spellData");
            SELECTION_OPTION_SPELL_DATA_FIELD.setAccessible(true);
            SELECTION_OPTION_GET_CAST_SOURCE_METHOD = SELECTION_OPTION_CLASS.getMethod("getCastSource");

            // 3. SpellData
            SPELL_DATA_CLASS = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellData");
            SPELL_DATA_GET_SPELL_METHOD = SPELL_DATA_CLASS.getMethod("getSpell");
            SPELL_DATA_GET_LEVEL_METHOD = SPELL_DATA_CLASS.getMethod("getLevel");
            SPELL_DATA_EMPTY = SPELL_DATA_CLASS.getField("EMPTY").get(null);

            // 4. AbstractSpell
            ABSTRACT_SPELL_CLASS = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            ABSTRACT_SPELL_GET_LEVEL_FOR_METHOD = ABSTRACT_SPELL_CLASS.getMethod("getLevelFor", int.class, LivingEntity.class);
            ABSTRACT_SPELL_GET_MANA_COST_METHOD = ABSTRACT_SPELL_CLASS.getMethod("getManaCost", int.class);

            CAST_SOURCE_CLASS = Class.forName("io.redspace.ironsspellbooks.api.spells.CastSource");
            ABSTRACT_SPELL_ATTEMPT_INITIATE_CAST_METHOD = ABSTRACT_SPELL_CLASS.getMethod(
                    "attemptInitiateCast",
                    ItemStack.class, int.class, Level.class, Player.class,
                    CAST_SOURCE_CLASS, boolean.class, String.class
            );

            // 5. CastSource 常量
            CAST_SOURCE_SPELLBOOK = CAST_SOURCE_CLASS.getField("SPELLBOOK").get(null);

            // 6. MagicData
            MAGIC_DATA_CLASS = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            MAGIC_DATA_GET_PLAYER_MAGIC_DATA_METHOD = MAGIC_DATA_CLASS.getMethod("getPlayerMagicData", LivingEntity.class);
            MAGIC_DATA_GET_MANA_METHOD = MAGIC_DATA_CLASS.getMethod("getMana");

        } catch (Throwable t) {
            // ⭐ catch(Throwable) 覆盖 ExceptionInInitializerError/LinkageError：
            // 反射初始化类失败时若只 catch(Exception) 会让 Error 直接崩服
            ironSpellsPresent = false;
            LOGGER.warn("[TinkersNewlife] 铁魔法（Iron's Spells）反射初始化失败，模块化魔杖法术功能不可用: {}", t.toString());
        }
    }

    public static boolean isIronSpellsAvailable() {
        return ironSpellsPresent && initialized;
    }

    public static boolean tryCastSpell(ServerPlayer player, ItemStack staffStack, InteractionHand hand) {
        if (!isIronSpellsAvailable()) return false;
        if (player.level().isClientSide) return false;

        try {
            // 1. 获取玩家当前选中的法术
            Object selectionManager = SPELL_SELECTION_MANAGER_CONSTRUCTOR.newInstance(player);
            Object selectionOption = SPELL_SELECTION_MANAGER_GET_SELECTION_METHOD.invoke(selectionManager);
            if (selectionOption == null) return false;

            Object spellData = SELECTION_OPTION_SPELL_DATA_FIELD.get(selectionOption);
            if (spellData == null || spellData.equals(SPELL_DATA_EMPTY)) return false;

            Object spell = SPELL_DATA_GET_SPELL_METHOD.invoke(spellData);
            if (spell == null) return false;

            int spellLevel = (int) SPELL_DATA_GET_LEVEL_METHOD.invoke(spellData);
            spellLevel = (int) ABSTRACT_SPELL_GET_LEVEL_FOR_METHOD.invoke(spell, spellLevel, player);

            // 2. 检查魔力
            Object magicData = MAGIC_DATA_GET_PLAYER_MAGIC_DATA_METHOD.invoke(null, player);
            if (magicData == null) return false;

            float mana = (float) MAGIC_DATA_GET_MANA_METHOD.invoke(magicData);
            int manaCost = (int) ABSTRACT_SPELL_GET_MANA_COST_METHOD.invoke(spell, spellLevel);
            if (mana < manaCost) return false;

            // 3. 获取 CastSource
            Object castSource = SELECTION_OPTION_GET_CAST_SOURCE_METHOD.invoke(selectionOption);
            if (castSource == null) {
                castSource = CAST_SOURCE_SPELLBOOK;
            }

            String castingSlot = (hand == InteractionHand.MAIN_HAND) ? MAINHAND : OFFHAND;

            // 4. 调用 attemptInitiateCast
            return (boolean) ABSTRACT_SPELL_ATTEMPT_INITIATE_CAST_METHOD.invoke(
                    spell,
                    staffStack,
                    spellLevel,
                    player.level(),
                    player,
                    castSource,
                    true,
                    castingSlot
            );
        } catch (Throwable t) {
            LOGGER.warn("[TinkersNewlife] 铁魔法施法反射调用失败: {}", t.toString());
            return false;
        }
    }

    // =====================================================================
    //  神圣之力（神灵金近战特性联动铁魔法）：给武器注入预设法术容器 + 持有者法力
    //  原版参考：RevelationFix ValetteinItemMixin.initializeSpellContainer ——
    //  ISpellContainer.create(...) + addSpell(HEALING_CIRCLE@10 / WALL_OF_FIRE@5 / ANGEL_WINGS@5) + save
    // =====================================================================

    private static boolean divineInited = false;
    private static boolean divineInitLogged = false;
    private static Class<?> ISPELL_CONTAINER_CLASS;
    private static Class<?> SPELL_REGISTRY_CLASS;
    private static Class<?> ATTRIBUTE_REGISTRY_CLASS;
    private static Method ISPELL_CONTAINER_IS_CONTAINER;
    private static Method ISPELL_CONTAINER_CREATE;
    private static Method ISPELL_CONTAINER_ADD_SPELL;
    private static Method ISPELL_CONTAINER_SAVE;
    private static Field SPELL_REGISTRY_HEALING;
    private static Field SPELL_REGISTRY_WALL;
    private static Field SPELL_REGISTRY_ANGEL;
    private static Field ATTRIBUTE_REGISTRY_MAX_MANA;

    private static void initDivine() {
        if (divineInited) return;
        divineInited = true;
        try {
            Class.forName("io.redspace.ironsspellbooks.IronsSpellbooks");
            ISPELL_CONTAINER_CLASS = Class.forName("io.redspace.ironsspellbooks.api.spells.ISpellContainer");
            SPELL_REGISTRY_CLASS = Class.forName("io.redspace.ironsspellbooks.api.registry.SpellRegistry");
            ATTRIBUTE_REGISTRY_CLASS = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
            ISPELL_CONTAINER_IS_CONTAINER = ISPELL_CONTAINER_CLASS.getMethod("isSpellContainer", ItemStack.class);
            ISPELL_CONTAINER_CREATE = ISPELL_CONTAINER_CLASS.getMethod("create", int.class, boolean.class, boolean.class);
            ISPELL_CONTAINER_ADD_SPELL = ISPELL_CONTAINER_CLASS.getMethod("addSpell",
                    Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell"),
                    int.class, boolean.class, ItemStack.class);
            ISPELL_CONTAINER_SAVE = ISPELL_CONTAINER_CLASS.getMethod("save", ItemStack.class);
            SPELL_REGISTRY_HEALING = SPELL_REGISTRY_CLASS.getField("HEALING_CIRCLE_SPELL");
            SPELL_REGISTRY_WALL = SPELL_REGISTRY_CLASS.getField("WALL_OF_FIRE_SPELL");
            SPELL_REGISTRY_ANGEL = SPELL_REGISTRY_CLASS.getField("ANGEL_WINGS_SPELL");
            ATTRIBUTE_REGISTRY_MAX_MANA = ATTRIBUTE_REGISTRY_CLASS.getField("MAX_MANA");
        } catch (Throwable t) {
            if (!divineInitLogged) {
                divineInitLogged = true;
                LOGGER.warn("[TinkersNewlife] 铁魔法神圣之力反射初始化失败（铁魔法未装？）: {}", t.toString());
            }
        }
    }

    /** 铁魔法是否存在（负载判断用） */
    public static boolean hasIronSpells() {
        return ironSpellsPresent;
    }

    /** 给物品栈注入预设法术容器（火墙术Lv5 / 天使之翼Lv5 / 治愈之环Lv10）；铁魔法未装则无事发生 */
    public static void initSpellContainer(ItemStack stack) {
        if (!ironSpellsPresent || stack == null || stack.isEmpty()) return;
        initDivine();
        try {
            if ((Boolean) ISPELL_CONTAINER_IS_CONTAINER.invoke(null, stack)) return;
            Object container = ISPELL_CONTAINER_CREATE.invoke(null, 3, true, false);
            Object healing = ((net.minecraftforge.registries.RegistryObject<?>) SPELL_REGISTRY_HEALING.get(null)).get();
            Object wall = ((net.minecraftforge.registries.RegistryObject<?>) SPELL_REGISTRY_WALL.get(null)).get();
            Object angel = ((net.minecraftforge.registries.RegistryObject<?>) SPELL_REGISTRY_ANGEL.get(null)).get();
            ISPELL_CONTAINER_ADD_SPELL.invoke(container, healing, 10, true, stack);
            ISPELL_CONTAINER_ADD_SPELL.invoke(container, wall, 5, true, stack);
            ISPELL_CONTAINER_ADD_SPELL.invoke(container, angel, 5, true, stack);
            ISPELL_CONTAINER_SAVE.invoke(container, stack);
        } catch (Throwable t) {
            LOGGER.warn("[TinkersNewlife] 铁魔法注入法术容器失败: {}", t.toString());
        }
    }

    /** 获取铁魔法 MAX_MANA 属性（Attribute）；不存在返回 null */
    public static net.minecraft.world.entity.ai.attributes.Attribute maxManaAttribute() {
        if (!ironSpellsPresent) return null;
        initDivine();
        try {
            return (net.minecraft.world.entity.ai.attributes.Attribute)
                    ((net.minecraftforge.registries.RegistryObject<?>) ATTRIBUTE_REGISTRY_MAX_MANA.get(null)).get();
        } catch (Throwable t) {
            return null;
        }
    }
}