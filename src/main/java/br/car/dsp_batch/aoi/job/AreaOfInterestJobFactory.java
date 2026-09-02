package br.car.dsp_batch.aoi.job;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.aoi.ddl.AreaOfInterestTableDdlBuilder;
import br.car.dsp_batch.aoi.introspection.AreaOfInterestIntrospectionService;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestMetadataRegistry;
import br.car.dsp_batch.aoi.partitioner.DeferredAreaOfInterestPartitioner;
import br.car.dsp_batch.aoi.tasklet.AreaOfInterestChangeDetectionTasklet;
import br.car.dsp_batch.aoi.tasklet.AreaOfInterestTableSetupTasklet;
import br.car.dsp_batch.batch.config.ChangeDecider;
import br.car.dsp_batch.batch.config.ParallelizationConfig;
import br.car.dsp_batch.batch.listener.GeoCacheUpdateListener;
import br.car.dsp_batch.batch.listener.ParallelizationMonitorListener;
import br.car.dsp_batch.layer.dto.LayerFeatureRecord;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.SyncWatermarkCommitListener;
import br.car.dsp_batch.sync.WatermarkChangeDetectionEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Builds the AOI Geoserver migration job (setup → change detection → partitioned sync).
 */
@Slf4j
@Component
public class AreaOfInterestJobFactory {

    public static final String JOB_NAME = "areaOfInterestGeoserverJob";
    private static final String STEP_PREFIX = "areaOfInterest";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource sourceDataSource;
    private final DataSource targetDataSource;
    private final DataSource geoTargetDataSource;
    private final AreaOfInterestIntrospectionService introspectionService;
    private final AreaOfInterestTableDdlBuilder ddlBuilder;
    private final AreaOfInterestMetadataRegistry registry;
    private final WatermarkChangeDetectionEngine changeDetectionEngine;
    private final SyncStateRepository syncStateRepository;
    private final SyncWatermarkCommitListener watermarkCommitListener;
    private final GeoCacheUpdateListener geoCacheUpdateListener;
    private final ChangeDecider changeDecider;
    private final ParallelizationConfig parallelizationConfig;
    private final ParallelizationMonitorListener parallelizationMonitorListener;
    private final ItemReader<LayerFeatureRecord> areaOfInterestReader;
    private final ItemWriter<LayerFeatureRecord> areaOfInterestWriter;

    public AreaOfInterestJobFactory(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource,
            AreaOfInterestIntrospectionService introspectionService,
            AreaOfInterestTableDdlBuilder ddlBuilder,
            AreaOfInterestMetadataRegistry registry,
            WatermarkChangeDetectionEngine changeDetectionEngine,
            SyncStateRepository syncStateRepository,
            SyncWatermarkCommitListener watermarkCommitListener,
            GeoCacheUpdateListener geoCacheUpdateListener,
            ChangeDecider changeDecider,
            ParallelizationConfig parallelizationConfig,
            ParallelizationMonitorListener parallelizationMonitorListener,
            @Qualifier("areaOfInterestReader") ItemReader<LayerFeatureRecord> areaOfInterestReader,
            @Qualifier("areaOfInterestWriter") ItemWriter<LayerFeatureRecord> areaOfInterestWriter) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.sourceDataSource = sourceDataSource;
        this.targetDataSource = targetDataSource;
        this.geoTargetDataSource = geoTargetDataSource;
        this.introspectionService = introspectionService;
        this.ddlBuilder = ddlBuilder;
        this.registry = registry;
        this.changeDetectionEngine = changeDetectionEngine;
        this.syncStateRepository = syncStateRepository;
        this.watermarkCommitListener = watermarkCommitListener;
        this.geoCacheUpdateListener = geoCacheUpdateListener;
        this.changeDecider = changeDecider;
        this.parallelizationConfig = parallelizationConfig;
        this.parallelizationMonitorListener = parallelizationMonitorListener;
        this.areaOfInterestReader = areaOfInterestReader;
        this.areaOfInterestWriter = areaOfInterestWriter;
    }

    public Job createJob(AreaOfInterestConfig config) {
        String syncKey = config.resolveSyncKey();

        Step setupStep = buildSetupStep(config);
        Step changeDetectionStep = buildChangeDetectionStep(syncKey);
        Step workerStep = buildWorkerStep();
        Step masterStep = buildMasterStep(workerStep, syncKey);

        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(geoCacheUpdateListener)
                .listener(watermarkCommitListener)
                .start(setupStep)
                .next(changeDetectionStep)
                .next(changeDecider)
                .on("PROCESS").to(masterStep)
                .from(changeDecider).on("SKIP").end()
                .from(changeDecider).on("*").end()
                .end()
                .build();
    }

    private Step buildSetupStep(AreaOfInterestConfig config) {
        return new StepBuilder(STEP_PREFIX + "SetupStep", jobRepository)
                .tasklet(new AreaOfInterestTableSetupTasklet(
                        new JdbcTemplate(sourceDataSource),
                        new JdbcTemplate(targetDataSource),
                        new JdbcTemplate(geoTargetDataSource),
                        introspectionService,
                        ddlBuilder,
                        registry,
                        config,
                        JOB_NAME
                ), transactionManager)
                .build();
    }

    private Step buildChangeDetectionStep(String syncKey) {
        return new StepBuilder(STEP_PREFIX + "ChangeDetectionStep", jobRepository)
                .tasklet(new AreaOfInterestChangeDetectionTasklet(
                        new JdbcTemplate(sourceDataSource),
                        new JdbcTemplate(targetDataSource),
                        new JdbcTemplate(geoTargetDataSource),
                        changeDetectionEngine,
                        registry,
                        syncKey
                ), transactionManager)
                .build();
    }

    private Step buildWorkerStep() {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(JOB_NAME);

        return new StepBuilder(STEP_PREFIX + "GeoserverWorkerStep", jobRepository)
                .<LayerFeatureRecord, LayerFeatureRecord>chunk(settings.getChunkSize(), transactionManager)
                .reader(areaOfInterestReader)
                .writer(areaOfInterestWriter)
                .listener(parallelizationMonitorListener)
                .build();
    }

    private Step buildMasterStep(Step workerStep, String syncKey) {
        ParallelizationConfig.ParallelizationSettings settings =
                parallelizationConfig.getJobSettings(JOB_NAME);

        if (!settings.isEnabled()) {
            log.warn("{} - Partitioning disabled.", JOB_NAME);
            return workerStep;
        }

        Partitioner partitioner = new DeferredAreaOfInterestPartitioner(
                registry, syncKey, sourceDataSource, syncStateRepository);
        TaskExecutor taskExecutor = createTaskExecutor(settings);

        TaskExecutorPartitionHandler partitionHandler = new TaskExecutorPartitionHandler();
        partitionHandler.setStep(workerStep);
        partitionHandler.setTaskExecutor(taskExecutor);
        partitionHandler.setGridSize(settings.getThreadPoolSize());

        log.info("{} - Master step with {} partitions.", JOB_NAME, settings.getThreadPoolSize());

        return new StepBuilder(STEP_PREFIX + "GeoserverMasterStep", jobRepository)
                .partitioner(workerStep.getName(), partitioner)
                .partitionHandler(partitionHandler)
                .build();
    }

    private TaskExecutor createTaskExecutor(ParallelizationConfig.ParallelizationSettings settings) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = settings.isEnabled() ? settings.getThreadPoolSize() : 1;
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(settings.getQueueCapacity());
        executor.setThreadNamePrefix(JOB_NAME + "-");
        executor.initialize();
        return executor;
    }
}
