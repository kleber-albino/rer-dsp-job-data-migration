package br.car.dsp_batch.layer.introspection;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.BatchTemporalProperties;
import br.car.dsp_batch.temporal.CommonTemporalHandler;
import br.car.dsp_batch.temporal.SourceTemporalPolicy;
import br.car.dsp_batch.temporal.TemporalType;
import br.car.dsp_batch.temporal.TemporalTypeClassifier;
import br.car.dsp_batch.temporal.WatermarkColumnSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static br.car.dsp_batch.layer.config.LayerConfig.AREA_OF_INTEREST_ID_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.CREATED_AT_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.GEOMETRY_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.ID_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.LABEL_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.UPDATED_AT_COLUMN;

/**
 * Discovers PostGIS table structure on the source database and selects only
 * the columns declared in the layer contract for migration.
 */
@Slf4j
@Service
public class SchemaIntrospectionService {

    private final BatchTemporalProperties batchTemporalProperties;
    private final JdbcTemplate geoTargetJdbcTemplate;

    public SchemaIntrospectionService(
            BatchTemporalProperties batchTemporalProperties,
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate) {
        this.batchTemporalProperties = batchTemporalProperties;
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
    }

    public boolean tableExists(JdbcTemplate jdbc, QualifiedTable table) {
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

    public LayerTableMetadata introspect(JdbcTemplate sourceJdbc, LayerConfig config) {
        QualifiedTable source = config.resolveSourceTable();
        QualifiedTable target = config.resolveTargetTable();

        if (!tableExists(sourceJdbc, source)) {
            throw new IllegalStateException(
                    "Source table not found: " + source.qualified());
        }

        List<ColumnMetadata> allColumns = fetchColumns(sourceJdbc, source);
        if (allColumns.isEmpty()) {
            throw new IllegalStateException(
                    "Source table has no columns: " + source.qualified());
        }

        String primaryKey = requireConfiguredColumn(
                allColumns, config.getPrimaryKey(), "primary-key");
        String areaOfInterestIdColumn = requireConfiguredColumn(
                allColumns, config.getAreaOfInterestIdColumn(), "area-of-interest-id-column");
        String creationDateColumnName = requireConfiguredColumn(
                allColumns, config.getCreationDateColumn(), "creation-date-column");
        WatermarkColumnSpec creationDateColumn = requireTemporalColumn(
                allColumns, creationDateColumnName, config, "creation-date-column");
        WatermarkColumnSpec updatedAtColumn = resolveOptionalTemporalColumn(
                allColumns, config.getUpdatedAtColumn(), config, "updated-at-column");
        String updatedAtColumnName = updatedAtColumn == null ? null : updatedAtColumn.sourceColumn();
        String labelColumn = resolveOptionalConfiguredColumn(
                allColumns, config.getLabelColumn(), "label-column");
        GeometryInfo geometryInfo = resolveGeometry(
                sourceJdbc, source, allColumns, config.getGeometryColumn());

        List<String> additionalColumns = normalizeAdditionalColumns(config.getAdditionalColumns());
        validateAdditionalColumnsExist(allColumns, additionalColumns);
        validateAdditionalColumnsTemporalTypes(allColumns, additionalColumns);
        rejectCanonicalTargetNameCollisions(source, additionalColumns);

        warnIfHigherDimensional(source, geometryInfo);
        int srid = resolveSrid(sourceJdbc, source, geometryInfo, config.getSrid());
        requireExistingTargetCreatedAtTimestamptz(target);

        List<ColumnMetadata> migratedColumns = selectMigratedColumns(
                allColumns,
                primaryKey,
                areaOfInterestIdColumn,
                creationDateColumnName,
                updatedAtColumnName,
                labelColumn,
                geometryInfo.columnName(),
                additionalColumns
        );
        List<IndexMetadata> indexes = filterIndexes(
                fetchIndexes(sourceJdbc, source),
                migratedColumns
        );

        log.info(
                "Introspection completed for {}: pk={} -> {}, aoiLink={} -> {}, "
                        + "sourceCreatedAt={} ({}) -> {}, sourceUpdatedAt={} -> {}, label={} -> {}, "
                        + "sourceGeom={} -> {}, srid={}, migratedColumns={}",
                source.qualified(),
                primaryKey,
                ID_COLUMN,
                areaOfInterestIdColumn,
                AREA_OF_INTEREST_ID_COLUMN,
                creationDateColumnName,
                creationDateColumn.sourceType(),
                CREATED_AT_COLUMN,
                updatedAtColumnName,
                updatedAtColumnName == null ? "—" : UPDATED_AT_COLUMN,
                labelColumn,
                labelColumn == null ? "—" : LABEL_COLUMN,
                geometryInfo.columnName(),
                GEOMETRY_COLUMN,
                srid,
                migratedColumns.size()
        );

        return new LayerTableMetadata(
                config.resolveKey(),
                config.resolveLayerName(),
                source,
                target,
                primaryKey,
                geometryInfo.columnName(),
                areaOfInterestIdColumn,
                creationDateColumn,
                updatedAtColumn,
                labelColumn,
                srid,
                migratedColumns,
                indexes,
                config.getWhereClause()
        );
    }

    public LayerTableMetadata introspect(DataSource sourceDataSource, LayerConfig config) {
        return introspect(new JdbcTemplate(sourceDataSource), config);
    }

    private List<ColumnMetadata> fetchColumns(JdbcTemplate jdbc, QualifiedTable table) {
        return jdbc.query(
                """
                SELECT column_name,
                       udt_name,
                       character_maximum_length,
                       numeric_precision,
                       numeric_scale,
                       is_nullable
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """,
                (rs, rowNum) -> {
                    String name = rs.getString("column_name");
                    String udtName = rs.getString("udt_name");
                    boolean geometry = "geometry".equalsIgnoreCase(udtName)
                            || "geography".equalsIgnoreCase(udtName);
                    return new ColumnMetadata(
                            name,
                            udtName,
                            (Integer) rs.getObject("character_maximum_length"),
                            (Integer) rs.getObject("numeric_precision"),
                            (Integer) rs.getObject("numeric_scale"),
                            "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                            geometry
                    );
                },
                table.schema(),
                table.table()
        );
    }

    private List<ColumnMetadata> selectMigratedColumns(List<ColumnMetadata> allColumns,
                                                       String primaryKey,
                                                       String areaOfInterestIdColumn,
                                                       String creationDateColumn,
                                                       String updatedAtColumn,
                                                       String labelColumn,
                                                       String geometryColumn,
                                                       List<String> additionalColumns) {
        Map<String, ColumnMetadata> byName = new LinkedHashMap<>();
        for (ColumnMetadata column : allColumns) {
            byName.put(column.name(), column);
        }

        List<String> orderedNames = new ArrayList<>();
        orderedNames.add(primaryKey);
        orderedNames.add(areaOfInterestIdColumn);
        orderedNames.add(creationDateColumn);
        if (updatedAtColumn != null) {
            orderedNames.add(updatedAtColumn);
        }
        if (labelColumn != null) {
            orderedNames.add(labelColumn);
        }
        orderedNames.addAll(additionalColumns);
        orderedNames.add(geometryColumn);

        Set<String> seen = new LinkedHashSet<>();
        List<ColumnMetadata> selected = new ArrayList<>();
        for (String name : orderedNames) {
            if (!seen.add(name)) {
                continue;
            }
            ColumnMetadata column = byName.get(name);
            if (column == null) {
                throw new IllegalStateException(
                        "Configured column '" + name + "' was not found during migration selection.");
            }
            selected.add(column);
        }
        return selected;
    }

    private List<String> normalizeAdditionalColumns(List<String> additionalColumns) {
        if (additionalColumns == null || additionalColumns.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String column : additionalColumns) {
            if (column != null && !column.isBlank()) {
                normalized.add(column.trim());
            }
        }
        return normalized;
    }

    private void validateAdditionalColumnsExist(List<ColumnMetadata> columns,
                                                List<String> additionalColumns) {
        for (String column : additionalColumns) {
            requireColumn(columns, column, "additional-columns");
        }
    }

    private void validateAdditionalColumnsTemporalTypes(List<ColumnMetadata> columns,
                                                        List<String> additionalColumns) {
        Map<String, ColumnMetadata> byName = new LinkedHashMap<>();
        for (ColumnMetadata column : columns) {
            byName.put(column.name(), column);
        }
        for (String name : additionalColumns) {
            ColumnMetadata column = byName.get(name);
            if (column == null) {
                continue;
            }
            TemporalType type = TemporalTypeClassifier.classify(column.udtName());
            if (type == TemporalType.UNSUPPORTED && isExplicitlyTemporalUdt(column.udtName())) {
                throw new IllegalStateException(
                        "additional-columns entry '" + name + "' has unsupported temporal type '"
                                + column.udtName() + "'.");
            }
            CommonTemporalHandler.requireCommonSupported(name, column.udtName());
        }
    }

    private boolean isExplicitlyTemporalUdt(String udtName) {
        if (udtName == null) {
            return false;
        }
        String lower = udtName.toLowerCase(Locale.ROOT);
        return lower.contains("time") || lower.contains("date") || lower.contains("interval");
    }

    private void requireExistingTargetCreatedAtTimestamptz(QualifiedTable target) {
        if (!tableExists(geoTargetJdbcTemplate, target)) {
            return;
        }
        requireExistingTargetTimestamptzColumn(target, CREATED_AT_COLUMN);
    }

    private void requireExistingTargetTimestamptzColumn(QualifiedTable target, String columnName) {
        List<String> udts = geoTargetJdbcTemplate.query(
                """
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """,
                (rs, rowNum) -> rs.getString("udt_name"),
                target.schema(),
                target.table(),
                columnName
        );
        if (udts.isEmpty()) {
            throw new IllegalStateException(
                    "Target table " + target.qualified()
                            + " exists but has no '" + columnName + "' column.");
        }
        TemporalType type = TemporalTypeClassifier.classify(udts.getFirst());
        if (type != TemporalType.TIMESTAMPTZ) {
            throw new IllegalStateException(
                    "Destination column '" + columnName + "' on " + target.qualified()
                            + " must be timestamptz (found '" + udts.getFirst()
                            + "'). Refusing to ALTER automatically.");
        }
    }

    /**
     * Rejects extras whose target name collides with a canonical column
     * ({@code id}, {@code geom}, {@code updated_at}, etc.).
     * An unmapped source column with the same name is ignored.
     */
    private void rejectCanonicalTargetNameCollisions(QualifiedTable table,
                                                     List<String> additionalColumns) {
        for (String additionalColumn : additionalColumns) {
            if (LayerConfig.CANONICAL_TARGET_COLUMNS.contains(additionalColumn)) {
                throw new IllegalStateException(
                        "additional-columns entry '" + additionalColumn
                                + "' collides with a canonical target column on "
                                + table.qualified() + ".");
            }
        }
    }

    private GeometryInfo resolveGeometry(JdbcTemplate jdbc,
                                         QualifiedTable table,
                                         List<ColumnMetadata> columns,
                                         String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "geometry-column is required. Set batch.layers.geometry-column "
                            + "to the source column that should become '" + GEOMETRY_COLUMN + "'.");
        }
        String chosen = configured.trim();
        requireColumn(columns, chosen, "geometry-column");
        requireGeometryTypedColumn(columns, chosen);
        GeometryInfo registered = findRegisteredGeometry(jdbc, table, chosen);
        if (registered != null) {
            return registered;
        }
        return new GeometryInfo(chosen, null, null);
    }

    private GeometryInfo findRegisteredGeometry(JdbcTemplate jdbc,
                                                QualifiedTable table,
                                                String columnName) {
        List<GeometryInfo> matched = jdbc.query(
                """
                SELECT f_geometry_column, srid, type
                FROM geometry_columns
                WHERE f_table_schema = ? AND f_table_name = ? AND f_geometry_column = ?
                """,
                (rs, rowNum) -> new GeometryInfo(
                        rs.getString("f_geometry_column"),
                        (Integer) rs.getObject("srid"),
                        rs.getString("type")
                ),
                table.schema(),
                table.table(),
                columnName
        );
        return matched.isEmpty() ? null : matched.getFirst();
    }

    private void requireGeometryTypedColumn(List<ColumnMetadata> columns, String name) {
        boolean geometryTyped = columns.stream()
                .anyMatch(column -> column.name().equals(name) && column.geometry());
        if (!geometryTyped) {
            throw new IllegalStateException(
                    "geometry-column '" + name + "' exists but is not geometry/geography. "
                            + "Set batch.layers.geometry-column to a PostGIS geometry column "
                            + "(it will be stored as '" + GEOMETRY_COLUMN + "' on geo-target).");
        }
    }

    private WatermarkColumnSpec requireTemporalColumn(List<ColumnMetadata> columns,
                                                      String columnName,
                                                      LayerConfig config,
                                                      String field) {
        return resolveTemporalColumn(columns, columnName, config, field, true);
    }

    private WatermarkColumnSpec resolveOptionalTemporalColumn(List<ColumnMetadata> columns,
                                                              String configured,
                                                              LayerConfig config,
                                                              String field) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String name = requireConfiguredColumn(columns, configured, field);
        return resolveTemporalColumn(columns, name, config, field, false);
    }

    private WatermarkColumnSpec resolveTemporalColumn(List<ColumnMetadata> columns,
                                                      String name,
                                                      LayerConfig config,
                                                      String field,
                                                      boolean required) {
        ColumnMetadata column = columns.stream()
                .filter(col -> col.name().equals(name))
                .findFirst()
                .orElseThrow();
        TemporalType type = TemporalTypeClassifier.classify(column.udtName());
        if (!type.isWatermarkSupported()) {
            throw new IllegalStateException(
                    field + " '" + name + "' has type '" + column.udtName()
                            + "'. Expected timestamp, timestamptz or date "
                            + "(it will be stored as TIMESTAMPTZ on geo-target).");
        }
        SourceTemporalPolicy policy = batchTemporalProperties.resolvePolicy(
                config.getSourceTimezone(),
                "batch.layers.source-timezone for " + config.getSourceTable()
        );
        if (type == TemporalType.DATE) {
            log.warn(
                    "{} '{}' on {} is DATE — watermark granularity is daily",
                    field,
                    name,
                    config.getSourceTable()
            );
        }
        return WatermarkColumnSpec.of(name, type, policy);
    }

    private String resolveOptionalConfiguredColumn(List<ColumnMetadata> columns,
                                                   String configured,
                                                   String field) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return requireConfiguredColumn(columns, configured, field);
    }

    private void warnIfHigherDimensional(QualifiedTable table, GeometryInfo geometryInfo) {
        String type = geometryInfo.geometryType();
        if (type == null || type.isBlank()) {
            return;
        }
        String upper = type.toUpperCase(Locale.ROOT);
        if (upper.contains("Z") || upper.contains("M")) {
            log.warn(
                    "Table {} geometry column '{}' is {} — Z/M will be dropped (ST_Force2D) on geo-target.",
                    table.qualified(),
                    geometryInfo.columnName(),
                    type
            );
        }
    }

    private int resolveSrid(JdbcTemplate jdbc,
                            QualifiedTable table,
                            GeometryInfo geometryInfo,
                            Integer override) {
        if (override != null && override > 0) {
            return override;
        }
        if (geometryInfo.registeredSrid() != null && geometryInfo.registeredSrid() > 0) {
            return geometryInfo.registeredSrid();
        }

        Integer sampledSrid = jdbc.query(
                """
                SELECT DISTINCT ST_SRID(%s) AS srid
                FROM %s
                WHERE %s IS NOT NULL
                LIMIT 1
                """.formatted(
                        quoteIdentifier(geometryInfo.columnName()),
                        table.qualified(),
                        quoteIdentifier(geometryInfo.columnName())
                ),
                rs -> rs.next() ? rs.getInt("srid") : null
        );

        if (sampledSrid == null || sampledSrid <= 0) {
            throw new IllegalStateException(
                    "SRID not found for " + table.qualified()
                            + ". Set batch.layers.srid to the SRID that should be used "
                            + "for this layer on geo-target.");
        }
        return sampledSrid;
    }

    private List<IndexMetadata> fetchIndexes(JdbcTemplate jdbc, QualifiedTable table) {
        List<IndexRow> rows = jdbc.query(
                """
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = ? AND tablename = ?
                ORDER BY indexname
                """,
                (rs, rowNum) -> new IndexRow(
                        rs.getString("indexname"),
                        rs.getString("indexdef")
                ),
                table.schema(),
                table.table()
        );

        List<IndexMetadata> indexes = new ArrayList<>();
        for (IndexRow row : rows) {
            if (row.indexName().endsWith("_pkey")) {
                continue;
            }
            indexes.add(parseIndex(row));
        }
        return indexes;
    }

    private List<IndexMetadata> filterIndexes(List<IndexMetadata> indexes,
                                              List<ColumnMetadata> migratedColumns) {
        Set<String> migratedNames = new LinkedHashSet<>();
        for (ColumnMetadata column : migratedColumns) {
            migratedNames.add(column.name());
        }
        List<IndexMetadata> filtered = new ArrayList<>();
        for (IndexMetadata index : indexes) {
            if (index.columns().isEmpty()) {
                continue;
            }
            if (migratedNames.containsAll(index.columns())) {
                filtered.add(index);
            } else {
                log.debug(
                        "Skipping source index '{}' because not all columns are migrated: {}",
                        index.name(),
                        index.columns()
                );
            }
        }
        return filtered;
    }

    private IndexMetadata parseIndex(IndexRow row) {
        String definition = row.indexDef();
        boolean unique = definition.toUpperCase(Locale.ROOT).contains("UNIQUE INDEX");
        String method = "btree";
        if (definition.toUpperCase(Locale.ROOT).contains("USING GIST")) {
            method = "gist";
        } else if (definition.toUpperCase(Locale.ROOT).contains("USING GIN")) {
            method = "gin";
        }

        int openParen = definition.indexOf('(');
        int closeParen = definition.lastIndexOf(')');
        List<String> columns = List.of();
        if (openParen >= 0 && closeParen > openParen) {
            String columnsPart = definition.substring(openParen + 1, closeParen);
            columns = List.of(columnsPart.split(",")).stream()
                    .map(String::trim)
                    .map(this::stripQuotes)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        return new IndexMetadata(row.indexName(), columns, method, unique);
    }

    private String requireConfiguredColumn(List<ColumnMetadata> columns,
                                           String configured,
                                           String field) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    field + " is required. Set batch.layers." + field + ".");
        }
        String name = configured.trim();
        requireColumn(columns, name, field);
        return name;
    }

    private void requireColumn(List<ColumnMetadata> columns, String name, String field) {
        boolean exists = columns.stream().anyMatch(col -> col.name().equals(name));
        if (!exists) {
            throw new IllegalStateException(
                    field + " '" + name + "' does not exist on the source table.");
        }
    }

    private String stripQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record GeometryInfo(String columnName, Integer registeredSrid, String geometryType) {
    }

    private record IndexRow(String indexName, String indexDef) {
    }
}
