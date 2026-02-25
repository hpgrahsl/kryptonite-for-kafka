# Flink UDFs

Kryptonite for Kafka provides twelve Flink user-defined functions for encrypting and decrypting column values in Flink TABLE SQL / Table API.

**AEAD (AES-GCM / AES-GCM-SIV):**

| UDF | Description |
|---|---|
| `k4k_encrypt` | Encrypt scalar or complex values (object mode) |
| `k4k_decrypt` | Decrypt values (object mode) |
| `k4k_encrypt_array` | Encrypt array elements individually |
| `k4k_decrypt_array` | Decrypt array elements individually |
| `k4k_encrypt_map` | Encrypt map values individually |
| `k4k_decrypt_map` | Decrypt map values individually |

**FPE (FF3-1) — available since version 0.6.0:**

| UDF | Description |
|---|---|
| `k4k_encrypt_fpe` | FPE encrypt scalar values |
| `k4k_decrypt_fpe` | FPE decrypt scalar values |
| `k4k_encrypt_array_fpe` | FPE encrypt array elements |
| `k4k_decrypt_array_fpe` | FPE decrypt array elements |
| `k4k_encrypt_map_fpe` | FPE encrypt map values |
| `k4k_decrypt_map_fpe` | FPE decrypt map values |

---

## Installation

1. Download the UDF JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases) (available since version 0.5.0).
2. Place it in the **Flink libraries directory** scanned during cluster startup.
3. Register the functions in your Flink catalog before use.

### Function registration

Run in a Flink SQL session or as part of your SQL client initialisation:

```sql
ADD JAR '<FULL_PATH_TO_FLINK_UDFS_KRYPTONITE_JAR>';

CREATE FUNCTION k4k_encrypt          AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptUdf'        LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt          AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptUdf'        LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_array    AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptArrayUdf'   LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_array    AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptArrayUdf'   LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_map      AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptMapUdf'     LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_map      AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptMapUdf'     LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_fpe      AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptFpeUdf'     LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_fpe      AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptFpeUdf'     LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_array_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptArrayFpeUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_array_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptArrayFpeUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_encrypt_map_fpe  AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptMapFpeUdf'  LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_map_fpe  AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptMapFpeUdf'  LANGUAGE JAVA;
```

Verify:

```sql
SHOW USER FUNCTIONS;
-- Lists all 12 k4k_* functions
```

---

## Configuration

Configuration is passed as environment variables to the Flink TaskManager containers (or in `flink-conf.yaml`):

```yaml
environment:
  - cipher_data_keys=[{"identifier":"my-key","material":{"primaryKeyId":1234567890,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey","value":"<BASE64_KEY>","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":1234567890,"outputPrefixType":"TINK"}]}}]
  - cipher_data_key_identifier=my-key
```

For KMS-backed keysets, additionally set `key_source`, `kms_type`, `kms_config`, `kek_type`, `kek_uri`, `kek_config`. See the [Configuration Reference](../configuration.md).

---

## UDF Signatures

### k4k_encrypt / k4k_decrypt

```sql
-- Encrypt with configured defaults
K4K_ENCRYPT(data T) → VARCHAR

-- Encrypt with explicit key identifier and algorithm
K4K_ENCRYPT(data T, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → VARCHAR

-- Decrypt (typeCapture is an exemplary value for type inference)
K4K_DECRYPT(data VARCHAR, typeCapture T) → T
```

### k4k_encrypt_array / k4k_decrypt_array

```sql
K4K_ENCRYPT_ARRAY(data ARRAY<T>) → ARRAY<VARCHAR>
K4K_ENCRYPT_ARRAY(data ARRAY<T>, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → ARRAY<VARCHAR>

K4K_DECRYPT_ARRAY(data ARRAY<VARCHAR>, typeCapture T) → ARRAY<T>
```

### k4k_encrypt_map / k4k_decrypt_map

```sql
K4K_ENCRYPT_MAP(data MAP<K,V>) → MAP<K,VARCHAR>
K4K_ENCRYPT_MAP(data MAP<K,V>, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → MAP<K,VARCHAR>

K4K_DECRYPT_MAP(data MAP<K,VARCHAR>, typeCapture V) → MAP<K,V>
```

### FPE variants

```sql
K4K_ENCRYPT_FPE(data VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR, fpeTweak VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR, fpeTweak VARCHAR, fpeAlphabetType VARCHAR) → VARCHAR
K4K_ENCRYPT_FPE(data VARCHAR, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR, fpeTweak VARCHAR, fpeAlphabetType VARCHAR, fpeAlphabetCustom VARCHAR) → VARCHAR
```

`K4K_DECRYPT_FPE` and the `_ARRAY_FPE` / `_MAP_FPE` variants follow the same pattern.

---

## Examples

### Object mode encryption and decryption

```sql
-- Table to store encrypted records
CREATE TABLE my_data_encrypted (
  id VARCHAR,
  mystring VARCHAR,
  myint VARCHAR,
  myboolean VARCHAR,
  mysubdoc1 VARCHAR,
  myarray VARCHAR,
  mysubdoc2 VARCHAR
) WITH (
  'connector' = 'kafka',
  'topic' = 'my_data_enc_o',
  'properties.bootstrap.servers' = 'kafka:9092',
  'format' = 'json'
);

-- Encrypt on insert
INSERT INTO my_data_encrypted VALUES (
  K4K_ENCRYPT('1234567890'),
  K4K_ENCRYPT('some text'),
  K4K_ENCRYPT(42),
  K4K_ENCRYPT(true),
  K4K_ENCRYPT(ROW('hello', 1234)),
  K4K_ENCRYPT(ARRAY['a', 'b', 'c']),
  K4K_ENCRYPT(MAP['k1', 9, 'k2', 8])
);

-- Decrypt on select
SELECT
  K4K_DECRYPT(id, '') AS id,
  K4K_DECRYPT(mystring, '') AS mystring,
  K4K_DECRYPT(myint, 0) AS myint,
  K4K_DECRYPT(myboolean, false) AS myboolean,
  K4K_DECRYPT(mysubdoc1, ROW('', 0)) AS mysubdoc1,
  K4K_DECRYPT(myarray, array['']) AS myarray,
  K4K_DECRYPT(mysubdoc2, map['', 0]) AS mysubdoc2
FROM my_data_encrypted LIMIT 5;
```

### Element mode encryption

```sql
-- Table with element-wise encrypted complex columns
CREATE TABLE my_data_enc_e (
  id VARCHAR,
  myarray ARRAY<VARCHAR>,
  mymap MAP<VARCHAR, VARCHAR>,
  myrow ROW<f1 VARCHAR, f2 VARCHAR>
) WITH (...);

INSERT INTO my_data_enc_e VALUES (
  K4K_ENCRYPT('1234567890'),
  K4K_ENCRYPT_ARRAY(ARRAY['str_1', 'str_2', 'str_3']),
  K4K_ENCRYPT_MAP(MAP['k1', 9, 'k2', 8, 'k3', 7]),
  ROW(K4K_ENCRYPT('hello'), K4K_ENCRYPT(42))
);

-- Decrypt
SELECT
  K4K_DECRYPT(id, '') AS id,
  K4K_DECRYPT_ARRAY(myarray, '') AS myarray,
  K4K_DECRYPT_MAP(mymap, 0) AS mymap,
  ROW(K4K_DECRYPT(myrow.f1, ''), K4K_DECRYPT(myrow.f2, 0)) AS myrow
FROM my_data_enc_e LIMIT 5;
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

## Data Type Mapping

| Original type | Object mode (`k4k_encrypt`) | Element mode |
|---|---|---|
| `VARCHAR`, `INT`, `BIGINT`, `DOUBLE`, `BOOLEAN` | `VARCHAR` | — |
| `ARRAY<T>` | `VARCHAR` | `ARRAY<VARCHAR>` via `k4k_encrypt_array` |
| `MAP<K,V>` | `VARCHAR` | `MAP<K,VARCHAR>` via `k4k_encrypt_map` |
| `ROW<...>` | `VARCHAR` | Encrypt each field individually and reassemble |
