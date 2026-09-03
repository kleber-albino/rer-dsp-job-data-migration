package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Registers admin unit (L1/L2/L3) Geoserver jobs using a shared factory.
 */
@Configuration
public class AdministrativeUnitGeoserverJobsConfig {

    public static final String ADMIN_UNIT_LEVEL_1_JOB = "adminUnitLevel1GeoserverJob";
    public static final String ADMIN_UNIT_LEVEL_2_JOB = "adminUnitLevel2GeoserverJob";
    public static final String ADMIN_UNIT_LEVEL_3_JOB = "adminUnitLevel3GeoserverJob";

    private static final String LEVEL_1_PREFIX = "adminUnitLevel1";
    private static final String LEVEL_2_PREFIX = "adminUnitLevel2";
    private static final String LEVEL_3_PREFIX = "adminUnitLevel3";

    private final AdministrativeUnitGeoserverJobFactory jobFactory;

    public AdministrativeUnitGeoserverJobsConfig(AdministrativeUnitGeoserverJobFactory jobFactory) {
        this.jobFactory = jobFactory;
    }

    @Bean(name = ADMIN_UNIT_LEVEL_1_JOB)
    public Job adminUnitLevel1GeoserverJob(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel1ChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("adminUnitLevel1GeoserverMasterStep") Step masterStep) {
        return jobFactory.buildJob(jobRepository, ADMIN_UNIT_LEVEL_1_JOB, changeDetectionStep, masterStep);
    }

    @Bean(name = "adminUnitLevel1ChangeDetectionStep")
    public Step adminUnitLevel1ChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource,
            @Qualifier("adminUnitLevel1TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildChangeDetectionStep(
                jobRepository, transactionManager, sourceDataSource, targetDataSource, geoTargetDataSource,
                LEVEL_1_PREFIX + "ChangeDetectionStep", tableConfig);
    }

    @Bean(name = "adminUnitLevel1GeoserverMasterStep")
    public Step adminUnitLevel1GeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel1GeoserverWorkerStep") Step workerStep,
            @Qualifier("adminUnitLevel1GeoserverPartitioner") Partitioner partitioner,
            @Qualifier("adminUnitLevel1GeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return jobFactory.buildMasterStep(
                jobRepository, ADMIN_UNIT_LEVEL_1_JOB, LEVEL_1_PREFIX + "GeoserverMasterStep",
                workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "adminUnitLevel1GeoserverWorkerStep")
    public Step adminUnitLevel1GeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("adminUnitLevel1GeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("adminUnitLevel1GeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("adminUnitLevel1GeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return jobFactory.buildWorkerStep(
                jobRepository, transactionManager, ADMIN_UNIT_LEVEL_1_JOB,
                LEVEL_1_PREFIX + "GeoserverWorkerStep", reader, processor, writer);
    }

    @Bean(name = "adminUnitLevel1GeoserverPartitioner")
    public Partitioner adminUnitLevel1GeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel1TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildPartitioner(sourceDataSource, tableConfig);
    }

    @Bean(name = "adminUnitLevel1GeoserverReader")
    @StepScope
    public ItemReader<AdministrativeUnitDTO> adminUnitLevel1GeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel1TableConfig") JobTableConfig tableConfig,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return jobFactory.buildReader(
                sourceDataSource, ADMIN_UNIT_LEVEL_1_JOB, LEVEL_1_PREFIX + "GeoserverReader",
                tableConfig, minId, maxId);
    }

    @Bean(name = "adminUnitLevel1GeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> adminUnitLevel1GeoserverProcessor() {
        return jobFactory.buildProcessor();
    }

    @Bean(name = "adminUnitLevel1GeoserverWriter")
    @StepScope
    public ItemWriter<AdministrativeUnitDTO> adminUnitLevel1GeoserverWriter(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel1TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildWriter(sourceDataSource, tableConfig);
    }

    @Bean(name = ADMIN_UNIT_LEVEL_2_JOB)
    public Job adminUnitLevel2GeoserverJob(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel2ChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("adminUnitLevel2GeoserverMasterStep") Step masterStep) {
        return jobFactory.buildJob(jobRepository, ADMIN_UNIT_LEVEL_2_JOB, changeDetectionStep, masterStep);
    }

    @Bean(name = "adminUnitLevel2ChangeDetectionStep")
    public Step adminUnitLevel2ChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource,
            @Qualifier("adminUnitLevel2TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildChangeDetectionStep(
                jobRepository, transactionManager, sourceDataSource, targetDataSource, geoTargetDataSource,
                LEVEL_2_PREFIX + "ChangeDetectionStep", tableConfig);
    }

    @Bean(name = "adminUnitLevel2GeoserverMasterStep")
    public Step adminUnitLevel2GeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel2GeoserverWorkerStep") Step workerStep,
            @Qualifier("adminUnitLevel2GeoserverPartitioner") Partitioner partitioner,
            @Qualifier("adminUnitLevel2GeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return jobFactory.buildMasterStep(
                jobRepository, ADMIN_UNIT_LEVEL_2_JOB, LEVEL_2_PREFIX + "GeoserverMasterStep",
                workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "adminUnitLevel2GeoserverWorkerStep")
    public Step adminUnitLevel2GeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("adminUnitLevel2GeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("adminUnitLevel2GeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("adminUnitLevel2GeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return jobFactory.buildWorkerStep(
                jobRepository, transactionManager, ADMIN_UNIT_LEVEL_2_JOB,
                LEVEL_2_PREFIX + "GeoserverWorkerStep", reader, processor, writer);
    }

    @Bean(name = "adminUnitLevel2GeoserverPartitioner")
    public Partitioner adminUnitLevel2GeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel2TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildPartitioner(sourceDataSource, tableConfig);
    }

    @Bean(name = "adminUnitLevel2GeoserverReader")
    @StepScope
    public ItemReader<AdministrativeUnitDTO> adminUnitLevel2GeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel2TableConfig") JobTableConfig tableConfig,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return jobFactory.buildReader(
                sourceDataSource, ADMIN_UNIT_LEVEL_2_JOB, LEVEL_2_PREFIX + "GeoserverReader",
                tableConfig, minId, maxId);
    }

    @Bean(name = "adminUnitLevel2GeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> adminUnitLevel2GeoserverProcessor() {
        return jobFactory.buildProcessor();
    }

    @Bean(name = "adminUnitLevel2GeoserverWriter")
    @StepScope
    public ItemWriter<AdministrativeUnitDTO> adminUnitLevel2GeoserverWriter(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel2TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildWriter(sourceDataSource, tableConfig);
    }

    @Bean(name = ADMIN_UNIT_LEVEL_3_JOB)
    public Job adminUnitLevel3GeoserverJob(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel3ChangeDetectionStep") Step changeDetectionStep,
            @Qualifier("adminUnitLevel3GeoserverMasterStep") Step masterStep) {
        return jobFactory.buildJob(jobRepository, ADMIN_UNIT_LEVEL_3_JOB, changeDetectionStep, masterStep);
    }

    @Bean(name = "adminUnitLevel3ChangeDetectionStep")
    public Step adminUnitLevel3ChangeDetectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("targetDataSource") DataSource targetDataSource,
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource,
            @Qualifier("adminUnitLevel3TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildChangeDetectionStep(
                jobRepository, transactionManager, sourceDataSource, targetDataSource, geoTargetDataSource,
                LEVEL_3_PREFIX + "ChangeDetectionStep", tableConfig);
    }

    @Bean(name = "adminUnitLevel3GeoserverMasterStep")
    public Step adminUnitLevel3GeoserverMasterStep(
            JobRepository jobRepository,
            @Qualifier("adminUnitLevel3GeoserverWorkerStep") Step workerStep,
            @Qualifier("adminUnitLevel3GeoserverPartitioner") Partitioner partitioner,
            @Qualifier("adminUnitLevel3GeoserverTaskExecutor") TaskExecutor taskExecutor) {
        return jobFactory.buildMasterStep(
                jobRepository, ADMIN_UNIT_LEVEL_3_JOB, LEVEL_3_PREFIX + "GeoserverMasterStep",
                workerStep, partitioner, taskExecutor);
    }

    @Bean(name = "adminUnitLevel3GeoserverWorkerStep")
    public Step adminUnitLevel3GeoserverWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            @Qualifier("adminUnitLevel3GeoserverReader") ItemReader<AdministrativeUnitDTO> reader,
            @Qualifier("adminUnitLevel3GeoserverProcessor")
            ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> processor,
            @Qualifier("adminUnitLevel3GeoserverWriter") ItemWriter<AdministrativeUnitDTO> writer) {
        return jobFactory.buildWorkerStep(
                jobRepository, transactionManager, ADMIN_UNIT_LEVEL_3_JOB,
                LEVEL_3_PREFIX + "GeoserverWorkerStep", reader, processor, writer);
    }

    @Bean(name = "adminUnitLevel3GeoserverPartitioner")
    public Partitioner adminUnitLevel3GeoserverPartitioner(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel3TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildPartitioner(sourceDataSource, tableConfig);
    }

    @Bean(name = "adminUnitLevel3GeoserverReader")
    @StepScope
    public ItemReader<AdministrativeUnitDTO> adminUnitLevel3GeoserverReader(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel3TableConfig") JobTableConfig tableConfig,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return jobFactory.buildReader(
                sourceDataSource, ADMIN_UNIT_LEVEL_3_JOB, LEVEL_3_PREFIX + "GeoserverReader",
                tableConfig, minId, maxId);
    }

    @Bean(name = "adminUnitLevel3GeoserverProcessor")
    public ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> adminUnitLevel3GeoserverProcessor() {
        return jobFactory.buildProcessor();
    }

    @Bean(name = "adminUnitLevel3GeoserverWriter")
    @StepScope
    public ItemWriter<AdministrativeUnitDTO> adminUnitLevel3GeoserverWriter(
            @Qualifier("sourceDataSource") DataSource sourceDataSource,
            @Qualifier("adminUnitLevel3TableConfig") JobTableConfig tableConfig) {
        return jobFactory.buildWriter(sourceDataSource, tableConfig);
    }
}
