# Apache Flink UDFs

Kryptonite for Kafka provides multiple Flink user-defined functions (UDFs) for encrypting and decrypting column values in Flink Table API and Flink SQL jobs.

## UDFs Overview

### Probabilistic / Deterministic Encryption

| UDF | Description |
|---|---|
| `k4k_encrypt` | Encrypt scalar or complex values |
| `k4k_decrypt_with_schema` | Decrypt scalar or complex values using a schema string literal for the expected target type |
| `k4k_encrypt_array` | Encrypt `ARRAY` elements individually |
| `k4k_decrypt_array_with_schema` | Decrypt `ARRAY` elements individually using a schema string literal for the expected target type |
| `k4k_encrypt_map` | Encrypt `MAP` values individually |
| `k4k_decrypt_map_with_schema` | Decrypt `MAP` values individually using a schema string literal for the expected target type |
| `k4k_encrypt_row` | Encrypt `ROW` fields individually |
| `k4k_decrypt_row_with_schema` | Decrypt `ROW` fields individually using a schema string literal for the expected target type |
| `k4k_decrypt` _**(deprecated)**_ | Decrypt scalar or complex values using an exemplary type capture value for type inference - **please use `k4k_decrypt_with_schema` instead!** |
| `k4k_decrypt_array` _**(deprecated)**_ | Decrypt `ARRAY` elements using an exemplary type capture value for type inference - **please use `k4k_decrypt_array_with_schema` instead!** |
| `k4k_decrypt_map` _**(deprecated)**_ | Decrypt `MAP` values  using an exemplary type capture value for type inference - **please use `k4k_decrypt_map_with_schema` instead!** |

### Format-Preserving Encryption

| UDF | Description |
|---|---|
| `k4k_encrypt_fpe` | FPE encrypt scalar values |
| `k4k_decrypt_fpe` | FPE decrypt scalar values |
| `k4k_encrypt_array_fpe` | FPE encrypt array elements |
| `k4k_decrypt_array_fpe` | FPE decrypt array elements |
| `k4k_encrypt_map_fpe` | FPE encrypt map values |
| `k4k_decrypt_map_fpe` | FPE decrypt map values |

---

## Installation / Deployment

1. Download the UDF JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Place it in the **Flink libraries directory** scanned during cluster startup.
3. Register the functions in your Flink catalog before use.

### Function registration

Run this either in a Flink SQL session or as part of your SQL client initialisation:

```sql
ADD JAR '<FULL_PATH_TO_FLINK_UDFS_KRYPTONITE_JAR>';

CREATE FUNCTION k4k_encrypt                   AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptUdf'                  LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_with_schema       AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptWithSchemaUdf'        LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_array             AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptArrayUdf'             LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_array_with_schema AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptArrayWithSchemaUdf'   LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_map               AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptMapUdf'               LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_map_with_schema   AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptMapWithSchemaUdf'     LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_row               AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptRowUdf'               LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_row_with_schema   AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptRowWithSchemaUdf'     LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_fpe               AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptFpeUdf'               LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_fpe               AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptFpeUdf'               LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_array_fpe         AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptArrayFpeUdf'          LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_array_fpe         AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptArrayFpeUdf'          LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_map_fpe           AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptMapFpeUdf'            LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_map_fpe           AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptMapFpeUdf'            LANGUAGE JAVA;

-- NOTE: The following functions have been deprecated. Please use the corresponding
-- "*_with_schema" function alternatives instead. Registration and usage of these functions 
-- for new projects is highly discouraged to due upcoming removal!
-- CREATE FUNCTION k4k_decrypt                   AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptUdf'                  LANGUAGE JAVA;
-- CREATE FUNCTION k4k_decrypt_array             AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptArrayUdf'             LANGUAGE JAVA;
-- CREATE FUNCTION k4k_decrypt_map               AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptMapUdf'               LANGUAGE JAVA;


```

Verify:

```sql
SHOW USER FUNCTIONS;
-- Expected to list all successfully registered k4k_* functions (and potentially other registered user functions as well)
```

---

## Configuration

Any UDF-specific configuration is passed as environment variables to the Flink TaskManagers (or alternatively in `flink-conf.yaml`):

```yaml
environment:
  - cipher_data_keys=[{"identifier":"my-key","material":{"primaryKeyId":1234567890,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey","value":"<BASE64_KEY>","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":1234567890,"outputPrefixType":"TINK"}]}}]
  - cipher_data_key_identifier=my-key
```

For KMS-backed or encrypted keysets, additionally set `key_source`, `kms_type`, `kms_config`, `kek_type`, `kek_uri`, `kek_config`. See the [Configuration Reference](../configuration.md) for further details.

---

## UDF Signatures

### k4k_encrypt / k4k_decrypt

```sql
-- Encrypt with configured defaults
K4K_ENCRYPT(data T) → VARCHAR

-- Encrypt with explicit key identifier and algorithm
K4K_ENCRYPT(data T, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → VARCHAR

-- Decrypt (typeCapture is an exemplary value used for type inference)
K4K_DECRYPT(data VARCHAR, typeCapture T) → T
```

### k4k_decrypt_with_schema

An alternative to `k4k_decrypt` that takes a **Flink SQL type string literal** instead of a type capture value. The schema string must be a compile-time string literal — column references or runtime expressions are not accepted.

```sql
-- schemaString examples: 'STRING', 'INT', 'BIGINT', 'FLOAT', 'DOUBLE', 'BOOLEAN', 'BYTES'
K4K_DECRYPT_WITH_SCHEMA(data VARCHAR, schemaString VARCHAR) → T
```

### k4k_encrypt_array / k4k_decrypt_array

```sql
K4K_ENCRYPT_ARRAY(data ARRAY<T>) → ARRAY<VARCHAR>
K4K_ENCRYPT_ARRAY(data ARRAY<T>, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → ARRAY<VARCHAR>

-- typeCapture is a single element exemplary value used for type inference
K4K_DECRYPT_ARRAY(data ARRAY<VARCHAR>, typeCapture T) → ARRAY<T>
```

### k4k_decrypt_array_with_schema

An alternative to `k4k_decrypt_array` that takes a **Flink SQL type string literal** instead of a type capture value. The schema string must start with `ARRAY<...>` and must be a compile-time literal.

```sql
-- schemaString examples: 'ARRAY<STRING>', 'ARRAY<INT>', 'ARRAY<BIGINT>', 'ARRAY<DOUBLE>', 'ARRAY<BOOLEAN>'
K4K_DECRYPT_ARRAY_WITH_SCHEMA(data ARRAY<VARCHAR>, schemaString VARCHAR) → ARRAY<T>
```

### k4k_encrypt_map / k4k_decrypt_map

```sql
K4K_ENCRYPT_MAP(data MAP<K,V>) → MAP<K,VARCHAR>
K4K_ENCRYPT_MAP(data MAP<K,V>, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → MAP<K,VARCHAR>

-- typeCapture is a single exemplary map value used for type inference
K4K_DECRYPT_MAP(data MAP<K,VARCHAR>, typeCapture V) → MAP<K,V>
```

### k4k_decrypt_map_with_schema

An alternative to `k4k_decrypt_map` that takes a **Flink SQL type string literal** instead of a type capture value. The schema string must start with `MAP<...>` and must be a compile-time literal.

```sql
-- schemaString examples: 'MAP<STRING, STRING>', 'MAP<STRING, INT>', 'MAP<INT, STRING>', 'MAP<STRING, BOOLEAN>'
K4K_DECRYPT_MAP_WITH_SCHEMA(data MAP<K,VARCHAR>, schemaString VARCHAR) → MAP<K,V>
```

### k4k_encrypt_row

Encrypts `ROW` fields individually. Encrypted fields are returned as `VARCHAR`. Fields not in `fieldList` retain their original types. The `fieldList` parameter is a comma-separated list of field names to encrypt. Omitting it encrypts all fields. The `fieldList` parameter must be a string literal.

```sql
-- Encrypt all fields with configured defaults
K4K_ENCRYPT_ROW(data ROW<...>) → ROW<...(all fields as VARCHAR)...>

-- Encrypt only specific fields with configured defaults
K4K_ENCRYPT_ROW(data ROW<...>, fieldList VARCHAR) → ROW<...>

-- Encrypt all fields with explicit key identifier and algorithm
K4K_ENCRYPT_ROW(data ROW<...>, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → ROW<...(all fields as VARCHAR)...>

-- Encrypt only specific fields with explicit key identifier and algorithm
K4K_ENCRYPT_ROW(data ROW<...>, fieldList VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → ROW<...>
```

### k4k_decrypt_row_with_schema

Decrypts `ROW` fields using a schema string literal that describes the target row type after decryption. The schema string must start with `ROW<...>` or `ROW(...)` and must be a compile-time literal. The optional `fieldList` parameter (comma-separated field names) limits decryption to specific fields; fields not in the list are passed through unchanged.

```sql
-- Decrypt all fields
K4K_DECRYPT_ROW_WITH_SCHEMA(data ROW<...(all VARCHAR)...>, schemaString VARCHAR) → ROW<...>

-- Decrypt only specific fields (others are passed through as-is)
K4K_DECRYPT_ROW_WITH_SCHEMA(data ROW<...>, schemaString VARCHAR, fieldList VARCHAR) → ROW<...>
```

!!! note
    The schema string parameter for all decryption functions `K4K_decrypt_*_with_schema` MUST always be a **string literal**. Column references or runtime expressions cannot be supported.

### FPE variants

```sql
K4K_ENCRYPT_FPE(data VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR, fpeTweak VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR, fpeTweak VARCHAR, fpeAlphabetType VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR, fpeTweak VARCHAR, fpeAlphabetType VARCHAR, fpeAlphabetCustom VARCHAR) → VARCHAR
```

`K4K_DECRYPT_FPE` and the `*_ARRAY_FPE` / `*_MAP_FPE` variants follow the same pattern.

---

## Examples

### Object mode encryption and decryption

```sql
-- Table to store encrypted records (all columns as VARCHAR in object mode)
CREATE TABLE my_data_encrypted_o (
  id VARCHAR,
  mystring VARCHAR,
  myint VARCHAR,
  myboolean VARCHAR,
  mysubdoc1 VARCHAR,
  myarray VARCHAR,
  mysubdoc2 VARCHAR
) WITH (
  'connector' = 'kafka',
  'topic' = 'my_data_encrypted_o',
  'properties.bootstrap.servers' = 'kafka:9092',
  'format' = 'json'
);

-- Encrypt on insert
INSERT INTO my_data_encrypted_o VALUES (
  K4K_ENCRYPT('1234567890'),
  K4K_ENCRYPT('some foo text'),
  K4K_ENCRYPT(42),
  K4K_ENCRYPT(true),
  K4K_ENCRYPT(ROW('As I was going to St. Ives', 1234)),
  K4K_ENCRYPT(ARRAY['str_1', 'str_2', 'str_3']),
  K4K_ENCRYPT(MAP['k1', 9, 'k2', 8, 'k3', 7])
);

-- Decrypt using schema strings
SELECT
  K4K_DECRYPT_WITH_SCHEMA(id, 'STRING') AS id,
  K4K_DECRYPT_WITH_SCHEMA(mystring, 'STRING') AS mystring,
  K4K_DECRYPT_WITH_SCHEMA(myint, 'INT') AS myint,
  K4K_DECRYPT_WITH_SCHEMA(myboolean, 'BOOLEAN') AS myboolean,
  K4K_DECRYPT_WITH_SCHEMA(mysubdoc1, 'ROW<somestring STRING, someint INT>') AS mysubdoc1,
  K4K_DECRYPT_WITH_SCHEMA(myarray, 'ARRAY<STRING>') AS myarray,
  K4K_DECRYPT_WITH_SCHEMA(mysubdoc2, 'MAP<STRING, INT>') AS mysubdoc2
FROM my_data_encrypted_o LIMIT 1;
```

### Element mode encryption and decryption

```sql
-- Table with element-wise encrypted complex types
CREATE TABLE my_data_encrypted_e (
  id VARCHAR,
  mystring VARCHAR,
  myint VARCHAR,
  myboolean VARCHAR,
  mysubdoc1 ROW<somestring VARCHAR, someint VARCHAR>,
  myarray ARRAY<VARCHAR>,
  mysubdoc2 MAP<VARCHAR, VARCHAR>
) WITH (
  'connector' = 'kafka',
  'topic' = 'my_data_encrypted_e',
  'properties.bootstrap.servers' = 'kafka:9092',
  'format' = 'json'
);

-- Encrypt using k4k_encrypt_row,k4k_encrypt_array,k4k_encrypt_map for complex types
INSERT INTO my_data_encrypted_e VALUES (
  K4K_ENCRYPT('1234567890'),
  K4K_ENCRYPT('some foo text'),
  K4K_ENCRYPT(42),
  K4K_ENCRYPT(true),
  K4K_ENCRYPT_ROW(ROW('As I was going to St. Ives', 1234)),
  K4K_ENCRYPT_ARRAY(ARRAY['str_1', 'str_2', 'str_3']),
  K4K_ENCRYPT_MAP(MAP['k1', 9, 'k2', 8, 'k3', 7])
);

-- Decrypt using schema strings
SELECT
  K4K_DECRYPT_WITH_SCHEMA(id, 'STRING') AS id,
  K4K_DECRYPT_WITH_SCHEMA(mystring, 'STRING') AS mystring,
  K4K_DECRYPT_WITH_SCHEMA(myint, 'INT') AS myint,
  K4K_DECRYPT_WITH_SCHEMA(myboolean, 'BOOLEAN') AS myboolean,
  K4K_DECRYPT_ROW_WITH_SCHEMA(mysubdoc1, 'ROW<somestring STRING, someint INT>') AS mysubdoc1,
  K4K_DECRYPT_ARRAY_WITH_SCHEMA(myarray, 'ARRAY<STRING>') AS myarray,
  K4K_DECRYPT_MAP_WITH_SCHEMA(mysubdoc2, 'MAP<STRING, INT>') AS mysubdoc2
FROM my_data_encrypted_e LIMIT 1;
```

### Partial ROW field encryption and decryption

`k4k_encrypt_row` and `k4k_decrypt_row_with_schema` both accept an optional `fieldList` parameter (comma-separated field names) to process only a subset of fields. Fields not listed are passed through unchanged.

```sql
-- Encrypt only 'name'; 'age' and 'active' remain plaintext
INSERT INTO my_people_encrypted
SELECT K4K_ENCRYPT_ROW(myrow, 'name') AS myrow
FROM my_people_source;

-- Decrypt only 'name'; 'age' and 'active' pass through as-is
SELECT
  K4K_DECRYPT_ROW_WITH_SCHEMA(myrow, 'ROW<name STRING, age INT, active BOOLEAN>', 'name') AS myrow
FROM my_people_encrypted LIMIT 1;

-- Encrypt and decrypt multiple specific fields
INSERT INTO my_records_encrypted
SELECT K4K_ENCRYPT_ROW(myrow, 'id,score') AS myrow
FROM my_records_source;

SELECT
  K4K_DECRYPT_ROW_WITH_SCHEMA(myrow, 'ROW<id STRING, count INT, score DOUBLE, enabled BOOLEAN>', 'id,score') AS myrow
FROM my_records_encrypted LIMIT 1;
```

### FPE encryption

```sql
INSERT INTO customer_fpe_encrypted
SELECT
  customer_id,
  K4K_ENCRYPT_FPE(credit_card_number, 'myFpeKey', 'CUSTOM/MYSTO_FPE_FF3_1', 'tweakAB', 'DIGITS') AS credit_card_number,
  K4K_ENCRYPT_FPE(ssn, 'myFpeKey', 'CUSTOM/MYSTO_FPE_FF3_1', 'tweakXY', 'DIGITS') AS ssn
FROM customer_plaintext;
```

---

## Complex Data Type Mapping

| Original type | Object mode (`k4k_encrypt`) | Element mode (`k4k_encrypt_array`, `k4k_encrypt_map`, `k4k_encrypt_row`) |
|---|---|---|
| `ARRAY<T>` | `VARCHAR` | `ARRAY<VARCHAR>` using `k4k_encrypt_array` |
| `MAP<K,V>` | `VARCHAR` | `MAP<K,VARCHAR>` using `k4k_encrypt_map` |
| `ROW<...>` | `VARCHAR` | `ROW<...(all fields VARCHAR)...>` using `k4k_encrypt_row` |
