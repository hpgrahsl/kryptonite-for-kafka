# Getting Started — Apache Flink UDFs

Kryptonite for Kafka provides twelve Flink user-defined functions (UDFs) for encrypting and decrypting column values in Flink Table API / SQL pipelines. Once registered, they work like any built-in function in `INSERT`, `SELECT`, and streaming jobs.

---

## Prerequisites

- **Java 17+** on all Flink TaskManager and JobManager nodes
- A running Flink cluster (local, standalone, YARN, or Kubernetes)
- Access to a Flink SQL client or Table API application

---

## Step 1 — Install the UDF JAR

### Option A — Pre-built artifact (recommended)

1. Download the UDF JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases) (available since version 0.5.0).
2. Place the JAR in the **Flink libraries directory** that is scanned at cluster startup (typically `$FLINK_HOME/lib/`).
3. **Restart the Flink cluster** so the JAR is on the classpath.

### Option B — Build from source

```bash
git clone https://github.com/hpgrahsl/kryptonite-for-kafka.git
cd kryptonite-for-kafka
./mvnw clean package -DskipTests -pl flink-udfs-kryptonite
```

The fat JAR is produced under `flink-udfs-kryptonite/target/`.

---

## Step 2 — Configure key material

Key material is supplied to the Flink TaskManagers via environment variables. Set these in your Flink configuration (`flink-conf.yaml`) or as container environment variables:

```yaml
env.java.opts.taskmanager: >-
  -Dcipher_data_keys=[{"identifier":"my-demo-key","material":{"primaryKeyId":10000,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey","value":"<BASE64_KEY>","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":10000,"outputPrefixType":"TINK"}]}}]
  -Dcipher_data_key_identifier=my-demo-key
```

Or as plain environment variables on each TaskManager node:

```bash
export cipher_data_keys='[{"identifier":"my-demo-key","material":{...}}]'
export cipher_data_key_identifier=my-demo-key
```

!!! warning "Key material is a secret"
    Do not commit key material to source control. For production use, see [Key Management](../key-management.md).

Use the [Keyset Tool](../keyset-tool.md) to generate key material if you do not have a keyset yet.

---

## Step 3 — Register the UDFs

In a Flink SQL client session (or your Table API bootstrap code), load the JAR and register the functions:

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

Verify all twelve functions are available:

```sql
SHOW USER FUNCTIONS;
```

---

## Step 4 — Encrypt and decrypt in SQL

With the functions registered and key material configured, use them in any Flink SQL statement:

```sql
-- Encrypt selected columns on insert
INSERT INTO orders_encrypted
SELECT
  order_id,
  K4K_ENCRYPT(customer_email) AS customer_email,
  K4K_ENCRYPT(credit_card)    AS credit_card,
  amount                                          -- untouched
FROM orders_raw;

-- Decrypt on read
SELECT
  order_id,
  K4K_DECRYPT(customer_email, '') AS customer_email,
  K4K_DECRYPT(credit_card, '')    AS credit_card,
  amount
FROM orders_encrypted
LIMIT 10;
```

Encrypted columns are stored as `VARCHAR` (Base64-encoded ciphertext). Pass an exemplary value of the expected return type as the second argument to `K4K_DECRYPT` for type inference.

---

## Next Steps

- [Flink UDFs reference](../modules/flink-udfs.md) — all twelve UDF signatures, element-mode variants, FPE examples
- [Configuration reference](../configuration.md) — all parameters and their naming conventions
- [Key Management](../key-management.md) — moving from inline keys to cloud KMS
- [Keyset Tool](../keyset-tool.md) — generate keys for all algorithms and KMS backends
