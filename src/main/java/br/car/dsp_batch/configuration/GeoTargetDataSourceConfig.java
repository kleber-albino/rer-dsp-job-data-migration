package br.car.dsp_batch.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Geo-target DataSource — exhibition database with full geometries for GeoServer.
 */
@Configuration
public class GeoTargetDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.geo-target")
    public DataSourceProperties geoTargetDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.geo-target.hikari")
    public HikariConfig geoTargetHikariConfig() {
        HikariConfig config = new HikariConfig();
        DataSourceProperties properties = geoTargetDataSourceProperties();
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName(properties.getDriverClassName());
        // Allow pool startup even if the DB is down; unified connectivity check fails later.
        config.setInitializationFailTimeout(-1);
        // Defense in depth: session TZ must not reinterpret TIMESTAMP values.
        config.setConnectionInitSql("SET TIME ZONE 'UTC'");
        return config;
    }

    @Bean(name = "geoTargetDataSource")
    public DataSource geoTargetDataSource() {
        return new HikariDataSource(geoTargetHikariConfig());
    }

    @Bean(name = "geoTargetTransactionManager")
    public PlatformTransactionManager geoTargetTransactionManager(
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource) {
        return new DataSourceTransactionManager(geoTargetDataSource);
    }

    @Bean(name = "geoTargetJdbcTemplate")
    public JdbcTemplate geoTargetJdbcTemplate(
            @Qualifier("geoTargetDataSource") DataSource geoTargetDataSource) {
        return new JdbcTemplate(geoTargetDataSource);
    }
}
