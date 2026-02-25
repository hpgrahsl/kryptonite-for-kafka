# Key Management

Kryptonite for Kafka supports four modes for sourcing key material, controlled by the `key_source` parameter. The right choice depends on your security posture and operational model.

---

## Overview

```
key_source=CONFIG              → plain keysets inline in config
key_source=CONFIG_ENCRYPTED    → KEK-encrypted keysets inline in config
key_source=KMS                 → plain keysets in cloud secret manager
key_source=KMS_ENCRYPTED       → KEK-encrypted keysets in cloud secret manager
```

---

## `CONFIG` — Plain keysets in config

The simplest mode. Tink keyset JSON is embedded directly in the `cipher_data_keys` configuration parameter.

**When to use:** Development, testing, or environments where configuration is already protected (e.g., Kubernetes Secrets, Vault-injected env vars).

**Risk:** Key material is visible in connector/UDF configuration, which is typically accessible via the Kafka Connect REST API or ksqlDB system tables. Avoid for production without additional access controls.

```json
[
  {
    "identifier": "my-key",
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
]
```

For Kafka Connect, use the [file config provider](modules/connect-smt.md#externalising-key-material) to store key material in an external properties file instead of exposing it in the connector JSON.

---

## `CONFIG_ENCRYPTED` — KEK-encrypted keysets in config

Keysets are stored encrypted at rest in the configuration. A cloud KMS Key Encryption Key (KEK) is used to decrypt them at runtime. The key material is never stored in plaintext anywhere.

**When to use:** You want to ship encrypted keysets in config/secrets but do not want to store keysets in a cloud secret manager.

**Requires:** `kek_type`, `kek_uri`, and `kek_config` in addition to `cipher_data_keys`.

The `material` field in each keyset entry takes the encrypted form:

```json
{
  "encryptedKeyset": "<ENCRYPTED_AND_BASE64_ENCODED_KEYSET_HERE>",
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
```

Generate encrypted keysets with the [Keyset Tool](keyset-tool.md#kek-encrypted-keysets).

---

## `KMS` — Plain keysets in cloud secret manager

Keysets are stored as secrets in a cloud secret manager. The module fetches them at runtime using the configured credentials.

**When to use:** Your organisation already manages secrets centrally via Azure Key Vault, AWS Secrets Manager, or GCP Secret Manager, and you want keysets to be versioned and rotated there.

**Requires:** `kms_type` and `kms_config`. The `cipher_data_keys` array can be left empty (`[]`).

### Secret naming conventions

Each cloud provider uses a naming prefix to distinguish plain and encrypted keysets:

=== "Azure Key Vault"
    The secret name is the keyset identifier directly (no prefix). Azure supports separate vault instances to isolate plain vs encrypted keysets.

=== "AWS Secrets Manager"
    | Type | Prefix |
    |---|---|
    | Plain keysets | `k4k/tink_plain/` |
    | Encrypted keysets | `k4k/tink_encrypted/` |

    Full secret name: `k4k/tink_plain/<identifier>`

=== "GCP Secret Manager"
    | Type | Prefix |
    |---|---|
    | Plain keysets | `k4k-tink-plain_` |
    | Encrypted keysets | `k4k-tink-encrypted_` |

    Full secret name: `k4k-tink-plain_<identifier>`

---

## `KMS_ENCRYPTED` — Encrypted keysets in cloud secret manager

The most secure mode. Keysets are stored encrypted in a cloud secret manager, and a separate cloud KMS key is required to decrypt them at runtime.

**When to use:** Production environments requiring defence in depth — an attacker who gains access to the secret manager secrets still cannot use the keysets without access to the KEK in the KMS.

**Requires:** `kms_type`, `kms_config`, `kek_type`, `kek_uri`, and `kek_config`.

---

## Tink Keyset Format

All keyset material, regardless of the key source, must conform to Tink's keyset JSON specification.

### Plain keyset (AES-GCM)

```json
{
  "primaryKeyId": 123456789,
  "key": [
    {
      "keyData": {
        "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value": "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 123456789,
      "outputPrefixType": "TINK"
    }
  ]
}
```

### Plain keyset (AES-GCM-SIV, deterministic)

```json
{
  "primaryKeyId": 123456789,
  "key": [
    {
      "keyData": {
        "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
        "value": "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 123456789,
      "outputPrefixType": "TINK"
    }
  ]
}
```

### Plain keyset (FPE FF3-1)

```json
{
  "primaryKeyId": 2000001,
  "key": [
    {
      "keyData": {
        "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
        "value": "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 2000001,
      "outputPrefixType": "RAW"
    }
  ]
}
```

Note the different `typeUrl` and `outputPrefixType: RAW` for FPE keysets.

---

## Key Rotation

Tink keysets natively support multiple keys. The `primaryKeyId` determines which key is used for new encryptions. Older keys remain in the keyset for decryption of existing ciphertexts.

To rotate a key:

1. Generate a new keyset with multiple keys (e.g., `-n 2` with the Keyset Tool)
2. The first key ID is the primary
3. Existing ciphertexts are decrypted using whichever key ID is encoded in their metadata
4. New encryptions use the primary key

See [Keyset Tool](keyset-tool.md) for examples of generating multi-key keysets.
