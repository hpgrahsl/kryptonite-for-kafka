# Kryptonite for Kafka: Client-Side 🔒 Field-Level 🔓 Cryptography for Apache Kafka®

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

## Flink User-Defined Functions (UDFs)

Kryptonite for Kafka provides a set of [Flink](https://flink.apache.org/) [user-defined functions](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/udfs/) (UDFs) to selectively encrypt or decrypt column values in Flink `TABLES`. The simple examples below show how to install, configure, and apply the UDFs.

### Build and Deployment

Either you build this project from sources via Maven or you can download pre-built, self-contained packages of the latest artefacts. Starting with Kryptonite for Kafka 0.5.0, the pre-built Flink UDFs can be downloaded directly from the [release pages](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).

In order to deploy the UDFs **put the jar into the _'flink libraries directory'_** that is configured to be scanned during bootstrap of your Flink cluster.

Before the UDFs can be used, you need to pre-register all the functions you plan to use in the Flink catalog of your choice. The snippet below is meant to be interactively run in a Flink SQL session or as part of the initialization when launching the Flink SQL client. Also, it's going to assume the functions should get registered into the default (in-memory) catalog using the recommended function naming convention:

```sql
ADD JAR '<FULL_PATH_TO_FLINK_UDFS_KRYPTONITE_JAR_FILE_HERE>';

CREATE FUNCTION k4k_encrypt AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptUdf' LANGUAGE JAVA;

CREATE FUNCTION k4k_encrypt_array AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptArrayUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_array AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptArrayUdf' LANGUAGE JAVA;

CREATE FUNCTION k4k_encrypt_map AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptMapUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_map AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptMapUdf' LANGUAGE JAVA;

CREATE FUNCTION k4k_encrypt_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptFpeUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptFpeUdf' LANGUAGE JAVA;

CREATE FUNCTION k4k_encrypt_array_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptArrayFpeUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_array_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptArrayFpeUdf' LANGUAGE JAVA;

CREATE FUNCTION k4k_encrypt_map_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.EncryptMapFpeUdf' LANGUAGE JAVA;
CREATE FUNCTION k4k_decrypt_map_fpe AS 'com.github.hpgrahsl.flink.functions.kryptonite.DecryptMapFpeUdf' LANGUAGE JAVA;
```

Verify a successful deployment by checking all available user functions e.g. from within an interactive [Flink SQL Client](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sqlclient/) session, which should output all Kryptonite for Kafka related Flink user-defined functions like so:

```
Flink SQL> SHOW USER FUNCTIONS;

+-----------------------+
|         function name |
+-----------------------+
|           k4k_decrypt |
|     k4k_decrypt_array |
| k4k_decrypt_array_fpe |
|       k4k_decrypt_fpe |
|       k4k_decrypt_map |
|   k4k_decrypt_map_fpe |
|           k4k_encrypt |
|     k4k_encrypt_array |
| k4k_encrypt_array_fpe |
|       k4k_encrypt_fpe |
|       k4k_encrypt_map |
|   k4k_encrypt_map_fpe |
+-----------------------+
12 rows in set
```

After that, start using the available UDFs:

**AEAD Encryption (AES-GCM/AES-GCM-SIV):**
* `K4K_ENCRYPT`
* `K4K_ENCRYPT_ARRAY`
* `K4K_ENCRYPT_MAP`
* `K4K_DECRYPT`
* `K4K_DECRYPT_ARRAY`
* `K4K_DECRYPT_MAP`

**Format Preserving Encryption (FPE FF3-1) available in Kryptonite for Kafka starting with version 0.6.0:**
* `K4K_ENCRYPT_FPE`
* `K4K_ENCRYPT_ARRAY_FPE`
* `K4K_ENCRYPT_MAP_FPE`
* `K4K_DECRYPT_FPE`
* `K4K_DECRYPT_ARRAY_FPE`
* `K4K_DECRYPT_MAP_FPE`

to selectively encrypt or decrypt column values in your Flink `TABLE` rows.

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
            <td>cipher_data_keys</td>
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
     <td><strong>mandatory</strong> for all UDFs: 
                <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP,
                K4K_DECRYPT, K4K_DECRYPT_ARRAY, K4K_DECRYPT_MAP</code>
            </td>
        </tr>
        <tr>
            <td>cipher_data_key_identifier</td>
            <td>keyset identifier to be used as default data encryption keyset for all UDF calls which don't refer to a
                specific keyset identifier in the parameter list</td>
            <td>string</td>
            <td><pre>!no default!</pre></td>
            <td>non-empty string referring to an existing identifier for a keyset</td>
            <td><strong>mandatory</strong> for <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP</code>
            </td>
        <tr>
            <td>key_source</td>
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
            <td><strong>optional</strong> for all UDFs: 
                <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP,
                K4K_DECRYPT, K4K_DECRYPT_ARRAY, K4K_DECRYPT_MAP</code>
            </td>
        </tr>
        <tr>
            <td>kms_type</td>
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
            <td><strong>optional</strong> for all UDFs: 
                <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP,
                K4K_DECRYPT, K4K_DECRYPT_ARRAY, K4K_DECRYPT_MAP</code>
            </td>
        </tr>
        <tr>
            <td>kms_config</td>
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
           <td><strong>optional</strong> for all UDFs: 
                <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP,
                K4K_DECRYPT, K4K_DECRYPT_ARRAY, K4K_DECRYPT_MAP</code>
            </td>
        </tr>
        <tr>
            <td>kek_type</td>
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
            <td><strong>optional</strong> for all UDFs: 
                <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP,
                K4K_DECRYPT, K4K_DECRYPT_ARRAY, K4K_DECRYPT_MAP</code>
            </td>
        </tr>
        <tr>
            <td>kek_config</td>
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
            <td><strong>optional</strong> for all UDFs: 
                <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP,
                K4K_DECRYPT, K4K_DECRYPT_ARRAY, K4K_DECRYPT_MAP</code>
            </td>
        </tr>
        <tr>
            <td>kek_uri</td>
            <td>URI referring to the key encryption key stored in the respective remote/cloud KMS, currently only support Google Cloud KMS</td>
            <td>string</td>
            <td><pre>!no default!</pre></td>
            <td>a valid and supported Tink key encryption key URI, e.g. pointing to a key in Google Cloud KMS (<code>kek_type=GCP</code>)
            <pre>gcp-kms://...</pre>
            </td>
            <td><strong>optional</strong> for all UDFs: 
                <code>K4K_ENCRYPT, K4K_ENCRYPT_ARRAY, K4K_ENCRYPT_MAP,
                K4K_DECRYPT, K4K_DECRYPT_ARRAY, K4K_DECRYPT_MAP</code>
            </td>
        </tr>
    </tbody>
</table>


##### Configure Flink UDFs

Here is an example for how to specify the mandatory configuration settings for the encryption / decryption functions in a `compose.yaml` file for container-based deployments of Flink. You specify any of the supported settings described in the previous section as part of the environment for the Flink container services, e.g. 

```yaml
    environment:
      - cipher_data_keys=[{"identifier":"my-demo-secret-key","material":{"primaryKeyId":1234567890,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey","value":"<BASE64_ENCODED_KEY_HERE>","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":1234567890,"outputPrefixType":"TINK"}]}}]
```

You can add further configuration settings to the compose definition as you see fit. After making sure that all the mandatory configuration properties are set, start using the UDFs to encrypt and decrypt column values in Flink table rows.

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

For representing data records such as this in plaintext (i.e. in unencrypted form) the following TABLE could be created in Flink:

```sql
-- 'table with all plaintext columns'
CREATE TABLE my_sample_data_json (
id VARCHAR,
mystring VARCHAR,
myint INT,
myboolean BOOLEAN,
mysubdoc1 ROW<somestring VARCHAR,someint INT>,
myarray ARRAY<VARCHAR>,
mysubdoc2 MAP<VARCHAR,INT>
) WITH (
  'connector' = 'kafka',
  'topic' = 'my_sample_data_json',
  'properties.bootstrap.servers' = 'kafka:9092',
  'properties.group.id' = 'g123',
  'scan.startup.mode' = 'earliest-offset',
  'format' = 'json'
);
```

**However, in order to store selected fields as ciphertext in the first place (i.e. on insertion), redacted column data type definitions are needed.** Encrypted values are represented as BASE64-encoded strings which means that the target data type for encrypted columns must be defined as follows:

* **VARCHAR** for primitive Flink data types or for complex Flink data types if encrypted as a whole (_object mode_)
* **ARRAY&lt;VARCHAR&gt; | MAP&lt;VARCHAR,VARCHAR&gt; | ROW&lt; ... VARCHAR,... &gt;** in case complex Flink data types (ARRAY, MAP, ROW) are encrypted element-wise (_element mode_) the types for ARRAY elements, MAP values and ROW fields become VARCHAR

##### Object Mode Encryption Example

In case all fields of the data record above should get encrypted in object mode on insertion, all target data types become VARCHAR and the following Flink TABLE can be defined:

```sql
-- 'table with all ciphertext columns encrypted in object mode'
CREATE TABLE my_sample_data_json_enc_o (
id VARCHAR,
mystring VARCHAR,
myint VARCHAR,
myboolean VARCHAR,
mysubdoc1 VARCHAR,
myarray VARCHAR,
mysubdoc2 VARCHAR
) WITH (
  'connector' = 'kafka',
  'topic' = 'my_sample_data_json_enc_o',
  'properties.bootstrap.servers' = 'kafka:9092',
  'properties.group.id' = 'g234',
  'scan.startup.mode' = 'earliest-offset',
  'format' = 'json'
);
```

Applying the `K4K_ENCRYPT` UDF in the following two `INSERT` statements shows how to make sure that all column values are encrypted in object mode on-the-fly with the default settings for key identifier and cipher algorithm as defined in the configuration of the UDF. Doing so makes sure that the values are encrypted on the client-side (i.e. the Flink task manager nodes) before the data hits the Kafka brokers.

```sql
INSERT INTO my_sample_data_json_enc_o (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4K_ENCRYPT('1234567890'),
    K4K_ENCRYPT('some foo text'),
    K4K_ENCRYPT(42),
    K4K_ENCRYPT(true),
    K4K_ENCRYPT(ROW('As I was going to St. Ives',1234)),
    K4K_ENCRYPT(ARRAY['str_1','str_2','str_3']),
    K4K_ENCRYPT(MAP['k1',9,'k2',8,'k3',7])
);

INSERT INTO my_sample_data_json_enc_o (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4K_ENCRYPT('9876543210'),
    K4K_ENCRYPT('some bla text'),
    K4K_ENCRYPT(23),
    K4K_ENCRYPT(false),
    K4K_ENCRYPT(ROW('I met a man with seven wives',9876)),
    K4K_ENCRYPT(ARRAY['str_A','str_B','str_C']),
    K4K_ENCRYPT(MAP['k1',6,'k2',5,'k3',4])
);

```

Inspecting the contents of this Flink table after the insertion with a simple `SELECT` query shows two rows with their encrypted values as BASE64-encoded strings for each of the columns. 

```sql
SELECT * FROM my_sample_data_json_enc_o LIMIT 2;
```

```text
                             id                       mystring                          myint                      myboolean                      mysubdoc1                        myarray                      mysubdoc2
 LQE7msoBaQvZjgQ/UryvMB0Nyego0~ MAE7msoBrfE5/jXDYlpazS+LSHkIV~ JAE7msoBgIPxYGiQMFcgyvlNu0hRj~ JAE7msoBY9rUCEdE+xRSPV44hDZt5~ hgEBO5rKAblwqiQmkeYJsji7rraxE~ RwE7msoBGKbnzq3V9Apavotsfs3Eu~ MwE7msoBxQQG7t7xkbJmiDTt+sHQH~
 LQE7msoB2RY87WWvP8SHXCqjMypHB~ MAE7msoBYYIeCLJJbkM2E1uq/Tx/L~ JAE7msoBtun4JoxV2NvctsU1PJ+Sc~ JAE7msoBJnklz4RDFlbZXwXCmR174~ iQEBO5rKAcjqsLvuXgRyfg5pQlPHt~ RwE7msoBY6cAm/dw+OP4eRvxVJzFE~ MwE7msoBoRV0/LrFTB/BsSQgUy9rk~
```

Applying the `K4K_DECRYPT` UDF in the following `SELECT` statement shows how to decrypt the column values for all rows to get back the original values.

_Important to note here is the fact, that the 2nd function parameter is needed to support Flink SQL to properly infer the actual return type for decrypted values. In other words, the data type definition of a field needs to be known and specified by means of an exemplary value which is used for type inference in order to successfully decrypt the data._

```sql
SELECT 
    K4K_DECRYPT(id,'') AS id,
    K4K_DECRYPT(mystring,'') AS mystring,
    K4K_DECRYPT(myint,0) AS myint,
    K4K_DECRYPT(myboolean,false) AS myboolean,
    K4K_DECRYPT(mysubdoc1,ROW('',0)) AS mysubdoc1,
    K4K_DECRYPT(myarray,array['']) AS myarray,
    K4K_DECRYPT(mysubdoc2,map['',0]) AS mysubdoc2
FROM my_sample_data_json_enc_o LIMIT 2;
```

```
                             id                       mystring       myint myboolean                      mysubdoc1                        myarray                      mysubdoc2
                     1234567890                  some foo text          42      TRUE (As I was going to St. Ives, ~          [str_1, str_2, str_3]             {k3=7, k1=9, k2=8}
                     9876543210                  some bla text          23     FALSE (I met a man with seven wives~          [str_A, str_B, str_C]             {k3=4, k1=6, k2=5}
```

##### Element Mode Encryption Example

In case all fields of the data records above should get encrypted in element mode on insertion, the target data types for the Flink TABLE are defined as follows:

```sql
-- 'table with all ciphertext columns encrypted in element mode'
CREATE TABLE my_sample_data_json_enc_e (
id VARCHAR,
mystring VARCHAR,
myint VARCHAR,
myboolean VARCHAR,
mysubdoc1 ROW<somestring VARCHAR,someint VARCHAR>,
myarray ARRAY<VARCHAR>,
mysubdoc2 MAP<VARCHAR,VARCHAR>
) WITH (
  'connector' = 'kafka',
  'topic' = 'my_sample_data_json_enc_e',
  'properties.bootstrap.servers' = 'kafka:9092',
  'properties.group.id' = 'g234',
  'scan.startup.mode' = 'earliest-offset',
  'format' = 'json'
);
```

Applying the `K4K_ENCRYPT`,`K4K_ENCRYPT_ARRAY`, and `K4K_ENCRYPT_MAP` UDF in the following two `INSERT` statements shows how to make sure that all column values are encrypted in element mode on-the-fly with the default settings for key identifier and cipher algorithm as defined in the configuration of the UDF. Doing so makes sure that the values are encrypted on the client-side (i.e. the Flink task manager nodes) before the data hits the Kafka brokers.

```sql
INSERT INTO my_sample_data_json_enc_e (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4K_ENCRYPT('1234567890'),
    K4K_ENCRYPT('some foo text'),
    K4K_ENCRYPT(42),
    K4K_ENCRYPT(true),
    ROW(K4K_ENCRYPT('As I was going to St. Ives'),K4K_ENCRYPT(1234)),
    K4K_ENCRYPT_ARRAY(ARRAY['str_1','str_2','str_3']),
    K4K_ENCRYPT_MAP(MAP['k1',9,'k2',8,'k3',7])
);

INSERT INTO my_sample_data_json_enc_e (id,mystring,myint,myboolean,mysubdoc1,myarray,mysubdoc2) VALUES (
    K4K_ENCRYPT('9876543210'),
    K4K_ENCRYPT('some bla text'),
    K4K_ENCRYPT(23),
    K4K_ENCRYPT(false),
    ROW(K4K_ENCRYPT('I met a man with seven wives'),K4K_ENCRYPT(9876)),
    K4K_ENCRYPT_ARRAY(ARRAY['str_A','str_B','str_C']),
    K4K_ENCRYPT_MAP(MAP['k1',6,'k2',5,'k3',4])
);

```

Inspecting the contents of this Flink table after the insertion with a simple `SELECT` query shows two rows with their encrypted values as BASE64-encoded strings for each of the columns. 

```sql
SELECT * FROM my_sample_data_json_enc_e LIMIT 2;
```

```text
                             id                       mystring                          myint                      myboolean                      mysubdoc1                        myarray                      mysubdoc2
 LQE7msoBlUIp9lpYKWmaow0QVWXQK~ MAE7msoByn32xVPTRKLQLAx3Xxtq3~ JAE7msoBCg7AQvvXAr34fs4JdHjR9~ JAE7msoBh3dKrVMI76juHXNGUfz/G~ (PQE7msoBHEJCahY67ataS2spSVi4~ [KAE7msoBnxH1MErXPNEptph72/MC~ {k1=JAE7msoBHaV545FHxRF6ph4Rm~
 LQE7msoBhw9wO7Pw8xzx4GLA6Yps1~ MAE7msoBqh8cbURb4h7pdrb+7R+mQ~ JAE7msoBUf2KqxNQrqzEIVQSkKhkp~ JAE7msoBaD4t7uwxOzkrlZwNjr2Fc~ (PwE7msoB5NON1KMGAYElltvydr53~ [KAE7msoBZtQ9PO3XnnIC/nw3F30M~ {k1=JAE7msoBzNtlA7qXfiOBfB6Ku~
```

Applying the `K4K_DECRYPT`,`K4K_DECRYPT_ARRAY`, and `K4K_DECRYPT_MAP` UDF in the following `SELECT` statement shows how to decrypt the column values for all rows to get back the original values.

_Important to note here is the fact, that the 2nd function parameter is needed to support Flink SQL to properly infer the actual return type for decrypted values like so:_

* for simple fields there is no difference whether they have been encrypted in object or in element mode
* for `ROW` fields encrypted in element mode, it's necessary to process them individually and re-assemble the structured type manually
* for `ARRAY` fields encrypted in element mode, the UDF processes the array elements one by one which means the expected target type after decryption is specified to be that of a single element in said array
* for `MAP` fields encrypted in element mode, the UDF processes the map entries one by one which means the expected target type after decryption is specified to be that of a single map entry value in said map

In other words, the data type definition of a field needs to be known and specified upfront for successful decryption.

```sql
SELECT 
    K4K_DECRYPT(id,'') AS id,
    K4K_DECRYPT(mystring,'') AS mystring,
    K4K_DECRYPT(myint,0) AS myint,
    K4K_DECRYPT(myboolean,false) AS myboolean,
    ROW(
        K4K_DECRYPT(mysubdoc1.somestring,''),
        K4K_DECRYPT(mysubdoc1.someint,0)
    ) AS mysubdoc1,
    K4K_DECRYPT_ARRAY(myarray,'') AS myarray,
    K4K_DECRYPT_MAP(mysubdoc2,0) AS mysubdoc2
FROM my_sample_data_json_enc_e LIMIT 2;
```

```text
                             id                       mystring       myint myboolean                      mysubdoc1                        myarray                      mysubdoc2
                     1234567890                  some foo text          42      TRUE (As I was going to St. Ives, ~          [str_1, str_2, str_3]             {k1=9, k2=8, k3=7}
                     9876543210                  some bla text          23     FALSE (I met a man with seven wives~          [str_A, str_B, str_C]             {k1=6, k2=5, k3=4}
```

### Format Preserving Encryption (FPE)

Starting with version 0.6.0, Kryptonite for Kafka provides **Format Preserving Encryption (FPE)** UDFs using the FF3-1 algorithm. Unlike standard AEAD encryption which produces variable-length ciphertext, FPE maintains the original format and length of the plaintext data.

#### Key Characteristics of FPE

- **Format Preservation**: Encrypted data maintains the same format and length as the original plaintext
- **Character Set Preservation**: The ciphertext uses the same character set (alphabet) as the plaintext
- **Use Cases**: Ideal for scenarios where encrypted data must conform to specific formats, such as:
  - Credit card numbers (CCN)
  - Social security numbers (SSN)
  - Phone numbers
  - Account numbers
  - Database columns with strict format constraints

#### FPE UDF Function Signatures

The FPE UDFs provide multiple overloadings with different call parameter sets:

**K4K_ENCRYPT_FPE** - Encrypt data using Format Preserving Encryption:

```sql
-- Uses defaults from configuration
K4K_ENCRYPT_FPE(data)

-- Specify key identifier and algorithm
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm)

-- Specify key identifier, algorithm, and tweak
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak)

-- Specify key identifier, algorithm, tweak, and alphabet type
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType)

-- Specify all parameters including custom alphabet (only when fpeAlphabetType='CUSTOM')
K4K_ENCRYPT_FPE(data, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType, fpeAlphabetCustom)
```

**K4K_DECRYPT_FPE** - Decrypt FPE-encrypted data:

```sql
-- Uses defaults from configuration
K4K_DECRYPT_FPE(encryptedData)

-- Specify key identifier and algorithm
K4K_DECRYPT_FPE(encryptedData, keyIdentifier, cipherAlgorithm)

-- Specify key identifier, algorithm, and tweak
K4K_DECRYPT_FPE(encryptedData, keyIdentifier, cipherAlgorithm, fpeTweak)

-- Specify key identifier, algorithm, tweak, and alphabet type
K4K_DECRYPT_FPE(encryptedData, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType)

-- Specify all parameters including custom alphabet (only when fpeAlphabetType='CUSTOM')
K4K_DECRYPT_FPE(encryptedData, keyIdentifier, cipherAlgorithm, fpeTweak, fpeAlphabetType, fpeAlphabetCustom)
```

Similar signatures exist for `K4K_ENCRYPT_ARRAY_FPE`, `K4K_ENCRYPT_MAP_FPE`, `K4K_DECRYPT_ARRAY_FPE`, and `K4K_DECRYPT_MAP_FPE`.

#### Supported Alphabet Types

| Alphabet Type | Characters | Example Use Case |
|--------------|------------|------------------|
| `DIGITS` | `0123456789` | Credit card numbers, SSN, numeric IDs |
| `UPPERCASE` | `A-Z` | Uppercase text data |
| `LOWERCASE` | `a-z` | Lowercase text data |
| `ALPHANUMERIC` | `0-9A-Za-z` | Mixed alphanumeric codes |
| `ALPHANUMERIC_EXTENDED` | `0-9A-Za-z _,.!?@%$&§"'°^-+*/;:#(){}[]<>=~\|` | Text with special characters |
| `HEXADECIMAL` | `0-9A-F` | Hexadecimal strings |
| `CUSTOM` | User-defined via `fpeAlphabetCustom` | Custom character sets (e.g., binary: `01`) |

#### FPE SQL Example

Let's encrypt sensitive data while preserving its format. First, create a table for plaintext data:

```sql
-- Table with plaintext sensitive data
CREATE TABLE customer_data_plaintext (
    customer_id VARCHAR,
    credit_card_number VARCHAR,
    ssn VARCHAR,
    account_code VARCHAR
) WITH (
    'connector' = 'kafka',
    'topic' = 'customer_data_plain',
    'properties.bootstrap.servers' = 'kafka:9092',
    'scan.startup.mode' = 'earliest-offset',
    'format' = 'json'
);
```

Insert sample records:

```sql
INSERT INTO customer_data_plaintext (customer_id,credit_card_number,ssn,account_code) VALUES 
    ('CUST-123', '5544100122223434', '123456789', 'ACC12345XY'),
    ('CUST-234', '6606123443219988', '987654321', 'ACC98765ZW');
```

Create a target table where encrypted data will maintain the same format:

```sql
-- Table with FPE-encrypted data (same VARCHAR types, format preserved!)
CREATE TABLE customer_data_fpe_encrypted (
    customer_id VARCHAR,
    credit_card_number VARCHAR,
    ssn VARCHAR,
    account_code VARCHAR
) WITH (
    'connector' = 'kafka',
    'topic' = 'customer_data_fpe_enc',
    'properties.bootstrap.servers' = 'kafka:9092',
    'scan.startup.mode' = 'earliest-offset',
    'format' = 'json'
);
```

##### Encrypting with FPE

Insert data using FPE UDFs to encrypt while preserving format:

```sql
INSERT INTO customer_data_fpe_encrypted
SELECT
    customer_id,
    -- Encrypt 16-digit CCN using fpe key 1, DIGITS alphabet, and some constant tweak
    K4K_ENCRYPT_FPE(
        credit_card_number,
        'myFpeKey1',
        'CUSTOM/MYSTO_FPE_FF3_1',
        'tweakAB',
        'DIGITS'
    ) AS credit_card_number,
    -- Encrypt 9-digit SSN using fpe key 1, DIGITS alphabet, and different constant tweak
    K4K_ENCRYPT_FPE(
        ssn,
        'myFpeKey1',
        'CUSTOM/MYSTO_FPE_FF3_1',
        'tweakXY',
        'DIGITS'
    ) AS ssn,
    -- Encrypt account code with different fpe key 2,,default tweak, and default alphabet (ALPHANUMERIC)
    K4K_ENCRYPT_FPE(
        account_code,
        'myFpeKey2',
        'CUSTOM/MYSTO_FPE_FF3_1'
    ) AS account_code
FROM customer_data_plaintext;
```

After encryption, the data maintains its format:

```sql
SELECT * FROM customer_data_fpe_encrypted;
```

```text
                    customer_id             credit_card_number                            ssn                   account_code
                       CUST-123               4504103060547613                      574989795                     xSWUv2zl8m
                       CUST-234               1604279201734615                      180485792                     1M91w0D26R
```

Notice how:
- Credit card numbers remain 16 digits
- SSNs remain 9 digits
- Account codes maintain alphanumeric format and length

##### Decrypting FPE Data

Decrypt using the same parameters used for encryption:

```sql
SELECT
    customer_id,
    -- Decrypt CCN with matching parameters
    K4K_DECRYPT_FPE(
        credit_card_number,
        'myFpeKey1',
        'CUSTOM/MYSTO_FPE_FF3_1',
        'tweakAB',
        'DIGITS'
    ) AS credit_card_number,
    -- Decrypt SSN with matching parameters
    K4K_DECRYPT_FPE(
        ssn,
        'myFpeKey1',
        'CUSTOM/MYSTO_FPE_FF3_1',
        'tweakXY',
        'DIGITS'
    ) AS ssn,
    -- Decrypt account code with matching parameters
    K4K_DECRYPT_FPE(
        account_code,
        'myFpeKey2',
        'CUSTOM/MYSTO_FPE_FF3_1'
    ) AS account_code
FROM customer_data_fpe_encrypted;
```

which results in the same data that has been previously inserted into the table holding the original plaintext:

```text
                    customer_id             credit_card_number                            ssn                   account_code
                       CUST-123               5544100122223434                      123456789                     ACC12345XY
                       CUST-234               6606123443219988                      987654321                     ACC98765ZW
```

##### Using FPE with Arrays and Maps

For encrypting array elements or map values while preserving format:

```sql
-- Encrypt array of phone numbers (DIGITS alphabet)
SELECT K4K_ENCRYPT_ARRAY_FPE(
    ARRAY['5551234567', '5559876543'],
    'myFpeKey1',
    'CUSTOM/MYSTO_FPE_FF3_1',
    'tweakAB',
    'DIGITS'
) AS encrypted_phones;

-- Result: ARRAY['9873456210', '2341567890'] -- format preserved!

-- Encrypt map values (account IDs as ALPHANUMERIC)
SELECT K4K_ENCRYPT_MAP_FPE(
    MAP['primary', 'acc12345xY', 'secondary', 'acc98765zW'],
    'myFpeKey2',
    'CUSTOM/MYSTO_FPE_FF3_1'
) AS encrypted_accounts;

-- Result: {primary=Zx98Mn21qP, secondary=Rt76Cd43bN} -- format preserved!
```

##### Using Custom Alphabets

For binary data or custom character sets:

```sql
-- Encrypt binary string using CUSTOM alphabet
SELECT K4K_ENCRYPT_FPE(
    '01101000010001101000',
    'myFpeKey1',
    'CUSTOM/MYSTO_FPE_FF3_1',
    'mytweak',
    'CUSTOM',
    '01'  -- custom alphabet: only 0 and 1
) AS encrypted_binary;

-- Result: '10010111001110010111' -- still only 0s and 1s, same length!
```

#### FPE Keyset Configuration

FPE requires special keyset material. Configure FPE keys in your environment or job parameters:

```yaml
environment:
  - cipher_data_keys=[{"identifier":"myFpeKey","material":{"primaryKeyId":2000001,"key":[{"keyData":{"typeUrl":"io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey","value":"<BASE64_ENCODED_FPE_KEY>","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":2000001,"outputPrefixType":"RAW"}]}}]
  - cipher_data_key_identifier=myFpeKey
```

**Key differences from standard AEAD keysets:**
- `typeUrl`: Must be `io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey`
- `outputPrefixType`: Should be `RAW` (not `TINK`)

#### FPE Important Considerations

- **Consistent Configuration**: The same `fpeAlphabetType`, `fpeAlphabetCustom`, and `fpeTweak` must be used for both encryption and decryption
- **Tweak Parameter**: Different tweaks produce different ciphertext for the same plaintext
- **Minimum Length**: FPE requires input data to meet minimum length requirements based on the alphabet size
- **Format Constraints**: Ensure your input plaintext data conforms to the chosen alphabet (e.g., all digits for `DIGITS` alphabet)
