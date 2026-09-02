package br.car.dsp_batch.batch.listener;

import br.car.dsp_batch.sync.WatermarkContextKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Handles post-job cache refresh based on affected bounding boxes collected
 * during change detection.
 *
 * TODO: Implement real GeoServer Exhibition cache invalidation (e.g. GWC truncate
 * by layer name + affected bboxes) using {@code layerName} from the job execution
 * context. Integration with an external cache service can be plugged in later
 * without changing the job flow. Today this listener only logs the request.
 */
@Slf4j
@Component
public class GeoCacheUpdateListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Job {} started — preparing cache update collection", jobExecution.getJobInstance().getJobName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterJob(JobExecution jobExecution) {
        Boolean hasChanges = (Boolean) jobExecution.getExecutionContext()
                .get(WatermarkContextKeys.HAS_CHANGES);

        if (hasChanges == null || !hasChanges) {
            log.info("No cache update required for job {}", jobExecution.getJobInstance().getJobName());
            return;
        }

        List<String> bboxes = (List<String>) jobExecution.getExecutionContext()
                .get(WatermarkContextKeys.AFFECTED_BBOXES);
        String layerName = jobExecution.getExecutionContext()
                .getString(WatermarkContextKeys.LAYER_NAME);

        if (bboxes == null || bboxes.isEmpty()) {
            log.warn("Changes were detected but no bounding boxes were collected.");
            return;
        }

        if (layerName == null || layerName.isEmpty()) {
            log.error("Layer name was not set in the job execution context.");
            return;
        }

        log.info("Cache update requested for layer '{}' with {} affected areas",
                layerName, bboxes.size());
        for (String bbox : bboxes) {
            log.debug("Cache update bbox: {}", bbox);
        }
        // TODO: Call GeoServer Exhibition (GWC / seed truncate) for layerName + bboxes.
    }
}
