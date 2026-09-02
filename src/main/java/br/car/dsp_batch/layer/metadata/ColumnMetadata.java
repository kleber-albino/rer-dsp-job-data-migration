package br.car.dsp_batch.layer.metadata;

/**
 * Metadata for a column discovered on the source table.
 */
public record ColumnMetadata(
        String name,
        String udtName,
        Integer characterMaximumLength,
        Integer numericPrecision,
        Integer numericScale,
        boolean nullable,
        boolean geometry
) {
}
