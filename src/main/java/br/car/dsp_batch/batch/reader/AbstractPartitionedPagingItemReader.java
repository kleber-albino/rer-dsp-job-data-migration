package br.car.dsp_batch.batch.reader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.database.JdbcPagingItemReader;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Base paginated reader. When minId/maxId are present, it filters by the numeric range;
 * when they are null (alphanumeric column or no partitioning), it reads all records.
 */
@Slf4j
public abstract class AbstractPartitionedPagingItemReader<T> extends JdbcPagingItemReader<T> {

    protected AbstractPartitionedPagingItemReader(DataSource dataSource, Long minId, Long maxId, int pageSize) {
        super();
        this.setDataSource(dataSource);
        this.setPageSize(pageSize);

        if (minId != null && maxId != null) {
            log.info("Configuring reader with ID range: {}-{}", minId, maxId);
            Map<String, Object> parameterValues = new HashMap<>();
            parameterValues.put("minId", minId);
            parameterValues.put("maxId", maxId);
            this.setParameterValues(parameterValues);
        } else {
            log.info("Configuring reader without an ID range (full partition scan).");
        }
    }
}
