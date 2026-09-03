package br.car.dsp_batch.layer.metadata;

import br.car.dsp_batch.temporal.WatermarkColumnSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static br.car.dsp_batch.layer.config.LayerConfig.AREA_OF_INTEREST_ID_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.CREATED_AT_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.GEOMETRY_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.ID_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.LABEL_COLUMN;
import static br.car.dsp_batch.layer.config.LayerConfig.UPDATED_AT_COLUMN;

/**
 * Metadata for a layer table discovered on the source database.
 * {@code columns} contains only the columns selected for migration.
 */
public record LayerTableMetadata(
        String layerKey,
        String layerName,
        QualifiedTable sourceTable,
        QualifiedTable targetTable,
        String primaryKeyColumn,
        String geometryColumn,
        String areaOfInterestIdSourceColumn,
        WatermarkColumnSpec creationDateColumn,
        WatermarkColumnSpec updatedAtColumn,
        String labelSourceColumn,
        int srid,
        List<ColumnMetadata> columns,
        List<IndexMetadata> indexes,
        String whereClause
) {

    public String creationDateSourceColumn() {
        return creationDateColumn.sourceColumn();
    }

    public String updatedAtSourceColumn() {
        return updatedAtColumn == null ? null : updatedAtColumn.sourceColumn();
    }

    public boolean hasUpdatedAtColumn() {
        return updatedAtColumn != null;
    }

    public boolean hasLabelColumn() {
        return labelSourceColumn != null && !labelSourceColumn.isBlank();
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

    public List<String> sourceComparisonColumnNames() {
        return sourceNonGeometryColumnNames();
    }

    public List<String> targetComparisonColumnNames() {
        return targetNonGeometryColumnNames();
    }

    /** Geometry column name on geo-target (always {@code geom}). */
    public String resolveTargetGeometryColumn() {
        return GEOMETRY_COLUMN;
    }

    /** Last-update column name on geo-target (always {@code updated_at}). */
    public String resolveTargetUpdatedAtColumn() {
        return UPDATED_AT_COLUMN;
    }

    /** Creation column name on geo-target (always {@code created_at}). */
    public String resolveTargetCreatedAtColumn() {
        return CREATED_AT_COLUMN;
    }

    /** Display-name column on geo-target (always {@code label}). */
    public String resolveTargetLabelColumn() {
        return LABEL_COLUMN;
    }

    public String resolveTargetColumnName(String sourceColumnName) {
        if (sourceColumnName.equals(primaryKeyColumn)) {
            return ID_COLUMN;
        }
        if (sourceColumnName.equals(geometryColumn)) {
            return GEOMETRY_COLUMN;
        }
        if (sourceColumnName.equals(creationDateSourceColumn())) {
            return CREATED_AT_COLUMN;
        }
        if (hasUpdatedAtColumn() && sourceColumnName.equals(updatedAtSourceColumn())) {
            return UPDATED_AT_COLUMN;
        }
        if (hasLabelColumn() && sourceColumnName.equals(labelSourceColumn)) {
            return LABEL_COLUMN;
        }
        if (sourceColumnName.equals(areaOfInterestIdSourceColumn)) {
            return AREA_OF_INTEREST_ID_COLUMN;
        }
        return sourceColumnName;
    }

    public String resolveSourceColumnName(String targetColumnName) {
        if (ID_COLUMN.equals(targetColumnName)) {
            return primaryKeyColumn;
        }
        if (GEOMETRY_COLUMN.equals(targetColumnName)) {
            return geometryColumn;
        }
        if (CREATED_AT_COLUMN.equals(targetColumnName)) {
            return creationDateSourceColumn();
        }
        if (UPDATED_AT_COLUMN.equals(targetColumnName)) {
            return updatedAtSourceColumn();
        }
        if (AREA_OF_INTEREST_ID_COLUMN.equals(targetColumnName)) {
            return areaOfInterestIdSourceColumn;
        }
        if (LABEL_COLUMN.equals(targetColumnName)) {
            return labelSourceColumn;
        }
        return targetColumnName;
    }

    public String resolveTargetPrimaryKeyColumn() {
        return ID_COLUMN;
    }

    public String qualifiedSourceTable() {
        return sourceTable.qualified();
    }

    public String qualifiedTargetTable() {
        return targetTable.qualified();
    }

    public ColumnMetadata findColumn(String sourceColumnName) {
        for (ColumnMetadata column : columns) {
            if (column.name().equals(sourceColumnName)) {
                return column;
            }
        }
        return null;
    }
}
