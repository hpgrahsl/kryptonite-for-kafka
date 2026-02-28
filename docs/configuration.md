# Configuration Reference

All Kryptonite for Kafka modules share the same set of core configuration parameters. The table below shows which parameters are available and supported by each module.

| Parameter | Required | Default | [Kafka Connect SMT](./modules/connect-smt.md) | [Flink UDFs](./modules/flink-udfs.md) | [ksqlDB UDFs](./modules/ksqldb-udfs.md) | [Quarkus HTTP Service](./modules/funqy-http.md) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| `key_source` | — | `CONFIG` | ✓ | ✓ | ✓ | ✓ |
| `cipher_data_keys` | ✓ | &nbsp; | ✓ | ✓ | ✓ | ✓ |
| `cipher_data_key_identifier` | ✓ | &nbsp; | ✓ | ✓ | ✓ | ✓ |
| `kms_type` | — | `NONE` | ✓ | ✓ | ✓ | ✓ |
| `kms_config` | — | `{}` | ✓ | ✓ | ✓ | ✓ |
| `kek_type` | — | `NONE` | ✓ | ✓ | ✓ | ✓ |
| `kek_config` | — | `{}` | ✓ | ✓ | ✓ | ✓ |
| `kek_uri` | — | &nbsp; | ✓ | ✓ | ✓ | ✓ |
| `cipher_algorithm` | — | `TINK/AES_GCM` | ✓ | ✓ | ✓ | ✓ |
| `field_mode` | - | `ELEMENT` | ✓ | — | — | ✓ |
| `cipher_mode` | ✓ | &nbsp; | ✓ | — | — | — |

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

**Default: `CONFIG`**

---

### `cipher_data_keys`

A JSON array of Tink keyset objects. Each entry has an `identifier` and a `material` field containing a Tink keyset specification.

!!! warning "`cipher_data_keys` is a required config parameter"

**Plain keyset example** (when `key_source=CONFIG`):

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

May be deliberately left empty `[]` when keysets are sourced from cloud secret managers (`key_source=KMS` or `key_source=KMS_ENCRYPTED`).

---

### `cipher_data_key_identifier`

The default keyset identifier used for encryption in case field settings do not specify their own key. Must match an `identifier` present in `cipher_data_keys` (or resolvable from the used cloud KMS).

!!! warning "`cipher_data_keys` is a required config parameter"
    Empty string is acceptable for decryption-only scenarios.

---

### `kms_type`

The cloud secret manager to use when `key_source=KMS` or `key_source=KMS_ENCRYPTED`.

| Value | Provider |
|---|---|
| `NONE` | No KMS as keysets are sourced from config |
| `AZ_KV_SECRETS` | Azure Key Vault Secrets |
| `AWS_SM_SECRETS` | AWS Secrets Manager |
| `GCP_SM_SECRETS` | GCP Secret Manager |

**Default: `NONE`**

---

### `kms_config`

JSON object with authentication settings specific to the chosen cloud provider

=== "Azure Key Vault"
    ```json
    {
      "clientId": "...",
      "tenantId": "...",
      "clientSecret": "...",
      "keyVaultUrl": "..."
    }
    ```

=== "AWS Secrets Manager"
    ```json
    {
      "accessKey": "...",
      "secretKey": "...",
      "region": "..."
    }
    ```

=== "GCP Secret Manager"
    ```json
    {
      "credentials": "<GCP service account JSON contents>",
      "projectId": "..."
    }
    ```

---

### `kek_type`

The KMS provider holding the Key Encryption Key (KEK) used to encrypt/decrypt keysets at rest. Required when `key_source=CONFIG_ENCRYPTED` or `key_source=KMS_ENCRYPTED`.

| Value | Provider |
|---|---|
| `NONE` | No KEK |
| `GCP` | GCP Cloud KMS |
| `AWS` | AWS KMS |
| `AZURE` | Azure Key Vault |

**Default: `NONE`**

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
      "accessKey": "...",
      "secretKey": "..."
    }
    ```

=== "Azure Key Vault"
    ```json
    {
      "clientId": "...",
      "tenantId": "...",
      "clientSecret": "...",
      "keyVaultUrl": "..."
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

The default cipher algorithm used for encryption in case field settings do not specify their own cipher algorithm.

| Value | Description |
|---|---|
| `TINK/AES_GCM` | probabilistic AEAD |
| `TINK/AES_GCM_SIV` | deterministic AEAD |
| `CUSTOM/MYSTO_FPE_FF3_1` | format-preserving encryption |

**Default: `TINK/AES_GCM`**

---

### `field_mode`

Controls how complex fields (`ARRAY`, `MAP`, `STRUCT`, and `ROW` types) are processed. 

!!! note 
    This setting is only available for the Apache Kafka Connect SMT and the Quarkus Funqy HTTP Service. However, the UFDs in the module integrations for Apache Flink and ksqlDB offer similar capabilities directly when applying them.

| Value | Description |
|---|---|
| `OBJECT` | The complex field is serialised in its entirety and encrypted as a single opaque blob which always results in a `VARCHAR` |
| `ELEMENT` | Each element of an array, value in a map, or field in a struct/row type is encrypted individually. The result preserves the container shape of the complex type and contains separate `VARCHAR`s for each encrypted element, value, or field. |

**Default: `ELEMENT`**

---

## FPE Settings

These settings apply if and only if you have configured a format-preserving encryption (FPE) cipher i.e.  `cipher_algorithm=CUSTOM/MYSTO_FPE_FF3_1`.

!!! warning
    **All configured FPE settings must be chosen identical for encryption and decryption operations**, otherwise you end up with unexpected and most likely incorrect results.

### `cipher_fpe_tweak`

A 7-bytes tweak value that adds cryptographic variation to FPE. Different tweaks produce different ciphertexts for the same plaintext input.

**Default: `0000000`**

### `cipher_fpe_alphabet_type`

The character set to which both plaintext and ciphertext characters are mapped.

| Value | Characters |
|---|---|
| `DIGITS` | `0123456789` |
| `UPPERCASE` | `A-Z` |
| `LOWERCASE` | `a-z` |
| `ALPHANUMERIC` | `0-9A-Za-z` |
| `ALPHANUMERIC_EXTENDED` | `0-9A-Za-z` plus common symbols |
| `HEXADECIMAL` | `0-9A-F` |
| `CUSTOM` | Defined by `cipher_fpe_alphabet_custom` |

**Default: `ALPHANUMERIC`**

### `cipher_fpe_alphabet_custom`

The explicit character set when `cipher_fpe_alphabet_type=CUSTOM`. Minimum 2 unique characters. Example: `01` for binary strings.

---

## Module Specific Parameters

### Kafka Connect SMT

#### `cipher_mode`

`ENCRYPT` or `DECRYPT`. Required. Determines the direction of the transformation.

#### `field_config`

JSON array listing the payload fields to process. Each entry specifies at minimum the field `name`. Optional per-field overrides for other settings influencing the encryption / decryption behaviour.

* Example

```json
[
  { "name": "ssn" },
  { "name": "creditCard", "algorithm": "CUSTOM/MYSTO_FPE_FF3_1", "fpeAlphabetType": "DIGITS" },
  { "name": "mySubDoc1.nestedField" }
]
```

For decryption of schema-aware records, include the `schema` field to allow the SMT to reconstruct the original type.

* Example:

```json
[
  { "name": "myArray1", "schema": { "type": "ARRAY", "valueSchema": { "type": "STRING" } } }
]
```

#### `path_delimiter`

Separator for nested field references in `field_config`. 

**Default: `.`**
