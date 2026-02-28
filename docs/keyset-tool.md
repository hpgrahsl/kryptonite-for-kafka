# Keyset Tool

The Keyset Tool is a command-line utility for generating Tink keyset JSON configurations used by all Kryptonite for Kafka modules. It supports both plain and encrypted keyset generation for the supported cipher key types and cloud KMS integrations.

---

## Getting the Tool

### Direct download

Download the pre-built fat JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases). From the attached assets choose `kryptonite-keyset-tool-<VERSION>.jar` which is a self-contained, runnable JAR.

### Build from sources

```bash
./mvnw clean package -DskipTests -pl kryptonite-keyset-tool
```

The JAR is produced at `kryptonite-keyset-tool/target/kryptonite-keyset-tool-<VERSION>.jar`.

---

## Usage

```bash
java -jar kryptonite-keyset-tool-<VERSION>.jar [OPTIONS]
```

```text
Usage: kryptonite-keyset-tool [-ehpV] -a=<algorithm> [-f=<outputFormat>]
                              [-i=<identifier>] [--initial-key-id=<initialKeyId>]
                              [-k=<numKeysets>] [--kek-config=<kekConfigFile>]
                              [--kek-type=<kekType>] [--kek-uri=<kekUri>]
                              [-n=<numKeys>] [-o=<outputFile>] [-s=<keySize>]
```

Or run directly via Maven:

```bash
./mvnw -q exec:java -pl kryptonite-keyset-tool -Dexec.args="[OPTIONS]"
```

---

## Options

| Option | Required | Default | Description |
|---|---|---|---|
| `-a, --algorithm` | Yes | — | Cipher algorithm: `AES_GCM`, `AES_GCM_SIV`, or `FPE_FF31` |
| `-i, --identifier` | When format=`FULL` | — | Keyset identifier (required when output format is `FULL`) |
| `-f, --output-format` | No | `FULL` | `FULL` (with identifier wrapper) or `RAW` (bare Tink keyset) |
| `-s, --key-size` | No | `256` | Key size in bits. `AES_GCM`: 128 or 256. `AES_GCM_SIV`: fixed (ignored). `FPE_FF31`: 128, 192, or 256 |
| `-n, --num-keys` | No | `1` | Number of keys per keyset (1–1000) — useful for key rotation setups |
| `-k, --num-keysets` | No | `1` | Number of keysets to generate. When >1, output is a JSON array; identifiers are suffixed `_1`, `_2`, etc. |
| `--initial-key-id` | No | `10000` | Starting key ID, incremented by 1 per additional key |
| `-o, --output` | No | `stdout` | Output file path (default: `stdout`) |
| `-p, --pretty` | No | `false` | Pretty-print JSON output (default: single-line) |
| `-e, --encrypt` | No | `false` | Encrypt the generated keyset(s) using a KMS key encryption key (KEK). Requires: `--kek-type`, `--kek-uri`, `--kek-config` |
| `--kek-type` | When `-e` or `--encrypt` | — | `GCP`, `AWS`, or `AZURE` |
| `--kek-uri` | When `-e` or `--encrypt` | — | KMS key URI (see format below) |
| `--kek-config` | When `-e` or `--encrypt` | — | Path to a credentials JSON file |
| `-h, --help` | — | — | Show this help message and exit |
| `-V, --version` | — | — | Print version information and exit |

### KEK URI formats

| Provider | URI format |
|---|---|
| GCP | `gcp-kms://projects/<project>/locations/<location>/keyRings/<ring>/cryptoKeys/<key>` |
| AWS | `aws-kms://arn:aws:kms:<region>:<account>:key/<key-id>` |
| Azure | `azure-kv://<vault-name>.vault.azure.net/keys/<key-name>` |

### Key ID assignment

Key IDs are assigned sequentially from `--initial-key-id`. The first (lowest) key ID becomes the `primaryKeyId`. When generating multiple keysets (`-k N`), IDs are non-overlapping across keysets.

Example with `-n 3 -k 2 --initial-key-id 1000`:

- Keyset 1: IDs 1000, 1001, 1002 (primary = 1000)
- Keyset 2: IDs 1003, 1004, 1005 (primary = 1003)

---

## Plain Keysets

### Single AES-GCM keyset (`FULL` format)

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
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

### Single AES-GCM-SIV keyset (`RAW` format)

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -i my-det-key -f RAW -p
```

Output:

```json
  {
    "primaryKeyId" : 10000,
    "key" : [ {
      "keyData" : {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesSivKey",
        "value" : "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 10000,
      "outputPrefixType" : "TINK"
    } ]
  }
```

### FPE keyset 192-bit (`FULL` format)

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a FPE_FF31 -i my-fpe-key -f FULL -s 192 -p
```

Output:

```json
{
  "identifier" : "my-fpe-key",
  "material" : {
    "primaryKeyId" : 10000,
    "key" : [ {
      "keyData" : {
        "typeUrl" : "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
        "value" : "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 10000,
      "outputPrefixType" : "RAW"
    } ]
  }
}
```

FPE keyset output uses `typeUrl: io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey` and `outputPrefixType: RAW`.

### Single multi-key keyset

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-rotating-key -f FULL -n 3 --initial-key-id 20000 -p
```

Output:

```json
{
  "identifier" : "my-rotating-key",
  "material" : {
    "primaryKeyId" : 20000,
    "key" : [ {
      "keyData" : {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value" : "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 20000,
      "outputPrefixType" : "TINK"
    }, {
      "keyData" : {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value" : "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 20001,
      "outputPrefixType" : "TINK"
    }, {
      "keyData" : {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value" : "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 20002,
      "outputPrefixType" : "TINK"
    } ]
  }
}
```

### Multiple single-key keysets

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i demo-key -f FULL -k 2 -n 1 --initial-key-id 10000 -p
```

Output:

```json
[ {
  "identifier" : "demo-key_1",
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
}, {
  "identifier" : "demo-key_2",
  "material" : {
    "primaryKeyId" : 10001,
    "key" : [ {
      "keyData" : {
        "typeUrl" : "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value" : "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType" : "SYMMETRIC"
      },
      "status" : "ENABLED",
      "keyId" : 10001,
      "outputPrefixType" : "TINK"
    } ]
  }
} ]
```

Produces a JSON array of 2 keyset objects with identifiers `demo-key_1` and `demo-key_2` each containing a single key.

### RAW keyset format

Use `RAW` format when uploading directly as a secret value to a cloud secret manager. Note that the `identifier` wrapper is not needed there — the secret name itself acts as the identifier:

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -f RAW -p
```

Output:

```json
{
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
```

#### Writing keysets to output files

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -f RAW -o /path/to/keyset.json -p
```

File `keyset.json`:

```json
{
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
```

---

## Encrypted Keysets

When `key_source=CONFIG_ENCRYPTED` or `key_source=KMS_ENCRYPTED`, keysets are expected to be encrypted at rest using a key encryption key (KEK) stored in a supported cloud KMS. Pass `-e` together with `--kek-type`, `--kek-uri`, and `--kek-config`.

The `--kek-config` option takes a **file path** to a JSON credentials file — not an inline JSON string.

### Encrypted keyset with GCP Cloud KMS

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f RAW -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json
```

`gcp-credentials.json`: 

```json
{
  "type": "service_account",
  "project_id": "...",
  "private_key_id": "...",
  "private_key": "...",
  "client_email": "...",
  "client_id": "...",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/..."
}
```

### Encrypted keyset with AWS KMS

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f RAW -p \
  -e --kek-type AWS \
  --kek-uri "aws-kms://arn:aws:kms:eu-central-1:123456789012:key/abcd-1234-efgh-5678" \
  --kek-config /path/to/aws-credentials.json
```

`aws-credentials.json`:

```json
{
  "accessKey": "...",
  "secretKey": "..."
}
```

### Encrypted keyset with Azure Key Vault

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f RAW -p \
  -e --kek-type AZURE \
  --kek-uri "azure-kv://my-vault.vault.azure.net/keys/my-kek-key" \
  --kek-config /path/to/azure-credentials.json
```

`azure-credentials.json`:

```json
{
  "clientId": "...",
  "tenantId": "...",
  "clientSecret": "...",
  "keyVaultUrl": "https://my-vault.vault.azure.net"
}
```

!!! note "Azure envelope encryption"
    Azure Key Vault standard tier only supports RSA keys — symmetric AEAD is not available. The module transparently implements envelope encryption: the keyset is encrypted locally with AES-256-GCM, and only the 32-byte DEK is wrapped with RSA-OAEP-256 via Azure Key Vault. The caller does not need to configure anything differently.

### Multiple encrypted keysets

Example keyset tool call with GCP KMS keyset encryption intended for `key_source=CONFIG_ENCRYPTED` uses which expects `FULL` keyset format.

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -i det-key -f FULL -k 3 -n 2 -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json \
  -o encrypted-keysets-full.json
```

Example keyset tool call with GCP KMS keyset encryption intended for `key_source=KMS_ENCRYPTED` uses which expects `RAW` keyset format.

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -f RAW -k 3 -n 2 -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json \
  -o encrypted-keysets-raw.json
```

---

## Output format with/out keyset encryption

| `--output-format` | With `-e` | Resulting JSON structure |
|---|---|---|
| `FULL` | No | `{"identifier": "...", "material": {<plain Tink keyset>}}` |
| `FULL` | Yes | `{"identifier": "...", "material": {<encrypted Tink keyset>}` |
| `RAW` | No | `{<plain Tink keyset>}` |
| `RAW` | Yes | `{<encrypted Tink keyset>}` |
