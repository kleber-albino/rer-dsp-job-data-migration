package br.car.dsp_batch.layer.tasklet;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.ddl.LayerTableDdlBuilder;
import br.car.dsp_batch.layer.introspection.SchemaIntrospectionService;
import br.car.dsp_batch.layer.metadata.LayerMetadataRegistry;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Validates the source table, introspects its structure and creates the geo-target schema.
 */
@Slf4j
public class LayerTableSetupTasklet implements Tasklet {

    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate geoTargetJdbc;
    private final SchemaIntrospectionService introspectionService;
    private final LayerTableDdlBuilder ddlBuilder;
    private final LayerMetadataRegistry registry;
    private final LayerConfig config;
    private final String jobName;

    public LayerTableSetupTasklet(JdbcTemplate sourceJdbc,
                                      JdbcTemplate geoTargetJdbc,
                                      SchemaIntrospectionService introspectionService,
                                      LayerTableDdlBuilder ddlBuilder,
                                      LayerMetadataRegistry registry,
                                      LayerConfig config,
                                      String jobName) {
        this.sourceJdbc = sourceJdbc;
        this.geoTargetJdbc = geoTargetJdbc;
        this.introspectionService = introspectionService;
        this.ddlBuilder = ddlBuilder;
        this.registry = registry;
        this.config = config;
        this.jobName = jobName;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        log.info("Setting up layer {} ({})", config.resolveLayerName(), config.getSourceTable());

        LayerTableMetadata metadata = introspectionService.introspect(sourceJdbc, config);
        QualifiedTable target = metadata.targetTable();

        geoTargetJdbc.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(target.schema()));
        for (String statement : ddlBuilder.buildStatements(metadata)) {
            log.debug("Executing DDL: {}", statement);
            geoTargetJdbc.execute(statement);
        }

        registry.put(config.resolveKey(), metadata);

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();
        jobContext.putString("layerKey", config.resolveKey());
        jobContext.putString("layerJobName", jobName);
        jobContext.putString("sourceTable", metadata.qualifiedSourceTable());
        jobContext.putString("syncKey", config.resolveKey());

        log.info("Target table ready: {}", metadata.qualifiedTargetTable());
        return RepeatStatus.FINISHED;
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
