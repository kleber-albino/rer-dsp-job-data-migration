package br.car.dsp_batch.layer.metadata;

import java.util.List;

/**
 * Metadata for an index discovered on the source table.
 */
public record IndexMetadata(
        String name,
        List<String> columns,
        String method,
        boolean unique
) {
}
