# ksqlDB UDFs

Kryptonite for Kafka provides four ksqlDB user-defined functions:

| UDF | Description |
|---|---|
| `K4KENCRYPT` | Encrypt field data using AEAD (AES-GCM / AES-GCM-SIV) |
| `K4KDECRYPT` | Decrypt AEAD-encrypted field data |
| `K4KENCRYPTFPE` | Encrypt field data using Format-Preserving Encryption (FPE FF3-1) |
| `K4KDECRYPTFPE` | Decrypt FPE-encrypted field data |

---

## Installation

1. Download the UDF JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Place it in the **ksqlDB extension directory** configured via `ksql.extension.dir`.
3. Restart the ksqlDB server(s).

Verify deployment:

```sql
SHOW FUNCTIONS;
-- Output includes: K4KDECRYPT, K4KDECRYPTFPE, K4KENCRYPT, K4KENCRYPTFPE
```

---

## Configuration

UDF configuration is set in `ksql-server.properties` (or as Docker environment variables). Each UDF is configured independently.

### Minimal configuration for K4KENCRYPT

```properties
ksql.functions.k4kencrypt.cipher.data.keys=[ { "identifier": "my-key", "material": { "primaryKeyId": 123456789, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_KEY>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 123456789, "outputPrefixType": "TINK" } ] } } ]
ksql.functions.k4kencrypt.cipher.data.key.identifier=my-key
```

### Docker / docker-compose equivalent

```yaml
KSQL_KSQL_FUNCTIONS_K4KENCRYPT_CIPHER_DATA_KEYS: "[{ \"identifier\": \"my-key\", ... }]"
KSQL_KSQL_FUNCTIONS_K4KENCRYPT_CIPHER_DATA_KEY_IDENTIFIER: "my-key"
```

### Key management parameters

The same `key.source`, `kms.type`, `kms.config`, `kek.type`, `kek.config`, and `kek.uri` parameters as other modules are supported. Prefix each with `ksql.functions.<udf-name>.` in `ksql-server.properties`.

See the [Configuration Reference](../configuration.md) for all available parameters (note: ksqlDB uses dot-separated names).

---

## UDF Signatures

### K4KENCRYPT

```sql
-- Encrypt with configured defaults (key identifier + algorithm)
K4KENCRYPT(data T) → VARCHAR

-- Encrypt with explicit key identifier and algorithm
K4KENCRYPT(data T, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → VARCHAR

-- Encrypt complex types (object or element mode depends on typeCapture)
K4KENCRYPT(data U, typeCapture V) → V
K4KENCRYPT(data U, typeCapture V, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → V
```

`typeCapture` controls the return type and encryption mode:
- Pass `''` (empty string) for object mode → result is `VARCHAR`
- Pass `array['']` for element mode on arrays → result is `ARRAY<VARCHAR>`
- Pass `map('':='')` for element mode on maps → result is `MAP<VARCHAR,VARCHAR>`
- Pass `struct(...)` for element mode on structs → result is `STRUCT<...,VARCHAR,...>`

### K4KDECRYPT

```sql
-- Decrypt to inferred type (object mode)
K4KDECRYPT(data VARCHAR, typeCapture T) → T

-- Decrypt array elements (element mode)
K4KDECRYPT(data ARRAY<VARCHAR>, typeCapture E) → ARRAY<E>

-- Decrypt map values (element mode)
K4KDECRYPT(data MAP<K, VARCHAR>, typeCapture V) → MAP<K, V>
```

`typeCapture` is a dummy value used for type inference. Pass an exemplary value of the expected return type (e.g., `0` for INT, `false` for BOOLEAN, `array['']` for `ARRAY<STRING>`).

### K4KENCRYPTFPE / K4KDECRYPTFPE

```sql
-- With configured defaults
K4KENCRYPTFPE(data VARCHAR) → VARCHAR
K4KENCRYPTFPE(data ARRAY<VARCHAR>) → ARRAY<VARCHAR>
K4KENCRYPTFPE(data STRUCT<>) → STRUCT<>

-- With explicit parameters
K4KENCRYPTFPE(data, keyIdentifier, cipherAlgorithm) → same type
K4KENCRYPTFPE(data, keyIdentifier, cipherAlgorithm, fpeTweak) → same type
K4KENCRYPTFPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType) → same type
K4KENCRYPTFPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType, fpeAlphabetCustom) → same type
```

`K4KDECRYPTFPE` has the same signatures.

---

## Examples

### Object mode encryption

```sql
-- Create target stream with VARCHAR columns for encrypted fields
CREATE STREAM my_data_encrypted (
  id VARCHAR,
  mystring VARCHAR,  -- will hold Base64 ciphertext
  myint VARCHAR,
  myboolean VARCHAR
) WITH (KAFKA_TOPIC='my_data_enc', VALUE_FORMAT='JSON', PARTITIONS=1);

-- Encrypt all columns on insert
INSERT INTO my_data_encrypted VALUES (
  K4KENCRYPT('1234567890'),
  K4KENCRYPT('some text'),
  K4KENCRYPT(42),
  K4KENCRYPT(true)
);
```

### Object mode decryption

```sql
SELECT
  K4KDECRYPT(id, '') AS id,
  K4KDECRYPT(mystring, '') AS mystring,
  K4KDECRYPT(myint, 0) AS myint,
  K4KDECRYPT(myboolean, false) AS myboolean
FROM my_data_encrypted
EMIT CHANGES LIMIT 5;
```

### Element mode encryption (arrays and maps)

```sql
CREATE STREAM my_data_enc_e (
  id VARCHAR,
  myarray ARRAY<VARCHAR>,
  mymap MAP<VARCHAR, VARCHAR>
) WITH (KAFKA_TOPIC='my_data_enc_e', VALUE_FORMAT='JSON', PARTITIONS=1);

INSERT INTO my_data_enc_e VALUES (
  K4KENCRYPT('1234567890'),
  K4KENCRYPT(array['str_1','str_2','str_3'], array['']),  -- element mode
  K4KENCRYPT(map('k1':=9,'k2':=8,'k3':=7), map('':=''))   -- element mode
);
```

Element mode decryption:

```sql
SELECT
  K4KDECRYPT(id, '') AS id,
  K4KDECRYPT(myarray, '') AS myarray,     -- decrypt each element
  K4KDECRYPT(mymap, 0) AS mymap           -- decrypt each map value
FROM my_data_enc_e
EMIT CHANGES LIMIT 5;
```

### FPE encryption

```sql
-- Credit card numbers remain 16 digits after FPE encryption
INSERT INTO customer_fpe_encrypted
SELECT
  customer_id,
  K4KENCRYPTFPE(credit_card_number, 'myFpeKey', 'CUSTOM/MYSTO_FPE_FF3_1', 'CCN_FIELD', 'DIGITS') AS credit_card_number,
  K4KENCRYPTFPE(ssn, 'myFpeKey', 'CUSTOM/MYSTO_FPE_FF3_1', 'SSN_FIELD', 'DIGITS') AS ssn
FROM customer_plaintext
EMIT CHANGES;
```

---

## Data Type Mapping

| Original type | Encrypted as (object mode) | Encrypted as (element mode) |
|---|---|---|
| `VARCHAR` | `VARCHAR` | — |
| `INT`, `BIGINT`, `DOUBLE` | `VARCHAR` | — |
| `BOOLEAN` | `VARCHAR` | — |
| `ARRAY<T>` | `VARCHAR` | `ARRAY<VARCHAR>` |
| `MAP<K,V>` | `VARCHAR` | `MAP<K,VARCHAR>` |
| `STRUCT<...>` | `VARCHAR` | `STRUCT<...,VARCHAR,...>` |
