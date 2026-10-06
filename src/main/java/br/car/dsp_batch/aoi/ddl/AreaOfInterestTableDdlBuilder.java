package br.car.dsp_batch.aoi.ddl;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.layer.ddl.PostgresTypeMapper;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds CREATE TABLE and index DDL for AOI on geo-target and business target.
 */
@Slf4j
@Component
public class AreaOfInterestTableDdlBuilder {

    public static final String BOUNDARY_BOX_COLUMN = "boundary_box";
    public static final String CENTROID_COLUMN = "centroid_coordinates";

    private final PostgresTypeMapper typeMapper;

    public AreaOfInterestTableDdlBuilder(PostgresTypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    public List<DdlColumnDefinition> expectedBusinessColumns(AreaOfInterestTableMetadata metadata) {
        List<DdlColumnDefinition> columns = collectColumnDefinitions(metadata, false, true);
        if (!containsColumn(columns, AreaOfInterestConfig.AREA_COLUMN)
                && metadata.totalAreaSourceColumn() == null) {
            columns.add(new DdlColumnDefinition(AreaOfInterestConfig.AREA_COLUMN, "numeric"));
        }
        if (!containsColumn(columns, AreaOfInterestConfig.UPDATED_AT_COLUMN)) {
            columns.add(new DdlColumnDefinition(AreaOfInterestConfig.UPDATED_AT_COLUMN, "timestamptz"));
        }
        columns.add(new DdlColumnDefinition(
                BOUNDARY_BOX_COLUMN, "geometry(Polygon, " + metadata.srid() + ")"));
        columns.add(new DdlColumnDefinition(
                CENTROID_COLUMN, "geometry(Point, " + metadata.srid() + ")"));
        return dedupeColumns(columns);
    }

    public List<DdlColumnDefinition> expectedGeoColumns(AreaOfInterestTableMetadata metadata) {
        return collectColumnDefinitions(metadata, true, false);
    }

    public List<String> buildGeoTargetStatements(AreaOfInterestTableMetadata metadata) {
        List<String> statements = new ArrayList<>();
        statements.add(buildGeoCreateTable(metadata));
        statements.addAll(buildGeoIndexStatements(metadata));
        return statements;
    }

    public List<String> buildBusinessTargetStatements(AreaOfInterestTableMetadata metadata) {
        List<String> statements = new ArrayList<>();
        statements.add(buildBusinessCreateTable(metadata));
        statements.addAll(buildBusinessIndexStatements(metadata));
        return statements;
    }

    public List<String> buildBusinessIndexStatements(AreaOfInterestTableMetadata metadata) {
        List<String> statements = new ArrayList<>();
        statements.add(buildBusinessUpdatedAtIndex(metadata));
        statements.add(buildBusinessCreatedAtIndex(metadata));
        return statements;
    }

    public List<String> buildGeoIndexStatements(AreaOfInterestTableMetadata metadata) {
        List<String> statements = new ArrayList<>();
        statements.add(buildGeometryIndex(metadata));
        statements.add(buildCreatedAtIndex(metadata));
        if (metadata.hasUpdatedAtColumn()) {
            statements.add(buildUpdatedAtIndex(metadata));
        }
        statements.addAll(buildSecondaryIndexes(metadata));
        return statements;
    }

    public String buildGeoCreateTable(AreaOfInterestTableMetadata metadata) {
        List<String> columnDefinitions = formatColumnDefinitions(expectedGeoColumns(metadata));
        String targetPk = metadata.resolveTargetPrimaryKeyColumn();
        columnDefinitions.add("PRIMARY KEY (" + quote(targetPk) + ")");

        return "CREATE TABLE IF NOT EXISTS " + metadata.qualifiedTargetTable()
                + " (\n    "
                + String.join(",\n    ", columnDefinitions)
                + "\n)";
    }

    public String buildBusinessCreateTable(AreaOfInterestTableMetadata metadata) {
        List<String> columnDefinitions = formatColumnDefinitions(expectedBusinessColumns(metadata));
        String targetPk = metadata.resolveTargetPrimaryKeyColumn();
        columnDefinitions.add("PRIMARY KEY (" + quote(targetPk) + ")");

        return "CREATE TABLE IF NOT EXISTS " + metadata.qualifiedTargetTable()
                + " (\n    "
                + String.join(",\n    ", columnDefinitions)
                + "\n)";
    }

    private List<DdlColumnDefinition> collectColumnDefinitions(AreaOfInterestTableMetadata metadata,
                                                             boolean includeGeometry,
                                                             boolean includeBusinessExtras) {
        List<DdlColumnDefinition> columnDefinitions = new ArrayList<>();
        Set<String> emittedTargetColumns = new LinkedHashSet<>();

        for (ColumnMetadata column : metadata.columns()) {
            if (column.geometry()) {
                if (!includeGeometry) {
                    continue;
                }
                if (!column.name().equals(metadata.geometryColumn())) {
                    continue;
                }
                String targetGeom = metadata.resolveTargetGeometryColumn();
                if (!emittedTargetColumns.add(targetGeom)) {
                    continue;
                }
                columnDefinitions.add(new DdlColumnDefinition(
                        targetGeom, "geometry(Geometry, " + metadata.srid() + ")"));
                continue;
            }

            String targetColumnName = metadata.resolveTargetColumnName(column.name());
            if (!emittedTargetColumns.add(targetColumnName)) {
                continue;
            }

            String ddlType = resolveDdlType(targetColumnName, column);
            columnDefinitions.add(new DdlColumnDefinition(targetColumnName, ddlType));
        }
        if (includeBusinessExtras) {
            if (!emittedTargetColumns.contains(AreaOfInterestConfig.AREA_COLUMN)
                    && metadata.totalAreaSourceColumn() == null) {
                columnDefinitions.add(new DdlColumnDefinition(AreaOfInterestConfig.AREA_COLUMN, "numeric"));
                emittedTargetColumns.add(AreaOfInterestConfig.AREA_COLUMN);
            }
            if (emittedTargetColumns.add(AreaOfInterestConfig.UPDATED_AT_COLUMN)) {
                columnDefinitions.add(new DdlColumnDefinition(AreaOfInterestConfig.UPDATED_AT_COLUMN, "timestamptz"));
            }
        }
        return columnDefinitions;
    }

    private List<String> formatColumnDefinitions(List<DdlColumnDefinition> columns) {
        return columns.stream()
                .map(col -> quote(col.name()) + " " + col.sqlType())
                .collect(Collectors.toList());
    }

    private List<DdlColumnDefinition> dedupeColumns(List<DdlColumnDefinition> columns) {
        List<DdlColumnDefinition> deduped = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DdlColumnDefinition column : columns) {
            if (seen.add(column.name())) {
                deduped.add(column);
            }
        }
        return deduped;
    }

    private boolean containsColumn(List<DdlColumnDefinition> columns, String name) {
        return columns.stream().anyMatch(col -> col.name().equals(name));
    }

    private String resolveDdlType(String targetColumnName, ColumnMetadata column) {
        if (AreaOfInterestConfig.CREATED_AT_COLUMN.equals(targetColumnName)
                || AreaOfInterestConfig.UPDATED_AT_COLUMN.equals(targetColumnName)) {
            return "timestamptz";
        }
        if (AreaOfInterestConfig.ID_COLUMN.equals(targetColumnName)) {
            return "varchar(255)";
        }
        if (AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN.equals(targetColumnName)) {
            return "varchar(64)";
        }
        return typeMapper.toDdlType(column);
    }

    public String buildGeometryIndex(AreaOfInterestTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_geom_gist");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " USING GIST (" + quote(metadata.resolveTargetGeometryColumn()) + ")";
    }

    public String buildCreatedAtIndex(AreaOfInterestTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_created_at");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " (" + quote(AreaOfInterestConfig.CREATED_AT_COLUMN) + ")";
    }

    public String buildBusinessCreatedAtIndex(AreaOfInterestTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_business_created_at");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " (" + quote(AreaOfInterestConfig.CREATED_AT_COLUMN) + ")";
    }

    public String buildUpdatedAtIndex(AreaOfInterestTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_updated_at");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " (" + quote(AreaOfInterestConfig.UPDATED_AT_COLUMN) + ")";
    }

    public String buildBusinessUpdatedAtIndex(AreaOfInterestTableMetadata metadata) {
        String indexName = sanitizeIndexName(metadata.targetTable().table() + "_business_updated_at");
        return "CREATE INDEX IF NOT EXISTS " + quote(indexName)
                + " ON " + metadata.qualifiedTargetTable()
                + " (" + quote(AreaOfInterestConfig.UPDATED_AT_COLUMN) + ")";
    }

    public List<String> buildSecondaryIndexes(AreaOfInterestTableMetadata metadata) {
        List<String> statements = new ArrayList<>();
        Set<String> targetColumns = new LinkedHashSet<>(metadata.targetGeoNonGeometryColumnNames());
        targetColumns.add(metadata.resolveTargetGeometryColumn());

        for (IndexMetadata index : metadata.indexes()) {
            if (index.columns().isEmpty()) {
                continue;
            }

            List<String> mappedColumns = index.columns().stream()
                    .map(metadata::resolveTargetColumnName)
                    .toList();

            List<String> missingColumns = mappedColumns.stream()
                    .filter(column -> !targetColumns.contains(column))
                    .toList();
            if (!missingColumns.isEmpty()) {
                log.warn(
                        "Skipping secondary index '{}' on {}: mapped column(s) {} not present on target",
                        index.name(),
                        metadata.qualifiedTargetTable(),
                        missingColumns
                );
                continue;
            }

            if (isRedundantWithCanonicalIndexes(metadata, mappedColumns, index.method())) {
                continue;
            }

            String indexName = sanitizeIndexName(metadata.targetTable().table() + "_" + index.name());
            String columns = String.join(", ", mappedColumns.stream().map(this::quote).toList());
            String unique = index.unique() ? "UNIQUE " : "";
            String method = index.method() != null && !index.method().isBlank()
                    ? " USING " + index.method().toUpperCase()
                    : "";

            statements.add("CREATE " + unique + "INDEX IF NOT EXISTS " + quote(indexName)
                    + " ON " + metadata.qualifiedTargetTable()
                    + method + " (" + columns + ")");
        }
        return statements;
    }

    private boolean isRedundantWithCanonicalIndexes(AreaOfInterestTableMetadata metadata,
                                                    List<String> mappedColumns,
                                                    String method) {
        if (mappedColumns.size() != 1) {
            return false;
        }
        String only = mappedColumns.getFirst();
        if (only.equals(metadata.resolveTargetGeometryColumn()) && "gist".equalsIgnoreCase(method)) {
            return true;
        }
        return only.equals(AreaOfInterestConfig.UPDATED_AT_COLUMN)
                || only.equals(AreaOfInterestConfig.CREATED_AT_COLUMN);
    }

    private String sanitizeIndexName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
