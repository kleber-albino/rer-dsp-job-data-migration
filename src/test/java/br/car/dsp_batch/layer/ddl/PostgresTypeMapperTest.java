package br.car.dsp_batch.layer.ddl;

import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PostgresTypeMapperTest {

    private final PostgresTypeMapper mapper = new PostgresTypeMapper();

    @Test
    void toDdlType_ReturnsNullForGeometry() {
        ColumnMetadata geometry = new ColumnMetadata(
                "geom", "geometry", null, null, null, true, true);
        assertNull(mapper.toDdlType(geometry));
    }

    @ParameterizedTest
    @CsvSource({
            "varchar, 80, varchar(80)",
            "varchar, , varchar",
            "int4, , integer",
            "int8, , bigint",
            "bool, , boolean",
            "text, , text",
            "uuid, , uuid"
    })
    void toDdlType_MapsCommonTypes(String udtName, String lengthRaw, String expected) {
        Integer length = lengthRaw == null || lengthRaw.isBlank()
                ? null
                : Integer.parseInt(lengthRaw);
        ColumnMetadata column = new ColumnMetadata(
                "col", udtName, length, null, null, true, false);
        assertEquals(expected, mapper.toDdlType(column));
    }

    @Test
    void toDdlType_MapsNumericWithScale() {
        ColumnMetadata column = new ColumnMetadata(
                "area", "numeric", null, 12, 4, true, false);
        assertEquals("numeric(12,4)", mapper.toDdlType(column));
    }

    @Test
    void toDdlType_MapsNumericWithoutScale() {
        ColumnMetadata column = new ColumnMetadata(
                "area", "numeric", null, 10, 0, true, false);
        assertEquals("numeric(10)", mapper.toDdlType(column));
    }

    @Test
    void toDdlType_PreservesTemporalSemanticsForCommonColumns() {
        assertEquals("date", mapper.toDdlType(
                new ColumnMetadata("d", "date", null, null, null, true, false)));
        assertEquals("timestamp", mapper.toDdlType(
                new ColumnMetadata("t", "timestamp", null, null, null, true, false)));
        assertEquals("timestamptz", mapper.toDdlType(
                new ColumnMetadata("tz", "timestamptz", null, null, null, true, false)));
    }
}

