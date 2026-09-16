package br.car.dsp_batch.kpi.service;

import br.car.dsp_batch.aoi.config.AreaOfInterestConfig;
import br.car.dsp_batch.geometry.GeometrySql;
import br.car.dsp_batch.kpi.config.ThemeKpiConfig;
import br.car.dsp_batch.kpi.conversion.AreaUnitConverter;
import br.car.dsp_batch.kpi.ddl.KpiMeasureTableDdlBuilder;
import br.car.dsp_batch.layer.config.LayerConfig;
import br.car.dsp_batch.layer.introspection.SchemaIntrospectionService;
import br.car.dsp_batch.layer.metadata.QualifiedTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Calculates AOI and theme KPI areas from geo-target geometries and persists them.
 */
@Slf4j
@Service
public class KpiCalculationService {

    private final JdbcTemplate targetJdbcTemplate;
    private final JdbcTemplate geoTargetJdbcTemplate;
    private final SchemaIntrospectionService schemaIntrospectionService;
    private final KpiMeasureTableDdlBuilder ddlBuilder;

    public KpiCalculationService(
            @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate,
            @Qualifier("geoTargetJdbcTemplate") JdbcTemplate geoTargetJdbcTemplate,
            SchemaIntrospectionService schemaIntrospectionService,
            KpiMeasureTableDdlBuilder ddlBuilder) {
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.geoTargetJdbcTemplate = geoTargetJdbcTemplate;
        this.schemaIntrospectionService = schemaIntrospectionService;
        this.ddlBuilder = ddlBuilder;
    }

    public void ensureKpiMeasureTable() {
        for (String statement : ddlBuilder.buildStatements()) {
            log.debug("Executing KPI DDL: {}", statement);
            targetJdbcTemplate.execute(statement);
        }
    }

    public void calculateAndPersist(String areaUnitOfMeasurement, List<ThemeKpiConfig> themes) {
        ensureKpiMeasureTable();
        updateAreaOfInterestAreas(areaUnitOfMeasurement);
        recalculateThemeMeasures(themes);
    }

    public void updateAreaOfInterestAreas(String areaUnitOfMeasurement) {
        QualifiedTable geoAoi = new QualifiedTable("dsp", "area_of_interest");

        if (!schemaIntrospectionService.tableExists(geoTargetJdbcTemplate, geoAoi)) {
            throw new IllegalStateException(
                    "Geo-target table dsp.area_of_interest not found. Run the AOI migration job first.");
        }

        String geomColumn = AreaOfInterestConfig.GEOMETRY_COLUMN;
        String areaExpr = GeometrySql.areaGeographySquareMetres(quote(geomColumn));

        List<Map<String, Object>> rows = geoTargetJdbcTemplate.queryForList(
                "SELECT " + quote("id") + " AS id, " + areaExpr + " AS area_m2 "
                        + "FROM " + geoAoi.qualified()
                        + " WHERE " + GeometrySql.validNonEmptyPredicate(quote(geomColumn))
        );

        if (rows.isEmpty()) {
            log.info("No AOI geometries found on geo-target; skipping area update");
            return;
        }

        String updateSql = "UPDATE dsp.area_of_interest SET " + quote("area") + " = ? WHERE "
                + quote("id") + " = ?";
        targetJdbcTemplate.batchUpdate(updateSql, rows, rows.size(), (ps, row) -> {
            BigDecimal areaM2 = toBigDecimal(row.get("area_m2"));
            BigDecimal converted = AreaUnitConverter.fromSquareMetres(areaM2, areaUnitOfMeasurement);
            ps.setBigDecimal(1, converted);
            ps.setString(2, String.valueOf(row.get("id")));
        });

        log.info("Updated area for {} AOI record(s) using unit '{}'", rows.size(), areaUnitOfMeasurement);
    }

    public void recalculateThemeMeasures(List<ThemeKpiConfig> themes) {
        targetJdbcTemplate.execute("TRUNCATE dsp.kpi_measure");

        if (themes == null || themes.isEmpty()) {
            log.info("No theme KPIs configured; kpi_measure truncated");
            return;
        }

        for (ThemeKpiConfig theme : themes) {
            persistThemeMeasures(theme);
        }
    }

    private void persistThemeMeasures(ThemeKpiConfig theme) {
        String layerName = theme.resolveLayerName();
        LayerConfig layerRef = new LayerConfig();
        layerRef.setLayerName(layerName);
        QualifiedTable layerTable = layerRef.resolveTargetTable();

        if (!schemaIntrospectionService.tableExists(geoTargetJdbcTemplate, layerTable)) {
            throw new IllegalStateException(
                    "Geo-target table " + layerTable.qualified()
                            + " not found for KPI layer-name '" + layerName
                            + "'. Ensure batch.layers migration created the layer table or disable the KPI.");
        }

        String geomColumn = LayerConfig.GEOMETRY_COLUMN;
        String aoiColumn = LayerConfig.AREA_OF_INTEREST_ID_COLUMN;
        String areaExpr = "COALESCE(SUM("
                + GeometrySql.areaGeographySquareMetres(quote(geomColumn)) + "), 0)";

        List<Map<String, Object>> rows = geoTargetJdbcTemplate.queryForList(
                "SELECT " + quote(aoiColumn) + " AS area_of_interest_id, " + areaExpr + " AS area_m2 "
                        + "FROM " + layerTable.qualified()
                        + " WHERE " + quote(aoiColumn) + " IS NOT NULL "
                        + "AND " + GeometrySql.validNonEmptyPredicate(quote(geomColumn))
                        + " GROUP BY " + quote(aoiColumn)
        );

        if (rows.isEmpty()) {
            log.info("No features found for KPI layer '{}'", layerName);
            return;
        }

        String insertSql = "INSERT INTO dsp.kpi_measure (area_of_interest_id, value, kpi_name) VALUES (?, ?, ?)";
        targetJdbcTemplate.batchUpdate(insertSql, rows, rows.size(), (ps, row) -> {
            BigDecimal areaM2 = toBigDecimal(row.get("area_m2"));
            BigDecimal converted = AreaUnitConverter.fromSquareMetres(areaM2, theme.resolveUnitOfMeasurement());
            ps.setString(1, String.valueOf(row.get("area_of_interest_id")));
            ps.setBigDecimal(2, converted);
            ps.setString(3, layerName);
        });

        log.info("Persisted {} KPI measure(s) for layer '{}'", rows.size(), layerName);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
