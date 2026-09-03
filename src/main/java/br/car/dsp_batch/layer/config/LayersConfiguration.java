package br.car.dsp_batch.layer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.InitializingBean;

/**
 * Enables layer configuration properties.
 */
@Configuration
@EnableConfigurationProperties(LayersProperties.class)
public class LayersConfiguration implements InitializingBean {

    private final LayersProperties properties;

    public LayersConfiguration(LayersProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        properties.validate();
    }
}
