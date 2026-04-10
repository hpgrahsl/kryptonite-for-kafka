# GCP Cloud KMS

The `kryptonite-kms-gcp` module adds three capabilities:

1. **Keyset Storage**: fetch Tink keysets from GCP Secret Manager at runtime (`kms_type=GCP_SM_SECRETS`)
2. **Keyset Encryption Key**: use a GCP Cloud KMS symmetric key to encrypt/decrypt keysets at rest (`kek_type=GCP`)
3. **Envelope KEK**: use a GCP Cloud KMS key as the Key Encryption Key for envelope encryption (`cipher_algorithm=TINK/AES_GCM_ENVELOPE_KMS`)

Add the module JAR to the classpath alongside the core library. It is discovered automatically via `ServiceLoader`.

---

## Secret Storage with `kms_type=GCP_SM_SECRETS`

### Secret naming

Secrets are expected to be stored in GCP Secret Manager with a mandatory prefix to differentiate between plain vs. encrypted keysets.

| Keyset type | Secret name prefix | Example |
|---|---|---|
| Plain keysets | `k4k-tink-plain_` | `k4k-tink-plain_my-aes-key` |
| Encrypted keysets | `k4k-tink-encrypted_` | `k4k-tink-encrypted_my-aes-key` |

Create the actual cloud secrets using the `gcloud` CLI or the GCP Console. **Each secret's value must be the Tink keyset JSON in `RAW` format:**

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

GCP Cloud KMS symmetric keys (AES-256) support direct AEAD encryption. The module integrates via Tink's official `tink-java-gcpkms` library.

### IAM permissions required

- `roles/cloudkms.cryptoKeyEncrypterDecrypter` on the specific KMS key

### Configuration for `key_source=CONFIG_ENCRYPTED`

Generate the **GCP KEK encrypted keyset(s) in `FULL` format** using the [keyset tool](../keyset-tool.md), then configure:

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

Generate the **GCP KEK encrypted keyset(s) in `RAW` format** using the [keyset tool](../keyset-tool.md) and store it as a GCP secret.

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

---

## Envelope KEK with `cipher_algorithm=TINK/AES_GCM_ENVELOPE_KMS`

GCP Cloud KMS keys can act as the KEK for envelope encryption. In this mode a fresh DEK is generated per session, all field encryptions use the DEK, and GCP Cloud KMS wraps/unwraps the DEK on session boundaries. The raw KEK material never leaves GCP Cloud KMS.

See [Envelope Encryption](../envelope-encryption.md) for a full explanation of the encrypt/decrypt paths, DEK session lifecycle, and `EdekStore` configuration.

### IAM permissions required

The service account requires the same permission as for keyset encryption:

- `roles/cloudkms.cryptoKeyEncrypterDecrypter` on the specific KMS key

### Configuration

```json
{
  "cipher_algorithm": "TINK/AES_GCM_ENVELOPE_KMS",
  "envelope_kek_identifier": "my-gcp-kek",
  "envelope_kek_configs": "[{\"identifier\":\"my-gcp-kek\",\"type\":\"GCP\",\"uri\":\"gcp-kms://projects/my-project/locations/global/keyRings/my-ring/cryptoKeys/my-kek\",\"config\":{\"credentials\":\"<GCP service account JSON contents>\",\"projectId\":\"my-project\"}}]",
  "edek_store_config": "{\"kafkacache.bootstrap.servers\":\"broker1:9092\",\"kafkacache.topic\":\"_k4k_edeks\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": ""
}
```

`envelope_kek_configs` entry fields:

| Field | Description |
|---|---|
| `identifier` | Logical name referenced by `envelope_kek_identifier` |
| `type` | Must be `GCP` |
| `uri` | Full GCP Cloud KMS key URI (`gcp-kms://projects/...`) |
| `config.credentials` | Full content of the GCP service account JSON key file |
| `config.projectId` | GCP project ID where the KMS key is located |
