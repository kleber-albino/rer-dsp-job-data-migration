package br.car.dsp_batch.sync;

import br.car.dsp_batch.temporal.WatermarkColumnSpec;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;

/**
 * Shared helpers for watermark-aware range partitioning.
 */
public final class WatermarkPartitionSupport {

    private WatermarkPartitionSupport() {
    }

    public static String resolveWhereClause(String configWhere,
                                            WatermarkColumnSpec creationDateColumn,
                                            WatermarkColumnSpec updatedAtColumn,
                                            Instant watermark) {
        return WatermarkSql.combineWhere(
                configWhere,
                WatermarkSql.buildChangeDetectionFilter(
                        creationDateColumn, updatedAtColumn, watermark)
        );
    }

    public static br.car.dsp_batch.batch.partitioner.ColumnRangePartitioner columnRangePartitioner(
            DataSource sourceDataSource,
            String sourceTable,
            String partitionColumn,
            String whereClause) {
        return new br.car.dsp_batch.batch.partitioner.ColumnRangePartitioner(
                sourceDataSource,
                sourceTable,
                partitionColumn,
                whereClause
        );
    }
}
