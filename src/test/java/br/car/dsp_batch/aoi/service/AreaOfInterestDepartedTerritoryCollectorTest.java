package br.car.dsp_batch.aoi.service;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaOfInterestDepartedTerritoryCollectorTest {

    private static final Instant WATERMARK = Instant.parse("2026-03-01T00:00:00Z");

    private final JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);
    private final JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
    private final AreaOfInterestDepartedTerritoryCollector collector =
            new AreaOfInterestDepartedTerritoryCollector();

    @Test
    void collectFromDelta_ReadsPreviousLevel3FromGeoTarget() {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(mockResultSet("100"));
            return null;
        }).when(sourceJdbc).query(anyString(), any(RowCallbackHandler.class));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(mockResultSet("l3-a"));
            return null;
        }).when(geoTargetJdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));

        Set<String> departed = collector.collectFromDelta(
                sourceJdbc, geoTargetJdbc, sampleMetadata(), WATERMARK);

        assertEquals(Set.of("l3-a"), departed);

        ArgumentCaptor<String> geoSql = ArgumentCaptor.forClass(String.class);
        verify(geoTargetJdbc).query(geoSql.capture(), any(RowCallbackHandler.class), any(Object[].class));
        assertTrue(geoSql.getValue().contains("territory_level_3_id"), geoSql.getValue());
        assertTrue(geoSql.getValue().contains("dsp.area_of_interest"), geoSql.getValue());
    }

    @Test
    void collectFromDelta_ReturnsEmptyWhenSourceHasNoDelta() {
        doAnswer(invocation -> null).when(sourceJdbc).query(anyString(), any(RowCallbackHandler.class));

        assertTrue(collector.collectFromDelta(
                sourceJdbc, geoTargetJdbc, sampleMetadata(), WATERMARK).isEmpty());
        verify(geoTargetJdbc, never()).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    private static AreaOfInterestTableMetadata sampleMetadata() {
        return new AreaOfInterestTableMetadata(
                "area_of_interest",
                "area-of-interest",
                new QualifiedTable("src", "src_aoi"),
                new QualifiedTable("dsp", "area_of_interest"),
                "osm_id",
                TemporalTestFixtures.timestamptz("dat_criacao"),
                TemporalTestFixtures.timestamptz("dat_atualizacao"),
                "freguesia_shape_id",
                "num_area_ha",
                "geom",
                4326,
                List.of(new ColumnMetadata("osm_id", "int8", null, null, null, false, false)),
                List.of(),
                List.of(),
                "1=1"
        );
    }

    private static java.sql.ResultSet mockResultSet(Object value) {
        try {
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getObject(1)).thenReturn(value);
            when(rs.getObject("osm_id")).thenReturn(value);
            return rs;
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
