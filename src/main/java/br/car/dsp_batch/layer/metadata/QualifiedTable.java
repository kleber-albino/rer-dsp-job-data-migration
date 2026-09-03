package br.car.dsp_batch.layer.metadata;

/**
 * PostgreSQL qualified table name ({@code schema.table}).
 */
public record QualifiedTable(String schema, String table) {

    public String qualified() {
        return schema + "." + table;
    }

    public static QualifiedTable parse(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            throw new IllegalArgumentException("Qualified name must not be blank");
        }
        String trimmed = qualifiedName.trim();
        int dot = trimmed.indexOf('.');
        if (dot <= 0 || dot == trimmed.length() - 1) {
            throw new IllegalArgumentException(
                    "Expected schema.table format, got: " + qualifiedName);
        }
        return new QualifiedTable(trimmed.substring(0, dot), trimmed.substring(dot + 1));
    }
}
