package br.car.dsp_batch.temporal;

import java.time.ZoneId;
import java.util.Objects;

/**
 * Explicit timezone policy for interpreting source temporal values.
 * Never uses {@link ZoneId#systemDefault()}.
 */
public final class SourceTemporalPolicy {

    private final ZoneId zoneId;
    private final String configSource;

    private SourceTemporalPolicy(ZoneId zoneId, String configSource) {
        this.zoneId = zoneId;
        this.configSource = configSource;
    }

    public static SourceTemporalPolicy of(ZoneId zoneId, String configSource) {
        Objects.requireNonNull(zoneId, "zoneId");
        return new SourceTemporalPolicy(zoneId, configSource == null ? "explicit" : configSource);
    }

    public static SourceTemporalPolicy parse(String zoneIdText, String configSource) {
        if (zoneIdText == null || zoneIdText.isBlank()) {
            throw new IllegalArgumentException(
                    "source-timezone is blank (" + configSource + ")");
        }
        try {
            return of(ZoneId.of(zoneIdText.trim()), configSource);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid source-timezone '" + zoneIdText + "' (" + configSource + "): "
                            + ex.getMessage(),
                    ex);
        }
    }

    public static SourceTemporalPolicy none() {
        return new SourceTemporalPolicy(null, "none");
    }

    public ZoneId zoneIdOrNull() {
        return zoneId;
    }

    public ZoneId requireZoneId(TemporalType type) {
        if (!type.requiresSourceTimezone()) {
            return zoneId;
        }
        if (zoneId == null) {
            throw new IllegalStateException(
                    "source-timezone (IANA) is required when updated-at-column type is "
                            + type.name().toLowerCase()
                            + " (config=" + configSource + ")");
        }
        return zoneId;
    }

    public boolean hasZone() {
        return zoneId != null;
    }

    public String configSource() {
        return configSource;
    }

    /** Escapes a zone id for safe embedding in SQL string literals. */
    public String zoneIdSqlLiteral() {
        ZoneId z = zoneId;
        if (z == null) {
            throw new IllegalStateException("source-timezone is required for SQL projection");
        }
        return z.getId().replace("'", "''");
    }
}
