package br.car.dsp_batch.layer.ddl;

import br.car.dsp_batch.layer.metadata.ColumnMetadata;
import org.springframework.stereotype.Component;

/**
 * Maps PostgreSQL types discovered on the source to DDL types on the target.
 */
@Component
public class PostgresTypeMapper {

    public String toDdlType(ColumnMetadata column) {
        if (column.geometry()) {
            return null;
        }

        return switch (column.udtName()) {
            case "varchar", "bpchar" -> column.characterMaximumLength() != null
                    ? "varchar(" + column.characterMaximumLength() + ")"
                    : "varchar";
            case "numeric" -> toNumeric(column);
            case "int2" -> "smallint";
            case "int4" -> "integer";
            case "int8" -> "bigint";
            case "bool" -> "boolean";
            case "date" -> "date";
            case "timestamp" -> "timestamp";
            case "timestamptz" -> "timestamptz";
            case "text" -> "text";
            case "float8" -> "double precision";
            case "float4" -> "real";
            case "uuid" -> "uuid";
            case "json", "jsonb" -> column.udtName();
            default -> column.udtName();
        };
    }

    private String toNumeric(ColumnMetadata column) {
        Integer precision = column.numericPrecision();
        Integer scale = column.numericScale();
        if (precision == null) {
            return "numeric";
        }
        if (scale == null || scale == 0) {
            return "numeric(" + precision + ")";
        }
        return "numeric(" + precision + "," + scale + ")";
    }
}
