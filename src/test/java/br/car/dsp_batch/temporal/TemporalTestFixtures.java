package br.car.dsp_batch.temporal;

/**
 * Shared watermark column fixtures for unit tests.
 */
public final class TemporalTestFixtures {

    private TemporalTestFixtures() {
    }

    public static WatermarkColumnSpec timestamptz(String column) {
        return WatermarkColumnSpec.of(column, TemporalType.TIMESTAMPTZ, SourceTemporalPolicy.none());
    }

    public static WatermarkColumnSpec timestamp(String column, String zone) {
        return WatermarkColumnSpec.of(
                column,
                TemporalType.TIMESTAMP,
                SourceTemporalPolicy.parse(zone, "test"));
    }
}
