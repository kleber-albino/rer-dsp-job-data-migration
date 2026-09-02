package br.car.dsp_batch.temporal;

/**
 * PostgreSQL temporal kinds relevant to migration and watermark sync.
 */
public enum TemporalType {
    DATE,
    TIMESTAMP,
    TIMESTAMPTZ,
    TIME,
    TIMETZ,
    UNSUPPORTED;

    /** Types accepted as {@code updated-at-column} for watermark sync. */
    public boolean isWatermarkSupported() {
        return this == DATE || this == TIMESTAMP || this == TIMESTAMPTZ;
    }

    /** Types that need an explicit IANA source timezone to interpret as Instant. */
    public boolean requiresSourceTimezone() {
        return this == DATE || this == TIMESTAMP;
    }

    /** Types that may be migrated as ordinary attributes (not watermark). */
    public boolean isCommonSupported() {
        return this == DATE
                || this == TIMESTAMP
                || this == TIMESTAMPTZ
                || this == TIME
                || this == TIMETZ;
    }
}
