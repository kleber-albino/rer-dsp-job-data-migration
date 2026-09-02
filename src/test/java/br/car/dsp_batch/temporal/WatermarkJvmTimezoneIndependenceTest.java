package br.car.dsp_batch.temporal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ensures watermark conversion does not depend on the JVM default timezone.
 */
class WatermarkJvmTimezoneIndependenceTest {

    @Test
    void timestampConversion_SameUnderUtcAndSaoPauloJvmDefaults() {
        LocalDateTime wall = LocalDateTime.of(2026, 3, 15, 10, 30, 0);
        WatermarkColumnSpec spec = WatermarkColumnSpec.of(
                "source_updated_at",
                TemporalType.TIMESTAMP,
                SourceTemporalPolicy.of(java.time.ZoneId.of("America/Sao_Paulo"), "test"));

        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Instant utcJvm = WatermarkTemporalBridge.toInstant(wall, spec);
            String filterUtc = WatermarkTemporalBridge.buildPredicate(spec, utcJvm).sqlFragment();

            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
            Instant spJvm = WatermarkTemporalBridge.toInstant(wall, spec);
            String filterSp = WatermarkTemporalBridge.buildPredicate(spec, spJvm).sqlFragment();

            assertEquals(utcJvm, spJvm);
            assertEquals(filterUtc, filterSp);
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
