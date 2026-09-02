package br.car.dsp_batch.layer.partitioner;

import br.car.dsp_batch.layer.metadata.LayerMetadataRegistry;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.sync.SyncStateRepository;
import br.car.dsp_batch.sync.WatermarkPartitionSupport;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Creates the range partitioner at execution time after layer setup,
 * applying the same watermark filter used by the reader.
 */
public class DeferredLayerPartitioner implements Partitioner {

    private final LayerMetadataRegistry registry;
    private final String layerKey;
    private final DataSource sourceDataSource;
    private final SyncStateRepository syncStateRepository;

    public DeferredLayerPartitioner(LayerMetadataRegistry registry,
                                    String layerKey,
                                    DataSource sourceDataSource,
                                    SyncStateRepository syncStateRepository) {
        this.registry = registry;
        this.layerKey = layerKey;
        this.sourceDataSource = sourceDataSource;
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        LayerTableMetadata metadata = registry.getRequired(layerKey);
        var watermark = syncStateRepository.findWatermark(layerKey).orElse(null);
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
        return new LayerPartitioner(delegate, layerKey).partition(gridSize);
    }
}
