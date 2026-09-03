package br.car.dsp_batch.layer.metadata;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of discovered metadata keyed by layer during job execution.
 */
@Component
public class LayerMetadataRegistry {

    private final Map<String, LayerTableMetadata> metadataByKey = new ConcurrentHashMap<>();

    public void put(String layerKey, LayerTableMetadata metadata) {
        metadataByKey.put(layerKey, metadata);
    }

    public LayerTableMetadata get(String layerKey) {
        return metadataByKey.get(layerKey);
    }

    public LayerTableMetadata getRequired(String layerKey) {
        LayerTableMetadata metadata = metadataByKey.get(layerKey);
        if (metadata == null) {
            throw new IllegalStateException(
                    "Metadata not found for layer '" + layerKey
                            + "'. Was the setup step executed?");
        }
        return metadata;
    }

    public void clear() {
        metadataByKey.clear();
    }
}
