package dev.poligorn.aerocouriers.client;

import net.minecraft.resources.ResourceLocation;

/**
 * UV slices of Create 6.0.10 GUI atlases ({@code create:textures/gui/...}).
 * Coordinates match {@code com.simibubi.create.foundation.gui.AllGuiTextures} / {@code AllIcons}
 * on tag {@code mc1.21.1-6.0.10}. Textures stay in Create — a resource pack that recolors
 * Create (e.g. Create Style Interface) recolors these windows too.
 */
public enum CreateGuiTextures {
    STOCK_HEADER("stock_keeper", 0, 0, 256, 36),
    STOCK_BODY("stock_keeper", 0, 48, 256, 20),
    STOCK_FOOTER("stock_keeper", 0, 80, 256, 80),
    STOCK_ROW("stock_keeper", 37, 48, 182, 20),
    STOCK_SLOT("stock_keeper", 32, 200, 18, 18),
    STOCK_SCROLL_TOP("stock_keeper", 219, 192, 5, 4),
    STOCK_SCROLL_PAD("stock_keeper", 219, 196, 5, 1),
    STOCK_SCROLL_MID("stock_keeper", 219, 197, 5, 9),
    STOCK_SCROLL_BOT("stock_keeper", 219, 207, 5, 5),
    STOCK_BANNER_L("stock_keeper", 64, 228, 8, 16),
    STOCK_BANNER_M("stock_keeper", 73, 228, 1, 16),
    STOCK_BANNER_R("stock_keeper", 75, 228, 8, 16),

    BUTTON("widgets", 0, 0, 18, 18),
    BUTTON_HOVER("widgets", 18, 0, 18, 18),
    BUTTON_DOWN("widgets", 36, 0, 18, 18),
    BUTTON_GREEN("widgets", 72, 0, 18, 18),
    BUTTON_DISABLED("widgets", 90, 0, 18, 18),
    SPEECH_BG("widgets", 0, 24, 8, 8),
    SPEECH_BORDER("widgets", 8, 24, 8, 8),

    BRASS_TL("value_settings", 65, 9, 4, 4),
    BRASS_TR("value_settings", 70, 9, 4, 4),
    BRASS_BL("value_settings", 65, 19, 4, 4),
    BRASS_BR("value_settings", 70, 19, 4, 4),
    BRASS_LEFT("value_settings", 65, 14, 3, 4),
    BRASS_RIGHT("value_settings", 71, 14, 3, 4),
    BRASS_TOP("value_settings", 0, 24, 256, 3),
    BRASS_BOTTOM("value_settings", 0, 27, 256, 3),
    BRASS_FILL("value_settings", 80, 9, 1, 1),

    TEXTBOX_TOP("assemble", 1, 179, 150, 18),
    TEXTBOX_MIDDLE("assemble", 1, 198, 150, 1),
    TEXTBOX_BOTTOM("assemble", 1, 200, 150, 4),

    ICON_CONFIRM("icons", 0, 16, 16, 16),
    ICON_TRASH("icons", 16, 0, 16, 16),
    ICON_CLOSE("icons", 16, 144, 16, 16);

    public static final int FONT_COLOR = 0x575F7A;
    static final int ATLAS = 256;

    public final ResourceLocation location;
    public final int u;
    public final int v;
    public final int width;
    public final int height;

    CreateGuiTextures(String file, int u, int v, int width, int height) {
        this.location = ResourceLocation.fromNamespaceAndPath("create", "textures/gui/" + file + ".png");
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
    }

    public void blit(net.minecraft.client.gui.GuiGraphics graphics, int x, int y) {
        graphics.blit(location, x, y, width, height, (float) u, (float) v, width, height, ATLAS, ATLAS);
    }
}
