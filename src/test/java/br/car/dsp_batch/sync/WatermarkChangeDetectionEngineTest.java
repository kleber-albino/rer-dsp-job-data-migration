package br.car.dsp_batch.sync;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WatermarkChangeDetectionEngineTest {

    private final WatermarkChangeDetectionEngine engine =
            new WatermarkChangeDetectionEngine(mock(SyncStateRepository.class));

    @Test
    void normalizeId_ConvertsNumberToString() {
        assertEquals("42", engine.normalizeId(42L));
    }

    @Test
    void shouldRunOrphanCheck_WhenNoState() {
        assertTrue(engine.shouldRunOrphanCheck(null));
    }

    @Test
    void shouldRunOrphanCheck_WhenLastCheckRecent() {
        SyncState state = new SyncState(
                "admin_unit_level_1",
                "src.l1",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                1L,
                Instant.now()
        );
        assertFalse(engine.shouldRunOrphanCheck(state));
    }

    @Test
    void shouldRunOrphanCheck_WhenLastCheckTooOld() {
        SyncState state = new SyncState(
                "admin_unit_level_1",
                "src.l1",
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                1L,
                Instant.now().minus(WatermarkSettings.ORPHAN_CHECK_INTERVAL).minusSeconds(1)
        );
        assertTrue(engine.shouldRunOrphanCheck(state));
    }
}
