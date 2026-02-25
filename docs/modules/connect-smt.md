# Kafka Connect SMT

The `CipherField` Single Message Transformation (SMT) provides field-level encryption and decryption for Kafka Connect source and sink connectors. It works with both schemaless (JSON) and schema-aware (Avro, Protobuf, JSON Schema) records.

---

## Installation

1. Download the ZIP archive from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Extract the archive and place the root folder into your Kafka Connect **plugin path** (configured via `plugin.path` on the worker).
3. Restart the Connect worker(s).

If building from source:

```bash
./mvnw clean package -DskipTests -pl connect-transform-kryptonite
```

---

## Basic Configuration

The SMT is registered as a transformation on a connector:

```json
{
  "transforms": "cipher",
  "transforms.cipher.type": "com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value"
}
```

Use `CipherField$Value` to transform the record value, `CipherField$Key` for the record key.

---

## Encryption

### Schemaless record — encrypt selected fields

Given a JSON record value:

```json
{
  "id": "1234567890",
  "myString": "some foo bla text",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString": "hello json"},
  "myArray1": ["str_1", "str_2", "str_N"],
  "mySubDoc2": {"k1": 9, "k2": 8, "k3": 7}
}
```

To encrypt `myString`, `myArray1`, and `mySubDoc2`:

```json
{
  "transforms": "cipher",
  "transforms.cipher.type": "com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-key\",\"material\":{\"primaryKeyId\":123456789,\"key\":[{\"keyData\":{\"typeUrl\":\"type.googleapis.com/google.crypto.tink.AesGcmKey\",\"value\":\"<BASE64_KEY>\",\"keyMaterialType\":\"SYMMETRIC\"},\"status\":\"ENABLED\",\"keyId\":123456789,\"outputPrefixType\":\"TINK\"}]}}]",
  "transforms.cipher.cipher_data_key_identifier": "my-key",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT"
}
```

Result — encrypted fields are Base64-encoded strings:

```json
{
  "id": "1234567890",
  "myString": "M007MIScg8F0A/cAddWbayvUPObjxuGFxisu5MUckDhB...",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString": "hello json"},
  "myArray1": "UuEKnrv91bLImQvKqXTET7RTP93XeLfNRhzJaXVc6OGA...",
  "mySubDoc2": "fLAnBod5U8eS+LVNEm3vDJ1m32/HM170ASgJLKdPF78qDx..."
}
```

### Schema-aware record — encryption

Schema-aware encryption requires the same configuration. The SMT automatically **redacts the schema** so that encrypted fields are represented as `STRING` type, regardless of their original type.

### Schema-aware record — decryption

For decryption of schema-aware records, include `schema` in each field config entry so the SMT can restore the original schema:

```json
"transforms.cipher.field_config": "[{\"name\":\"myString\",\"schema\":{\"type\":\"STRING\"}},{\"name\":\"myArray1\",\"schema\":{\"type\":\"ARRAY\",\"valueSchema\":{\"type\":\"STRING\"}}},{\"name\":\"mySubDoc2\",\"schema\":{\"type\":\"MAP\",\"keySchema\":{\"type\":\"STRING\"},\"valueSchema\":{\"type\":\"INT32\"}}}]"
```

---

## Format-Preserving Encryption (FPE)

FPE keeps the original format and length of field values. Configure it per-field via `field_config`:

```json
"transforms.cipher.field_config": "[{\"name\":\"creditCardNumber\",\"algorithm\":\"CUSTOM/MYSTO_FPE_FF3_1\",\"fpeAlphabetType\":\"DIGITS\"},{\"name\":\"ssn\",\"algorithm\":\"CUSTOM/MYSTO_FPE_FF3_1\",\"fpeAlphabetType\":\"DIGITS\"}]"
```

After FPE encryption a 16-digit number remains a 16-digit number; a 9-digit SSN remains a 9-digit SSN.

The FPE keyset uses a different `typeUrl`:

```json
{
  "identifier": "my-fpe-key",
  "material": {
    "primaryKeyId": 2000001,
    "key": [{
      "keyData": {
        "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
        "value": "<BASE64_ENCODED_FPE_KEY>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 2000001,
      "outputPrefixType": "RAW"
    }]
  }
}
```

---

## Field Mode

| `field_mode` | Behaviour for complex types |
|---|---|
| `OBJECT` | Entire field (array, map, nested document) is serialised and encrypted as one blob → always stored as `VARCHAR`/`STRING` |
| `ELEMENT` | Each element of an array or map value is encrypted individually → result preserves the container shape |

Default: `ELEMENT`

---

## Externalising Key Material

Key material in the connector JSON is visible via the Connect REST API. Use the file-based config provider to keep secrets out of the connector configuration.

**1. Add to the Connect worker configuration:**

```properties
connect.config.providers=file
connect.config.providers.file.class=org.apache.kafka.common.config.provider.FileConfigProvider
```

**2. Create a properties file on each worker node** (e.g. `/secrets/kryptonite/classified.properties`):

```properties
cipher_data_keys=[{"identifier":"my-key","material":{...}}]
```

**3. Reference it in the connector config:**

```json
{
  "transforms.cipher.cipher_data_keys": "${file:/secrets/kryptonite/classified.properties:cipher_data_keys}"
}
```

---

## Configuration Reference

See the full [Configuration Reference](../configuration.md) for all parameters. Connect-specific naming uses underscores (`cipher_data_keys`, `cipher_mode`, etc.).

Key parameters:

| Parameter | Description |
|---|---|
| `cipher_mode` | `ENCRYPT` or `DECRYPT` |
| `cipher_data_keys` | JSON array of keyset objects |
| `cipher_data_key_identifier` | Default keyset identifier for encryption |
| `field_config` | JSON array of field names (and optional per-field overrides) |
| `field_mode` | `OBJECT` or `ELEMENT` |
| `key_source` | `CONFIG`, `CONFIG_ENCRYPTED`, `KMS`, or `KMS_ENCRYPTED` |
| `cipher_algorithm` | Default algorithm: `TINK/AES_GCM`, `TINK/AES_GCM_SIV`, `CUSTOM/MYSTO_FPE_FF3_1` |
| `path_delimiter` | Separator for nested field names (default `.`) |
