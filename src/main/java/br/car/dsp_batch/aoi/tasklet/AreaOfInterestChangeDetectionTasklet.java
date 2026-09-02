package br.car.dsp_batch.aoi.tasklet;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestMetadataRegistry;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.aoi.service.AreaOfInterestPersistenceService;
import br.car.dsp_batch.sync.WatermarkChangeDetectionEngine;
import br.car.dsp_batch.sync.WatermarkTableSpecs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Watermark change detection for AOI (business + geo-target).
 */
@Slf4j
public class AreaOfInterestChangeDetectionTasklet implements Tasklet {

    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final JdbcTemplate geoTargetJdbc;
    private final WatermarkChangeDetectionEngine engine;
    private final AreaOfInterestMetadataRegistry registry;
    private final String syncKey;

    public AreaOfInterestChangeDetectionTasklet(JdbcTemplate sourceJdbc,
                                                JdbcTemplate targetJdbc,
                                                JdbcTemplate geoTargetJdbc,
                                                WatermarkChangeDetectionEngine engine,
                                                AreaOfInterestMetadataRegistry registry,
                                                String syncKey) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
        this.geoTargetJdbc = geoTargetJdbc;
        this.engine = engine;
        this.registry = registry;
        this.syncKey = syncKey;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        AreaOfInterestTableMetadata metadata = registry.getRequired(syncKey);
        log.info("Executing watermark change detection for AOI={} table={}",
                metadata.layerName(), metadata.qualifiedSourceTable());
        AreaOfInterestPersistenceService.requirePositiveSrid(metadata);
        engine.detectChanges(
                sourceJdbc,
                geoTargetJdbc,
                targetJdbc,
                WatermarkTableSpecs.fromAreaOfInterestMetadata(metadata),
                chunkContext
        );
        return RepeatStatus.FINISHED;
    }
}
