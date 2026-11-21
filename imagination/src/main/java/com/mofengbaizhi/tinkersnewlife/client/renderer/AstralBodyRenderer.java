package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.astral.AstralBodyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;

/** An inventory-less server anchor rendered via a private, unsimulated client mannequin. */
public final class AstralBodyRenderer extends EntityRenderer<AstralBodyEntity> {
    private final PlayerRenderer wide, slim;
    private final Map<AstralBodyEntity, WeakReference<Mannequin>> models = new WeakHashMap<>();

    public AstralBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
        wide = new BodyPlayerRenderer(context, false);
        slim = new BodyPlayerRenderer(context, true);
        shadowRadius = .5F;
    }
    private static final class BodyPlayerRenderer extends PlayerRenderer {
        BodyPlayerRenderer(EntityRendererProvider.Context c, boolean slim) { super(c, slim); }
        @Override protected void renderNameTag(AbstractClientPlayer p, Component text, PoseStack pose, MultiBufferSource buffers, int light) {}
    }
    static final class Mannequin extends RemotePlayer {
        private final AstralBodyEntity body;
        Mannequin(ClientLevel level, GameProfile profile, AstralBodyEntity body) {
            super(level, profile);
            this.body = body;
            setMainArm(body.mainArm());
            for (EquipmentSlot slot : EquipmentSlot.values()) setItemSlot(slot, body.visualItem(slot));
            moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot());
            yBodyRot = yBodyRotO = body.getYRot();
            yHeadRot = yHeadRotO = body.headYaw();
            xRotO = body.getXRot();
            xCloak = xCloakO = getX(); yCloak = yCloakO = getY(); zCloak = zCloakO = getZ();
        }
        @Override public boolean isModelPartShown(PlayerModelPart part) { return body != null && body.shows(part); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public ResourceLocation getSkinTextureLocation() {
            var player = level().getPlayerByUUID(getUUID());
            return player instanceof AbstractClientPlayer p ? p.getSkinTextureLocation() : super.getSkinTextureLocation();
        }
        @Override public String getModelName() {
            var player = level().getPlayerByUUID(getUUID());
            return player instanceof AbstractClientPlayer p ? p.getModelName() : super.getModelName();
        }
    }
    @Override public void render(AstralBodyEntity body, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (body.ownerId() == null || !(body.level() instanceof ClientLevel level)) return;
        var ref = models.get(body);
        var proxy = ref == null ? null : ref.get();
        if (proxy == null) {
            var net = Minecraft.getInstance().getConnection();
            var info = net == null ? null : net.getPlayerInfo(body.ownerId());
            GameProfile profile = info != null ? info.getProfile() : new GameProfile(body.ownerId(), body.profileName());
            proxy = new Mannequin(level, profile, body);
            models.put(body, new WeakReference<>(proxy));
        }
        ("slim".equals(proxy.getModelName()) ? slim : wide).render(proxy, body.getYRot(), partial, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(AstralBodyEntity entity) {
        return entity.ownerId() == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.getDefaultSkin(entity.ownerId());
    }
}
