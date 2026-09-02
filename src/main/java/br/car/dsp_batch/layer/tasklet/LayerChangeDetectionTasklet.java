package br.car.dsp_batch.layer.tasklet;

import br.car.dsp_batch.layer.metadata.LayerMetadataRegistry;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.service.LayerFeaturePersistenceService;
import br.car.dsp_batch.sync.WatermarkChangeDetectionEngine;
import br.car.dsp_batch.sync.WatermarkTableSpecs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Watermark change detection tasklet for layers (geo-target only).
 */
@Slf4j
public class LayerChangeDetectionTasklet implements Tasklet {

    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate geoTargetJdbc;
    private final WatermarkChangeDetectionEngine engine;
    private final LayerMetadataRegistry registry;
    private final String layerKey;

    public LayerChangeDetectionTasklet(JdbcTemplate sourceJdbc,
                                     JdbcTemplate geoTargetJdbc,
                                     WatermarkChangeDetectionEngine engine,
                                     LayerMetadataRegistry registry,
                                     String layerKey) {
        this.sourceJdbc = sourceJdbc;
        this.geoTargetJdbc = geoTargetJdbc;
        this.engine = engine;
        this.registry = registry;
        this.layerKey = layerKey;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LayerTableMetadata metadata = registry.getRequired(layerKey);
        log.info("Executing watermark change detection for layer={} table={}",
                metadata.layerName(), metadata.qualifiedSourceTable());
        LayerFeaturePersistenceService.requirePositiveSrid(metadata);
        engine.detectChanges(
                sourceJdbc,
                geoTargetJdbc,
                null,
                WatermarkTableSpecs.fromLayerMetadata(metadata),
                chunkContext
        );
        return RepeatStatus.FINISHED;
    }
}
