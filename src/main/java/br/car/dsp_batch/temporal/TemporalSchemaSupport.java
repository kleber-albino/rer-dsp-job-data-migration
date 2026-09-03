package br.car.dsp_batch.temporal;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Reads PostgreSQL column UDTs from information_schema for temporal validation.
 */
@Component
public class TemporalSchemaSupport {

    public String requireUdtName(JdbcTemplate jdbc, String qualifiedTable, String columnName) {
        QualifiedTable table = QualifiedTable.parse(qualifiedTable);
        return requireUdtName(jdbc, table.schema(), table.table(), columnName);
    }

    public String requireUdtName(JdbcTemplate jdbc, String schema, String table, String columnName) {
        List<String> udts = jdbc.query(
                """
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """,
                (rs, rowNum) -> rs.getString("udt_name"),
                schema,
                table,
                columnName
        );
        if (udts.isEmpty()) {
            throw new IllegalStateException(
                    "Column '" + columnName + "' not found on " + schema + "." + table);
        }
        return udts.getFirst();
    }

    public void requireDestinationTimestamptz(JdbcTemplate jdbc,
                                              String qualifiedTable,
                                              String columnName) {
        if (!tableExists(jdbc, qualifiedTable)) {
            return;
        }
        String udt = requireUdtName(jdbc, qualifiedTable, columnName);
        TemporalType type = TemporalTypeClassifier.classify(udt);
        if (type != TemporalType.TIMESTAMPTZ) {
            throw new IllegalStateException(
                    "Destination column '" + columnName + "' on " + qualifiedTable
                            + " must be timestamptz (found '" + udt + "'). "
                            + "Refusing to ALTER automatically.");
        }
    }

    public boolean tableExists(JdbcTemplate jdbc, String qualifiedTable) {
        QualifiedTable table = QualifiedTable.parse(qualifiedTable);
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """,
                Integer.class,
                table.schema(),
                table.table()
        );
        return count != null && count > 0;
    }

    public WatermarkColumnSpec resolveWatermarkColumn(JdbcTemplate sourceJdbc,
                                                      String qualifiedSourceTable,
                                                      String columnName,
                                                      SourceTemporalPolicy policy) {
        String udt = requireUdtName(sourceJdbc, qualifiedSourceTable, columnName);
        TemporalType type = TemporalTypeClassifier.classify(udt);
        if (!type.isWatermarkSupported()) {
            throw new IllegalStateException(
                    "column '" + columnName + "' has type '" + udt
                            + "'. Expected timestamp, timestamptz or date "
                            + "(time/timetz/text are not allowed).");
        }
        return WatermarkColumnSpec.of(columnName.trim(), type, policy);
    }

    public WatermarkColumnSpec resolveOptionalWatermarkColumn(JdbcTemplate sourceJdbc,
                                                              String qualifiedSourceTable,
                                                              String columnName,
                                                              SourceTemporalPolicy policy) {
        if (columnName == null || columnName.isBlank()) {
            return null;
        }
        return resolveWatermarkColumn(sourceJdbc, qualifiedSourceTable, columnName, policy);
    }

    public TemporalColumnSpecs resolveTemporalColumns(JdbcTemplate sourceJdbc,
                                                      String qualifiedSourceTable,
                                                      String creationDateColumn,
                                                      String updatedAtColumn,
                                                      SourceTemporalPolicy policy) {
        WatermarkColumnSpec creation = resolveWatermarkColumn(
                sourceJdbc, qualifiedSourceTable, creationDateColumn, policy);
        WatermarkColumnSpec updated = resolveOptionalWatermarkColumn(
                sourceJdbc, qualifiedSourceTable, updatedAtColumn, policy);
        return TemporalColumnSpecs.of(creation, updated);
    }

    public static String normalizeUdt(String udtName) {
        return udtName == null ? "" : udtName.toLowerCase(Locale.ROOT);
    }
}
