# Keyset Tool

The Keyset Tool is a command-line utility for generating Tink keyset JSON configurations used by all Kryptonite for Kafka modules. It supports plain and KMS-encrypted keyset generation for all three supported algorithms and all three cloud KMS backends.

---

## Getting the Tool

### Download (recommended)

Download the pre-built fat JAR from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).

### Build from source

```bash
./mvnw clean package -DskipTests -pl kryptonite-keyset-tool
```

The JAR is produced at `kryptonite-keyset-tool/target/kryptonite-keyset-tool-0.1.0.jar`.

---

## Usage

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar [OPTIONS]
```

Or run via Maven without building the JAR:

```bash
./mvnw -q exec:java -pl kryptonite-keyset-tool -Dexec.args="[OPTIONS]"
```

---

## Options

| Option | Required | Default | Description |
|---|---|---|---|
| `-a, --algorithm` | Yes | — | `AES_GCM`, `AES_GCM_SIV`, or `FPE_FF31` |
| `-i, --identifier` | When format=`FULL` | — | Keyset identifier string |
| `-f, --output-format` | No | `FULL` | `FULL` (with identifier wrapper) or `RAW` (bare Tink keyset) |
| `-s, --key-size` | No | `256` | Key size in bits. AES_GCM: 128 or 256. AES_GCM_SIV: fixed (ignored). FPE_FF31: 128, 192, or 256 |
| `-n, --num-keys` | No | `1` | Number of keys per keyset (1–1000) — useful for key rotation setups |
| `-k, --num-keysets` | No | `1` | Number of keysets to generate. When >1, output is a JSON array; identifiers are suffixed `_1`, `_2`, etc. |
| `--initial-key-id` | No | `10000` | Starting key ID, incremented by 1 per additional key |
| `-o, --output` | No | stdout | Output file path |
| `-p, --pretty` | No | `false` | Pretty-print JSON output |
| `-e, --encrypt` | No | `false` | Encrypt the keyset using a cloud KMS KEK. Requires `--kek-type`, `--kek-uri`, `--kek-config` |
| `--kek-type` | When `--encrypt` | — | `GCP`, `AWS`, or `AZURE` |
| `--kek-uri` | When `--encrypt` | — | KMS key URI (see format below) |
| `--kek-config` | When `--encrypt` | — | Path to a credentials JSON file |
| `-h, --help` | — | — | Show help |
| `-V, --version` | — | — | Show version |

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

### Single AES-GCM keyset (FULL format)

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

### Deterministic keyset (AES-GCM-SIV) — for Kafka record keys

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -i my-det-key -f FULL -p
```

### FPE keyset with 192-bit key

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a FPE_FF31 -i my-fpe-key -f FULL -s 192 -p
```

FPE keyset output uses `typeUrl: io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey` and `outputPrefixType: RAW`.

### Multi-key keyset for key rotation

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-rotating-key -f FULL -n 3 --initial-key-id 20000 -p
```

### Multiple keysets as a JSON array

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i demo-key -f FULL -k 3 -n 2 --initial-key-id 10000 -p
```

Produces a JSON array of 3 keyset objects with identifiers `demo-key_1`, `demo-key_2`, `demo-key_3`.

### RAW format (bare Tink keyset, no wrapper)

Use `RAW` format when uploading directly as a secret value to a cloud secret manager (the `identifier` wrapper is not needed there — the secret name itself is the identifier):

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -f RAW -p
```

### Write output to file

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -o /path/to/keyset.json -p
```

---

## KEK-Encrypted Keysets

When `key_source=CONFIG_ENCRYPTED` or `key_source=KMS_ENCRYPTED`, keysets must be encrypted at rest using a cloud KMS KEK. Pass `-e` together with `--kek-type`, `--kek-uri`, and `--kek-config`.

The `--kek-config` option takes a **file path** to a JSON credentials file — not an inline JSON string.

### Encrypted keyset with GCP Cloud KMS

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json
```

`gcp-credentials.json` — GCP service account JSON file.

### Encrypted keyset with AWS KMS

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -p \
  -e --kek-type AWS \
  --kek-uri "aws-kms://arn:aws:kms:eu-central-1:123456789012:key/abcd-1234-efgh-5678" \
  --kek-config /path/to/aws-credentials.json
```

`aws-credentials.json`:

```json
{
  "accessKey": "AKIA...",
  "secretKey": "..."
}
```

### Encrypted keyset with Azure Key Vault

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -p \
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

### Encrypted keyset in RAW format — for cloud secret manager upload

To generate an encrypted keyset suitable for uploading directly as a secret value (used with `key_source=KMS_ENCRYPTED`):

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -f RAW \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json \
  -o my-encrypted-keyset.json
```

The resulting `{encryptedKeyset, keysetInfo}` JSON can be stored directly as the secret value. The secret name in the cloud secret manager acts as the keyset identifier.

### Multiple encrypted keysets in one call

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM_SIV -i det-key -f FULL -k 3 -n 2 -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-credentials.json \
  -o encrypted-keysets.json
```

---

## Output format summary

| `--output-format` | With `-e` | Resulting JSON structure |
|---|---|---|
| `FULL` | No | `{"identifier": "...", "material": {<plain Tink keyset>}}` |
| `FULL` | Yes | `{"identifier": "...", "material": {"encryptedKeyset": "...", "keysetInfo": {...}}}` |
| `RAW` | No | `{<plain Tink keyset>}` |
| `RAW` | Yes | `{"encryptedKeyset": "...", "keysetInfo": {...}}` |
