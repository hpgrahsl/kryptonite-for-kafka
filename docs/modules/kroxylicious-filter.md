# :material-shield-lock: Kroxylicious Proxy Filter

The Kryptonite proxy filter provides **transparent, client-agnostic field-level encryption and decryption** for Apache Kafka. It runs as a pair of filters inside a [Kroxylicious](https://kroxylicious.io) proxy — no changes to producers, consumers, or Kafka brokers are required.


**Field-Level Encryption with Proxy Filter**

<div class="k4k-module-img">
<a href="../../assets/images/8a_kroxylicious_filter_encryption.png" class="glightbox" data-glightbox="type: image"><img src="../../assets/images/8a_kroxylicious_filter_encryption.png" alt="Kroxylicious Encryption Filter"></a>
</div>

**Field-Level Decryption with Proxy Filter**

<div class="k4k-module-img">
<a href="../../assets/images/8b_kroxylicious_filter_decryption.png" class="glightbox" data-glightbox="type: image"><img src="../../assets/images/8b_kroxylicious_filter_decryption.png" alt="Kroxylicious Decryption Filter"></a>
</div>

---

## How it works

Encryption happens on the **produce path**: the proxy intercepts `ProduceRequest` messages from clients, encrypts the configured fields, and forwards the encrypted records to the broker. Decryption happens symmetrically on the **fetch path**: the proxy intercepts `FetchResponse` messages from the broker, decrypts the fields, and returns plaintext records to consumers.

Kryptonite for Kafka ships two independent filters:

| Filter | Direction | Responsibility |
|---|---|---|
| **Encryption** | Produce (client → proxy → broker) | Intercepts `ProduceRequest` messages and encrypts the configured fields before records reach the broker |
| **Decryption** | Fetch (broker → proxy → client) | Intercepts `FetchResponse` messages and decrypts the configured fields before records reach the consumer |

Each filter operates entirely independently. Running both in the same proxy instance is the most common setup. It gives producers and consumers a single transparent endpoint with bi-directional, end-to-end field-level protection. However, this is not a requirement. You can deploy only the encryption filter in a proxy that sits in front of producers, and a separate proxy instance with only the decryption filter in front of consumers, depending on your architecture.

Fields that are not listed in the configuration (see  `topic_field_configs` setting) pass through completely unmodified regardless of which filters are active.

Topic routing is controlled by `topic_pattern` entries, each carrying its own `field_configs` list. Patterns are **Java regular expressions** anchored at both ends (equivalent to `^pattern$`), giving full regex expressiveness for topic name matching.

!!! note "Schema Registry Integration"
    For schema-aware record formats (`JSON_SR`, `AVRO`) the filter automatically derives and registers encrypted schemas in the schema registry. Consumers always receive schema-correct records. The encrypted schema uses type `string` for every encrypted field, and the decrypted schema fully restores the original type.

---

## Installation / Deployment

1. Download the filter JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Place the JAR in a folder that is on Kroxylicious' classpath (e.g. the `lib/` directory next to any other JARs shipping with Kroxylicious).
3. Reference the filter factories in your Kroxylicious proxy configuration YAML (see examples below).
4. Run Kroxylicious (see official [Proxy Quick Start](https://kroxylicious.io/documentation/0.20.0/html/proxy-quick-start/))

If building from sources:

```bash
./mvnw clean package -DskipTests -pl kroxylicious-filter-kryptonite
```

---

## Basic Usage

### Proxy Config Structure

A basic Kroxylicious proxy config defines a `virtualClusters` block which, at the very minimum, specifies the upstream broker and the gateway address, plus optional `filterDefinitions` and a corresponding `defaultFilters` block. A minimal skeleton could look like this:

```yaml
virtualClusters:
  - name: mycluster
    targetCluster:
      bootstrapServers: kafka:9092        # upstream Kafka broker
    gateways:
      - name: mygateway
        portIdentifiesNode:
          bootstrapAddress: 0.0.0.0:9192  # proxy listen address
          advertisedBrokerAddressPattern: localhost  # address advertised to clients
    logNetwork: false
    logFrames: false

filterDefinitions:
  # ... (see per-format examples below)

defaultFilters:
  - some-filter
  - another-filter
```

!!! note
    `advertisedBrokerAddressPattern` controls the broker address that Kroxylicious advertises to clients in metadata responses. Set it to the hostname or IP that clients use to reach the proxy. For single-node local setups `localhost` is typical; for containerised or remote deployments use the proxy's externally reachable hostname.

If you want to dig deeper, it is recommended to read the official documentation to understand all the [proxy configuration](https://kroxylicious.io/documentation/0.20.0/html/kroxylicious-proxy/#assembly-configuring-proxy-proxy) options in-depth.

---

### Kryptonite Filter Definitions

What follows are basic examples for proxy filter definitions.

#### Plain JSON Records

For records in plain JSON (no schema registry), the example below shows how to configure both the encrypt and decrypt `filterDefinitions` to process the given (nested) fields in the record values:

```yaml
filterDefinitions:
  - name: kryptonite-encrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteEncryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_algorithm: TINK/AES_GCM
      cipher_data_key_identifier: my-key
      cipher_data_keys:
        - identifier: my-key
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: <BASE64_ENCODED_KEY_MATERIAL_HERE>
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      record_format: JSON
      topic_field_configs:
        - topic_pattern: orders
          field_configs:
            - name: customer.email
            - name: customer.phone
            - name: payment.card_number
              fieldMode: OBJECT

  - name: kryptonite-decrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteDecryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_data_keys:
        - identifier: my-key
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: <BASE64_ENCODED_KEY_MATERIAL_HERE>
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      record_format: JSON
      topic_field_configs:
        - topic_pattern: orders
          field_configs:
            - name: customer.email
            - name: customer.phone
            - name: payment.card_number

defaultFilters:
  - kryptonite-encrypt
  - kryptonite-decrypt
```

Given this input record value:

```json
{
  "orderId": "ORD-9981",
  "customer": {
    "name": "Jane Doe",
    "email": "jane@example.com",
    "phone": "+1-555-0100"
  },
  "payment": {
    "card_number": "4111111111111111",
    "expiry": "12/26"
  }
}
```

after applying the encryption filter this is what gets forwarded to the actual broker:

```json
{
  "orderId": "ORD-9981",
  "customer": {
    "name": "Jane Doe",
    "email": "AaBbCcDdEeFfGgH...",
    "phone": "XxYyZzAaBb1234..."
  },
  "payment": {
    "card_number": "M007MIScg8F0A/cAdW...",
    "expiry": "12/26"
  }
}
```

Consumers fetching through the proxy receive the original plaintext values transparently.

---

#### JSON Schema Records

When records are serialized with JSON based on a corresponding schema in a schema registry, set `record_format: JSON_SR` and provide a `schema_registry_url`. The filter then automatically:

- fetches the original JSON schema from the schema registry on the first record per topic,
- derives an encrypted JSON schema (replacing encrypted field types with `string`) and registers it in SR,
- attaches the correct schema ID for the derived, encrypted JSON schema to every record.

```yaml
filterDefinitions:
  - name: kryptonite-encrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteEncryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_algorithm: TINK/AES_GCM
      cipher_data_key_identifier: my-key
      cipher_data_keys:
        - identifier: my-key
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: <BASE64_ENCODED_KEY_MATERIAL_HERE>
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      schema_registry_url: http://schema-registry:8081
      record_format: JSON_SR
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
            - name: personal.lastname
            - name: personal.age
            - name: contact
              fieldMode: OBJECT
            - name: knownresidences
              fieldMode: ELEMENT

  - name: kryptonite-decrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteDecryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_data_keys:
        - identifier: my-key
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: <BASE64_ENCODED_KEY_MATERIAL_HERE>
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      schema_registry_url: http://schema-registry:8081
      record_format: JSON_SR
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
            - name: personal.lastname
            - name: personal.age
            - name: contact
              fieldMode: OBJECT
            - name: knownresidences
              fieldMode: ELEMENT

defaultFilters:
  - kryptonite-encrypt
  - kryptonite-decrypt
```

---

#### Avro Records

When dealing with Avro records set `record_format: AVRO` and provide a `schema_registry_url`. The filter then automatically:

- fetches the original Avro Schema from the schema registry on the first record per topic,
- derives an encrypted Avro schema (replacing encrypted field types with `string`) and registers it in schema registry,
- attaches the correct schema ID for the derived, encrypted Avro schema to every record. 

```yaml
filterDefinitions:
  - name: kryptonite-encrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteEncryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_algorithm: TINK/AES_GCM
      cipher_data_key_identifier: my-key
      cipher_data_keys:
        - identifier: my-key
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: <BASE64_ENCODED_KEY_MATERIAL_HERE>
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      schema_registry_url: http://schema-registry:8081
      record_format: AVRO
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
            - name: personal.lastname
            - name: personal.age
            - name: contact
              fieldMode: OBJECT
            - name: knownresidences
              fieldMode: ELEMENT

  - name: kryptonite-decrypt
    type: com.github.hpgrahsl.kroxylicious.filters.kryptonite.filter.KryptoniteDecryptionFilterFactory
    config:
      key_source: CONFIG
      cipher_data_keys:
        - identifier: my-key
          material:
            primaryKeyId: 1000000001
            key:
              - keyData:
                  typeUrl: type.googleapis.com/google.crypto.tink.AesGcmKey
                  value: <BASE64_ENCODED_KEY_MATERIAL_HERE>
                  keyMaterialType: SYMMETRIC
                status: ENABLED
                keyId: 1000000001
                outputPrefixType: TINK
      schema_registry_url: http://schema-registry:8081
      record_format: AVRO
      topic_field_configs:
        - topic_pattern: persons
          field_configs:
            - name: personal.lastname
            - name: personal.age
            - name: contact
              fieldMode: OBJECT
            - name: knownresidences
              fieldMode: ELEMENT

defaultFilters:
  - kryptonite-encrypt
  - kryptonite-decrypt
```

---

### Topic Specific Configuration Overrides

The `topic_field_configs` list maps topic name patterns to field configurations. Each entry specifies:

- `topic_pattern`: a **Java regular expression** that is implicitly anchored (`^pattern$`). The first matching entry wins.
- `field_configs`: the list of fields to encrypt/decrypt for matching topics.

```yaml
topic_field_configs:
  # exact topic name match
  - topic_pattern: orders
    field_configs:
      - name: customer.email

  # prefix match — all topics starting with "payments."
  - topic_pattern: payments\..*
    field_configs:
      - name: card_number
      - name: iban

  # suffix match — all topics ending with ".pii"
  - topic_pattern: .*\.pii
    field_configs:
      - name: ssn
      - name: date_of_birth

  # character class — staging or production topics
  - topic_pattern: (staging|production)\.events
    field_configs:
      - name: user_id
```

!!! warning "First-match semantics"
    Only the first matching `topic_pattern` is applied. Order your entries from most specific to least specific.

---

## Field Configuration

### Nested Fields

Use dot-path notation (`"a.b"`) to address nested fields:

```yaml
field_configs:
  - name: person.address.street
  - name: person.address.zip
  - name: metadata.internal.secret
```

### Field Modes

Each field entry supports an optional `fieldMode` that controls how complex values (arrays, maps, objects) are processed:

| `fieldMode` | Behaviour |
|---|---|
| `OBJECT` (default) | The entire field value is encrypted as a single unit and stored as a Base64-encoded ciphertext string |
| `ELEMENT` | For arrays each array element is individually encrypted. For maps each map value is individually encrypted. For structs/records/objects each field value is individually encrypted. The container structure is always preserved. |

```yaml
field_configs:
  # OBJECT mode — encrypts the entire array/map as one ciphertext
  - name: tags
    fieldMode: OBJECT

  # ELEMENT mode — each array element becomes its own ciphertext
  - name: phone_numbers
    fieldMode: ELEMENT

  # ELEMENT mode on a map — map keys are kept, values are individually encrypted
  - name: attributes
    fieldMode: ELEMENT
```

### Per-Field Algorithm and Key Overrides

Any field can override the filter-level defaults for algorithm and key identifier:

```yaml
cipher_data_key_identifier: default-key  # filter-level default

topic_field_configs:
  - topic_pattern: orders
    field_configs:
      # uses filter-level default key + algorithm
      - name: customer.email

      # override key identifier
      - name: payment.card_number
        keyId: payment-key

      # override algorithm (deterministic encryption — use for fields you want to perform correlation based on ciphertext)
      - name: user_id
        algorithm: TINK/AES_GCM_SIV
        keyId: deterministic-key
```

!!! tip "Deterministic Encryption"
    Use `TINK/AES_GCM_SIV` (AES-GCM-SIV) when the encrypted field's ciphertext is supposed to be used e.g. in JOINs or GROUP BY operations to make sure that the same plaintext input value always produces the same ciphertext. Standard `TINK/AES_GCM` is probabilistic and produces different ciphertexts for each encryption of the same value.

---

## Format-Preserving Encryption (FPE)

FPE keeps the original format and length of field values. Configure it per-field via the `algorithm`, `fpeAlphabetType`, and optionally `fpeTweak` / `fpeAlphabetCustom` settings:

```yaml
cipher_data_keys:
  - identifier: my-fpe-key
    material:
      primaryKeyId: 2000001
      key:
        - keyData:
            typeUrl: io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey
            value: <BASE64_ENCODED_KEY_MATERIAL>
            keyMaterialType: SYMMETRIC
          status: ENABLED
          keyId: 2000001
          outputPrefixType: RAW

topic_field_configs:
  - topic_pattern: customers
    field_configs:
      # 16-digit credit card number stays 16 digits
      - name: credit_card_number
        algorithm: CUSTOM/MYSTO_FPE_FF3_1
        keyId: my-fpe-key
        fpeAlphabetType: DIGITS

      # SSN stays 9 digits
      - name: ssn
        algorithm: CUSTOM/MYSTO_FPE_FF3_1
        keyId: my-fpe-key
        fpeAlphabetType: DIGITS
        fpeTweak: myTweakValue

      # alphanumeric identifier preserves length and character set
      - name: reference_code
        algorithm: CUSTOM/MYSTO_FPE_FF3_1
        keyId: my-fpe-key
        fpeAlphabetType: ALPHANUMERIC
```

!!! danger "Important"
    Both the encrypt and decrypt filter must be configured with the **exact same** FPE settings (`algorithm`, `keyId`, `fpeAlphabetType`, `fpeTweak`) for a successful round-trip.

---

## Key Management

All key management options from the core library are supported. Set `key_source` and the related parameters to control where keyset material is loaded from.

```yaml
# Inline keysets (development / testing)
key_source: CONFIG
cipher_data_keys: [...]

# Inline keysets encrypted at rest using a cloud KMS KEK
key_source: CONFIG_ENCRYPTED
kek_type: GCP           # or AWS, AZURE
kek_uri: gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-key
kek_config: /path/to/gcp-credentials.json
cipher_data_keys: [...]

# Keysets stored in a cloud secret manager
key_source: KMS
kms_type: GCP_SM_SECRETS   # or AZ_KV_SECRETS, AWS_SM_SECRETS
kms_config: '{"projectId":"my-project"}'

# Keysets stored in a cloud secret manager AND encrypted at rest
key_source: KMS_ENCRYPTED
kms_type: GCP_SM_SECRETS
kms_config: '{"projectId":"my-project"}'
kek_type: GCP
kek_uri: gcp-kms://...
kek_config: /path/to/credentials.json
```

See the [Key Management](../key-management.md) and [Cloud KMS](../kms/overview.md) pages for details.

---

## Schema Registry Subjects

When `record_format` is `JSON_SR` or `AVRO`, the filter creates and manages additional schema registry subjects alongside the original record value schemas. All subject names are derived from the original `<topic>-value` subject and follow these naming conventions:

| Subject name | Purpose |
|---|---|
| `<topic>-value` | Original producer schema (untouched!) |
| `<topic>-value__k4k_enc` | Encrypted schema for which the original field types are replaced with type `string` for encrypted fields; consumed by direct SR clients reading encrypted data |
| `<topic>-value__k4k_meta<originalSchemaId>` | Encryption metadata, keyed by the original schema ID|
| `<topic>-value__k4k_meta<encryptedSchemaId>` | Encryption metadata, keyed by the encrypted schema ID |

!!! Note
    The metadata subject is registered twice for every original <-> encrypted schema pair to support direct lookups from either direction. A producer holding the original schema ID can resolve the encryption settings in the same way as a consumer can based on the encrypted schema ID. This means there is no need to perform any dedicated reverse-lookup requests. Since both subjects refer to the exact same schema content the stored schema entry actually only exists once with a single schema ID.

---

## Schema Modes

| `schema_mode` | Description |
|---|---|
| `DYNAMIC` (default) | The filter derives and registers encrypted/decrypted schemas automatically at runtime. Use this for most deployments as all the necessary actions happen transparently under the hood. |
| `STATIC` | Additional schemas must be pre-registered in the schema registry before the proxy is started to process any data. The proxy filter in this case will look them up on demand by the subjects naming convention rather than deriving and registering them. This mode is typically only used if strict schema governance envrionments would prohibit any ad hoc registration of schemas. In this mode, the proxy filter doesn't need schema registry write access. |

---

## Configuration Reference

**Configuration Settings common to both the encrypt and decrypt filter definitions:**

<div class="k4k-param-table" markdown="1">

| Parameter | Required | Default | Description |
|---|---|---|---|
| `topic_field_configs` | No | `[]` | List of topic pattern → field config mappings. An empty list is valid but results in a no-op filter where all records pass through unmodified. |
| `record_format` | Yes | — | Record format: `JSON`, `JSON_SR`, or `AVRO` |
| `key_source` | No | `CONFIG` | Key management mode: `CONFIG`, `CONFIG_ENCRYPTED`, `KMS`, `KMS_ENCRYPTED` |
| `cipher_algorithm` | No | `TINK/AES_GCM` | Default cipher algorithm for all fields |
| `cipher_data_key_identifier` | No | — | Default keyset identifier for encryption |
| `cipher_data_keys` | No | — | Inline keyset list (required when `key_source` is `CONFIG` or `CONFIG_ENCRYPTED`) |
| `kms_type` | No | `NONE` | Cloud secret manager type: `AZ_KV_SECRETS`, `AWS_SM_SECRETS`, `GCP_SM_SECRETS` |
| `kms_config` | No | — | Cloud secret manager config (JSON string or file path) |
| `kek_type` | No | `NONE` | Cloud KMS key-encryption-key type: `GCP`, `AWS`, `AZURE` |
| `kek_uri` | No | — | KEK URI |
| `kek_config` | No | — | KEK provider config (JSON string or file path) |
| `schema_registry_url` | No | — | Confluent Schema Registry base URL (required for `JSON_SR`, `AVRO`) |
| `schema_registry_config` | No | `{}` | Additional SR client properties (e.g. auth headers) as a string map |
| `schema_mode` | No | `DYNAMIC` | Schema handling mode: `DYNAMIC` or `STATIC` |
| `serde_type` | No | `KRYO` | Internal serde format for encrypted field envelopes: `KRYO` or `AVRO` |
| `blocking_pool_size` | No | JVM default | Size of the blocking executor thread pool used to dispatch blocking calls to |

</div>

**`topic_field_configs` entry:**

| Parameter | Required | Description |
|---|---|---|
| `topic_pattern` | Yes | Java regex matched against the topic name (implicitly anchored) |
| `field_configs` | Yes | List of field config entries |

**`field_configs` entry:**

<div class="k4k-param-table" markdown="1">

| Parameter | Required | Default | Description |
|---|---|---|---|
| `name` | Yes | — | Field name (dot-separated path for nested fields, e.g. `person.address.street`) |
| `fieldMode` | No | `OBJECT` | `OBJECT` or `ELEMENT` |
| `algorithm` | No | Filter default | Cipher algorithm override for this field |
| `keyId` | No | Filter default | Keyset identifier override for this field |
| `fpeTweak` | No | — | FPE tweak value (FPE algorithms only) |
| `fpeAlphabetType` | No | — | FPE alphabet type: `DIGITS`, `ALPHANUMERIC`, `LETTERS`, `CUSTOM` (FPE algorithms only) |
| `fpeAlphabetCustom` | No | — | Custom alphabet string (only when `fpeAlphabetType: CUSTOM`) |

</div>
