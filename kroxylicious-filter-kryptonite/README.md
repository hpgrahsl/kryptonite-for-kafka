# Kryptonite for Kafka: Kroxylicious Filter

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

A custom [Kroxylicious](https://kroxylicious.io) filter that brings **field-level encryption** to the Kafka proxy layer using the same Kryptonite for Kafka core building blocks as any of the other modules.

## 📚 Documentation

### 👉 For the latest module documentation please go to the official docs page for the [Kroxylicious Filter](https://hpgrahsl.github.io/kryptonite-for-kafka/dev/modules/kroxylicious-filter/) 👈

_The contents in this README are most likely outdated by now and won't be regularly maintained going forward. You should regard them as deprecated and  expect removal any time without prior notice._

---

## Why this module?

Kroxylicious ships with its own `RecordEncryption` filter, but that filter encrypts **entire record values**. As a consequence, the whole payload becomes opaque ciphertext. While that is a great choice for blanket data protection, it doesn't work whenever use cases require a more fine-grained and flexible mechanism for payload encryption.

This module takes a different approach: **only the fields you designate are encrypted**. The rest of the record stays in plaintext, schemas if present stay valid, and consumers that don't hold the keys can still process non-sensitive fields. Similar to other filters, encryption and decryption happen transparently such that producers and consumers don't need any code changes.

A second key difference is **cross-module interoperability**. The encrypted field content is compatible with other Kryptonite for Kafka modules (Kafka Connect SMT, ksqlDB UDFs, Flink UDFs, Funqy HTTP API). This means, any field encrypted by the proxy filter can be directly decrypted successfully without the absolute need to consume data through the proxy.

## What it does

Two Kroxylicious filter implementations intercept specific Kafka protocol requests. Each is instantiated and configured by its corresponding factory class (`KryptoniteEncryptionFilterFactory`, `KryptoniteDecryptionFilterFactory`):

- **`KryptoniteEncryptionFilter`** — intercepts `ProduceRequest` on the way to the broker; encrypts the designated fields according to the filter configuration before the record gets delegated to the actual Kafka brokers.
- **`KryptoniteDecryptionFilter`** — intercepts `FetchResponse` on the way back to consumers; decrypts the designated fields according to the filter configuration before the record gets handed to the client.

_Note: Currently, both filter implementations also implement `ApiVersionsResponseFilter` to downgrade the Produce/Fetch API version seen by the client to v12. It's the last version that includes topic names directly in the wire format (v13+ switched to topic UUIDs). This downgrade guarantees that the filter always receives topic names without requiring any additional metadata round-trips._

Field selection is driven by a **topic routing table**: each topic pattern (exact name or wildcard) maps to a list of field names. Dot-notation paths address nested fields (e.g. `myfield.subfield`). Per-field overrides for cipher algorithm, key ID, field mode, and FPE settings are all supported.

Schemas are handled automatically in DYNAMIC mode: the filter derives the encrypted schema from the original, registers it in the Schema Registry, and attaches the correct schema ID to outgoing records. This guarantees that consumers see a fully valid schema registry-backed payload.

## Design notes

### Dual-factory KMS sync

Each filter factory (`KryptoniteEncryptionFilterFactory`, `KryptoniteDecryptionFilterFactory`) creates its own `Kryptonite` instance and therefore its own key vault. When a proxy deployment uses both factories simultaneously (very basic encrypt+decrypt on-the-same-proxy topology), and `kmsRefreshIntervalMinutes` is enabled, **both vaults independently sync against the KMS** — each running its own background daemon thread on the configured interval.

In practice this means the KMS will receive twice the number of list/fetch calls compared to a single-factory deployment. For most KMS rate limits and key counts this is negligible, but it is worth being aware of if you have strict KMS quota constraints or a very large number of keysets.

The simplest mitigation is to configure the same `kms_refresh_interval_minutes` in both factory configs and accept the doubled call rate, or to increase the interval if quota headroom is tight. A shared key-vault / shared `Kryptonite` instance across both factories would eliminate the duplication but is not implemented today.

## What is currently supported

| Dimension | Supported |
|---|---|
| **Record formats** | `JSON` (plain JSON without Schema Registry), `JSON_SR` (JSON Schema + Confluent Schema Registry), `AVRO` (Avro + Confluent Schema Registry, see limitations below) |
| **Schema mode** | `DYNAMIC` — encrypted schemas are auto-derived and registered at runtime; `STATIC` — all schema registry subjects must be pre-registered by the operator before the proxy starts; the filter performs no schema registry writes which is the fallback option in case of "locked-down" schema registries |
| **Field encryption modes** | `OBJECT` — encrypt the whole field value (default); `ELEMENT` — encrypt array elements or object/map/record values individually |
| **Cipher algorithms** | `TINK/AES_GCM` (probabilistic AEAD), `TINK/AES_GCM_SIV` (deterministic AEAD), `CUSTOM/MYSTO_FPE_FF31` (format-preserving encryption, String/text fields only) |
| **Key sources** | same options as all other Kryptonite modules: `CONFIG`, `CONFIG_ENCRYPTED`, `KMS`, `KMS_ENCRYPTED` |
| **Cloud KMS** | same options as all other Kryptonite modules: Azure Key Vault, GCP KMS, AWS Secrets Manager (via the respective `kryptonite-kms-*` modules) |
| **Topic routing** | Exact topic names and wildcard patterns (`*` = any sequence, `?` = single char); first-match wins |
| **Partial decrypt** | Different consumers can decrypt different subsets of the encrypted fields; for schema-aware records each distinct subset gets its own derived schema registry subject (requires schema mode `DYNAMIC`) |
| **Cross-module interop** | encrypted field envelopes are byte-for-byte compatible and directly decryptable by the other Kryptonite for Kafka modules |

## Schema Registry subject naming

For schema-aware record formats (`JSON_SR`, `AVRO`) the filter manages several schema registry subjects per topic:

| Subject | Purpose |
|---|---|
| `<topicName>-value__k4k_enc` | Encrypted schema (field types replaced with `string`) |
| `<topicName>-value__k4k_meta_<schemaId>` | Encryption metadata keyed by schema ID: stores original schema ID, encrypted schema ID, encrypted field list, and per-field modes. Looked up by `encryptedSchemaId` on the decrypt path and by `originalSchemaId` on the encrypt path in STATIC mode. |
| `<topicName>-value__k4k_dec_<hash>` | Partial-decrypt schema for consumers that decrypt only a subset of fields; hash is first 8 hex chars of SHA-256 over `encryptedSchemaId:sorted-field-names` |

In **DYNAMIC** mode the filter registers and maintains all subjects automatically; the encrypted schema and partial-decrypt subjects are registered with `NONE` compatibility mode. All registered schema documents are clean — no custom keywords are injected into the schema JSON or Avro schema.

In **STATIC** mode the filter performs no schema registry subject registrations or compatibility overrides — all subjects must be pre-registered by an operator or CI/CD pipeline before the proxy starts. Any missing subject causes a descriptive runtime error.

## Failure handling

- **Encryption failure**: If encrypting a field fails, the exception propagates and the produce request fails. Plaintext data is never forwarded to the broker on encryption failure. **However, the proxy filter has no way to detect missing field configuration on sensitive data. It's your own responsibility to makes sure that field names/paths match!**
- **Decryption failure**: If decrypting a field fails, the original (still-encrypted) bytes are returned to the consumer and the error is logged at ERROR level. The consumer receives raw broker data rather than corrupt or partial output.

## Quick-start configuration

### Plain JSON (no Schema Registry)

```yaml
filterDefinitions:
  - name: kryptonite-encrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteEncryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_algorithm: TINK/AES_GCM
      cipher_data_key_identifier: keyA
      cipher_data_keys:
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
      record_format: JSON
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
            - name: personal.lastname            # OBJECT mode (default): encrypt whole field
            - name: tags
              fieldMode: ELEMENT                 # ELEMENT mode: encrypt each array element

  - name: kryptonite-decrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteDecryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_data_keys:
        # ... same key material as above ...
      record_format: JSON
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
            - name: personal.lastname
            - name: tags
              fieldMode: ELEMENT

defaultFilters:
  - kryptonite-encrypt
  - kryptonite-decrypt
```

### JSON Schema with Confluent Schema Registry

Add `schema_registry_url` and set `record_format: JSON_SR`. An optional `schema_registry_config` map allows passing additional Confluent SR client properties (e.g. authentication headers):

```yaml
filterDefinitions:
  - name: kryptonite-encrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteEncryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_algorithm: TINK/AES_GCM
      cipher_data_key_identifier: keyA
      cipher_data_keys:
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
      schema_registry_url: http://localhost:8081
      # schema_registry_config:                  # optional SR client properties
      #   basic.auth.credentials.source: USER_INFO
      #   basic.auth.user.info: "user:password"
      record_format: JSON_SR
      schema_mode: DYNAMIC
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
            - name: personal.lastname
            - name: tags
              fieldMode: ELEMENT

  - name: kryptonite-decrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteDecryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_data_keys:
        # ... same key material as above ...
      schema_registry_url: http://localhost:8081
      record_format: JSON_SR
      schema_mode: DYNAMIC
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
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
| `key_source` | `CONFIG` | Key management mode: `CONFIG`, `CONFIG_ENCRYPTED`, `KMS`, `KMS_ENCRYPTED` |
| `cipher_algorithm` | `TINK/AES_GCM` | Default cipher for fields that don't specify one |
| `cipher_data_key_identifier` | _(empty)_ | Default key ID used when a field has no per-field `keyId` |
| `cipher_data_keys` | _(none)_ | List of inline Tink keyset entries (required for `CONFIG`/`CONFIG_ENCRYPTED` key sources) |
| `kms_type` | `NONE` | KMS secret backend: `AZ_KV_SECRETS`, `AWS_SM_SECRETS`, `GCP_SM_SECRETS`, `NONE` |
| `kms_config` | `{}` | JSON config string for the KMS backend |
| `kms_refresh_interval_minutes` | `0` | Background KMS sync interval in minutes; `0` disables the refresh. When enabled, a daemon thread periodically re-fetches all known keysets from the KMS, picks up newly added keys, and retains cached keys that were removed (until restart). Only effective for `KMS` and `KMS_ENCRYPTED` key sources. See also: [Dual-factory KMS sync note](#dual-factory-kms-sync). |
| `kek_type` | `NONE` | Key-encryption key provider: `AZURE`, `GCP`, `AWS`, `NONE` |
| `kek_uri` | _(empty)_ | URI of the key-encryption key in the cloud provider |
| `kek_config` | `{}` | JSON config string for the KEK provider |
| `schema_registry_url` | _(none)_ | Confluent Schema Registry base URL (required for `JSON_SR` and `AVRO`) |
| `schema_registry_config` | `{}` | Additional SR client properties (auth, TLS, etc.) |
| `record_format` | `JSON_SR` | `JSON`, `JSON_SR`, or `AVRO` |
| `schema_mode` | `DYNAMIC` | Schema registration mode: `DYNAMIC` (filter auto-derives and registers SR subjects at runtime) or `STATIC` (filter performs no SR writes — all subjects must be pre-registered by the operator) |
| `serde_type` | `KRYO` | Wire format for plaintext field values inside the encrypted envelope: `KRYO` (default, cross-module compatible) or `AVRO` |
| `blocking_pool_size` | `max(2, CPU count)` | Size of the thread pool used for blocking crypto operations; tune if you need to limit thread count or increase throughput under high load |
| `topic_field_configs` | _(required)_ | Ordered list of topic-pattern-to-field-config mappings |

### Per-field configuration (`field_configs` entries)

| Parameter | Default | Description |
|---|---|---|
| `name` | _(required)_ | Dot-notation field path (e.g. `personal.lastname`) |
| `algorithm` | global `cipher_algorithm` | Per-field cipher override |
| `keyId` | global `cipher_data_key_identifier` | Per-field key ID override |
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

## Testing

### Unit and integration tests

Run the standard Maven test lifecycle — no extra flags needed:

```bash
./mvnw test -pl kroxylicious-filter-kryptonite
```

Integration tests (Surefire, class name suffix `*IT`) spin up Kafka and Schema Registry containers via Testcontainers and cover the full encrypt/decrypt round-trip at the processor level for all three record formats.

### End-to-end tests

The e2e tests (`*ProxyRoundTripIT` classes under `e2e/`) launch a fully containerized topology — Kafka, optionally a Confluent Schema Registry, and a real Kroxylicious proxy loaded with the filter fat JAR — and exercise the proxy from a Kafka client's perspective.

They are **disabled by default** and require the fat JAR to be built first:

```bash
# 1. build the fat JAR
./mvnw package -pl kroxylicious-filter-kryptonite -DskipTests

# 2. run all ITs including e2e
./mvnw verify -pl kroxylicious-filter-kryptonite -De2e.tests=true
```

Three test classes cover the supported record formats:

| Class | Format | Schema Registry |
|---|---|---|
| `JsonProxyRoundTripIT` | `JSON` | no |
| `JsonSrProxyRoundTripIT` | `JSON_SR` | yes |
| `AvroProxyRoundTripIT` | `AVRO` | yes |

Each class runs three scenarios: object-mode round-trip, element-mode round-trip, and encrypted-at-rest verification (direct Kafka read bypassing the proxy).

Container images can be overridden via system properties:

| Property | Default |
|---|---|
| `e2e.kafka.image` | `confluentinc/cp-kafka:7.9.0` |
| `e2e.schema.registry.image` | `confluentinc/cp-schema-registry:7.9.0` |
| `e2e.kroxylicious.image` | `quay.io/kroxylicious/kroxylicious:0.19.0` |

## Known Limitations

### JSON Schema: unsupported schema constructs

`JsonSchemaDeriver` navigates the schema `properties` tree following the dot-path. The following constructs are not supported and will cause the field to be silently skipped without schema modification:

- `$ref` — schema references are not resolved
- `oneOf`, `anyOf`, `allOf` on an **intermediate** node along the dot-path (i.e. a parent field is defined via a combiner). A combiner on the **leaf field itself** is fine — it is replaced wholesale with `{"type":"string"}` on encrypt and restored on decrypt. Example of the unsupported case: encrypting `personal.value` where `personal` is defined as `{"oneOf": [...]}` — navigation cannot descend into the combiner branches and the field is left unchanged.

All encrypted field schemas must be defined inline in the schema document.

### AVRO: named type references on encrypted field paths

Avro schemas allow fields to reference a previously defined named type by name string rather than inlining the schema (e.g. `"type": "com.example.Address"` instead of an inline record schema). The schema deriver (`AvroSchemaDeriver`) navigates field schemas directly and does not resolve named type references. If a field on an encrypted field path uses a named type reference rather than an inline schema definition, schema derivation will fail with an error. All field schemas on encrypted paths must be defined inline in the schema document.

### AVRO: non-nullable binary unions

`AvroSchemaDeriver` handles the standard Avro nullable field pattern `["null", T]` — it preserves the null branch and replaces the non-null branch with `string` (OBJECT mode) or the appropriate array/map-of-string schema (ELEMENT mode). Union schemas with two or more non-null branches (e.g. `["int", "string"]`) are not recognised as nullable unions and will produce a `SchemaDerivationException`. Only nullable unions (`["null", T]` or `[T, "null"]`) are supported for encrypted fields.
