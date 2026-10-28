package dev.poligorn.aerocouriers.client;

import net.minecraft.network.chat.Component;

public final class BalanceDisplay {
    private BalanceDisplay() {
    }

    /** Plain text fallback where coin widgets are not used (e.g. clipped overflow hints). */
    public static Component format(long balance, boolean numismaticsBackend) {
        return Component.translatable("ui.aerocouriers.balance", balance);
    }

    public static String balanceLabel() {
        return Component.translatable("ui.aerocouriers.balance.label").getString();
    }
}
