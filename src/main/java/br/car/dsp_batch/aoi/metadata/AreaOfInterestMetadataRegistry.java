package br.car.dsp_batch.aoi.metadata;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of AOI metadata populated during the setup step.
 */
@Component
public class AreaOfInterestMetadataRegistry {

    private final Map<String, AreaOfInterestTableMetadata> metadataByKey = new ConcurrentHashMap<>();

    public void put(String syncKey, AreaOfInterestTableMetadata metadata) {
        metadataByKey.put(syncKey, metadata);
    }

    public AreaOfInterestTableMetadata get(String syncKey) {
        return metadataByKey.get(syncKey);
    }

    public AreaOfInterestTableMetadata getRequired(String syncKey) {
        AreaOfInterestTableMetadata metadata = metadataByKey.get(syncKey);
        if (metadata == null) {
            throw new IllegalStateException(
                    "AOI metadata not found for sync key '" + syncKey
                            + "'. Was the setup step executed?");
        }
        return metadata;
    }

    public void clear() {
        metadataByKey.clear();
    }
}
