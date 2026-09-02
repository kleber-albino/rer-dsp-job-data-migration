package br.car.dsp_batch.aoi.config;

import br.car.dsp_batch.aoi.job.AreaOfInterestJobFactory;
import org.springframework.batch.core.Job;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the AOI Geoserver migration job.
 */
@Configuration
public class AreaOfInterestJobsConfig {

    private final AreaOfInterestJobFactory jobFactory;

    public AreaOfInterestJobsConfig(AreaOfInterestJobFactory jobFactory) {
        this.jobFactory = jobFactory;
    }

    @Bean
    @ConfigurationProperties(prefix = "batch.area-of-interest")
    public AreaOfInterestConfig areaOfInterestConfig() {
        return new AreaOfInterestConfig();
    }

    @Bean(name = AreaOfInterestJobFactory.JOB_NAME)
    public Job areaOfInterestGeoserverJob(AreaOfInterestConfig areaOfInterestConfig) {
        areaOfInterestConfig.validate();
        return jobFactory.createJob(areaOfInterestConfig);
    }
}
