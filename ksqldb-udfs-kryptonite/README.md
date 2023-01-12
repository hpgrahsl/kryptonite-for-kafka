# Kryptonite for Kafka: Client-Side 🔒 Field-Level 🔓 Cryptography for Apache Kafka®

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

## ksqlDB User-Defined Functions (UDFs)

Kryptonite for Kafka provides two [ksqlDB](https://ksqlDB.io) [user-defined functions](https://docs.ksqldb.io/en/latest/reference/user-defined-functions/) (UDFs) named `K4KENCRYPT` and `K4KDECRYPT`. The simple examples below show how to install, configure and apply the UDFs to selectively encrypt or decrypt column values in ksqlDB `STREAMS` and `TABLES`.

### Build and Deployment

Either you build this project from sources via Maven or you can download pre-built, self-contained packages of the latest artefacts. Starting with Kryptonite for Kafka 0.4.0, the pre-built ksqlDB UDFs can be downloaded directly from the [release pages](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).

In order to deploy the UDFs **put the jar into your _'ksql extension directory'_** that is configured to be scanned during bootstrap of the ksqlDB server process(es).

Verify a successful deployment by checking all available functions from within the ksqlDB CLI, which somewhere along the lines should output both Kryptonite for Kafka related user-defined functions like so:

```
ksql> SHOW FUNCTIONS;

Function Name         | Category
--------------------------------------------
...                   | ...                              
K4KDECRYPT            | cryptography       
K4KENCRYPT            | cryptography
...                   | ...          
--------------------------------------------
```

### Configuration

The following table lists configuration options for the UDFs.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Description</th>
            <th>Type</th>
            <th>Default</th>
            <th>Valid Values</th>
            <th>?</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>cipher.data.keys</td>
            <td>JSON array with plain or encrypted data key objects specifying the key identifiers together with key
                sets for encryption / decryption which are defined in Tink's key specification format. The contained
                keyset objects are mandatory if
                <code>kms_type=NONE</code> but the array may be left empty for e.g. <code>kms_type=AZ_KV_SECRETS</code> in order to resolve keysets from a remote KMS such as Azure Key Vault.
                <strong>NOTE: Irrespective of their origin, all plain or encrypted keysets
                    (see the example values in the right column) are expected to be valid tink keyset descriptions in
                    JSON format.</strong>
            </td>
            <td>JSON array</td>
            <td>
                <pre>[]</pre>
            </td>
            <td>JSON array either empty or holding N data key config objects each of which refers to a tink keyset in JSON format (see "material" field)
                <ul>
                <li>plain data key config example:</li>
    <pre>
[
  {
    "identifier": "my-demo-secret-key-123",
    "material": {
      "primaryKeyId": 123456789,
      "key": [
        {
          "keyData": {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
            "value": "&lt;BASE64_ENCODED_KEY_HERE&gt;",
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
    </pre>
    <li>encrypted data key config example:</li>
    <pre>
[
  {
    "identifier": "my-demo-secret-key-123",
    "material": {
      "encryptedKeyset": "&lt;ENCRYPTED_AND_BASE64_ENCODED_KEYSET_HERE&gt;",
      "keysetInfo": {
        "primaryKeyId": 123456789,
        "keyInfo": [
          {
            "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey",
            "status": "ENABLED",
            "keyId": 123456789,
            "outputPrefixType": "TINK"
          }
        ]
      }
    }
  }
]
    </pre>
    </ul>
    </td>
    <td><strong>mandatory</strong> for both,
                <code>K4KENCRYPT</code> and
                <code>K4KDECRYPT</code>
            </td>
        </tr>
        <tr>
            <td>cipher.data.key.identifier</td>
            <td>keyset identifier to be used as default data encryption keyset for all UDF calls which don't refer to a
                specific keyset identifier in the parameter list</td>
            <td>string</td>
            <td><pre>!no default!</pre></td>
            <td>non-empty string referring to an existing identifier for a keyset</td>
            <td><strong>mandatory</strong> for <code>K4KENCRYPT</code>
            </td>
        <tr>
            <td>key.source</td>
            <td>defines the nature and origin of the keysets:
            <ul>
                <li>plain data keysets in <code>cipher_data_keys (key_source=CONFIG)</code></li>
                <li>encrypted data keysets in <code>cipher_data_keys (key_source=CONFIG_ENCRYPTED)</code></li>
                <li>plain data keysets residing in a cloud/remote key management system <code>(key_source=KMS)</code></li>
                <li>encrypted data keysets residing in a cloud/remote key management system <code>(key_source=KMS_ENCRYPTED)</code></li>
            </ul>
                When using the KMS options refer to the <code>kms_type</code> and <code>kms_config</code> settings. When using encrypted data
                keysets refer to the <code>kek_type</code>, <code>kek_config</code> and <code>kek_uri</code> settings as well.
            </td>
            <td>string</td>
            <td>
                <pre>CONFIG</pre>
            </td>
            <td>
                <pre>CONFIG</pre>
                <pre>CONFIG_ENCRYPTED</pre>
                <pre>KMS</pre>
                <pre>KMS_ENCRYPTED</pre>
            </td>
            <td><strong>optional</strong> for both,
                <code>K4KENCRYPT</code> and
                <code>K4KDECRYPT</code>
            </td>
        </tr>
        <tr>
            <td>kms.type</td>
            <td>defines if:
                <ul>
                <li>data keysets are read from the config directly <code>kms_source=CONFIG | CONFIG_ENCRYPTED</code></li>
                <li>data keysets are resolved from a remote/cloud key management system (currently only supports Azure Key Vault) <code>kms_source=KMS | KMS_ENCRYPTED</code>
                </li>
                </ul>
            </td>
            <td>string</td>
            <td>
                <pre>NONE</pre>
            </td>
            <td>
                <pre>NONE</pre>
                <pre>AZ_KV_SECRETS</pre>
            </td>
            <td><strong>optional</strong> for both,
                <code>K4KENCRYPT</code> and
                <code>K4KDECRYPT</code>
            </td>
        </tr>
        <tr>
            <td>kms.config</td>
            <td>JSON object specifying KMS-specific client authentication settings. Currently only supports Azure Key Vault <code>kms_type=AZ_KV_SECRETS</code></td>
            <td>JSON object</td>
            <td><pre>{}</pre></td>
            <td>JSON object defining the KMS-specific client authentication settings, e.g. for Azure Key Vault:
                <pre>
    {
        "clientId": "...",
        "tenantId": "...",
        "clientSecret": "...",
        "keyVaultUrl": "..."
    }
    </pre>
            </td>
            <td><strong>optional</strong> for both,
                <code>K4KENCRYPT</code> and
                <code>K4KDECRYPT</code>
            </td>
        </tr>
        <tr>
            <td>kek.type</td>
            <td>defines if KMS key encryption - currently only supports Google Cloud KMS - is used for encrypting data keysets and must be specified when using <code>kms_source=CONFIG_ENCRYPTED | KMS_ENCRYPTED</code> 
            </td>
            <td>string</td>
            <td>
                <pre>NONE</pre>
            </td>
            <td>
                <pre>NONE</pre>
                <pre>GCP</pre>
            </td>
            <td><strong>optional</strong> for both,
                <code>K4KENCRYPT</code> and
                <code>K4KDECRYPT</code>
            </td>
        </tr>
        <tr>
            <td>kek.config</td>
            <td>JSON object specifying KMS-specific client authentication settings (currently only supports Google Cloud KMS) <code>kek_type=GCP</code></td>
            <td>JSON object</td>
            <td><pre>{}</pre></td>
            <td>JSON object specifying the KMS-specific client authentication settings, e.g. for Google Cloud KMS:
<pre>
{
  "type": "service_account",
  "project_id": "...",
  "private_key_id": "...",
  "private_key": "-----BEGIN PRIVATE KEY----- ... -----END PRIVATE KEY-----\n",
  "client_email": "...",
  "client_id": "...",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "..."
}
</pre>
            </td>
            <td><strong>optional</strong> for both,
                <code>K4KENCRYPT</code> and
                <code>K4KDECRYPT</code>
            </td>
        </tr>
        <tr>
            <td>kek.uri</td>
            <td>URI referring to the key encryption key stored in the respective remote/cloud KMS, currently only support Google Cloud KMS</td>
            <td>string</td>
            <td><pre>!no default!</pre></td>
            <td>a valid and supported Tink key encryption key URI, e.g. pointing to a key in Google Cloud KMS (<code>kek_type=GCP</code>)
            <pre>gcp-kms://...</pre>
            </td>
            <td><strong>optional</strong> for both,
                <code>K4KENCRYPT</code> and
                <code>K4KDECRYPT</code>
            </td>
        </tr>
    </tbody>
</table>

##### UDF K4KENCRYPT

Below are examples how to specify the mandatory configuration settings for the `K4KENCRYPT` UDF:

1. directly within the `ksql-server.properties` for running ksqlDB natively / on bare metal

```properties
ksql.functions.k4kencrypt.cipher.data.keys=[ { "identifier": "my-demo-secret-key-123", "material": { "primaryKeyId": 1234567890, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_ENCODED_KEY_HERE>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 1234567890, "outputPrefixType": "TINK" } ] } } ]
ksql.functions.k4kencrypt.cipher.data.key.identifier=my-demo-secret-key-123
```

2. in the `docker-compose.yml` file for container-based deployments of ksqlDB

```yaml
KSQL_KSQL_FUNCTIONS_K4KENCRYPT_CIPHER_DATA_KEYS: "[ { \"identifier\": \"my-demo-secret-key-123\", \"material\": { \"primaryKeyId\": 1234567890, \"key\": [ { \"keyData\": { \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\", \"value\": \"<BASE64_ENCODED_KEY_HERE>\", \"keyMaterialType\": \"SYMMETRIC\" }, \"status\": \"ENABLED\", \"keyId\": 1234567890, \"outputPrefixType\": \"TINK\" } ] } } ]"
KSQL_KSQL_FUNCTIONS_K4KENCRYPT_CIPHER_DATA_KEY_IDENTIFIER: "my-demo-secret-key-123"
```

##### UDF K4KDECRYPT

Below are examples how to specify the mandatory configuration settings for the `K4KDECRYPT` UDF:

1. directly within the `ksql-server.properties` for running ksqlDB natively / on bare metal

```properties
ksql.functions.k4kdecrypt.cipher.data.keys=[ { "identifier": "my-demo-secret-key-123", "material": { "primaryKeyId": 1234567890, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_ENCODED_KEY_HERE>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 1234567890, "outputPrefixType": "TINK" } ] } } ]
```

2. in the `docker-compose.yml` file for container-based deployments of ksqlDB

```yaml
KSQL_KSQL_FUNCTIONS_K4KDECRYPT_CIPHER_DATA_KEYS: "[ { \"identifier\": \"my-demo-secret-key-123\", \"material\": { \"primaryKeyId\": 1234567890, \"key\": [ { \"keyData\": { \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\", \"value\": \"<BASE64_ENCODED_KEY_HERE>\", \"keyMaterialType\": \"SYMMETRIC\" }, \"status\": \"ENABLED\", \"keyId\": 1234567890, \"outputPrefixType\": \"TINK\" } ] } } ]"
```

After making sure that all the mandatory configuration properties are set, start using `K4KENCRYPT` and `K4KDECRYPT` to encrypt and decrypt column values in ksqlDB rows.

### Usage Description

##### UDF K4KENCRYPT

```text
Name        : K4KENCRYPT
Author      : H.P. Grahsl (@hpgrahsl)
Version     : 0.2.0
Overview    : 🔒 encrypt field data ... hopefully without fighting 🐲 🐉
Type        : SCALAR
Jar         : <EXTENSION_DIR>/ksqldb-udfs-kryptonite-0.2.0.jar
Variations  : 

	Variation   : K4KENCRYPT(data T, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR)
	Returns     : VARCHAR
	Description : 🔒 encrypt primitive or complex field data in object mode using the specified key identifier and cipher algorithm
	data        : the data to encrypt
	keyIdentifier: the key identifier to use for encryption
	cipherAlgorithm: the cipher algorithm to use for encryption

	Variation   : K4KENCRYPT(data T)
	Returns     : VARCHAR
	Description : 🔒 encrypt primitive or complex field data in object mode using the configured defaults for key identifier and cipher algorithm
	data        : the data to encrypt

	Variation   : K4KENCRYPT(data U, typeCapture V)
	Returns     : V
	Description : 🔒 encrypt complex field data either in object mode or element mode using the configured defaults for key identifier and cipher algorithm
	data        : the data to encrypt
	typeCapture : param for target type inference (use STRING for object mode encryption, use MAP | ARRAY | STRUCT for element mode encryption)

	Variation   : K4KENCRYPT(data U, typeCapture V, keyIdentifier VARCHAR, cipherAlgorithm VARCHAR)
	Returns     : V
	Description : 🔒 encrypt complex field data either in object mode or element mode using the specified key identifier and cipher algorithm
	data        : the data to encrypt
	typeCapture : param for target type inference (use STRING for object mode encryption, use MAP | ARRAY | STRUCT for element mode encryption)
	keyIdentifier: the key identifier to use for encryption
	cipherAlgorithm: the cipher algorithm to use for encryption
```

##### UDF K4KDECRYPT

```text
Name        : K4KDECRYPT
Author      : H.P. Grahsl (@hpgrahsl)
Version     : 0.2.0
Overview    : 🔓 decrypt field data ... hopefully without fighting 🐲 🐉
Type        : SCALAR
Jar         : <EXTENSION_DIR>/ksqldb-udfs-kryptonite-0.2.0.jar
Variations  : 

	Variation   : K4KDECRYPT(data ARRAY<VARCHAR>, typeCapture E)
	Returns     : ARRAY<E>
	Description : 🔓 decrypt array elements (element mode)
	data        : the encrypted array elements (given as base64 encoded ciphertext) to decrypt
	typeCapture : param for elements' target type inference

	Variation   : K4KDECRYPT(data MAP<K, VARCHAR>, typeCapture V)
	Returns     : MAP<K, V>
	Description : 🔓 decrypt map values (element mode)
	data        : the encrypted map entries (values given as base64 encoded ciphertext) to decrypt
	typeCapture : param for values' target type inference

	Variation   : K4KDECRYPT(data VARCHAR, typeCapture T)
	Returns     : T
	Description : 🔓 decrypt the field data (object mode)
	data        : the encrypted data (base64 encoded ciphertext) to decrypt
	typeCapture : param for target type inference
```

### Applying the UDFs 

The following fictional data records - represented in JSON-encoded format - are used to illustrate a simple encrypt/decrypt scenario:

```json5
[
    {
      "id": "1234567890",
      "myString": "some foo text",
      "myInt": 42,
      "myBoolean": true,
      "mySubDoc1": {"someString":"As I was going to St. Ives","someInt":1234},
      "myArray1": ["str_1","str_2","str_3"],
      "mySubDoc2": {"k1":9,"k2":8,"k3":7}
    }
    ,
    {
      "id": "9876543210",
      "myString": "some bla text",
      "myInt": 23,
      "myBoolean": false,
      "mySubDoc1": {"someString":"I met a man with seven wives","someInt":9876},
      "myArray1": ['str_A','str_B','str_C'],
      "mySubDoc2": {"k1":6,"k2":5,"k3":4}
    }
]
```

Representing data records such as this in plaintext (i.e. in unencrypted form) the following STREAM could be created in ksqlDB:

```sql
-- 'data stream with all plaintext columns'
CREATE STREAM MY_SAMPLE_DATA_JSON (
id VARCHAR,
mystring VARCHAR,
myint INT,
myboolean BOOLEAN,
mysubdoc1 STRUCT<somestring VARCHAR,someint INT>,
myarray ARRAY<VARCHAR>,
mysubdoc2 MAP<VARCHAR,INT>
) WITH (
KAFKA_TOPIC = 'my_sample_data_json',
VALUE_FORMAT = 'JSON',
PARTITIONS = 1
);
```

**However, in order to store selected fields as ciphertext in the first place (i.e. on insertion), redacted column data type definitions are needed.** Encrypted values are represented as BASE64-encoded strings which means that the target data type for encrypted columns must be defined as follows:

* **VARCHAR** for primitive ksqlDB data types or for complex ksqlDB data types if encrypted as a whole (_object mode_)
* **ARRAY&lt;VARCHAR&gt; | MAP&lt;VARCHAR,VARCHAR&gt; | STRUCT&lt; ... VARCHAR,... &gt;** in case complex ksqlDB data types (ARRAY,MAP,STRUCT) are encrypted element-wise (_element mode_) the types for ARRAY elements, MAP values and STRUCT fields become VARCHAR

##### Object Mode Encryption Example

In case all fields of the data record above should get encrypted in object mode on insertion, all target data types become VARCHAR and the following ksqlDB STREAM can be defined:

```sql
-- 'data stream with all ciphertext columns encrypted in object mode'
CREATE STREAM MY_SAMPLE_DATA_JSON_ENC_O (
id VARCHAR,
mystring VARCHAR,
myint VARCHAR,
myboolean VARCHAR,
mysubdoc1 VARCHAR,
myarray VARCHAR,
mysubdoc2 VARCHAR
) WITH (
KAFKA_TOPIC = 'my_sample_data_json_enc_o',
VALUE_FORMAT = 'JSON',
PARTITIONS = 1
);
```

Applying the `K4KENCRYPT` UDF in the following two `INSERT` statements shows how to make sure that all column values are encrypted in object mode on-the-fly with the default settings for key identifier and cipher algorithm as defined in the configuration of the UDF. Doing so makes sure that the values are encrypted on the client-side (i.e. the ksqlDB processing nodes) before the data hits the Kafka brokers.

```sql
INSERT INTO MY_SAMPLE_DATA_JSON_ENC_O (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4KENCRYPT('1234567890'),
    K4KENCRYPT('some foo text'),
    K4KENCRYPT(42),
    K4KENCRYPT(true),
    K4KENCRYPT(STRUCT(somestring:='As I was going to St. Ives',someint:=1234)),
    K4KENCRYPT(array['str_1','str_2','str_3']),
    K4KENCRYPT(map('k1':=9,'k2':=8,'k3':=7))
);

INSERT INTO MY_SAMPLE_DATA_JSON_ENC_O (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4KENCRYPT('9876543210'),
    K4KENCRYPT('some bla text'),
    K4KENCRYPT(23),
    K4KENCRYPT(false),
    K4KENCRYPT(STRUCT(somestring:='I met a man with seven wives',someint:=9876)),
    K4KENCRYPT(array['str_A','str_B','str_C']),
    K4KENCRYPT(map('k1':=6,'k2':=5,'k3':=4))
);

```

Inspecting the contents of this stream after the insertion with a simple `SELECT` query shows two rows with their encrypted values as BASE64-encoded strings for each of the columns. 

```sql
SELECT * FROM MY_SAMPLE_DATA_JSON_ENC_O EMIT CHANGES LIMIT 2;
```

```text
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|ID                  |MYSTRING            |MYINT               |MYBOOLEAN           |MYSUBDOC1           |MYARRAY             |MYSUBDOC2           |
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|LQE7msoB0ctE6pSB6+Jo|MAE7msoBJBjwD/CCJEDd|JAE7msoBgqjBM8CFeTCa|JAE7msoBjNcJBmM0b2TW|dgE7msoBzMT8Bm+XzoBb|NQE7msoBRIEE1iYgoqAu|NQE7msoBJgahKzOKsNRl|
|xE6grFGeE6/47W9329at|fFagu44PkI43QbxX6dPc|bRcRKqdM04TEaiIn9yn3|xcBgQOWQyy3EjhM1NYBn|FjHdSh+eoraSLQiWlxKg|sB/WFFoumU0z3kSu9Sh/|XivhYvprZEpz84POSO3f|
|OBiPNIAPwQb7d+TLk6zs|QKY3a6si9a8BIVM+xFEX|LUofMikoDDCybXktZGVt|+HziRpLFDDCybXktZGVt|UoJF+rftQq7xbOShH0X6|QTZ9zKd5UD9gWve7/4dB|XKf2xa3fG8XVUYSyQaYu|
|DDCybXktZGVtby1zZWNy|WvxDDDCybXktZGVtby1z|by1zZWNyZXQta2V5LTEy|by1zZWNyZXQta2V5LTEy|+NDVW228FMBgnQTMuVeN|8iyR2PoSX4QMMLJteS1k|QHgS6/mDp94MMLJteS1k|
|ZXQta2V5LTEys2ux    |ZWNyZXQta2V5LTEys2ux|s2ux                |s2ux                |Rsnavu1+OBB/OaiAiCS6|ZW1vLXNlY3JldC1rZXkt|ZW1vLXNlY3JldC1rZXkt|
|                    |                    |                    |                    |ez3NwSu9wC5SGctP2tgR|MTKza7E=            |MTKza7E=            |
|                    |                    |                    |                    |TrwwWVIfYyOvbaEmn2bB|                    |                    |
|                    |                    |                    |                    |hkZ3KnaoYsvpffpsMAww|                    |                    |
|                    |                    |                    |                    |sm15LWRlbW8tc2VjcmV0|                    |                    |
|                    |                    |                    |                    |LWtleS0xMrNrsQ==    |                    |                    |
|LQE7msoBX7UQi9stFcc6|MAE7msoBbD3sMzKbj9Ap|JAE7msoB32HSCCt2W6z4|JAE7msoBy8OJ9nUDj8iY|eQE7msoBv7auFfnsjyrm|NQE7msoB0Opi0OAuVQso|NQE7msoBsCn/ryz9ySj5|
|BhWD7ipEvbP+VkOTgnHc|GSOA1gsEhI3MgMLTkKB0|amh3UoW6NiC0R2dvOl5v|psWOFG+XdVYyEeqHAxlH|8I8ERhFGvzWydzhAxiCD|PKkQf6QQbSx9XAI9a9YQ|4iqhZklun+AqwLL7e7Zi|
|1PYWXA+KqPKdPOJn2ItM|GHmN3iJBhKWLunrHpnl2|XPyS/QG8DDCybXktZGVt|/Ed6ArqaDDCybXktZGVt|b09YJ/XN/3+rkPm9/dWt|WtHQCZkCItoKe0DoHzW/|6HKhS/614fyhBdeFa2e0|
|DDCybXktZGVtby1zZWNy|0x5DDDCybXktZGVtby1z|by1zZWNyZXQta2V5LTEy|by1zZWNyZXQta2V5LTEy|KlQFI+dnscsxgjeb94vs|jCaMfaaNLmgMMLJteS1k|IKj7sWmcpugMMLJteS1k|
|ZXQta2V5LTEys2ux    |ZWNyZXQta2V5LTEys2ux|s2ux                |s2ux                |BcAleL0OozN01S7ukPV7|ZW1vLXNlY3JldC1rZXkt|ZW1vLXNlY3JldC1rZXkt|
|                    |                    |                    |                    |V3jb1PHQaMVvgPbo7bBc|MTKza7E=            |MTKza7E=            |
|                    |                    |                    |                    |KcgvYJwbwcDe8BSg0U9A|                    |                    |
|                    |                    |                    |                    |D0nO7okbgdtJXTzF5zhy|                    |                    |
|                    |                    |                    |                    |XQwwsm15LWRlbW8tc2Vj|                    |                    |
|                    |                    |                    |                    |cmV0LWtleS0xMrNrsQ==|                    |                    |
Limit Reached
Query terminated
```

Applying the `K4KDECRYPT` UDF in the following `SELECT` statement shows how to decrypt the column values for all rows to get back the original values.

Important to note here is the fact, that the 2nd function parameter is needed to support ksqlDB to properly infer the actual return type for decrypted values. In other words, the data type definition of a field needs to be known and specified upfront for successful decryption.

```sql
SELECT 
    K4KDECRYPT(id,'') AS id,
    K4KDECRYPT(mystring,'') AS mystring,
    K4KDECRYPT(myint,0) AS myint,
    K4KDECRYPT(myboolean,false) AS myboolean,
    K4KDECRYPT(mysubdoc1,struct(somestring:='',someint:=0)) AS mysubdoc1,
    K4KDECRYPT(myarray,array['']) AS myarray,
    K4KDECRYPT(mysubdoc2,map('':=0)) AS mysubdoc2
FROM MY_SAMPLE_DATA_JSON_ENC_O
EMIT CHANGES LIMIT 2;
```

```text
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|ID                  |MYSTRING            |MYINT               |MYBOOLEAN           |MYSUBDOC1           |MYARRAY             |MYSUBDOC2           |
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|1234567890          |some foo text       |42                  |true                |{SOMESTRING=As I was|[str_1, str_2, str_3|{k3=7, k1=9, k2=8}  |
|                    |                    |                    |                    | going to St. Ives, |]                   |                    |
|                    |                    |                    |                    |SOMEINT=1234}       |                    |                    |
|9876543210          |some bla text       |23                  |false               |{SOMESTRING=I met a |[str_A, str_B, str_C|{k3=4, k1=6, k2=5}  |
|                    |                    |                    |                    |man with seven wives|]                   |                    |
|                    |                    |                    |                    |, SOMEINT=9876}     |                    |                    |
Limit Reached
Query terminated
```

##### Element Mode Encryption Example

In case all fields of the data records above should get encrypted in element mode on insertion, the target data types for the ksqlDB STREAM are defined as follows:

```sql
-- 'data stream with all ciphertext columns encrypted in object mode'
CREATE STREAM MY_SAMPLE_DATA_JSON_ENC_E (
id VARCHAR,
mystring VARCHAR,
myint VARCHAR,
myboolean VARCHAR,
mysubdoc1 STRUCT<somestring VARCHAR,someint VARCHAR>,
myarray ARRAY<VARCHAR>,
mysubdoc2 MAP<VARCHAR,VARCHAR>
) WITH (
KAFKA_TOPIC = 'my_sample_data_json_enc_e',
VALUE_FORMAT = 'JSON',
PARTITIONS = 1
);
```

Applying the `K4KENCRYPT` UDF in the following two `INSERT` statements shows how to make sure that all column values are encrypted in element mode on-the-fly with the default settings for key identifier and cipher algorithm as defined in the configuration of the UDF. Doing so makes sure that the values are encrypted on the client-side (i.e. the ksqlDB processing nodes) before the data hits the Kafka brokers.

Note, that for complex field types the 2nd function parameter is used to specify the return data type for encrypted values, which in case of the STRUCT, ARRAY and MAP field types are defined such that the function encrypts data items individually.

```sql
INSERT INTO MY_SAMPLE_DATA_JSON_ENC_E (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4KENCRYPT('1234567890'),
    K4KENCRYPT('some foo text'),
    K4KENCRYPT(42),
    K4KENCRYPT(true),
    K4KENCRYPT(STRUCT(somestring:='As I was going to St. Ives',someint:=1234),STRUCT(somestring:='',someint:='')),
    K4KENCRYPT(array['str_1','str_2','str_3'],array['']),
    K4KENCRYPT(map('k1':=9,'k2':=8,'k3':=7),map('':=''))
);

INSERT INTO MY_SAMPLE_DATA_JSON_ENC_E (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4KENCRYPT('9876543210'),
    K4KENCRYPT('some bla text'),
    K4KENCRYPT(23),
    K4KENCRYPT(false),
    K4KENCRYPT(STRUCT(somestring:='I met a man with seven wives',someint:=9876),STRUCT(somestring:='',someint:='')),
    K4KENCRYPT(array['str_A','str_B','str_C'],array['']),
    K4KENCRYPT(map('k1':=6,'k2':=5,'k3':=4),map('':=''))
);

```

Inspecting the contents of this stream after the insertion with a simple `SELECT` query shows two rows with their encrypted values for each of the columns.

```sql
SELECT * FROM MY_SAMPLE_DATA_JSON_ENC_E EMIT CHANGES LIMIT 2;
```

```text
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|ID                  |MYSTRING            |MYINT               |MYBOOLEAN           |MYSUBDOC1           |MYARRAY             |MYSUBDOC2           |
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|LQE7msoB0y9eQ3Y6kel1|MAE7msoBzJE36JZDJLpC|JAE7msoBNOW04Sm+tVPw|JAE7msoBd+KIZfUDMwr/|{SOMESTRING=PQE7msoB|[KAE7msoBGrjQNXzVKzD|{k3=JAE7msoBoRywOvIe|
|qZrGS2lMlNLFEoX5kvkl|AOISRefQXLDL3LbHr8f+|zB6ORoVO0kWtAxdqlcjb|+Way+ICAnapv3i0UNg2s|b7+qWFeiGg1+QNAaQWKm|x3UmWODfdgDY1tyqqU6f|g9WY6GsyiuqK8MZUFUZI|
|ak2qZfAESCXsYRY+fikz|5xS9blpLAasr/Ye6Um6u|Aq4QOf2oDDCybXktZGVt|xNqzEKZsDDCybXktZGVt|L82XZKRzweQMFOX9ZuV+|i5bwKGIXxgLRxrwwwsm1|IZ/DVsEj6tmcDDCybXkt|
|DDCybXktZGVtby1zZWNy|GKdGDDCybXktZGVtby1z|by1zZWNyZXQta2V5LTEy|by1zZWNyZXQta2V5LTEy|iiZey10E0EnFajY14g4W|5LWRlbW8tc2VjcmV0LWt|ZGVtby1zZWNyZXQta2V5|
|ZXQta2V5LTEys2ux    |ZWNyZXQta2V5LTEys2ux|s2ux                |s2ux                |Y9oRWfGolA6pZgwwsm15|leS0xMrNrsQ==, KAE7m|LTEys2ux, k1=JAE7mso|
|                    |                    |                    |                    |LWRlbW8tc2VjcmV0LWtl|soBFFY6RSDABGLM7JPli|BrlHYN8B7c57wXeXXNcf|
|                    |                    |                    |                    |eS0xMrNrsQ==, SOMEIN|XFPQ85C+p/BgKF8xU8OS|XRAE1WfBP7gEEn4+qoBP|
|                    |                    |                    |                    |T=JQE7msoBPR/hE8Dvuj|rM8YhAzOwwwsm15LWRlb|7DDCybXktZGVtby1zZWN|
|                    |                    |                    |                    |OGXU9iq5ZF4aq382XVJy|W8tc2VjcmV0LWtleS0xM|yZXQta2V5LTEys2ux, k|
|                    |                    |                    |                    |MPxvFRUHx7XQwwsm15LW|rNrsQ==, KAE7msoBvzT|2=JAE7msoBf386bikTWC|
|                    |                    |                    |                    |RlbW8tc2VjcmV0LWtleS|bDJc/Eqy5OvxI/gDqicW|eUf+drenNHL7pa60bGEK|
|                    |                    |                    |                    |0xMrNrsQ==}         |JHE5cxUFn2hb6jlHT3XA|5oUenuB1vsDDCybXktZG|
|                    |                    |                    |                    |                    |e7Awwsm15LWRlbW8tc2V|Vtby1zZWNyZXQta2V5LT|
|                    |                    |                    |                    |                    |jcmV0LWtleS0xMrNrsQ=|Eys2ux}             |
|                    |                    |                    |                    |                    |=]                  |                    |
|LQE7msoBIM5R3mRQo8Gb|MAE7msoBoFvuA4iIm25i|JAE7msoBcZSS6GX7c/OV|JAE7msoBxgFo8ELdtbTQ|{SOMESTRING=PwE7msoB|[KAE7msoByZ0niuFi+45|{k3=JAE7msoBaDxK3jNG|
|+mEoxQ1iLKwMJ01UGdLI|+knYmvdlbPePt5RkdL2C|Byt60nLhPrh7Cn5oAIwe|uq8vSBCzRVyGvpx4j8iS|F1qINnXLKPNIDK8tbwuT|d/2gBc7xs2BfMDAd01eH|kLrhIwTQPpdMb+AStomE|
|ZNWYcHW2alpVdDPc63JY|rK9lAPb46G9fNZ3BnZHX|yRpUCh8hDDCybXktZGVt|BuUGnkQ+DDCybXktZGVt|p4660vfWkmyKBw7alwk9|UO2Zeml+l5j9jugwwsm1|wq/V1MHWOhjXDDCybXkt|
|DDCybXktZGVtby1zZWNy|zBcrDDCybXktZGVtby1z|by1zZWNyZXQta2V5LTEy|by1zZWNyZXQta2V5LTEy|dAnVVyAhv36d2RHGxHVg|5LWRlbW8tc2VjcmV0LWt|ZGVtby1zZWNyZXQta2V5|
|ZXQta2V5LTEys2ux    |ZWNyZXQta2V5LTEys2ux|s2ux                |s2ux                |ec5OAmLxAczG9VZ4DDCy|leS0xMrNrsQ==, KAE7m|LTEys2ux, k1=JAE7mso|
|                    |                    |                    |                    |bXktZGVtby1zZWNyZXQt|soBqQf6cwHV/nipsHGnE|BKmrGKLGJmm3u/zJvR2z|
|                    |                    |                    |                    |a2V5LTEys2ux, SOMEIN|HP6z2wO5Y5CD3rLGQtaq|wiyZjE7mN7FqCmy5YyMX|
|                    |                    |                    |                    |T=JgE7msoBwRAlSmYNx4|ZA2H6zceQwwsm15LWRlb|ADDCybXktZGVtby1zZWN|
|                    |                    |                    |                    |Pj7GIHM2oytX/KY+4Z8S|W8tc2VjcmV0LWtleS0xM|yZXQta2V5LTEys2ux, k|
|                    |                    |                    |                    |VuCHexB0dwCokMMLJteS|rNrsQ==, KAE7msoB87m|2=JAE7msoBqH9D+6q8Tb|
|                    |                    |                    |                    |1kZW1vLXNlY3JldC1rZX|h0V5ZUImKhkLPx0eLOEi|I12XyFGvhqljZyxZKgx0|
|                    |                    |                    |                    |ktMTKza7E=}         |ijhhqXOeYDfASXUUwsNs|CdLP6pRbzuDDCybXktZG|
|                    |                    |                    |                    |                    |Bvwwwsm15LWRlbW8tc2V|Vtby1zZWNyZXQta2V5LT|
|                    |                    |                    |                    |                    |jcmV0LWtleS0xMrNrsQ=|Eys2ux}             |
|                    |                    |                    |                    |                    |=]                  |                    |
Limit Reached
Query terminated
```

Complex field types have been encrypted differently, namely in element mode, due to the chosen return types which are:

* `STRUCT<somestring VARCHAR,someint VARCHAR>` for `mysubdoc1` which causes all struct fields to be encrypted separately and each field becoming a VARCHAR representing the BASE64-encoded ciphertext
* `ARRAY<VARCHAR>` for `myarray` which causes all array elements to be encrypted separately and each element becoming a VARCHAR representing the BASE64-encoded ciphertext
* `MAP<VARCHAR,VARCHAR>` for `mysubdoc2` which causes all map values to be encrypted separately and each value becoming a VARCHAR representing the BASE64-encoded ciphertext

Applying the `K4KDECRYPT` UDF in the following `SELECT` statement shows how to decrypt the column values for all rows to get back the original values.

Important to note here is the fact, that the 2nd function parameter is needed to support ksqlDB to properly infer the actual return type for decrypted values like so:
* for simple fields there is no difference whether they have been encrypted in object or in element mode
* for `STRUCT` fields encrypted in element mode, it's necessary to process them individually and re-assemble the struct manually
* for `ARRAY` fields encrypted in element mode, the UDF processes the array elements one by one which means the expected target type after decryption is specified to be that of a single element in said array
* for `MAP` fields encrypted in element mode, the UDF processes the map entries one by one which means the expected target type after decryption is specified to be that of a single map entry value in said map

In other words, the data type definition of a field needs to be known and specified upfront for successful decryption.

```sql
SELECT 
    K4KDECRYPT(id,'') AS id,
    K4KDECRYPT(mystring,'') AS mystring,
    K4KDECRYPT(myint,0) AS myint,
    K4KDECRYPT(myboolean,false) AS myboolean,
    STRUCT(
        somestring:=K4KDECRYPT(mysubdoc1->somestring,''),
        someint:=K4KDECRYPT(mysubdoc1->someint,0)
    ) AS mysubdoc1,
    K4KDECRYPT(myarray,'') AS myarray,
    K4KDECRYPT(mysubdoc2,0) AS mysubdoc2
FROM MY_SAMPLE_DATA_JSON_ENC_E
EMIT CHANGES LIMIT 2;
```

```text
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|ID                  |MYSTRING            |MYINT               |MYBOOLEAN           |MYSUBDOC1           |MYARRAY             |MYSUBDOC2           |
+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+--------------------+
|1234567890          |some foo text       |42                  |true                |{SOMESTRING=As I was|[str_1, str_2, str_3|{k3=7, k1=9, k2=8}  |
|                    |                    |                    |                    | going to St. Ives, |]                   |                    |
|                    |                    |                    |                    |SOMEINT=1234}       |                    |                    |
|9876543210          |some bla text       |23                  |false               |{SOMESTRING=I met a |[str_A, str_B, str_C|{k3=4, k1=6, k2=5}  |
|                    |                    |                    |                    |man with seven wives|]                   |                    |
|                    |                    |                    |                    |, SOMEINT=9876}     |                    |                    |
Limit Reached
Query terminated
```

