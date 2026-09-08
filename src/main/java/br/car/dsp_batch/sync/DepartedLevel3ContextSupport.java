package br.car.dsp_batch.sync;

import org.springframework.batch.item.ExecutionContext;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads and merges {@link WatermarkContextKeys#DEPARTED_LEVEL_3_IDS} in the job execution context.
 */
public final class DepartedLevel3ContextSupport {

    private DepartedLevel3ContextSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Set<String> read(ExecutionContext context) {
        if (context == null) {
            return Set.of();
        }
        Object value = context.get(WatermarkContextKeys.DEPARTED_LEVEL_3_IDS);
        if (!(value instanceof Set<?> set) || set.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Object item : set) {
            if (item != null) {
                String id = item.toString().trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids.isEmpty() ? Set.of() : Set.copyOf(ids);
    }

    public static void merge(ExecutionContext context, Collection<String> level3Ids) {
        if (context == null || level3Ids == null || level3Ids.isEmpty()) {
            return;
        }
        Set<String> merged = new LinkedHashSet<>(read(context));
        for (String id : level3Ids) {
            if (id != null && !id.isBlank()) {
                merged.add(id.trim());
            }
        }
        if (!merged.isEmpty()) {
            context.put(WatermarkContextKeys.DEPARTED_LEVEL_3_IDS, merged);
        }
    }
}
