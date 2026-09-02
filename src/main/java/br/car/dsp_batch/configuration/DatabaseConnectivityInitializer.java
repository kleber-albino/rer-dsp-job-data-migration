package br.car.dsp_batch.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Checks all 4 datasources before beans are created, logs every status, then fails only after reporting.
 * Runs as ApplicationContextInitializer so Spring Batch JobRepository does not connect first.
 */
public class DatabaseConnectivityInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectivityInitializer.class);
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment env = applicationContext.getEnvironment();

        log.info("Checking connectivity to the 4 datasources...");

        List<CheckResult> results = List.of(
                check("batch", env, "spring.datasource.batch"),
                check("source", env, "spring.datasource.source"),
                check("target", env, "spring.datasource.target"),
                check("geo-target", env, "spring.datasource.geo-target")
        );

        for (CheckResult result : results) {
            if (result.operational()) {
                log.info("Database status [{}] | url={} | OPERATIONAL", result.name(), result.url());
            } else {
                log.error("Database status [{}] | url={} | UNAVAILABLE | reason={}",
                        result.name(), result.url(), result.errorMessage());
            }
        }

        List<String> unavailable = new ArrayList<>();
        for (CheckResult result : results) {
            if (!result.operational()) {
                unavailable.add(result.name());
            }
        }

        if (!unavailable.isEmpty()) {
            throw new IllegalStateException(
                    "Connectivity failed for database(s): " + String.join(", ", unavailable)
                            + ". See status above and fix the connection before running the job.");
        }

        log.info("All 4 datasources are operational.");
    }

    private CheckResult check(String name, Environment env, String prefix) {
        String url = env.getProperty(prefix + ".url");
        String username = env.getProperty(prefix + ".username");
        String password = env.getProperty(prefix + ".password");
        String driverClassName = env.getProperty(prefix + ".driver-class-name");

        if (url == null || url.isBlank()) {
            return CheckResult.unavailable(name, url, "JDBC URL is not configured (" + prefix + ".url)");
        }

        try {
            if (driverClassName != null && !driverClassName.isBlank()) {
                Class.forName(driverClassName);
            }

            Properties props = new Properties();
            if (username != null) {
                props.setProperty("user", username);
            }
            if (password != null) {
                props.setProperty("password", password);
            }
            props.setProperty("connectTimeout", String.valueOf(LOGIN_TIMEOUT_SECONDS));
            props.setProperty("loginTimeout", String.valueOf(LOGIN_TIMEOUT_SECONDS));
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);

            try (Connection connection = DriverManager.getConnection(url, props);
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                if (!resultSet.next()) {
                    return CheckResult.unavailable(name, url, "SELECT 1 returned no rows");
                }
                return CheckResult.operational(name, url);
            }
        } catch (Exception ex) {
            return CheckResult.unavailable(name, url, ex.getMessage());
        }
    }

    private record CheckResult(String name, String url, boolean operational, String errorMessage) {

        static CheckResult operational(String name, String url) {
            return new CheckResult(name, url, true, null);
        }

        static CheckResult unavailable(String name, String url, String errorMessage) {
            return new CheckResult(name, url, false, errorMessage);
        }
    }
}
