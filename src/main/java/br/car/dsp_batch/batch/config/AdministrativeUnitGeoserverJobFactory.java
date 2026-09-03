package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import br.car.dsp_batch.batch.listener.GeoCacheUpdateListener;
import br.car.dsp_batch.batch.listener.ParallelizationMonitorListener;
import br.car.dsp_batch.batch.partitioner.DeferredWatermarkPartitioner;
import br.car.dsp_batch.batch.processor.AdministrativeUnitGeoserverProcessor;
import br.car.dsp_batch.batch.reader.AdministrativeUnitGeoserverReader;
import br.car.dsp_batch.batch.tasklet.ChangeDetectionTasklet;
import br.car.dsp_batch.batch.writer.AdministrativeUnitGeoserverWriter;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.SyncWatermarkCommitListener;
import br.car.dsp_batch.sync.WatermarkChangeDetectionEngine;
import br.car.dsp_batch.temporal.BatchTemporalProperties;
import br.car.dsp_batch.temporal.TemporalSchemaSupport;
import br.car.dsp_batch.temporal.TemporalColumnSpecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Builds Spring Batch jobs/steps for admin unit and AOI Geoserver sync (watermark-only).
 */
@Component
public class AdministrativeUnitGeoserverJobFactory {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ParallelizationConfig parallelizationConfig;
    private final ParallelizationMonitorListener parallelizationMonitorListener;
    private final ChangeDecider changeDecider;
    private final GeoCacheUpdateListener geoCacheUpdateListener;
    private final AdministrativeUnitPersistenceService persistenceService;
    private final SyncStateRepository syncStateRepository;
    private final SyncWatermarkCommitListener syncWatermarkCommitListener;
    private final WatermarkChangeDetectionEngine changeDetectionEngine;
    private final TemporalSchemaSupport temporalSchemaSupport;
    private final BatchTemporalProperties batchTemporalProperties;

    public AdministrativeUnitGeoserverJobFactory(
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            ChangeDecider changeDecider,
            GeoCacheUpdateListener geoCacheUpdateListener,
            AdministrativeUnitPersistenceService persistenceService,
            SyncStateRepository syncStateRepository,
            SyncWatermarkCommitListener syncWatermarkCommitListener,
            WatermarkChangeDetectionEngine changeDetectionEngine,
            TemporalSchemaSupport temporalSchemaSupport,
            BatchTemporalProperties batchTemporalProperties) {
        this.parallelizationConfig = parallelizationConfig;
        this.parallelizationMonitorListener = parallelizationMonitorListener;
        this.changeDecider = changeDecider;
        this.geoCacheUpdateListener = geoCacheUpdateListener;
        this.persistenceService = persistenceService;
        this.syncStateRepository = syncStateRepository;
        this.syncWatermarkCommitListener = syncWatermarkCommitListener;
        this.changeDetectionEngine = changeDetectionEngine;
        this.temporalSchemaSupport = temporalSchemaSupport;
        this.batchTemporalProperties = batchTemporalProperties;
    }

    public Job buildJob(JobRepository jobRepository,
                        String jobName,
                        Step changeDetectionStep,
                        Step masterStep) {
        return new JobBuilder(jobName, jobRepository)
                .start(changeDetectionStep)
                .next(changeDecider)
                .on("PROCESS").to(masterStep)
                .from(changeDecider).on("SKIP").end()
                .from(changeDecider).on("*").end()
                .end()
                .listener(geoCacheUpdateListener)
                .listener(syncWatermarkCommitListener)
                .build();
    }

    public Step buildChangeDetectionStep(JobRepository jobRepository,
                                         PlatformTransactionManager transactionManager,
                                         DataSource sourceDataSource,
                                         DataSource targetDataSource,
                                         DataSource geoTargetDataSource,
                                         String stepName,
                                         JobTableConfig tableConfig) {
        return new StepBuilder(stepName, jobRepository)
                .tasklet(new ChangeDetectionTasklet(
                                new JdbcTemplate(sourceDataSource),
                                new JdbcTemplate(targetDataSource),
                                new JdbcTemplate(geoTargetDataSource),
                                tableConfig,
                                changeDetectionEngine,
                                temporalSchemaSupport,
                                batchTemporalProperties),
                        transactionManager)
                .build();
    }

    public Step buildMasterStep(JobRepository jobRepository,
                                String jobName,
                                String stepName,
                                Step workerStep,
                                Partitioner partitioner,
                                TaskExecutor taskExecutor) {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(jobName);

        if (!settings.isEnabled()) {
            logger.warn("{} - Partitioning disabled.", jobName);
            return workerStep;
        }

        logger.info("{} - Configuring master step with {} partitions.",
                jobName, settings.getThreadPoolSize());

        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(workerStep);
        partitionHandler.setTaskExecutor(taskExecutor);
        partitionHandler.setGridSize(settings.getThreadPoolSize());

        return new StepBuilder(stepName, jobRepository)
                .partitioner(workerStep.getName(), partitioner)
                .partitionHandler(partitionHandler)
                .build();
    }

    public Step buildWorkerStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                String jobName,
                                String stepName,
                                ItemReader<AdministrativeUnitDTO> reader,
                                ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
                                ItemWriter<AdministrativeUnitDTO> writer) {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(jobName);

        return new StepBuilder(stepName, jobRepository)
                .<AdministrativeUnitDTO, AdministrativeUnitDTO>chunk(settings.getChunkSize(), transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(parallelizationMonitorListener)
                .build();
    }

    public Partitioner buildPartitioner(DataSource sourceDataSource, JobTableConfig tableConfig) {
        return new DeferredWatermarkPartitioner(
                sourceDataSource,
                tableConfig,
                syncStateRepository,
                temporalSchemaSupport,
                batchTemporalProperties);
    }

    public AdministrativeUnitGeoserverReader buildReader(DataSource sourceDataSource,
                                                         String jobName,
                                                         String readerName,
                                                         JobTableConfig tableConfig,
                                                         Long minId,
                                                         Long maxId) {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(jobName);
        Instant watermark = syncStateRepository
                .findWatermark(tableConfig.getSyncKey())
                .orElse(null);
        TemporalColumnSpecs temporalColumns = resolveTemporalColumns(sourceDataSource, tableConfig);
        return new AdministrativeUnitGeoserverReader(
                sourceDataSource,
                minId,
                maxId,
                settings.getPageSize(),
                tableConfig,
                watermark,
                temporalColumns,
                readerName
        );
    }

    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> buildProcessor() {
        return new AdministrativeUnitGeoserverProcessor();
    }

    public ItemWriter<AdministrativeUnitDTO> buildWriter(DataSource sourceDataSource,
                                                         JobTableConfig tableConfig) {
        return new AdministrativeUnitGeoserverWriter(
                persistenceService,
                tableConfig,
                resolveTemporalColumns(sourceDataSource, tableConfig));
    }

    private TemporalColumnSpecs resolveTemporalColumns(DataSource sourceDataSource,
                                                       JobTableConfig tableConfig) {
        return temporalSchemaSupport.resolveTemporalColumns(
                new JdbcTemplate(sourceDataSource),
                tableConfig.getSourceTable(),
                tableConfig.getCreationDateColumn(),
                tableConfig.getUpdatedAtColumn(),
                batchTemporalProperties.resolvePolicy(
                        tableConfig.getSourceTimezone(),
                        "batch.*.source-timezone for " + tableConfig.getSourceTable()
                )
        );
    }
}
