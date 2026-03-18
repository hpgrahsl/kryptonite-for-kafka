/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.kryptonite.converters.avro;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaDeriverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JsonSchemaDeriver deriver = new JsonSchemaDeriver();

    // --- primitive types ---

    @Test
    void deriveNull() {
        assertEquals(Schema.Type.NULL, deriver.derive(NullNode.getInstance(), "f").getType());
    }

    @Test
    void deriveBoolean() {
        assertEquals(Schema.Type.BOOLEAN, deriver.derive(MAPPER.valueToTree(true), "f").getType());
    }

    @Test
    void deriveIntegralNumber() {
        var schema = deriver.derive(MAPPER.valueToTree(42L), "f");
        assertEquals(Schema.Type.LONG, schema.getType());
    }

    @Test
    void deriveDecimalNumber() {
        var schema = deriver.derive(MAPPER.valueToTree(3.14), "f");
        assertEquals(Schema.Type.DOUBLE, schema.getType());
    }

    @Test
    void deriveString() {
        assertEquals(Schema.Type.STRING, deriver.derive(MAPPER.valueToTree("hello"), "f").getType());
    }

    // --- array types ---

    @Test
    void deriveEmptyArray() throws Exception {
        var node = MAPPER.readTree("[]");
        var schema = deriver.derive(node, "f");
        assertEquals(Schema.Type.ARRAY, schema.getType());
        assertEquals(Schema.Type.NULL, schema.getElementType().getType());
    }

    @Test
    void deriveHomogeneousLongArray() throws Exception {
        var node = MAPPER.readTree("[1, 2, 3]");
        var schema = deriver.derive(node, "f");
        assertEquals(Schema.Type.ARRAY, schema.getType());
        assertEquals(Schema.Type.LONG, schema.getElementType().getType());
    }

    @Test
    void deriveHomogeneousStringArray() throws Exception {
        var node = MAPPER.readTree("[\"a\", \"b\"]");
        var schema = deriver.derive(node, "f");
        assertEquals(Schema.Type.ARRAY, schema.getType());
        assertEquals(Schema.Type.STRING, schema.getElementType().getType());
    }

    @Test
    void deriveAllNullArray() throws Exception {
        var node = MAPPER.readTree("[null, null]");
        var schema = deriver.derive(node, "f");
        assertEquals(Schema.Type.ARRAY, schema.getType());
        assertEquals(Schema.Type.NULL, schema.getElementType().getType());
    }

    @Test
    void deriveHeterogeneousArrayNullFirst() throws Exception {
        // mixed: null + long → union [null, long]
        var node = MAPPER.readTree("[null, 42]");
        var schema = deriver.derive(node, "f");
        assertEquals(Schema.Type.ARRAY, schema.getType());
        var itemSchema = schema.getElementType();
        assertEquals(Schema.Type.UNION, itemSchema.getType());
        var unionTypes = itemSchema.getTypes();
        assertEquals(2, unionTypes.size());
        assertEquals(Schema.Type.NULL, unionTypes.get(0).getType());
        assertEquals(Schema.Type.LONG, unionTypes.get(1).getType());
    }

    @Test
    void deriveHeterogeneousArrayNoNulls() throws Exception {
        // long + string, no nulls → union [long, string]
        var node = MAPPER.readTree("[1, \"a\"]");
        var schema = deriver.derive(node, "f");
        assertEquals(Schema.Type.ARRAY, schema.getType());
        var itemSchema = schema.getElementType();
        assertEquals(Schema.Type.UNION, itemSchema.getType());
        var unionTypes = itemSchema.getTypes();
        assertEquals(2, unionTypes.size());
        assertEquals(Schema.Type.LONG, unionTypes.get(0).getType());
        assertEquals(Schema.Type.STRING, unionTypes.get(1).getType());
    }

    // --- object/record types ---

    @Test
    void deriveFlatObject() throws Exception {
        var node = MAPPER.readTree("{\"name\": \"Alice\", \"age\": 30}");
        var schema = deriver.derive(node, "order");
        assertEquals(Schema.Type.RECORD, schema.getType());
        assertNotNull(schema.getField("name"));
        assertEquals(Schema.Type.STRING, schema.getField("name").schema().getType());
        assertNotNull(schema.getField("age"));
        assertEquals(Schema.Type.LONG, schema.getField("age").schema().getType());
    }

    @Test
    void deriveNestedObjectProducesUniqueRecordNames() throws Exception {
        var node = MAPPER.readTree("{\"address\": {\"city\": \"Vienna\"}}");
        var outer = deriver.derive(node, "order");
        assertEquals(Schema.Type.RECORD, outer.getType());
        var addressField = outer.getField("address");
        assertNotNull(addressField);
        assertEquals(Schema.Type.RECORD, addressField.schema().getType());
        // inner record name must differ from outer to avoid Avro naming conflict
        assertNotEquals(outer.getName(), addressField.schema().getName());
    }

    // --- repeated calls ---

    @Test
    void differentPrimitiveTypesAtSameFieldPathReturnRespectiveSchemas() {
        var nullSchema = deriver.derive(NullNode.getInstance(), "myField");
        var longSchema = deriver.derive(MAPPER.valueToTree(99L), "myField");
        assertEquals(Schema.Type.NULL, nullSchema.getType());
        assertEquals(Schema.Type.LONG, longSchema.getType());
    }

    @Test
    void objectsWithDifferentStructuresAtSameFieldPathBothSucceed() throws Exception {
        var first = deriver.derive(MAPPER.readTree("{\"a\": 1}"), "myField");
        var second = deriver.derive(MAPPER.readTree("{\"b\": \"x\"}"), "myField");
        assertEquals(Schema.Type.RECORD, first.getType());
        assertEquals(Schema.Type.RECORD, second.getType());
        assertNotNull(first.getField("a"));
        assertNotNull(second.getField("b"));
    }

    @Test
    void heterogeneousArrayOfArraysThrows() throws Exception {
        // two distinct array types in one array would require a union of two ARRAY schemas,
        // which the Avro spec forbids
        var node = MAPPER.readTree("[[1, 2], [\"a\", \"b\"]]");
        assertThrows(IllegalArgumentException.class, () -> deriver.derive(node, "f"));
    }

    // --- sanitizeName ---

    @Test
    void sanitizeNameReplacesDotsWithUnderscore() {
        assertEquals("order_customer_id", JsonSchemaDeriver.sanitizeName("order.customer.id"));
    }

    @Test
    void sanitizeNameReplacesDashes() {
        assertEquals("my_field", JsonSchemaDeriver.sanitizeName("my-field"));
    }

    @Test
    void sanitizeNamePrependsF_WhenStartsWithDigit() {
        assertEquals("f_123abc", JsonSchemaDeriver.sanitizeName("123abc"));
    }

    @Test
    void sanitizeNameAlreadyValidUnchanged() {
        assertEquals("validName_123", JsonSchemaDeriver.sanitizeName("validName_123"));
    }

}
