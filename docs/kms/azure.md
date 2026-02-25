# Azure Key Vault

The `kryptonite-kms-azure` module adds two capabilities:

1. **Keyset storage**: fetch Tink keysets from Azure Key Vault Secrets at runtime (`kms_type=AZ_KV_SECRETS`)
2. **Keyset Encryption**: use an Azure Key Vault RSA key to encrypt/decrypt keysets at rest (`kek_type=AZURE`)

Add the module JAR to the classpath alongside the core library. It is discovered automatically via `ServiceLoader`.

---

## Secret Storage with `kms_type=AWS_SM_SECRETS`

### Secret naming

Unlike the AWS and GCP modules, Azure Key Vault uses the secret name **directly** as the keyset identifier, hence no prefix is prepended. The secret name is exactly the identifier used by the module. To separate plain and encrypted keysets, use two different Key Vault instances each with their individual `keyVaultUrl`.

Create secrets using the Azure CLI or Portal. The secret value must be the Tink keyset JSON (in `RAW` format):

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
    Azure Key Vault standard tier only exposes RSA and EC keys — symmetric AEAD is not available. Since direct RSA encryption of a full Tink keyset is not feasible (RSA modulus limit) the module transparently implements **envelope encryption**:

    1. A fresh ephemeral 256-bit DEK and 12-byte IV are generated locally
    2. The keyset is encrypted locally with AES-256-GCM using the DEK
    3. Only the 32-byte DEK is wrapped with RSA-OAEP-256 via `CryptographyClient.wrapKey()`
    4. The output wire format: `[4-byte wrappedKeyLen][wrappedKey][12-byte IV][AES-GCM ciphertext+tag]`

    As this is fully transparent to the caller it can be configured like any other KEK provider.

### IAM permissions required

The service principal used in `kek_config` requires the following permissions on the key vault (which may be a separate vault from the keyset storage vault):

- `Key Wrap Key`
- `Key Unwrap Key`

!!! tip "Use separate vaults for keys and secrets"
    It is recommended to use one Key Vault instance for KEK keys (with `Key Wrap/Unwrap` permissions) and a separate instance for keyset secrets (with `Secret Get/List` permissions). This isolates the two permission scopes cleanly.

### Generate an encrypted keyset

:octicons-book-24: [Keyset Tool Details](../keyset-tool.md) 

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -p \
  -e --kek-type AZURE \
  --kek-uri "azure-kv://my-keys-vault.vault.azure.net/keys/my-rsa-kek" \
  --kek-config /path/to/azure-credentials.json
```

`azure-credentials.json`:

```json
{
  "clientId": "...",
  "tenantId": "...",
  "clientSecret": "...",
  "keyVaultUrl": "https://my-keys-vault.vault.azure.net"
}
```

### KEK URI format

```
azure-kv://<vault-name>.vault.azure.net/keys/<key-name>
azure-kv://<vault-name>.vault.azure.net/keys/<key-name>/<version>
```

Omitting the version uses the current / latest key version.

### Configuration for `key_source=CONFIG_ENCRYPTED`)

Generate the Azure KEK encrypted keyset in `FULL` format using the [keyset tool](../keyset-tool.md), then configure:

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

Generate the Azure KEK encrypted keyset in `RAW` format using the [keyset tool](../keyset-tool.md) and store it as an Azure Key Vault secret.

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
