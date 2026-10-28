package dev.poligorn.aerocouriers.compat.numismatics;

import dev.ithundxr.createnumismatics.content.backend.Coin;
import net.minecraft.network.chat.Component;

/** Formats spur totals using Numismatics coin names (client display when the Numismatics backend is active). */
public final class NumismaticsBalanceFormat {
    private static final Coin[] HIGH_TO_LOW = {
            Coin.SUN, Coin.CROWN, Coin.COG, Coin.SPROCKET, Coin.BEVEL, Coin.SPUR
    };

    private NumismaticsBalanceFormat() {
    }

    public static Component balanceLine(long spurs) {
        int amount = spurs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, spurs);
        String breakdown = coinBreakdown(amount);
        if (breakdown.isEmpty()) {
            return Component.translatable("ui.aerocouriers.balance.numismatics", amount);
        }
        return Component.translatable("ui.aerocouriers.balance.numismatics_breakdown", amount, breakdown);
    }

    private static String coinBreakdown(int spurs) {
        if (spurs <= 0) {
            return "";
        }
        StringBuilder breakdown = new StringBuilder();
        int remaining = spurs;
        int denominations = 0;
        for (Coin coin : HIGH_TO_LOW) {
            if (remaining < coin.value) {
                continue;
            }
            int count = remaining / coin.value;
            if (count <= 0) {
                continue;
            }
            remaining -= count * coin.value;
            denominations++;
            if (!breakdown.isEmpty()) {
                breakdown.append(", ");
            }
            breakdown.append(count).append(' ').append(coinLabel(coin));
        }
        return denominations > 1 ? breakdown.toString() : "";
    }

    /** Short labels aligned with Numismatics spur denominations (no Create types on the signature). */
    private static String coinLabel(Coin coin) {
        if (coin == Coin.SPUR) {
            return "sp";
        }
        if (coin == Coin.BEVEL) {
            return "bev";
        }
        if (coin == Coin.SPROCKET) {
            return "spr";
        }
        if (coin == Coin.COG) {
            return "cog";
        }
        if (coin == Coin.CROWN) {
            return "cr";
        }
        if (coin == Coin.SUN) {
            return "sun";
        }
        return "sp";
    }
}
