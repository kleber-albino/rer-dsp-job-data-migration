package br.car.dsp_batch.batch.config;

import br.car.dsp_batch.sync.WatermarkContextKeys;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.stereotype.Component;

/**
 * Decides whether processing should continue based on change detection results.
 */
@Component
public class ChangeDecider implements JobExecutionDecider {

    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
        Boolean hasChanges = (Boolean) jobExecution.getExecutionContext()
                .get(WatermarkContextKeys.HAS_CHANGES);

        if (hasChanges != null && hasChanges) {
            return new FlowExecutionStatus("PROCESS");
        }

        return new FlowExecutionStatus("SKIP");
    }
}
