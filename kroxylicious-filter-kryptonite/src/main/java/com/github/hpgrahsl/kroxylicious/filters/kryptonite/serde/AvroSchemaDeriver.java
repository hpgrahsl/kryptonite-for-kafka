package com.github.hpgrahsl.kroxylicious.filters.kryptonite.serde;

import com.github.hpgrahsl.kroxylicious.filters.kryptonite.config.FieldConfig;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives modified Avro {@link Schema} objects for the Schema Registry produce and consume paths.
 *
 * <p>Used internally by {@link DefaultDynamicSchemaRegistryAdapter} for the {@code AVRO} record format.
 * No metadata is injected into derived schemas — metadata is stored in the encryption metadata subject.
 *
 * <p>Union handling: Avro nullable fields use the pattern {@code ["null", T]}. Type replacement
 * preserves the null branch and replaces {@code T} with {@code string}. Null-branch ordering
 * from the original schema is preserved.
 *
 * <p>OBJECT mode on complex types (nested records, arrays, maps, enums, fixed) encrypts the
 * entire field value; type replacement collapses the field schema to {@code string}
 * (or {@code ["null","string"]} for nullable unions). The original schema is restored on decrypt.
 *
 * <p>ELEMENT mode on record fields: each direct field of the record is replaced with
 * {@code string}; the record container and field names are preserved. The original sub-field
 * schemas are restored on decrypt.
 */
class AvroSchemaDeriver {

    private static final Logger LOG = LoggerFactory.getLogger(AvroSchemaDeriver.class);
    private static final Schema STRING_SCHEMA = Schema.create(Schema.Type.STRING);

    /**
     * Produce path: derives the encrypted Avro schema from the original.
     *
     * <p>OBJECT mode: replaces the target field's schema with {@code string}
     * (or {@code ["null","string"]} for nullable unions).
     * ELEMENT mode on array fields: replaces {@code items} with {@code string}.
     * ELEMENT mode on map fields: replaces {@code values} with {@code string}.
     * ELEMENT mode on record fields: replaces each direct field schema with {@code string}.
     *
     * @return pair of (encrypted Schema, encryptedFieldModes map for encryption metadata)
     */
    DeriveEncryptedResult deriveEncrypted(Schema originalSchema, Set<FieldConfig> fieldConfigs) {
        List<String> encryptedFields = new ArrayList<>();
        Map<String, String> encryptedFieldModes = new java.util.LinkedHashMap<>();

        Schema result = originalSchema;
        for (FieldConfig fc : fieldConfigs) {
            String[] pathParts = fc.getName().split("\\.");
            FieldConfig.FieldMode mode = fc.getFieldMode().orElse(FieldConfig.DEFAULT_MODE);
            try {
                result = replaceFieldType(result, pathParts, 0, mode);
                encryptedFields.add(fc.getName());
                encryptedFieldModes.put(fc.getName(), mode.name());
            } catch (FieldNotFoundException e) {
                LOG.debug("deriveEncrypted: field path '{}' not found in Avro schema — leaving unchanged", fc.getName());
            }
        }
        return new DeriveEncryptedResult(result, encryptedFields, encryptedFieldModes);
    }

    /** Result of {@link #deriveEncrypted}: derived Schema plus field metadata for the encryption metadata subject. */
    record DeriveEncryptedResult(Schema schema, List<String> encryptedFields,
                                 Map<String, String> encryptedFieldModes) {}

    /**
     * Consume path: derives the partial-decrypt Avro schema.
     *
     * <p>Starts from the encrypted schema and restores field types for {@code decryptedFields}
     * using the original schema. Still-encrypted fields remain as {@code string}.
     *
     * @param encryptedFieldModes explicit mode map from encryption metadata ({@code fieldName → "ELEMENT"|"OBJECT"})
     */
    Schema derivePartialDecrypt(Schema encryptedSchema, Schema originalSchema,
                                List<String> decryptedFields,
                                Map<String, String> encryptedFieldModes) {
        Schema result = encryptedSchema;
        for (String fieldName : decryptedFields) {
            String[] pathParts = fieldName.split("\\.");
            String mode = encryptedFieldModes.get(fieldName);
            try {
                Schema originalFieldSchema = resolveFieldSchema(originalSchema, pathParts, 0);
                result = restoreFieldType(result, pathParts, 0, originalFieldSchema, mode);
            } catch (FieldNotFoundException e) {
                LOG.debug("derivePartialDecrypt: field path '{}' not found — leaving unchanged", fieldName);
            }
        }
        return result;
    }

    // ---- Schema tree navigation and replacement ----

    /**
     * Recursively navigates the schema tree following {@code pathParts} and replaces the target
     * field's type. Returns a new {@link Schema} with the replacement applied (Avro schemas are
     * immutable — affected records are rebuilt up the path).
     */
    private Schema replaceFieldType(Schema schema, String[] pathParts, int index,
                                    FieldConfig.FieldMode mode) {
        Schema unwrapped = unwrapNullableUnion(schema);
        boolean wasNullable = unwrapped != schema;

        if (unwrapped.getType() == Schema.Type.UNION) {
            throw new SchemaDerivationException(
                    "Encrypted field path '" + String.join(".", pathParts) + "': a non-nullable union type"
                            + " was encountered while traversing the path — only nullable unions"
                            + " [\"null\", T] are supported. See 'Known Limitations' in the filter README.");
        }
        if (unwrapped.getType() != Schema.Type.RECORD) {
            throw new SchemaDerivationException(
                    "Expected RECORD at path segment '" + pathParts[index]
                            + "' but found " + unwrapped.getType());
        }

        Schema.Field targetField = unwrapped.getField(pathParts[index]);
        if (targetField == null) {
            throw new FieldNotFoundException(pathParts[index]);
        }

        Schema newFieldSchema;
        if (index == pathParts.length - 1) {
            // Leaf: apply the transformation
            newFieldSchema = transformLeafFieldSchema(targetField.schema(), mode, pathParts[index]);
        } else {
            // Intermediate: recurse deeper
            newFieldSchema = replaceFieldType(targetField.schema(), pathParts, index + 1, mode);
        }

        Schema rebuilt = rebuildRecord(unwrapped, targetField.name(), newFieldSchema);
        return wasNullable ? rewrapNullableUnion(schema, rebuilt) : rebuilt;
    }

    /**
     * Transforms the field schema at the leaf of the path according to {@code mode}.
     */
    private Schema transformLeafFieldSchema(Schema fieldSchema, FieldConfig.FieldMode mode,
                                            String fieldName) {
        Schema unwrapped = unwrapNullableUnion(fieldSchema);
        boolean wasNullable = unwrapped != fieldSchema;

        if (unwrapped.getType() == Schema.Type.UNION) {
            throw new SchemaDerivationException(
                    "Field '" + fieldName + "' has a non-nullable union schema — only nullable"
                            + " unions [\"null\", T] are supported for encrypted fields."
                            + " See 'Known Limitations' in the filter README.");
        }

        Schema transformed;
        if (mode == FieldConfig.FieldMode.ELEMENT) {
            transformed = transformElementMode(unwrapped, fieldName);
        } else {
            // OBJECT mode: entire field → string (all Avro types including RECORD, ENUM, FIXED)
            if (unwrapped.getType() == Schema.Type.STRING) {
                transformed = unwrapped; // already string, no change
            } else {
                transformed = STRING_SCHEMA;
            }
        }

        return wasNullable ? rewrapNullableUnion(fieldSchema, transformed) : transformed;
    }

    private Schema transformElementMode(Schema fieldSchema, String fieldName) {
        return switch (fieldSchema.getType()) {
            case ARRAY -> Schema.createArray(STRING_SCHEMA);
            case MAP -> Schema.createMap(STRING_SCHEMA);
            case RECORD -> {
                List<Schema.Field> stringFields = fieldSchema.getFields().stream()
                        .map(f -> new Schema.Field(f.name(), STRING_SCHEMA, f.doc(), f.defaultVal(), f.order()))
                        .toList();
                Schema newRecord = Schema.createRecord(
                        fieldSchema.getName(), fieldSchema.getDoc(),
                        fieldSchema.getNamespace(), fieldSchema.isError(), stringFields);
                fieldSchema.getAliases().forEach(newRecord::addAlias);
                yield newRecord;
            }
            default -> throw new SchemaDerivationException(
                    "ELEMENT mode requires array, map or record field type, but field '" + fieldName
                            + "' has type " + fieldSchema.getType());
        };
    }

    /**
     * Restores a field's schema to its original type (used on decrypt path).
     */
    private Schema restoreFieldType(Schema encryptedSchema, String[] pathParts, int index,
                                    Schema originalFieldSchema, String mode) {
        Schema unwrapped = unwrapNullableUnion(encryptedSchema);
        boolean wasNullable = unwrapped != encryptedSchema;

        if (unwrapped.getType() == Schema.Type.UNION) {
            throw new SchemaDerivationException(
                    "Encrypted field path '" + String.join(".", pathParts) + "': a non-nullable union type"
                            + " was encountered while traversing the path — only nullable unions"
                            + " [\"null\", T] are supported. See 'Known Limitations' in the filter README.");
        }
        if (unwrapped.getType() != Schema.Type.RECORD) {
            throw new SchemaDerivationException(
                    "Expected RECORD at path segment '" + pathParts[index]
                            + "' but found " + unwrapped.getType());
        }

        Schema.Field targetField = unwrapped.getField(pathParts[index]);
        if (targetField == null) throw new FieldNotFoundException(pathParts[index]);

        Schema newFieldSchema;
        if (index == pathParts.length - 1) {
            newFieldSchema = originalFieldSchema;
        } else {
            newFieldSchema = restoreFieldType(targetField.schema(), pathParts, index + 1,
                    originalFieldSchema, mode);
        }

        Schema rebuilt = rebuildRecord(unwrapped, targetField.name(), newFieldSchema);
        return wasNullable ? rewrapNullableUnion(encryptedSchema, rebuilt) : rebuilt;
    }

    /**
     * Resolves the field schema at the end of {@code pathParts} starting at {@code index}
     * without rebuilding — used to fetch the original schema for type restoration.
     */
    private Schema resolveFieldSchema(Schema schema, String[] pathParts, int index) {
        Schema unwrapped = unwrapNullableUnion(schema);
        if (unwrapped.getType() == Schema.Type.UNION) {
            throw new SchemaDerivationException(
                    "Encrypted field path '" + String.join(".", pathParts) + "': a non-nullable union type"
                            + " was encountered while traversing the path — only nullable unions"
                            + " [\"null\", T] are supported. See 'Known Limitations' in the filter README.");
        }
        if (unwrapped.getType() != Schema.Type.RECORD) {
            throw new FieldNotFoundException(pathParts[index]);
        }
        Schema.Field field = unwrapped.getField(pathParts[index]);
        if (field == null) throw new FieldNotFoundException(pathParts[index]);
        if (index == pathParts.length - 1) return field.schema();
        return resolveFieldSchema(field.schema(), pathParts, index + 1);
    }

    // ---- Union helpers ----

    /**
     * If {@code schema} is a nullable union {@code ["null", T]} or {@code [T, "null"]},
     * returns {@code T}. Otherwise returns {@code schema} unchanged.
     */
    private static Schema unwrapNullableUnion(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) return schema;
        List<Schema> types = schema.getTypes();
        if (types.size() != 2) return schema;
        boolean hasNull = types.stream().anyMatch(s -> s.getType() == Schema.Type.NULL);
        if (!hasNull) return schema;
        return types.stream()
                .filter(s -> s.getType() != Schema.Type.NULL)
                .findFirst()
                .orElse(schema);
    }

    /**
     * Rebuilds a nullable union schema substituting {@code newNonNull} for the non-null branch,
     * preserving the original null-branch ordering.
     */
    private static Schema rewrapNullableUnion(Schema originalUnion, Schema newNonNull) {
        List<Schema> original = originalUnion.getTypes();
        List<Schema> rebuilt = new ArrayList<>();
        for (Schema branch : original) {
            rebuilt.add(branch.getType() == Schema.Type.NULL ? branch : newNonNull);
        }
        return Schema.createUnion(rebuilt);
    }

    // ---- Record rebuilding ----

    /**
     * Copies all fields of {@code record}, replacing the schema of {@code targetFieldName}
     * with {@code newFieldSchema}. Returns a new {@link Schema.Type#RECORD} schema.
     */
    private static Schema rebuildRecord(Schema record, String targetFieldName, Schema newFieldSchema) {
        List<Schema.Field> newFields = new ArrayList<>();
        for (Schema.Field f : record.getFields()) {
            Schema fieldSchema = f.name().equals(targetFieldName) ? newFieldSchema : f.schema();
            Schema.Field newField = new Schema.Field(f.name(), fieldSchema, f.doc(), f.defaultVal(), f.order());
            f.aliases().forEach(newField::addAlias);
            newFields.add(newField);
        }
        Schema rebuilt = Schema.createRecord(
                record.getName(), record.getDoc(), record.getNamespace(), record.isError(), newFields);
        record.getAliases().forEach(rebuilt::addAlias);
        return rebuilt;
    }

    static class SchemaDerivationException extends RuntimeException {
        SchemaDerivationException(String message) { super(message); }
    }

    private static class FieldNotFoundException extends RuntimeException {
        FieldNotFoundException(String fieldName) { super(fieldName); }
    }
}
