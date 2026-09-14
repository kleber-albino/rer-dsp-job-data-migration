package br.car.dsp_batch.aoi.introspection;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.layer.introspection.SchemaIntrospectionService;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
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

class AreaOfInterestIntrospectionServiceTest {

    private AreaOfInterestIntrospectionService service;
    private SchemaIntrospectionService schemaIntrospectionService;
    private JdbcTemplate sourceJdbc;
    private JdbcTemplate targetJdbc;
    private JdbcTemplate geoTargetJdbc;

    @BeforeEach
    void setUp() {
        schemaIntrospectionService = mock(SchemaIntrospectionService.class);
        sourceJdbc = mock(JdbcTemplate.class);
        targetJdbc = mock(JdbcTemplate.class);
        geoTargetJdbc = mock(JdbcTemplate.class);
        service = new AreaOfInterestIntrospectionService(
                new BatchTemporalProperties("America/Sao_Paulo"),
                schemaIntrospectionService,
                geoTargetJdbc,
                targetJdbc);
        stubSourceTableExists();
        stubTargetTablesMissing();
    }

    @Test
    void introspect_AllowsUnmappedSourceColumnNamedCreatedAt() {
        AreaOfInterestConfig config = baseConfig();
        config.setCreationDateColumn("creation_date");
        config.setUpdatedAtColumn("alteration_date");
        config.setAdditionalColumns(List.of("management_plan"));

        stubColumns(
                column("id", "int8", false),
                column("creation_date", "timestamptz", false),
                column("created_at", "timestamptz", false),
                column("alteration_date", "timestamptz", false),
                column("territory_level_3_fk", "int8", false),
                column("total_area_ha", "numeric", false),
                column("management_plan", "varchar", false),
                column("geom", "geometry", true)
        );
        stubGeometryColumnLookup("geom", 4674, "MULTIPOLYGON");
        stubEmptyIndexes();

        AreaOfInterestTableMetadata metadata = service.introspect(sourceJdbc, config);

        assertEquals("creation_date", metadata.creationDateSourceColumn());
        assertEquals("created_at", metadata.resolveTargetCreatedAtColumn());
        assertFalse(metadata.columns().stream().anyMatch(c -> "created_at".equals(c.name())));
    }

    @Test
    void introspect_RejectsCreatedAtInAdditionalColumns() {
        AreaOfInterestConfig config = baseConfig();
        config.setCreationDateColumn("creation_date");
        config.setAdditionalColumns(List.of("created_at"));

        stubColumns(
                column("id", "int8", false),
                column("creation_date", "timestamptz", false),
                column("created_at", "timestamptz", false),
                column("updated_at", "timestamptz", false),
                column("territory_level_3_fk", "int8", false),
                column("total_area_ha", "numeric", false),
                column("geom", "geometry", true)
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.introspect(sourceJdbc, config)
        );
        assertTrue(ex.getMessage().contains("additional-columns"));
        assertTrue(ex.getMessage().contains("created_at"));
    }

    private AreaOfInterestConfig baseConfig() {
        AreaOfInterestConfig config = new AreaOfInterestConfig();
        config.setSourceTable("conservation.conservation_units");
        config.setTargetTable("dsp.area_of_interest");
        config.setPrimaryKey("id");
        config.setCreationDateColumn("creation_date");
        config.setUpdatedAtColumn("updated_at");
        config.setTerritoryLevel3Column("territory_level_3_fk");
        config.setTotalAreaColumn("total_area_ha");
        config.setGeometryColumn("geom");
        config.setSrid(4674);
        return config;
    }

    private void stubSourceTableExists() {
        when(schemaIntrospectionService.tableExists(eq(sourceJdbc), any())).thenReturn(true);
    }

    private void stubTargetTablesMissing() {
        when(schemaIntrospectionService.tableExists(eq(targetJdbc), any())).thenReturn(false);
        when(schemaIntrospectionService.tableExists(eq(geoTargetJdbc), any())).thenReturn(false);
    }

    private void stubColumns(ColumnMetadata... columns) {
        when(sourceJdbc.query(contains("information_schema.columns"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(columns));
    }

    private void stubEmptyIndexes() {
        when(sourceJdbc.query(contains("pg_indexes"), any(RowMapper.class), any(), any()))
                .thenReturn(List.of());
    }

    private void stubGeometryColumnLookup(String columnName, Integer srid, String type) {
        when(sourceJdbc.query(contains("f_geometry_column = ?"), any(RowMapper.class), any(), any(), eq(columnName)))
                .thenAnswer(invocation -> mapGeometryRows(
                        invocation.getArgument(1),
                        geomColumn(columnName, srid, type)
                ));
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
