package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.entity.ModifiableArrow;

@Mod.EventBusSubscriber(modid = "tinkersnewlife")
public final class SeaSerpentModifier extends NoLevelsModifier {
    private static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife", "annoying_sea_serpent"));
    private static final String LAUNCHED = "tnlSeaSerpentLaunched";

    private static boolean applies(AbstractArrow arrow) {
        return IntegrationLoader.isIceAndFire() && arrow instanceof ModifiableArrow modifiable
                && !modifiable.getDisplayTool().isEmpty()
                && ToolStack.from(modifiable.getDisplayTool()).getModifierLevel(ID) > 0;
    }

    public static boolean swims(AbstractArrow arrow) {
        return arrow.isInWater() && applies(arrow);
    }

    public static boolean swimsAtImpact(AbstractArrow arrow, net.minecraft.world.phys.Vec3 impact) {
        var pos = net.minecraft.core.BlockPos.containing(impact);
        var fluid = arrow.level().getFluidState(pos);
        return fluid.is(net.minecraft.tags.FluidTags.WATER)
                && impact.y < pos.getY() + fluid.getHeight(arrow.level(), pos) && applies(arrow);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void launch(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide || !(event.getEntity() instanceof ModifiableArrow arrow)
                || arrow.getPersistentData().getBoolean(LAUNCHED) || !applies(arrow)) return;
        var velocity = arrow.getDeltaMovement();
        double speed = velocity.length();
        if (!Double.isFinite(speed) || speed < 1e-6) return;
        arrow.getPersistentData().putBoolean(LAUNCHED, true);
        arrow.setDeltaMovement(velocity.scale((speed + 2) / speed));
    }
}
