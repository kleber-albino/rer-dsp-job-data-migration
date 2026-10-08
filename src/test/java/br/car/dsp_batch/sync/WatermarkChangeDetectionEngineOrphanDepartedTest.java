package br.car.dsp_batch.sync;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.config.LayersProperties;
import br.car.dsp_batch.layer.introspection.SchemaIntrospectionService;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatermarkChangeDetectionEngineOrphanDepartedTest {

    private final SyncStateRepository syncStateRepository = mock(SyncStateRepository.class);
    private final WatermarkChangeDetectionEngine engine =
            new WatermarkChangeDetectionEngine(syncStateRepository);

    @Test
    void deleteOrphans_CapturesDepartedLevel3BeforeDelete() {
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);
        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        JdbcTemplate businessTargetJdbc = mock(JdbcTemplate.class);
        JobExecution jobExecution = new JobExecution(1L);

        when(syncStateRepository.findBySyncKey(SyncKeys.AREA_OF_INTEREST)).thenReturn(Optional.of(
                new SyncState(
                        SyncKeys.AREA_OF_INTEREST,
                        "src.aoi",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        1L,
                        Instant.now().minus(WatermarkSettings.ORPHAN_CHECK_INTERVAL).minusSeconds(60)
                )));

        doAnswer(invocation -> null).when(sourceJdbc).query(anyString(), any(RowCallbackHandler.class));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(mockBboxResultSet("100"));
            return null;
        }).when(geoTargetJdbc).query(anyString(), any(RowCallbackHandler.class));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(mockResultSet("l3-a"));
            return null;
        }).when(geoTargetJdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
        when(geoTargetJdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        WatermarkTableSpec spec = areaOfInterestSpec();

        engine.detectChanges(
                sourceJdbc,
                geoTargetJdbc,
                businessTargetJdbc,
                spec,
                chunkContext(jobExecution)
        );

        assertEquals(Set.of("l3-a"), DepartedLevel3ContextSupport.read(jobExecution.getExecutionContext()));

        ArgumentCaptor<String> departedSql = ArgumentCaptor.forClass(String.class);
        verify(geoTargetJdbc).query(departedSql.capture(), any(RowCallbackHandler.class), any(Object[].class));
        assertEquals(
                "SELECT DISTINCT territory_level_3_id FROM dsp.area_of_interest WHERE id IN (?) "
                        + "AND territory_level_3_id IS NOT NULL",
                departedSql.getValue());
        InOrder order = inOrder(businessTargetJdbc, geoTargetJdbc);
        order.verify(businessTargetJdbc).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "100");
        order.verify(geoTargetJdbc).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "100");
    }

    @Test
    void deleteOrphans_RemovesKpiAndLayerFeaturesBeforeAreaOfInterest() {
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);
        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        JdbcTemplate businessTargetJdbc = mock(JdbcTemplate.class);
        SchemaIntrospectionService introspection = mock(SchemaIntrospectionService.class);
        LayersProperties layers = new LayersProperties();
        layers.setLayers(List.of(layer("zona-seguranca-300m"), layer("zona-seguranca-600m")));
        WatermarkChangeDetectionEngine engineWithLayers =
                new WatermarkChangeDetectionEngine(syncStateRepository, layers, introspection);

        stubOrphanScan(sourceJdbc, geoTargetJdbc, "73", "1673");
        when(introspection.tableExists(eq(businessTargetJdbc), any(QualifiedTable.class))).thenReturn(true);
        when(introspection.tableExists(eq(geoTargetJdbc), eq(new QualifiedTable("dsp", "zona_seguranca_300m"))))
                .thenReturn(true);
        when(introspection.tableExists(eq(geoTargetJdbc), eq(new QualifiedTable("dsp", "zona_seguranca_600m"))))
                .thenReturn(false);
        when(businessTargetJdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(geoTargetJdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        JobExecution jobExecution = new JobExecution(3L);
        engineWithLayers.detectChanges(
                sourceJdbc, geoTargetJdbc, businessTargetJdbc, areaOfInterestSpec(), chunkContext(jobExecution));

        assertEquals(Set.of("1673"), DepartedLevel3ContextSupport.read(jobExecution.getExecutionContext()));
        InOrder order = inOrder(businessTargetJdbc, geoTargetJdbc);
        order.verify(businessTargetJdbc).update(
                "DELETE FROM \"dsp\".\"kpi_measure\" WHERE \"area_of_interest_id\" IN (?)",
                "73");
        order.verify(geoTargetJdbc).update(
                "DELETE FROM \"dsp\".\"zona_seguranca_300m\" WHERE \"area_of_interest_id\" IN (?)",
                "73");
        order.verify(businessTargetJdbc).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "73");
        order.verify(geoTargetJdbc).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "73");
        verify(geoTargetJdbc, never()).update(contains("zona_seguranca_600m"), any(Object[].class));
    }

    @Test
    void deleteOrphans_RetriesAreaDeleteWhenGeoTargetFailsAfterBusinessDelete() {
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);
        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        JdbcTemplate businessTargetJdbc = mock(JdbcTemplate.class);
        SchemaIntrospectionService introspection = mock(SchemaIntrospectionService.class);
        LayersProperties layers = new LayersProperties();
        WatermarkChangeDetectionEngine engineWithKpi =
                new WatermarkChangeDetectionEngine(syncStateRepository, layers, introspection);

        stubOrphanScan(sourceJdbc, geoTargetJdbc, "73", "1673");
        when(introspection.tableExists(eq(businessTargetJdbc), any(QualifiedTable.class))).thenReturn(true);
        when(businessTargetJdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(geoTargetJdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new RuntimeException("geo-target delete failed"))
                .thenReturn(1);

        WatermarkTableSpec spec = areaOfInterestSpec();
        ChunkContext context = chunkContext(new JobExecution(4L));
        assertThrows(RuntimeException.class, () -> engineWithKpi.detectChanges(
                sourceJdbc, geoTargetJdbc, businessTargetJdbc, spec, context));
        engineWithKpi.detectChanges(sourceJdbc, geoTargetJdbc, businessTargetJdbc, spec, context);

        InOrder order = inOrder(businessTargetJdbc, geoTargetJdbc);
        order.verify(businessTargetJdbc).update(
                "DELETE FROM \"dsp\".\"kpi_measure\" WHERE \"area_of_interest_id\" IN (?)",
                "73");
        order.verify(businessTargetJdbc).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "73");
        order.verify(geoTargetJdbc).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "73");
        verify(businessTargetJdbc, times(2)).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "73");
        verify(geoTargetJdbc, times(2)).update(
                "DELETE FROM dsp.area_of_interest WHERE id IN (?)",
                "73");
    }

    @Test
    void deleteOrphans_SkipsDepartedCaptureWhenColumnNotConfigured() {
        JdbcTemplate sourceJdbc = mock(JdbcTemplate.class);
        JdbcTemplate geoTargetJdbc = mock(JdbcTemplate.class);
        JobExecution jobExecution = new JobExecution(2L);

        doAnswer(invocation -> null).when(sourceJdbc).query(anyString(), any(RowCallbackHandler.class));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(mockBboxResultSet("100"));
            return null;
        }).when(geoTargetJdbc).query(anyString(), any(RowCallbackHandler.class));
        when(geoTargetJdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        WatermarkTableSpec spec = new WatermarkTableSpec(
                SyncKeys.ADMIN_UNIT_LEVEL_3,
                "src.l3",
                "id",
                "geom",
                TemporalTestFixtures.timestamptz("created_at"),
                TemporalTestFixtures.timestamptz("updated_at"),
                "1=1",
                4326,
                "territory-level-3",
                "dsp.territory_level_3",
                "id",
                "geometry",
                null,
                null,
                null
        );

        when(syncStateRepository.findBySyncKey(SyncKeys.ADMIN_UNIT_LEVEL_3)).thenReturn(Optional.of(
                new SyncState(
                        SyncKeys.ADMIN_UNIT_LEVEL_3,
                        "src.l3",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        1L,
                        Instant.now().minus(WatermarkSettings.ORPHAN_CHECK_INTERVAL).minusSeconds(60)
                )));

        engine.detectChanges(
                sourceJdbc,
                geoTargetJdbc,
                null,
                spec,
                chunkContext(jobExecution)
        );

        assertEquals(Set.of(), DepartedLevel3ContextSupport.read(jobExecution.getExecutionContext()));
        verify(geoTargetJdbc, never()).query(
                anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    private void stubOrphanScan(JdbcTemplate sourceJdbc,
                                JdbcTemplate geoTargetJdbc,
                                String orphanId,
                                String level3Id) {
        when(syncStateRepository.findBySyncKey(SyncKeys.AREA_OF_INTEREST)).thenReturn(Optional.of(
                new SyncState(
                        SyncKeys.AREA_OF_INTEREST,
                        "src.aoi",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        1L,
                        Instant.now().minus(WatermarkSettings.ORPHAN_CHECK_INTERVAL).minusSeconds(60)
                )));
        doAnswer(invocation -> null).when(sourceJdbc).query(anyString(), any(RowCallbackHandler.class));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(mockBboxResultSet(orphanId));
            return null;
        }).when(geoTargetJdbc).query(anyString(), any(RowCallbackHandler.class));
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            handler.processRow(mockResultSet(level3Id));
            return null;
        }).when(geoTargetJdbc).query(anyString(), any(RowCallbackHandler.class), any(Object[].class));
    }

    private static LayerConfig layer(String layerName) {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("public.src_layer");
        config.setLayerName(layerName);
        config.setPrimaryKey("id");
        config.setAreaOfInterestIdColumn("airport_ref_id");
        config.setCreationDateColumn("sourced_at");
        config.setGeometryColumn("the_geom");
        return config;
    }

    private static WatermarkTableSpec areaOfInterestSpec() {
        return new WatermarkTableSpec(
                SyncKeys.AREA_OF_INTEREST,
                "src.aoi",
                "id",
                "geom",
                TemporalTestFixtures.timestamptz("created_at"),
                TemporalTestFixtures.timestamptz("updated_at"),
                "1=1",
                4326,
                "area-of-interest",
                "dsp.area_of_interest",
                "id",
                "geometry",
                "dsp.area_of_interest",
                "id",
                "territory_level_3_id"
        );
    }

    private static ChunkContext chunkContext(JobExecution jobExecution) {
        StepContext stepContext = mock(StepContext.class);
        org.springframework.batch.core.StepExecution stepExecution =
                mock(org.springframework.batch.core.StepExecution.class);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);
        return new ChunkContext(stepContext);
    }

    private static java.sql.ResultSet mockResultSet(Object value) {
        try {
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getObject(1)).thenReturn(value);
            return rs;
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static java.sql.ResultSet mockBboxResultSet(Object id) {
        try {
            java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
            when(rs.getObject("id")).thenReturn(id);
            when(rs.getDouble("minx")).thenReturn(0.0);
            when(rs.getDouble("miny")).thenReturn(0.0);
            when(rs.getDouble("maxx")).thenReturn(1.0);
            when(rs.getDouble("maxy")).thenReturn(1.0);
            return rs;
        } catch (java.sql.SQLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
