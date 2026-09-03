package br.car.dsp_batch.aoi.partitioner;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestMetadataRegistry;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.WatermarkPartitionSupport;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Creates range partitions after AOI setup, applying the same watermark filter as the reader.
 */
public class DeferredAreaOfInterestPartitioner implements Partitioner {

    private final AreaOfInterestMetadataRegistry registry;
    private final String syncKey;
    private final DataSource sourceDataSource;
    private final SyncStateRepository syncStateRepository;

    public DeferredAreaOfInterestPartitioner(AreaOfInterestMetadataRegistry registry,
                                             String syncKey,
                                             DataSource sourceDataSource,
                                             SyncStateRepository syncStateRepository) {
        this.registry = registry;
        this.syncKey = syncKey;
        this.sourceDataSource = sourceDataSource;
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        AreaOfInterestTableMetadata metadata = registry.getRequired(syncKey);
        var watermark = syncStateRepository.findWatermark(syncKey).orElse(null);
        String whereClause = WatermarkPartitionSupport.resolveWhereClause(
                metadata.whereClause(),
                metadata.creationDateColumn(),
                metadata.updatedAtColumn(),
                watermark
        );
        Partitioner delegate = WatermarkPartitionSupport.columnRangePartitioner(
                sourceDataSource,
                metadata.qualifiedSourceTable(),
                metadata.primaryKeyColumn(),
                whereClause
        );
        return new AreaOfInterestPartitioner(delegate, syncKey).partition(gridSize);
    }

    private static final class AreaOfInterestPartitioner implements Partitioner {

        private final Partitioner delegate;
        private final String syncKey;

        private AreaOfInterestPartitioner(Partitioner delegate, String syncKey) {
            this.delegate = delegate;
            this.syncKey = syncKey;
        }

        @Override
        public Map<String, ExecutionContext> partition(int gridSize) {
            Map<String, ExecutionContext> partitions = delegate.partition(gridSize);
            for (ExecutionContext context : partitions.values()) {
                context.putString("aoiSyncKey", syncKey);
            }
            return partitions;
        }
    }
}
