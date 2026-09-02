package br.car.dsp_batch.batch.writer;

import br.car.dsp_batch.batch.config.JobTableConfig;
import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import br.car.dsp_batch.service.AdministrativeUnitPersistenceService;
import br.car.dsp_batch.temporal.TemporalColumnSpecs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;

/**
 * Writer that persists administrative unit chunks via upsert.
 */
@Slf4j
public class AdministrativeUnitGeoserverWriter implements ItemWriter<AdministrativeUnitDTO> {

    private final AdministrativeUnitPersistenceService persistenceService;
    private final JobTableConfig tableConfig;
    private final TemporalColumnSpecs temporalColumns;

    public AdministrativeUnitGeoserverWriter(AdministrativeUnitPersistenceService persistenceService,
                                             JobTableConfig tableConfig,
                                             TemporalColumnSpecs temporalColumns) {
        this.persistenceService = persistenceService;
        this.tableConfig = tableConfig;
        this.temporalColumns = temporalColumns;
    }

    @Override
    public void write(Chunk<? extends AdministrativeUnitDTO> chunk) {
        log.debug("Thread: {} - Writing chunk of {} items for table {}",
                Thread.currentThread().getName(),
                chunk.size(),
                tableConfig.getTargetTable());
        persistenceService.upsertAll(
                new ArrayList<>(chunk.getItems()), tableConfig, temporalColumns);
    }
}
