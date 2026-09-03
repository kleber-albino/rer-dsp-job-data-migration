package br.car.dsp_batch.batch.config.table;

import br.car.dsp_batch.sync.SyncKeys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized {@link br.car.dsp_batch.batch.config.JobTableConfig} beans
 * for each administrative unit level.
 */
@Configuration
public class AdministrativeUnitTableConfigs {

    @Bean(name = "adminUnitLevel1TableConfig")
    @ConfigurationProperties(prefix = "batch.admin-unit.level-1")
    public AdministrativeUnitTableProperties adminUnitLevel1TableConfig() {
        AdministrativeUnitTableProperties properties = new AdministrativeUnitTableProperties();
        properties.setSyncKey(SyncKeys.ADMIN_UNIT_LEVEL_1);
        return properties;
    }

    @Bean(name = "adminUnitLevel2TableConfig")
    @ConfigurationProperties(prefix = "batch.admin-unit.level-2")
    public AdministrativeUnitTableProperties adminUnitLevel2TableConfig() {
        AdministrativeUnitTableProperties properties = new AdministrativeUnitTableProperties();
        properties.setSyncKey(SyncKeys.ADMIN_UNIT_LEVEL_2);
        return properties;
    }

    @Bean(name = "adminUnitLevel3TableConfig")
    @ConfigurationProperties(prefix = "batch.admin-unit.level-3")
    public AdministrativeUnitTableProperties adminUnitLevel3TableConfig() {
        AdministrativeUnitTableProperties properties = new AdministrativeUnitTableProperties();
        properties.setSyncKey(SyncKeys.ADMIN_UNIT_LEVEL_3);
        return properties;
    }
}
