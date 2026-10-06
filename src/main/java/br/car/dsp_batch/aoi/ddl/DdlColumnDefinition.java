package br.car.dsp_batch.aoi.ddl;

/**
 * Target column name and SQL type fragment (identifier is unquoted).
 */
public record DdlColumnDefinition(String name, String sqlType) {
}
