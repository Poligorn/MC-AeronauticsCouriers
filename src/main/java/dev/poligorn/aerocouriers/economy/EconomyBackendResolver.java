package dev.poligorn.aerocouriers.economy;

import java.util.Locale;

/** Pure selection of the active economy backend from config and runtime mod presence. */
public final class EconomyBackendResolver {
    public enum Choice {
        INTERNAL,
        NUMISMATICS
    }

    private EconomyBackendResolver() {
    }

    public static Choice resolve(String configBackend, boolean numismaticsLoaded) {
        String key = configBackend == null ? "auto" : configBackend.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "internal" -> Choice.INTERNAL;
            case "numismatics" -> numismaticsLoaded ? Choice.NUMISMATICS : Choice.INTERNAL;
            default -> numismaticsLoaded ? Choice.NUMISMATICS : Choice.INTERNAL;
        };
    }

    public static boolean forcedNumismaticsMissing(String configBackend, boolean numismaticsLoaded) {
        if (numismaticsLoaded) {
            return false;
        }
        return "numismatics".equals(normalize(configBackend));
    }

    public static boolean usesNumismaticsBackend(String configBackend, boolean numismaticsLoaded) {
        return resolve(configBackend, numismaticsLoaded) == Choice.NUMISMATICS;
    }

    private static String normalize(String configBackend) {
        return configBackend == null ? "auto" : configBackend.trim().toLowerCase(Locale.ROOT);
    }
}
