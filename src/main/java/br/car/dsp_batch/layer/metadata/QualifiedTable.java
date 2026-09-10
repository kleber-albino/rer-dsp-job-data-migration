package br.car.dsp_batch.layer.metadata;

/**
 * PostgreSQL qualified table name ({@code schema.table}), with exactly one {@code .}.
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
        int firstDot = trimmed.indexOf('.');
        int lastDot = trimmed.lastIndexOf('.');
        if (firstDot <= 0 || firstDot != lastDot || firstDot == trimmed.length() - 1) {
            throw new IllegalArgumentException(
                    "Expected schema.table (exactly one '.'), got: " + qualifiedName);
        }
        return new QualifiedTable(
                trimmed.substring(0, firstDot),
                trimmed.substring(firstDot + 1));
    }
}
