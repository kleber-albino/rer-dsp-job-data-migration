package br.car.dsp_batch.batch.config;

/**
 * Validates watermark-related fields on {@link JobTableConfig}.
 */
public final class JobTableConfigValidator {

    private JobTableConfigValidator() {
    }

    public static void requireWatermarkFields(JobTableConfig tableConfig) {
        requireCreationDateColumn(tableConfig);
        requireSyncKey(tableConfig);
    }

    public static void requireCreationDateColumn(JobTableConfig tableConfig) {
        String column = tableConfig.getCreationDateColumn();
        if (column == null || column.isBlank()) {
            throw new IllegalArgumentException(
                    "creation-date-column is required for table " + tableConfig.getSourceTable());
        }
    }

    public static String requireSyncKey(JobTableConfig tableConfig) {
        String syncKey = tableConfig.getSyncKey();
        if (syncKey == null || syncKey.isBlank()) {
            throw new IllegalArgumentException(
                    "sync-key is required for table " + tableConfig.getSourceTable());
        }
        return syncKey.trim();
    }
}
