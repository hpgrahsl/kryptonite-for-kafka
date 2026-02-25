# Getting Started

Kryptonite for Kafka ships as four independent integration modules. Pick the one that fits your stack and follow the module-specific guide.

<div class="grid cards" markdown>

-   :material-transit-connection-variant: &nbsp; **Kafka Connect SMT**

    ---

    Encrypt and decrypt fields directly inside any Kafka Connect source or sink connector — zero code, configuration only.

    [:octicons-arrow-right-24: Get started with Connect SMT](getting-started/connect-smt.md)

-   :material-table-arrow-right: &nbsp; **Apache Flink UDFs**

    ---

    Use `K4K_ENCRYPT` / `K4K_DECRYPT` and companions in Flink Table API / SQL pipelines.

    [:octicons-arrow-right-24: Get started with Flink UDFs](getting-started/flink-udfs.md)

-   :material-api: &nbsp; **Quarkus HTTP API**

    ---

    Run a lightweight REST service and encrypt or decrypt fields from any language over HTTP.

    [:octicons-arrow-right-24: Get started with the HTTP API](getting-started/funqy-http.md)

-   :material-database-search: &nbsp; **ksqlDB UDFs**

    ---

    Use `K4KENCRYPT` / `K4KDECRYPT` and companions inline in ksqlDB streams and tables.

    [:octicons-arrow-right-24: Get started with ksqlDB UDFs](getting-started/ksqldb-udfs.md)

</div>

---

## Common Prerequisites

All modules share the same baseline requirements:

- **Java 17+** on the host running the module
- A running **Apache Kafka** cluster reachable from the module's runtime
- Cryptographic key material — generate it with the [Keyset Tool](keyset-tool.md) or embed an existing Tink keyset

---

## Generating a Keyset

Every module needs at least one Tink keyset. The quickest way is the [Keyset Tool](keyset-tool.md):

```bash
java -jar kryptonite-keyset-tool/target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-demo-key -f FULL -p
```

This prints a `FULL`-format keyset (with the `identifier` wrapper expected by all modules) directly to stdout:

```json
{
  "identifier": "my-demo-key",
  "material": {
    "primaryKeyId": 10000,
    "key": [{
      "keyData": {
        "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value": "<BASE64_ENCODED_KEY>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 10000,
      "outputPrefixType": "TINK"
    }]
  }
}
```

!!! warning "Key material is a secret"
    The `value` field is your raw key. Treat it like a password — do not commit it to source control.

See [Key Management](key-management.md) for production key storage options (cloud KMS, encrypted keysets).
