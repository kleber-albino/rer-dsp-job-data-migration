package br.car.dsp_batch.aoi.service;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.aoi.metadata.AreaOfInterestTableMetadata;
import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.sync.WatermarkSql;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects level 3 territories that AOIs are leaving before an incremental upsert runs.
 */
@Slf4j
@Service
public class AreaOfInterestDepartedTerritoryCollector {

    private static final int IN_CLAUSE_BATCH_SIZE = 500;

    public Set<String> collectFromDelta(JdbcTemplate sourceJdbc,
                                      JdbcTemplate geoTargetJdbc,
                                      AreaOfInterestTableMetadata metadata,
                                      Instant watermark) {
        Set<Object> deltaIds = fetchDeltaSourceIds(sourceJdbc, metadata, watermark);
        if (deltaIds.isEmpty()) {
            return Set.of();
        }

        String targetTable = metadata.qualifiedTargetTable();
        String targetPk = metadata.resolveTargetPrimaryKeyColumn();
        String territoryColumn = AreaOfInterestConfig.TERRITORY_LEVEL_3_ID_COLUMN;

        Set<String> departedLevel3Ids = new HashSet<>();
        List<Object> batch = new ArrayList<>(IN_CLAUSE_BATCH_SIZE);
        for (Object id : deltaIds) {
            batch.add(id);
            if (batch.size() >= IN_CLAUSE_BATCH_SIZE) {
                departedLevel3Ids.addAll(fetchDepartedLevel3Ids(
                        geoTargetJdbc, targetTable, targetPk, territoryColumn, batch));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            departedLevel3Ids.addAll(fetchDepartedLevel3Ids(
                    geoTargetJdbc, targetTable, targetPk, territoryColumn, batch));
        }

        log.info("Delta departed capture: {} level 3 territor(ies) for {} AOI id(s)",
                departedLevel3Ids.size(), deltaIds.size());
        return departedLevel3Ids.isEmpty() ? Set.of() : Set.copyOf(departedLevel3Ids);
    }

    private Set<Object> fetchDeltaSourceIds(JdbcTemplate sourceJdbc,
                                            AreaOfInterestTableMetadata metadata,
                                            Instant watermark) {
        String pk = metadata.primaryKeyColumn();
        String geom = metadata.geometryColumn();
        String table = metadata.qualifiedSourceTable();
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);

        StringBuilder where = new StringBuilder("WHERE ").append(validGeomFilter);
        String changeFilter = WatermarkSql.buildChangeDetectionFilter(
                metadata.creationDateColumn(), metadata.updatedAtColumn(), watermark);
        where.append(" AND ").append(changeFilter);

        String configWhere = metadata.whereClause();
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());
        if (hasConfigWhere) {
            where.append(" AND (").append(configWhere).append(")");
        }

        String sql = "SELECT " + pk + " FROM " + table + " " + where;
        Set<Object> ids = new HashSet<>();
        sourceJdbc.query(sql, (RowCallbackHandler) rs -> ids.add(normalizeId(rs.getObject(pk))));
        return ids;
    }

    private Set<String> fetchDepartedLevel3Ids(JdbcTemplate geoTargetJdbc,
                                               String targetTable,
                                               String targetPk,
                                               String territoryColumn,
                                               List<Object> deltaIds) {
        String placeholders = deltaIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("");
        String sql = String.format(
                "SELECT DISTINCT %s FROM %s WHERE %s IN (%s) AND %s IS NOT NULL",
                territoryColumn,
                targetTable,
                targetPk,
                placeholders,
                territoryColumn);

        Set<String> departedLevel3Ids = new HashSet<>();
        geoTargetJdbc.query(sql, rs -> {
            Object value = rs.getObject(1);
            if (value != null) {
                departedLevel3Ids.add(value.toString().trim());
            }
        }, deltaIds.toArray());
        return departedLevel3Ids;
    }

    private static Object normalizeId(Object id) {
        if (id == null) {
            return null;
        }
        if (id instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return id.toString().trim();
    }
}
