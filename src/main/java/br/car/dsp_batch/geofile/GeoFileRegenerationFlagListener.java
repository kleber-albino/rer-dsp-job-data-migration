package br.car.dsp_batch.geofile;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.batch.config.table.AdministrativeUnitTableProperties;
import br.car.dsp_batch.sync.DepartedLevel3ContextSupport;
import br.car.dsp_batch.sync.SyncKeys;
import br.car.dsp_batch.sync.WatermarkContextKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

/**
 * Marks the territories a run changed as needing their download files regenerated.
 *
 * <p>Only on {@link BatchStatus#COMPLETED}: a failed run may have written part of the data,
 * and flagging then would publish files from a half-migrated base. Leaving the flag as it was
 * costs one extra migration cycle, which the next successful run closes anyway.
 */
@Slf4j
@Component
public class GeoFileRegenerationFlagListener implements JobExecutionListener {

    private final GeoFileRegenerationFlagRepository flagRepository;
    private final AdministrativeUnitTableProperties level2TableConfig;
    private final AdministrativeUnitTableProperties level3TableConfig;
    /**
     * Lazy: the AOI config bean is declared by the same class that builds the AOI job, which
     * needs this listener. Resolving it at afterJob time keeps the wiring acyclic.
     */
    private final ObjectProvider<AreaOfInterestConfig> areaOfInterestConfig;

    public GeoFileRegenerationFlagListener(
            GeoFileRegenerationFlagRepository flagRepository,
            @Qualifier("adminUnitLevel2TableConfig") AdministrativeUnitTableProperties level2TableConfig,
            @Qualifier("adminUnitLevel3TableConfig") AdministrativeUnitTableProperties level3TableConfig,
            ObjectProvider<AreaOfInterestConfig> areaOfInterestConfig) {
        this.flagRepository = flagRepository;
        this.level2TableConfig = level2TableConfig;
        this.level3TableConfig = level3TableConfig;
        this.areaOfInterestConfig = areaOfInterestConfig;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            log.warn(
                    "Job {} finished with status={} — requires_s3_file_regeneration left untouched",
                    jobName,
                    jobExecution.getStatus()
            );
            return;
        }

        var context = jobExecution.getExecutionContext();
        String syncKey = context.getString(WatermarkContextKeys.SYNC_KEY, null);
        if (syncKey == null || syncKey.isBlank()) {
            return;
        }

        Instant previousWatermark = readPreviousWatermark(jobExecution);
        switch (syncKey) {
            case SyncKeys.ADMIN_UNIT_LEVEL_2 -> markTerritoryLevel(
                    syncKey, level2TableConfig.getTargetTable(), previousWatermark);
            case SyncKeys.ADMIN_UNIT_LEVEL_3 -> markTerritoryLevel(
                    syncKey, level3TableConfig.getTargetTable(), previousWatermark);
            case SyncKeys.AREA_OF_INTEREST -> markAreaOfInterestParents(jobExecution, syncKey, previousWatermark);
            default -> log.debug(
                    "syncKey={} does not feed territorial downloads — nothing to flag", syncKey);
        }
    }

    private void markTerritoryLevel(String syncKey, String territoryTable, Instant previousWatermark) {
        int flagged = flagRepository.markChangedTerritories(territoryTable, previousWatermark);
        log.info(
                "Flagged {} row(s) of {} for geo file regeneration (syncKey={}, since={})",
                flagged, territoryTable, syncKey, previousWatermark
        );
    }

    private void markAreaOfInterestParents(JobExecution jobExecution,
                                         String syncKey,
                                         Instant previousWatermark) {
        AreaOfInterestConfig config = areaOfInterestConfig.getIfAvailable();
        if (config == null) {
            log.warn("AOI configuration unavailable — skipping geo file regeneration flags");
            return;
        }

        var result = flagRepository.markParentsOfChangedAreasOfInterest(
                config.getTargetTable(),
                AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN,
                level3TableConfig.getTargetTable(),
                level2TableConfig.getTargetTable(),
                previousWatermark
        );

        Set<String> departedLevel3Ids = DepartedLevel3ContextSupport.read(jobExecution.getExecutionContext());
        var departedResult = flagRepository.markLevel3TerritoriesAndParents(
                departedLevel3Ids,
                level3TableConfig.getTargetTable(),
                level2TableConfig.getTargetTable()
        );

        log.info(
                "Flagged {} level 2 and {} level 3 row(s) for geo file regeneration "
                        + "(syncKey={}, since={})",
                result.level2Flagged(), result.level3Flagged(), syncKey, previousWatermark
        );
        if (!departedLevel3Ids.isEmpty()) {
            log.info(
                    "Flagged {} departed level 2 and {} departed level 3 row(s) "
                            + "that lost AOIs (syncKey={}, departedLevel3Count={})",
                    departedResult.level2Flagged(),
                    departedResult.level3Flagged(),
                    syncKey,
                    departedLevel3Ids.size()
            );
        }
    }

    private static Instant readPreviousWatermark(JobExecution jobExecution) {
        String value = jobExecution.getExecutionContext()
                .getString(WatermarkContextKeys.PREVIOUS_WATERMARK, null);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
