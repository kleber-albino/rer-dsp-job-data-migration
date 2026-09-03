package br.car.dsp_batch.batch.partitioner;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.WatermarkPartitionSupport;
import br.car.dsp_batch.temporal.BatchTemporalProperties;
import br.car.dsp_batch.temporal.TemporalSchemaSupport;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Resolves the partition WHERE clause at execution time using the persisted watermark.
 */
public class DeferredWatermarkPartitioner implements Partitioner {

    private final DataSource sourceDataSource;
    private final JobTableConfig tableConfig;
    private final SyncStateRepository syncStateRepository;
    private final TemporalSchemaSupport temporalSchemaSupport;
    private final BatchTemporalProperties batchTemporalProperties;

    public DeferredWatermarkPartitioner(DataSource sourceDataSource,
                                        JobTableConfig tableConfig,
                                        SyncStateRepository syncStateRepository,
                                        TemporalSchemaSupport temporalSchemaSupport,
                                        BatchTemporalProperties batchTemporalProperties) {
        this.sourceDataSource = sourceDataSource;
        this.tableConfig = tableConfig;
        this.syncStateRepository = syncStateRepository;
        this.temporalSchemaSupport = temporalSchemaSupport;
        this.batchTemporalProperties = batchTemporalProperties;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        var watermark = syncStateRepository.findWatermark(tableConfig.getSyncKey()).orElse(null);
        var temporalColumns = temporalSchemaSupport.resolveTemporalColumns(
                new JdbcTemplate(sourceDataSource),
                tableConfig.getSourceTable(),
                tableConfig.getCreationDateColumn(),
                tableConfig.getUpdatedAtColumn(),
                batchTemporalProperties.resolvePolicy(
                        tableConfig.getSourceTimezone(),
                        "batch.*.source-timezone for " + tableConfig.getSourceTable()
                )
        );
        String whereClause = WatermarkPartitionSupport.resolveWhereClause(
                tableConfig.getWhereClause(),
                temporalColumns.creationDateColumn(),
                temporalColumns.updatedAtColumn(),
                watermark
        );
        Partitioner delegate = WatermarkPartitionSupport.columnRangePartitioner(
                sourceDataSource,
                tableConfig.getSourceTable(),
                tableConfig.getPartitionColumn(),
                whereClause
        );
        return delegate.partition(gridSize);
    }
}
