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
 * Source DataSource — origin of geographic data used for updates.
 */
@Configuration
public class SourceDataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.source")
    public DataSourceProperties sourceDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.source.hikari")
    public HikariConfig sourceHikariConfig() {
        HikariConfig config = new HikariConfig();
        DataSourceProperties properties = sourceDataSourceProperties();
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

    @Bean(name = "sourceDataSource")
    public DataSource sourceDataSource() {
        return new HikariDataSource(sourceHikariConfig());
    }

    @Bean(name = "sourceTransactionManager")
    public PlatformTransactionManager sourceTransactionManager(
            @Qualifier("sourceDataSource") DataSource sourceDataSource) {
        return new DataSourceTransactionManager(sourceDataSource);
    }

    @Bean(name = "sourceJdbcTemplate")
    public JdbcTemplate sourceJdbcTemplate(@Qualifier("sourceDataSource") DataSource sourceDataSource) {
        return new JdbcTemplate(sourceDataSource);
    }
}
