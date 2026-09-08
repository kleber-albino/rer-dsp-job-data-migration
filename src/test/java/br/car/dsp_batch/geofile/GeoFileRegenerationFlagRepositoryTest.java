package br.car.dsp_batch.geofile;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeoFileRegenerationFlagRepositoryTest {

    private static final Instant WATERMARK = Instant.parse("2026-03-01T00:00:00Z");

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final GeoFileRegenerationFlagRepository repository =
            new GeoFileRegenerationFlagRepository(jdbcTemplate);

    @Test
    void markChangedTerritories_FlagsEveryRowOnFirstLoad() {
        when(jdbcTemplate.update(anyString())).thenReturn(27);

        assertEquals(27, repository.markChangedTerritories("dsp.territory_level_2", null));

        verify(jdbcTemplate).update(
                "UPDATE dsp.territory_level_2 SET requires_s3_file_regeneration = TRUE");
    }

    @Test
    void markChangedTerritories_FlagsOnlyTheWatermarkWindow() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(3);

        assertEquals(3, repository.markChangedTerritories("dsp.territory_level_3", WATERMARK));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), args.capture());
        assertTrue(sql.getValue().contains("created_at > ? OR updated_at > ?"), sql.getValue());
        OffsetDateTime since = WATERMARK.atOffset(ZoneOffset.UTC);
        assertEquals(since, args.getValue()[0]);
        assertEquals(since, args.getValue()[1]);
    }

    @Test
    void markParentsOfChangedAreasOfInterest_FlagsLevel3AndItsLevel2() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(5, 2);

        var result = repository.markParentsOfChangedAreasOfInterest(
                "dsp.area_of_interest",
                "territory_level_3_id",
                "dsp.territory_level_3",
                "dsp.territory_level_2",
                WATERMARK);

        assertEquals(5, result.level3Flagged());
        assertEquals(2, result.level2Flagged());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(sql.capture(), any(Object[].class));
        String level3Sql = sql.getAllValues().get(0);
        String level2Sql = sql.getAllValues().get(1);
        assertTrue(level3Sql.startsWith("UPDATE dsp.territory_level_3"), level3Sql);
        assertTrue(level3Sql.contains("FROM dsp.area_of_interest aoi"), level3Sql);
        assertTrue(level2Sql.startsWith("UPDATE dsp.territory_level_2"), level2Sql);
        assertTrue(level2Sql.contains("t3.parent_id"), level2Sql);
    }

    @Test
    void markParentsOfChangedAreasOfInterest_OnFirstLoadReadsEveryAreaOfInterest() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 1);

        repository.markParentsOfChangedAreasOfInterest(
                "dsp.area_of_interest",
                "territory_level_3_id",
                "dsp.territory_level_3",
                "dsp.territory_level_2",
                null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(sql.capture(), args.capture());
        assertTrue(sql.getAllValues().stream().noneMatch(statement -> statement.contains("created_at >")),
                sql.getAllValues().toString());
        assertEquals(0, args.getAllValues().get(0).length);
    }

    @Test
    void markLevel3TerritoriesAndParents_FlagsLevel3AndItsLevel2() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1, 1);

        var result = repository.markLevel3TerritoriesAndParents(
                List.of("l3-a", "l3-b"),
                "dsp.territory_level_3",
                "dsp.territory_level_2");

        assertEquals(1, result.level3Flagged());
        assertEquals(1, result.level2Flagged());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(sql.capture(), any(Object[].class));
        String level3Sql = sql.getAllValues().get(0);
        String level2Sql = sql.getAllValues().get(1);
        assertTrue(level3Sql.contains("WHERE id IN (?,?)"), level3Sql);
        assertTrue(level2Sql.contains("t3.parent_id"), level2Sql);
    }

    @Test
    void markLevel3TerritoriesAndParents_SkipsWhenEmpty() {
        var result = repository.markLevel3TerritoriesAndParents(
                Set.of(),
                "dsp.territory_level_3",
                "dsp.territory_level_2");

        assertEquals(0, result.level3Flagged());
        assertEquals(0, result.level2Flagged());
        verify(jdbcTemplate, org.mockito.Mockito.never()).update(anyString(), any(Object[].class));
    }
}
