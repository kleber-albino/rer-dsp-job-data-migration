package br.car.dsp_batch.layer.partitioner;

import br.car.dsp_batch.sync.WatermarkSql;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import br.car.dsp_batch.temporal.WatermarkTemporalBridge;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredLayerPartitionerTest {

    @Test
    void combineWhere_ReturnsNullWhenNothingToFilter() {
        assertNull(WatermarkSql.combineWhere("1=1", null));
        assertNull(WatermarkSql.combineWhere(null, null));
    }

    @Test
    void combineWhere_KeepsOnlyConfigWhere() {
        assertEquals("status = 'A'", WatermarkSql.combineWhere("status = 'A'", null));
    }

    @Test
    void combineWhere_KeepsOnlyChangeDetectionFilter() {
        assertEquals(
                "data_criacao IS NOT NULL",
                WatermarkSql.combineWhere("1=1", "data_criacao IS NOT NULL")
        );
    }

    @Test
    void combineWhere_CombinesDualDatePredicate() {
        Instant wm = Instant.parse("2026-08-10T15:00:00Z");
        String filter = WatermarkSql.buildChangeDetectionFilter(
                TemporalTestFixtures.timestamptz("data_criacao"),
                TemporalTestFixtures.timestamptz("data_atualizacao"),
                wm);
        assertEquals(
                "(status = 'A') AND " + filter,
                WatermarkSql.combineWhere("status = 'A'", filter)
        );
        assertTrue(filter.contains("data_criacao IS NOT NULL"));
        assertTrue(filter.contains("data_atualizacao IS NOT NULL"));
        assertEquals(
                filter,
                WatermarkTemporalBridge.buildChangeDetectionPredicate(
                        TemporalTestFixtures.timestamptz("data_criacao"),
                        TemporalTestFixtures.timestamptz("data_atualizacao"),
                        wm).sqlFragment()
        );
    }
}
