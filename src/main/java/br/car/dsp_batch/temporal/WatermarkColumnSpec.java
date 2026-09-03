package br.car.dsp_batch.temporal;

import java.util.Objects;

/**
 * Watermark column descriptor: source name, PostgreSQL temporal kind, and timezone policy.
 */
public record WatermarkColumnSpec(
        String sourceColumn,
        TemporalType sourceType,
        SourceTemporalPolicy policy
) {
    public WatermarkColumnSpec {
        Objects.requireNonNull(sourceColumn, "sourceColumn");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(policy, "policy");
        if (sourceColumn.isBlank()) {
            throw new IllegalArgumentException("sourceColumn must not be blank");
        }
        if (!sourceType.isWatermarkSupported()) {
            throw new IllegalArgumentException(
                    "updated-at-column type " + sourceType
                            + " is not supported for watermark sync (column=" + sourceColumn + ")");
        }
        if (sourceType.requiresSourceTimezone()) {
            policy.requireZoneId(sourceType);
        }
    }

    public static WatermarkColumnSpec of(String sourceColumn,
                                         TemporalType sourceType,
                                         SourceTemporalPolicy policy) {
        return new WatermarkColumnSpec(sourceColumn, sourceType, policy);
    }
}
