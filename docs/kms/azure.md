# Azure Key Vault

The `kryptonite-kms-azure` module adds three capabilities:

1. **Keyset storage**: fetch Tink keysets from Azure Key Vault Secrets at runtime (`kms_type=AZ_KV_SECRETS`)
2. **Keyset Encryption**: use an Azure Key Vault RSA key to encrypt/decrypt keysets at rest (`kek_type=AZURE`)
3. **Envelope KEK**: use an Azure Key Vault RSA key as the Key Encryption Key for envelope encryption (`cipher_algorithm=TINK/AES_GCM_ENVELOPE_KMS`)

Add the module JAR to the classpath alongside the core library. The runtime discovers it automatically via `ServiceLoader`.

---

## Secret Storage with `kms_type=AZ_KV_SECRETS`

### Secret naming

Unlike the AWS and GCP modules, Azure Key Vault uses the secret name **directly** as the keyset identifier, hence no prefix is prepended. The secret name is exactly the identifier used by the module. To separate plain and encrypted keysets, use two different Key Vault instances each with their individual `keyVaultUrl`.

Create the actual cloud secrets using the Azure CLI or Portal. **Each secret's value must be the Tink keyset JSON in `RAW` format:**

```bash
az keyvault secret set \
  --vault-name my-secrets-vault \
  --name my-aes-key \
  --value "$(cat my-aes-key-raw.json)"
```

### IAM permissions required

The service principal used in `kms_config` requires on the secrets vault:

- `Secret Get`
- `Secret List`

### Configuration

```json
{
  "key_source": "KMS",
  "kms_type": "AZ_KV_SECRETS",
  "kms_config": "{\"clientId\":\"...\",\"tenantId\":\"...\",\"clientSecret\":\"...\",\"keyVaultUrl\":\"https://my-secrets-vault.vault.azure.net\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": "my-aes-key"
}
```

`kms_config` fields:

| Field | Description |
|---|---|
| `clientId` | Service principal application ID |
| `tenantId` | Azure Active Directory tenant ID |
| `clientSecret` | Service principal client secret |
| `keyVaultUrl` | Base URL of the Key Vault instance, e.g. `https://my-vault.vault.azure.net` |

---

## Key Encryption Key with `kek_type=AZURE`

!!! info "Envelope encryption"
    Azure Key Vault standard tier only exposes RSA and EC keys — symmetric AEAD is not available. Since direct RSA encryption of a full Tink keyset is impractical (RSA modulus limit), the module transparently implements **envelope encryption**:

    1. A fresh ephemeral 256-bit DEK and 12-byte IV are generated locally
    2. The keyset is encrypted locally with AES-256-GCM using the DEK
    3. Only the 32-byte DEK is wrapped with RSA-OAEP-256 via `CryptographyClient.wrapKey()`
    4. The output wire format: `[4-byte wrappedKeyLen][wrappedKey][12-byte IV][AES-GCM ciphertext+tag]`

    Since this is fully transparent to the caller, it can be configured like any other KEK provider.

### IAM permissions required

The service principal used in `kek_config` requires the following permissions on the key vault (which may be a separate vault from the keyset storage vault):

- `Key Wrap Key`
- `Key Unwrap Key`

!!! tip "Use separate vaults for keys and secrets"
    Use one Key Vault instance for KEK keys (with `Key Wrap/Unwrap` permissions) and a separate instance for keyset secrets (with `Secret Get/List` permissions). This isolates the two permission scopes cleanly.

### KEK URI format

```
azure-kv://<vault-name>.vault.azure.net/keys/<key-name>
azure-kv://<vault-name>.vault.azure.net/keys/<key-name>/<version>
```

Omitting the version uses the current / latest key version.

### Configuration for `key_source=CONFIG_ENCRYPTED`

Generate the **Azure KEK encrypted keyset(s) in `FULL` format** using the [keyset tool](../keyset-tool.md), then configure:

```json
{
  "key_source": "CONFIG_ENCRYPTED",
  "cipher_data_keys": "[{\"identifier\":\"my-key\",\"material\":{\"encryptedKeyset\":\"...\",\"keysetInfo\":{...}}}]",
  "cipher_data_key_identifier": "my-key",
  "kek_type": "AZURE",
  "kek_uri": "azure-kv://my-keys-vault.vault.azure.net/keys/my-rsa-kek",
  "kek_config": "{\"clientId\":\"...\",\"tenantId\":\"...\",\"clientSecret\":\"...\",\"keyVaultUrl\":\"https://my-keys-vault.vault.azure.net\"}"
}
```

### Configuration for `key_source=KMS_ENCRYPTED`

Generate the **Azure KEK encrypted keyset(s) in `RAW` format** using the [keyset tool](../keyset-tool.md) and store it as an Azure Key Vault secret.

Then configure:

```json
{
  "key_source": "KMS_ENCRYPTED",
  "kms_type": "AZ_KV_SECRETS",
  "kms_config": "{\"clientId\":\"...\",\"tenantId\":\"...\",\"clientSecret\":\"...\",\"keyVaultUrl\":\"https://my-secrets-vault.vault.azure.net\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": "my-key",
  "kek_type": "AZURE",
  "kek_uri": "azure-kv://my-keys-vault.vault.azure.net/keys/my-rsa-kek",
  "kek_config": "{\"clientId\":\"...\",\"tenantId\":\"...\",\"clientSecret\":\"...\",\"keyVaultUrl\":\"https://my-keys-vault.vault.azure.net\"}"
}
```

---

## Envelope KEK with `cipher_algorithm=TINK/AES_GCM_ENVELOPE_KMS`

Azure Key Vault RSA keys can act as the KEK for field-level envelope encryption. In this mode a fresh DEK is generated per session, all field encryption uses the DEK, and Azure Key Vault wraps/unwraps the DEK on session boundaries using RSA-OAEP-256. The wrapped DEK (~256 bytes) is stored in the EdekStore (external Kafka topic) — only a compact 16-byte fingerprint travels with each ciphertext.

See [Envelope Encryption](../envelope-encryption.md) for a full explanation of the encrypt/decrypt paths, DEK session lifecycle, and EdekStore configuration.

!!! note "AAD not supported"
    Azure Key Vault's RSA-OAEP-256 key wrap/unwrap API has no associated-data parameter. The `wrapAad` binding present in GCP and AWS providers is silently ignored for Azure. The security model relies on the EdekStore fingerprint lookup chain rather than cryptographic binding of AAD to the wrapped DEK.

### IAM permissions required

The service principal used in `envelope_kek_configs` requires on the keys vault:

- `Key Wrap Key`
- `Key Unwrap Key`

### Configuration

```json
{
  "cipher_algorithm": "TINK/AES_GCM_ENVELOPE_KMS",
  "envelope_kek_identifier": "my-azure-kek",
  "envelope_kek_configs": "[{\"identifier\":\"my-azure-kek\",\"type\":\"AZURE\",\"uri\":\"azure-kv://my-keys-vault.vault.azure.net/keys/my-rsa-kek\",\"config\":{\"clientId\":\"...\",\"tenantId\":\"...\",\"clientSecret\":\"...\",\"keyVaultUrl\":\"https://my-keys-vault.vault.azure.net\"}}]",
  "edek_store_config": "{\"kafkacache.bootstrap.servers\":\"broker1:9092\",\"kafkacache.topic\":\"_k4k_edeks\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": ""
}
```

`envelope_kek_configs` entry fields:

| Field | Description |
|---|---|
| `identifier` | Logical name referenced by `envelope_kek_identifier` |
| `type` | Must be `AZURE` |
| `uri` | Azure Key Vault key URI (`azure-kv://<vault-host>/keys/<key-name>[/<version>]`) |
| `config.clientId` | Service principal application ID |
| `config.tenantId` | Azure Active Directory tenant ID |
| `config.clientSecret` | Service principal client secret |
| `config.keyVaultUrl` | Base URL of the Key Vault instance holding the RSA key |
