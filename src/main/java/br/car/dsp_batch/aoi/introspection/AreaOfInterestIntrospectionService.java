package br.car.dsp_batch.aoi.introspection;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.layer.introspection.SchemaIntrospectionService;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.BatchTemporalProperties;
import br.car.dsp_batch.temporal.SourceTemporalPolicy;
import br.car.dsp_batch.temporal.TemporalType;
import br.car.dsp_batch.temporal.TemporalTypeClassifier;
import br.car.dsp_batch.temporal.WatermarkColumnSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static br.car.dsp_batch.aoi.config.AreaOfInterestConfig.AREA_COLUMN;
import static br.car.dsp_batch.aoi.config.AreaOfInterestConfig.CREATED_AT_COLUMN;
import static br.car.dsp_batch.aoi.config.AreaOfInterestConfig.GEOMETRY_COLUMN;
import static br.car.dsp_batch.aoi.config.AreaOfInterestConfig.ID_COLUMN;
import static br.car.dsp_batch.aoi.config.AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN;
import static br.car.dsp_batch.aoi.config.AreaOfInterestConfig.UPDATED_AT_COLUMN;

/**
 * Discovers AOI source structure and selects columns for migration (layer-like contract).
 */
@Slf4j
@Service
public class AreaOfInterestIntrospectionService {

    private final BatchTemporalProperties batchTemporalProperties;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final JdbcTemplate geoTargetJdbcTemplate;
    private final JdbcTemplate targetJdbcTemplate;

    public AreaOfInterestIntrospectionService(
            BatchTemporalProperties batchTemporalProperties,
            SchemaIntrospectionService schemaIntrospectionService,
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate,
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate) {
        this.batchTemporalProperties = batchTemporalProperties;
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
        this.targetJdbcTemplate = targetJdbcTemplate;
    }

    public AreaOfInterestTableMetadata introspect(JdbcTemplate sourceJdbc, AreaOfInterestConfig config) {
        config.validate();

        QualifiedTable source = config.resolveSourceTable();
        QualifiedTable target = config.resolveTargetTable();

        if (!schemaIntrospectionService.tableExists(sourceJdbc, source)) {
            throw new IllegalStateException("Source table not found: " + source.qualified());
        }

        List<ColumnMetadata> allColumns = fetchColumns(sourceJdbc, source);
        if (allColumns.isEmpty()) {
            throw new IllegalStateException("Source table has no columns: " + source.qualified());
        }

        String primaryKey = requireConfiguredColumn(allColumns, config.getPrimaryKey(), "primary-key");
        String creationDateColumnName = requireConfiguredColumn(
                allColumns, config.getCreationDateColumn(), "creation-date-column");
        WatermarkColumnSpec creationDateColumn = requireTemporalColumn(
                allColumns, creationDateColumnName, config, "creation-date-column");
        WatermarkColumnSpec updatedAtColumn = resolveOptionalTemporalColumn(
                allColumns, config.getUpdatedAtColumn(), config, "updated-at-column");
        String updatedAtColumnName = updatedAtColumn == null ? null : updatedAtColumn.sourceColumn();
        String territoryLevel3Column = requireConfiguredColumn(
                allColumns, config.getTerritoryLevel3Column(), "territory-level-3-column");
        String totalAreaColumn = requireConfiguredColumn(
                allColumns, config.getTotalAreaColumn(), "total-area-column");
        GeometryInfo geometryInfo = resolveGeometry(sourceJdbc, source, allColumns, config.getGeometryColumn());

        List<String> additionalColumns = normalizeOptionalColumns(config.getAdditionalColumns());
        List<String> businessOnlyColumns = normalizeOptionalColumns(config.getBusinessOnlyPersistColumns());
        validateOptionalColumnsExist(allColumns, additionalColumns, "additional-columns");
        validateOptionalColumnsExist(allColumns, businessOnlyColumns, "business-only-persist-columns");
        rejectCanonicalTargetNameCollisions(source, additionalColumns, businessOnlyColumns);

        int srid = resolveSrid(sourceJdbc, source, geometryInfo, config.getSrid());
        requireExistingTargetCreatedAtTimestamptz(targetJdbcTemplate, target);
        requireExistingTargetCreatedAtTimestamptz(geoTargetJdbcTemplate, target);

        List<ColumnMetadata> migratedColumns = selectMigratedColumns(
                allColumns,
                primaryKey,
                creationDateColumnName,
                updatedAtColumnName,
                territoryLevel3Column,
                totalAreaColumn,
                geometryInfo.columnName(),
                additionalColumns,
                businessOnlyColumns);
        List<IndexMetadata> indexes = filterIndexes(
                fetchIndexes(sourceJdbc, source), migratedColumns);

        log.info(
                "AOI introspection completed for {}: pk={} -> {}, creationDate={} -> {}, "
                        + "sourceUpdatedAt={} -> {}, territoryLevel3={} -> {}, totalArea={} -> {}, "
                        + "sourceGeom={} -> {}, srid={}, migratedColumns={}",
                source.qualified(),
                primaryKey,
                ID_COLUMN,
                creationDateColumnName,
                CREATED_AT_COLUMN,
                updatedAtColumnName,
                updatedAtColumnName == null ? "—" : UPDATED_AT_COLUMN,
                territoryLevel3Column,
                TERRITORY_LEVEL_3_ID_COLUMN,
                totalAreaColumn,
                AREA_COLUMN,
                geometryInfo.columnName(),
                GEOMETRY_COLUMN,
                srid,
                migratedColumns.size()
        );

        return new AreaOfInterestTableMetadata(
                config.resolveSyncKey(),
                config.resolveLayerName(),
                source,
                target,
                primaryKey,
                creationDateColumn,
                updatedAtColumn,
                territoryLevel3Column,
                totalAreaColumn,
                geometryInfo.columnName(),
                srid,
                migratedColumns,
                businessOnlyColumns,
                indexes,
                config.getWhereClause()
        );
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
                (rs, rowNum) -> new ColumnMetadata(
                        rs.getString("column_name"),
                        rs.getString("udt_name"),
                        (Integer) rs.getObject("character_maximum_length"),
                        (Integer) rs.getObject("numeric_precision"),
                        (Integer) rs.getObject("numeric_scale"),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                        "geometry".equalsIgnoreCase(rs.getString("udt_name"))
                                || "geography".equalsIgnoreCase(rs.getString("udt_name"))
                ),
                table.schema(),
                table.table()
        );
    }

    private List<ColumnMetadata> selectMigratedColumns(List<ColumnMetadata> allColumns,
                                                       String primaryKey,
                                                       String creationDateColumn,
                                                       String updatedAtColumn,
                                                       String territoryLevel3Column,
                                                       String totalAreaColumn,
                                                       String geometryColumn,
                                                       List<String> additionalColumns,
                                                       List<String> businessOnlyColumns) {
        Map<String, ColumnMetadata> byName = new LinkedHashMap<>();
        for (ColumnMetadata column : allColumns) {
            byName.put(column.name(), column);
        }

        List<String> orderedNames = new ArrayList<>();
        orderedNames.add(primaryKey);
        orderedNames.add(creationDateColumn);
        if (updatedAtColumn != null) {
            orderedNames.add(updatedAtColumn);
        }
        orderedNames.add(territoryLevel3Column);
        orderedNames.add(totalAreaColumn);
        orderedNames.addAll(additionalColumns);
        orderedNames.addAll(businessOnlyColumns);
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

    private List<String> normalizeOptionalColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String column : columns) {
            if (column != null && !column.isBlank()) {
                normalized.add(column.trim());
            }
        }
        return normalized;
    }

    private void validateOptionalColumnsExist(List<ColumnMetadata> columns,
                                                List<String> optionalColumns,
                                                String field) {
        for (String column : optionalColumns) {
            requireColumn(columns, column, field);
        }
    }

    private void requireExistingTargetCreatedAtTimestamptz(JdbcTemplate jdbc, QualifiedTable target) {
        if (!schemaIntrospectionService.tableExists(jdbc, target)) {
            return;
        }
        List<String> udts = jdbc.query(
                """
                SELECT udt_name
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """,
                (rs, rowNum) -> rs.getString("udt_name"),
                target.schema(),
                target.table(),
                CREATED_AT_COLUMN
        );
        if (udts.isEmpty()) {
            throw new IllegalStateException(
                    "Target table " + target.qualified()
                            + " exists but has no '" + CREATED_AT_COLUMN + "' column.");
        }
        TemporalType type = TemporalTypeClassifier.classify(udts.getFirst());
        if (type != TemporalType.TIMESTAMPTZ) {
            throw new IllegalStateException(
                    "Destination column '" + CREATED_AT_COLUMN + "' on " + target.qualified()
                            + " must be timestamptz (found '" + udts.getFirst()
                            + "'). Refusing to ALTER automatically.");
        }
    }

    private void rejectCanonicalTargetNameCollisions(QualifiedTable table,
                                                     List<String> additionalColumns,
                                                     List<String> businessOnlyColumns) {
        for (String additionalColumn : additionalColumns) {
            if (AreaOfInterestConfig.CANONICAL_TARGET_COLUMNS.contains(additionalColumn)) {
                throw new IllegalStateException(
                        "additional-columns entry '" + additionalColumn
                                + "' collides with a canonical target column on "
                                + table.qualified() + ".");
            }
        }
        for (String businessOnlyColumn : businessOnlyColumns) {
            if (AreaOfInterestConfig.CANONICAL_TARGET_COLUMNS.contains(businessOnlyColumn)) {
                throw new IllegalStateException(
                        "business-only-persist-columns entry '" + businessOnlyColumn
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
                    "geometry-column is required for batch.area-of-interest.");
        }
        String chosen = configured.trim();
        requireColumn(columns, chosen, "geometry-column");
        requireGeometryTypedColumn(columns, chosen);
        GeometryInfo registered = findRegisteredGeometry(jdbc, table, chosen);
        return registered != null ? registered : new GeometryInfo(chosen, null, null);
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
                    "geometry-column '" + name + "' exists but is not geometry/geography.");
        }
    }

    private WatermarkColumnSpec requireTemporalColumn(List<ColumnMetadata> columns,
                                                      String columnName,
                                                      AreaOfInterestConfig config,
                                                      String field) {
        ColumnMetadata column = columns.stream()
                .filter(col -> col.name().equals(columnName))
                .findFirst()
                .orElseThrow();
        TemporalType type = TemporalTypeClassifier.classify(column.udtName());
        if (!type.isWatermarkSupported()) {
            throw new IllegalStateException(
                    field + " '" + columnName + "' has type '" + column.udtName()
                            + "'. Expected timestamp, timestamptz or date.");
        }
        SourceTemporalPolicy policy = batchTemporalProperties.resolvePolicy(
                config.getSourceTimezone(),
                "batch.area-of-interest.source-timezone for " + config.getSourceTable()
        );
        if (type == TemporalType.DATE) {
            log.warn(
                    "{} '{}' on {} is DATE — watermark granularity is daily",
                    field,
                    columnName,
                    config.getSourceTable()
            );
        }
        return WatermarkColumnSpec.of(columnName, type, policy);
    }

    private WatermarkColumnSpec resolveOptionalTemporalColumn(List<ColumnMetadata> columns,
                                                              String configured,
                                                              AreaOfInterestConfig config,
                                                              String field) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        String name = requireConfiguredColumn(columns, configured, field);
        return requireTemporalColumn(columns, name, config, field);
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
                            + ". Set batch.area-of-interest.srid.");
        }
        return sampledSrid;
    }

    private List<IndexMetadata> fetchIndexes(JdbcTemplate jdbc, QualifiedTable table) {
        return jdbc.query(
                """
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = ? AND tablename = ?
                ORDER BY indexname
                """,
                (rs, rowNum) -> parseIndex(
                        rs.getString("indexname"),
                        rs.getString("indexdef")
                ),
                table.schema(),
                table.table()
        ).stream()
                .filter(index -> !index.name().endsWith("_pkey"))
                .toList();
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
            }
        }
        return filtered;
    }

    private IndexMetadata parseIndex(String indexName, String definition) {
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

        return new IndexMetadata(indexName, columns, method, unique);
    }

    private String requireConfiguredColumn(List<ColumnMetadata> columns,
                                           String configured,
                                           String field) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    field + " is required. Set batch.area-of-interest." + field + ".");
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
}
