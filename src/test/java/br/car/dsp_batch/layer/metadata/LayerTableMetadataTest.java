package br.car.dsp_batch.layer.metadata;

import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.temporal.TemporalTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerTableMetadataTest {

    @Test
    void resolveTargetGeometryColumn_AlwaysReturnsGeom() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_criacao", "data_atualizacao", "nome");

        assertEquals(LayerConfig.GEOMETRY_COLUMN, metadata.resolveTargetGeometryColumn());
        assertEquals("geom", metadata.resolveTargetColumnName("the_geom"));
        assertEquals("the_geom", metadata.resolveSourceColumnName("geom"));
    }

    @Test
    void resolveTargetPrimaryKey_AlwaysReturnsId() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_criacao", "data_atualizacao", "nome");

        assertEquals("id", metadata.resolveTargetPrimaryKeyColumn());
        assertEquals("id", metadata.resolveTargetColumnName("source_pk"));
        assertEquals("source_pk", metadata.resolveSourceColumnName("id"));
    }

    @Test
    void resolveTargetColumnName_StillRenamesAreaOfInterestId() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_criacao", "data_atualizacao", "nome");

        assertEquals("area_of_interest_id", metadata.resolveTargetColumnName("conservation_unit_id"));
        assertEquals("conservation_unit_id", metadata.resolveSourceColumnName("area_of_interest_id"));
    }

    @Test
    void resolveTargetColumnName_RenamesCreatedAtAndUpdatedAt() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_criacao", "data_atualizacao", "nome");

        assertEquals(LayerConfig.CREATED_AT_COLUMN, metadata.resolveTargetCreatedAtColumn());
        assertEquals("created_at", metadata.resolveTargetColumnName("data_criacao"));
        assertEquals("data_criacao", metadata.resolveSourceColumnName("created_at"));
        assertEquals(LayerConfig.UPDATED_AT_COLUMN, metadata.resolveTargetUpdatedAtColumn());
        assertEquals("updated_at", metadata.resolveTargetColumnName("data_atualizacao"));
        assertEquals("data_atualizacao", metadata.resolveSourceColumnName("updated_at"));
    }

    @Test
    void resolveTargetColumnName_RenamesLabel() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_criacao", "data_atualizacao", "nome");

        assertEquals(LayerConfig.LABEL_COLUMN, metadata.resolveTargetLabelColumn());
        assertEquals("label", metadata.resolveTargetColumnName("nome"));
        assertEquals("nome", metadata.resolveSourceColumnName("label"));
    }

    @Test
    void resolveTargetColumnName_KeepsExtraColumns() {
        LayerTableMetadata metadata = sampleMetadata("the_geom", "data_criacao", "data_atualizacao", "nome");

        assertEquals("codigo", metadata.resolveTargetColumnName("codigo"));
        assertEquals("codigo", metadata.resolveSourceColumnName("codigo"));
    }

    private LayerTableMetadata sampleMetadata(String geometryColumn,
                                              String creationDateColumn,
                                              String updatedAtColumn,
                                              String labelColumn) {
        return new LayerTableMetadata(
                "dsp_rivers",
                "rivers",
                new QualifiedTable("conservation", "rivers"),
                new QualifiedTable("dsp", "rivers"),
                "source_pk",
                geometryColumn,
                "conservation_unit_id",
                TemporalTestFixtures.timestamptz(creationDateColumn),
                TemporalTestFixtures.timestamptz(updatedAtColumn),
                labelColumn,
                4674,
                List.of(
                        new ColumnMetadata("source_pk", "int8", null, null, null, false, false),
                        new ColumnMetadata("conservation_unit_id", "int8", null, null, null, false, false),
                        new ColumnMetadata(labelColumn, "varchar", 255, null, null, true, false),
                        new ColumnMetadata(creationDateColumn, "timestamptz", null, null, null, false, false),
                        new ColumnMetadata(updatedAtColumn, "timestamptz", null, null, null, false, false),
                        new ColumnMetadata("codigo", "varchar", 40, null, null, true, false),
                        new ColumnMetadata(geometryColumn, "geometry", null, null, null, true, true)
                ),
                List.of(),
                "1=1"
        );
    }
}
