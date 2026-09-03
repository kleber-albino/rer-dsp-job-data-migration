package br.car.dsp_batch.temporal;

/**
 * Creation and optional update columns used for incremental sync and DSP timestamptz binding.
 */
public record TemporalColumnSpecs(
        WatermarkColumnSpec creationDateColumn,
        WatermarkColumnSpec updatedAtColumn
) {
    public TemporalColumnSpecs {
        if (creationDateColumn == null) {
            throw new IllegalArgumentException("creationDateColumn is required");
        }
    }

    public static TemporalColumnSpecs of(WatermarkColumnSpec creationDateColumn,
                                       WatermarkColumnSpec updatedAtColumn) {
        return new TemporalColumnSpecs(creationDateColumn, updatedAtColumn);
    }

    public WatermarkColumnSpec specForSourceColumn(String sourceColumn) {
        if (sourceColumn == null) {
            return null;
        }
        if (creationDateColumn.sourceColumn().equalsIgnoreCase(sourceColumn)) {
            return creationDateColumn;
        }
        if (updatedAtColumn != null
                && updatedAtColumn.sourceColumn().equalsIgnoreCase(sourceColumn)) {
            return updatedAtColumn;
        }
        return null;
    }

    public boolean hasUpdatedAtColumn() {
        return updatedAtColumn != null;
    }
}
