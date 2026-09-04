package br.car.dsp_batch.sync;

/**
 * Job execution context keys used by watermark-based change detection and commit.
 */
public final class WatermarkContextKeys {

    public static final String HAS_CHANGES = "hasChanges";
    public static final String AFFECTED_BBOXES = "affectedBboxes";
    public static final String LAYER_NAME = "layerName";
    public static final String PROPOSED_WATERMARK = "proposedWatermark";
    /**
     * Watermark in force when the run started. Read after the job to know which window
     * was processed — {@link #PROPOSED_WATERMARK} already describes the new position.
     */
    public static final String PREVIOUS_WATERMARK = "previousWatermark";
    public static final String ORPHAN_CHECK_RAN = "orphanCheckRan";
    public static final String SYNC_KEY = "syncKey";
    public static final String SOURCE_TABLE = "sourceTable";

    private WatermarkContextKeys() {
    }
}
