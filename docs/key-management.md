# Key Management

All Kryptonite for Kafka modules support four ways to source the required key material. You can define this by means of the `key_source` configuration parameter. The right choice primarly depends on your security posture and preferred operational model.

---

## Overview

```
key_source=CONFIG              → plain keysets inline in config
key_source=CONFIG_ENCRYPTED    → KEK-encrypted keysets inline in config
key_source=KMS                 → plain keysets in cloud secret manager
key_source=KMS_ENCRYPTED       → KEK-encrypted keysets in cloud secret manager
```

---

## Plain keysets in configuration

The simplest and least secure mode. Plain keysets are embedded directly in the `cipher_data_keys` configuration parameter.

!!! question "When to use?"
    Development & testing, or demos. Also, it could be a legitimate choice for environments where configuration is already protected by other means (e.g., Kubernetes Secrets, Vault-injected env vars).

!!! danger "Risk"
    Key material might become visible or accidentally exposed. For instance, this could happen in Kafka Connect environments where connector configurations might be accessible via the Kafka Connect REST API. Avoid this configuration for any serious production without additional access controls in place.

### Plain keyset example

```json
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
```

!!! tip "Tip"
    Generate plain keysets with the [Keyset Tool](./keyset-tool/#plain-keysets).

!!! tip "Tip"
    For Kafka Connect, it's recommended to use the [file config provider](https://kafka.apache.org/42/configuration/configuration-providers/#example-referencing-files). Find a concrete example to store keysets in an external properties file instead of exposing it directly in the connector's configuration [here](modules/connect-smt.md#externalising-key-material).

---

## Encrypted keysets in configuration 

Encrypted keysets give you moderate security while still being able to directly reference keys inside the configuration. A [cloud KMS](./kms/overview.md) key encryption key (KEK) is used to decrypt the keysets at runtime. The plain keysets are never stored anywhere.

!!! question "When to use?"
    You'd like to ship encrypted keysets as part of the configuration but do not want to store keysets externally in a cloud secret manager.

**Requires:** `key_source=CONFIG_ENCRYPTED`, depends on `kek_type`, `kek_uri`, and `kek_config` in addition to `cipher_data_keys`.

### Encrypted keyset example

Note, that the `material` field takes the encrypted form:

```json
  {
    "identifier": "my-key",
    "material": {
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
  }
```

!!! tip "Tip"
    Generate encrypted keysets with the [Keyset Tool](keyset-tool.md#kek-encrypted-keysets).

---

## Plain keysets in cloud secret manager

Plain keysets are stored as secrets in a cloud secret manager which provides reasonable key material protection. These are either fetched lazily on-demand or eagerly loaded during module initialization using the configured cloud provider credentials.

!!! question "When to use?"
    Your already manage other types of application secrets centrally via any supported cloud provider KMS (GCP Secret Manager, AWS Secrets Manager, Azure Key Vault) and you want keysets to be treated the same.

**Requires:** `key_source=KMS`, depends on `kms_type`, `kms_config`, `kek_type`, `kek_uri`, and `kek_config`. The `cipher_data_keys` array can be left empty (`[]`).

### Plain keyset example

```json
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
```

!!! tip "Tip"
    Generate plain keysets with the [Keyset Tool](./keyset-tool/#plain-keysets).

---

## Encrypted keysets in cloud secret manager

The most secure mode. Keysets are stored encrypted in a cloud secret manager, and a separate cloud KMS key encryption key is required to decrypt them at runtime.

!!! question "When to use?"
    **Production environments requiring maximum keyset protection.** An attacker who gains access to the secret manager secrets still cannot make use of any of the keysets without having access to the KEK in the KMS.

**Requires:** `key_source=KMS_ENCRYPTED`, depends on `kms_type`,`kms_config`, `kek_type`, `kek_uri`, and `kek_config`. The `cipher_data_keys` array can be left empty (`[]`).

### Encrypted keyset example

Note, that the `material` field takes the encrypted form:

```json
  {
    "identifier": "my-key",
    "material": {
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
  }
```

!!! tip "Tip"
    Generate encrypted keysets with the [Keyset Tool](keyset-tool.md#kek-encrypted-keysets).

---

## Secret naming conventions

Depending on which cloud secret manager is configured to store plain or encrypted keysets, the following secret name prefixes must be considered to distinguish plain from encrypted keysets:

=== "GCP Secret Manager"
     | Type | Mandatory Prefix | Key Identifier Example | Full Secret Name
    |---|---|---|---|
    | Plain keysets | `k4k-tink-plain_` | `key_123` | `k4k-tink-plain_key_123`
    | Encrypted keysets | `k4k-tink-encrypted_` | `key_XYZ` | `k4k-tink-encrypted_key_XYZ`

    **Full secret name: `(k4k-tink-plain | k4k-tink-encrypted)_<key_identifier>`**

=== "AWS Secrets Manager"
    | Type | Mandatory Prefix | Key Identifier Example | Full Secret Name
    |---|---|---|---|
    | Plain keysets | `k4k/tink_plain/` | `key_123` | `k4k/tink_plain/key_123`
    | Encrypted keysets | `k4k/tink_encrypted/` | `key_XYZ` | `k4k/tink_encrypted/key_XYZ`

    **Full secret name: `k4k/(tink_plain | tink_encrypted)/<key_identifier>`**

=== "Azure Key Vault"
    **The secret name is the keyset identifier as is.** No prefix is assumed. Azure supports separate vault instances to properly isolate plain vs. encrypted keysets.

    **Full secret name: `<key_identifier>`**

---

## Tink Keyset Format

All keyset material, regardless of the key source, must conform to Tink's plain or encrypted keyset specification.

### Plain keyset examples

* **AES-GCM (probabilistic encryption)**

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

* **AES-GCM-SIV (deterministic encryption)**

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

* **FPE FF3-1 (format-preserving encryption)**

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

Note the custom `typeUrl` and `outputPrefixType: RAW` for FPE keysets.

---

## Key Rotation

Tink keysets natively support multiple keys. The `primaryKeyId` determines which key is used for new encryptions. Older keys are supposed to remain in the keyset for decryption of existing ciphertexts.

1. Generate a new keyset with multiple keys (e.g., `-n 2` with the [Keyset Tool](./keyset-tool.md))
2. One of the keys in the keyset must be the primary key, initially it's the first one
3. Any new encryption operations use the currently designated primary key
4. A different key in the keyset can be promoted to become the primary key at any time
5. Once the primary key changed, old ciphertexts remain decryptable so long as the previously used primary key is still resolvable within the configured keyset.

See [Keyset Tool](keyset-tool.md) for examples of generating multi-key keysets.
