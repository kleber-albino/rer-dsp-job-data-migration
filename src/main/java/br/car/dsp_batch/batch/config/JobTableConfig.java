package br.car.dsp_batch.batch.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration contract for geographic table synchronization (watermark-only).
 */
public interface JobTableConfig {

    /** Fully qualified source table name (schema.table). */
    String getSourceTable();

    /** Fully qualified target table name (schema.table). */
    String getTargetTable();

    /** Primary key column name on the source table. */
    String getPrimaryKey();

    /**
     * Column used for range partitioning.
     * Defaults to the primary key when not explicitly set.
     */
    default String getPartitionColumn() {
        return getPrimaryKey();
    }

    /** Geometry column name on the source table. */
    String getGeometryColumn();

    /** WHERE clause fragment used when reading active source records (without the WHERE keyword). */
    String getWhereClause();

    /**
     * Columns persisted on both business and geo targets (excluding geometry).
     * Must include the primary key column.
     */
    List<String> getPersistColumns();

    /**
     * Extra columns written only to the business target ({@code dsp-db}).
     * Use for KPI measure columns (e.g. {@code theme_1}) that must not go to the exhibition DB.
     */
    default List<String> getBusinessOnlyPersistColumns() {
        return Collections.emptyList();
    }

    /**
     * {@link #getPersistColumns()} plus {@link #getBusinessOnlyPersistColumns()} (no duplicates).
     */
    default List<String> getAllBusinessPersistColumns() {
        List<String> merged = new ArrayList<>(getPersistColumns());
        List<String> businessOnly = getBusinessOnlyPersistColumns();
        if (businessOnly != null) {
            for (String column : businessOnly) {
                if (column != null && !column.isBlank() && !merged.contains(column)) {
                    merged.add(column);
                }
            }
        }
        return merged;
    }

    /** GeoServer / GeoWebCache layer name. */
    String getLayerName();

    /**
     * Maps source column names to target column names.
     * Empty map means source and target share the same names.
     */
    default Map<String, String> getColumnMapping() {
        return Collections.emptyMap();
    }

    /**
     * Geometry SRID configured per job in YAML (required; must be a positive integer).
     */
    int getSrid();

    /** Source column holding the record creation timestamp (mandatory). */
    String getCreationDateColumn();

    /** Source column holding the last update timestamp (optional). */
    String getUpdatedAtColumn();

    /**
     * Optional IANA timezone override for TIMESTAMP/DATE watermark columns.
     * Falls back to {@code batch.source-timezone}.
     */
    default String getSourceTimezone() {
        return null;
    }

    /** Key used in {@code batch_job_execution_sync_state}. */
    String getSyncKey();

    /** Resolves the target column name for a given source column. */
    default String resolveTargetColumn(String sourceColumn) {
        return getColumnMapping().getOrDefault(sourceColumn, sourceColumn);
    }
}
