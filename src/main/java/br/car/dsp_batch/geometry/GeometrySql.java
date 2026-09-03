package br.car.dsp_batch.geometry;

/**
 * Shared PostGIS SQL fragments for geometry validation and map-ready 2D output.
 * <p>
 * Exhibition / WMS targets use 2D columns. Geometries with Z or M (e.g. Point Z)
 * are flattened with {@code ST_Force2D}; XY is kept, elevation/measure is dropped.
 * Point, LineString, Polygon and multi variants are accepted (no polygon-only extract).
 */
public final class GeometrySql {

    private GeometrySql() {
    }

    /**
     * Predicate: column is present, valid and not empty.
     *
     * @param geometryColumn SQL identifier of the geometry column (already safe/quoted if needed)
     */
    public static String validNonEmptyPredicate(String geometryColumn) {
        requireGeometryExpression(geometryColumn);
        return "(" + geometryColumn + " IS NOT NULL"
                + " AND NOT ST_IsEmpty(ST_MakeValid(" + geometryColumn + ")))";
    }

    /**
     * Wraps an expression with {@code ST_Force2D} (idempotent for already-2D geometries).
     */
    public static String force2d(String geometryExpression) {
        requireGeometryExpression(geometryExpression);
        return "ST_Force2D(" + geometryExpression + ")";
    }

    /**
     * GeoJSON text of a 2D geometry (Z/M dropped).
     */
    public static String asGeoJsonText2d(String geometryExpression) {
        return "public.ST_AsGeoJSON(" + force2d(geometryExpression) + ")::text";
    }

    /**
     * Placeholder expression for UPSERT: GeoJSON param → 2D geometry with SRID.
     */
    public static String geomFromGeoJsonParam2d(int srid) {
        if (srid <= 0) {
            throw new IllegalArgumentException("srid must be a positive integer");
        }
        return String.format(
                "public.ST_SetSRID(public.ST_Force2D(public.ST_GeomFromGeoJSON(?)), %d)",
                srid
        );
    }

    private static void requireGeometryExpression(String geometryExpression) {
        if (geometryExpression == null || geometryExpression.isBlank()) {
            throw new IllegalArgumentException("geometry expression must not be blank");
        }
    }
}
