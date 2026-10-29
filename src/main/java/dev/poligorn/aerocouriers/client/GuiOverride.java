package dev.poligorn.aerocouriers.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Optional hand-drawn replacements for elements that are still drawn in code.
 * A missing file keeps the code path. Drop a PNG at the documented path and reload resources.
 */
public final class GuiOverride {
    public static final ResourceLocation WAYBILL_BOARD = texture("waybill_board.png");
    public static final ResourceLocation WAYBILL_PAPER = texture("waybill_paper.png");
    public static final ResourceLocation WAYBILL_CLIP = texture("waybill_clip.png");
    public static final ResourceLocation WAYBILL_STAMP = texture("waybill_stamp.png");
    public static final ResourceLocation WAYBILL_SPUR = texture("waybill_spur.png");
    public static final ResourceLocation HIDDEN_STACKS_BADGE = texture("hidden_stacks_badge.png");
    public static final ResourceLocation ANTENNA_ARC_SEND = texture("antenna_arc_send.png");
    public static final ResourceLocation ANTENNA_ARC_RECEIVE = texture("antenna_arc_receive.png");
    public static final ResourceLocation ANTENNA_LAMP_FLASH = texture("antenna_lamp_flash.png");

    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();

    private GuiOverride() {
    }

    public static ResourceLocation texture(String file) {
        return ResourceLocation.fromNamespaceAndPath("aerocouriers", "textures/gui/override/" + file);
    }

    /** Stretches the PNG over {@code w}×{@code h}. Returns false when the file is absent. */
    public static boolean blit(GuiGraphics graphics, ResourceLocation id, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return false;
        }
        int[] size = size(id);
        if (size == null) {
            return false;
        }
        graphics.blit(id, x, y, w, h, 0.0F, 0.0F, size[0], size[1], size[0], size[1]);
        return true;
    }

    private static int[] size(ResourceLocation id) {
        int[] cached = SIZES.get(id);
        if (cached != null) {
            return cached[0] < 0 ? null : cached;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }
        Optional<Resource> resource = minecraft.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            SIZES.put(id, new int[] {-1, -1});
            return null;
        }
        try (InputStream in = resource.get().open()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                SIZES.put(id, new int[] {-1, -1});
                return null;
            }
            int[] read = {image.getWidth(), image.getHeight()};
            SIZES.put(id, read);
            return read;
        } catch (IOException ignored) {
            SIZES.put(id, new int[] {-1, -1});
            return null;
        }
    }

    /** Resource reload (F3+T) can add or replace an override. */
    public static void clearCache() {
        SIZES.clear();
    }
}
