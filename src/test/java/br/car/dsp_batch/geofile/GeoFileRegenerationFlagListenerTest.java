package br.car.dsp_batch.geofile;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.batch.config.table.AdministrativeUnitTableProperties;
import br.car.dsp_batch.sync.DepartedLevel3ContextSupport;
import br.car.dsp_batch.sync.SyncKeys;
import br.car.dsp_batch.sync.WatermarkContextKeys;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeoFileRegenerationFlagListenerTest {

    private static final Instant WATERMARK = Instant.parse("2026-03-01T00:00:00Z");

    private final GeoFileRegenerationFlagRepository repository =
            mock(GeoFileRegenerationFlagRepository.class);

    @Test
    void afterJob_FlagsTheLevel2TerritoriesTheRunChanged() {
        listener().afterJob(execution(BatchStatus.COMPLETED, SyncKeys.ADMIN_UNIT_LEVEL_2, WATERMARK));

        verify(repository).markChangedTerritories("dsp.territory_level_2", WATERMARK);
    }

    @Test
    void afterJob_FlagsTheLevel3TerritoriesTheRunChanged() {
        listener().afterJob(execution(BatchStatus.COMPLETED, SyncKeys.ADMIN_UNIT_LEVEL_3, WATERMARK));

        verify(repository).markChangedTerritories("dsp.territory_level_3", WATERMARK);
    }

    @Test
    void afterJob_FlagsBothParentsOfTheChangedAreasOfInterest() {
        when(repository.markParentsOfChangedAreasOfInterest(
                anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new GeoFileRegenerationFlagRepository.AreaOfInterestFlagResult(1, 2));
        when(repository.markLevel3TerritoriesAndParents(
                anyCollection(), anyString(), anyString()))
                .thenReturn(new GeoFileRegenerationFlagRepository.DepartedTerritoryFlagResult(0, 0));

        listener().afterJob(execution(BatchStatus.COMPLETED, SyncKeys.AREA_OF_INTEREST, WATERMARK));

        verify(repository).markParentsOfChangedAreasOfInterest(
                "dsp.area_of_interest",
                AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN,
                "dsp.territory_level_3",
                "dsp.territory_level_2",
                WATERMARK);
        verify(repository).markLevel3TerritoriesAndParents(
                Set.of(),
                "dsp.territory_level_3",
                "dsp.territory_level_2");
    }

    @Test
    void afterJob_FlagsDepartedLevel3TerritoriesThatLostAreasOfInterest() {
        when(repository.markParentsOfChangedAreasOfInterest(
                anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new GeoFileRegenerationFlagRepository.AreaOfInterestFlagResult(1, 1));
        when(repository.markLevel3TerritoriesAndParents(
                anyCollection(), anyString(), anyString()))
                .thenReturn(new GeoFileRegenerationFlagRepository.DepartedTerritoryFlagResult(1, 1));

        JobExecution jobExecution = execution(BatchStatus.COMPLETED, SyncKeys.AREA_OF_INTEREST, WATERMARK);
        DepartedLevel3ContextSupport.merge(jobExecution.getExecutionContext(), Set.of("l3-a"));

        listener().afterJob(jobExecution);

        verify(repository).markLevel3TerritoriesAndParents(
                Set.of("l3-a"),
                "dsp.territory_level_3",
                "dsp.territory_level_2");
    }

    @Test
    void afterJob_LeavesTheFlagsAloneWhenTheRunDidNotComplete() {
        listener().afterJob(execution(BatchStatus.FAILED, SyncKeys.ADMIN_UNIT_LEVEL_2, WATERMARK));

        verifyNoInteractions(repository);
    }

    @Test
    void afterJob_IgnoresLevel1BecauseItHasNoTerritorialDownload() {
        listener().afterJob(execution(BatchStatus.COMPLETED, SyncKeys.ADMIN_UNIT_LEVEL_1, WATERMARK));

        verifyNoInteractions(repository);
    }

    @Test
    void afterJob_DoesNothingWithoutASyncKey() {
        listener().afterJob(execution(BatchStatus.COMPLETED, null, WATERMARK));

        verifyNoInteractions(repository);
    }

    @Test
    void afterJob_PassesANullWatermarkOnFirstLoad() {
        listener().afterJob(execution(BatchStatus.COMPLETED, SyncKeys.ADMIN_UNIT_LEVEL_2, null));

        verify(repository).markChangedTerritories("dsp.territory_level_2", null);
    }

    @Test
    void afterJob_SkipsTheAreaOfInterestFlagsWhenItsConfigurationIsAbsent() {
        GeoFileRegenerationFlagListener listener = new GeoFileRegenerationFlagListener(
                repository,
                tableConfig("dsp.territory_level_2"),
                tableConfig("dsp.territory_level_3"),
                emptyProvider());

        listener.afterJob(execution(BatchStatus.COMPLETED, SyncKeys.AREA_OF_INTEREST, WATERMARK));

        verify(repository, never()).markParentsOfChangedAreasOfInterest(
                anyString(), anyString(), anyString(), anyString(), eq(WATERMARK));
        verify(repository, never()).markLevel3TerritoriesAndParents(
                anyCollection(), anyString(), anyString());
    }

    private GeoFileRegenerationFlagListener listener() {
        AreaOfInterestConfig aoiConfig = mock(AreaOfInterestConfig.class);
        when(aoiConfig.getTargetTable()).thenReturn("dsp.area_of_interest");
        return new GeoFileRegenerationFlagListener(
                repository,
                tableConfig("dsp.territory_level_2"),
                tableConfig("dsp.territory_level_3"),
                provider(aoiConfig));
    }

    private static AdministrativeUnitTableProperties tableConfig(String targetTable) {
        AdministrativeUnitTableProperties properties = new AdministrativeUnitTableProperties();
        properties.setTargetTable(targetTable);
        return properties;
    }

    private static JobExecution execution(BatchStatus status, String syncKey, Instant previousWatermark) {
        JobExecution execution = new JobExecution(1L);
        execution.setJobInstance(new JobInstance(1L, "adminUnitLevel2GeoserverJob"));
        execution.setStatus(status);
        if (syncKey != null) {
            execution.getExecutionContext().putString(WatermarkContextKeys.SYNC_KEY, syncKey);
        }
        if (previousWatermark != null) {
            execution.getExecutionContext()
                    .putString(WatermarkContextKeys.PREVIOUS_WATERMARK, previousWatermark.toString());
        }
        return execution;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AreaOfInterestConfig> provider(AreaOfInterestConfig config) {
        ObjectProvider<AreaOfInterestConfig> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(config);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AreaOfInterestConfig> emptyProvider() {
        ObjectProvider<AreaOfInterestConfig> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
