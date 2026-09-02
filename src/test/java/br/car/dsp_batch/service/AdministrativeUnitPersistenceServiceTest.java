package br.car.dsp_batch.service;

import br.car.dsp_batch.batch.config.table.AdministrativeUnitTableProperties;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AdministrativeUnitPersistenceServiceTest {

    @ParameterizedTest
    @ValueSource(ints = {4326, 4674, 3857})
    void requirePositiveSrid_AcceptsConfiguredValues(int srid) {
        AdministrativeUnitTableProperties config = baseConfig();
        config.setSrid(srid);
        assertDoesNotThrow(() -> AdministrativeUnitPersistenceService.requirePositiveSrid(config));
    }

    @Test
    void requirePositiveSrid_RejectsMissingOrInvalid() {
        AdministrativeUnitTableProperties config = baseConfig();
        config.setSrid(0);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> AdministrativeUnitPersistenceService.requirePositiveSrid(config)
        );
        assertTrue(ex.getMessage().contains("srid"));
    }

    @Test
    void upsertAll_FailsFastWhenBusinessTargetFails() {
        JdbcTemplate target = mock(JdbcTemplate.class);
        JdbcTemplate geoTarget = mock(JdbcTemplate.class);
        doThrow(new DataAccessResourceFailureException("business down"))
                .when(target).batchUpdate(anyString(), anyList(), anyInt(), any());

        AdministrativeUnitPersistenceService service =
                new AdministrativeUnitPersistenceService(target, geoTarget);

        AdministrativeUnitTableProperties config = baseConfig();
        config.setSrid(4326);

        assertThrows(RuntimeException.class, () -> service.upsertAll(List.of(sampleItem()), config));
        verify(geoTarget, never()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void upsertAll_FailsFastWhenGeoTargetFailsAfterBusiness() {
        JdbcTemplate target = mock(JdbcTemplate.class);
        JdbcTemplate geoTarget = mock(JdbcTemplate.class);
        doThrow(new DataAccessResourceFailureException("geo down"))
                .when(geoTarget).batchUpdate(anyString(), anyList(), anyInt(), any());

        AdministrativeUnitPersistenceService service =
                new AdministrativeUnitPersistenceService(target, geoTarget);

        AdministrativeUnitTableProperties config = baseConfig();
        config.setSrid(4674);

        assertThrows(RuntimeException.class, () -> service.upsertAll(List.of(sampleItem()), config));
        verify(target, times(1)).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    private static AdministrativeUnitTableProperties baseConfig() {
        AdministrativeUnitTableProperties config = new AdministrativeUnitTableProperties();
        config.setSourceTable("source.unit");
        config.setTargetTable("dsp.territory_level_1");
        config.setPrimaryKey("id");
        config.setGeometryColumn("geom");
        config.setPersistColumns(List.of("id", "name"));
        config.setColumnMapping(java.util.Map.of("id", "id", "name", "name", "geom", "geometry"));
        config.setLayerName("territory-level-1");
        return config;
    }

    private static AdministrativeUnitDTO sampleItem() {
        AdministrativeUnitDTO dto = new AdministrativeUnitDTO();
        dto.setId("1");
        dto.setGeometryGeoJson("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,1],[0,0]]]}");
        dto.putAttribute("id", "1");
        dto.putAttribute("name", "Unit");
        return dto;
    }
}
