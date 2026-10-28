package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.license.LicenseRank;
import net.minecraft.network.chat.Component;

/** Roman numerals and short rank labels for the board UI. */
public final class BoardRankLabels {
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private BoardRankLabels() {
    }

    public static String roman(LicenseRank rank) {
        if (rank.isAdmin()) {
            return "OP";
        }
        int level = rank.level();
        if (level >= 1 && level < ROMAN.length) {
            return ROMAN[level];
        }
        return Integer.toString(level);
    }

    public static Component rankLine(LicenseRank rank) {
        return Component.translatable("ui.aerocouriers.board.rank_line", roman(rank), rank.displayName());
    }

    public static Component rankRequiredShort(LicenseRank rank) {
        return Component.translatable("ui.aerocouriers.row.rank_required", roman(rank));
    }
}
