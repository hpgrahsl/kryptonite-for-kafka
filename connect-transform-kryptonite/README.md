# Kryptonite for Kafka: Client-Side 🔒 Field-Level 🔓 Cryptography for Apache Kafka®

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

## Kafka Connect Transformation (SMT)

Kryptonite for Kafka provides a turn-key ready [Kafka Connect](https://kafka.apache.org/documentation/#connect) [single message transformation](https://kafka.apache.org/documentation/#connect_transforms) (SMT) called `CipherField`. The simple examples below show how to install, configure and apply the SMT to encrypt and decrypt record fields.

### Build and Deployment

Either you build this project from sources via Maven or you can download pre-built, self-contained packages of the latest Kryptonite for Kafka artefacts. The pre-built Kafka Connect SMT can be downloaded from here [connect-transform-kryptonite-0.3.1.jar](https://drive.google.com/file/d/1aWobunR9eZabSflrhP-ZRBUAZpj1hORv/view?usp=sharing)

In order to deploy this custom SMT **put the jar into your _'connect plugin path'_** that is configured to be scanned during boostrap of the kafka connect worker node(s).


### Data Records without Schema

The following fictional data **record value without schema** - represented in JSON-encoded format - is used to illustrate a simple encrypt/decrypt scenario:

```json5
{
  "id": "1234567890",
  "myString": "some foo bla text",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString":"hello json"},
  "myArray1": ["str_1","str_2","...","str_N"],
  "mySubDoc2": {"k1":9,"k2":8,"k3":7}
}
```

#### Encryption of selected fields

Let's assume the fields `"myString"`,`"myArray1"` and `"mySubDoc2"` of the above data record should get encrypted, the `CipherField` SMT can be configured like so:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":{<TINK_KEYSET_SPEC_JSON_HERE>}}]", //key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_identifier": "my-demo-secret-key-123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **encrypted using the keyset** configured by its id with the `cipher_data_key_identifier` parameter. The keysets themselves are configured using the parameter `cipher_data_keys` where the key material itself is specified according to a [Tink](https://developers.google.com/tink/) keyset configuration in JSON format (here is a [concrete example](#tink-keysets)). Apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the keyset materials renders encryption useless. The recommended way of doing this for now is to either

- indirectly reference keyset materials by externalizing them into a separate properties file (find a few details [here](#externalize-configuration-parameters))

or

- to NOT store the keyset materials at the client-side in the first place, but instead resolve keysets at runtime from a cloud KMS such as [Azure Key Vault](https://azure.microsoft.com/en-us/services/key-vault/) which is supported as well.

_In general though, this can be considered a "chicken-and-egg" problem since the confidential settings in order to access a remote KMS also need to be stored somewhere somehow._

Since the configuration parameter `field_mode` is set to `OBJECT`, complex field types are processed as a whole instead of element-wise, the latter of which can be achieved by choosing `ELEMENT` mode.

Below is an exemplary JSON-encoded record after the encryption:

```json5
{
  "id": "1234567890",
  "myString": "M007MIScg8F0A/cAddWbayvUPObjxuGFxisu5MUckDhBss6fo3gMWSsR4xOLPEfs4toSDDCxa7E=",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString":"hello json"},
  "myArray1": "UuEKnrv91bLImQvKqXTET7RTP93XeLfNRhzJaXVc6OGA4E+mbvGFs/q6WEFCAFy9wklJE5EPXJ+P85nTBCiVrTkU+TR+kUWB9zNplmOL70sENwwwsWux",
  "mySubDoc2": "fLAnBod5U8eS+LVNEm3vDJ1m32/HM170ASgJLKdPF78qDxcsiWj+zOkvZBsk2g44ZWHiSDy3JrI1btmUQhJc4OTnmqIPB1qAADqKhJztvyfcffOfM+y0ISsNk4+V6k0XHBdaT1tJXqLTsyoQfWmSZsnwpM4WARo5/cQWdAwwsWux"
}
```

**NOTE:** Encrypted fields are always represented as **Base64-encoded strings** which contain both, the **ciphertext of the fields' original values** and authenticated but unencrypted(!) meta-data. If you want to learn about a few more details look [here](#cipher-algorithm-specifics).

#### Decryption of selected fields

Provided that the keyset used to encrypt the original data record is made available to a specific sink connector, the `CipherField` SMT can be configured to decrypt the data as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "DECRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":{<TINK_KEYSET_SPEC_JSON_HERE>}}]", //key materials of utmost secrecy!
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **decrypted using the keyset that was used to encrypt the original data**. Apparently, this can work if and only if the keyset is properly configured.

Below is an exemplary JSON-encoded record after the decryption, which is equal to the original record:

```json5
{
  "id": "1234567890",
  "myString": "some foo bla text",
  "myInt": 42,
  "myBoolean": true,
  "mySubDoc1": {"myString":"hello json"},
  "myArray1": ["str_1","str_2","...","str_N"],
  "mySubDoc2": {"k1":9,"k2":8,"k3":7}
}
```

### Data Records with Schema

The following example is based on an **Avro value record** and used to illustrate a simple encrypt/decrypt scenario for data records with schema. The schema could be defined as:

```json5
{
    "type": "record", "fields": [
        { "name": "id", "type": "string" },
        { "name": "myString", "type": "string" },
        { "name": "myInt", "type": "int" },
        { "name": "myBoolean", "type": "boolean" },
        { "name": "mySubDoc1", "type": "record",
            "fields": [
                { "name": "myString", "type": "string" }
            ]
        },
        { "name": "myArray1", "type": { "type": "array", "items": "string"}},
        { "name": "mySubDoc2", "type": { "type": "map", "values": "int"}}
    ]
}
```

The data of one such fictional record - represented by its `Struct.toString()` output - might look as:

```text
Struct{
  id=1234567890,
  myString=some foo bla text,
  myInt=42,
  myBoolean=true,
  mySubDoc1=Struct{myString=hello json},
  myArray1=[str_1, str_2, ..., str_N],
  mySubDoc2={k1=9, k2=8, k3=7}
}
```

#### Encryption of selected fields

Let's assume the fields `"myString"`,`"myArray1"` and `"mySubDoc2"` of the above data record should get encrypted, the `CipherField` SMT can be configured as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":{<TINK_KEYSET_SPEC_JSON_HERE>}}]", //key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_identifier": "my-demo-secret-key-123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **encrypted using the keyset** configured by its id with the `cipher_data_key_identifier` parameter. The keysets themselves are configured using the parameter `cipher_data_keys` where the key material itself is specified according to a [Tink](https://developers.google.com/tink/) keyset configuration in JSON format (here is a [concrete example](#tink-keysets)). Apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the keyset materials renders encryption useless. The recommended way of doing this for now is to either

- indirectly reference keyset materials by externalizing them into a separate properties file (find a few details [here](#externalize-configuration-parameters))

or

- to NOT store the keyset materials at the client-side in the first place, but instead resolve keysets at runtime from a cloud KMS such as [Azure Key Vault](https://azure.microsoft.com/en-us/services/key-vault/) which is supported as well.

_In general though, this can be considered a "chicken-and-egg" problem since the confidential settings in order to access a remote KMS also need to be store somewhere somehow._

Since the configuration parameter `field_mode` in the configuration above is set to 'OBJECT', complex field types are processed as a whole instead of element-wise, the latter of which can be achieved by choosing `ELEMENT` mode.

Below is an exemplary `Struct.toString()` output of the record after the encryption:

```text
Struct{
  id=1234567890,
  myString=MwpKn9k5V4prVVGvAZdm6iOp8GnVUR7zyT+Ljb+bhcrFaGEx9xSNOpbZaJZ4YeBsJAj7DDCxa7E=,
  myInt=42,
  myBoolean=true,
  mySubDoc1=Struct{myString=hello json},
  myArray1=Ujlij/mbI48akEIZ08q363zOfV+OMJ+ZFewZEMBiaCnk7NuZZH+mfw6HGobtRzvxeavRhTL3lKI1jYPz0CYl7PqS7DJOJtJ1ccKDa5FLAgP0BQwwsWux,
  mySubDoc2=fJxvxo1LX1ceg2/Ba4+vq2NlgyJNiWGZhjWh6rkHQzuG+C7I8lNW8ECLxqJkNhuYuMMlZjK51gAZfID4HEWcMPz026HexzurptZdgkM1fqJMTMIryDKVlAicXc8phZ7gELZCepQWE0XKmQg0UBXr924V46x9I9QwaWUAdgwwsWux
}
```

**NOTE 1:** Encrypted fields are always represented as **Base64-encoded strings** which contain both, the **ciphertext of the fields' original values** and authenticated meta-data (unencrypted!) about the field in question. If you want to learn about a few more details look [here](#cipher-algorithm-specifics).

**NOTE 2:** Obviously, in order to support this **the original schema of the data record is automatically redacted such that any encrypted fields can be stored as strings**, even though the original data types for the fields in question were different ones.

#### Decryption of selected fields

Provided that the keyset used to encrypt the original data record is made available to a specific sink connector, the `CipherField` SMT can be configured to decrypt the data as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "DECRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":{<TINK_KEYSET_SPEC_JSON_HERE>}}]", //key materials of utmost secrecy!
  "transforms.cipher.field_config": "[{\"name\":\"myString\",\"schema\": {\"type\": \"STRING\"}},{\"name\":\"myArray1\",\"schema\": {\"type\": \"ARRAY\",\"valueSchema\": {\"type\": \"STRING\"}}},{\"name\":\"mySubDoc2\",\"schema\": { \"type\": \"MAP\", \"keySchema\": { \"type\": \"STRING\" }, \"valueSchema\": { \"type\": \"INT32\"}}}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

**Take notice of the extended `field_config` parameter settings.** For decryption of schema-aware data, the SMT configuration expects that for each field to decrypt the original schema information is explicitly specified.  This allows to **redact the encrypted record's schema towards a compatible decrypted record's schema upfront,** such that the resulting plaintext field values can be stored in accordance with their original data types.

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **decrypted using the keyset that was used to encrypt the original data**. Apparently, this can work if and only if the keyset is properly configured.

Below is the decrypted data - represented by its `Struct.toString()` output - which is equal to the original record:

```text
Struct{
  id=1234567890,
  myString=some foo bla text,
  myInt=42,
  myBoolean=true,
  mySubDoc1=Struct{myString=hello json},
  myArray1=[str_1, str_2, ..., str_N],
  mySubDoc2={k1=9, k2=8, k3=7}
}
```

## Configuration Parameters

<table class="data-table"><tbody>
<tr>
<th>Name</th>
<th>Description</th>
<th>Type</th>
<th>Default</th>
<th>Valid Values</th>
<th>Importance</th>
</tr>
<tr>
<td>cipher_data_key_identifier</td><td>keyset identifier to be used as default data encryption keyset for all fields which don't refer to a field-specific keyset identifier</td><td>string</td><td>""</td><td>
<ul>
<li>non-empty string if <pre>cipher_mode=ENCRYPT</pre></li>
<li>empty string if <pre>cipher_mode=DECRYPT</pre></li>
</ul>
</td><td>high</td></tr>
<tr>
<td>cipher_data_keys</td><td>JSON array with data key objects specifying the key identifiers together with key sets for encryption / decryption which are defined in Tink's key specification format. The contained keyset objects are mandatory if <pre>kms_type=NONE</pre> but the array may be left empty in order to resolve keysets from a remote KMS such as Azure Key Vault. <pre>kms_type=AZ_KV_SECRETS</pre><strong>Irrespective of their origin, all keysets ("material" fields) are expected to be valid tink keyset descriptions in JSON format which are used for encryption / decryption purposes.</strong></td><td>password</td><td><pre>[]</pre></td><td>JSON array either empty or holding N data key config objects each of which refers to a tink keyset in JSON format, e.g.
<pre>
[
    {
        "identifier": "my-demo-secret-key-123",
        "material": {
            "primaryKeyId": 1234567890,
            "key": [
                {
                    "keyData": {
                        "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
                        "value": "&lt;BASE64_ENCODED_KEY_HERE&gt;",
                        "keyMaterialType": "SYMMETRIC"
                    },
                    "status": "ENABLED",
                    "keyId": 1234567890,
                    "outputPrefixType": "TINK"
                }
            ]
        }
    }
]
</pre></td><td>high</td></tr>
<tr>
<td>cipher_mode</td><td>defines whether the data should get encrypted or decrypted</td><td>string</td><td></td><td>
<pre>
ENCRYPT
DECRYPT
</pre></td><td>high</td></tr>
<tr>
<td>field_config</td><td>JSON array with field config objects specifying which fields together with their settings should get either encrypted / decrypted (nested field names are expected to be <strong>separated by '.' per default</strong>, or by a custom <pre>path_delimiter</pre> config</td><td>string</td><td></td><td>JSON array holding at least one valid field config object, e.g. <pre>
[
    {
        "name": "my-field-abc"
    },
    {
        "name": "my-nested.field-xyz"
    }
]
</pre></td><td>high</td></tr>
<tr>
<td>key_source</td><td>defines the origin of the keysets which can be defined directly in the config or fetched from a remote KMS (see <pre>kms_type</pre> and <pre>kms_config</pre>)</td><td>string</td><td><pre>CONFIG</pre></td><td>
<pre>
CONFIG
KMS
</pre></td><td>medium</td></tr>
<tr>
<td>kms_type</td><td>defines if keysets are read from the config directly or resolved from a remote/cloud KMS (e.g. Azure Key Vault).</td><td>string</td><td><pre>NONE</pre></td><td>
<pre>
NONE
AZ_KV_SECRETS
</pre></td><td>medium</td></tr>
<tr>
<td>kms_config</td><td>JSON object specifying KMS-specific client authentication settings (currently only supports Azure Key Vault). <pre>kms_type=AZ_KV_SECRETS</pre></td><td>string</td><td>{}</td><td>JSON object defining the KMS-specific client authentication settings, e.g. for azure key vault access:
<pre>
{
    "clientId": "...",
    "tenantId": "...",
    "clientSecret": "...",
    "keyVaultUrl": "..."
}
</pre></td><td>medium</td></tr>
<tr>
<td>field_mode</td><td>defines how to process complex field types (maps, lists, structs), either as full objects or element-wise</td><td>string</td><td><pre>ELEMENT</pre></td><td>
<pre>
ELEMENT
OBJECT
</pre>
</td><td>medium</td></tr>
<tr>
<td>cipher_algorithm</td><td>cipher algorithm used for data encryption</td><td>string</td><td><pre>TINK/AES_GCM</pre></td><td>
<pre>
JCE/AES_GCM
TINK/AES_GCM
TINK/AES_GCM_SIV
</pre>
</td><td>medium</td></tr>
<tr>
<td>cipher_text_encoding</td><td>defines the encoding of the resulting ciphertext bytes (currently only supports BASE64)</td><td>string</td><td><pre>BASE64</pre></td><td><pre>BASE64</pre></td><td>low</td></tr>
<tr>
<td>path_delimiter</td><td>path delimiter used as field name separator when referring to nested fields in the input record</td><td>string</td><td><pre>.</pre></td><td>non-empty string</td><td>low</td></tr>
</tbody></table>

### Externalize configuration parameters

The problem with directly specifying configuration parameters which contain sensitive data, such as keyset materials, is that they are exposed via Kafka Connect's REST API. This means for connect clusters that are shared among teams the configured keyset materials would leak, which would be unacceptable. The way to deal with this for now, is to indirectly reference such configuration parameters from external property files.

This approach can be used to configure any kind of sensitive data such keyset materials themselves or KMS-specific client authentication settings, in case the keysets aren't sourced from the config directly but rather retrieved from a cloud KMS such as Azure Key Vault.

Below is a quick example of how such a configuration would look like:

1. Before you can make use of configuration parameters from external sources you have to customize your Kafka Connect worker configuration by adding the following two settings:

```
connect.config.providers=file
connect.config.providers.file.class=org.apache.kafka.common.config.provider.FileConfigProvider
```

2. Then you create the external properties file e.g. `classified.properties` which contains the keyset materials. This file needs to be available on all your Kafka Connect workers which you want to run Kryptonite on. Let's pretend the file is located at path `/secrets/kryptonite/classified.properties` on your worker nodes:

```properties
cipher_data_keys=[{"identifier":"my-demo-secret-key-123","material":{<TINK_KEYSET_SPEC_JSON_HERE>}}]
```

3. Finally, you simply reference this file and the corresponding key of the property therein, from your SMT configuration like so:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "${file:/secrets/kryptonite/classified.properties:cipher_data_keys}",
  "transforms.cipher.cipher_data_key_identifier": "my-demo-secret-key-123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

In case you want to learn more about configuration parameter externalization there is e.g. this nice [blog post](https://debezium.io/blog/2019/12/13/externalized-secrets/) from the Debezium team showing how to externalize username and password settings using a docker-compose example.

### Tink Keysets

Key material is configured in the `cipher_data_keys` property of the `CipherField` SMT which takes an array of JSON objects. The `material` field in one such JSON object represents a keyset and might look as follows:

```json5
{
  "primaryKeyId": 1234567890,
  "key": [
    {
      "keyData": {
        "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value": "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 1234567890,
      "outputPrefixType": "TINK"
    }
  ]
}
```

Note that the JSON snippet above needs to be specified either:

- as single-line JSON object in an [external config file](#externalize-configuration-parameters) (`.properties`)

`... "material": { "primaryKeyId": 1234567890, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "<BASE64_ENCODED_KEY_HERE>", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 1234567890, "outputPrefixType": "TINK" } ] } ...`

or

- as single-line escape/quoted JSON string if included directly within a connector's JSON configuration

`"... \"material\": { \"primaryKeyId\": 1234567890, \"key\": [ { \"keyData\": { \"typeUrl\": \"type.googleapis.com/google.crypto.tink.AesGcmKey\", \"value\": \"<BASE64_ENCODED_KEY_HERE>\", \"keyMaterialType\": \"SYMMETRIC\" }, \"status\": \"ENABLED\", \"keyId\": 1234567890, \"outputPrefixType\": \"TINK\" } ] } ..."`
