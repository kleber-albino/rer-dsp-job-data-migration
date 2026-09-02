package br.car.dsp_batch.batch.processor;

import br.car.dsp_batch.batch.dto.AdministrativeUnitDTO;
import org.springframework.batch.item.ItemProcessor;

/**
 * Pass-through processor for administrative unit records.
 * Subclasses or future strategies can add transformations without changing the job wiring.
 */
public class AdministrativeUnitGeoserverProcessor
        implements ItemProcessor<AdministrativeUnitDTO, AdministrativeUnitDTO> {

    @Override
    public AdministrativeUnitDTO process(AdministrativeUnitDTO item) {
        return item;
    }
}
