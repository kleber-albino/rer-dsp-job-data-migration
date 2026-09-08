package br.car.dsp_batch.sync;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartedLevel3ContextSupportTest {

    @Test
    void merge_AccumulatesDistinctIds() {
        ExecutionContext context = new ExecutionContext();

        DepartedLevel3ContextSupport.merge(context, Set.of("l3-a"));
        DepartedLevel3ContextSupport.merge(context, Set.of("l3-b", "l3-a"));

        assertEquals(Set.of("l3-a", "l3-b"), DepartedLevel3ContextSupport.read(context));
    }

    @Test
    void read_ReturnsEmptyWhenUnset() {
        assertTrue(DepartedLevel3ContextSupport.read(new ExecutionContext()).isEmpty());
    }

    @Test
    void read_NormalizesStoredValues() {
        ExecutionContext context = new ExecutionContext();
        context.put(WatermarkContextKeys.DEPARTED_LEVEL_3_IDS, new LinkedHashSet<>(Set.of(" l3-a ", 42L)));

        assertEquals(Set.of("l3-a", "42"), DepartedLevel3ContextSupport.read(context));
    }
}
