# GCP Cloud KMS

The `kryptonite-kms-gcp` module adds two capabilities:

1. **Keyset Storage**: fetch Tink keysets from GCP Secret Manager at runtime (`kms_type=GCP_SM_SECRETS`)
2. **Keyset Encryption Key**: use a GCP Cloud KMS symmetric key to encrypt/decrypt keysets at rest (`kek_type=GCP`)

Add the module JAR to the classpath alongside the core library. It is discovered automatically via `ServiceLoader`.

---

## Secret Storage with `kms_type=GCP_SM_SECRETS`

### Secret naming

Secrets are expected to be stored in GCP Secret Manager with a mandatory prefix to differentiate between plain vs. encrypted keysets.

| Keyset type | Secret name prefix | Example |
|---|---|---|
| Plain keysets | `k4k-tink-plain_` | `k4k-tink-plain_my-aes-key` |
| Encrypted keysets | `k4k-tink-encrypted_` | `k4k-tink-encrypted_my-aes-key` |

Create secrets using the `gcloud` CLI or the Console. The secret value must be the Tink keyset JSON (in `RAW` format):

```bash
gcloud secrets create k4k-tink-plain_my-aes-key \
  --data-file=my-aes-key-raw.json \
  --project=my-gcp-project
```

### IAM permissions required

The service account used in `kms_config` requires:

- `roles/secretmanager.secretAccessor` on the project (or on individual secrets)

### Configuration

```json
{
  "key_source": "KMS",
  "kms_type": "GCP_SM_SECRETS",
  "kms_config": "{\"credentials\":\"<GCP service account JSON contents>\",\"projectId\":\"my-gcp-project\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": "my-aes-key"
}
```

`kms_config` fields:

| Field | Description |
|---|---|
| `credentials` | Full content of the GCP service account JSON key file |
| `projectId` | GCP project ID where secrets are stored |

---

## Key Encryption Key with `kek_type=GCP`

GCP Cloud KMS symmetric keys (AES-256) support direct AEAD encryption. The module integrates via Tink's official `tink-java-gcpkms` library. No envelope encryption is needed.

### IAM permissions required

- `roles/cloudkms.cryptoKeyEncrypterDecrypter` on the specific KMS key

### Generate an encrypted keyset

:octicons-book-24: [Keyset Tool Details](../keyset-tool.md) 

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -p \
  -e --kek-type GCP \
  --kek-uri "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek" \
  --kek-config /path/to/gcp-service-account.json
```

`--kek-config` takes the **file path** to the service account JSON file.

### Configuration for `key_source=CONFIG_ENCRYPTED`

Generate the GCP KEK encrypted keyset in `FULL` format using the [keyset tool](../keyset-tool.md), then configure:

```json
{
  "key_source": "CONFIG_ENCRYPTED",
  "cipher_data_keys": "[{\"identifier\":\"my-key\",\"material\":{\"encryptedKeyset\":\"...\",\"keysetInfo\":{...}}}]",
  "cipher_data_key_identifier": "my-key",
  "kek_type": "GCP",
  "kek_uri": "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek",
  "kek_config": "{\"credentials\":\"<GCP service account JSON contents>\",\"projectId\":\"my-project\"}"
}
```

### Configuration for `key_source=KMS_ENCRYPTED`

Generate the GCP KEK encrypted keyset in `RAW` format using the [keyset tool](../keyset-tool.md) and store it as a GCP secret.

Then configure:

```json
{
  "key_source": "KMS_ENCRYPTED",
  "kms_type": "GCP_SM_SECRETS",
  "kms_config": "{\"credentials\":\"<GCP service account JSON contents>\",\"projectId\":\"my-gcp-project\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": "my-key",
  "kek_type": "GCP",
  "kek_uri": "gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek",
  "kek_config": "{\"credentials\":\"<GCP service account JSON contents>\",\"projectId\":\"my-project\"}"
}
```
