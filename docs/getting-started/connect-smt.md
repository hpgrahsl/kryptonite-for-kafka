# Getting Started — Kafka Connect SMT

The `CipherField` Single Message Transformation (SMT) encrypts and decrypts individual record fields inside any Kafka Connect source or sink connector. No custom code required — everything is driven by connector configuration.

---

## Prerequisites

- **Java 17+** on every Connect worker node
- A running Kafka Connect cluster (standalone or distributed)
- A Kafka Connect plugin path configured on all workers (`plugin.path` in `connect-distributed.properties`)

---

## Step 1 — Install the SMT

### Option A — Pre-built artifact (recommended)

1. Download the ZIP archive from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Extract the archive. The root folder of the extracted content is the plugin directory.
3. Copy (or move) that folder into your Connect worker's `plugin.path`.
4. **Restart all Connect workers** so the new plugin is picked up.

Verify the plugin is loaded:

```bash
curl -s http://localhost:8083/connector-plugins | jq '.[].class' | grep -i kryptonite
```

### Option B — Build from source

```bash
git clone https://github.com/hpgrahsl/kryptonite-for-kafka.git
cd kryptonite-for-kafka
./mvnw clean package -DskipTests -pl connect-transform-kryptonite
```

The plugin ZIP is produced under `connect-transform-kryptonite/target/`.

---

## Step 2 — Generate a keyset

Use the [Keyset Tool](../keyset-tool.md) to produce the key material. For a minimal setup, generate a plain AES-GCM keyset in `FULL` format:

```bash
java -jar kryptonite-keyset-tool/target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-demo-key -f FULL -p
```

Copy the printed JSON — you will paste it into the connector config in the next step.

!!! warning "Key material is a secret"
    Do not commit key material to source control. For production use, see [Key Management](../key-management.md).

---

## Step 3 — Configure a connector

Add the SMT to any connector by including `transforms` properties in the connector config. The example below encrypts three fields of a schemaless JSON record:

```json
{
  "transforms": "cipher",
  "transforms.cipher.type": "com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-key\",\"material\":{\"primaryKeyId\":10000,\"key\":[{\"keyData\":{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\",\"value\":\"<BASE64_KEY>\",\"keyMaterialType\":\"SYMMETRIC\"},\"status\":\"ENABLED\",\"keyId\":10000,\"outputPrefixType\":\"TINK\"}]}}]",
  "transforms.cipher.cipher_data_key_identifier": "my-demo-key",
  "transforms.cipher.field_config": "[{\"name\":\"creditCardNumber\"},{\"name\":\"ssn\"},{\"name\":\"email\"}]",
  "transforms.cipher.field_mode": "OBJECT"
}
```

The corresponding decryption config differs only in `cipher_mode`:

```json
{
  "transforms.cipher.cipher_mode": "DECRYPT"
}
```

!!! tip "Key safety via file config provider"
    Embedding key material in connector JSON exposes it through the Connect REST API. Use the Kafka `FileConfigProvider` to keep secrets out of the connector config — see [Kafka Connect SMT](../modules/connect-smt.md) for details.

---

## Step 4 — Verify

After deploying the connector, produce a test record and consume it from the output topic. Encrypted fields appear as Base64-encoded strings; all other fields are unchanged:

```json
{
  "customerId": "CUST-42",
  "creditCardNumber": "UuEKnrv91bLImQvKqXTET7RTP93XeLfNRhzJaXVc6OGA...",
  "ssn": "M007MIScg8F0A/cAddWbayvUPObjxuGFxisu5MUckDhB...",
  "email": "fLAnBod5U8eS+LVNEm3vDJ1m32/HM170ASgJLKdPF78q...",
  "amount": 99.95
}
```

To confirm decryption, deploy a second connector with `cipher_mode: DECRYPT` pointing at the encrypted topic.

---

## Next Steps

- [Kafka Connect SMT reference](../modules/connect-smt.md) — full configuration options, FPE, schema-aware records, field modes
- [Configuration reference](../configuration.md) — all parameters and their naming conventions
- [Key Management](../key-management.md) — moving from inline keys to cloud KMS
- [Keyset Tool](../keyset-tool.md) — generate keys for all algorithms and KMS backends
