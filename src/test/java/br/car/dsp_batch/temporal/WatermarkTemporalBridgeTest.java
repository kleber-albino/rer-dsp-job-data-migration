package br.car.dsp_batch.temporal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatermarkTemporalBridgeTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void timestamptz_RoundTripToInstantAndDsp() {
        OffsetDateTime odt = OffsetDateTime.parse("2026-08-10T15:00:00.123456789-03:00");
        WatermarkColumnSpec spec = WatermarkColumnSpec.of(
                "updated_at", TemporalType.TIMESTAMPTZ, SourceTemporalPolicy.none());

        Instant instant = WatermarkTemporalBridge.toInstant(odt, spec);
        assertEquals(odt.toInstant().truncatedTo(java.time.temporal.ChronoUnit.MICROS), instant);

        OffsetDateTime dsp = WatermarkTemporalBridge.toDspTimestamptz(instant);
        assertEquals(ZoneOffset.UTC, dsp.getOffset());
        assertEquals(instant, dsp.toInstant());
    }

    @Test
    void timestamp_UsesConfiguredZoneNotJvmDefault() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDateTime wall = LocalDateTime.of(2026, 1, 15, 12, 0, 0);
            WatermarkColumnSpec spec = WatermarkColumnSpec.of(
                    "source_updated_at",
                    TemporalType.TIMESTAMP,
                    SourceTemporalPolicy.of(SAO_PAULO, "test"));

            Instant instant = WatermarkTemporalBridge.toInstant(wall, spec);
            assertEquals(wall.atZone(SAO_PAULO).toInstant()
                    .truncatedTo(java.time.temporal.ChronoUnit.MICROS), instant);

            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
            Instant again = WatermarkTemporalBridge.toInstant(wall, spec);
            assertEquals(instant, again);

            Object bind = WatermarkTemporalBridge.toSourceBindValue(instant, spec);
            assertEquals(wall, bind);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void date_UsesStartOfDayInConfiguredZone() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        WatermarkColumnSpec spec = WatermarkColumnSpec.of(
                "d", TemporalType.DATE, SourceTemporalPolicy.of(SAO_PAULO, "test"));

        Instant instant = WatermarkTemporalBridge.toInstant(date, spec);
        assertEquals(date.atStartOfDay(SAO_PAULO).toInstant()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS), instant);
        assertEquals(date, WatermarkTemporalBridge.toSourceBindValue(instant, spec));
    }

    @Test
    void timestamp_RequiresSourceTimezone() {
        assertThrows(IllegalStateException.class, () ->
                WatermarkColumnSpec.of(
                        "u", TemporalType.TIMESTAMP, SourceTemporalPolicy.none()));
    }

    @Test
    void time_RejectedAsWatermark() {
        assertThrows(IllegalArgumentException.class, () ->
                WatermarkColumnSpec.of(
                        "u", TemporalType.TIME, SourceTemporalPolicy.none()));
    }

    @Test
    void buildPredicate_TimestamptzUsesUtcLiteral() {
        Instant wm = Instant.parse("2026-08-10T15:00:00Z");
        WatermarkColumnSpec spec = WatermarkColumnSpec.of(
                "data_atualizacao", TemporalType.TIMESTAMPTZ, SourceTemporalPolicy.none());
        WatermarkPredicate predicate = WatermarkTemporalBridge.buildPredicate(spec, wm);
        assertTrue(predicate.sqlFragment().contains("IS NOT NULL"));
        assertTrue(predicate.sqlFragment().contains("TIMESTAMP WITH TIME ZONE '2026-08-10T15:00:00Z'"));
        assertTrue(predicate.sqlFragment().contains("data_atualizacao > "));
    }

    @Test
    void buildPredicate_TimestampProjectsWithConfiguredZone() {
        Instant wm = Instant.parse("2026-08-10T18:00:00Z");
        WatermarkColumnSpec spec = WatermarkColumnSpec.of(
                "source_updated_at",
                TemporalType.TIMESTAMP,
                SourceTemporalPolicy.of(SAO_PAULO, "test"));
        WatermarkPredicate predicate = WatermarkTemporalBridge.buildPredicate(spec, wm);
        assertTrue(predicate.sqlFragment().contains("AT TIME ZONE 'America/Sao_Paulo'"));
        assertTrue(predicate.sqlFragment().contains("TIMESTAMP WITH TIME ZONE '2026-08-10T18:00:00Z'"));
    }

    @Test
    void buildPredicate_DateCastsProjectedWallClock() {
        Instant wm = Instant.parse("2026-08-10T03:00:00Z");
        WatermarkColumnSpec spec = WatermarkColumnSpec.of(
                "d", TemporalType.DATE, SourceTemporalPolicy.of(SAO_PAULO, "test"));
        WatermarkPredicate predicate = WatermarkTemporalBridge.buildPredicate(spec, wm);
        assertTrue(predicate.sqlFragment().contains("::date"));
        assertTrue(predicate.sqlFragment().contains("America/Sao_Paulo"));
    }

    @Test
    void buildChangeDetectionPredicate_UsesCreationOnlyWhenUpdatedMissing() {
        Instant wm = Instant.parse("2026-08-10T15:00:00Z");
        WatermarkColumnSpec creation = WatermarkColumnSpec.of(
                "data_criacao", TemporalType.TIMESTAMPTZ, SourceTemporalPolicy.none());
        WatermarkPredicate firstLoad = WatermarkTemporalBridge.buildChangeDetectionPredicate(
                creation, null, null);
        assertEquals("data_criacao IS NOT NULL", firstLoad.sqlFragment());

        WatermarkPredicate incremental = WatermarkTemporalBridge.buildChangeDetectionPredicate(
                creation, null, wm);
        assertTrue(incremental.sqlFragment().contains("data_criacao IS NOT NULL"));
        assertTrue(incremental.sqlFragment().contains("data_criacao > TIMESTAMP WITH TIME ZONE"));
        assertTrue(!incremental.sqlFragment().contains(" OR "));
    }

    @Test
    void buildChangeDetectionPredicate_ConsidersBothDatesWhenUpdatedPresent() {
        Instant wm = Instant.parse("2026-08-10T15:00:00Z");
        WatermarkColumnSpec creation = WatermarkColumnSpec.of(
                "data_criacao", TemporalType.TIMESTAMPTZ, SourceTemporalPolicy.none());
        WatermarkColumnSpec updated = WatermarkColumnSpec.of(
                "data_atualizacao", TemporalType.TIMESTAMPTZ, SourceTemporalPolicy.none());
        WatermarkPredicate predicate = WatermarkTemporalBridge.buildChangeDetectionPredicate(
                creation, updated, wm);
        assertTrue(predicate.sqlFragment().contains("data_criacao IS NOT NULL"));
        assertTrue(predicate.sqlFragment().contains("data_atualizacao IS NOT NULL"));
        assertTrue(predicate.sqlFragment().contains(" OR "));
    }

    @Test
    void truncate_ToMicroseconds() {
        Instant nano = Instant.parse("2026-01-01T00:00:00.123456789Z");
        assertEquals(Instant.parse("2026-01-01T00:00:00.123456Z"),
                WatermarkTemporalBridge.truncate(nano));
    }
}
