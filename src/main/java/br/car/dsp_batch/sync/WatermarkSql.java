package br.car.dsp_batch.sync;

import br.car.dsp_batch.temporal.WatermarkColumnSpec;
import br.car.dsp_batch.temporal.WatermarkTemporalBridge;

import java.time.Instant;

/**
 * SQL fragments for incremental change detection on source temporal columns.
 */
public final class WatermarkSql {

    private WatermarkSql() {
    }

    /**
     * Builds a WHERE fragment using creation (mandatory) and optional update columns.
     * First load: {@code creation IS NOT NULL}. Incremental: creation or update after watermark.
     */
    public static String buildChangeDetectionFilter(WatermarkColumnSpec creationDateColumn,
                                                    WatermarkColumnSpec updatedAtColumn,
                                                    Instant watermark) {
        return WatermarkTemporalBridge.buildChangeDetectionPredicate(
                creationDateColumn, updatedAtColumn, watermark).sqlFragment();
    }

    /**
     * Combines the optional YAML {@code where-clause} with the change-detection filter.
     * See {@link #combineWhere(String, String)} for semantics.
     */
    public static String combineWhere(String configWhere, String changeDetectionFilter) {
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        if (!hasConfigWhere && changeDetectionFilter == null) {
            return null;
        }
        if (hasConfigWhere && changeDetectionFilter == null) {
            return configWhere;
        }
        if (!hasConfigWhere) {
            return changeDetectionFilter;
        }
        return "(" + configWhere + ") AND " + changeDetectionFilter;
    }
}
