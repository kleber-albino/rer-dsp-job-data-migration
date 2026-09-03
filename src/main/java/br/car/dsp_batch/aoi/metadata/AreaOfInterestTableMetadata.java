package br.car.dsp_batch.aoi.metadata;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.WatermarkColumnSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Metadata for Area of Interest table discovered on the source database.
 */
public record AreaOfInterestTableMetadata(
        String syncKey,
        String layerName,
        QualifiedTable sourceTable,
        QualifiedTable targetTable,
        String primaryKeyColumn,
        WatermarkColumnSpec creationDateColumn,
        WatermarkColumnSpec updatedAtColumn,
        String territoryLevel3SourceColumn,
        String totalAreaSourceColumn,
        String geometryColumn,
        int srid,
        List<ColumnMetadata> columns,
        List<String> businessOnlySourceColumns,
        List<IndexMetadata> indexes,
        String whereClause
) {

    public boolean isBusinessOnlySourceColumn(String sourceColumnName) {
        return businessOnlySourceColumns.contains(sourceColumnName);
    }

    public String creationDateSourceColumn() {
        return creationDateColumn.sourceColumn();
    }

    public String updatedAtSourceColumn() {
        return updatedAtColumn == null ? null : updatedAtColumn.sourceColumn();
    }

    public boolean hasUpdatedAtColumn() {
        return updatedAtColumn != null;
    }

    public List<String> sourceNonGeometryColumnNames() {
        List<String> names = new ArrayList<>();
        for (ColumnMetadata column : columns) {
            if (!column.geometry()) {
                names.add(column.name());
            }
        }
        return names;
    }

    public List<String> targetNonGeometryColumnNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String sourceColumn : sourceNonGeometryColumnNames()) {
            names.add(resolveTargetColumnName(sourceColumn));
        }
        return List.copyOf(names);
    }

    /** Non-geometry columns written to geo-target (excludes business-only KPI columns). */
    public List<String> targetGeoNonGeometryColumnNames() {
        Set<String> names = new LinkedHashSet<>();
        for (String sourceColumn : sourceNonGeometryColumnNames()) {
            if (isBusinessOnlySourceColumn(sourceColumn)) {
                continue;
            }
            names.add(resolveTargetColumnName(sourceColumn));
        }
        return List.copyOf(names);
    }

    public String resolveTargetGeometryColumn() {
        return AreaOfInterestConfig.GEOMETRY_COLUMN;
    }

    public String resolveTargetUpdatedAtColumn() {
        return AreaOfInterestConfig.UPDATED_AT_COLUMN;
    }

    public String resolveTargetCreatedAtColumn() {
        return AreaOfInterestConfig.CREATED_AT_COLUMN;
    }

    public String resolveTargetColumnName(String sourceColumnName) {
        if (sourceColumnName.equals(primaryKeyColumn)) {
            return AreaOfInterestConfig.ID_COLUMN;
        }
        if (sourceColumnName.equals(creationDateSourceColumn())) {
            return AreaOfInterestConfig.CREATED_AT_COLUMN;
        }
        if (hasUpdatedAtColumn() && sourceColumnName.equals(updatedAtSourceColumn())) {
            return AreaOfInterestConfig.UPDATED_AT_COLUMN;
        }
        if (sourceColumnName.equals(territoryLevel3SourceColumn)) {
            return AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN;
        }
        if (sourceColumnName.equals(totalAreaSourceColumn)) {
            return AreaOfInterestConfig.AREA_COLUMN;
        }
        if (sourceColumnName.equals(geometryColumn)) {
            return AreaOfInterestConfig.GEOMETRY_COLUMN;
        }
        return sourceColumnName;
    }

    public String resolveSourceColumnName(String targetColumnName) {
        if (AreaOfInterestConfig.ID_COLUMN.equals(targetColumnName)) {
            return primaryKeyColumn;
        }
        if (AreaOfInterestConfig.CREATED_AT_COLUMN.equals(targetColumnName)) {
            return creationDateSourceColumn();
        }
        if (AreaOfInterestConfig.UPDATED_AT_COLUMN.equals(targetColumnName)) {
            return updatedAtSourceColumn();
        }
        if (AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN.equals(targetColumnName)) {
            return territoryLevel3SourceColumn;
        }
        if (AreaOfInterestConfig.AREA_COLUMN.equals(targetColumnName)) {
            return totalAreaSourceColumn;
        }
        if (AreaOfInterestConfig.GEOMETRY_COLUMN.equals(targetColumnName)) {
            return geometryColumn;
        }
        return targetColumnName;
    }

    public String resolveTargetPrimaryKeyColumn() {
        return AreaOfInterestConfig.ID_COLUMN;
    }

    public String qualifiedSourceTable() {
        return sourceTable.qualified();
    }

    public String qualifiedTargetTable() {
        return targetTable.qualified();
    }
}
