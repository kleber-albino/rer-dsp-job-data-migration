package br.car.dsp_batch.layer.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayersPropertiesTest {

    @Test
    void validate_AcceptsRequiredColumnsAndExtras() {
        LayersProperties properties = new LayersProperties();
        properties.getLayers().add(validLayer());

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validate_RejectsMissingPrimaryKey() {
        LayerConfig layer = validLayer();
        layer.setPrimaryKey(null);
        LayersProperties properties = propertiesWith(layer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("primary-key"));
    }

    @Test
    void validate_AcceptsMissingLabelColumn() {
        LayerConfig layer = validLayer();
        layer.setLabelColumn(" ");
        LayersProperties properties = propertiesWith(layer);

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validate_RejectsMissingCreationDateColumn() {
        LayerConfig layer = validLayer();
        layer.setCreationDateColumn(null);
        LayersProperties properties = propertiesWith(layer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("creation-date-column"));
    }

    @Test
    void validate_RejectsMissingGeometryColumn() {
        LayerConfig layer = validLayer();
        layer.setGeometryColumn(null);
        LayersProperties properties = propertiesWith(layer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("geometry-column"));
    }

    @Test
    void validate_RejectsAdditionalColumnCollidingWithCanonicalName() {
        LayerConfig layer = validLayer();
        layer.setAdditionalColumns(List.of("label"));
        LayersProperties properties = propertiesWith(layer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("canonical target column"));
    }

    @Test
    void validate_RejectsAdditionalColumnDuplicatingRequiredMapping() {
        LayerConfig layer = validLayer();
        layer.setAdditionalColumns(List.of("source_name"));
        LayersProperties properties = propertiesWith(layer);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("duplicates a required column"));
    }

    @Test
    void validate_RejectsDuplicateTargetTableNames() {
        LayersProperties properties = new LayersProperties();

        LayerConfig first = validLayer();
        first.setSourceTable("schema_a.mesma");

        LayerConfig second = validLayer();
        second.setSourceTable("schema_b.mesma");

        properties.getLayers().add(first);
        properties.getLayers().add(second);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertTrue(ex.getMessage().contains("Duplicate target table"));
    }

    private static LayersProperties propertiesWith(LayerConfig layer) {
        LayersProperties properties = new LayersProperties();
        properties.getLayers().add(layer);
        return properties;
    }

    private static LayerConfig validLayer() {
        LayerConfig layer = new LayerConfig();
        layer.setSourceTable("src_schema.tabela");
        layer.setPrimaryKey("source_pk");
        layer.setAreaOfInterestIdColumn("cod_imovel");
        layer.setCreationDateColumn("data_criacao");
        layer.setUpdatedAtColumn("data_atualizacao");
        layer.setLabelColumn("source_name");
        layer.setGeometryColumn("source_geom");
        layer.setAdditionalColumns(List.of("codigo", "area_ha"));
        return layer;
    }
}
