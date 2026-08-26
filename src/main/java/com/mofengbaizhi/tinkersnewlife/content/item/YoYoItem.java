package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * 悠悠球（Yo-Yo）
 * <p>
 * 功能：
 * <ul>
 *   <li>右键向视线方向发射一个旋转的悠悠球，飞行距离 10 格</li>
 *   <li>触碰到生物或抵达最远飞行距离后停滞 3 秒</li>
 *   <li>停滞期间每 2 tick 对触碰到的实体造成伤害，每帧伤害为玩家此时总伤害的 10%</li>
 *   <li>停滞结束后飞回，飞回期间触碰到的实体同样受到帧伤</li>
 *   <li>发射后玩家手与悠悠球实体之间绘制一条连线，颜色取弓弦材质颜色</li>
 * </ul>
 */
public class YoYoItem extends ModifiableItem {

    public static final ToolDefinition YO_YO_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "yo_yo"));

    /** 弓弦部件在工具中的索引（tool_definitions 第 4 个部件） */
    private static final int BOWSTRING_PART_INDEX = 3;

    public YoYoItem(Properties properties) {
        super(properties, YO_YO_DEFINITION);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // ✅ 使用 ToolHelper 安全获取
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null || tool.isBroken()) {
            return InteractionResultHolder.fail(stack);
        }

        // 计算玩家当前总伤害（工具攻击伤害，用于帧伤 = 10% 总伤害）
        float damage = tool.getStats().get(ToolStats.ATTACK_DAMAGE);

        YoYoEntity yoYo = new YoYoEntity(level, player, damage, getBowstringVariantId(tool));
        // 沿玩家视线方向发射
        var look = player.getLookAngle();
        yoYo.launch(look.x, look.y, look.z);
        level.addFreshEntity(yoYo);

        // 消耗少量耐久
        if (!player.isCreative() && level instanceof ServerLevel) {
            stack.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(hand));
        }

        return InteractionResultHolder.success(stack);
    }

    /**
     * 获取弓弦部件的材质 VariantId 字符串（渲染器据此在客户端取材质颜色）。
     * 无弓弦部件或未知材质时返回空串（渲染器用默认白色）。
     */
    private static String getBowstringVariantId(ToolStack tool) {
        MaterialNBT materials = tool.getMaterials();
        if (materials == null || materials.size() <= BOWSTRING_PART_INDEX) {
            return "";
        }
        MaterialVariant variant = materials.get(BOWSTRING_PART_INDEX);
        if (variant == null || variant.isUnknown()) {
            return "";
        }
        return variant.getVariant().toString();
    }
}
