package dev.poligorn.aerocouriers.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Pixel layout for {@link WaybillCreateScreen}. Positions come from the scaled screen size and the
 * current font line height — no hardcoded stacks that overlap when GUI scale or language changes.
 */
public final class WaybillCreateLayout {
    public static final int FIELD_H = 20;
    public static final int SLOT = 18;
    public static final int PAD = 8;
    public static final int GAP = 6;
    public static final int COLS = 3;
    public static final int MONEY_COLS = 4;

    public final int panelW;
    public final int panelH;
    public final int left;
    public final int top;
    public final int paperLeft;
    public final int paperTop;
    public final int paperW;
    public final int paperH;
    public final int clipX;
    public final int clipY;
    public final int innerW;
    public final int lineH;

    public final int headerY;
    public final int balanceX;
    public final int originY;
    public final int weightY;

    public final int cargoX;
    public final int cargoY;
    public final int cargoRows;
    public final int cargoH;

    public final int destTitleY;
    public final int coordLabelY;
    public final int coordFieldY;
    public final int[] coordX;
    public final int[] coordW;
    public final int mapBtnX;
    public final int mapBtnW;
    public final int mapBtnY;

    public final int stationX;
    public final int stationY;
    public final int stationW;
    public final int waypointX;
    public final int waypointW;

    public final int moneyLabelY;
    public final int moneyFieldY;
    public final int[] moneyX;
    public final int[] moneyW;

    public final int infoY;
    public final int infoH;
    public final int infoW;

    public final int buttonY;
    public final int cancelX;
    public final int cancelW;
    public final int submitX;
    public final int submitW;
    public final int noticeX;
    public final int noticeW;

    private WaybillCreateLayout(
            int panelW, int panelH, int left, int top,
            int paperLeft, int paperTop, int paperW, int paperH, int clipX, int clipY,
            int innerW, int lineH,
            int headerY, int balanceX, int originY, int weightY,
            int cargoX, int cargoY, int cargoRows, int cargoH,
            int destTitleY, int coordLabelY, int coordFieldY, int[] coordX, int[] coordW,
            int mapBtnX, int mapBtnW, int mapBtnY,
            int stationX, int stationY, int stationW, int waypointX, int waypointW,
            int moneyLabelY, int moneyFieldY, int[] moneyX, int[] moneyW,
            int infoY, int infoH, int infoW,
            int buttonY, int cancelX, int cancelW, int submitX, int submitW, int noticeX, int noticeW) {
        this.panelW = panelW;
        this.panelH = panelH;
        this.left = left;
        this.top = top;
        this.paperLeft = paperLeft;
        this.paperTop = paperTop;
        this.paperW = paperW;
        this.paperH = paperH;
        this.clipX = clipX;
        this.clipY = clipY;
        this.innerW = innerW;
        this.lineH = lineH;
        this.headerY = headerY;
        this.balanceX = balanceX;
        this.originY = originY;
        this.weightY = weightY;
        this.cargoX = cargoX;
        this.cargoY = cargoY;
        this.cargoRows = cargoRows;
        this.cargoH = cargoH;
        this.destTitleY = destTitleY;
        this.coordLabelY = coordLabelY;
        this.coordFieldY = coordFieldY;
        this.coordX = coordX;
        this.coordW = coordW;
        this.mapBtnX = mapBtnX;
        this.mapBtnW = mapBtnW;
        this.mapBtnY = mapBtnY;
        this.stationX = stationX;
        this.stationY = stationY;
        this.stationW = stationW;
        this.waypointX = waypointX;
        this.waypointW = waypointW;
        this.moneyLabelY = moneyLabelY;
        this.moneyFieldY = moneyFieldY;
        this.moneyX = moneyX;
        this.moneyW = moneyW;
        this.infoY = infoY;
        this.infoH = infoH;
        this.infoW = infoW;
        this.buttonY = buttonY;
        this.cancelX = cancelX;
        this.cancelW = cancelW;
        this.submitX = submitX;
        this.submitW = submitW;
        this.noticeX = noticeX;
        this.noticeW = noticeW;
    }

    public static WaybillCreateLayout compute(
            int screenW, int screenH, int lineH, int cargoCount,
            boolean showMapButton, boolean showWaypointList, int mapLabelW) {
        int line = Math.max(8, lineH);
        int over = WaybillCreateGui.CLIP_OVERHANG;
        int bottomMargin = 6;
        int maxW = Math.max(16, screenW - 8);
        int maxH = Math.max(16, screenH - 8);
        int panelW = clamp(screenW - 40, Math.min(360, maxW), Math.min(520, maxW));
        panelW = Math.min(panelW, maxW);
        int panelH = clamp(screenH - 24, Math.min(220, maxH), Math.min(440, maxH));
        panelH = Math.min(panelH, maxH);
        int maxPanelH = screenH - 2 * over - 2 - bottomMargin;
        if (panelH > maxPanelH) {
            panelH = Math.max(WaybillCreateLayout.FIELD_H * 6, maxPanelH);
        }
        int left = Math.max(0, (screenW - panelW) / 2);
        int clipY = Math.max(over + 2, (screenH - panelH - over) / 2);
        int top = clipY + over;
        int paperLeft = left + WaybillCreateGui.FRAME_PAD;
        int paperTop = top + WaybillCreateGui.FRAME_PAD;
        int paperW = panelW - WaybillCreateGui.FRAME_PAD * 2;
        int paperH = panelH - WaybillCreateGui.FRAME_PAD * 2;
        int clipX = left + (panelW - WaybillCreateGui.CLIP_W) / 2;
        int innerW = Math.max(32, paperW - PAD * 2);
        int cargoRows = Math.max(1, (Math.max(0, cargoCount) + 8) / 9);
        boolean compact = panelH < 280;
        boolean stackedMeta = panelH >= 220;
        int labelGap = compact ? 1 : 2;
        int sectionGap = panelH < 200 ? 2 : compact ? 3 : 5;

        int buttonY = paperTop + paperH - PAD - FIELD_H;
        int y = clipY + WaybillCreateGui.CLIP_H + 4;
        int headerY = y;
        y += line + (compact ? 2 : 4);
        int originY = y;
        int weightY = stackedMeta ? y + line + 1 : y;
        y += (stackedMeta ? line * 2 : line) + (compact ? 2 : 4);

        int destTitleH = compact ? 0 : line + labelGap;
        int coordBlockH = line + labelGap + FIELD_H;
        int stationH = FIELD_H;
        int moneyH = line + labelGap + FIELD_H;
        int minInfo = compact ? line : line * 2;
        int midH = destTitleH + coordBlockH + stationH + moneyH
                + sectionGap * (compact ? 3 : 4);
        int cargoMin = cargoRows * SLOT;
        int cargoBudget = buttonY - 4 - minInfo - midH - y;
        int cargoH;
        if (cargoBudget >= cargoMin) {
            cargoH = cargoMin + (cargoBudget - cargoMin) * 40 / 100;
        } else {
            cargoH = Math.max(line, cargoBudget);
        }
        int cargoY = y;
        y += cargoH + sectionGap;

        int destTitleY = y;
        if (!compact) {
            y += destTitleH;
        }
        int coordLabelY = y;
        int coordFieldY = y + line + labelGap;
        y += coordBlockH + sectionGap;

        int mapBtnW = 0;
        int mapBtnX = paperLeft + PAD;
        int mapBtnY = coordFieldY;
        boolean mapOnCoordRow = showMapButton && innerW >= 280;
        if (showMapButton) {
            mapBtnW = clamp(mapLabelW + 16, 88, Math.min(160, innerW / 3));
        }
        int coordBudget = innerW;
        if (mapOnCoordRow) {
            coordBudget = Math.max(90, innerW - mapBtnW - GAP);
            mapBtnX = paperLeft + PAD + coordBudget + GAP;
        }
        int[] coordX = new int[COLS];
        int[] coordW = new int[COLS];
        splitRow(paperLeft + PAD, coordBudget, COLS, coordX, coordW);

        int stationY = y;
        if (showMapButton && !mapOnCoordRow) {
            mapBtnY = stationY;
            mapBtnX = paperLeft + PAD;
        }
        int stationX = paperLeft + PAD;
        int stationW = innerW;
        int waypointX = paperLeft + PAD;
        int waypointW = 0;
        if (showMapButton && !mapOnCoordRow) {
            stationX = paperLeft + PAD + mapBtnW + GAP;
            stationW = Math.max(64, innerW - mapBtnW - GAP);
        }
        if (showWaypointList) {
            int pair = showMapButton && !mapOnCoordRow ? stationW : innerW;
            int half = Math.max(64, (pair - GAP) / 2);
            stationW = half;
            waypointX = stationX + stationW + GAP;
            waypointW = Math.max(64, pair - stationW - GAP);
        }
        y += stationH + sectionGap;

        int moneyLabelY = y;
        int moneyFieldY = y + line + labelGap;
        int[] moneyX = new int[MONEY_COLS];
        int[] moneyW = new int[MONEY_COLS];
        splitRow(paperLeft + PAD, innerW, MONEY_COLS, moneyX, moneyW);
        y += moneyH + sectionGap;

        int infoY = Math.min(y, buttonY - minInfo);
        int infoH = Math.max(line, buttonY - 4 - infoY);
        int infoW = innerW;

        int cancelW = 80;
        int submitW = Math.min(118, Math.max(80, innerW / 3));
        int cancelX = paperLeft + PAD;
        int submitX = paperLeft + paperW - PAD - submitW;
        int noticeX = cancelX + cancelW + GAP;
        int noticeW = Math.max(16, submitX - GAP - noticeX);
        int balanceX = paperLeft + Math.max(PAD + 80, paperW - PAD - Math.min(140, innerW / 2));

        return new WaybillCreateLayout(
                panelW, panelH, left, top, paperLeft, paperTop, paperW, paperH, clipX, clipY, innerW, line,
                headerY, balanceX, originY, weightY,
                paperLeft + PAD, cargoY, cargoRows, cargoH,
                destTitleY, coordLabelY, coordFieldY, coordX, coordW,
                mapBtnX, mapBtnW, mapBtnY,
                stationX, stationY, stationW, waypointX, waypointW,
                moneyLabelY, moneyFieldY, moneyX, moneyW,
                infoY, infoH, infoW,
                buttonY, cancelX, cancelW, submitX, submitW, noticeX, noticeW);
    }

    /**
     * GUI coordinates are {@code realPixels / scale}. Checks scales 2–4 at common resolutions.
     */
    public static List<String> checkCommonScales() {
        int[][] screens = {
                {1920, 1080}, {1280, 720}, {1366, 768}, {1600, 900}, {2560, 1440}
        };
        int[] scales = {2, 3, 4};
        List<String> failures = new ArrayList<>();
        for (int[] res : screens) {
            for (int scale : scales) {
                int w = res[0] / scale;
                int h = res[1] / scale;
                for (boolean map : new boolean[] {false, true}) {
                    for (boolean list : new boolean[] {false, true}) {
                        for (int cargo : new int[] {0, 9, 27}) {
                            WaybillCreateLayout layout = compute(w, h, 9, cargo, map, list, 110);
                            String id = res[0] + "x" + res[1] + "@s" + scale + " cargo=" + cargo
                                    + " map=" + map + " list=" + list;
                            failures.addAll(layout.validate(id, w, h));
                        }
                    }
                }
            }
        }
        return failures;
    }

    List<String> validate(String id, int screenW, int screenH) {
        List<String> failures = new ArrayList<>();
        if (panelW > screenW || panelH > screenH) {
            failures.add(id + ": panel " + panelW + "x" + panelH + " exceeds screen " + screenW + "x" + screenH);
        }
        if (left < 0 || top < 0 || left + panelW > screenW + 1 || top + panelH > screenH + 1) {
            failures.add(id + ": panel origin out of screen");
        }
        if (clipY < 0) {
            failures.add(id + ": clipboard clip above screen (clipY=" + clipY + ")");
        }
        if (top + panelH > screenH + 1) {
            failures.add(id + ": panel bottom below screen (bottom=" + (top + panelH) + ", screenH=" + screenH + ")");
        }
        if (coordLabelY + lineH > coordFieldY) {
            failures.add(id + ": XYZ labels overlap coord fields (" + coordLabelY + "+" + lineH + ">" + coordFieldY + ")");
        }
        if (coordFieldY + FIELD_H > stationY) {
            failures.add(id + ": coord fields overlap station row");
        }
        if (moneyLabelY + lineH > moneyFieldY) {
            failures.add(id + ": money labels overlap money fields");
        }
        if (stationY + FIELD_H > moneyLabelY) {
            failures.add(id + ": station row overlaps money labels");
        }
        if (moneyFieldY + FIELD_H > infoY) {
            failures.add(id + ": money fields overlap info");
        }
        if (infoY + 2 > buttonY) {
            failures.add(id + ": info overlaps buttons");
        }
        if (weightY + lineH > cargoY) {
            failures.add(id + ": weight overlaps cargo");
        }
        int cargoBottom = cargoY + Math.max(lineH, Math.min(SLOT, cargoH));
        if (cargoBottom > destTitleY && destTitleY < coordLabelY) {
            failures.add(id + ": cargo overlaps destination title");
        } else if (cargoBottom > coordLabelY) {
            failures.add(id + ": cargo overlaps destination");
        }
        for (int i = 0; i < COLS; i++) {
            if (coordW[i] < 24) {
                failures.add(id + ": coord column " + i + " too narrow: " + coordW[i]);
            }
        }
        for (int i = 0; i < MONEY_COLS; i++) {
            if (moneyW[i] < 24) {
                failures.add(id + ": money column " + i + " too narrow: " + moneyW[i]);
            }
        }
        if (mapBtnW > 0 && mapBtnY == coordFieldY && coordX[COLS - 1] + coordW[COLS - 1] > mapBtnX) {
            failures.add(id + ": coord fields overlap map button");
        }
        if (waypointW > 0 && stationX + stationW > waypointX) {
            failures.add(id + ": station overlaps waypoint list");
        }
        if (cancelX + cancelW > noticeX || noticeX + 8 > submitX) {
            failures.add(id + ": footer buttons overlap");
        }
        return failures;
    }

    private static void splitRow(int start, int budget, int columns, int[] xs, int[] ws) {
        int colW = Math.max(24, (budget - GAP * (columns - 1)) / columns);
        int used = colW * columns + GAP * (columns - 1);
        int slack = Math.max(0, budget - used);
        int cursor = start;
        for (int i = 0; i < columns; i++) {
            xs[i] = cursor;
            ws[i] = colW + (i == columns - 1 ? slack : 0);
            cursor += ws[i] + GAP;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (min > max) {
            return max;
        }
        return Math.max(min, Math.min(max, value));
    }
}
