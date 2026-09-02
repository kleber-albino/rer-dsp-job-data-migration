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

    public void validate() {
        Set<String> keys = new HashSet<>();
        for (LayerConfig layer : layers) {
            layer.validate();
            String key = layer.resolveKey();
            if (!keys.add(key)) {
                throw new IllegalStateException(
                        "Duplicate target table 'dsp."
                                + layer.resolveSourceTable().table()
                                + "' for batch.layers (same table name from different sources)");
            }
        }
    }
}
