package br.car.dsp_batch.aoi.writer;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.aoi.service.AreaOfInterestPersistenceService;
import br.car.dsp_batch.layer.dto.LayerFeatureRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;

/**
 * Writer that persists AOI chunks via dual-write UPSERT.
 */
@Slf4j
public class AreaOfInterestWriter implements ItemWriter<LayerFeatureRecord> {

    private final AreaOfInterestPersistenceService persistenceService;
    private final AreaOfInterestTableMetadata metadata;

    public AreaOfInterestWriter(AreaOfInterestPersistenceService persistenceService,
                                AreaOfInterestTableMetadata metadata) {
        this.persistenceService = persistenceService;
        this.metadata = metadata;
    }

    @Override
    public void write(Chunk<? extends LayerFeatureRecord> chunk) {
        log.debug("Thread: {} - Writing AOI chunk of {} items for {}",
                Thread.currentThread().getName(),
                chunk.size(),
                metadata.qualifiedTargetTable());
        persistenceService.upsertAll(new ArrayList<>(chunk.getItems()), metadata);
    }
}
