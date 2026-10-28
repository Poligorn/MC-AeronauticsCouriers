package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.compat.numismatics.NumismaticsBalanceFormat;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

public final class BalanceDisplay {
    private static final String NUMISMATICS_MOD_ID = "numismatics";

    private BalanceDisplay() {
    }

    public static Component format(long balance, boolean numismaticsBackend) {
        if (numismaticsBackend && ModList.get().isLoaded(NUMISMATICS_MOD_ID)) {
            try {
                return NumismaticsBalanceFormat.balanceLine(balance);
            } catch (Throwable ignored) {
                // Numismatics absent or API mismatch on client — fall back to internal wording.
            }
        }
        return Component.translatable("ui.aerocouriers.balance", balance);
    }
}
