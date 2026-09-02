package br.car.dsp_batch.batch.config;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * Parallelization settings keyed by job name.
 */
@Configuration
@EnableConfigurationProperties(ParallelizationConfig.ParallelizationProperties.class)
public class ParallelizationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ParallelizationConfig.class);

    private final ParallelizationProperties properties;

    public ParallelizationConfig(ParallelizationProperties properties) {
        this.properties = properties;
    }

    public ParallelizationSettings getJobSettings(String jobName) {
        ParallelizationSettings settings = properties.getJobs().get(jobName);
        if (settings == null) {
            logger.warn("No parallelization settings found for job '{}'. Using defaults.", jobName);
            return new ParallelizationSettings();
        }
        return settings;
    }

    @Bean(name = "adminUnitLevel1GeoserverTaskExecutor")
    public TaskExecutor adminUnitLevel1GeoserverTaskExecutor() {
        return createTaskExecutor("adminUnitLevel1GeoserverJob");
    }

    @Bean(name = "adminUnitLevel2GeoserverTaskExecutor")
    public TaskExecutor adminUnitLevel2GeoserverTaskExecutor() {
        return createTaskExecutor("adminUnitLevel2GeoserverJob");
    }

    @Bean(name = "adminUnitLevel3GeoserverTaskExecutor")
    public TaskExecutor adminUnitLevel3GeoserverTaskExecutor() {
        return createTaskExecutor("adminUnitLevel3GeoserverJob");
    }

    @Bean(name = "areaOfInterestGeoserverTaskExecutor")
    public TaskExecutor areaOfInterestGeoserverTaskExecutor() {
        return createTaskExecutor("areaOfInterestGeoserverJob");
    }

    private TaskExecutor createTaskExecutor(String jobName) {
        ParallelizationSettings settings = getJobSettings(jobName);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int poolSize = settings.isEnabled() ? settings.getThreadPoolSize() : 1;
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(settings.getQueueCapacity());
        executor.setThreadNamePrefix(jobName + "-");
        executor.initialize();
        return executor;
    }

    @Getter
    @Setter
    @ConfigurationProperties(prefix = "parallelization")
    public static class ParallelizationProperties {
        private Map<String, ParallelizationSettings> jobs = new HashMap<>();
    }

    @Getter
    @Setter
    public static class ParallelizationSettings {
        private boolean enabled = true;
        private int threadPoolSize = 1;
        private int chunkSize = 100;
        private int pageSize = 1000;
        private int queueCapacity = 100;
    }
}
