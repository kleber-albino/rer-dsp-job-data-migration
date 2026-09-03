package br.car.dsp_batch.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs chunk progress and basic system resources for parallelized steps.
 */
@Slf4j
@Component
public class ParallelizationMonitorListener implements ChunkListener {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public void beforeChunk(ChunkContext context) {
        String threadName = Thread.currentThread().getName();
        String stepName = context.getStepContext().getStepName();
        String jobName = context.getStepContext().getJobName();

        log.debug("Job: {} | Step: {} | Thread: {} | Starting chunk at {}",
                jobName, stepName, threadName, LocalDateTime.now().format(FORMATTER));

        logSystemResources();
    }

    @Override
    public void afterChunk(ChunkContext context) {
        String threadName = Thread.currentThread().getName();
        String stepName = context.getStepContext().getStepName();
        String jobName = context.getStepContext().getJobName();

        long commitCount = context.getStepContext().getStepExecution().getCommitCount();
        long readCount = context.getStepContext().getStepExecution().getReadCount();
        long writeCount = context.getStepContext().getStepExecution().getWriteCount();

        log.info("Job: {} | Step: {} | Thread: {} | Completed chunk {} | Read: {} | Written: {} at {}",
                jobName, stepName, threadName, commitCount, readCount, writeCount,
                LocalDateTime.now().format(FORMATTER));
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        String threadName = Thread.currentThread().getName();
        String stepName = context.getStepContext().getStepName();
        String jobName = context.getStepContext().getJobName();

        log.error("Job: {} | Step: {} | Thread: {} | Chunk ERROR at {}",
                jobName, stepName, threadName, LocalDateTime.now().format(FORMATTER));
    }

    private void logSystemResources() {
        var runtime = Runtime.getRuntime();
        var memoryMBean = ManagementFactory.getMemoryMXBean();
        var threadMBean = ManagementFactory.getThreadMXBean();

        long usedMemory = memoryMBean.getHeapMemoryUsage().getUsed() / 1024 / 1024;
        long maxMemory = memoryMBean.getHeapMemoryUsage().getMax() / 1024 / 1024;
        int activeThreads = threadMBean.getThreadCount();
        int availableProcessors = runtime.availableProcessors();

        log.debug("Memory: {}MB/{}MB | Threads: {} | CPU Cores: {}",
                usedMemory, maxMemory, activeThreads, availableProcessors);
    }
}
