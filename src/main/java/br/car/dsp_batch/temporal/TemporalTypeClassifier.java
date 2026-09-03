package br.car.dsp_batch.temporal;

import java.util.Locale;

/**
 * Maps PostgreSQL {@code udt_name} values to {@link TemporalType}.
 */
public final class TemporalTypeClassifier {

    private TemporalTypeClassifier() {
    }

    public static TemporalType classify(String udtName) {
        if (udtName == null || udtName.isBlank()) {
            return TemporalType.UNSUPPORTED;
        }
        String udt = udtName.toLowerCase(Locale.ROOT).trim();
        return switch (udt) {
            case "date" -> TemporalType.DATE;
            case "timestamp", "timestamp without time zone" -> TemporalType.TIMESTAMP;
            case "timestamptz", "timestamp with time zone" -> TemporalType.TIMESTAMPTZ;
            case "time", "time without time zone" -> TemporalType.TIME;
            case "timetz", "time with time zone" -> TemporalType.TIMETZ;
            default -> TemporalType.UNSUPPORTED;
        };
    }

    public static boolean isTemporal(String udtName) {
        TemporalType type = classify(udtName);
        return type != TemporalType.UNSUPPORTED;
    }
}
