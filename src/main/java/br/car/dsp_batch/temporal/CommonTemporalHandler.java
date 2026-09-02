package br.car.dsp_batch.temporal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;

/**
 * Typed read/write for ordinary temporal columns (not watermark canonicalization).
 * Preserves source semantics; does not convert everything to TIMESTAMPTZ.
 */
public final class CommonTemporalHandler {

    private CommonTemporalHandler() {
    }

    public static Class<?> javaType(TemporalType type) {
        return switch (type) {
            case DATE -> LocalDate.class;
            case TIMESTAMP -> LocalDateTime.class;
            case TIMESTAMPTZ -> OffsetDateTime.class;
            case TIME -> LocalTime.class;
            case TIMETZ -> OffsetTime.class;
            case UNSUPPORTED -> Object.class;
        };
    }

    public static Object read(ResultSet rs, String column, String udtName) throws SQLException {
        TemporalType type = TemporalTypeClassifier.classify(udtName);
        if (!type.isCommonSupported()) {
            return rs.getObject(column);
        }
        return rs.getObject(column, javaType(type));
    }

    public static Object read(ResultSet rs, String column, TemporalType type) throws SQLException {
        if (type == null || !type.isCommonSupported()) {
            return rs.getObject(column);
        }
        return rs.getObject(column, javaType(type));
    }

    public static void write(PreparedStatement ps, int index, Object value, String udtName)
            throws SQLException {
        TemporalType type = TemporalTypeClassifier.classify(udtName);
        write(ps, index, value, type);
    }

    public static void write(PreparedStatement ps, int index, Object value, TemporalType type)
            throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        if (type == null || !type.isCommonSupported()) {
            ps.setObject(index, value);
            return;
        }
        ps.setObject(index, value);
    }

    public static void requireCommonSupported(String columnName, String udtName) {
        TemporalType type = TemporalTypeClassifier.classify(udtName);
        if (TemporalTypeClassifier.isTemporal(udtName) && !type.isCommonSupported()) {
            throw new IllegalStateException(
                    "Column '" + columnName + "' has unsupported temporal type '"
                            + udtName + "'.");
        }
        if (type == TemporalType.UNSUPPORTED
                && udtName != null
                && looksLikeRejectedTemporalText(udtName)) {
            throw new IllegalStateException(
                    "Column '" + columnName + "' has unsupported temporal type '"
                            + udtName + "'.");
        }
    }

    private static boolean looksLikeRejectedTemporalText(String udtName) {
        String lower = udtName.toLowerCase();
        return lower.contains("interval");
    }
}
