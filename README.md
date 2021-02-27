# Kryptonite - An SMT for Kafka Connect

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

Kryptonite is a turn-key ready [transformation](https://kafka.apache.org/documentation/#connect_transforms) (SMT) for [Apache Kafka®](https://kafka.apache.org/) to do field-level encryption/decryption of records with or without schema in data integration scenarios based on [Kafka Connect](https://kafka.apache.org/documentation/#connect). It uses authenticated encryption with associated data ([AEAD](https://en.wikipedia.org/wiki/Authenticated_encryption)) and in particular applies [AES](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard) in [GCM](https://en.wikipedia.org/wiki/Galois/Counter_Mode) mode.

## tl;dr

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

Let's assume the fields `"myString"`,`"myArray1"` and `"mySubDoc2"` of the above data record should get encrypted, the CipherField SMT can be configured as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"}]", //key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_identifier": "my-demo-secret-key-123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **encrypted using the secret key** specified by its id with the `cipher_data_key_identifier` parameter. Currently, the secret keys to work with have to be configured using the parameter `cipher_data_keys`. Apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the secret keys renders encryption useless. The recommended way of doing this for now is to indirectly reference secret key materials by externalizing them into a separate properties file. Read a few details about this [here](#externalize-configuration-parameters).

Since the configuration parameter `field_mode` is set to 'OBJECT', complex field types are processed as a whole instead of element-wise. 

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

**NOTE:** Encrypted fields are always represented as **Base64-encoded strings** which contain both, the **ciphertext of the fields original value** and authenticated but unencrypted(!) meta-data. If you want to learn about a few more details look [here](#cipher-algorithm-specific).

#### Decryption of selected fields

Provided that the secret key material used to encrypt the original data record is made available to a specific sink connector, the CipherField SMT can be configured to decrypt the data like so:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "DECRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"}]", //key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_identifier": "my-demo-secret-key-123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **decrypted using the secret key id that is specified and was used to encrypt the original data**. Apparently, this can work if and only if the secret key id has the correct key material configured using the parameter `cipher_data_keys`.

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

The following is based on an **Avro value record** and used to illustrate a simple encrypt/decrypt scenario for data records with schema. The schema could be defined as:

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

Let's assume the fields `"myString"`,`"myArray1"` and `"mySubDoc2"` of the above data record should get encrypted, the CipherField SMT can be configured as follows:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "ENCRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"}]", //key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_identifier": "my-demo-secret-key-123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\"},{\"name\":\"myArray1\"},{\"name\":\"mySubDoc2\"}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **encrypted using the secret key** specified by its id with the `cipher_data_key_identifier` parameter. Currently, all available secret keys have to be directly configured using the parameter `cipher_data_keys`. Apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the secret keys renders encryption useless. The recommended way of doing this for now is to indirectly reference secret key materials by externalizing them into a separate properties file. Read a few details about this [here](#externalize-configuration-parameters).

Since the configuration parameter `field_mode` is set to 'OBJECT', complex field types are processed as a whole instead of element-wise.

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

**NOTE 1:** Encrypted fields are always represented as **Base64-encoded strings** which contain both, the **ciphertext of the fields original value** and authenticated meta-data (unencrypted!) about the field in question. If you want to learn about a few more details look [here](#cipher-algorithm-specific).

**NOTE 2:** Obviously, in order to support this **the original schema of the data record is automatically redacted such that any encrypted fields can be stored as strings**, even though the original data types for the fields in question were different ones.

#### Decryption of selected fields

Provided that the secret key material used to encrypt the original data record is made available to a specific sink connector, the CipherField SMT can be configured to decrypt the data like so:

```json5
{
  //...
  "transforms":"cipher",
  "transforms.cipher.type":"com.github.hpgrahsl.kafka.connect.transforms.kryptonite.CipherField$Value",
  "transforms.cipher.cipher_mode": "DECRYPT",
  "transforms.cipher.cipher_data_keys": "[{\"identifier\":\"my-demo-secret-key-123\",\"material\":\"0bpRAigAvP9fTTFw43goyg==\"}]", //key materials of utmost secrecy!
  "transforms.cipher.cipher_data_key_identifier": "my-demo-secret-key-123",
  "transforms.cipher.field_config": "[{\"name\":\"myString\",\"schema\": {\"type\": \"STRING\"}},{\"name\":\"myArray1\",\"schema\": {\"type\": \"ARRAY\",\"valueSchema\": {\"type\": \"STRING\"}}},{\"name\":\"mySubDoc2\",\"schema\": { \"type\": \"MAP\", \"keySchema\": { \"type\": \"STRING\" }, \"valueSchema\": { \"type\": \"INT32\"}}}]",
  "transforms.cipher.field_mode": "OBJECT",
  //...
}
```

**Take notice of the extended `field_config` parameter settings.** For decryption of schema-aware data, the SMT configuration expects that for each field to decrypt the original schema information is explicitly specified.  This allows to **redact the encrypted record's schema towards a compatible decrypted record's schema upfront,** such that the resulting plaintext field values can be stored in accordance with their original data types.   

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **decrypted using the secret key id that is specified and was used to encrypt the original data**. Apparently, this can work if and only if the secret key id has the correct key material configured using the parameter `cipher_data_keys`.

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
<td>cipher_data_key_identifier</td><td>secret key identifier to be used as default data encryption key for all fields which don't refer to a field-specific secret key identifier</td><td>string</td><td></td><td>non-empty string</td><td>high</td></tr>
<tr>
<td>cipher_data_keys</td><td>JSON array with data key objects specifying the key identifiers and base64 encoded key bytes used for encryption / decryption</td><td>password</td><td></td><td>JSON array holding at least one valid data key config object, e.g. [{"identifier":"my-key-id-1234-abcd","material":"dmVyeS1zZWNyZXQta2V5JA=="}]</td><td>high</td></tr>
<tr>
<td>cipher_mode</td><td>defines whether the data should get encrypted or decrypted</td><td>string</td><td></td><td>ENCRYPT or DECRYPT</td><td>high</td></tr>
<tr>
<td>field_config</td><td>JSON array with field config objects specifying which fields together with their settings should get either encrypted / decrypted (nested field names are expected to be separated by '.' per default, or by a custom 'path_delimiter' config</td><td>string</td><td></td><td>JSON array holding at least one valid field config object, e.g. [{"name": "my-field-abc"},{"name": "my-nested.field-xyz"}]</td><td>high</td></tr>
<tr>
<td>field_mode</td><td>defines how to process complex field types (maps, lists, structs), either as full objects or element-wise</td><td>string</td><td>ELEMENT</td><td>ELEMENT or OBJECT</td><td>medium</td></tr>
<tr>
<td>cipher_algorithm</td><td>cipher algorithm used for data encryption (currently supports only one AEAD cipher: AES/GCM/NoPadding)</td><td>string</td><td>AES/GCM/NoPadding</td><td>AES/GCM/NoPadding</td><td>low</td></tr>
<tr>
<td>cipher_text_encoding</td><td>defines the encoding of the resulting ciphertext bytes (currently only supports 'base64')</td><td>string</td><td>base64</td><td>base64</td><td>low</td></tr>
<tr>
<td>path_delimiter</td><td>path delimiter used as field name separator when referring to nested fields in the input record</td><td>string</td><td>.</td><td>non-empty string</td><td>low</td></tr>
</tbody></table>

### Externalize configuration parameters

The problem with directly specifying configuration parameters which contain sensitive data, such as secret key materials, is that they are exposed via Kafka Connect's REST API. This means for connect clusters that are shared among teams the configured secret key materials would leak, which is of course unacceptable. The way to deal with this for now, is to indirectly reference such configuration parameters from external property files.

Below is a quick example of how such a configuration would look like:

1. Before you can make use of configuration parameters from external sources you have customize your Kafka Connect worker configuration by adding the following two settings:

```
connect.config.providers=file
connect.config.providers.file.class=org.apache.kafka.common.config.provider.FileConfigProvider
```

2. Then you create the external properties file e.g. `classified.properties` which contains the secret key materials. This file needs to be available on all your Kafka Connect workers which you want to run Kryptonite on. Let's pretend the file is located at path `/secrets/kryptonite/classified.properties` your worker nodes:

```properties
cipher_data_keys=[{"identifier":"my-demo-secret-key-123","material":"0bpRAigAvP9fTTFw43goyg=="}]
```

3. Finally, you simply reference this file and contained `key=value` therein, from the SMT configuration like so:

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

### Build, Installation / Deployment

Either you can build this project from sources via Maven or you can download a pre-built, self-contained package of Kryptonite [kafka-connect-transform-kryptonite-0.1.0.jar](https://drive.google.com/file/d/1T-QUKzCoRi_YHSVcLBxMWPm_WGBvlo46/view?usp=sharing).

In order to deploy it you simply put the jar into a _'plugin path'_ that is configured to be scanned by your Kafka Connect worker nodes.

After that, simply configure Kryptonite as transformation for any of your source / sink connectors, sit back and relax! Happy 'binge watching' ciphertext ;-)

### Cipher Algorithm Specific

Kryptonite currently provides a single cipher algorithm, namely, AES in GCM mode. It offers so-called _authentic encryption with associated data_ (AEAD). This basically means that besides the ciphertext, an encrypted field additionally contains unencrypted but authenticated meta-data. In order to keep the storage overhead per encrypted field down to a minimum, the SMT implementation only incorporates a version identifier for Kryptonite itself (`k1`) together with a short identifier representing the algorithm (`01` for `AES/GCM/NoPadding`) which was used to encrypt the field in question. Future versions may support additional algorithms or might benefit from further meta-data, so this should be considered to undergo changes.

By design, every application of Kryptonite on a specific record field results in different ciphertexts for one and the same plaintext. This is in general not only desirable but very important to make attacks harder. However, in the context of Kafka Connect records this has an unfavorable consequence for source connectors. **Applying the SMT on a source record's key would result in a 'partition mix-up'** because records with the same original plaintext key would end up in different topic partitions. In other words, **do NOT(!) use Kryptonite for source record keys** at the moment. There are plans in place to do away with this restriction and extend Kryptonite with a deterministic mode which could then safely support the encryption of record keys while at the same time keep topic partitioning and record ordering intact.

## Donate
If you like this project and want to support its further development and maintenance we are happy about your [PayPal donation](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

## License Information
This project is licensed according to [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)

```
Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
