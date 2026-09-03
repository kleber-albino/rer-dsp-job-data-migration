package br.car.dsp_batch.temporal;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Single conversion path for watermark / {@code updated_at}:
 * source representation ↔ {@link Instant} ↔ DSP {@code TIMESTAMPTZ}.
 * Never uses {@link java.time.ZoneId#systemDefault()}.
 */
public final class WatermarkTemporalBridge {

    private WatermarkTemporalBridge() {
    }

    public static Instant truncate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    public static Instant toInstant(Object value, WatermarkColumnSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (value == null) {
            return null;
        }
        Instant raw = switch (spec.sourceType()) {
            case TIMESTAMPTZ -> fromTimestamptz(value);
            case TIMESTAMP -> fromTimestamp(value, spec.policy().requireZoneId(TemporalType.TIMESTAMP));
            case DATE -> fromDate(value, spec.policy().requireZoneId(TemporalType.DATE));
            default -> throw new IllegalStateException(
                    "Unsupported watermark type " + spec.sourceType());
        };
        return truncate(raw);
    }

    public static Instant readInstant(ResultSet rs, String column, WatermarkColumnSpec spec)
            throws SQLException {
        Objects.requireNonNull(spec, "spec");
        return switch (spec.sourceType()) {
            case TIMESTAMPTZ -> {
                OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
                yield odt == null ? null : truncate(odt.toInstant());
            }
            case TIMESTAMP -> {
                LocalDateTime ldt = rs.getObject(column, LocalDateTime.class);
                if (ldt == null) {
                    yield null;
                }
                yield truncate(ldt.atZone(spec.policy().requireZoneId(TemporalType.TIMESTAMP))
                        .toInstant());
            }
            case DATE -> {
                LocalDate date = rs.getObject(column, LocalDate.class);
                if (date == null) {
                    yield null;
                }
                yield truncate(date.atStartOfDay(spec.policy().requireZoneId(TemporalType.DATE))
                        .toInstant());
            }
            default -> throw new IllegalStateException(
                    "Unsupported watermark type " + spec.sourceType());
        };
    }

    /**
     * Value to persist on DSP {@code updated_at TIMESTAMPTZ}.
     */
    public static OffsetDateTime toDspTimestamptz(Instant instant) {
        if (instant == null) {
            return null;
        }
        return truncate(instant).atOffset(ZoneOffset.UTC);
    }

    /**
     * Builds a SQL filter fragment (and optional UTC ISO literal) for paging providers
     * that cannot bind extra parameters. Zone name always comes from {@link SourceTemporalPolicy}.
     */
    public static WatermarkPredicate buildPredicate(WatermarkColumnSpec spec, Instant watermark) {
        Objects.requireNonNull(spec, "spec");
        String column = spec.sourceColumn();
        String notNull = column + " IS NOT NULL";
        if (watermark == null) {
            return WatermarkPredicate.notNullOnly(notNull);
        }
        Instant truncated = truncate(watermark);
        String greaterThan = buildGreaterThanExpression(spec, truncated);
        return WatermarkPredicate.withFilter(notNull + " AND " + greaterThan, truncated);
    }

    /**
     * Change detection across mandatory creation date and optional update date.
     */
    public static WatermarkPredicate buildChangeDetectionPredicate(WatermarkColumnSpec creationDateColumn,
                                                                   WatermarkColumnSpec updatedAtColumn,
                                                                   Instant watermark) {
        Objects.requireNonNull(creationDateColumn, "creationDateColumn");
        String creationNotNull = creationDateColumn.sourceColumn() + " IS NOT NULL";
        if (watermark == null) {
            return WatermarkPredicate.notNullOnly(creationNotNull);
        }
        Instant truncated = truncate(watermark);
        String creationDelta = buildGreaterThanExpression(creationDateColumn, truncated);
        if (updatedAtColumn == null) {
            String sql = creationNotNull + " AND (" + creationDelta + ")";
            return WatermarkPredicate.withFilter(sql, truncated);
        }
        String updatedColumn = updatedAtColumn.sourceColumn();
        String updatedDelta = buildGreaterThanExpression(updatedAtColumn, truncated);
        String sql = creationNotNull + " AND ((" + creationDelta + ") OR ("
                + updatedColumn + " IS NOT NULL AND " + updatedDelta + "))";
        return WatermarkPredicate.withFilter(sql, truncated);
    }

    public static Instant maxEventInstant(Instant creationInstant, Instant updatedInstant) {
        if (creationInstant == null) {
            return updatedInstant;
        }
        if (updatedInstant == null) {
            return creationInstant;
        }
        return creationInstant.isAfter(updatedInstant) ? creationInstant : updatedInstant;
    }

    private static String buildGreaterThanExpression(WatermarkColumnSpec spec, Instant watermark) {
        String column = spec.sourceColumn();
        String utcLiteral = watermark.toString();
        return switch (spec.sourceType()) {
            case TIMESTAMPTZ -> column + " > TIMESTAMP WITH TIME ZONE '" + utcLiteral + "'";
            case TIMESTAMP -> {
                String zone = spec.policy().zoneIdSqlLiteral();
                yield column + " > (TIMESTAMP WITH TIME ZONE '" + utcLiteral
                        + "' AT TIME ZONE '" + zone + "')";
            }
            case DATE -> {
                String zone = spec.policy().zoneIdSqlLiteral();
                yield column + " > ((TIMESTAMP WITH TIME ZONE '" + utcLiteral
                        + "' AT TIME ZONE '" + zone + "')::date)";
            }
            default -> throw new IllegalStateException(
                    "Unsupported watermark type " + spec.sourceType());
        };
    }

    /**
     * Bind value for {@code JdbcTemplate} style {@code col > ?} when the comparison
     * expression projects the watermark to the source type in SQL.
     * Prefer {@link #buildPredicate} for readers that embed SQL strings.
     */
    public static Object toSourceBindValue(Instant watermark, WatermarkColumnSpec spec) {
        Objects.requireNonNull(spec, "spec");
        if (watermark == null) {
            return null;
        }
        Instant truncated = truncate(watermark);
        return switch (spec.sourceType()) {
            case TIMESTAMPTZ -> truncated.atOffset(ZoneOffset.UTC);
            case TIMESTAMP -> LocalDateTime.ofInstant(
                    truncated, spec.policy().requireZoneId(TemporalType.TIMESTAMP));
            case DATE -> LocalDate.ofInstant(
                    truncated, spec.policy().requireZoneId(TemporalType.DATE));
            default -> throw new IllegalStateException(
                    "Unsupported watermark type " + spec.sourceType());
        };
    }

    public static void requireWatermarkType(TemporalType type, String columnName) {
        if (type == null || !type.isWatermarkSupported()) {
            throw new IllegalStateException(
                    "updated-at-column '" + columnName + "' has unsupported type '"
                            + (type == null ? "null" : type.name().toLowerCase())
                            + "'. Expected timestamp, timestamptz or date.");
        }
        if (type == TemporalType.TIME || type == TemporalType.TIMETZ) {
            throw new IllegalStateException(
                    "updated-at-column '" + columnName
                            + "' cannot be time/timetz (not an absolute point in time).");
        }
    }

    private static Instant fromTimestamptz(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        throw unexpected(value, "timestamptz");
    }

    private static Instant fromTimestamp(Object value, java.time.ZoneId zone) {
        if (value instanceof LocalDateTime ldt) {
            return ldt.atZone(zone).toInstant();
        }
        if (value instanceof Timestamp ts) {
            // Prefer LocalDateTime extraction without JVM default zone reinterpretation:
            // Timestamp.toLocalDateTime() uses the JVM calendar — avoid when possible.
            return ts.toLocalDateTime().atZone(zone).toInstant();
        }
        throw unexpected(value, "timestamp");
    }

    private static Instant fromDate(Object value, java.time.ZoneId zone) {
        if (value instanceof LocalDate date) {
            return date.atStartOfDay(zone).toInstant();
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay(zone).toInstant();
        }
        throw unexpected(value, "date");
    }

    private static IllegalArgumentException unexpected(Object value, String expected) {
        return new IllegalArgumentException(
                "Cannot convert " + value.getClass().getName()
                        + " to Instant for source type " + expected);
    }
}
