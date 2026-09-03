package br.car.dsp_batch.aoi.ddl;

import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.layer.ddl.PostgresTypeMapper;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaOfInterestTableDdlBuilderTest {

    private final AreaOfInterestTableDdlBuilder builder =
            new AreaOfInterestTableDdlBuilder(new PostgresTypeMapper());

    @Test
    void buildGeoCreateTable_UsesCanonicalTargetColumns() {
        AreaOfInterestTableMetadata metadata = sampleMetadata(List.of());

        String ddl = builder.buildGeoCreateTable(metadata);

        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS dsp.area_of_interest"));
        assertTrue(ddl.contains("\"id\" varchar(255)"));
        assertTrue(ddl.contains("\"created_at\" timestamptz"));
        assertTrue(ddl.contains("\"updated_at\" timestamptz"));
        assertTrue(ddl.contains("\"territory_level_3_id\" varchar(64)"));
        assertTrue(ddl.contains("\"area\""));
        assertTrue(ddl.contains("\"geom\" geometry(Geometry, 4674)"));
        assertTrue(!ddl.contains("\"creation_date\""));
        assertTrue(!ddl.contains("\"commune_id\""));
        assertTrue(!ddl.contains("\"total_area_ha\""));
        assertTrue(ddl.contains("PRIMARY KEY (\"id\")"));
    }

    @Test
    void buildGeoCreateTable_ForcesVarcharIdsEvenWhenSourceIsBigint() {
        AreaOfInterestTableMetadata metadata = sampleMetadata(List.of());

        String geoDdl = builder.buildGeoCreateTable(metadata);
        String businessDdl = builder.buildBusinessCreateTable(metadata);

        assertTrue(geoDdl.contains("\"id\" varchar(255)"));
        assertTrue(geoDdl.contains("\"territory_level_3_id\" varchar(64)"));
        assertTrue(!geoDdl.contains("\"id\" bigint"));
        assertTrue(!geoDdl.contains("\"territory_level_3_id\" bigint"));
        assertTrue(businessDdl.contains("\"id\" varchar(255)"));
        assertTrue(businessDdl.contains("\"territory_level_3_id\" varchar(64)"));
    }

    @Test
    void buildGeoCreateTable_ExcludesBusinessOnlyColumns() {
        AreaOfInterestTableMetadata metadata = sampleMetadata(List.of("theme_1", "theme_2"));

        String geoDdl = builder.buildGeoCreateTable(metadata);
        String businessDdl = builder.buildBusinessCreateTable(metadata);

        assertTrue(!geoDdl.contains("\"theme_1\""));
        assertTrue(!geoDdl.contains("\"theme_2\""));
        assertTrue(businessDdl.contains("\"theme_1\""));
        assertTrue(businessDdl.contains("\"theme_2\""));
    }

    @Test
    void buildBusinessCreateTable_AddsBoundaryBoxAndCentroid() {
        AreaOfInterestTableMetadata metadata = sampleMetadata(List.of());

        String ddl = builder.buildBusinessCreateTable(metadata);

        assertTrue(ddl.contains("\"boundary_box\" geometry(Polygon, 4674)"));
        assertTrue(ddl.contains("\"centroid_coordinates\" geometry(Point, 4674)"));
        assertTrue(!ddl.contains("\"geom\""));
        assertTrue(ddl.contains("\"territory_level_3_id\""));
        assertTrue(ddl.contains("\"theme_1\" numeric"));
        assertTrue(ddl.contains("\"theme_2\" numeric"));
        assertTrue(ddl.contains("\"theme_3\" numeric"));
        assertTrue(ddl.contains("\"theme_4\" numeric"));
    }

    @Test
    void buildBusinessCreateTable_AlwaysIncludesUpdatedAtWhenSourceOmitsIt() {
        AreaOfInterestTableMetadata metadata = sampleMetadataWithoutSourceUpdatedAt(List.of());

        String ddl = builder.buildBusinessCreateTable(metadata);

        assertTrue(ddl.contains("\"updated_at\" timestamptz"));
        assertTrue(ddl.contains("\"theme_1\" numeric"));
    }

    @Test
    void buildBusinessTargetStatements_IncludesUpdatedAtIndexOnBusinessTarget() {
        AreaOfInterestTableMetadata metadata = sampleMetadataWithoutSourceUpdatedAt(List.of());

        List<String> statements = builder.buildBusinessTargetStatements(metadata);

        assertTrue(statements.stream().anyMatch(sql -> sql.contains("\"updated_at\" timestamptz")));
        assertTrue(statements.stream().anyMatch(sql -> sql.contains("_business_updated_at")));
    }

    private static AreaOfInterestTableMetadata sampleMetadataWithoutSourceUpdatedAt(
            List<String> businessOnlySourceColumns
    ) {
        List<ColumnMetadata> columns = new java.util.ArrayList<>(List.of(
                new ColumnMetadata("id", "int8", null, null, null, false, false),
                new ColumnMetadata("creation_date", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("territory_level_3_fk", "int8", null, null, null, false, false),
                new ColumnMetadata("total_area_ha", "numeric", null, null, null, false, false),
                new ColumnMetadata("geom", "geometry", null, null, null, true, true)
        ));
        for (String businessOnly : businessOnlySourceColumns) {
            columns.add(new ColumnMetadata(businessOnly, "numeric", null, null, null, true, false));
        }
        return new AreaOfInterestTableMetadata(
                "area_of_interest",
                "chile-conservation-units",
                new QualifiedTable("conservation", "conservation_units"),
                new QualifiedTable("dsp", "area_of_interest"),
                "id",
                TemporalTestFixtures.timestamptz("creation_date"),
                null,
                "territory_level_3_fk",
                "total_area_ha",
                "geom",
                4674,
                columns,
                businessOnlySourceColumns,
                List.of(),
                "1=1"
        );
    }

    private static AreaOfInterestTableMetadata sampleMetadata(List<String> businessOnlySourceColumns) {
        List<ColumnMetadata> columns = new java.util.ArrayList<>(List.of(
                new ColumnMetadata("id", "int8", null, null, null, false, false),
                new ColumnMetadata("creation_date", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("updated_at", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("territory_level_3_fk", "int8", null, null, null, false, false),
                new ColumnMetadata("total_area_ha", "numeric", null, null, null, false, false),
                new ColumnMetadata("geom", "geometry", null, null, null, true, true)
        ));
        for (String businessOnly : businessOnlySourceColumns) {
            columns.add(new ColumnMetadata(businessOnly, "numeric", null, null, null, true, false));
        }
        return new AreaOfInterestTableMetadata(
                "area_of_interest",
                "chile-conservation-units",
                new QualifiedTable("conservation", "conservation_units"),
                new QualifiedTable("dsp", "area_of_interest"),
                "id",
                TemporalTestFixtures.timestamptz("creation_date"),
                TemporalTestFixtures.timestamptz("updated_at"),
                "territory_level_3_fk",
                "total_area_ha",
                "geom",
                4674,
                columns,
                businessOnlySourceColumns,
                List.of(),
                "1=1"
        );
    }
}
