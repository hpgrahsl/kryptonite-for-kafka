# AWS KMS

The `kryptonite-kms-aws` module adds three capabilities:

1. **Keyset Storage**: fetch Tink keysets from AWS Secrets Manager at runtime (`kms_type=AWS_SM_SECRETS`)
2. **Keyset Encryption**: use an AWS KMS symmetric key to encrypt/decrypt keysets at rest (`kek_type=AWS`)
3. **Envelope KEK**: use an AWS KMS key as the Key Encryption Key for envelope encryption (`cipher_algorithm=TINK/AES_GCM_ENVELOPE_KMS`)

Add the module JAR to the classpath alongside the core library. The runtime discovers it automatically via `ServiceLoader`.

---

## Secret Storage with `kms_type=AWS_SM_SECRETS`

### Secret naming

Secrets are expected to be stored in AWS Secrets Manager with a mandatory prefix to differentiate between plain vs. encrypted keysets.

| Keyset type | Secret name prefix | Example |
|---|---|---|
| Plain keysets | `k4k/tink_plain/` | `k4k/tink_plain/my-aes-key` |
| Encrypted keysets | `k4k/tink_encrypted/` | `k4k/tink_encrypted/my-aes-key` |

Create the actual cloud secrets using the AWS CLI or AWS Console. **Each secret's value must be the Tink keyset JSON in `RAW` format:**

```bash
aws secretsmanager create-secret \
  --name "k4k/tink_plain/my-aes-key" \
  --secret-string '{"primaryKeyId":10000,"key":[...]}'
```

### IAM permissions required

The credentials used in `kms_config` require:

- `secretsmanager:GetSecretValue`
- `secretsmanager:ListSecrets`

Scope the policy to the `k4k/tink_plain/*` and `k4k/tink_encrypted/*` prefixes.

### Configuration

```json
{
  "key_source": "KMS",
  "kms_type": "AWS_SM_SECRETS",
  "kms_config": "{\"accessKey\":\"AKIA...\",\"secretKey\":\"...\",\"region\":\"eu-central-1\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": "my-aes-key"
}
```

`kms_config` fields:

| Field | Description |
|---|---|
| `accessKey` | AWS access key ID |
| `secretKey` | AWS secret access key |
| `region` | AWS region where the secrets are stored |

---

## Keyset Encryption with `kek_type=AWS`

AWS KMS symmetric keys (AES-256) support direct AEAD encryption. The module integrates via Tink's official `tink-java-awskms` library.

### IAM permissions required

- `kms:Encrypt`
- `kms:Decrypt`
- `kms:GenerateDataKey`

Scope the policy to the specific KMS key ARN.

### Configuration for `key_source=CONFIG_ENCRYPTED`

Generate the **AWS KEK encrypted keyset(s) in `FULL` format** using the [keyset tool](../keyset-tool.md), then configure:

```json
{
  "key_source": "CONFIG_ENCRYPTED",
  "cipher_data_keys": "[{\"identifier\":\"my-key\",\"material\":{\"encryptedKeyset\":\"...\",\"keysetInfo\":{...}}}]",
  "cipher_data_key_identifier": "my-key",
  "kek_type": "AWS",
  "kek_uri": "aws-kms://arn:aws:kms:eu-central-1:123456789012:key/abcd-1234-efgh-5678",
  "kek_config": "{\"accessKey\":\"AKIA...\",\"secretKey\":\"...\"}"
}
```

### Configuration for `key_source=KMS_ENCRYPTED`

Generate the **AWS KEK encrypted keyset(s) in `RAW` format** using the [keyset tool](../keyset-tool.md) and store it in Secrets Manager under `k4k/tink_encrypted/<identifier>`.

Then configure:

```json
{
  "key_source": "KMS_ENCRYPTED",
  "kms_type": "AWS_SM_SECRETS",
  "kms_config": "{\"accessKey\":\"AKIA...\",\"secretKey\":\"...\",\"region\":\"eu-central-1\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": "my-key",
  "kek_type": "AWS",
  "kek_uri": "aws-kms://arn:aws:kms:eu-central-1:123456789012:key/abcd-1234-efgh-5678",
  "kek_config": "{\"accessKey\":\"AKIA...\",\"secretKey\":\"...\"}"
}
```

---

## Envelope KEK with `cipher_algorithm=TINK/AES_GCM_ENVELOPE_KMS`

AWS KMS keys can act as the KEK for envelope encryption. In this mode a fresh DEK is generated per session, all field encryptions use the DEK, and AWS KMS wraps/unwraps the DEK on session boundaries. The raw KEK material never leaves AWS KMS.

See [Envelope Encryption](../envelope-encryption.md) for a full explanation of the encrypt/decrypt paths, DEK session lifecycle, and `EdekStore` configuration.

### IAM permissions required

The credentials require the same permissions as for keyset encryption:

- `kms:Encrypt`
- `kms:Decrypt`
- `kms:GenerateDataKey`

Scope the policy to the specific KMS key ARN.

### Configuration

```json
{
  "cipher_algorithm": "TINK/AES_GCM_ENVELOPE_KMS",
  "envelope_kek_identifier": "my-aws-kek",
  "envelope_kek_configs": "[{\"identifier\":\"my-aws-kek\",\"type\":\"AWS\",\"uri\":\"aws-kms://arn:aws:kms:eu-central-1:123456789012:key/abcd-1234-efgh-5678\",\"config\":{\"accessKey\":\"AKIA...\",\"secretKey\":\"...\",\"region\":\"eu-central-1\"}}]",
  "edek_store_config": "{\"kafkacache.bootstrap.servers\":\"broker1:9092\",\"kafkacache.topic\":\"_k4k_edeks\"}",
  "cipher_data_keys": "[]",
  "cipher_data_key_identifier": ""
}
```

`envelope_kek_configs` entry fields:

| Field | Description |
|---|---|
| `identifier` | Logical name referenced by `envelope_kek_identifier` |
| `type` | Must be `AWS` |
| `uri` | Full AWS KMS key ARN URI (`aws-kms://arn:aws:kms:...`) |
| `config.accessKey` | AWS access key ID |
| `config.secretKey` | AWS secret access key |
| `config.region` | AWS region where the KMS key is located |
