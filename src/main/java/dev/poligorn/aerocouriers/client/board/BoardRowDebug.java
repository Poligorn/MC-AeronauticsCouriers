package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import org.slf4j.Logger;

/** Optional layout trace: {@code -Daerocouriers.logBoardRows=true}. */
public final class BoardRowDebug {
    private static final Logger LOG = AeronauticsCouriers.LOGGER;
    private static final boolean ENABLED = Boolean.getBoolean("aerocouriers.logBoardRows");

    private BoardRowDebug() {
    }

    public static void logRow(
            int index,
            int frameX,
            int frameY,
            int frameW,
            int frameH,
            int line1Y,
            int line2Y,
            int textLeft,
            int textRight,
            int mapX,
            int crateX,
            int detailsX,
            int detailsW,
            int buttonY
    ) {
        if (!ENABLED || index > 6) {
            return;
        }
        int iconSize = BoardRowLayout.ICON;
        LOG.info(
                "board row {} frame=({},{} {}x{}) line1Y={} line2Y={} text=[{},{}] "
                        + "map=({},{} {}x{}) crate=({},{} {}x{}) details=({},{} w={} h={}) btnY={}",
                index, frameX, frameY, frameW, frameH, line1Y, line2Y, textLeft, textRight,
                mapX, buttonY, iconSize, iconSize,
                crateX, buttonY, iconSize, iconSize,
                detailsX, buttonY, detailsW, BoardRowMetrics.BUTTON_HEIGHT, buttonY);
    }
}
