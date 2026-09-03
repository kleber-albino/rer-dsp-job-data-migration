package br.car.dsp_batch.temporal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalTypeClassifierTest {

    @ParameterizedTest
    @CsvSource({
            "date, DATE",
            "timestamp, TIMESTAMP",
            "timestamptz, TIMESTAMPTZ",
            "time, TIME",
            "timetz, TIMETZ",
            "varchar, UNSUPPORTED",
            "text, UNSUPPORTED"
    })
    void classify_MapsKnownUdts(String udt, TemporalType expected) {
        assertEquals(expected, TemporalTypeClassifier.classify(udt));
    }

    @Test
    void watermarkSupport_Flags() {
        assertTrue(TemporalType.TIMESTAMPTZ.isWatermarkSupported());
        assertTrue(TemporalType.TIMESTAMP.isWatermarkSupported());
        assertTrue(TemporalType.DATE.isWatermarkSupported());
        assertFalse(TemporalType.TIME.isWatermarkSupported());
        assertFalse(TemporalType.TIMETZ.isWatermarkSupported());
        assertFalse(TemporalType.UNSUPPORTED.isWatermarkSupported());
    }
}
