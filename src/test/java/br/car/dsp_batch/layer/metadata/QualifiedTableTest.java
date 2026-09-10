package br.car.dsp_batch.layer.metadata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QualifiedTableTest {

    @Test
    void parse_AcceptsSchemaAndTable() {
        QualifiedTable table = QualifiedTable.parse("usr_geocar_aplicacao.imovel");

        assertEquals("usr_geocar_aplicacao", table.schema());
        assertEquals("imovel", table.table());
        assertEquals("usr_geocar_aplicacao.imovel", table.qualified());
    }

    @Test
    void parse_RejectsInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> QualifiedTable.parse("sem_schema"));
        assertThrows(IllegalArgumentException.class, () -> QualifiedTable.parse(""));
        assertThrows(IllegalArgumentException.class, () -> QualifiedTable.parse(".tabela"));
        assertThrows(IllegalArgumentException.class, () -> QualifiedTable.parse("schema."));
    }

    @Test
    void parse_RejectsMoreThanOneDot() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> QualifiedTable.parse("foo.bar.baz")
        );
        assertEquals("Expected schema.table (exactly one '.'), got: foo.bar.baz", ex.getMessage());
    }
}
