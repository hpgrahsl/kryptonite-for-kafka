# Getting Started — ksqlDB UDFs

Kryptonite for Kafka provides four ksqlDB user-defined functions: `K4KENCRYPT`, `K4KDECRYPT`, `K4KENCRYPTFPE`, and `K4KDECRYPTFPE`. Once deployed, they integrate directly into ksqlDB queries and persistent stream transformations.

---

## Prerequisites

- **Java 17+** on every ksqlDB server node
- A running ksqlDB server with a configured extension directory (`ksql.extension.dir` in `ksql-server.properties`)
- A running Apache Kafka cluster reachable by ksqlDB

---

## Step 1 — Install the UDF JAR

### Option A — Pre-built artifact (recommended)

1. Download the UDF JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Copy the JAR into the directory configured via `ksql.extension.dir` on every ksqlDB server node.
3. **Restart all ksqlDB server(s)** to load the new functions.

### Option B — Build from source

```bash
git clone https://github.com/hpgrahsl/kryptonite-for-kafka.git
cd kryptonite-for-kafka
./mvnw clean package -DskipTests -pl ksqldb-udfs-kryptonite
```

The UDF JAR is produced under `ksqldb-udfs-kryptonite/target/`.

---

## Step 2 — Generate a keyset

Use the [Keyset Tool](../keyset-tool.md) to produce key material:

```bash
java -jar kryptonite-keyset-tool/target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-demo-key -f FULL -p
```

Copy the printed JSON — you will reference it in the ksqlDB server configuration in the next step.

!!! warning "Key material is a secret"
    Do not commit key material to source control. For production use, see [Key Management](../key-management.md).

---

## Step 3 — Configure the ksqlDB server

UDF configuration is set in `ksql-server.properties`. Each UDF is configured independently under its own prefix. At minimum, configure `K4KENCRYPT`:

```properties
ksql.functions.k4kencrypt.cipher.data.keys=[{"identifier":"my-demo-key","material":{"primaryKeyId":10000,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey","value":"<BASE64_KEY>","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":10000,"outputPrefixType":"TINK"}]}}]
ksql.functions.k4kencrypt.cipher.data.key.identifier=my-demo-key
```

For Docker / docker-compose, set the equivalent environment variables:

```yaml
KSQL_KSQL_FUNCTIONS_K4KENCRYPT_CIPHER_DATA_KEYS: '[{"identifier":"my-demo-key","material":{...}}]'
KSQL_KSQL_FUNCTIONS_K4KENCRYPT_CIPHER_DATA_KEY_IDENTIFIER: "my-demo-key"
```

`K4KDECRYPT` shares key configuration with `K4KENCRYPT` by default. Configure `K4KENCRYPTFPE` and `K4KDECRYPTFPE` separately if you intend to use format-preserving encryption.

See the [Configuration reference](../configuration.md) for all available parameters.

---

## Step 4 — Verify deployment

After restarting ksqlDB, confirm the UDFs are registered:

```sql
SHOW FUNCTIONS;
```

The output should include:

```
 Function Name | Category
---------------+-----------
 K4KDECRYPT    | OTHER
 K4KDECRYPTFPE | OTHER
 K4KENCRYPT    | OTHER
 K4KENCRYPTFPE | OTHER
```

---

## Step 5 — Encrypt and decrypt in ksqlDB

With the functions registered and key material configured, use them in any ksqlDB statement:

```sql
-- Create an encrypted stream
CREATE STREAM orders_encrypted AS
  SELECT
    order_id,
    K4KENCRYPT(customer_email) AS customer_email,
    K4KENCRYPT(credit_card)    AS credit_card,
    amount                                        -- untouched
  FROM orders_raw
  EMIT CHANGES;

-- Decrypt on read
SELECT
  order_id,
  K4KDECRYPT(customer_email, '') AS customer_email,
  K4KDECRYPT(credit_card, '')    AS credit_card,
  amount
FROM orders_encrypted
EMIT CHANGES LIMIT 5;
```

Encrypted fields are stored as `VARCHAR` (Base64-encoded ciphertext). Pass an exemplary value of the expected return type as the second argument to `K4KDECRYPT` for type inference (`''` for `STRING`, `0` for `INT`, `false` for `BOOLEAN`, etc.).

---

## Next Steps

- [ksqlDB UDFs reference](../modules/ksqldb-udfs.md) — all UDF signatures, element-mode variants, FPE examples, data type mapping
- [Configuration reference](../configuration.md) — all parameters and their naming conventions
- [Key Management](../key-management.md) — moving from inline keys to cloud KMS
- [Keyset Tool](../keyset-tool.md) — generate keys for all algorithms and KMS backends
