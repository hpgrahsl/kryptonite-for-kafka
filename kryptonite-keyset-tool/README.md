# Kryptonite for Kafka Keyset Generator Tool

A command-line tool for generating Tink keyset JSON configurations used by all _Kryptonite for Kafka_ modules. Supports both plaintext and KMS-encrypted keyset generation using GCP Cloud KMS, AWS KMS, or Azure Key Vault.

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

| Option                | Required         | Default | Description                                                                                                                                                                                          |
| --------------------- | ---------------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `-a, --algorithm`     | Yes              | -       | Cipher algorithm: `AES_GCM`, `AES_GCM_SIV`, or `FPE_FF31`                                                                                                                                            |
| `-i, --identifier`    | When format=FULL | -       | Keyset identifier string                                                                                                                                                                             |
| `-f, --output-format` | No               | `FULL`  | `FULL` (wrapped with identifier + material) or `RAW` (Tink keyset only)                                                                                                                              |
| `-s, --key-size`      | No               | `256`   | Key size in bits. AES_GCM: 128 or 256. AES_GCM_SIV: fixed (flag ignored). FPE_FF31: 128, 192, or 256                                                                                                 |
| `-n, --num-keys`      | No               | `1`     | Number of keys per keyset (1-1000)                                                                                                                                                                   |
| `-k, --num-keysets`   | No               | `1`     | Number of keysets to generate (1-1000). When > 1, output is a JSON array with identifiers suffixed `_1`, `_2`, etc.                                                                                  |
| `--initial-key-id`    | No               | `10000` | Starting key ID, incremented by 1 for each additional key                                                                                                                                            |
| `-o, --output`        | No               | stdout  | Output file path                                                                                                                                                                                     |
| `-p, --pretty`        | No               | `false` | Pretty-print JSON output                                                                                                                                                                             |
| `-e, --encrypt`       | No               | `false` | Encrypt the keyset using a KMS key encryption key (KEK). Requires `--kek-type`, `--kek-uri`, and `--kek-config`.                                                                                     |
| `--kek-type`          | When `--encrypt` | -       | KMS key encryption key type: `GCP`, `AWS`, or `AZURE`                                                                                                                                                |
| `--kek-uri`           | When `--encrypt` | -       | KMS key encryption key URI (e.g. `gcp-kms://projects/.../cryptoKeys/...`, `aws-kms://arn:aws:kms:...`, or `azure-kv://<vault-host>/keys/<key-name>`)                                                 |
| `--kek-config`        | When `--encrypt` | -       | Path to KMS credentials/config file (GCP service account JSON; AWS credentials JSON with `accessKey`/`secretKey`; Azure credentials JSON with `clientId`, `tenantId`, `clientSecret`, `keyVaultUrl`) |
| `-h, --help`          | -                | -       | Show help message                                                                                                                                                                                    |
| `-V, --version`       | -                | -       | Show version                                                                                                                                                                                         |

### Key ID Assignment

Key IDs are assigned sequentially starting from `--initial-key-id`. The first (lowest) ID becomes the `primaryKeyId`. When generating multiple keysets (`-k`), IDs are non-overlapping across keysets.

For example, with `-n 3 -k 2 --initial-key-id 1000`:

- Keyset 1: key IDs 1000, 1001, 1002 (primaryKeyId = 1000)
- Keyset 2: key IDs 1003, 1004, 1005 (primaryKeyId = 1003)

### Algorithms

- **AES_GCM** -- Probabilistic authenticated encryption (AEAD). Use for encrypting field values where identical plaintexts should produce different ciphertexts.
- **AES_GCM_SIV** -- Deterministic authenticated encryption. Use for scenarios requiring consistent ciphertext for the same plaintext (e.g., joins, partitioning). Key size is fixed internally.
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
  "identifier": "my-aes-key",
  "material": {
    "primaryKeyId": 10000,
    "key": [
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 10000,
        "outputPrefixType": "TINK"
      }
    ]
  }
}
```

### AES-GCM keyset with 5 keys for key rotation

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-rotating-key -f FULL -n 5 --initial-key-id 20000 -p
```

Output:

```json
{
  "identifier": "my-rotating-key",
  "material": {
    "primaryKeyId": 20000,
    "key": [
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 20000,
        "outputPrefixType": "TINK"
      },
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 20001,
        "outputPrefixType": "TINK"
      },
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 20002,
        "outputPrefixType": "TINK"
      },
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 20003,
        "outputPrefixType": "TINK"
      },
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 20004,
        "outputPrefixType": "TINK"
      }
    ]
  }
}
```

### RAW format (Tink keyset only, no wrapper)

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -f RAW
```

Output:

```json
{
  "primaryKeyId": 10000,
  "key": [
    {
      "keyData": {
        "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value": "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 10000,
      "outputPrefixType": "TINK"
    }
  ]
}
```

### AES-GCM with 128-bit key size

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-128-key -f FULL -s 128 -p
```

Output:

```json
{
  "identifier": "my-128-key",
  "material": {
    "primaryKeyId": 10000,
    "key": [
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 10000,
        "outputPrefixType": "TINK"
      }
    ]
  }
}
```

### Deterministic encryption keyset (AES-GCM-SIV)

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -i my-det-key -f FULL -p
```

Output:

```json
{
  "identifier": "my-det-key",
  "material": {
    "primaryKeyId": 10000,
    "key": [
      {
        "keyData": {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 10000,
        "outputPrefixType": "TINK"
      }
    ]
  }
}
```

### FPE keyset with 192-bit key

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a FPE_FF31 -i my-fpe-key -f FULL -s 192 -p
```

Output:

```json
{
  "identifier": "my-fpe-key",
  "material": {
    "primaryKeyId": 10000,
    "key": [
      {
        "keyData": {
          "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
          "value": "<BASE64_ENCODED_KEY_HERE>",
          "keyMaterialType": "SYMMETRIC"
        },
        "status": "ENABLED",
        "keyId": 10000,
        "outputPrefixType": "RAW"
      }
    ]
  }
}
```

### Multiple keysets as JSON array

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i demo-key -f FULL -k 3 -n 2 --initial-key-id 10000 -p
```

Output: a JSON array with 3 keysets, each containing 2 keys:

```json
[
  {
    "identifier": "demo-key_1",
    "material": {
      "primaryKeyId": 10000,
      "key": [
        {
          "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "value": "<BASE64_ENCODED_KEY_HERE>",
            "keyMaterialType": "SYMMETRIC"
          },
          "status": "ENABLED",
          "keyId": 10000,
          "outputPrefixType": "TINK"
        },
        {
          "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "value": "<BASE64_ENCODED_KEY_HERE>",
            "keyMaterialType": "SYMMETRIC"
          },
          "status": "ENABLED",
          "keyId": 10001,
          "outputPrefixType": "TINK"
        }
      ]
    }
  },
  {
    "identifier": "demo-key_2",
    "material": {
      "primaryKeyId": 10002,
      "key": [
        {
          "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "value": "<BASE64_ENCODED_KEY_HERE>",
            "keyMaterialType": "SYMMETRIC"
          },
          "status": "ENABLED",
          "keyId": 10002,
          "outputPrefixType": "TINK"
        },
        {
          "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "value": "<BASE64_ENCODED_KEY_HERE>",
            "keyMaterialType": "SYMMETRIC"
          },
          "status": "ENABLED",
          "keyId": 10003,
          "outputPrefixType": "TINK"
        }
      ]
    }
  },
  {
    "identifier": "demo-key_3",
    "material": {
      "primaryKeyId": 10004,
      "key": [
        {
          "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "value": "<BASE64_ENCODED_KEY_HERE>",
            "keyMaterialType": "SYMMETRIC"
          },
          "status": "ENABLED",
          "keyId": 10004,
          "outputPrefixType": "TINK"
        },
        {
          "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "value": "<BASE64_ENCODED_KEY_HERE>",
            "keyMaterialType": "SYMMETRIC"
          },
          "status": "ENABLED",
          "keyId": 10005,
          "outputPrefixType": "TINK"
        }
      ]
    }
  }
]
```

### Write output to file

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -o /path/to/keyset.json -p
```

File `keyset.json`:

```json
{
  "identifier" : "my-key",
  "material" : {
    "primaryKeyId" : 10000,
    "key" : [ {
      "keyData" : {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value" : "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 10000,
      "outputPrefixType" : "TINK"
    } ]
  }
}
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
  "identifier": "my-encrypted-key",
  "material": {
    "encryptedKeyset": "<base64-encoded encrypted keyset bytes>",
    "keysetInfo": {
      "primaryKeyId": 10000,
      "keyInfo": [
        {
          "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
          "status": "ENABLED",
          "keyId": 10000,
          "outputPrefixType": "TINK"
        }
      ]
    }
  }
}
```

### Encrypted keyset with AWS KMS

Generate an AES-GCM keyset encrypted with an AWS KMS key encryption key (KEK). The credentials config file is a JSON object containing `accessKey` and `secretKey`.

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-encrypted-key -f FULL -p \
  -e --kek-type AWS \
  --kek-uri "aws-kms://arn:aws:kms:eu-central-1:123456789012:key/abcd-1234-efgh-5678" \
  --kek-config /path/to/aws-credentials.json
```

Where `aws-credentials.json` contains:

```json
{
  "accessKey": "AKIA...",
  "secretKey": "..."
}
```

### Encrypted keyset with Azure Key Vault KEK

Generate an AES-GCM keyset encrypted with an Azure Key Vault RSA key encryption key (KEK). Because Azure Key Vault standard tier only supports RSA keys, the module uses envelope encryption internally — the DEK is wrapped with RSA-OAEP-256 via Azure Key Vault, while the keyset itself is encrypted locally with AES-256-GCM. This is fully transparent to the caller.

The credentials config file is a JSON object containing `clientId`, `tenantId`, `clientSecret`, and `keyVaultUrl`.

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-encrypted-key -f FULL -p \
  -e --kek-type AZURE \
  --kek-uri "azure-kv://my-vault.vault.azure.net/keys/my-kek-key" \
  --kek-config /path/to/azure-credentials.json
```

Where `azure-credentials.json` contains:

```json
{
  "clientId": "...",
  "tenantId": "...",
  "clientSecret": "...",
  "keyVaultUrl": "https://my-vault.vault.azure.net"
}
```

To generate the keyset in RAW format (bare `{encryptedKeyset, keysetInfo}` JSON) suitable for uploading directly as an Azure Key Vault secret value:

```bash
java -jar target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -f RAW \
  -e --kek-type AZURE \
  --kek-uri "azure-kv://my-vault.vault.azure.net/keys/my-kek-key" \
  --kek-config /path/to/azure-credentials.json \
  -o my-encrypted-keyset.json
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

**Note:** Encrypted keyset generation is supported for all algorithms (`AES_GCM`, `AES_GCM_SIV`, and `FPE_FF31`). The KMS provider is discovered at runtime via `ServiceLoader`, so additional KMS types can be supported by adding the corresponding `kryptonite-kms-*` module to the classpath.

## Running Tests

```bash
../mvnw test
```
