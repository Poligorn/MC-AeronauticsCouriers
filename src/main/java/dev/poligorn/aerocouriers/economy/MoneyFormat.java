package dev.poligorn.aerocouriers.economy;

import net.minecraft.network.chat.Component;

/**
 * The only place a money amount becomes text. Numismatics uses its own lang ({@code item.numismatics.spur} for 1,
 * {@code item.numismatics.spur.plural} otherwise). Emeralds use {@code item.minecraft.emerald} for 1 and
 * {@code ui.aerocouriers.currency.emerald.plural} otherwise. The client resolves the translatable, so chat follows
 * the player's language.
 */
public final class MoneyFormat {
    private MoneyFormat() {
    }

    public static Component unit(long amount) {
        boolean one = Math.abs(amount) == 1L;
        if (MoneyScale.numismaticsBackend()) {
            return Component.translatable(one ? "item.numismatics.spur" : "item.numismatics.spur.plural");
        }
        if (one) {
            return Component.translatable("item.minecraft.emerald");
        }
        return Component.translatable("ui.aerocouriers.currency.emerald.plural");
    }

    public static Component component(long amount) {
        return Component.literal(grouped(amount) + " ").append(unit(amount));
    }

    /**
     * Short unit for the board-card rate when the full word does not fit beside the collateral.
     * {@code sp}/{@code шп} with Numismatics, {@code em}/{@code эм} for emeralds.
     */
    public static Component compactUnit() {
        String key = MoneyScale.numismaticsBackend()
                ? "ui.aerocouriers.currency.compact.spur"
                : "ui.aerocouriers.currency.compact.emerald";
        return Component.translatable(key);
    }

    public static String compactText(long amount) {
        return grouped(amount) + " " + compactUnit().getString();
    }

    /** Resolved on the side that calls it. Client UI should prefer this; server chat should send {@link #component}. */
    public static String text(long amount) {
        return component(amount).getString();
    }

    /** {@code 1820 -> "1 820"}, same grouping as the tablet mockup. */
    public static String grouped(long value) {
        String raw = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        int lead = raw.length() % 3;
        for (int i = 0; i < raw.length(); i++) {
            if (i > 0 && (i - lead) % 3 == 0) {
                out.append(' ');
            }
            out.append(raw.charAt(i));
        }
        return (value < 0 ? "-" : "") + out;
    }
}
