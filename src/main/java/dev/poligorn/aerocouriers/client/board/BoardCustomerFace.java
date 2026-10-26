package dev.poligorn.aerocouriers.client.board;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** 8×8 player head (face + hat layer) for board rows. */
public final class BoardCustomerFace {
    private static final int SIZE = 8;

    private BoardCustomerFace() {
    }

    public static void draw(GuiGraphics graphics, int x, int y, Optional<UUID> customerId) {
        ResourceLocation skin = customerId
                .map(BoardCustomerFace::skinFor)
                .orElse(DefaultPlayerSkin.getDefaultTexture());
        graphics.blit(skin, x, y, SIZE, SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(skin, x, y, SIZE, SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    private static ResourceLocation skinFor(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getSkin().texture();
            }
        }
        return DefaultPlayerSkin.get(id).texture();
    }

    public static void drawVillageIcon(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(dev.poligorn.aerocouriers.client.AerocouriersGuiSprites.ICON_VILLAGE, x, y, SIZE, SIZE);
    }
}
