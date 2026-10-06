package br.car.dsp_batch.aoi.ddl;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adds missing columns on existing AOI tables (minimal init SQL or a prior run).
 */
@Slf4j
@Component
public class AreaOfInterestTargetSchemaAligner {

    public void alignMissingColumns(JdbcTemplate jdbc,
                                    QualifiedTable table,
                                    List<DdlColumnDefinition> expectedColumns) {
        Set<String> existing = fetchExistingColumnNames(jdbc, table);
        for (DdlColumnDefinition column : expectedColumns) {
            if (existing.contains(column.name())) {
                continue;
            }
            String statement = "ALTER TABLE " + table.qualified()
                    + " ADD COLUMN IF NOT EXISTS " + quote(column.name())
                    + " " + column.sqlType();
            log.info("Adding missing column {} on {}", column.name(), table.qualified());
            log.debug("Executing DDL: {}", statement);
            jdbc.execute(statement);
        }
    }

    private Set<String> fetchExistingColumnNames(JdbcTemplate jdbc, QualifiedTable table) {
        List<String> names = jdbc.query(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                """,
                (rs, rowNum) -> rs.getString("column_name"),
                table.schema(),
                table.table()
        );
        return new HashSet<>(names);
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
