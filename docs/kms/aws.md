# AWS KMS

The `kryptonite-kms-aws` module adds two capabilities:

1. **Keyset Storage**: fetch Tink keysets from AWS Secrets Manager at runtime (`kms_type=AWS_SM_SECRETS`)
2. **Keyset Encryption**: use an AWS KMS symmetric key to encrypt/decrypt keysets at rest (`kek_type=AWS`)

Add the module JAR to the classpath alongside the core library. It is discovered automatically via `ServiceLoader`.

---

## Secret Storage with `kms_type=AWS_SM_SECRETS`

### Secret naming

Secrets are expected to be stored in AWS Secrets Manager with a mandatory prefix to differentiate between plain vs. encrypted keysets.

| Keyset type | Secret name prefix | Example |
|---|---|---|
| Plain keysets | `k4k/tink_plain/` | `k4k/tink_plain/my-aes-key` |
| Encrypted keysets | `k4k/tink_encrypted/` | `k4k/tink_encrypted/my-aes-key` |

Create secrets using the AWS CLI or Console. The secret value must be the Tink keyset JSON (in `RAW` format — no `identifier` wrapper):

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

AWS KMS symmetric keys (AES-256) support direct AEAD encryption via the KMS `Encrypt` / `Decrypt` API. No envelope encryption layer is needed.

### IAM permissions required

- `kms:Encrypt`
- `kms:Decrypt`
- `kms:GenerateDataKey`

Scope the policy to the specific KMS key ARN.

### Generate an encrypted keyset

:octicons-book-24: [Keyset Tool Details](../keyset-tool.md) 

```bash
java -jar kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-key -f FULL -p \
  -e --kek-type AWS \
  --kek-uri "aws-kms://arn:aws:kms:eu-central-1:123456789012:key/abcd-1234-efgh-5678" \
  --kek-config /path/to/aws-credentials.json
```

`aws-credentials.json`:

```json
{
  "accessKey": "AKIA...",
  "secretKey": "..."
}
```

### Configuration for `key_source=CONFIG_ENCRYPTED`

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

Store the encrypted keyset JSON in Secrets Manager under `k4k/tink_encrypted/<identifier>`, then configure:

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
