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

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **encrypted using the secret key** specified by its id with the `cipher_data_key_identifier` parameter. Currently, all available secret keys have to be directly configured using the parameter `cipher_data_keys`. Apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the secret keys renders encryption useless.

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

**NOTE:** Encrypted fields are always represented as **Base64-encoded strings** which contain both, the **ciphertext of the fields original value** and authenticated but unencrypted(!) meta-data.

#### Decryption of selected fields

Given that the secret key material used to encrypt the original data record is made available to a specific sink connector, the CipherField SMT can be configured to decrypt the data like so:

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

The following is based on an **AVRO value record** and used to illustrate a simple encrypt/decrypt scenario for data records with schema. The schema could be defined as:

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

The result after applying this SMT is a record in which all the fields specified in the `field_config` parameter are **encrypted using the secret key** specified by its id with the `cipher_data_key_identifier` parameter. Currently, all available secret keys have to be directly configured using the parameter `cipher_data_keys`. Apparently, the **configured key materials have to be treated with utmost secrecy**, for leaking any of the secret keys renders encryption useless.

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

**NOTE 1:** Encrypted fields are always represented as **Base64-encoded strings** which contain both, the **ciphertext of the fields original value** and authenticated meta-data (unencrypted!) about the field in question.

**NOTE 2:** Obviously, in order to support this **the original schema of the data record is automatically redacted such that any encrypted fields can be stored as strings**, even though the original data types for the fields in question were different ones.

#### Decryption of selected fields

Given that the secret key material used to encrypt the original data record is made available to a specific sink connector, the CipherField SMT can be configured to decrypt the data like so:

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

### Build, Installation / Deployment

* TODO...

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
