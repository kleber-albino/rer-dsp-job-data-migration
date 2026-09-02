package br.car.dsp_batch.layer.config;

import br.car.dsp_batch.batch.config.ParallelizationConfig;
import br.car.dsp_batch.layer.metadata.LayerMetadataRegistry;
import br.car.dsp_batch.layer.reader.LayerFeatureReader;
import br.car.dsp_batch.layer.service.LayerFeaturePersistenceService;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.layer.writer.LayerFeatureWriter;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Shared {@link StepScope} beans used by all layer jobs.
 */
@Configuration
public class LayerStepComponentsConfig {

    @Bean
    @StepScope
    public LayerFeatureReader layerFeatureReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{jobExecutionContext['layerKey']}") String layerKey,
            @Value("#{jobExecutionContext['layerJobName']}") String layerJobName,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId,
            LayerMetadataRegistry registry,
            ParallelizationConfig parallelizationConfig,
            SyncStateRepository syncStateRepository) {
        var metadata = registry.getRequired(layerKey);
        int pageSize = parallelizationConfig.getJobSettings(layerJobName).getPageSize();
        Instant watermark = syncStateRepository.findWatermark(layerKey).orElse(null);
        return new LayerFeatureReader(
                sourceDataSource,
                minId,
                maxId,
                pageSize,
                metadata,
                watermark,
                "layerFeatureReader_" + layerKey
        );
    }

    @Bean
    @StepScope
    public LayerFeatureWriter layerFeatureWriter(
            LayerFeaturePersistenceService persistenceService,
            LayerMetadataRegistry registry,
            @Value("#{jobExecutionContext['layerKey']}") String layerKey) {
        return new LayerFeatureWriter(persistenceService, registry.getRequired(layerKey));
    }
}
