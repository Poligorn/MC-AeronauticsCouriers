package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.board.PixelGlyphs;
import dev.poligorn.aerocouriers.client.board.TabletDraw;
import dev.poligorn.aerocouriers.client.board.TabletPalette;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.TutorialIntentPayload;
import dev.poligorn.aerocouriers.task.TaskView;
import dev.poligorn.aerocouriers.tutorial.TutorialData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Tablet-only onboarding chrome: the welcome and «complete» windows, the teal coach-mark (frame + «Step N of 4» plate
 * with a «skip» link) and the Profile checklist. Progress comes from the server task {@code tutorial}.
 */
final class BoardTutorialUi {
    static final int STEPS = 4;
    private static final int TEAL_BG = 0xF0102A26;
    private static final int MODAL_W = 260;
    private static final int PAD = 8;
    private static final int LINE = 10;

    enum Modal { NONE, WELCOME, COMPLETE }

    record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Runnable toBoard;
    private final Runnable toProfile;
    private @Nullable Rect primaryButton;
    private @Nullable Rect secondaryButton;
    private @Nullable Rect skipLink;

    BoardTutorialUi(Runnable toBoard, Runnable toProfile) {
        this.toBoard = toBoard;
        this.toProfile = toProfile;
    }

    static Modal modal() {
        BoardSnapshotPayload.Extras extras = ClientBoard.extras();
        if (extras.tutorialCompletionPending()) {
            return Modal.COMPLETE;
        }
        if (extras.tutorialEnabled()
                && TutorialData.State.byOrdinal(extras.tutorialState()) == TutorialData.State.OFFERED) {
            return Modal.WELCOME;
        }
        return Modal.NONE;
    }

    static TutorialData.State state() {
        return TutorialData.State.byOrdinal(ClientBoard.extras().tutorialState());
    }

    /** Current step 0..3, or -1 when no tutorial is running. */
    static int step() {
        BoardSnapshotPayload.Extras extras = ClientBoard.extras();
        if (!extras.tutorialEnabled() || !state().inProgress()) {
            return -1;
        }
        Optional<TaskView> task = ClientTasks.byId("tutorial");
        if (task.isPresent()) {
            return task.get().currentStep();
        }
        return state() == TutorialData.State.STARTED ? 0 : state() == TutorialData.State.ACCEPTED ? 2 : 3;
    }

    // --- modal windows -------------------------------------------------------------------------------------------

    void renderModal(GuiGraphics graphics, Font font, int cx, int cy, int cw, int ch, int mx, int my) {
        Modal modal = modal();
        primaryButton = null;
        secondaryButton = null;
        if (modal == Modal.NONE) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);
        graphics.fill(cx, cy, cx + cw, cy + ch, TabletPalette.SCRIM);
        int w = Math.min(MODAL_W, cw - 16);
        int textW = w - PAD * 2;
        List<Line> lines = modal == Modal.WELCOME ? welcomeLines(font, textW) : completeLines(font, textW);
        int bodyH = 0;
        for (Line line : lines) {
            bodyH += line.height();
        }
        int headerH = 14;
        int h = headerH + PAD + bodyH + PAD + TabletDraw.BUTTON_H + PAD;
        int x = cx + (cw - w) / 2;
        int y = cy + Math.max(4, (ch - h) / 2);
        TabletDraw.panel(graphics, x, y, w, h, headerH);
        String header = Component.translatable(modal == Modal.WELCOME
                ? "ui.aerocouriers.tutorial.welcome.header" : "ui.aerocouriers.tutorial.complete.header").getString();
        TabletDraw.bold(graphics, font, TabletDraw.trunc(font, header, textW), x + PAD, y + 4, TabletPalette.TEAL);
        int ty = y + headerH + PAD;
        for (Line line : lines) {
            line.draw(graphics, font, x + PAD, ty, textW);
            ty += line.height();
        }
        int by = y + h - PAD - TabletDraw.BUTTON_H;
        int bw = (textW - 6) / 2;
        String primary = Component.translatable(modal == Modal.WELCOME
                ? "ui.aerocouriers.tutorial.welcome.start" : "ui.aerocouriers.tutorial.complete.board").getString();
        String secondary = Component.translatable(modal == Modal.WELCOME
                ? "ui.aerocouriers.tutorial.welcome.skip" : "ui.aerocouriers.tutorial.complete.profile").getString();
        primaryButton = new Rect(x + PAD, by, bw, TabletDraw.BUTTON_H);
        secondaryButton = new Rect(x + PAD + bw + 6, by, bw, TabletDraw.BUTTON_H);
        TabletDraw.button(graphics, font, primaryButton.x(), by, bw, primary, TabletDraw.ButtonKind.PRIMARY, null,
                primaryButton.contains(mx, my));
        TabletDraw.button(graphics, font, secondaryButton.x(), by, bw, secondary, TabletDraw.ButtonKind.SECONDARY,
                null, secondaryButton.contains(mx, my));
        graphics.pose().popPose();
    }

    /** @return true while a modal is open (it swallows every click) */
    boolean modalClicked(double mx, double my) {
        Modal modal = modal();
        if (modal == Modal.NONE) {
            return false;
        }
        boolean primary = primaryButton != null && primaryButton.contains(mx, my);
        boolean secondary = secondaryButton != null && secondaryButton.contains(mx, my);
        if (!primary && !secondary) {
            return true;
        }
        ClientCouriersSounds.playUi(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
        if (modal == Modal.WELCOME) {
            ClientBoard.tutorial(primary ? TutorialIntentPayload.Op.START : TutorialIntentPayload.Op.SKIP, 0);
            if (primary) {
                toProfile.run();
            }
        } else {
            ClientBoard.tutorial(TutorialIntentPayload.Op.ACK_COMPLETE, 0);
            (primary ? toBoard : toProfile).run();
        }
        return true;
    }

    private static List<Line> welcomeLines(Font font, int width) {
        List<Line> out = new ArrayList<>();
        out.add(Line.text(font, Component.translatable("ui.aerocouriers.tutorial.welcome.title"), width,
                TabletPalette.TXT, 4));
        out.add(Line.text(font, Component.translatable("ui.aerocouriers.tutorial.welcome.body"), width,
                TabletPalette.SEC, 4));
        out.add(Line.text(font, Component.translatable("ui.aerocouriers.tutorial.welcome.issuer"), width,
                TabletPalette.SEC, 0));
        return out;
    }

    private static List<Line> completeLines(Font font, int width) {
        int xp = ClientBoard.extras().tutorialXp();
        List<Line> out = new ArrayList<>();
        out.add(Line.text(font, Component.translatable("ui.aerocouriers.tutorial.complete.title"), width,
                TabletPalette.TXT, 2));
        out.add(Line.xpBar(Component.translatable("ui.aerocouriers.tutorial.xp", xp)));
        out.add(Line.text(font, Component.translatable("ui.aerocouriers.tutorial.complete.reward",
                dev.poligorn.aerocouriers.economy.MoneyFormat.component(0)), width, TabletPalette.SEC, 4));
        out.add(Line.text(font, Component.translatable("ui.aerocouriers.tutorial.complete.now"), width,
                TabletPalette.TXT, 2));
        for (int i = 1; i <= 3; i++) {
            out.add(Line.text(font, Component.literal("• ").append(
                    Component.translatable("ui.aerocouriers.tutorial.complete.can." + i)), width, TabletPalette.SEC, 0));
        }
        return out;
    }

    private interface Line {
        int height();

        void draw(GuiGraphics graphics, Font font, int x, int y, int width);

        static Line text(Font font, Component text, int width, int color, int gap) {
            List<FormattedCharSequence> split = font.split(text, width);
            return new Line() {
                @Override
                public int height() {
                    return split.size() * LINE + gap;
                }

                @Override
                public void draw(GuiGraphics graphics, Font f, int x, int y, int w) {
                    int yy = y;
                    for (FormattedCharSequence seq : split) {
                        graphics.drawString(f, seq, x, yy, color, false);
                        yy += LINE;
                    }
                }
            };
        }

        static Line xpBar(Component label) {
            return new Line() {
                @Override
                public int height() {
                    return 16;
                }

                @Override
                public void draw(GuiGraphics graphics, Font f, int x, int y, int w) {
                    String s = label.getString();
                    int lw = TabletDraw.boldWidth(f, s);
                    TabletDraw.bold(graphics, f, s, x, y + 1, TabletPalette.TEAL);
                    int bx = x + lw + 6;
                    TabletDraw.box(graphics, bx, y + 1, x + w - 1, y + 8, TabletPalette.SLOT_BG,
                            TabletPalette.TEAL);
                    graphics.fill(bx + 1, y + 2, x + w - 1, y + 8, TabletPalette.PRIMARY_BG);
                }
            };
        }
    }

    // --- coach-mark ----------------------------------------------------------------------------------------------

    /**
     * Teal frame around {@code target} (when on screen) and the plate, hanging from {@code anchorY} when
     * {@code fromTop}, otherwise standing on it.
     *
     * @param onTop draw above overlays (details window open)
     */
    void renderCoach(GuiGraphics graphics, Font font, int step, Component text, @Nullable Rect target, int x,
                     int anchorY, boolean fromTop, int w, int mx, int my, boolean onTop) {
        skipLink = null;
        if (step < 0) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, onTop ? 520.0F : 300.0F);
        if (target != null) {
            float pulse = 0.5F + 0.5F * (float) Math.sin((System.currentTimeMillis() % 1400L) / 1400.0 * Math.PI * 2);
            int alpha = 0x90 + (int) (0x6F * pulse);
            int color = (alpha << 24) | (TabletPalette.TEAL & 0xFFFFFF);
            TabletDraw.outline(graphics, target.x() - 2, target.y() - 2, target.x() + target.w() + 1,
                    target.y() + target.h() + 1, color);
            TabletDraw.outline(graphics, target.x() - 1, target.y() - 1, target.x() + target.w(),
                    target.y() + target.h(), (alpha / 2 << 24) | (TabletPalette.TEAL & 0xFFFFFF));
        }
        int textW = w - 12;
        List<FormattedCharSequence> body = font.split(text, textW);
        int h = 6 + LINE + 2 + body.size() * LINE + 4;
        int y = fromTop ? anchorY : anchorY - h;
        TabletDraw.box(graphics, x, y, x + w - 1, y + h - 1, TEAL_BG, TabletPalette.TEAL);
        String head = Component.translatable("ui.aerocouriers.tutorial.coach.step", step + 1, STEPS).getString();
        TabletDraw.bold(graphics, font, head, x + 6, y + 5, TabletPalette.TEAL);
        int px = x + 6 + TabletDraw.boldWidth(font, head) + 6;
        for (int i = 0; i < STEPS; i++) {
            int c = i <= step ? TabletPalette.TEAL : TabletPalette.DIVIDER;
            graphics.fill(px + i * 7, y + 6, px + i * 7 + 5, y + 11, c);
        }
        String skip = Component.translatable("ui.aerocouriers.tutorial.skip").getString();
        int sw = font.width(skip);
        int sx = x + w - 6 - sw;
        skipLink = new Rect(sx - 2, y + 3, sw + 4, LINE + 2);
        int sc = skipLink.contains(mx, my) ? TabletPalette.TXT : TabletPalette.SEC;
        graphics.drawString(font, skip, sx, y + 5, sc, false);
        TabletDraw.hline(graphics, sx, sx + sw - 1, y + 14, sc);
        int ty = y + 6 + LINE + 2;
        for (FormattedCharSequence seq : body) {
            graphics.drawString(font, seq, x + 6, ty, TabletPalette.TXT, false);
            ty += LINE;
        }
        graphics.pose().popPose();
    }

    boolean coachClicked(double mx, double my) {
        if (skipLink != null && skipLink.contains(mx, my)) {
            skipLink = null;
            ClientCouriersSounds.playUi(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
            ClientBoard.tutorial(TutorialIntentPayload.Op.SKIP, 0);
            return true;
        }
        return false;
    }

    void clearCoach() {
        skipLink = null;
    }

    // --- Profile checklist ---------------------------------------------------------------------------------------

    static int checklistHeight() {
        return step() < 0 ? 0 : 6 + LINE + 2 + STEPS * LINE + 4;
    }

    static void renderChecklist(GuiGraphics graphics, Font font, int x, int y, int w) {
        int current = step();
        if (current < 0) {
            return;
        }
        int h = checklistHeight();
        TabletDraw.box(graphics, x, y, x + w - 1, y + h - 1, TEAL_BG, TabletPalette.TEAL);
        String head = Component.translatable("tutorial.aerocouriers.task.title", current, STEPS).getString();
        TabletDraw.bold(graphics, font, TabletDraw.trunc(font, head, w - 12), x + 6, y + 5, TabletPalette.TEAL);
        int ty = y + 6 + LINE + 2;
        for (int i = 0; i < STEPS; i++) {
            boolean done = i < current;
            boolean now = i == current;
            int color = done ? TabletPalette.TEAL : now ? TabletPalette.TXT : TabletPalette.LOCKC;
            if (done) {
                PixelGlyphs.draw(graphics, x + 6, ty, PixelGlyphs.CHECK, color);
            } else {
                graphics.fill(x + 7, ty + 2, x + 11, ty + 6, now ? TabletPalette.TEAL : TabletPalette.DIVIDER);
            }
            String s = Component.translatable("tutorial.aerocouriers.step." + (i + 1)).getString();
            graphics.drawString(font, TabletDraw.trunc(font, s, w - 24), x + 16, ty, color, false);
            ty += LINE;
        }
    }
}
