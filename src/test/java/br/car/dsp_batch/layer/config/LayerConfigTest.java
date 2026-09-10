package br.car.dsp_batch.layer.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerConfigTest {

    @Test
    void resolveTargetTable_WithoutLayerName_UsesSourceTableName() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("usr_geocar_aplicacao.parcelas_rurais");

        assertEquals("dsp", config.resolveTargetTable().schema());
        assertEquals("parcelas_rurais", config.resolveTargetTable().table());
        assertEquals("dsp.parcelas_rurais", config.resolveTargetTable().qualified());
    }

    @Test
    void resolveTargetTable_WithLayerName_UsesNormalizedLayerName() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("public.features");
        config.setLayerName("tipo-a");

        assertEquals("dsp.tipo_a", config.resolveTargetTable().qualified());
        assertEquals("dsp_tipo_a", config.resolveKey());
    }

    @Test
    void resolveKey_DerivesFromPhysicalLayerName() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("source_schema.example_features");

        assertEquals("dsp_example_features", config.resolveKey());
    }

    @Test
    void resolveLayerName_DefaultsToSourceTableName() {
        LayerConfig config = new LayerConfig();
        config.setSourceTable("cliente.zona_app");

        assertEquals("zona_app", config.resolveLayerName());
    }

    @Test
    void canonicalConstants_MatchTargetColumnNames() {
        assertEquals("id", LayerConfig.ID_COLUMN);
        assertEquals("area_of_interest_id", LayerConfig.AREA_OF_INTEREST_ID_COLUMN);
        assertEquals("geom", LayerConfig.GEOMETRY_COLUMN);
        assertEquals("created_at", LayerConfig.CREATED_AT_COLUMN);
        assertEquals("updated_at", LayerConfig.UPDATED_AT_COLUMN);
        assertEquals("label", LayerConfig.LABEL_COLUMN);
    }

    @Test
    void resolveMigratedSourceColumns_IncludesRequiredAndExtras() {
        LayerConfig config = new LayerConfig();
        config.setPrimaryKey("source_pk");
        config.setAreaOfInterestIdColumn("source_aoi_fk");
        config.setCreationDateColumn("source_created_at");
        config.setUpdatedAtColumn("source_updated_at");
        config.setLabelColumn("source_name");
        config.setGeometryColumn("source_geom");
        config.setAdditionalColumns(List.of("codigo", "area_ha"));

        assertEquals(
                List.of(
                        "source_pk",
                        "source_aoi_fk",
                        "source_created_at",
                        "source_updated_at",
                        "source_name",
                        "source_geom",
                        "codigo",
                        "area_ha"
                ),
                config.resolveMigratedSourceColumns()
        );
    }
}
