package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.batch.config.table.AdministrativeUnitTableProperties;
import br.car.dsp_batch.sync.SyncKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobTableConfigValidatorTest {

    @Test
    void requireCreationDateColumn_RejectsMissingColumn() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.unit");
        config.setSyncKey(SyncKeys.ADMIN_UNIT_LEVEL_1);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JobTableConfigValidator.requireCreationDateColumn(config)
        );
        assertTrue(ex.getMessage().contains("creation-date-column"));
    }

    @Test
    void requireSyncKey_RejectsMissingKey() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.unit");
        config.setCreationDateColumn("source_created_at");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JobTableConfigValidator.requireSyncKey(config)
        );
        assertTrue(ex.getMessage().contains("sync-key"));
    }

    @Test
    void areaOfInterestConfig_DefaultSyncKey() {
        AreaOfInterestConfig config = new AreaOfInterestConfig();
        assertEquals(SyncKeys.AREA_OF_INTEREST, config.resolveSyncKey());
    }

    @Test
    void adminUnitProperties_ValidateRequiresCreationDate() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.unit");
        config.setSyncKey(SyncKeys.ADMIN_UNIT_LEVEL_1);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, config::validateWatermarkConfig);
        assertTrue(ex.getMessage().contains("creation-date-column"));
    }

    @Test
    void adminUnitProperties_ValidateRequiresSyncKey() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.unit");
        config.setCreationDateColumn("source_created_at");

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, config::validateWatermarkConfig);
        assertTrue(ex.getMessage().contains("sync-key"));
    }
}
