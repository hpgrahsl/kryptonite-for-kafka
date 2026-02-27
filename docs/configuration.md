# Configuration Reference

All Kryptonite for Kafka modules share the same set of configuration parameters using a consistent underscore-based naming convention. The table below shows which parameters are supported by each module.

| Parameter | Kafka Connect SMT | ksqlDB UDFs | Flink UDFs | Quarkus HTTP API |
|:---|---|---|---|---|
| `key_source` | ✓ | ✓ | ✓ | ✓ |
| `cipher_data_keys` | ✓ | ✓ | ✓ | ✓ |
| `cipher_data_key_identifier` | ✓ | ✓ | ✓ | ✓ |
| `kms_type` | ✓ | ✓ | ✓ | ✓ |
| `kms_config` | ✓ | ✓ | ✓ | ✓ |
| `kek_type` | ✓ | ✓ | ✓ | ✓ |
| `kek_config` | ✓ | ✓ | ✓ | ✓ |
| `kek_uri` | ✓ | ✓ | ✓ | ✓ |
| `cipher_algorithm` | ✓ | ✓ | ✓ | ✓ |
| `field_mode` | ✓ | — | — | ✓ |

---

## Core Parameters

### `key_source`

Defines the origin and protection of the key material.

| Value | Description |
|---|---|
| `CONFIG` | Plain Tink keysets provided directly in `cipher_data_keys` |
| `CONFIG_ENCRYPTED` | Encrypted Tink keysets provided in `cipher_data_keys` for which the proper key encryption key (KEK) is required to be able to decrypt them |
| `KMS` | Plain Tink keysets stored in a cloud secret manager (requires `kms_type` and `kms_config` settings) |
| `KMS_ENCRYPTED` | Encrypted Tink keysets stored in a cloud secret manager (requires: all related KMS and KEK settings) |

Default: `CONFIG`

---

### `cipher_data_keys`

A JSON array of Tink keyset objects. Each entry has an `identifier` and a `material` field containing a Tink keyset specification.

**Plain keyset example:**

```json
[
  {
    "identifier": "my-demo-key",
    "material": {
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
  }
]
```

**Encrypted keyset example** (when `key_source=CONFIG_ENCRYPTED`):

```json
[
  {
    "identifier": "my-demo-key",
    "material": {
      "encryptedKeyset": "<ENCRYPTED_AND_BASE64_ENCODED_KEYSET_HERE>",
      "keysetInfo": {
        "primaryKeyId": 123456789,
        "keyInfo": [
          {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "status": "ENABLED",
            "keyId": 123456789,
            "outputPrefixType": "TINK"
          }
        ]
      }
    }
  }
]
```

May be left empty (`[]`) when `key_source=KMS` or `key_source=KMS_ENCRYPTED`.

---

### `cipher_data_key_identifier`

The default keyset identifier used for encryption in case field settings do not specify their a certain key. Must match an `identifier` present in `cipher_data_keys` (or resolvable from the used cloud KMS).

Required for encryption. Empty string is acceptable for decryption-only configurations.

---

### `kms_type`

The cloud secret manager to use when `key_source=KMS` or `key_source=KMS_ENCRYPTED`.

| Value | Provider |
|---|---|
| `NONE` | No KMS as keysets are sourced from config (default) |
| `AZ_KV_SECRETS` | Azure Key Vault Secrets |
| `AWS_SM_SECRETS` | AWS Secrets Manager |
| `GCP_SM_SECRETS` | GCP Secret Manager |

---

### `kms_config`

JSON object with cloud-provider-specific authentication settings.

=== "Azure Key Vault"
    ```json
    {
      "clientId": "...",
      "tenantId": "...",
      "clientSecret": "...",
      "keyVaultUrl": "https://<vault-name>.vault.azure.net"
    }
    ```

=== "AWS Secrets Manager"
    ```json
    {
      "accessKey": "AKIA...",
      "secretKey": "...",
      "region": "eu-central-1"
    }
    ```

=== "GCP Secret Manager"
    ```json
    {
      "credentials": "<GCP service account JSON contents>",
      "projectId": "my-gcp-project"
    }
    ```

---

### `kek_type`

The KMS provider holding the Key Encryption Key (KEK) used to encrypt/decrypt keysets at rest. Required when `key_source=CONFIG_ENCRYPTED` or `key_source=KMS_ENCRYPTED`.

| Value | Provider |
|---|---|
| `NONE` | No KEK (default) |
| `GCP` | GCP Cloud KMS |
| `AWS` | AWS KMS |
| `AZURE` | Azure Key Vault |

---

### `kek_config`

JSON object with credentials for the KEK provider.

=== "GCP Cloud KMS"
    ```json
    {
      "credentials": "<GCP service account JSON contents>",
      "projectId": "my-gcp-project"
    }
    ```

=== "AWS KMS"
    ```json
    {
      "accessKey": "AKIA...",
      "secretKey": "..."
    }
    ```

=== "Azure Key Vault"
    ```json
    {
      "clientId": "...",
      "tenantId": "...",
      "clientSecret": "...",
      "keyVaultUrl": "https://<vault-name>.vault.azure.net"
    }
    ```

---

### `kek_uri`

URI referencing the Key Encryption Key in the chosen cloud KMS.

| Provider | URI format |
|---|---|
| GCP | `gcp-kms://projects/<project>/locations/<location>/keyRings/<ring>/cryptoKeys/<key>` |
| AWS | `aws-kms://arn:aws:kms:<region>:<account>:key/<key-id>` |
| Azure | `azure-kv://<vault-name>.vault.azure.net/keys/<key-name>` |

---

## Encryption Parameters

### `cipher_algorithm`

Default cipher algorithm for fields that do not specify their own algorithm.

| Value | Description |
|---|---|
| `TINK/AES_GCM` | Probabilistic AEAD (default) |
| `TINK/AES_GCM_SIV` | Deterministic AEAD — use for Kafka record keys |
| `CUSTOM/MYSTO_FPE_FF3_1` | Format-preserving encryption |

---

### `field_mode`

Controls how complex fields (arrays, maps, structs) are processed.

| Value | Description |
|---|---|
| `OBJECT` | The entire field is serialised and encrypted as a single opaque blob → result is always `VARCHAR` |
| `ELEMENT` | Each element of an array or map value is encrypted individually → result type preserves the container shape |

Default: `ELEMENT`

---

## FPE Parameters

These parameters apply when `cipher_algorithm=CUSTOM/MYSTO_FPE_FF3_1`.

### `cipher_fpe_tweak`

A tweak value that adds cryptographic variation to FPE. Different tweaks produce different ciphertexts for the same plaintext. Must be identical between encryption and decryption.

Default: `0000000`

### `cipher_fpe_alphabet_type`

The character set from which both plaintext and ciphertext characters are drawn.

| Value | Characters |
|---|---|
| `DIGITS` | `0123456789` |
| `UPPERCASE` | `A-Z` |
| `LOWERCASE` | `a-z` |
| `ALPHANUMERIC` | `0-9A-Za-z` |
| `ALPHANUMERIC_EXTENDED` | `0-9A-Za-z` plus common symbols |
| `HEXADECIMAL` | `0-9A-F` |
| `CUSTOM` | Defined by `cipher_fpe_alphabet_custom` |

Default: `ALPHANUMERIC`

### `cipher_fpe_alphabet_custom`

The explicit character set when `cipher_fpe_alphabet_type=CUSTOM`. Minimum 2 unique characters. Example: `01` for binary strings.

---

## Kafka Connect — Additional Parameters

### `cipher_mode`

`ENCRYPT` or `DECRYPT`. Required. Determines the direction of the transformation.

### `field_config`

JSON array listing the fields to process. Each entry specifies at minimum a `name`. Optional per-field overrides:

```json
[
  { "name": "ssn" },
  { "name": "creditCard", "algorithm": "CUSTOM/MYSTO_FPE_FF3_1", "fpeAlphabetType": "DIGITS" },
  { "name": "mySubDoc1.nestedField" }
]
```

For decryption of schema-aware records, include the `schema` field to allow the SMT to reconstruct the original type:

```json
[
  { "name": "myArray1", "schema": { "type": "ARRAY", "valueSchema": { "type": "STRING" } } }
]
```

### `path_delimiter`

Separator for nested field references in `field_config`. Default: `.`

### `cipher_text_encoding`

Encoding of the resulting ciphertext bytes.

| Value | Description |
|---|---|
| `BASE64` | Base64-encoded string (default) |
| `RAW_BYTES` | Raw byte array |
