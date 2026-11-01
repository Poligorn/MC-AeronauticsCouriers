package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.net.NameStationPayload;
import dev.poligorn.aerocouriers.net.OpenStationNamePayload;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Placement naming dialog (0.3.2). Cork-style like the village-admin Rename window.
 * Cancel / Esc keeps the random name assigned on first load.
 */
public final class StationNameScreen extends Screen {
    private static final int W = 288;
    /** Compact like mockup 02: buttons sit one line under the hint. */
    private static final int H = 110;

    private final OpenStationNamePayload payload;
    private int x;
    private int y;
    private @Nullable EditBox field;
    private @Nullable String qaValue;

    public StationNameScreen(OpenStationNamePayload payload) {
        super(Component.translatable("ui.aerocouriers.station_name.title"));
        this.payload = payload;
    }

    /** QA: pre-fill the field after init (file-hook screenshots). */
    public void qaSetValue(String value) {
        qaValue = value;
        if (field != null) {
            field.setValue(value);
        }
    }

    public EditBox field() {
        return field;
    }

    @Override
    protected void init() {
        x = (width - W) / 2;
        y = (height - H) / 2;
        field = new EditBox(font, x + 14, y + 33, Math.max(8, 268 - 8), 16,
                Component.translatable("ui.aerocouriers.station_name.prompt"));
        field.setMaxLength(VillageBoardPolicy.NAME_MAX);
        field.setBordered(false);
        field.setTextColor(CorkPalette.INPUT);
        field.setFilter(value -> value.codePoints().allMatch(VillageBoardPolicy::isAllowedNameChar));
        if (qaValue != null) {
            field.setValue(qaValue);
        }
        addRenderableWidget(field);
        setInitialFocus(field);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        x = (width - W) / 2;
        y = (height - H) / 2;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        CorkBoardGui.fullBright();
        CorkBoardGui.dialog(graphics, x, y, W, H);
        CorkBoardGui.bold(graphics, font, Component.translatable("ui.aerocouriers.station_name.title"),
                x + 9, y + 6, CorkPalette.ADMIN_TITLE);

        String value = typed();
        int len = value.length();
        String counter = len + "/" + VillageBoardPolicy.NAME_MAX;
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.station_name.prompt"),
                x + 10, y + 22, CorkPalette.ADMIN_VALUE);
        CorkBoardGui.text(graphics, font, counter,
                x + W - 12 - font.width(counter), y + 22, CorkPalette.ADMIN_VALUE);

        boolean empty = value.isEmpty();
        boolean taken = payload.nameTaken(value);
        boolean invalid = !empty && !VillageBoardPolicy.hasValidNameChars(value);
        boolean canSave = !empty && !taken && !invalid && value.length() <= VillageBoardPolicy.NAME_MAX;
        CorkBoardGui.input(graphics, x + 10, y + 33, 268, 16, field != null && field.isFocused(), taken || invalid);

        Component status;
        int statusColor;
        if (empty) {
            status = Component.translatable("ui.aerocouriers.station_name.empty");
            statusColor = CorkPalette.ADMIN_HINT;
        } else if (taken) {
            status = Component.translatable("ui.aerocouriers.station_name.taken", value);
            statusColor = CorkPalette.ADMIN_ERROR;
        } else if (invalid) {
            status = Component.translatable("ui.aerocouriers.station_name.invalid");
            statusColor = CorkPalette.ADMIN_ERROR;
        } else {
            status = Component.translatable("ui.aerocouriers.station_name.free");
            statusColor = CorkPalette.ADMIN_OK;
        }
        CorkBoardGui.text(graphics, font, status, x + 12, y + 54, statusColor);
        CorkBoardGui.text(graphics, font, Component.translatable("ui.aerocouriers.station_name.hint"),
                x + 12, y + 66, CorkPalette.ADMIN_HINT);

        int bw = (W - 36) / 2;
        int by = y + H - 24;
        CorkBoardGui.button(graphics, font, x + 12, by, bw, 16,
                Component.translatable("ui.aerocouriers.station_name.cancel"),
                overCancel(mouseX, mouseY), true, CorkBoardGui.ButtonStyle.NORMAL);
        CorkBoardGui.button(graphics, font, x + W - 12 - bw, by, bw, 16,
                Component.translatable("ui.aerocouriers.station_name.save"),
                canSave && overOk(mouseX, mouseY), canSave, CorkBoardGui.ButtonStyle.TEAL);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overCancel(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (overOk(mouseX, mouseY) && canSave()) {
            save();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / KP_ENTER
            if (canSave()) {
                save();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void save() {
        if (!canSave()) {
            return;
        }
        PacketDistributor.sendToServer(new NameStationPayload(payload.pos(), typed()));
        onClose();
    }

    private boolean canSave() {
        String value = typed();
        return !value.isEmpty()
                && !payload.nameTaken(value)
                && VillageBoardPolicy.hasValidNameChars(value)
                && value.length() <= VillageBoardPolicy.NAME_MAX;
    }

    private String typed() {
        return field == null ? "" : field.getValue().strip();
    }

    private boolean overCancel(double mouseX, double mouseY) {
        int bw = (W - 36) / 2;
        int by = y + H - 24;
        return mouseX >= x + 12 && mouseX < x + 12 + bw && mouseY >= by && mouseY < by + 16;
    }

    private boolean overOk(double mouseX, double mouseY) {
        int bw = (W - 36) / 2;
        int by = y + H - 24;
        return mouseX >= x + W - 12 - bw && mouseX < x + W - 12 && mouseY >= by && mouseY < by + 16;
    }
}
