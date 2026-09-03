package br.car.dsp_batch.layer.introspection;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.temporal.BatchTemporalProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaIntrospectionServiceTest {

    private SchemaIntrospectionService service;
    private JdbcTemplate jdbc;
    private JdbcTemplate geoTargetJdbc;
    private BatchTemporalProperties batchTemporalProperties;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        geoTargetJdbc = mock(JdbcTemplate.class);
        batchTemporalProperties = new BatchTemporalProperties("America/Sao_Paulo");
        service = new SchemaIntrospectionService(batchTemporalProperties, geoTargetJdbc);
        stubTargetTableMissing();
    }

    @Test
    void introspect_SelectsOnlyConfiguredColumnsAndMapsCanonicalTargets() {
        LayerConfig config = baseConfig();
        config.setAdditionalColumns(List.of("codigo"));
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("codigo", "varchar", false),
                column("ignored", "text", false),
                column("the_geom", "geometry", true)
        );
        stubGeometryColumnLookup("the_geom", 4674, "MULTILINESTRING");
        stubEmptyIndexes();

        LayerTableMetadata metadata = service.introspect(jdbc, config);

        assertEquals("source_pk", metadata.primaryKeyColumn());
        assertEquals("id", metadata.resolveTargetPrimaryKeyColumn());
        assertEquals("nome", metadata.labelSourceColumn());
        assertEquals("label", metadata.resolveTargetLabelColumn());
        assertEquals("the_geom", metadata.geometryColumn());
        assertEquals("geom", metadata.resolveTargetGeometryColumn());
        assertEquals(
                List.of("source_pk", "conservation_unit_id", "data_criacao", "data_atualizacao", "nome", "codigo", "the_geom"),
                metadata.columns().stream().map(ColumnMetadata::name).toList()
        );
        assertFalse(metadata.columns().stream().anyMatch(c -> "ignored".equals(c.name())));
    }

    @Test
    void introspect_HonorsGeometryColumnAndMapsToGeom() {
        LayerConfig config = baseConfig();
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("centroid", "geometry", true),
                column("the_geom", "geometry", true)
        );
        stubGeometryColumnLookup("the_geom", 4674, "MULTILINESTRING");
        stubEmptyIndexes();

        LayerTableMetadata metadata = service.introspect(jdbc, config);

        assertEquals("the_geom", metadata.geometryColumn());
        assertEquals("geom", metadata.resolveTargetGeometryColumn());
        assertEquals(4674, metadata.srid());
    }

    @Test
    void introspect_UsesConfiguredSridOverSource() {
        LayerConfig config = baseConfig();
        config.setSrid(4326);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );
        stubGeometryColumnLookup("the_geom", 4674, "MULTILINESTRING");
        stubEmptyIndexes();

        LayerTableMetadata metadata = service.introspect(jdbc, config);

        assertEquals(4326, metadata.srid());
    }

    @Test
    void introspect_FailsWhenGeometryColumnIsNotGeometryType() {
        LayerConfig config = baseConfig();
        config.setGeometryColumn("nome");

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("is not geometry/geography"));
        assertTrue(ex.getMessage().contains("geometry-column"));
    }

    @Test
    void introspect_FailsWithoutSrid() {
        LayerConfig config = baseConfig();

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );
        stubGeometryColumnLookup("the_geom", 0, "GEOMETRY");
        stubSampledSrid(0);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("SRID not found"));
        assertTrue(ex.getMessage().contains("batch.layers.srid"));
    }

    @Test
    void introspect_FailsWhenUpdatedAtColumnMissing() {
        LayerConfig config = baseConfig();
        config.setUpdatedAtColumn("data_inexistente");
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("updated-at-column"));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void introspect_FailsWhenLabelColumnMissing() {
        LayerConfig config = baseConfig();
        config.setLabelColumn("nome_inexistente");
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("label-column"));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    void introspect_FailsWhenUpdatedAtColumnHasInvalidType() {
        LayerConfig config = baseConfig();
        config.setUpdatedAtColumn("nome");
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_criacao", "timestamptz", false),
                column("nome", "varchar", false),
                column("the_geom", "geometry", true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("updated-at-column"));
        assertTrue(ex.getMessage().contains("timestamp"));
    }

    @Test
    void introspect_FailsWhenTimestampWatermarkWithoutTimezone() {
        SchemaIntrospectionService localService = new SchemaIntrospectionService(
                new BatchTemporalProperties(null), geoTargetJdbc);
        LayerConfig config = baseConfig();
        config.setUpdatedAtColumn("data_atualizacao");
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("data_criacao", "timestamptz", false),
                column("nome", "varchar", false),
                column("data_atualizacao", "timestamp", false),
                column("the_geom", "geometry", true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> localService.introspect(jdbc, config)
        );
        assertTrue(ex.getMessage().contains("source-timezone"));
    }

    @Test
    void introspect_FailsWhenPersistColumnMissing() {
        LayerConfig config = baseConfig();
        config.setAdditionalColumns(List.of("nao_existe"));
        config.setSrid(4674);

        stubTableExists();
        stubColumns(
                column("source_pk", "int8", false),
                column("conservation_unit_id", "int8", false),
                column("nome", "varchar", false),
                column("data_criacao", "timestamptz", false),
                column("data_atualizacao", "timestamptz", false),
                column("the_geom", "geometry", true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(jdbc, config)
        );

        assertTrue(ex.getMessage().contains("additional-columns"));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    private LayerConfig baseConfig() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("conservation.rivers");
        config.setPrimaryKey("source_pk");
        config.setAreaOfInterestIdColumn("conservation_unit_id");
        config.setCreationDateColumn("data_criacao");
        config.setUpdatedAtColumn("data_atualizacao");
        config.setLabelColumn("nome");
        config.setGeometryColumn("the_geom");
        return config;
    }

    private void stubTableExists() {
        when(jdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), any(), any()))
                .thenReturn(1);
    }

    private void stubTargetTableMissing() {
        when(geoTargetJdbc.queryForObject(
                contains("information_schema.tables"), eq(Integer.class), any(), any()))
                .thenReturn(0);
    }

    private void stubColumns(ColumnMetadata... columns) {
        when(jdbc.query(contains("information_schema.columns"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(columns));
    }

    private void stubEmptyIndexes() {
        when(jdbc.query(contains("pg_indexes"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of());
    }

    private void stubGeometryColumnLookup(String columnName, Integer srid, String type) {
        when(jdbc.query(contains("f_geometry_column = ?"), any(RowMapper.class), any(), any(), eq(columnName)))
                .thenAnswer(invocation -> mapGeometryRows(
                        invocation.getArgument(1),
                        geomColumn(columnName, srid, type)
                ));
    }

    private void stubSampledSrid(int srid) {
        when(jdbc.query(contains("ST_SRID"), any(org.springframework.jdbc.core.ResultSetExtractor.class)))
                .thenReturn(srid);
    }

    @SuppressWarnings("unchecked")
    private List<Object> mapGeometryRows(RowMapper<Object> mapper, GeomColumn... rows) {
        List<Object> result = new ArrayList<>();
        int rowNum = 0;
        for (GeomColumn row : rows) {
            ResultSet rs = mock(ResultSet.class);
            try {
                when(rs.getString("f_geometry_column")).thenReturn(row.columnName());
                when(rs.getObject("srid")).thenReturn(row.srid());
                when(rs.getString("type")).thenReturn(row.type());
                result.add(mapper.mapRow(rs, rowNum++));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private static ColumnMetadata column(String name, String udt, boolean geometry) {
        return new ColumnMetadata(name, udt, null, null, null, true, geometry);
    }

    private static GeomColumn geomColumn(String name, Integer srid, String type) {
        return new GeomColumn(name, srid, type);
    }

    private record GeomColumn(String columnName, Integer srid, String type) {
    }
}
