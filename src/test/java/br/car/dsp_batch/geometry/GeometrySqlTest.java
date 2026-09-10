package br.car.dsp_batch.geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometrySqlTest {

    @Test
    void validNonEmptyPredicate_AcceptsAnyGeometryType() {
        String predicate = GeometrySql.validNonEmptyPredicate("geom");

        assertEquals("(geom IS NOT NULL AND NOT ST_IsEmpty(ST_MakeValid(geom)))", predicate);
        assertFalse(predicate.contains("CollectionExtract"));
        assertFalse(predicate.contains(", 3)"));
    }

    @Test
    void validNonEmptyPredicate_UsesProvidedColumnName() {
        String predicate = GeometrySql.validNonEmptyPredicate("geometry");

        assertTrue(predicate.contains("geometry IS NOT NULL"));
        assertTrue(predicate.contains("ST_MakeValid(geometry)"));
    }

    @Test
    void validNonEmptyPredicate_RejectsBlankColumn() {
        assertThrows(IllegalArgumentException.class, () -> GeometrySql.validNonEmptyPredicate(" "));
        assertThrows(IllegalArgumentException.class, () -> GeometrySql.validNonEmptyPredicate(null));
    }

    @Test
    void force2d_WrapsExpression() {
        assertEquals("ST_Force2D(geom)", GeometrySql.force2d("geom"));
    }

    @Test
    void transform_WrapsExpressionWithTargetSrid() {
        assertEquals(
                "public.ST_Transform(the_geom, 4674)",
                GeometrySql.transform("the_geom", 4674)
        );
    }

    @Test
    void transform_RejectsInvalidSrid() {
        assertThrows(IllegalArgumentException.class, () -> GeometrySql.transform("geom", 0));
    }

    @Test
    void asGeoJsonText2d_Forces2dBeforeGeoJson() {
        String sql = GeometrySql.asGeoJsonText2d("geom");

        assertEquals("public.ST_AsGeoJSON(ST_Force2D(geom))::text", sql);
        assertTrue(sql.contains("ST_Force2D"));
    }

    @Test
    void geomFromGeoJsonParam2d_Forces2dOnInsert() {
        String sql = GeometrySql.geomFromGeoJsonParam2d(4674);

        assertEquals(
                "public.ST_SetSRID(public.ST_Force2D(public.ST_GeomFromGeoJSON(?)), 4674)",
                sql
        );
    }

    @Test
    void geomFromGeoJsonParam2d_RejectsInvalidSrid() {
        assertThrows(IllegalArgumentException.class, () -> GeometrySql.geomFromGeoJsonParam2d(0));
    }
}
