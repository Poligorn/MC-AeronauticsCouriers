package dev.poligorn.aerocouriers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 20×20 button on the player inventory (GDD §10.1). Position comes from {@code aerocouriers-client.toml};
 * INVENTORY_SIDE tracks {@code leftPos} when the recipe book opens.
 */
public final class InventoryBoardButton extends Button {
    private final InventoryScreen inventory;

    private InventoryBoardButton(InventoryScreen inventory, int x, int y) {
        super(x, y, 20, 20, Component.empty(), pressed -> ClientBoard.requestOpen(0), DEFAULT_NARRATION);
        this.inventory = inventory;
        setTooltip(Tooltip.create(Component.translatable("ui.aerocouriers.open")));
    }

    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inventory)) {
            return;
        }
        if (CouriersClientConfig.BUTTON_POSITION.get() == CouriersClientConfig.ButtonPosition.HIDDEN) {
            return;
        }
        int[] pos = position(inventory);
        event.addListener(new InventoryBoardButton(inventory, pos[0], pos[1]));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int[] pos = position(inventory);
        setX(pos[0]);
        setY(pos[1]);
        RenderSystem.enableBlend();
        WidgetSprites sprites = AerocouriersGuiSprites.buttonSprites();
        ResourceLocation sprite = sprites.get(active, isHoveredOrFocused());
        graphics.blitSprite(sprite, getX(), getY(), getWidth(), getHeight());
        graphics.renderFakeItem(new ItemStack(ModItems.PILOT_LICENSE.get()), getX() + 2, getY() + 2);
    }

    static int[] position(InventoryScreen screen) {
        return switch (CouriersClientConfig.BUTTON_POSITION.get()) {
            case CORNER -> new int[] {2, screen.height - 22};
            case INVENTORY_SIDE -> new int[] {Math.max(0, screen.getGuiLeft() - 22), screen.getGuiTop() + 10};
            case CUSTOM -> new int[] {
                    CouriersClientConfig.BUTTON_CUSTOM_X.get(),
                    CouriersClientConfig.BUTTON_CUSTOM_Y.get()
            };
            case HIDDEN -> new int[] {-100, -100};
        };
    }
}
