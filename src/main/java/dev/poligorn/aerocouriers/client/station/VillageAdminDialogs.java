package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.net.StationSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import dev.poligorn.aerocouriers.village.VillageBoardPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class VillageAdminDialogs {
    protected final StationSnapshotPayload snapshot;
    protected int x;
    protected int y;
    protected int w;
    protected int h;

    protected VillageAdminDialogs(StationSnapshotPayload snapshot) {
        this.snapshot = snapshot;
    }

    public void layout(int screenW, int screenH) {
        w = Math.min(320, screenW - 24);
        h = 150;
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;
    }

    public abstract void init(VillageAdminScreen screen);

    public abstract void render(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    public abstract boolean mouseClicked(double mouseX, double mouseY);

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public static final class Rename extends VillageAdminDialogs {
        private EditBox field;

        public Rename(StationSnapshotPayload snapshot) {
            super(snapshot);
        }

        @Override
        public void layout(int screenW, int screenH) {
            w = Math.min(300, screenW - 24);
            h = 148;
            x = (screenW - w) / 2;
            y = (screenH - h) / 2;
        }

        @Override
        public void init(VillageAdminScreen screen) {
            field = new EditBox(screen.getFont(), x + 12, y + 36, w - 24, 16,
                    Component.translatable("ui.aerocouriers.village_admin.dialog.rename.field"));
            field.setMaxLength(VillageBoardPolicy.NAME_MAX);
            field.setValue(snapshot.name());
            screen.addDialogWidget(field);
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            CorkBoardGui.paper(graphics, x, y, w, h, CorkBoardGui.PANEL, CorkBoardGui.WOOD_LINE);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.dialog.rename.title"),
                    x + 12, y + 8, CorkBoardGui.HEADER, false);
            String value = field == null ? snapshot.name() : field.getValue();
            int len = value.strip().length();
            graphics.drawString(font, len + "/" + VillageBoardPolicy.NAME_MAX,
                    x + w - 12 - font.width(len + "/" + VillageBoardPolicy.NAME_MAX), y + 22, CorkBoardGui.HEADER, false);
            boolean taken = snapshot.view().nameTaken(value) || value.strip().isEmpty()
                    || value.strip().length() > VillageBoardPolicy.NAME_MAX;
            boolean same = value.strip().equals(snapshot.name());
            Component status = taken && !same
                    ? Component.translatable("ui.aerocouriers.village_admin.dialog.rename.taken", value.strip())
                    : Component.translatable("ui.aerocouriers.village_admin.dialog.rename.free");
            graphics.drawString(font, status, x + 12, y + 58,
                    taken && !same ? CorkBoardGui.RED : CorkBoardGui.TEAL, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.dialog.rename.hint",
                    snapshot.name()), x + 12, y + 70, CorkBoardGui.INK_MUTED, false);
            boolean canSave = !taken && !same;
            drawButtons(graphics, font, mouseX, mouseY, canSave,
                    Component.translatable("ui.aerocouriers.village_admin.dialog.rename.save"));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY) {
            if (overCancel(mouseX, mouseY)) {
                return true;
            }
            String value = field == null ? "" : field.getValue().strip();
            boolean taken = snapshot.view().nameTaken(value) || value.isEmpty();
            if (overOk(mouseX, mouseY) && !taken && !value.equals(snapshot.name())) {
                PacketDistributor.sendToServer(VillageAdminPayload.text(snapshot.stationPos(),
                        VillageAdminPayload.Action.RENAME, value));
                return true;
            }
            return contains(mouseX, mouseY);
        }
    }

    public static final class Reputation extends VillageAdminDialogs {
        private EditBox field;
        private Mode mode = Mode.SET;

        public Reputation(StationSnapshotPayload snapshot) {
            super(snapshot);
        }

        @Override
        public void layout(int screenW, int screenH) {
            w = Math.min(340, screenW - 24);
            h = 168;
            x = (screenW - w) / 2;
            y = (screenH - h) / 2;
        }

        @Override
        public void init(VillageAdminScreen screen) {
            field = new EditBox(screen.getFont(), x + 80, y + 40, 48, 16,
                    Component.translatable("ui.aerocouriers.village_admin.dialog.rep.amount"));
            field.setMaxLength(3);
            field.setValue(Integer.toString(snapshot.reputation()));
            screen.addDialogWidget(field);
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            CorkBoardGui.paper(graphics, x, y, w, h, CorkBoardGui.PANEL, CorkBoardGui.WOOD_LINE);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.dialog.rep.title",
                    snapshot.name()), x + 12, y + 8, CorkBoardGui.HEADER, false);
            int mx = x + 12;
            int my = y + 22;
            for (Mode m : Mode.values()) {
                Component label = Component.translatable(m.key);
                int cw = CorkBoardGui.chipW(font, label);
                CorkBoardGui.chip(graphics, font, mx, my, cw, 14, label, mode == m, false);
                mx += cw + 4;
            }
            graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.dialog.rep.amount"),
                    x + 12, y + 44, CorkBoardGui.HEADER, false);
            int next = preview();
            CorkBoardGui.reputationBar(graphics, x + 12, y + 64, w - 24, 7, snapshot.reputation(),
                    VillageBoardPolicy.REPUTATION_MAX);
            CorkBoardGui.reputationBar(graphics, x + 12, y + 78, w - 24, 7, next, VillageBoardPolicy.REPUTATION_MAX);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.dialog.rep.preview",
                    snapshot.reputation(), next), x + 12, y + 90, CorkBoardGui.HEADER, false);
            graphics.drawString(font, Component.translatable("ui.aerocouriers.village_admin.dialog.rep.warn"),
                    x + 12, y + 104, CorkBoardGui.GOLD, false);
            drawButtons(graphics, font, mouseX, mouseY, true,
                    Component.translatable("ui.aerocouriers.village_admin.dialog.rep.apply",
                            snapshot.reputation(), next));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY) {
            int mx = x + 12;
            int my = y + 22;
            Font font = Minecraft.getInstance().font;
            for (Mode m : Mode.values()) {
                Component label = Component.translatable(m.key);
                int cw = CorkBoardGui.chipW(font, label);
                if (mouseX >= mx && mouseX < mx + cw && mouseY >= my && mouseY < my + 14) {
                    mode = m;
                    if (m == Mode.SET && field != null) {
                        field.setValue(Integer.toString(snapshot.reputation()));
                    } else if (field != null && field.getValue().equals(Integer.toString(snapshot.reputation()))) {
                        field.setValue("1");
                    }
                    return false;
                }
                mx += cw + 4;
            }
            if (overCancel(mouseX, mouseY)) {
                return true;
            }
            if (overOk(mouseX, mouseY)) {
                int amount = parse();
                if (mode == Mode.SET) {
                    PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                            VillageAdminPayload.Action.REP_SET, amount));
                } else {
                    PacketDistributor.sendToServer(VillageAdminPayload.amount(snapshot.stationPos(),
                            VillageAdminPayload.Action.REP_ADD, mode == Mode.SUB ? -amount : amount));
                }
                return true;
            }
            return contains(mouseX, mouseY);
        }

        private int parse() {
            try {
                return Math.max(0, Integer.parseInt(field == null ? "0" : field.getValue().strip()));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private int preview() {
            int amount = parse();
            int current = snapshot.reputation();
            return switch (mode) {
                case SET -> VillageBoardPolicy.clampReputation(amount);
                case ADD -> VillageBoardPolicy.clampReputation(current + amount);
                case SUB -> VillageBoardPolicy.clampReputation(current - amount);
            };
        }
    }

    public static final class Confirm extends VillageAdminDialogs {
        private final Component title;
        private final Component body;
        private final VillageAdminPayload.Action action;

        public Confirm(StationSnapshotPayload snapshot, Component title, Component body,
                       VillageAdminPayload.Action action) {
            super(snapshot);
            this.title = title;
            this.body = body;
            this.action = action;
        }

        @Override
        public void layout(int screenW, int screenH) {
            w = Math.min(280, screenW - 24);
            h = 110;
            x = (screenW - w) / 2;
            y = (screenH - h) / 2;
        }

        @Override
        public void init(VillageAdminScreen screen) {
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            CorkBoardGui.paper(graphics, x, y, w, h, CorkBoardGui.PANEL, CorkBoardGui.WOOD_LINE);
            graphics.drawString(font, title, x + 12, y + 10, CorkBoardGui.HEADER, false);
            int ty = y + 28;
            for (var line : font.split(body, w - 24)) {
                graphics.drawString(font, line, x + 12, ty, CorkBoardGui.INK_MUTED, false);
                ty += 10;
            }
            drawButtons(graphics, font, mouseX, mouseY, true,
                    Component.translatable("ui.aerocouriers.village_admin.dialog.confirm"));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY) {
            if (overCancel(mouseX, mouseY)) {
                return true;
            }
            if (overOk(mouseX, mouseY)) {
                PacketDistributor.sendToServer(VillageAdminPayload.of(snapshot.stationPos(), action));
                return true;
            }
            return contains(mouseX, mouseY);
        }
    }

    enum Mode {
        SET("ui.aerocouriers.village_admin.dialog.rep.set"),
        ADD("ui.aerocouriers.village_admin.dialog.rep.add"),
        SUB("ui.aerocouriers.village_admin.dialog.rep.sub");
        final String key;

        Mode(String key) {
            this.key = key;
        }
    }

    protected void drawButtons(GuiGraphics graphics, Font font, int mouseX, int mouseY, boolean okEnabled,
                               Component ok) {
        int bw = (w - 36) / 2;
        int by = y + h - 26;
        boolean cancelHover = overCancel(mouseX, mouseY);
        boolean okHover = okEnabled && overOk(mouseX, mouseY);
        CorkBoardGui.button(graphics, font, x + 12, by, bw, 16,
                Component.translatable("ui.aerocouriers.village_admin.dialog.cancel"),
                cancelHover, true, false);
        CorkBoardGui.button(graphics, font, x + w - 12 - bw, by, bw, 16, ok, okHover, okEnabled, false);
    }

    protected boolean overCancel(double mouseX, double mouseY) {
        int bw = (w - 36) / 2;
        int by = y + h - 26;
        return mouseX >= x + 12 && mouseX < x + 12 + bw && mouseY >= by && mouseY < by + 16;
    }

    protected boolean overOk(double mouseX, double mouseY) {
        int bw = (w - 36) / 2;
        int by = y + h - 26;
        return mouseX >= x + w - 12 - bw && mouseX < x + w - 12 && mouseY >= by && mouseY < by + 16;
    }

    protected static int clamp(int value) {
        return Mth.clamp(value, VillageBoardPolicy.REPUTATION_MIN, VillageBoardPolicy.REPUTATION_MAX);
    }
}
