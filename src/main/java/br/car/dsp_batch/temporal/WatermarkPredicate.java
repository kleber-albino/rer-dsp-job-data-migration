package br.car.dsp_batch.temporal;

import java.time.Instant;

/**
 * SQL fragment for watermark filtering on the source update column.
 */
public record WatermarkPredicate(String sqlFragment, Instant watermarkUtc) {

    public static WatermarkPredicate notNullOnly(String sqlFragment) {
        return new WatermarkPredicate(sqlFragment, null);
    }

    public static WatermarkPredicate withFilter(String sqlFragment, Instant watermarkUtc) {
        return new WatermarkPredicate(sqlFragment, watermarkUtc);
    }
}
