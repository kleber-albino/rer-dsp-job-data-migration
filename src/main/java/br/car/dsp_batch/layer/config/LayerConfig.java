package br.car.dsp_batch.layer.config;

import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for a geographic layer (source table) to migrate.
 * Each row in the source table is a feature.
 * Target table is {@code dsp.<physical-layer-name>} on geo-target
 * (resolved {@code layer-name}, hyphens become underscores).
 *
 * <p>Mandatory source columns are mapped to fixed target names
 * ({@code id}, {@code area_of_interest_id}, {@code created_at}, {@code geom}).
 * Optional mappings: {@code updated-at-column} → {@code updated_at},
 * {@code label-column} → {@code label}.
 * Optional extras in {@code additional-columns} keep the source column name on the target.
 */
@Getter
@Setter
public class LayerConfig {

    public static final String TARGET_SCHEMA = "dsp";

    /** Canonical primary key on every migrated layer table in geo-target. */
    public static final String ID_COLUMN = "id";

    /** Canonical FK column on every migrated layer table in geo-target. */
    public static final String AREA_OF_INTEREST_ID_COLUMN = "area_of_interest_id";

    /** Canonical creation timestamp on every migrated layer table in geo-target. */
    public static final String CREATED_AT_COLUMN = "created_at";

    /**
     * Canonical geometry column on every migrated layer table in geo-target.
     * Source may use another name ({@code the_geom}, {@code shape}, etc.);
     * configure {@code geometry-column} to choose which source column to migrate.
     */
    public static final String GEOMETRY_COLUMN = "geom";

    /**
     * Canonical last-update column on every migrated layer table in geo-target.
     * Source may use another name ({@code data_atualizacao}, etc.);
     * configure {@code updated-at-column} to choose which source column to migrate.
     */
    public static final String UPDATED_AT_COLUMN = "updated_at";

    /**
     * Canonical display-name column on every migrated layer table in geo-target.
     * Source may use another name ({@code nome}, {@code name}, etc.);
     * configure {@code label-column} to choose which source column to migrate.
     */
    public static final String LABEL_COLUMN = "label";

    /** Target column names reserved for the canonical contract. */
    public static final Set<String> CANONICAL_TARGET_COLUMNS = Set.of(
            ID_COLUMN,
            AREA_OF_INTEREST_ID_COLUMN,
            CREATED_AT_COLUMN,
            UPDATED_AT_COLUMN,
            LABEL_COLUMN,
            GEOMETRY_COLUMN
    );

    private String sourceTable;
    private String layerName;
    private String primaryKey;
    private String areaOfInterestIdColumn;
    private String creationDateColumn;
    private String updatedAtColumn;
    private String labelColumn;
    private String geometryColumn;
    /** Extra source columns to migrate (same name on target). */
    private List<String> additionalColumns = new ArrayList<>();
    private String whereClause = "1=1";
    private Integer srid;
    /**
     * Optional IANA timezone override for interpreting TIMESTAMP/DATE watermark columns.
     * Falls back to {@code batch.source-timezone}.
     */
    private String sourceTimezone;
    private boolean enabled = true;

    public QualifiedTable resolveSourceTable() {
        return QualifiedTable.parse(sourceTable);
    }

    public String physicalTableName() {
        return resolveLayerName().replace('-', '_');
    }

    public QualifiedTable resolveTargetTable() {
        return new QualifiedTable(TARGET_SCHEMA, physicalTableName());
    }

    public String resolveKey() {
        return TARGET_SCHEMA + "_" + physicalTableName();
    }

    public String resolveLayerName() {
        if (layerName != null && !layerName.isBlank()) {
            return layerName.trim();
        }
        return resolveSourceTable().table();
    }

    public boolean hasUpdatedAtColumn() {
        return updatedAtColumn != null && !updatedAtColumn.isBlank();
    }

    public boolean hasLabelColumn() {
        return labelColumn != null && !labelColumn.isBlank();
    }

    /**
     * Ordered set of source columns that must be migrated (required + extras).
     */
    public List<String> resolveMigratedSourceColumns() {
        Set<String> names = new LinkedHashSet<>();
        names.add(trimRequired(primaryKey));
        names.add(trimRequired(areaOfInterestIdColumn));
        names.add(trimRequired(creationDateColumn));
        if (hasUpdatedAtColumn()) {
            names.add(updatedAtColumn.trim());
        }
        if (hasLabelColumn()) {
            names.add(labelColumn.trim());
        }
        names.add(trimRequired(geometryColumn));
        if (additionalColumns != null) {
            for (String column : additionalColumns) {
                if (column != null && !column.isBlank()) {
                    names.add(column.trim());
                }
            }
        }
        return List.copyOf(names);
    }

    private static String trimRequired(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Validates YAML contract for this layer (required mappings and optional extras).
     */
    public void validate() {
        requireNonBlank("source-table", sourceTable);
        requireQualifiedTable(sourceTable);
        requireNonBlank("primary-key", primaryKey);
        requireNonBlank("area-of-interest-id-column", areaOfInterestIdColumn);
        requireNonBlank("creation-date-column", creationDateColumn);
        requireNonBlank("geometry-column", geometryColumn);
        validateAdditionalColumns();
    }

    private void validateAdditionalColumns() {
        if (additionalColumns == null || additionalColumns.isEmpty()) {
            return;
        }

        Set<String> requiredSource = requiredSourceColumns();

        Set<String> seen = new LinkedHashSet<>();
        for (String raw : additionalColumns) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException(
                        "additional-columns must not contain blank entries (source-table="
                                + sourceTable + ")");
            }
            String column = raw.trim();
            if (!seen.add(column)) {
                throw new IllegalStateException(
                        "duplicate additional-columns entry '" + column + "' (source-table="
                                + sourceTable + ")");
            }
            if (requiredSource.contains(column)) {
                throw new IllegalStateException(
                        "additional-columns entry '" + column
                                + "' duplicates a required column mapping (source-table="
                                + sourceTable + ")");
            }
            String lower = column.toLowerCase(java.util.Locale.ROOT);
            if (CANONICAL_TARGET_COLUMNS.contains(lower) || CANONICAL_TARGET_COLUMNS.contains(column)) {
                throw new IllegalStateException(
                        "additional-columns entry '" + column
                                + "' collides with a canonical target column name "
                                + CANONICAL_TARGET_COLUMNS + " (source-table=" + sourceTable + ")");
            }
        }
    }

    private Set<String> requiredSourceColumns() {
        Set<String> required = new LinkedHashSet<>();
        required.add(primaryKey.trim());
        required.add(areaOfInterestIdColumn.trim());
        required.add(creationDateColumn.trim());
        required.add(geometryColumn.trim());
        if (hasUpdatedAtColumn()) {
            required.add(updatedAtColumn.trim());
        }
        if (hasLabelColumn()) {
            required.add(labelColumn.trim());
        }
        return required;
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("batch.layers: '" + field + "' is required");
        }
    }

    private static void requireQualifiedTable(String value) {
        try {
            QualifiedTable.parse(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "batch.layers: invalid 'source-table' value '" + value + "'. " + ex.getMessage(),
                    ex);
        }
    }
}
