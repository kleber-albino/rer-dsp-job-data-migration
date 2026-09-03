package br.car.dsp_batch.layer.writer;

import br.car.dsp_batch.layer.dto.LayerFeatureRecord;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.service.LayerFeaturePersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;

/**
 * Writer that persists feature chunks via UPSERT on the geo-target.
 */
@Slf4j
public class LayerFeatureWriter implements ItemWriter<LayerFeatureRecord> {

    private final LayerFeaturePersistenceService persistenceService;
    private final LayerTableMetadata metadata;

    public LayerFeatureWriter(LayerFeaturePersistenceService persistenceService,
                              LayerTableMetadata metadata) {
        this.persistenceService = persistenceService;
        this.metadata = metadata;
    }

    @Override
    public void write(Chunk<? extends LayerFeatureRecord> chunk) {
        log.debug("Thread: {} - Writing chunk of {} items for {}",
                Thread.currentThread().getName(),
                chunk.size(),
                metadata.qualifiedTargetTable());
        persistenceService.upsertAll(new ArrayList<>(chunk.getItems()), metadata);
    }
}
