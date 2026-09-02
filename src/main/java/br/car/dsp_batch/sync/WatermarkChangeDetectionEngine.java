package br.car.dsp_batch.sync;

import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.temporal.TemporalType;
import br.car.dsp_batch.temporal.WatermarkTemporalBridge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Shared engine for incremental watermark change detection.
 * Admin units/AOI and layers delegate delta and orphan logic here.
 */
@Slf4j
@Service
public class WatermarkChangeDetectionEngine {

    private final SyncStateRepository syncStateRepository;

    public WatermarkChangeDetectionEngine(SyncStateRepository syncStateRepository) {
        this.syncStateRepository = syncStateRepository;
    }

    public void detectChanges(JdbcTemplate sourceJdbc,
                              JdbcTemplate geoTargetJdbc,
                              JdbcTemplate businessTargetJdbc,
                              WatermarkTableSpec spec,
                              ChunkContext chunkContext) {
        requireSpec(spec);
        log.info("Starting change detection for table={} syncKey={}",
                spec.sourceTable(), spec.syncKey());
        logDateGranularityWarnings(spec);

        Optional<SyncState> state = syncStateRepository.findBySyncKey(spec.syncKey());
        Instant watermark = state.map(SyncState::watermarkLastEventAt).orElse(null);
        boolean runOrphanCheck = shouldRunOrphanCheck(state.orElse(null));

        List<String> deltaBboxes = new ArrayList<>();
        Instant maxLastEventAt = collectDeltaBboxes(sourceJdbc, spec, watermark, deltaBboxes);

        List<String> affectedBboxes = new ArrayList<>(deltaBboxes);
        if (runOrphanCheck) {
            log.info("Running orphan check for {}", spec.sourceTable());
            affectedBboxes.addAll(deleteOrphans(sourceJdbc, geoTargetJdbc, businessTargetJdbc, spec));
        }

        var jobContext = chunkContext.getStepContext()
                .getStepExecution()
                .getJobExecution()
                .getExecutionContext();

        jobContext.putString(WatermarkContextKeys.SYNC_KEY, spec.syncKey());
        jobContext.putString(WatermarkContextKeys.SOURCE_TABLE, spec.sourceTable());
        jobContext.put(WatermarkContextKeys.ORPHAN_CHECK_RAN, runOrphanCheck);

        if (maxLastEventAt != null) {
            jobContext.putString(WatermarkContextKeys.PROPOSED_WATERMARK, maxLastEventAt.toString());
        }

        boolean hasDelta = !deltaBboxes.isEmpty();
        if (!hasDelta && affectedBboxes.isEmpty()) {
            log.info("No changes detected in {} (watermark={})", spec.sourceTable(), watermark);
            jobContext.put(WatermarkContextKeys.HAS_CHANGES, false);
        } else if (!hasDelta) {
            log.info("Only orphan deletions in {} — skipping UPSERT (watermark={})",
                    spec.sourceTable(), watermark);
            jobContext.put(WatermarkContextKeys.HAS_CHANGES, false);
            jobContext.put(WatermarkContextKeys.AFFECTED_BBOXES, affectedBboxes);
            jobContext.put(WatermarkContextKeys.LAYER_NAME, spec.layerName());
        } else {
            log.info("Detected {} delta areas in {} (watermark={}, proposed={})",
                    deltaBboxes.size(), spec.sourceTable(), watermark, maxLastEventAt);
            jobContext.put(WatermarkContextKeys.HAS_CHANGES, true);
            jobContext.put(WatermarkContextKeys.AFFECTED_BBOXES, affectedBboxes);
            jobContext.put(WatermarkContextKeys.LAYER_NAME, spec.layerName());
        }
    }

    public boolean shouldRunOrphanCheck(SyncState state) {
        if (state == null || state.watermarkLastEventAt() == null) {
            return true;
        }
        Instant lastCheck = state.lastOrphanCheckAt();
        if (lastCheck == null) {
            return true;
        }
        return lastCheck.isBefore(Instant.now().minus(WatermarkSettings.ORPHAN_CHECK_INTERVAL));
    }

    Instant collectDeltaBboxes(JdbcTemplate sourceJdbc,
                               WatermarkTableSpec spec,
                               Instant watermark,
                               List<String> bboxes) {
        String pk = spec.sourcePrimaryKey();
        String geom = spec.sourceGeometryColumn();
        String creationDate = spec.sourceCreationDateColumn();
        String updatedAt = spec.sourceUpdatedAtColumn();
        String table = spec.sourceTable();
        int srid = spec.srid();

        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String configWhere = spec.whereClause();
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        StringBuilder where = new StringBuilder("WHERE ").append(validGeomFilter);
        String changeFilter = WatermarkSql.buildChangeDetectionFilter(
                spec.creationDateColumn(), spec.updatedAtColumn(), watermark);
        where.append(" AND ").append(changeFilter);
        if (hasConfigWhere) {
            where.append(" AND (").append(configWhere).append(")");
        }

        String geom2d = GeometrySql.force2d(geom);
        String innerSelectColumns = pk + ", " + creationDate + " AS feature_created_at";
        String outerSelectColumns = pk + ", feature_created_at";
        if (updatedAt != null) {
            innerSelectColumns += ", " + updatedAt + " AS feature_updated_at";
            outerSelectColumns += ", feature_updated_at";
        }
        String sql = String.format(
                "SELECT %s, "
                        + "ST_XMin(env3857) as minx, "
                        + "ST_YMin(env3857) as miny, "
                        + "ST_XMax(env3857) as maxx, "
                        + "ST_YMax(env3857) as maxy "
                        + "FROM ("
                        + "  SELECT %s, "
                        + "  ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) as env3857 "
                        + "  FROM %s %s"
                        + ") t",
                outerSelectColumns,
                innerSelectColumns,
                geom2d,
                srid,
                table,
                where
        );

        AtomicReference<Instant> maxLastEventAt = new AtomicReference<>();
        sourceJdbc.query(sql, rs -> {
            Instant creationInstant = WatermarkTemporalBridge.readInstant(
                    rs, "feature_created_at", spec.creationDateColumn());
            Instant updatedInstant = updatedAt == null ? null : WatermarkTemporalBridge.readInstant(
                    rs, "feature_updated_at", spec.updatedAtColumn());
            Instant eventInstant = WatermarkTemporalBridge.maxEventInstant(
                    creationInstant, updatedInstant);
            if (eventInstant != null) {
                maxLastEventAt.updateAndGet(current ->
                        current == null || eventInstant.isAfter(current) ? eventInstant : current);
            }
            bboxes.add(formatBbox(
                    rs.getDouble("minx"),
                    rs.getDouble("miny"),
                    rs.getDouble("maxx"),
                    rs.getDouble("maxy")));
        });

        log.info("Source delta: {} record(s) after watermark {}", bboxes.size(), watermark);
        return maxLastEventAt.get();
    }

    private void logDateGranularityWarnings(WatermarkTableSpec spec) {
        if (spec.creationDateColumn().sourceType() == TemporalType.DATE) {
            log.warn(
                    "creation-date-column '{}' on {} is DATE — watermark granularity is daily "
                            + "(source-timezone={})",
                    spec.sourceCreationDateColumn(),
                    spec.sourceTable(),
                    spec.creationDateColumn().policy().zoneIdOrNull());
        }
        if (spec.hasUpdatedAtColumn()
                && spec.updatedAtColumn().sourceType() == TemporalType.DATE) {
            log.warn(
                    "updated-at-column '{}' on {} is DATE — watermark granularity is daily "
                            + "(source-timezone={})",
                    spec.sourceUpdatedAtColumn(),
                    spec.sourceTable(),
                    spec.updatedAtColumn().policy().zoneIdOrNull());
        }
    }

    private List<String> deleteOrphans(JdbcTemplate sourceJdbc,
                                       JdbcTemplate geoTargetJdbc,
                                       JdbcTemplate businessTargetJdbc,
                                       WatermarkTableSpec spec) {
        Set<Object> sourceIds = fetchSourceIds(sourceJdbc, spec);
        TargetIdsAndBboxes geoTarget = fetchTargetIdsAndBboxes(
                geoTargetJdbc,
                spec.geoTargetTable(),
                spec.geoTargetPrimaryKey(),
                spec.geoTargetGeometryColumn(),
                spec.srid()
        );

        Set<Object> orphans = new HashSet<>(geoTarget.ids());
        orphans.removeAll(sourceIds);

        if (orphans.isEmpty()) {
            log.info("Orphan check: no deleted records for {}", spec.geoTargetTable());
            return List.of();
        }

        List<String> orphanBboxes = new ArrayList<>();
        for (Object id : orphans) {
            String bbox = geoTarget.bboxesById().get(id);
            if (bbox != null) {
                orphanBboxes.add(bbox);
            }
            log.warn("DELETED orphan: id={}", id);
        }

        deleteRemovedRecords(
                geoTargetJdbc, spec.geoTargetTable(), spec.geoTargetPrimaryKey(), orphans);
        if (businessTargetJdbc != null
                && spec.businessTargetTable() != null
                && !spec.businessTargetTable().isBlank()) {
            deleteRemovedRecords(
                    businessTargetJdbc,
                    spec.businessTargetTable(),
                    spec.businessTargetPrimaryKey(),
                    orphans);
        }
        return orphanBboxes;
    }

    private Set<Object> fetchSourceIds(JdbcTemplate sourceJdbc, WatermarkTableSpec spec) {
        String pk = spec.sourcePrimaryKey();
        String geom = spec.sourceGeometryColumn();
        String table = spec.sourceTable();
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String configWhere = spec.whereClause();
        boolean hasConfigWhere = configWhere != null
                && !configWhere.isBlank()
                && !"1=1".equals(configWhere.trim());

        StringBuilder sql = new StringBuilder("SELECT ").append(pk)
                .append(" FROM ").append(table)
                .append(" WHERE ").append(validGeomFilter);
        if (hasConfigWhere) {
            sql.append(" AND (").append(configWhere).append(")");
        }

        Set<Object> ids = new HashSet<>();
        RowCallbackHandler handler = rs -> ids.add(normalizeId(rs.getObject(pk)));
        sourceJdbc.query(sql.toString(), handler);
        log.info("Orphan check source ids: {}", ids.size());
        return ids;
    }

    private TargetIdsAndBboxes fetchTargetIdsAndBboxes(JdbcTemplate targetJdbc,
                                                       String table,
                                                       String pk,
                                                       String geom,
                                                       int srid) {
        String validGeomFilter = GeometrySql.validNonEmptyPredicate(geom);
        String geom2d = GeometrySql.force2d(geom);

        String sql = String.format(
                "SELECT %s, "
                        + "ST_XMin(env3857) as minx, "
                        + "ST_YMin(env3857) as miny, "
                        + "ST_XMax(env3857) as maxx, "
                        + "ST_YMax(env3857) as maxy "
                        + "FROM ("
                        + "  SELECT %s, "
                        + "  ST_Transform(ST_Envelope(ST_SetSRID(%s, %d)), 3857) as env3857 "
                        + "  FROM %s WHERE %s"
                        + ") t",
                pk, pk, geom2d, srid, table, validGeomFilter
        );

        Set<Object> ids = new HashSet<>();
        Map<Object, String> bboxesById = new java.util.HashMap<>();
        targetJdbc.query(sql, rs -> {
            Object id = normalizeId(rs.getObject(pk));
            ids.add(id);
            bboxesById.put(id, formatBbox(
                    rs.getDouble("minx"),
                    rs.getDouble("miny"),
                    rs.getDouble("maxx"),
                    rs.getDouble("maxy")));
        });
        log.info("Orphan check target ids: {}", ids.size());
        return new TargetIdsAndBboxes(ids, bboxesById);
    }

    private void deleteRemovedRecords(JdbcTemplate targetJdbc,
                                      String table,
                                      String pk,
                                      Set<Object> idsToDelete) {
        if (idsToDelete.isEmpty()) {
            return;
        }

        String placeholders = idsToDelete.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = String.format("DELETE FROM %s WHERE %s IN (%s)", table, pk, placeholders);

        int deleted = targetJdbc.update(sql, idsToDelete.toArray());
        log.warn("Deleted {} inactive records from {}: {}", deleted, table, idsToDelete.size());
    }

    public Object normalizeId(Object id) {
        if (id == null) {
            return null;
        }
        if (id instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        return id.toString().trim();
    }

    private static void requireSpec(WatermarkTableSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("WatermarkTableSpec is required");
        }
        if (spec.syncKey() == null || spec.syncKey().isBlank()) {
            throw new IllegalArgumentException("WATERMARK requires sync-key");
        }
        if (spec.creationDateColumn() == null) {
            throw new IllegalArgumentException(
                    "WATERMARK requires creation-date-column for table " + spec.sourceTable());
        }
    }

    private String formatBbox(double minX, double minY, double maxX, double maxY) {
        return String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", minX, minY, maxX, maxY);
    }

    private record TargetIdsAndBboxes(Set<Object> ids, Map<Object, String> bboxesById) {
    }
}
