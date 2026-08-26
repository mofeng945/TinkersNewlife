package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

public class DragonboneTrait extends Modifier {

    /** 静态缓存 ModifierId，避免每次命中都 new ResourceLocation */
    private static final ModifierId DRAGONBONE_ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dragonbone"));

    /**
     * 工具上是否有龙骨特性。
     * ⭐ 原实现遍历全部修饰符做 instanceof，每次命中都执行；
     * 改用 getModifierLevel(id) 直接查询（O(1) 语义）。
     */
    public static boolean hasDragonbone(IToolStackView tool) {
        return tool.getModifierLevel(DRAGONBONE_ID) > 0;
    }

    public static boolean isConductionActive(IToolStackView tool) {
        return hasDragonbone(tool);
    }
}
