package br.car.dsp_batch.geofile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Turns {@code requires_s3_file_regeneration} on for the territories a migration run touched,
 * so {@code rer-dsp-job-geo-file-generation} republishes their download files.
 *
 * <p>Set-based on purpose: the affected rows can be millions and the flag is idempotent,
 * so a single UPDATE over the watermark window costs less than carrying ids through the job.
 */
@Slf4j
@Repository
public class GeoFileRegenerationFlagRepository {

    private final JdbcTemplate targetJdbcTemplate;

    public GeoFileRegenerationFlagRepository(
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate) {
        this.targetJdbcTemplate = targetJdbcTemplate;
    }

    /**
     * Flags the rows of a territory table changed after {@code previousWatermark}.
     * A null watermark means first load — every row is flagged.
     */
    public int markChangedTerritories(String territoryTable, Instant previousWatermark) {
        if (previousWatermark == null) {
            return targetJdbcTemplate.update(
                    "UPDATE " + territoryTable + " SET requires_s3_file_regeneration = TRUE");
        }
        OffsetDateTime since = toOffsetDateTime(previousWatermark);
        return targetJdbcTemplate.update(
                "UPDATE " + territoryTable
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE created_at > ? OR updated_at > ?",
                since,
                since);
    }

    /**
     * Flags the level 3 parents of the AOIs changed after {@code previousWatermark}, and the
     * level 2 parents of those level 3s — an AOI edit changes both territorial downloads.
     */
    public AreaOfInterestFlagResult markParentsOfChangedAreasOfInterest(
            String areaOfInterestTable,
            String territoryLevel3Column,
            String territoryLevel3Table,
            String territoryLevel2Table,
            Instant previousWatermark) {
        String changedLevel3Ids = "SELECT DISTINCT aoi." + territoryLevel3Column
                + " FROM " + areaOfInterestTable + " aoi"
                + " WHERE aoi." + territoryLevel3Column + " IS NOT NULL";
        Object[] level3Args;
        if (previousWatermark == null) {
            level3Args = new Object[0];
        } else {
            OffsetDateTime since = toOffsetDateTime(previousWatermark);
            changedLevel3Ids += " AND (aoi.created_at > ? OR aoi.updated_at > ?)";
            level3Args = new Object[]{since, since};
        }

        int level3Flagged = targetJdbcTemplate.update(
                "UPDATE " + territoryLevel3Table
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE id IN (" + changedLevel3Ids + ")",
                level3Args);
        int level2Flagged = targetJdbcTemplate.update(
                "UPDATE " + territoryLevel2Table
                        + " SET requires_s3_file_regeneration = TRUE"
                        + " WHERE id IN ("
                        + "  SELECT DISTINCT t3.parent_id FROM " + territoryLevel3Table + " t3"
                        + "  WHERE t3.parent_id IS NOT NULL AND t3.id IN (" + changedLevel3Ids + ")"
                        + ")",
                level3Args);
        return new AreaOfInterestFlagResult(level2Flagged, level3Flagged);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** Rows flagged per level by an AOI run. */
    public record AreaOfInterestFlagResult(int level2Flagged, int level3Flagged) {
    }
}
