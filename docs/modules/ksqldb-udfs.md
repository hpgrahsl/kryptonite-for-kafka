# :material-database-search: ksqlDB UDFs

Kryptonite for Kafka provides multiple ksqlDB user-defined functions (UDFs) for encrypting and decrypting column values in ksqlDB `STREAM` and `TABLE` queries.

**Field-Level Encryption with UDFs in ksqlDB**

<div class="k4k-module-img">
<a href="../../assets/images/05a_csflc_ksqldb_encryption.png" class="glightbox" data-glightbox="type: image"><img src="../../assets/images/05a_csflc_ksqldb_encryption.png" alt="ksqlDB UDFs"></a>
</div>

**Field-Level Decryption with UDFs in ksqlDB**

<div class="k4k-module-img">
<a href="../../assets/images/05b_csflc_ksqldb_decryption.png" class="glightbox" data-glightbox="type: image"><img src="../../assets/images/05b_csflc_ksqldb_decryption.png" alt="ksqlDB UDFs"></a>
</div>

### UDFs Overview

| UDF               | Description                                                                            |
|-------------------|----------------------------------------------------------------------------------------|
| `K4K_ENCRYPT`     | Encrypt field data using probabilistic / deterministic ciphers (AES-GCM / AES-GCM-SIV) |
| `K4K_DECRYPT`     | Decrypt field data using probabilistic / deterministic ciphers (AES-GCM / AES-GCM-SIV) |
| `K4K_ENCRYPT_FPE` | Encrypt field data using format-preserving encryption (FPE FF3-1)                      |
| `K4K_DECRYPT_FPE` | Decrypt field data using format-preserving encryption (FPE FF3-1)                      |

---

## Installation / Deployment

1. Download the UDF JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Place it in the **ksqlDB extension directory** configured via `ksql.extension.dir` or using the env var `KSQL_KSQL_EXTENSION_DIR` for container deployments
3. (Re)Start the ksqlDB server(s).

Verify deployment:

```sql
SHOW FUNCTIONS;
```

Output:

```text
 Function Name           | Category
--------------------------------------------
 ....                    | ...

 K4K_DECRYPT             | cryptography
 K4K_DECRYPT_FPE         | cryptography
 K4K_ENCRYPT             | cryptography
 K4K_ENCRYPT_FPE         | cryptography

 ....                    | ...
--------------------------------------------
```

---

## Configuration

UDF configuration is set in `ksql-server.properties` or as container environment variables. Also, each UDF needs to be configured independently which means that you might end up with both `K4K_ENCRYPT` and `K4K_DECRYPT` settings in your configuration.

### Minimal `.properties` for encryption / decryption

```properties

# encryption settings
ksql.functions.k4k_encrypt.cipher_data_keys=[ { "identifier": "my-key", "material": { "primaryKeyId": 123456789, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_KEY>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 123456789, "outputPrefixType": "TINK" } ] } } ]
ksql.functions.k4k_encrypt.cipher_data_key_identifier=my-key

# decryption settings
ksql.functions.k4k_decrypt.cipher_data_keys=[ { "identifier": "my-key", "material": { "primaryKeyId": 123456789, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_KEY>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 123456789, "outputPrefixType": "TINK" } ] } } ]

```

### Minimal `env vars` for encryption / decryption

```yaml

# encryption settings
KSQL_KSQL_FUNCTIONS_K4K__ENCRYPT_CIPHER__DATA__KEYS: '[ { "identifier": "my-key", "material": { "primaryKeyId": 123456789, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_KEY>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 123456789, "outputPrefixType": "TINK" } ] } } ]'
KSQL_KSQL_FUNCTIONS_K4K__ENCRYPT_CIPHER__DATA__KEY__IDENTIFIER: "my-key"

# decryption settings
KSQL_KSQL_FUNCTIONS_K4K__DECRYPT_CIPHER__DATA__KEYS: '[ { "identifier": "my-key", "material": { "primaryKeyId": 123456789, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_KEY>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 123456789, "outputPrefixType": "TINK" } ] } } ]'
```

!!! note "ENV VAR naming"
    Confluent's Docker images convert environment variables to ksqlDB properties using these rules: dots (`.`) become single underscores (`_`), and underscores (`_`) become double underscores (`__`). Since Kryptonite's own config key contain underscores "_" separators (e.g. `cipher_data_keys`), each underscore in a config key becomes double underscores `__` in the environment variable name. The same applies to underscores in the UDF name itself (e.g. `k4k_encrypt` → `K4K__ENCRYPT`).

The same `key_source`, `kms_type`, `kms_config`, `kek_type`, `kek_config`, and `kek_uri` parameters are supported as for the other Kryptonite for Kafka modules. Prefix each with `ksql.functions.<udf-name>.` in `ksql-server.properties` (using `__` in env vars for any occurring underscore in a config key's name).

See the full [Configuration Reference](../configuration.md) for all available parameters.

---

## UDF Signatures

### K4K_ENCRYPT

```sql
-- Encrypt with configured defaults (key identifier + algorithm)
K4K_ENCRYPT(data T) → VARCHAR

-- Encrypt with explicit key identifier and algorithm
K4K_ENCRYPT(data T, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → VARCHAR

-- Encrypt complex types (object or element mode depends on typeCapture)
K4K_ENCRYPT(data U, typeCapture V) → V
K4K_ENCRYPT(data U, typeCapture V, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → V
```

`typeCapture` controls the return type and encryption mode:
- Pass `''` (empty string) for object mode → result is `VARCHAR`
- Pass `array['']` for element mode on arrays → result is `ARRAY<VARCHAR>`
- Pass `map('':='')` for element mode on maps → result is `MAP<VARCHAR,VARCHAR>`
- Pass `struct(...)` for element mode on structs → result is `STRUCT<...,VARCHAR,...>`

### K4K_DECRYPT

```sql
-- Decrypt to inferred type (object mode)
K4K_DECRYPT(data VARCHAR, typeCapture T) → T

-- Decrypt array elements (element mode)
K4K_DECRYPT(data ARRAY<VARCHAR>, typeCapture E) → ARRAY<E>

-- Decrypt map values (element mode)
K4K_DECRYPT(data MAP<K, VARCHAR>, typeCapture V) → MAP<K, V>
```

`typeCapture` is a dummy value used for type inference. Pass an exemplary value of the expected return type (e.g., `0` for INT, `false` for BOOLEAN, `array['']` for `ARRAY<STRING>`).

### K4K_ENCRYPT_FPE

```sql
-- With configured defaults
K4K_ENCRYPT_FPE(data VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data ARRAY<VARCHAR>) → ARRAY<VARCHAR>
K4K_ENCRYPT_FPE(data STRUCT<..., VARCHAR, ...>) → STRUCT<>

-- With explicit parameters
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm) → VARCHAR
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak) → VARCHAR
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType) → VARCHAR
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType, fpeAlphabetCustom) → VARCHAR
```

### K4K_DECRYPT_FPE

```sql
-- With configured defaults
K4K_DECRYPT_FPE(data VARCHAR) → VARCHAR
K4K_DECRYPT_FPE(data ARRAY<VARCHAR>) → ARRAY<VARCHAR>
K4K_DECRYPT_FPE(data STRUCT<... VARCHAR, ...>) → STRUCT<... VARCHAR, ...>

-- With explicit parameters
K4K_DECRYPT_FPE(data, keyIdentifier, cipherAlgorithm) → VARCHAR
K4K_DECRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak) → VARCHAR
K4K_DECRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType) → VARCHAR
K4K_DECRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType, fpeAlphabetCustom) → VARCHAR
```

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
  K4K_ENCRYPT('1234567890'),
  K4K_ENCRYPT('some text'),
  K4K_ENCRYPT(42),
  K4K_ENCRYPT(true)
);
```

### Object mode decryption

```sql
SELECT
  K4K_DECRYPT(id, '') AS id,
  K4K_DECRYPT(mystring, '') AS mystring,
  K4K_DECRYPT(myint, 0) AS myint,
  K4K_DECRYPT(myboolean, false) AS myboolean
FROM my_data_encrypted
EMIT CHANGES LIMIT 1;
```

### Element mode encryption

This example encrypts `ARRAY` elements and `MAP` values:

```sql
CREATE STREAM my_data_enc_e (
  id VARCHAR,
  myarray ARRAY<VARCHAR>,
  mymap MAP<VARCHAR, VARCHAR>
) WITH (KAFKA_TOPIC='my_data_enc_e', VALUE_FORMAT='JSON', PARTITIONS=1);

INSERT INTO my_data_enc_e VALUES (
  K4K_ENCRYPT('1234567890'),
  K4K_ENCRYPT(array['str_1','str_2','str_3'], array['']),  -- element mode
  K4K_ENCRYPT(map('k1':=9,'k2':=8,'k3':=7), map('':=''))   -- element mode
);
```

### Element mode decryption:

This example decrypts `ARRAY` elements and `MAP` values:

```sql
SELECT
  K4K_DECRYPT(id, '') AS id,
  K4K_DECRYPT(myarray, '') AS myarray,     -- decrypt each element
  K4K_DECRYPT(mymap, 0) AS mymap           -- decrypt each map value
FROM my_data_enc_e
EMIT CHANGES LIMIT 1;
```

### FPE encryption

```sql
-- Credit card numbers remain 16 digits after FPE encryption
INSERT INTO customer_fpe_encrypted
SELECT
  customer_id,
  K4K_ENCRYPT_FPE(credit_card_number, 'myFpeKey', 'CUSTOM/MYSTO_FPE_FF3_1', 'CCN_FIELD', 'DIGITS') AS credit_card_number,
  K4K_ENCRYPT_FPE(ssn, 'myFpeKey', 'CUSTOM/MYSTO_FPE_FF3_1', 'SSN_FIELD', 'DIGITS') AS ssn
FROM customer_plaintext
EMIT CHANGES;
```

---

## Complex Data Type Mapping

| Original type | `OBJECT` mode | `ELEMENT` mode |
|---|---|---|
| `ARRAY<T>` | `VARCHAR` | `ARRAY<VARCHAR>` |
| `MAP<K,V>` | `VARCHAR` | `MAP<K,VARCHAR>` |
| `STRUCT<...>` | `VARCHAR` | `STRUCT<...,VARCHAR,...>` |
