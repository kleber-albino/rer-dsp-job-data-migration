package br.car.dsp_batch.layer.partitioner;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.Map;

/**
 * Wraps {@link br.car.dsp_batch.batch.partitioner.ColumnRangePartitioner}
 * and propagates the layer key to each partition.
 */
public class LayerPartitioner implements Partitioner {

    private final Partitioner delegate;
    private final String layerKey;

    public LayerPartitioner(Partitioner delegate, String layerKey) {
        this.delegate = delegate;
        this.layerKey = layerKey;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = delegate.partition(gridSize);
        for (ExecutionContext context : partitions.values()) {
            context.putString("layerKey", layerKey);
        }
        return partitions;
    }
}
