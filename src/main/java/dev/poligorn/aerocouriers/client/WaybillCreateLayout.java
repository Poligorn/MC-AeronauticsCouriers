package dev.poligorn.aerocouriers.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Pixel layout for the paper clipboard, mapped from the 640×360 mockup (GUI scale 3).
 * Screens large enough for that mockup keep the existing coordinate scale.
 * Smaller screens keep the same unscaled layout and shrink the whole clipboard with {@link #fitScale}.
 */
public final class WaybillCreateLayout {
    public static final int DESIGN_W = 640;
    public static final int DESIGN_H = 360;
    public static final int SLOT = 18;
    public static final int FIELD_H = 14;
    public static final int CHIP_H = 13;
    public static final int BTN_H = 20;
    public static final int PICK_W = 66;
    public static final int PICK_H = 16;

    public final double scale;
    public final int ox;
    public final int oy;
    public final int screenW;
    public final int screenH;
    /** Pose scale applied on top of this layout. 1 when the screen already fits the mockup. */
    public final double fitScale;
    public final double fitX;
    public final double fitY;

    public final int boardX0;
    public final int boardY0;
    public final int boardX1;
    public final int boardY1;
    public final int paperX0;
    public final int paperY0;
    public final int paperX1;
    public final int paperY1;
    public final int clipCx;
    public final int contentX0;
    public final int contentX1;
    public final int leftColX1;
    public final int rightColX0;
    public final int fieldX0;

    public final int headerY;
    public final int headerSubY;
    public final int headerRuleY;
    public final int section1Y;
    public final int slotY;
    public final int occupiedY;
    public final int weightY;
    public final int weightBarY;
    public final int cargoMetaY;
    public final int rewardY;
    public final int collateralY;
    public final int collateralErrY;
    public final int deadlineY;
    public final int rankY;
    public final int rankRaiseY;
    public final int section3Y;
    public final int routeY;
    public final int summaryY;
    public final int section4Y;
    public final int payY;
    public final int riskY;
    public final int footerRuleY;
    public final int buttonY;
    public final int cancelX;
    public final int cancelW;
    public final int submitX;
    public final int submitW;
    public final int pickX;
    public final int pickY;
    public final int zoneChipX;
    public final int zoneChipY;
    public final int durationChipX;
    /** Collateral row and the cargo value line are left out and the rows below move up. */
    public final boolean noCollateral;

    private WaybillCreateLayout(
            double scale, int ox, int oy, int screenW, int screenH,
            double fitScale, double fitX, double fitY,
            int boardX0, int boardY0, int boardX1, int boardY1,
            int paperX0, int paperY0, int paperX1, int paperY1,
            int clipCx, int contentX0, int contentX1, int leftColX1, int rightColX0, int fieldX0,
            int headerY, int headerSubY, int headerRuleY, int section1Y, int slotY, int occupiedY,
            int weightY, int weightBarY, int cargoMetaY, int rewardY, int collateralY, int collateralErrY,
            int deadlineY, int rankY, int rankRaiseY, int section3Y, int routeY, int summaryY,
            int section4Y, int payY, int riskY, int footerRuleY, int buttonY,
            int cancelX, int cancelW, int submitX, int submitW,
            int pickX, int pickY, int zoneChipX, int zoneChipY, int durationChipX, boolean noCollateral) {
        this.noCollateral = noCollateral;
        this.scale = scale;
        this.ox = ox;
        this.oy = oy;
        this.screenW = screenW;
        this.screenH = screenH;
        this.fitScale = fitScale;
        this.fitX = fitX;
        this.fitY = fitY;
        this.boardX0 = boardX0;
        this.boardY0 = boardY0;
        this.boardX1 = boardX1;
        this.boardY1 = boardY1;
        this.paperX0 = paperX0;
        this.paperY0 = paperY0;
        this.paperX1 = paperX1;
        this.paperY1 = paperY1;
        this.clipCx = clipCx;
        this.contentX0 = contentX0;
        this.contentX1 = contentX1;
        this.leftColX1 = leftColX1;
        this.rightColX0 = rightColX0;
        this.fieldX0 = fieldX0;
        this.headerY = headerY;
        this.headerSubY = headerSubY;
        this.headerRuleY = headerRuleY;
        this.section1Y = section1Y;
        this.slotY = slotY;
        this.occupiedY = occupiedY;
        this.weightY = weightY;
        this.weightBarY = weightBarY;
        this.cargoMetaY = cargoMetaY;
        this.rewardY = rewardY;
        this.collateralY = collateralY;
        this.collateralErrY = collateralErrY;
        this.deadlineY = deadlineY;
        this.rankY = rankY;
        this.rankRaiseY = rankRaiseY;
        this.section3Y = section3Y;
        this.routeY = routeY;
        this.summaryY = summaryY;
        this.section4Y = section4Y;
        this.payY = payY;
        this.riskY = riskY;
        this.footerRuleY = footerRuleY;
        this.buttonY = buttonY;
        this.cancelX = cancelX;
        this.cancelW = cancelW;
        this.submitX = submitX;
        this.submitW = submitW;
        this.pickX = pickX;
        this.pickY = pickY;
        this.zoneChipX = zoneChipX;
        this.zoneChipY = zoneChipY;
        this.durationChipX = durationChipX;
    }

    public int sx(int designX) {
        return ox + (int) Math.round(designX * scale);
    }

    public int sy(int designY) {
        return oy + (int) Math.round(designY * scale);
    }

    public int sw(int designW) {
        return Math.max(1, (int) Math.round(designW * scale));
    }

    /** Screen pixel → layout pixel. Identity when {@link #fitScale} is 1. */
    public double localX(double screenX) {
        return (screenX - fitX) / fitScale;
    }

    public double localY(double screenY) {
        return (screenY - fitY) / fitScale;
    }

    public int localXi(int screenX) {
        return (int) Math.floor(localX(screenX));
    }

    public int localYi(int screenY) {
        return (int) Math.floor(localY(screenY));
    }

    public static WaybillCreateLayout compute(int screenW, int screenH) {
        return compute(screenW, screenH, false);
    }

    public static WaybillCreateLayout compute(int screenW, int screenH, boolean noCollateral) {
        double fit = Math.min(screenW / (double) DESIGN_W, screenH / (double) DESIGN_H);
        if (fit < 1.0) {
            double fitX = (screenW - DESIGN_W * fit) / 2.0;
            double fitY = (screenH - DESIGN_H * fit) / 2.0;
            return fromScale(1.0, 0, 0, screenW, screenH, fit, fitX, fitY, noCollateral);
        }
        int ox = (int) Math.round((screenW - DESIGN_W * fit) / 2.0);
        int oy = (int) Math.round((screenH - DESIGN_H * fit) / 2.0);
        return fromScale(fit, ox, oy, screenW, screenH, 1.0, 0.0, 0.0, noCollateral);
    }

    private static WaybillCreateLayout fromScale(double s, int ox, int oy, int screenW, int screenH,
                                                 double fitScale, double fitX, double fitY, boolean noCollateral) {
        int bx0 = map(ox, s, 40);
        int by0 = map(oy, s, 36);
        int bx1 = map(ox, s, 599);
        int by1 = map(oy, s, 354);
        int px0 = map(ox, s, 68);
        int py0 = map(oy, s, 50);
        int px1 = map(ox, s, 571);
        int py1 = map(oy, s, 343);
        int cx0 = px0 + Math.max(6, (int) Math.round(8 * s));
        int cx1 = px1 - Math.max(6, (int) Math.round(8 * s));
        int y = py0 + Math.max(10, (int) Math.round(15 * s));
        int headerY = y;
        int headerSubY = y + 11;
        int headerRuleY = y + 23;
        int section1Y = y + 27;
        int slotY = section1Y + 14;
        int occupiedY = slotY + 22;
        int weightY = occupiedY + 14;
        int weightBarY = weightY + 11;
        int cargoMetaY = weightBarY + 13;
        int leftColX1 = cx0 + Math.max(160, (int) Math.round(226 * s));
        int rightColX0 = cx0 + Math.max(180, (int) Math.round(246 * s));
        int fieldX0 = rightColX0 + Math.max(80, (int) Math.round(112 * s));
        int rewardY = section1Y + 14;
        int collateralY = noCollateral ? rewardY : rewardY + 18;
        int collateralErrY = collateralY + 16;
        int deadlineY = noCollateral ? rewardY + 20 : collateralY + 28;
        int rankY = deadlineY + 19;
        int rankRaiseY = rankY + 11;
        int leftEnd = cargoMetaY + (noCollateral ? 21 : 33);
        int rightEnd = rankRaiseY + 9;
        int section3Y = Math.max(leftEnd, rightEnd) + 6;
        int routeY = section3Y + 14;
        int summaryY = routeY + 27;
        int section4Y = summaryY + 17;
        int payY = section4Y + 14;
        int riskY = payY + 13;
        int buttonY = py1 - 9 - BTN_H;
        int footerRuleY = buttonY - 7;
        int cancelW = 64;
        int submitW = 132;
        int pickX = cx0 + Math.max(200, (int) Math.round(306 * s));
        int pickY = routeY - 3;
        int zoneChipW = 26;
        int zoneChipX = cx1 - 3 * (zoneChipW + 2) + 2;
        int durationChipX = cx1 - 5 * (22 + 2) + 2;
        return new WaybillCreateLayout(
                s, ox, oy, screenW, screenH, fitScale, fitX, fitY,
                bx0, by0, bx1, by1, px0, py0, px1, py1,
                (bx0 + bx1) / 2, cx0, cx1, leftColX1, rightColX0, fieldX0,
                headerY, headerSubY, headerRuleY, section1Y, slotY, occupiedY,
                weightY, weightBarY, cargoMetaY, rewardY, collateralY, collateralErrY,
                deadlineY, rankY, rankRaiseY, section3Y, routeY, summaryY,
                section4Y, payY, riskY, footerRuleY, buttonY,
                cx0, cancelW, cx1 - submitW + 1, submitW,
                pickX, pickY, zoneChipX, routeY + 10, durationChipX, noCollateral);
    }

    private static int map(int origin, double scale, int design) {
        return origin + (int) Math.round(design * scale);
    }

    public int paperW() {
        return paperX1 - paperX0 + 1;
    }

    public int paperH() {
        return paperY1 - paperY0 + 1;
    }

    public int boardW() {
        return boardX1 - boardX0 + 1;
    }

    public int boardH() {
        return boardY1 - boardY0 + 1;
    }

    public int durationChipX(int index) {
        return contentX1 - (5 - index) * (22 + 2) + 2;
    }

    public int zoneChipX(int index) {
        return contentX1 - (3 - index) * (26 + 2) + 2;
    }

    public int customMinutesX() {
        return rightColX0 + 38;
    }

    public int customMinutesW() {
        return 48;
    }

    public int fieldRight() {
        return contentX1;
    }

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
                for (boolean peace : new boolean[] {false, true}) {
                    WaybillCreateLayout layout = compute(w, h, peace);
                    String id = res[0] + "x" + res[1] + "@s" + scale + (peace ? "/peace" : "");
                    failures.addAll(layout.validate(id, w, h));
                }
            }
        }
        return failures;
    }

    List<String> validate(String id, int screenW, int screenH) {
        if (fitScale < 1.0) {
            List<String> failures = validatePlaced(id, DESIGN_W, DESIGN_H);
            double clipTop = boardY0 - 27.0;
            double left = fitX + boardX0 * fitScale;
            double top = fitY + clipTop * fitScale;
            double right = fitX + (boardX1 + 4) * fitScale;
            double bottom = fitY + (boardY1 + 5) * fitScale;
            if (left < -1.5 || top < -1.5 || right > screenW + 1.5 || bottom > screenH + 1.5) {
                failures.add(id + ": fitted clipboard " + (int) left + "," + (int) top + "-"
                        + (int) right + "," + (int) bottom + " off " + screenW + "x" + screenH);
            }
            return failures;
        }
        return validatePlaced(id, screenW, screenH);
    }

    private List<String> validatePlaced(String id, int screenW, int screenH) {
        List<String> failures = new ArrayList<>();
        if (boardX0 < 0 || boardY0 < 0 || boardX1 >= screenW + 4 || boardY1 >= screenH + 8) {
            failures.add(id + ": board " + boardX0 + "," + boardY0 + "-" + boardX1 + "," + boardY1
                    + " off " + screenW + "x" + screenH);
        }
        if (paperX0 <= boardX0 || paperY0 <= boardY0 || paperX1 >= boardX1 || paperY1 >= boardY1) {
            failures.add(id + ": paper not inside board");
        }
        double boardShare = (boardY1 - boardY0 + 1) / (double) screenH;
        if (boardShare < 0.78 || boardShare > 0.96) {
            failures.add(id + ": board height share " + boardShare);
        }
        if (slotY + SLOT > occupiedY) {
            failures.add(id + ": slots overlap occupied line");
        }
        if (!noCollateral && rewardY + FIELD_H > collateralY) {
            failures.add(id + ": reward field overlaps collateral");
        }
        if (buttonY + BTN_H > paperY1 + 2) {
            failures.add(id + ": buttons below paper");
        }
        if (cancelX + cancelW + 8 > submitX) {
            failures.add(id + ": footer buttons overlap");
        }
        if (section3Y < rankRaiseY) {
            failures.add(id + ": route overlaps conditions");
        }
        if (riskY + 14 > footerRuleY) {
            failures.add(id + ": risk overlaps footer");
        }
        return failures;
    }
}
