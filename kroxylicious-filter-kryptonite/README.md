# Kryptonite Filter for Kroxylicious

A custom [Kroxylicious](https://kroxylicious.io) filter that brings **field-level encryption** to the Kafka proxy layer using the same Kryptonite for Kafka core building blocks as any of the other modules.

## Why this module?

Kroxylicious ships with its own `RecordEncryption` filter, but that filter encrypts **entire record values** — the whole payload becomes opaque ciphertext. That works well for blanket data protection, but it doesn't allow for downstream consumers that need to read even a single unprotected field without decrypting the entire record.

This module takes a different approach: **only the fields you designate are encrypted**. The rest of the record stays in plaintext, schemas stay valid, and consumers that don't hold the keys can still process non-sensitive fields. Similar to other filters, encryption and decryption happen transparently such that producers and consumers don't need any code changes.

A second key difference is **cross-module interoperability**. The encrypted field content is compatible with other Kryptonite for Kafka modules (Kafka Connect SMT, ksqlDB UDFs, Flink UDFs, Funqy HTTP API). This means, any field encrypted by the proxy filter can be decrypted by the Connect SMT or a Flink UDF.

## What it does

Two Kroxylicious filter factories intercept the Kafka protocol:

- **`KryptoniteEncryptionFilterFactory`** — intercepts `ProduceRequest` on the way to the broker; encrypts the designated fields according to the filter configuration before the record gets delegated to the actual Kafka brokers.
- **`KryptoniteDecryptionFilterFactory`** — intercepts `FetchResponse` on the way back to consumers; decrypts the designated fields according to the filter configuration before the record gets handed to the client.

Both filters also implement `ApiVersionsResponseFilter` to downgrade the Produce/Fetch API version seen by the client to v12 — the last version of those protocols that includes topic names directly in the wire format (v13+ switched to topic UUIDs). This downgrade guarantees that the filter always receives topic names without requiring any additional metadata round-trips.

Field selection is driven by a **topic routing table**: each topic pattern (exact name or wildcard) maps to a list of field names. Dot-notation paths address nested fields (e.g. `personal.lastname`). Per-field overrides for cipher algorithm, key ID, field mode, and FPE settings are all supported.

Schemas are handled automatically in DYNAMIC mode: the filter derives the encrypted schema from the original, registers it in the Schema Registry, and attaches the correct schema ID to outgoing records — consumers see a fully valid schema-registry-backed payload.

## What is currently supported

| Dimension | Supported |
|---|---|
| **Record formats** | `JSON` (plain JSON without Schema Registry), `JSON_SR` (JSON Schema + Confluent Schema Registry), `AVRO` (Avro + Confluent Schema Registry, see limitations below) |
| **Schema mode** | `DYNAMIC` — encrypted schemas are auto-derived and registered at runtime |
| **Field encryption modes** | `OBJECT` — encrypt the whole field value (default); `ELEMENT` — encrypt array elements or object/map values individually |
| **Cipher algorithms** | `TINK/AES_GCM` (probabilistic AEAD), `TINK/AES_GCM_SIV` (deterministic AEAD), FPE (format-preserving encryption, String/text fields only) |
| **Key sources** | `CONFIG`, `CONFIG_ENCRYPTED`, `KMS`, `KMS_ENCRYPTED` — same options as all other Kryptonite modules |
| **Cloud KMS** | Azure Key Vault, GCP KMS, AWS Secrets Manager (via the respective `kryptonite-kms-*` modules) |
| **Topic routing** | Exact topic names and wildcard patterns (`*` = any sequence, `?` = single char); first-match wins |
| **Partial decrypt** | Different consumers can decrypt different subsets of the encrypted fields; each distinct subset gets its own derived Schema Registry subject |
| **Cross-module interop** | Encrypted envelopes are byte-for-byte compatible with Connect SMT, ksqlDB, Flink, and Funqy |

## Schema Registry subject naming

For schema-aware record formats (`JSON_SR`, `AVRO`) the filter auto-manages several Schema Registry subjects per topic:

| Subject | Purpose |
|---|---|
| `<topicName>-value__k4k_enc` | Encrypted schema (field types replaced with `string`) |
| `<topicName>-value__k4k_meta` | Encryption metadata: stores original schema ID, encrypted field list, and per-field modes |
| `<topicName>-value__k4k_dec_<hash>` | Partial-decrypt schema for consumers that decrypt only a subset of fields |

The encryption metadata subject is registered with `NONE` compatibility mode, as is the encrypted schema subject. All schema documents are clean — no custom keywords are injected into the schema JSON or Avro schema itself.

## Failure handling

- **Encryption failure**: If encrypting a field fails, the exception propagates and the produce request fails. Plaintext data is never forwarded to the broker on encryption failure.
- **Decryption failure**: If decrypting a field fails, the original (still-encrypted) bytes are returned to the consumer and the error is logged at ERROR level. The consumer receives raw broker data rather than corrupt or partial output.

## Quick-start configuration

### Plain JSON (no Schema Registry)

```yaml
filterDefinitions:
  - name: kryptonite-encrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteEncryptionFilterFactory
    config:
      keySource: CONFIG
      cipherAlgorithm: TINK/AES_GCM
      cipherDataKeyIdentifier: keyA
      cipherDataKeys:
        - identifier: keyA
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: GhDRulECKAC8/19NMXDjeCjK
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      recordFormat: JSON
      topicFieldConfigs:
        - topicPattern: persons
          fieldConfigs:
            - name: personal.lastname            # OBJECT mode (default): encrypt whole field
            - name: tags
              fieldMode: ELEMENT                 # ELEMENT mode: encrypt each array element

  - name: kryptonite-decrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteDecryptionFilterFactory
    config:
      keySource: CONFIG
      cipherDataKeys:
        # ... same key material as above ...
      recordFormat: JSON
      topicFieldConfigs:
        - topicPattern: persons
          fieldConfigs:
            - name: personal.lastname
            - name: tags
              fieldMode: ELEMENT

defaultFilters:
  - kryptonite-encrypt
  - kryptonite-decrypt
```

### JSON Schema with Confluent Schema Registry

Add `schemaRegistryUrl` and set `recordFormat: JSON_SR`. An optional `schemaRegistryConfig` map allows passing additional Confluent SR client properties (e.g. authentication headers):

```yaml
filterDefinitions:
  - name: kryptonite-encrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteEncryptionFilterFactory
    config:
      keySource: CONFIG
      cipherAlgorithm: TINK/AES_GCM
      cipherDataKeyIdentifier: keyA
      cipherDataKeys:
        - identifier: keyA
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: GhDRulECKAC8/19NMXDjeCjK
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      schemaRegistryUrl: http://localhost:8081
      # schemaRegistryConfig:                    # optional SR client properties
      #   basic.auth.credentials.source: USER_INFO
      #   basic.auth.user.info: "user:password"
      recordFormat: JSON_SR
      schemaMode: DYNAMIC
      topicFieldConfigs:
        - topicPattern: persons
          fieldConfigs:
            - name: personal.lastname
            - name: tags
              fieldMode: ELEMENT

  - name: kryptonite-decrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteDecryptionFilterFactory
    config:
      keySource: CONFIG
      cipherDataKeys:
        # ... same key material as above ...
      schemaRegistryUrl: http://localhost:8081
      recordFormat: JSON_SR
      schemaMode: DYNAMIC
      topicFieldConfigs:
        - topicPattern: persons
          fieldConfigs:
            - name: personal.lastname
            - name: tags
              fieldMode: ELEMENT

defaultFilters:
  - kryptonite-encrypt
  - kryptonite-decrypt
```

See `sample-proxy-config-json.yaml`, `sample-proxy-config-jsonschema.yaml`, and `sample-proxy-config-avro.yaml` for complete working examples.

## Configuration reference

| Parameter | Default | Description |
|---|---|---|
| `keySource` | `CONFIG` | Key management mode: `CONFIG`, `CONFIG_ENCRYPTED`, `KMS`, `KMS_ENCRYPTED` |
| `cipherAlgorithm` | `TINK/AES_GCM` | Default cipher for fields that don't specify one |
| `cipherDataKeyIdentifier` | _(empty)_ | Default key ID used when a field has no per-field `keyId` |
| `cipherDataKeys` | _(none)_ | List of inline Tink keyset entries (required for `CONFIG`/`CONFIG_ENCRYPTED` key sources) |
| `kmsType` | `NONE` | KMS secret backend: `AZ_KV_SECRETS`, `AWS_SM_SECRETS`, `GCP_SM_SECRETS`, `NONE` |
| `kmsConfig` | `{}` | JSON config string for the KMS backend |
| `kekType` | `NONE` | Key-encryption key provider: `AZURE`, `GCP`, `AWS`, `NONE` |
| `kekUri` | _(empty)_ | URI of the key-encryption key in the cloud provider |
| `kekConfig` | `{}` | JSON config string for the KEK provider |
| `schemaRegistryUrl` | _(none)_ | Confluent Schema Registry base URL (required for `JSON_SR` and `AVRO`) |
| `schemaRegistryConfig` | `{}` | Additional SR client properties (auth, TLS, etc.) |
| `recordFormat` | `JSON_SR` | `JSON`, `JSON_SR`, or `AVRO` |
| `schemaMode` | `DYNAMIC` | Schema registration mode; only `DYNAMIC` is currently implemented |
| `topicFieldConfigs` | _(required)_ | Ordered list of topic-pattern-to-field-config mappings |

### Per-field configuration (`fieldConfigs` entries)

| Parameter | Default | Description |
|---|---|---|
| `name` | _(required)_ | Dot-notation field path (e.g. `personal.lastname`) |
| `algorithm` | global `cipherAlgorithm` | Per-field cipher override |
| `keyId` | global `cipherDataKeyIdentifier` | Per-field key ID override |
| `fieldMode` | `OBJECT` | `OBJECT` or `ELEMENT` |
| `fpeTweak` | _(default from core)_ | FPE tweak value (for FPE algorithms only) |
| `fpeAlphabetType` | _(default from core)_ | FPE alphabet type: `ALPHANUMERIC`, `NUMERIC`, `ALPHA`, `CUSTOM` |
| `fpeAlphabetCustom` | _(none)_ | Custom alphabet string (when `fpeAlphabetType: CUSTOM`) |
| `encoding` | `BASE64` | Output encoding for the encrypted envelope |

## Building

```bash
# from the repository root
./mvnw clean install -pl kroxylicious-filter-kryptonite
```

The module produces a fat JAR that can be dropped into any Kroxylicious deployment as a custom filter plugin.

## Known Limitations

### FPE: String/text fields only

Format-preserving encryption (FPE) operates on the UTF-8 string representation of the field value. For JSON records this means only textual JSON nodes (`node.isTextual()`) are encrypted; non-text nodes (numbers, booleans, objects, arrays) are passed through unchanged. For Avro records, only `CharSequence`/`Utf8` field values are FPE-encrypted; other types are skipped. Configure FPE algorithms only on fields whose values are always strings.

### JSON Schema: unsupported schema constructs

`JsonSchemaDeriver` navigates the schema `properties` tree following the dot-path. The following constructs are not supported for encrypted fields and will silently pass through without schema modification:

- `$ref` — schema references are not resolved
- `oneOf`, `anyOf`, `allOf` combiners

All encrypted field schemas must be defined inline in the schema document.

### AVRO: named type references on encrypted field paths

Avro schemas allow fields to reference a previously defined named type by name string rather than inlining the schema (e.g. `"type": "com.example.Address"` instead of an inline record schema). The schema deriver (`AvroSchemaDeriver`) navigates field schemas directly and does not resolve named type references. If a field on an encrypted field path uses a named type reference rather than an inline schema definition, schema derivation will fail with an error. All field schemas on encrypted paths must be defined inline in the schema document.

### AVRO: non-nullable binary unions

`AvroSchemaDeriver` handles the standard Avro nullable field pattern `["null", T]` — it preserves the null branch and replaces the non-null branch with `string` (OBJECT mode) or the appropriate array/map-of-string schema (ELEMENT mode). Union schemas with two or more non-null branches (e.g. `["int", "string"]`) are not recognised as nullable unions and will produce a `SchemaDerivationException`. Only nullable unions (`["null", T]` or `[T, "null"]`) are supported for encrypted fields.
