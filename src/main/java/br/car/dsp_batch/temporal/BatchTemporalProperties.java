package br.car.dsp_batch.temporal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Global temporal defaults for the batch job ({@code batch.source-timezone}).
 */
@Component
public class BatchTemporalProperties {

    private final String sourceTimezone;

    public BatchTemporalProperties(
            @Value("${batch.source-timezone:}") String sourceTimezone) {
        this.sourceTimezone = sourceTimezone == null || sourceTimezone.isBlank()
                ? null
                : sourceTimezone.trim();
    }

    public String getSourceTimezone() {
        return sourceTimezone;
    }

    public SourceTemporalPolicy resolvePolicy(String overrideTimezone, String configSource) {
        if (overrideTimezone != null && !overrideTimezone.isBlank()) {
            return SourceTemporalPolicy.parse(overrideTimezone, configSource);
        }
        if (sourceTimezone != null) {
            return SourceTemporalPolicy.parse(sourceTimezone, "batch.source-timezone");
        }
        return SourceTemporalPolicy.none();
    }
}
