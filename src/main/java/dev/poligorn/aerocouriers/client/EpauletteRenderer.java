package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.registry.ModComponents;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Epaulettes for the worn license. Ported from SkyPact Pilot Wings ({@code ru.skypact.pilotwings.EpauletteRenderer},
 * MIT, same author); the rank now comes from the {@code aerocouriers:license_rank} component instead of the item id.
 */
public class EpauletteRenderer implements ICurioRenderer {
    // Lifts the glow layer over the base one to avoid z-fighting (~0.016 px).
    private static final float GLOW_OFFSET = 0.001f;

    private static final Map<LicenseRank, ResourceLocation> BASE = new EnumMap<>(LicenseRank.class);
    private static final Map<LicenseRank, ResourceLocation> GLOW = new EnumMap<>(LicenseRank.class);

    static {
        for (LicenseRank rank : LicenseRank.values()) {
            String name = rank.getSerializedName();
            BASE.put(rank, AeronauticsCouriers.id("textures/entity/epaulette_" + name + ".png"));
            GLOW.put(rank, AeronauticsCouriers.id("textures/entity/epaulette_" + name + "_glow.png"));
        }
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack, SlotContext ctx, PoseStack pose,
            RenderLayerParent<T, M> parent, MultiBufferSource buffer,
            int light, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch) {

        if (!(parent.getModel() instanceof HumanoidModel<?> model)) {
            return;
        }

        LicenseRank rank = stack.getOrDefault(ModComponents.LICENSE_RANK.get(), LicenseRank.RANK1);
        boolean slim = isSlimArms(ctx);

        VertexConsumer base = buffer.getBuffer(RenderType.entityCutoutNoCull(BASE.get(rank)));
        renderShoulder(pose, base, model.rightArm, light, true, slim, 0f);
        renderShoulder(pose, base, model.leftArm, light, false, slim, 0f);

        VertexConsumer glow = buffer.getBuffer(RenderType.eyes(GLOW.get(rank)));
        renderShoulder(pose, glow, model.rightArm, LightTexture.FULL_BRIGHT, true, slim, GLOW_OFFSET);
        renderShoulder(pose, glow, model.leftArm, LightTexture.FULL_BRIGHT, false, slim, GLOW_OFFSET);
    }

    private static void renderShoulder(PoseStack pose, VertexConsumer vc, ModelPart arm, int light,
                                       boolean right, boolean slim, float yOffset) {
        pose.pushPose();
        arm.translateAndRotate(pose);

        float w = slim ? 3f : 4f;
        float x0 = (right ? (slim ? -2f : -3f) : -1f) / 16f;
        float x1 = x0 + w / 16f;
        // Up is -Y in model space.
        float y = (-2.25f / 16f) - yOffset;
        float z0 = -2f / 16f;
        float z1 = 2f / 16f;

        PoseStack.Pose p = pose.last();

        float uL = right ? 1f : 0f;
        float uR = right ? 0f : 1f;

        // Top face (+Y normal, seen from above)
        vertex(vc, p, x0, y, z0, uL, 0, light, 1f);
        vertex(vc, p, x0, y, z1, uL, 1, light, 1f);
        vertex(vc, p, x1, y, z1, uR, 1, light, 1f);
        vertex(vc, p, x1, y, z0, uR, 0, light, 1f);

        // Bottom face (-Y normal, seen from below)
        vertex(vc, p, x1, y, z0, uR, 0, light, -1f);
        vertex(vc, p, x1, y, z1, uR, 1, light, -1f);
        vertex(vc, p, x0, y, z1, uL, 1, light, -1f);
        vertex(vc, p, x0, y, z0, uL, 0, light, -1f);

        pose.popPose();
    }

    private static boolean isSlimArms(SlotContext ctx) {
        return ctx.entity() instanceof AbstractClientPlayer player
                && player.getSkin().model() == PlayerSkin.Model.SLIM;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               int light, float ny) {
        vc.addVertex(p, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, 0f, ny, 0f);
    }
}
