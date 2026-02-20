# kryptonite-kms-aws

AWS KMS integration module for [kryptonite-for-kafka](../README.md). Provides two independent capabilities: storing Tink keysets in **AWS Secrets Manager** and using an **AWS KMS key** as the Key Encryption Key (KEK) to protect keysets at rest.

## Capabilities

### 1. Secret Storage — `KmsType: AWS_SM_SECRETS`

Tink keysets are stored as secrets in AWS Secrets Manager. The module resolves keyset identifiers and fetches the corresponding keyset JSON from Secrets Manager at runtime.

Secret names follow a fixed prefix convention to separate plain and encrypted keysets:

| Vault type | Secret name prefix |
|---|---|
| Plain keysets | `k4k/tink_plain/` |
| Encrypted keysets | `k4k/tink_encrypted/` |

The keyset identifier is automatically appended to the prefix to form the full secret name, e.g. `k4k/tink_plain/myKey`. It's an implementation detail that the user of tink keysets doesn't have to care about. You simply create your tink keysets with the identifier only.

### 2. Key Encryption Key — `KekType: AWS`

An AWS KMS symmetric key is used to encrypt Tink keysets at rest. AWS KMS symmetric keys (AES-256) support direct AEAD encryption via the KMS `Encrypt` / `Decrypt` API, so no additional envelope encryption layer is needed.

The module integrates with Tink's KMS client abstraction via the official `tink-awskms` library (`AwsKmsClient`), which is registered with Tink's global `KmsClients` registry.

## Architecture

```
ServiceLoader
  └── AwsKmsKeyVaultProvider   (KmsType="AWS_SM_SECRETS")
        ├── AwsKeyVault          — resolves plain TinkKeyConfig JSON via AwsSecretResolver
        └── AwsKeyVaultEncrypted — resolves encrypted TinkKeyConfigEncrypted JSON, decrypts via KmsKeyEncryption

  └── AwsKmsKeyEncryptionProvider (KekType="AWS")
        └── AwsKeyEncryption     — registers AwsKmsClient with Tink, returns KmsAead-backed KeysetHandle
```

**`AwsSecretResolver`** — implements `KeyMaterialResolver`. Builds an `AWSSecretsManager` client from an access key / secret key credential pair, lists all secrets filtered by prefix (with pagination support), and fetches specific secret values by name.

**`AwsKeyVault`** / **`AwsKeyVaultEncrypted`** — extend `AbstractKeyVault`. Maintain an in-memory cache of `KeysetHandle` objects. Supports optional prefetch (eager load all known identifiers at startup) or lazy on-demand loading.

**`AwsKeyEncryption`** — reads `accessKey` and `secretKey` from the credentials JSON, creates an `AwsKmsClient` with `AWSStaticCredentialsProvider`, registers it with Tink's `KmsClients`, and returns a `KeysetHandle` generated via `KmsAeadKeyManager`. The resulting AEAD primitive delegates all encrypt/decrypt operations to AWS KMS.

Both providers are discovered via Java `ServiceLoader` from `META-INF/services/`.

## Configuration

### Secret Storage (`kmsConfig`)

```json
{
  "accessKey": "AKIA...",
  "secretKey": "...",
  "region": "eu-central-1"
}
```

### KEK (`kekConfig` / `kekUri`)

```json
{
  "accessKey": "AKIA...",
  "secretKey": "..."
}
```

KEK URI format:
```
aws-kms://arn:aws:kms:<region>:<account-id>:key/<key-id>
```

The IAM principal associated with the credentials requires `secretsmanager:GetSecretValue` and `secretsmanager:ListSecrets` for secret storage, and `kms:Encrypt` / `kms:Decrypt` / `kms:GenerateDataKey` on the KMS key for KEK usage.
