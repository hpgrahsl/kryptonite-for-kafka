# Kryptonite for Kafka Keyset Generator Tool

A command-line tool for generating Tink keyset JSON configurations used by all _Kryptonite for Kafka_ modules. Supports both plaintext and KMS-encrypted keyset generation, the latter only with GCP's Cloud KMS for now.

## Build

```bash
# Build the fat JAR (from the module directory)
../mvnw clean package -DskipTests
```

The runnable JAR is created at `target/kryptonite-keyset-tool-0.1.0.jar`.

## Usage

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar [OPTIONS]
```

Alternatively, run directly via Maven:

```bash
../mvnw -q exec:java -Dexec.args="[OPTIONS]"
```

### Options

| Option | Required | Default | Description |
|--------|----------|---------|-------------|
| `-a, --algorithm` | Yes | - | Cipher algorithm: `AES_GCM`, `AES_GCM_SIV`, or `FPE_FF31` |
| `-i, --identifier` | When format=FULL | - | Keyset identifier string |
| `-f, --output-format` | No | `FULL` | `FULL` (wrapped with identifier + material) or `RAW` (Tink keyset only) |
| `-s, --key-size` | No | `256` | Key size in bits. AES_GCM: 128 or 256. AES_GCM_SIV: fixed (flag ignored). FPE_FF31: 128, 192, or 256 |
| `-n, --num-keys` | No | `1` | Number of keys per keyset (1-1000) |
| `-k, --num-keysets` | No | `1` | Number of keysets to generate (1-1000). When > 1, output is a JSON array with identifiers suffixed `_1`, `_2`, etc. |
| `--initial-key-id` | No | `10000` | Starting key ID, incremented by 1 for each additional key |
| `-o, --output` | No | stdout | Output file path |
| `-p, --pretty` | No | `false` | Pretty-print JSON output |
| `-e, --encrypt` | No | `false` | Encrypt the keyset using a KMS key encryption key (KEK). Requires `--kek-type`, `--kek-uri`, and `--kek-config`. |
| `--kek-type` | When `--encrypt` | - | KMS key encryption key type (e.g. `GCP`) |
| `--kek-uri` | When `--encrypt` | - | KMS key encryption key URI (e.g. `gcp-kms://projects/.../cryptoKeys/...`) |
| `--kek-config` | When `--encrypt` | - | Path to KMS credentials/config file (e.g. GCP service account JSON) |
| `-h, --help` | - | - | Show help message |
| `-V, --version` | - | - | Show version |

### Key ID Assignment

Key IDs are assigned sequentially starting from `--initial-key-id`. The first (lowest) ID becomes the `primaryKeyId`. When generating multiple keysets (`-k`), IDs are non-overlapping across keysets.

For example, with `-n 3 -k 2 --initial-key-id 1000`:
- Keyset 1: key IDs 1000, 1001, 1002 (primaryKeyId = 1000)
- Keyset 2: key IDs 1003, 1004, 1005 (primaryKeyId = 1003)

### Algorithms

- **AES_GCM** -- Probabilistic authenticated encryption (AEAD). Use for encrypting field values where identical plaintexts should produce different ciphertexts.
- **AES_GCM_SIV** -- Deterministic authenticated encryption. Use for Kafka record keys or scenarios requiring consistent ciphertext for the same plaintext (e.g., joins, partitioning). Key size is fixed internally.
- **FPE_FF31** -- Format-preserving encryption. Encrypts data while preserving its format and length. Uses a custom Tink key type with `RAW` output prefix.

## Examples

### Single AES-GCM keyset (FULL format, pretty-printed)

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-aes-key -f FULL -p
```

Output:

```json
{
  "identifier" : "my-aes-key",
  "material" : {
    "primaryKeyId" : 10000,
    "key" : [ {
      "keyData" : {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value" : "...",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 10000,
      "outputPrefixType" : "TINK"
    } ]
  }
}
```

### AES-GCM keyset with 5 keys for key rotation

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-rotating-key -f FULL -n 5 --initial-key-id 20000 -p
```

### RAW format (Tink keyset only, no wrapper)

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -f RAW
```

### AES-GCM with 128-bit key size

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-128-key -f FULL -s 128 -p
```

### Deterministic encryption keyset (AES-GCM-SIV)

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -i my-det-key -f FULL -p
```

### FPE keyset with 192-bit key

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a FPE_FF31 -i my-fpe-key -f FULL -s 192 -p
```

### Multiple keysets as JSON array

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i demo-key -f FULL -k 3 -n 2 --initial-key-id 10000 -p
```

Output: a JSON array with 3 keysets, each containing 2 keys:

```json
[ {
  "identifier" : "demo-key_1",
  "material" : { "primaryKeyId" : 10000, "key" : [ ... ] }
}, {
  "identifier" : "demo-key_2",
  "material" : { "primaryKeyId" : 10002, "key" : [ ... ] }
}, {
  "identifier" : "demo-key_3",
  "material" : { "primaryKeyId" : 10004, "key" : [ ... ] }
} ]
```

### Write output to file

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -o /path/to/keyset.json -p
```

### Encrypted keyset with GCP KMS

Generate an AES-GCM keyset encrypted with a GCP Cloud KMS key encryption key (KEK). The keyset material is encrypted at rest — only the `keysetInfo` metadata (key IDs, status, output prefix type) remains in plaintext.

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-encrypted-key -f FULL -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json
```

Output:

```json
{
  "identifier" : "my-encrypted-key",
  "material" : {
    "encryptedKeyset" : "<base64-encoded encrypted keyset bytes>",
    "keysetInfo" : {
      "primaryKeyId" : 10000,
      "keyInfo" : [ {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "status" : "ENABLED",
        "keyId" : 10000,
        "outputPrefixType" : "TINK"
      } ]
    }
  }
}
```

### Multiple encrypted keysets

Generate 3 encrypted AES-GCM-SIV keysets, each with 2 keys, written to a file:

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -i det-key -f FULL -k 3 -n 2 -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json \
  -o /path/to/encrypted-keysets.json
```

**Note:** Encrypted keyset generation is supported for all algorithms (`AES_GCM`, `AES_GCM_SIV`, and `FPE_FF31`). The KMS provider is discovered at runtime via `ServiceLoader`, so additional KMS types (e.g. AWS) can be supported by adding the corresponding `kryptonite-kms-*` module to the classpath.

## Running Tests

```bash
../mvnw test
```
