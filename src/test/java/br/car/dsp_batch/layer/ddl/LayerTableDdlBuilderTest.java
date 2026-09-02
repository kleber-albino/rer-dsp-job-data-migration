package br.car.dsp_batch.layer.ddl;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import br.car.dsp_batch.layer.metadata.IndexMetadata;
import br.car.dsp_batch.layer.metadata.LayerTableMetadata;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerTableDdlBuilderTest {

    private final LayerTableDdlBuilder builder = new LayerTableDdlBuilder(new PostgresTypeMapper());

    @Test
    void buildCreateTable_UsesCanonicalTargetColumns() {
        LayerTableMetadata metadata = sampleMetadata();

        String ddl = builder.buildCreateTable(metadata);

        assertTrue(ddl.contains("CREATE TABLE IF NOT EXISTS dsp.parcelas"));
        assertTrue(ddl.contains("\"id\" varchar(255)"));
        assertTrue(ddl.contains("\"label\" varchar(255)"));
        assertTrue(ddl.contains("\"area_of_interest_id\" varchar(255)"));
        assertTrue(ddl.contains("\"created_at\" timestamptz"));
        assertTrue(ddl.contains("\"updated_at\" timestamptz"));
        assertTrue(ddl.contains("\"codigo\" varchar(40)"));
        assertTrue(ddl.contains("\"geom\" geometry(Geometry, 4674)"));
        assertTrue(!ddl.contains("\"the_geom\""));
        assertTrue(!ddl.contains("\"data_atualizacao\""));
        assertTrue(!ddl.contains("\"id_parcela\""));
        assertTrue(!ddl.contains("\"nome\""));
        assertTrue(ddl.contains("PRIMARY KEY (\"id\")"));
        assertTrue(!ddl.contains("\"cod_imovel\""));
    }

    @Test
    void buildCreateTable_ForcesUpdatedAtTimestamptzEvenWhenSourceIsTimestamp() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id_parcela", "varchar", 80, null, null, false, false),
                new ColumnMetadata("cod_imovel", "varchar", 80, null, null, false, false),
                new ColumnMetadata("nome", "varchar", 255, null, null, true, false),
                new ColumnMetadata("data_criacao", "timestamp", null, null, null, false, false),
                new ColumnMetadata("data_atualizacao", "timestamp", null, null, null, false, false),
                new ColumnMetadata("the_geom", "geometry", null, null, null, true, true)
        );
        LayerTableMetadata metadata = metadata(
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_criacao",
                "data_atualizacao",
                "nome",
                columns,
                List.of()
        );

        String ddl = builder.buildCreateTable(metadata);

        assertTrue(ddl.contains("\"updated_at\" timestamptz"));
        assertTrue(!ddl.contains("\"updated_at\" timestamp\""));
    }

    @Test
    void buildCreateTable_ForcesVarcharIdsEvenWhenSourceIsBigint() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id_parcela", "int8", null, null, null, false, false),
                new ColumnMetadata("cod_imovel", "int8", null, null, null, false, false),
                new ColumnMetadata("nome", "varchar", 255, null, null, true, false),
                new ColumnMetadata("data_criacao", "timestamp", null, null, null, false, false),
                new ColumnMetadata("data_atualizacao", "timestamp", null, null, null, false, false),
                new ColumnMetadata("the_geom", "geometry", null, null, null, true, true)
        );
        LayerTableMetadata metadata = metadata(
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_criacao",
                "data_atualizacao",
                "nome",
                columns,
                List.of()
        );

        String ddl = builder.buildCreateTable(metadata);

        assertTrue(ddl.contains("\"id\" varchar(255)"));
        assertTrue(ddl.contains("\"area_of_interest_id\" varchar(255)"));
        assertTrue(!ddl.contains("\"id\" bigint"));
        assertTrue(!ddl.contains("\"area_of_interest_id\" bigint"));
    }

    @Test
    void buildCreateTable_MigratesOnlyChosenGeometryColumn() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id_parcela", "varchar", 80, null, null, false, false),
                new ColumnMetadata("cod_imovel", "varchar", 80, null, null, false, false),
                new ColumnMetadata("nome", "varchar", 255, null, null, true, false),
                new ColumnMetadata("data_criacao", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("data_atualizacao", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("the_geom", "geometry", null, null, null, true, true)
        );
        LayerTableMetadata metadata = metadata(
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_criacao",
                "data_atualizacao",
                "nome",
                columns,
                List.of()
        );

        String ddl = builder.buildCreateTable(metadata);

        assertTrue(ddl.contains("\"geom\" geometry(Geometry, 4674)"));
        assertTrue(!ddl.contains("\"centroid\""));
        assertTrue(!ddl.contains("\"the_geom\""));
    }

    @Test
    void buildStatements_ReturnsCreateTableAndIndexes() {
        LayerTableMetadata metadata = sampleMetadata();

        List<String> statements = builder.buildStatements(metadata);

        assertTrue(statements.size() >= 4);
        assertTrue(statements.getFirst().startsWith("CREATE TABLE"));
        assertTrue(statements.stream().anyMatch(s -> s.contains("USING GIST")));
        assertTrue(statements.stream().anyMatch(s -> s.contains(LayerConfig.AREA_OF_INTEREST_ID_COLUMN)));
        assertTrue(statements.stream().anyMatch(s -> s.contains(LayerConfig.UPDATED_AT_COLUMN)
                && s.startsWith("CREATE INDEX")));
    }

    @Test
    void buildGeometryIndex_UsesGistOnCanonicalGeomColumn() {
        LayerTableMetadata metadata = sampleMetadata();

        String ddl = builder.buildGeometryIndex(metadata);

        assertTrue(ddl.contains("CREATE INDEX IF NOT EXISTS"));
        assertTrue(ddl.contains("USING GIST (\"geom\")"));
    }

    @Test
    void buildAreaOfInterestIdIndex_CreatesBtreeOnCanonicalColumn() {
        LayerTableMetadata metadata = sampleMetadata();

        String ddl = builder.buildAreaOfInterestIdIndex(metadata);

        assertTrue(ddl.contains("CREATE INDEX IF NOT EXISTS"));
        assertTrue(ddl.contains("(\"area_of_interest_id\")"));
    }

    @Test
    void buildUpdatedAtIndex_CreatesBtreeOnCanonicalColumn() {
        String ddl = builder.buildUpdatedAtIndex(sampleMetadata());
        assertTrue(ddl.contains("(\"updated_at\")"));
    }

    @Test
    void buildSecondaryIndexes_SkipsDuplicateGistOnGeometry() {
        LayerTableMetadata metadata = metadata(
                sampleMetadata().primaryKeyColumn(),
                sampleMetadata().geometryColumn(),
                sampleMetadata().areaOfInterestIdSourceColumn(),
                sampleMetadata().creationDateSourceColumn(),
                sampleMetadata().updatedAtSourceColumn(),
                sampleMetadata().labelSourceColumn(),
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_geo_idx", List.of("the_geom"), "gist", false))
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.isEmpty());
    }

    @Test
    void buildSecondaryIndexes_SkipsRedundantIndexOnRenamedAreaOfInterestColumn() {
        LayerTableMetadata metadata = metadata(
                sampleMetadata().primaryKeyColumn(),
                sampleMetadata().geometryColumn(),
                sampleMetadata().areaOfInterestIdSourceColumn(),
                sampleMetadata().creationDateSourceColumn(),
                sampleMetadata().updatedAtSourceColumn(),
                sampleMetadata().labelSourceColumn(),
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_aoi_idx", List.of("cod_imovel"), "btree", false))
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.isEmpty());
    }

    @Test
    void buildSecondaryIndexes_KeepsIndexOnExtraColumn() {
        LayerTableMetadata metadata = metadata(
                sampleMetadata().primaryKeyColumn(),
                sampleMetadata().geometryColumn(),
                sampleMetadata().areaOfInterestIdSourceColumn(),
                sampleMetadata().creationDateSourceColumn(),
                sampleMetadata().updatedAtSourceColumn(),
                sampleMetadata().labelSourceColumn(),
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_codigo_idx", List.of("codigo"), "btree", false))
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.size() == 1);
        assertTrue(indexes.getFirst().contains("(\"codigo\")"));
    }

    @Test
    void buildSecondaryIndexes_MapsCanonicalColumnsInsideCompositeIndex() {
        LayerTableMetadata metadata = sampleMetadata();
        List<String> indexes = builder.buildSecondaryIndexes(metadata(
                metadata.primaryKeyColumn(),
                metadata.geometryColumn(),
                metadata.areaOfInterestIdSourceColumn(),
                metadata.creationDateSourceColumn(),
                metadata.updatedAtSourceColumn(),
                metadata.labelSourceColumn(),
                metadata.columns(),
                List.of(new IndexMetadata(
                        "parcelas_aoi_nome_idx",
                        List.of("cod_imovel", "nome"),
                        "btree",
                        false
                ))
        ));

        assertTrue(indexes.size() == 1);
        assertTrue(indexes.getFirst().contains("area_of_interest_id"));
        assertTrue(indexes.getFirst().contains("label"));
    }

    @Test
    void buildSecondaryIndexes_SkipsIndexWhenMappedColumnMissingOnTarget() {
        LayerTableMetadata metadata = metadata(
                sampleMetadata().primaryKeyColumn(),
                sampleMetadata().geometryColumn(),
                sampleMetadata().areaOfInterestIdSourceColumn(),
                sampleMetadata().creationDateSourceColumn(),
                sampleMetadata().updatedAtSourceColumn(),
                sampleMetadata().labelSourceColumn(),
                sampleMetadata().columns(),
                List.of(new IndexMetadata("parcelas_orphan_idx", List.of("coluna_inexistente"), "btree", false))
        );

        List<String> indexes = builder.buildSecondaryIndexes(metadata);

        assertTrue(indexes.isEmpty());
    }

    private LayerTableMetadata sampleMetadata() {
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id_parcela", "varchar", 80, null, null, false, false),
                new ColumnMetadata("cod_imovel", "varchar", 80, null, null, false, false),
                new ColumnMetadata("nome", "varchar", 255, null, null, true, false),
                new ColumnMetadata("data_criacao", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("data_atualizacao", "timestamptz", null, null, null, false, false),
                new ColumnMetadata("codigo", "varchar", 40, null, null, true, false),
                new ColumnMetadata("the_geom", "geometry", null, null, null, true, true)
        );
        return metadata(
                "id_parcela",
                "the_geom",
                "cod_imovel",
                "data_criacao",
                "data_atualizacao",
                "nome",
                columns,
                List.of()
        );
    }

    private LayerTableMetadata metadata(String primaryKey,
                                        String geometryColumn,
                                        String aoiColumn,
                                        String creationDateColumn,
                                        String updatedAtColumn,
                                        String labelColumn,
                                        List<ColumnMetadata> columns,
                                        List<IndexMetadata> indexes) {
        return new LayerTableMetadata(
                "dsp_parcelas",
                "parcelas",
                new QualifiedTable("src", "parcelas"),
                new QualifiedTable("dsp", "parcelas"),
                primaryKey,
                geometryColumn,
                aoiColumn,
                TemporalTestFixtures.timestamptz(creationDateColumn),
                TemporalTestFixtures.timestamptz(updatedAtColumn),
                labelColumn,
                4674,
                columns,
                indexes,
                "1=1"
        );
    }
}
