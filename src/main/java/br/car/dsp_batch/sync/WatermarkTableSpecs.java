package br.car.dsp_batch.sync;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.temporal.TemporalColumnSpecs;
/**
 * Factories that build {@link WatermarkTableSpec} from domain contracts.
 */
public final class WatermarkTableSpecs {

    private WatermarkTableSpecs() {
    }

    public static WatermarkTableSpec fromJobTableConfig(JobTableConfig tableConfig,
                                                        TemporalColumnSpecs temporalColumns) {
        String syncKey = tableConfig.getSyncKey();
        if (syncKey == null || syncKey.isBlank()) {
            throw new IllegalArgumentException(
                    "WATERMARK strategy requires sync-key for table "
                            + tableConfig.getSourceTable());
        }
        if (temporalColumns == null || temporalColumns.creationDateColumn() == null) {
            throw new IllegalArgumentException(
                    "WATERMARK strategy requires creation-date-column metadata for table "
                            + tableConfig.getSourceTable());
        }

        String targetPk = tableConfig.resolveTargetColumn(tableConfig.getPrimaryKey());
        String targetGeom = tableConfig.resolveTargetColumn(tableConfig.getGeometryColumn());

        return new WatermarkTableSpec(
                syncKey.trim(),
                tableConfig.getSourceTable(),
                tableConfig.getPrimaryKey(),
                tableConfig.getGeometryColumn(),
                temporalColumns.creationDateColumn(),
                temporalColumns.updatedAtColumn(),
                tableConfig.getWhereClause(),
                tableConfig.getSrid(),
                tableConfig.getLayerName(),
                tableConfig.getTargetTable(),
                targetPk,
                targetGeom,
                tableConfig.getTargetTable(),
                targetPk
        );
    }

    public static WatermarkTableSpec fromAreaOfInterestMetadata(AreaOfInterestTableMetadata metadata) {
        return new WatermarkTableSpec(
                metadata.syncKey(),
                metadata.qualifiedSourceTable(),
                metadata.primaryKeyColumn(),
                metadata.geometryColumn(),
                metadata.creationDateColumn(),
                metadata.updatedAtColumn(),
                metadata.whereClause(),
                metadata.srid(),
                metadata.layerName(),
                metadata.qualifiedTargetTable(),
                metadata.resolveTargetPrimaryKeyColumn(),
                metadata.resolveTargetGeometryColumn(),
                metadata.qualifiedTargetTable(),
                metadata.resolveTargetPrimaryKeyColumn()
        );
    }

    public static WatermarkTableSpec fromLayerMetadata(LayerTableMetadata metadata) {
        return new WatermarkTableSpec(
                metadata.layerKey(),
                metadata.qualifiedSourceTable(),
                metadata.primaryKeyColumn(),
                metadata.geometryColumn(),
                metadata.creationDateColumn(),
                metadata.updatedAtColumn(),
                metadata.whereClause(),
                metadata.srid(),
                metadata.layerName(),
                metadata.qualifiedTargetTable(),
                metadata.resolveTargetPrimaryKeyColumn(),
                metadata.resolveTargetGeometryColumn(),
                null,
                null
        );
    }
}
