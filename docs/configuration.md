# Configuration Reference

All Kryptonite for Kafka modules share the same set of core configuration parameters. The table below shows which parameters are available and supported by each module.

<div class="k4k-param-table" markdown="1">

| Parameter | Required | Default | [Kafka Connect SMT](./modules/connect-smt.md) | [Flink UDFs](./modules/flink-udfs.md) | [ksqlDB UDFs](./modules/ksqldb-udfs.md) | [Quarkus HTTP Service](./modules/funqy-http.md) | [Kroxylicious Filter](./modules/kroxylicious-filter.md) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| [`key_source`](#key_source) | — | `CONFIG` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`cipher_data_keys`](#cipher_data_keys) | ✓ | &nbsp; | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`cipher_data_key_identifier`](#cipher_data_key_identifier) | ✓ | &nbsp; | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`kms_type`](#kms_type) | — | `NONE` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`kms_config`](#kms_config) | — | `{}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`kek_type`](#kek_type) | — | `NONE` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`kek_config`](#kek_config) | — | `{}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`kek_uri`](#kek_uri) | — | &nbsp; | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`cipher_algorithm`](#cipher_algorithm) | — | `TINK/AES_GCM` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`field_mode`](#field_mode) | - | `ELEMENT` | ✓ | — | — | ✓ | ✓ |
| [`cipher_mode`](#cipher_mode) | ✓ | &nbsp; | ✓ | — | — | — | — |
| [`envelope_kek_configs`](#envelope_kek_configs) | ✓ (envelope encryption) | `[]` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`envelope_kek_identifier`](#envelope_kek_identifier) | ✓ (envelope encryption) | &nbsp; | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`edek_store_config`](#edek_store_config) | ✓ (envelope encryption) | `{}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`dek_max_encryptions`](#dek_max_encryptions) | — | `100000` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`dek_ttl_minutes`](#dek_ttl_minutes) | — | `720` | ✓ | ✓ | ✓ | ✓ | ✓ |
| [`dek_key_bits`](#dek_key_bits) | — | `128` | ✓ | ✓ | ✓ | ✓ | ✓ |

</div>

---

## Core Parameters

### `key_source`

Defines the origin and protection of the key material.

<div class="k4k-param-table" markdown="1">

| Value | Description |
|---|---|
| `CONFIG` | Plain Tink keysets provided directly in `cipher_data_keys` |
| `CONFIG_ENCRYPTED` | Encrypted Tink keysets provided in `cipher_data_keys` for which the proper key encryption key (KEK) is required to be able to decrypt them |
| `KMS` | Plain Tink keysets stored in a cloud secret manager (requires `kms_type` and `kms_config` settings) |
| `KMS_ENCRYPTED` | Encrypted Tink keysets stored in a cloud secret manager (requires: all related KMS and KEK settings) |
| `NONE` | No Tink keysets involved. Use this exclusively with `TINK/AES_GCM_ENVELOPE_KMS` (requires `envelope_kek_configs` and `edek_store_config`) |

</div>

**Default: `CONFIG`**

---

### `cipher_data_keys`

A JSON array of Tink keyset objects. Each entry has an `identifier` and a `material` field containing a Tink keyset specification.

!!! warning "`cipher_data_keys` is a required config parameter"
    Also may be deliberately set to the empty array `[]` when working with `key_source=KMS`, `key_source=KMS_ENCRYPTED`, or `key_source=NONE`.

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

May be deliberately left empty `[]` when keysets are sourced from cloud secret managers (`key_source=KMS` or `key_source=KMS_ENCRYPTED`), or when exclusively working with [KMS-backed envelope encryption](./envelope-encryption.md#kms-based-envelope-encryption) (`key_source=NONE`).

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

<div class="k4k-param-table" markdown="1">

| Value | Description |
|---|---|
| `TINK/AES_GCM` | probabilistic AEAD |
| `TINK/AES_GCM_SIV` | deterministic AEAD |
| `CUSTOM/MYSTO_FPE_FF3_1` | format-preserving encryption |
| `TINK/AES_GCM_ENVELOPE_KEYSET` | envelope encryption — Tink keyset as KEK, wrapped DEK bundled inline |
| `TINK/AES_GCM_ENVELOPE_KMS` | envelope encryption — cloud KMS key as KEK, wrapped DEK in EdekStore |

</div>

**Default: `TINK/AES_GCM`**

!!! tip
    See [Envelope Encryption](envelope-encryption.md) for a full explanation of the two envelope variants, DEK session lifecycle, and required configuration.

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

## Envelope Encryption Parameters

These parameters apply when `cipher_algorithm` is set to `TINK/AES_GCM_ENVELOPE_KEYSET` or `TINK/AES_GCM_ENVELOPE_KMS`.

### `envelope_kek_configs`

JSON array of KEK entries for KMS-based envelope encryption (`TINK/AES_GCM_ENVELOPE_KMS`). Each entry specifies a KEK `identifier`, `type` (cloud provider), `uri`, and provider-specific `config` credentials. Not required for keyset-based envelope encryption.

```json
[
  {
    "identifier": "my-kek",
    "type": "GCP",
    "uri": "gcp-kms://projects/<project>/locations/<location>/keyRings/<ring>/cryptoKeys/<key>",
    "config": {
      "credentials": "<GCP service account JSON>",
      "projectId": "<project>"
    }
  }
]
```

See [Envelope Encryption / KEK configuration](envelope-encryption.md#kek-configuration-for-kms-based-envelope-encryption) for examples for all supported providers.

**Default: `[]` (disabled)**

---

### `envelope_kek_identifier`

The default KEK identifier when working with envelope encryption and field settings do not specify their own individual key. Must match an identifier present in `envelope_kek_configs`.

---

### `edek_store_config`

JSON object configuring the backing `EdekStore` implementation. It's required for KMS-based envelope encryption (`TINK/AES_GCM_ENVELOPE_KMS`). Currently, the default implementation is based on KCache/Kafka which persistently maps DEK fingerprints to wrapped DEKs. A minimum viable configuration is this:

```json
{
  "kafkacache.bootstrap.servers": "broker1:9092,...",
  "kafkacache.topic": "_k4k_edeks"
}
```

See [Envelope Encryption / EdekStore configuration](envelope-encryption.md#edekstore-configuration) for the full list of supported keys.

**Default: `{}` (disabled)**

---

### `dek_max_encryptions`

Maximum number of field encryptions before the current DEK session is rotated and a new DEK is generated. Applies to both envelope encryption variants (`TINK/AES_GCM_ENVELOPE_KEYSET`, `TINK/AES_GCM_ENVELOPE_KMS`).

**Default: `100000`**

---

### `dek_ttl_minutes`

Maximum age of a DEK session in minutes. The session is rotated when this threshold is reached, regardless of `dek_max_encryptions`. Applies to both envelope encryption variants (`TINK/AES_GCM_ENVELOPE_KEYSET`, `TINK/AES_GCM_ENVELOPE_KMS`).

**Default: `720` (12 hours)**

---

### `dek_key_bits`

Size of the generated DEK in bits. Accepted values: `128` or `256`. Applies to both envelope encryption variants (`TINK/AES_GCM_ENVELOPE_KEYSET`, `TINK/AES_GCM_ENVELOPE_KMS`).

**Default: `128`**

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
