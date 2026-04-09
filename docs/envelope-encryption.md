# Envelope Encryption

By default, Kryptonite for Kafka applies encryption directly based on Tink keyset. An alternative mode is to opt-in for [envelope encryption](https://en.wikipedia.org/wiki/Hybrid_cryptosystem#Envelope_encryption) for which a short-lived **Data Encryption Key (DEK)** is used to encrypt the field data, and a long-lived **Key Encryption Key (KEK)** wraps the DEK. The wrapped DEK travels with the ciphertext and there is no direct relationship between the ciphertext and the long-lived key material (KEK).

**Key benefits:**

- **KEK never touches field data:** the KEK is only used to wrap/unwrap DEKs; all field encryption uses the short-lived DEK
- **Independent DEK and KEK lifecycle:** DEKs rotate automatically on a high-frequency operational schedule; the KEK rotates on a slower administrative schedule; these two concerns are fully decoupled
- **Reduced blast radius:** a compromised DEK only affects records encrypted during that DEK's lifetime which is configurable

---

## Two Variants

Kryptonite for Kafka supports two envelope encryption variants which differ in where the KEK lives and how the wrapped DEK is stored alongside the ciphertext.

| | Keyset-based | KMS-based |
|---|---|---|
| **Algorithm** | `TINK/AES_GCM_ENVELOPE_KEYSET` | `TINK/AES_GCM_ENVELOPE_KMS` |
| **KEK** | A Tink keyset (from config or cloud secret manager) | A cloud KMS key which never leaves the KMS |
| **Wrapped DEK** | Bundled as is with the ciphertext | Stored externally in an `EdekStore` implementation (defaults to KCache) |
| **Extra Infrastructure** | None | Persistent Storage (defaults to Kafka topic) |

---

## Keyset-based Envelope Encryption

Uses a regular Tink keyset as the KEK. The wrapped DEK is bundled directly inside the ciphertext alongside the encrypted field data, so no external store is needed.

**Wire format:** `[4-byte wrappedDekLen | wrappedDek | dekCiphertext]`

**Configuration:**

```properties
cipher_algorithm=TINK/AES_GCM_ENVELOPE_KEYSET
cipher_data_key_identifier=my-kek-keyset
cipher_data_keys=[{"identifier":"my-kek-keyset","material":{...}}]
```

The `cipher_data_key_identifier` and `cipher_data_keys` refer to the keyset acting as the KEK. All the usual `key_source` options apply which means you can source the KEK keyset from configuration (plain or encrypted) or from a cloud secret manager.

!!! tip "DEK session caching"
    By default a single DEK session is reused for up to 100,000 encryptions or 720 minutes, whichever comes first. Tune `dek_max_encryptions` and `dek_ttl_minutes` to balance KMS call frequency against DEK lifetime. See [Configuration](configuration.md#envelope-encryption-parameters) for details.

---

## KMS-based Envelope Encryption

Uses a cloud KMS key as the KEK. The KEK never leaves the cloud KMS and all wrap/unwrap operations happen remotely. Because the wrapped DEK would make every ciphertext considerably larger if bundled inline, only a compact 16-byte fingerprint is embedded in the ciphertext; the actual wrapped DEK is stored externally using implementations of `EdekStore` for the default is KCache/Kafka.

**Wire format:** `[16-byte fingerprint | dekCiphertext]`

**Configuration:**

```properties
cipher_algorithm=TINK/AES_GCM_ENVELOPE_KMS
envelope_kek_configs=[{"identifier":"my-kek","type":"GCP","uri":"gcp-kms://...","config":{"credentials":"...","projectId":"..."}}]
envelope_kek_identifier=my-kek
edek_store_config={"kafkacache.bootstrap.servers":"localhost:9092","kafkacache.topic":"_k4k_edeks"}
```

!!! warning "An EdekStore implementation is required"
    KMS-based envelope encryption will refuse to start if `edek_store_config` is not provided. The EdekStore is load-bearing as without it the wrapped DEK could not be derived at decrypt time.

### The EdekStore

The EdekStore defaults to KCache which persists into a compacted Kafka topic to permanently store the wrapped DEKs. Each record maps a 16-byte fingerprint (a SHA-256 prefix of the wrapped DEK) to the actual wrapped DEK bytes.

**Encrypt path:**

1. When a new DEK session is created, the wrapped DEK is published to the EdekStore before any encryption happens, only then is the DEK session made available for use
2. Each ciphertext carries only the 16-byte fingerprint as a compact pointer to the wrapped DEK in the EdekStore

**Decrypt path:**

1. Extract the 16-byte fingerprint from the ciphertext
2. Check the L1 in-process cache (fingerprint → Aead): if hit, decrypt directly; no EdekStore or KMS involved
3. On L1 miss: look up the fingerprint in the EdekStore to retrieve the wrapped DEK
4. Call the KMS to unwrap the DEK, build the Aead, and populate the L1 cache for future lookups
5. Decrypt the field data with the Aead

**Topic requirements:**

- Must be a compacted topic — `cleanup.policy=compact`
- `kafkacache.topic.require.compact=true` is enforced by default; Kryptonite for Kafka will refuse to start against a non-compacted topic
- Replication factor and partition count follow standard Kafka best practices for your environment; a single partition is sufficient since write volume is very low (one record per DEK session)

!!! info "Cross-Instance Consumer Lag"
    Each instance maintains a local in-memory mirror of the EdekStore topic via a background KCache consumer thread. After a new wrapped DEK is published, other instances will only see it once their consumer thread has caught up to that offset. If a decrypt request for a freshly-encrypted record reaches an instance whose consumer hasn't yet processed the new entry, the fingerprint lookup will fail. In practice this lag should be sub-second under normal conditions, and retry logic in the consumer application is the recommended mitigation.

### EdekStore configuration

The `edek_store_config` value is a JSON object and is supposed to contain the expected configuration based on the chosen EdekStore implementation. For KCache/Kafka it might look like this:

```json
{
  "kafkacache.bootstrap.servers": "broker1:9092,...",
  "kafkacache.topic": "_k4k_edeks"
}
```

**Required keys for KCache/Kafka EdekStore**

| Key | Description |
|---|---|
| `kafkacache.bootstrap.servers` | Kafka bootstrap address for the EdekStore topic |
| `kafkacache.topic` | Name of the compacted topic to use |

**Optional overrides** (defaults shown):

| Key | Default | Description |
|---|---|---|
| `kafkacache.backing.cache` | `memory` | In-memory map; the compacted topic is the durable store |
| `kafkacache.topic.require.compact` | `true` | Fail at startup if the topic is not compacted |

!!! note
    For security settings (SSL, SASL, authentication) use the corresponding `kafkacache.*` prefixed keys defined by KCache — for example `kafkacache.security.protocol`, `kafkacache.ssl.truststore.location`, `kafkacache.sasl.mechanism`. Raw Kafka `ssl.*` / `sasl.*` keys are not forwarded directly.

---

## DEK Session Lifecycle

Both variants share the same DEK session management. Instead of generating a fresh DEK for every field encryption (which would mean a KMS/keyset operation per record), a DEK session is reused across a configurable window.

A session expires when **either** threshold is reached first:

- **`dek_max_encryptions`:** maximum number of field encryptions with this DEK (default: `100,000`)
- **`dek_ttl_minutes`:** maximum age of the DEK session (default: `720` = 12 hours)

When a session expires, a new DEK is generated and wrapped transparently without the need for intervention. For KMS-based envelope encryption the new wrapped DEK is published to the `EdekStore` before the session becomes active.

!!! tip "Tuning Guidance"
    Lower `dek_max_encryptions` or `dek_ttl_minutes` to increase DEK freshness at the cost of more frequent KMS calls. The defaults are reasonable and suit most workloads. For KMS-based envelope encryption each session creation involves one KMS network call, so very aggressive rotation (e.g., `dek_max_encryptions=1`) will not only significantly impact throughput but might saturate the KMS quickly and noticeably increase cloud KMS costs.

---

## KEK configuration for KMS-based envelope encryption

The `envelope_kek_configs` parameter takes a JSON array of KEK entries. Each entry identifies which cloud KMS key to use as the KEK:

=== "GCP Cloud KMS"
    ```json
    [
      {
        "identifier": "my-gcp-kek",
        "type": "GCP",
        "uri": "gcp-kms://projects/<project>/locations/<location>/keyRings/<ring>/cryptoKeys/<key>",
        "config": {
          "credentials": "<GCP service account JSON contents>",
          "projectId": "<project>"
        }
      }
    ]
    ```

=== "AWS KMS"
    ```json
    [
      {
        "identifier": "my-aws-kek",
        "type": "AWS",
        "uri": "aws-kms://arn:aws:kms:<region>:<account>:key/<key-id>",
        "config": {
          "accessKey": "...",
          "secretKey": "...",
          "region": "..."
        }
      }
    ]
    ```

Multiple KEK entries are supported. Each field can reference a different KEK identifier, enabling per-field or per-topic key isolation.

!!! warning "Azure Key Vault not supported for KMS-based envelope encryption"
    Azure Key Vault (`kek_type=AZURE`) is not currently supported as a KEK for `TINK/AES_GCM_ENVELOPE_KMS`. Attempting to configure it will throw at startup. Azure Key Vault can still be used for keyset encryption (`key_source=CONFIG_ENCRYPTED` / `KMS_ENCRYPTED`) as normal.

See [Cloud KMS](kms/overview.md) for provider-specific IAM permissions and setup details.
