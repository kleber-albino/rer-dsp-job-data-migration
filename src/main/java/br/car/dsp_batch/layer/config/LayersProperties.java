package br.car.dsp_batch.layer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geographic layers configured via YAML ({@code batch.layers}).
 */
@ConfigurationProperties(prefix = "batch")
@Getter
@Setter
public class LayersProperties {

    private List<LayerConfig> layers = new ArrayList<>();

    public List<LayerConfig> enabledLayers() {
        return layers.stream()
                .filter(LayerConfig::isEnabled)
                .toList();
    }

    public static final Set<String> RESERVED_PHYSICAL_TABLES = Set.of(
            "territory_level_1",
            "territory_level_2",
            "territory_level_3",
            "area_of_interest"
    );

    public void validate() {
        Set<String> keys = new HashSet<>();
        for (LayerConfig layer : layers) {
            layer.validate();
            String physical = layer.physicalTableName();
            if (RESERVED_PHYSICAL_TABLES.contains(physical)) {
                throw new IllegalStateException(
                        "Target table 'dsp." + physical
                                + "' is reserved for a fixed migration");
            }
            if (!keys.add(layer.resolveKey())) {
                throw new IllegalStateException(
                        "Duplicate target table 'dsp." + physical + "' for batch.layers");
            }
        }
    }
}
