# Getting Started Guides

Kryptonite for Kafka ships four independent integration modules. Pick the ones that fit your use cases and follow the module-specific guide.

<div class="grid cards" markdown>

-   :material-transit-connection-variant: &nbsp; **Kafka Connect SMT**

    ---

    Encrypt and decrypt payload fields directly inside any Kafka Connect source or sink connector by applying the SMTs. Fully configurable, zero code required.

    :octicons-book-24: [Learn more](modules/connect-smt.md)

    :octicons-rocket-24: [Getting Started Guide (_coming soon_)](./getting-started.md)

-   :material-table-arrow-right: &nbsp; **Apache Flink UDFs**

    ---

    Use any of the available UDFs `K4K_ENCRYPT_*` / `K4K_DECRYPT_*` to encrypt and decrypt data in Flink Table API / Flink SQL jobs.

    :octicons-book-24: [Learn more](modules/flink-udfs.md)

    :octicons-rocket-24: [Getting Started Guide (_coming soon_)](./getting-started.md)

-   :material-database-search: &nbsp; **ksqlDB UDFs**

    ---

    Use `K4KENCRYPT` / `K4KDECRYPT` and companions in ksqlDB queries to encrypt and decrypt data in ksqlDB streams and tables.

    :octicons-book-24: [Learn more](modules/ksqldb-udfs.md)

    :octicons-rocket-24: [Getting Started Guide (_coming soon_)](./getting-started.md)

-   :material-api: &nbsp; **Quarkus HTTP API**

    ---

    Start a lightweight HTTP service and use the provided web API endpoints to encrypt and decrypt fields from any application or tool that talks HTTP.

    :octicons-book-24: [Learn more](modules/funqy-http.md)

    :octicons-rocket-24: [Getting Started Guide (_coming soon_)](./getting-started.md)

</div>

---

## Common Prerequisites

Kryptonite for Kafka modules share the same baseline requirements:

- **Java 17+** available on the host running the respective Kryptonite for Kafka module
- **Key material:** generate keysets either with the purpose-built [Keyset Tool](keyset-tool.md) or embed an existing Tink keyset
- **Apache Kafka:** a running cluster reachable from the module's runtime

---

## Generating a Keyset

Every module needs at least one Tink keyset. The most convenient and quickest way is to use the [Keyset Tool](keyset-tool.md) and generate, for instance, a single keyset containing one `AES_GCM` key like so:

```bash
java -jar kryptonite-keyset-tool/target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-demo-key -f FULL -p
```

This generates and pretty prints a `FULL`-formatted keyset to `stdout`:

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
    The `value` field is the actual raw key in Base64 encoding. Treat it with utmost secrecy, just like any important password. **NEVER commit production keysets to source control!**

See [Key Management](key-management.md) for production options regarding key storage and optional key encryption.
