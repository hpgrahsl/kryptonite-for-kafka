# kryptonite-kms-azure

Azure KMS integration module for [kryptonite-for-kafka](../README.md). Provides two independent capabilities: storing Tink keysets in **Azure Key Vault Secrets** and using an **Azure Key Vault Key** as the Key Encryption Key (KEK) to protect keysets at rest.

## Capabilities

### 1. Secret Storage — `KmsType: AZ_KV_SECRETS`

Tink keysets are stored as secrets in Azure Key Vault (Secrets API). The module resolves keyset identifiers and fetches the corresponding keyset JSON from Key Vault at runtime.

Unlike the AWS and GCP modules, Azure Key Vault uses the secret name directly as the keyset identifier — no prefix convention is applied. Therefore, each secret's name is exactly the keyset identifier used by kryptonite. The idea for Azure is to fully separate plaintext from encrypted keysets which is possible since Azure supports multiple different key vault instances, each having their separate base URLs.

### 2. Key Encryption Key — `KekType: AZURE`

An Azure Key Vault RSA key is used to encrypt Tink keysets at rest.

> **Note — envelope encryption required.** Azure Key Vault standard tier only exposes RSA and EC keys for cryptographic operations; it does not offer symmetric AEAD primitives. An RSA-OAEP-256 key wrapping operation is limited to the RSA modulus size (e.g., ~446 bytes for a 4096-bit key), which is far less than the size of a typical Tink keyset. Direct encryption is therefore not possible.
>
> To work around this constraint, the module implements **envelope encryption** entirely within the custom `AzureKmsAead` class, mimicking the approach used internally by the `tink-awskms` / `tink-gcpkms` libraries for their own use cases.

## Architecture

```
ServiceLoader
  └── AzureKmsKeyVaultProvider   (KmsType="AZ_KV_SECRETS")
        ├── AzureKeyVault          — resolves plain TinkKeyConfig JSON via AzureSecretResolver
        └── AzureKeyVaultEncrypted — resolves encrypted TinkKeyConfigEncrypted JSON, decrypts via KmsKeyEncryption

  └── AzureKmsKeyEncryptionProvider (KekType="AZURE")
        └── AzureKeyEncryption     — registers AzureKmsClient with Tink, returns KmsAead-backed KeysetHandle
              └── AzureKmsClient   — Tink KmsClient for azure-kv:// URIs
                    └── AzureKmsAead — envelope encryption via RSA-OAEP-256 + AES-256-GCM
```

**`AzureSecretResolver`** — implements `KeyMaterialResolver`. Builds a `SecretClient` (Azure SDK) from service-principal credentials, lists all secrets in the vault, and fetches specific secret values by name.

**`AzureKeyVault`** / **`AzureKeyVaultEncrypted`** — extend `AbstractKeyVault`. Maintain an in-memory cache of `KeysetHandle` objects. Supports optional prefetch (eager load all known identifiers at startup) or lazy on-demand loading.

**`AzureKeyEncryption`** — registers an `AzureKmsClient` with Tink's global `KmsClients` registry and returns a `KeysetHandle` generated via `KmsAeadKeyManager`. The resulting AEAD primitive delegates to `AzureKmsAead`.

### Envelope Encryption Detail (`AzureKmsAead`)

`AzureKmsAead` implements Tink's `Aead` interface using a two-level scheme:

**Encrypt:**
1. Generate a fresh ephemeral 256-bit DEK and 12-byte IV using `SecureRandom`.
2. Encrypt the plaintext locally with **AES-256-GCM** using the DEK; `associatedData` is bound as GCM AAD.
3. Wrap the 32-byte DEK with **RSA-OAEP-256** via Azure Key Vault's `CryptographyClient.wrapKey()` — only the small DEK is sent to Azure.
4. Assemble the output in a self-describing wire format.

**Decrypt:** parse the wire format, unwrap the DEK via `CryptographyClient.unwrapKey()`, then AES-256-GCM decrypt (GCM tag failure automatically rejects tampered ciphertext or wrong `associatedData`).

**Wire format:**
```
[ 4 bytes  : wrappedKeyLen (big-endian int)   ]
[ N bytes  : RSA-OAEP-256 wrapped DEK         ]
[ 12 bytes : GCM IV                           ]
[ M bytes  : AES-256-GCM ciphertext + 16-byte tag ]
```

**`AzureKmsClient`** — implements Tink's `KmsClient` for  custom `azure-kv://` URIs. On `getAead(uri)` it strips the `azure-kv://` scheme, prepends `https://` to form the Key Vault key URL, builds a `CryptographyClient`, and returns an `AzureKmsAead`.

Both providers are discovered via Java `ServiceLoader` from `META-INF/services/`.

## Configuration

### Secret Storage (`kmsConfig`)

```json
{
  "clientId": "...",
  "tenantId": "...",
  "clientSecret": "...",
  "keyVaultUrl": "https://<vault-name>.vault.azure.net"
}
```

### KEK (`kekConfig` / `kekUri`)

```json
{
  "clientId": "...",
  "tenantId": "...",
  "clientSecret": "...",
  "keyVaultUrl": "https://<vault-name>.vault.azure.net"
}
```

KEK URI format:
```
azure-kv://<vault-name>.vault.azure.net/keys/<key-name>[/<version>]
```

The service principal requires `Secret Get` and `Secret List` permissions on the secrets vault for secret storage, and `Key Wrap Key` / `Key Unwrap Key` permissions on the keys vault for KEK usage. A separate Key Vault instance for KEK keys is recommended to cleanly isolate the two permission scopes.
