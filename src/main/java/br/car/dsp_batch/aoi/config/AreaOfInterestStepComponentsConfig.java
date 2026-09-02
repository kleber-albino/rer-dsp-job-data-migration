package br.car.dsp_batch.aoi.config;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestMetadataRegistry;
import br.car.dsp_batch.aoi.reader.AreaOfInterestReader;
import br.car.dsp_batch.aoi.service.AreaOfInterestPersistenceService;
import br.car.dsp_batch.aoi.writer.AreaOfInterestWriter;
import br.car.dsp_batch.batch.config.ParallelizationConfig;
import br.car.dsp_batch.sync.SyncStateRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Step-scoped beans used by the AOI migration job.
 */
@Configuration
public class AreaOfInterestStepComponentsConfig {

    @Bean
    @StepScope
    public AreaOfInterestReader areaOfInterestReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Value("#{jobExecutionContext['aoiSyncKey']}") String syncKey,
            @Value("#{jobExecutionContext['aoiJobName']}") String jobName,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId,
            AreaOfInterestMetadataRegistry registry,
            ParallelizationConfig parallelizationConfig,
            SyncStateRepository syncStateRepository) {
        var metadata = registry.getRequired(syncKey);
        int pageSize = parallelizationConfig.getJobSettings(jobName).getPageSize();
        Instant watermark = syncStateRepository.findWatermark(syncKey).orElse(null);
        return new AreaOfInterestReader(
                sourceDataSource,
                minId,
                maxId,
                pageSize,
                metadata,
                watermark,
                "areaOfInterestReader"
        );
    }

    @Bean
    @StepScope
    public AreaOfInterestWriter areaOfInterestWriter(
            AreaOfInterestPersistenceService persistenceService,
            AreaOfInterestMetadataRegistry registry,
            @Value("#{jobExecutionContext['aoiSyncKey']}") String syncKey) {
        return new AreaOfInterestWriter(persistenceService, registry.getRequired(syncKey));
    }
}
