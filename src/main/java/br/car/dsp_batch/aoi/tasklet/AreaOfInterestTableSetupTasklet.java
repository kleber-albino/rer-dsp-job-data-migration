package br.car.dsp_batch.aoi.tasklet;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.aoi.ddl.AreaOfInterestTableDdlBuilder;
import br.car.dsp_batch.aoi.introspection.AreaOfInterestIntrospectionService;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestMetadataRegistry;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.sync.WatermarkContextKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Introspects the AOI source table and creates target schemas on business + geo databases.
 */
@Slf4j
public class AreaOfInterestTableSetupTasklet implements Tasklet {

    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final JdbcTemplate geoTargetJdbc;
    private final AreaOfInterestIntrospectionService introspectionService;
    private final AreaOfInterestTableDdlBuilder ddlBuilder;
    private final AreaOfInterestMetadataRegistry registry;
    private final AreaOfInterestConfig config;
    private final String jobName;

    public AreaOfInterestTableSetupTasklet(JdbcTemplate sourceJdbc,
                                           JdbcTemplate targetJdbc,
                                           JdbcTemplate geoTargetJdbc,
                                           AreaOfInterestIntrospectionService introspectionService,
                                           AreaOfInterestTableDdlBuilder ddlBuilder,
                                           AreaOfInterestMetadataRegistry registry,
                                           AreaOfInterestConfig config,
                                           String jobName) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
        this.geoTargetJdbc = geoTargetJdbc;
        this.introspectionService = introspectionService;
        this.ddlBuilder = ddlBuilder;
        this.registry = registry;
        this.config = config;
        this.jobName = jobName;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Setting up AOI {} ({})", config.resolveLayerName(), config.getSourceTable());

        AreaOfInterestTableMetadata metadata = introspectionService.introspect(sourceJdbc, config);
        QualifiedTable target = metadata.targetTable();

        targetJdbc.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(target.schema()));
        geoTargetJdbc.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(target.schema()));

        for (String statement : ddlBuilder.buildBusinessTargetStatements(metadata)) {
            log.debug("Executing business DDL: {}", statement);
            targetJdbc.execute(statement);
        }
        for (String statement : ddlBuilder.buildGeoTargetStatements(metadata)) {
            log.debug("Executing geo-target DDL: {}", statement);
            geoTargetJdbc.execute(statement);
        }

        registry.put(metadata.syncKey(), metadata);

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();
        jobContext.putString("aoiSyncKey", metadata.syncKey());
        jobContext.putString("aoiJobName", jobName);
        jobContext.putString("sourceTable", metadata.qualifiedSourceTable());
        jobContext.putString(WatermarkContextKeys.LAYER_NAME, metadata.layerName());

        log.info("AOI target tables ready: {}", metadata.qualifiedTargetTable());
        return RepeatStatus.FINISHED;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
