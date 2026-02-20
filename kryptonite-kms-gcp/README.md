# kryptonite-kms-gcp

GCP KMS integration module for [kryptonite-for-kafka](../README.md). Provides two independent capabilities: storing Tink keysets in **GCP Secret Manager** and using a **GCP Cloud KMS key** as the Key Encryption Key (KEK) to protect keysets at rest.

## Capabilities

### 1. Secret Storage — `KmsType: GCP_SM_SECRETS`

Tink keysets are stored as secrets in GCP Secret Manager. The module resolves keyset identifiers and fetches the corresponding keyset JSON from Secret Manager at runtime.

Secret names follow a fixed prefix convention to separate plain and encrypted keysets within the same GCP project:

| Vault type | Secret name prefix |
|---|---|
| Plain keysets | `k4k-tink-plain_` |
| Encrypted keysets | `k4k-tink-encrypted_` |

The keyset identifier is automatically appended to the prefix to form the full secret name, e.g. `k4k-tink-plain_myKey`. It's an implementation detail that the user of tink keysets doesn't have to care about. You simply create your tink keysets with the identifier only.

### 2. Key Encryption Key — `KekType: GCP`

A GCP Cloud KMS symmetric key is used to encrypt Tink keysets at rest. GCP Cloud KMS symmetric keys (AES-256) support direct AEAD encryption of arbitrary-size payloads via the KMS `Encrypt` / `Decrypt` API, so no additional envelope encryption layer is needed.

The module integrates with Tink's KMS client abstraction via the official `tink-gcpkms` library (`GcpKmsClient`), which is registered with Tink's global `KmsClients` registry.

## Architecture

```
ServiceLoader
  └── GcpKmsKeyVaultProvider   (KmsType="GCP_SM_SECRETS")
        ├── GcpKeyVault          — resolves plain TinkKeyConfig JSON via GcpSecretResolver
        └── GcpKeyVaultEncrypted — resolves encrypted TinkKeyConfigEncrypted JSON, decrypts via KmsKeyEncryption

  └── GcpKmsKeyEncryptionProvider (KekType="GCP")
        └── GcpKeyEncryption     — registers GcpKmsClient with Tink, returns KmsAead-backed KeysetHandle
```

**`GcpSecretResolver`** — implements `KeyMaterialResolver`. Builds a `SecretManagerServiceClient` from a GCP service account JSON credential, lists all secrets by prefix, and fetches specific secret versions (`latest`) by name.

**`GcpKeyVault`** / **`GcpKeyVaultEncrypted`** — extend `AbstractKeyVault`. Maintain an in-memory cache of `KeysetHandle` objects. Supports optional prefetch (eager load all known identifiers at startup) or lazy on-demand loading.

**`GcpKeyEncryption`** — loads `GoogleCredentials` from the credentials JSON, creates a `GcpKmsClient` bound to the KEK URI, registers it with Tink's `KmsClients`, and returns a `KeysetHandle` generated via `KmsAeadKeyManager`. The resulting AEAD primitive delegates all encrypt/decrypt operations to GCP Cloud KMS.

Both providers are discovered via Java `ServiceLoader` from `META-INF/services/`.

## Configuration

### Secret Storage (`kmsConfig`)

```json
{
  "credentials": "<GCP service account JSON contents>",
  "projectId": "my-gcp-project"
}
```

### KEK (`kekConfig` / `kekUri`)

```json
{
  "credentials": "<GCP service account JSON contents>",
  "projectId": "my-gcp-project"
}
```

KEK URI format:
```
gcp-kms://projects/<project>/locations/<location>/keyRings/<keyring>/cryptoKeys/<key>
```

The same service account JSON is used for both secret storage and KEK operations. The account requires `roles/secretmanager.secretAccessor` for secret storage and `roles/cloudkms.cryptoKeyEncrypterDecrypter` for KEK usage.
