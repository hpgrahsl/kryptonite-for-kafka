# Kryptonite for Kafka: Client-Side 🔒 Field-Level 🔓 Cryptography for Apache Kafka®

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

## HTTP API based on Quarkus Funqy

Kryptonite for Kafka provides a lightweight [Quarkus](https://quarkus.io/)-based standalone service exposing an HTTP API implemented with [Funqy](https://quarkus.io/guides/funqy). It's primary use case is to allow client applications, written in languages / runtimes other than Java / JVM, to participate in end-to-end encryption scenarios build on top of Kafka. Learn how to build, configure, and run the HTTP API service to encrypt and decrypt payload fields based on simple usage examples.

### Build

Either you build this project from sources via Maven or you can download pre-built, self-contained packages of the latest artefacts. Starting with Kryptonite for Kafka 0.4.0, the pre-built Funqy HTTP API service can be downloaded directly from the [release pages](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).

### Configuration

Before running the Quarkus application make sure to specify your individual configuration options in `application.properties` when you build form sources. In case you run the pre-built binaries, you have to properly override any of the mandatory/default settings when starting the application.

The default `application.properties` file which ships with the sources and pre-built binaries looks as follows:

```properties
#############################################
# Kryptonite for Kafka HTTP API configuration
#############################################
#
# MANDATORY config settings
#
cipher.data.keys=[]
cipher.data.key.identifier=
#
# OPTIONAL config settings with the following defaults
#
key.source=CONFIG
kms.type=NONE
kms.config={}
kek.type=NONE
kek.config={}
kek.uri=gcp-kms://
dynamic.key.id.prefix=__#
path.delimiter=.
field.mode=ELEMENT
cipher.algorithm=TINK/AES_GCM
cipher.fpe.tweak=0000000
cipher.fpe.alphabet.type=ALPHANUMERIC
cipher.fpe.alphabet.custom=
#############################################
```

As can be seen from the comments, the first two properties (`cipher.data.keys=[]` and `cipher.data.key.identifier=`) are **mandatory**. All other properties are _either optional_ or are specified with _reasonable defaults_. For a detailed explanation of each configuration option and possible / valid values take a closer look at the table below:

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Description</th>
            <th>Type</th>
            <th>Default</th>
            <th>Valid Values</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td>cipher.data.keys</td>
            <td>JSON array with plain or encrypted data key objects specifying the key identifiers together with key
                sets for encryption / decryption which are defined in Tink's key specification format. The contained
                keyset objects are mandatory if
                <code>kms.type=NONE</code> but the array may be left empty for e.g. <code>kms.type=AZ_KV_SECRETS</code> in order to resolve keysets from a remote KMS such as Azure Key Vault.
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
        </tr>
        <tr>
            <td>cipher.data.key.identifier</td>
            <td>keyset identifier to be used as default data encryption keyset for all UDF calls which don't refer to a
                specific keyset identifier in the parameter list</td>
            <td>string</td>
            <td><pre>!no default!</pre></td>
            <td>non-empty string referring to an existing identifier for a keyset</td>
        <tr>
            <td>key.source</td>
            <td>defines the nature and origin of the keysets:
            <ul>
                <li>plain data keysets in <code>cipher_data_keys (key.source=CONFIG)</code></li>
                <li>encrypted data keysets in <code>cipher_data_keys (key.source=CONFIG_ENCRYPTED)</code></li>
                <li>plain data keysets residing in a cloud/remote key management system <code>(key.source=KMS)</code></li>
                <li>encrypted data keysets residing in a cloud/remote key management system <code>(key.source=KMS_ENCRYPTED)</code></li>
            </ul>
                When using the KMS options refer to the <code>kms.type</code> and <code>kms.config</code> settings. When using encrypted data
                keysets refer to the <code>kek_type</code>, <code>kek.config</code> and <code>kek.uri</code> settings as well.
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
        </tr>
        <tr>
            <td>kms.type</td>
            <td>defines if:
                <ul>
                <li>data keysets are read from the config directly <code>kms.source=CONFIG | CONFIG_ENCRYPTED</code></li>
                <li>data keysets are resolved from a remote/cloud key management system (currently only supports Azure Key Vault) <code>kms.source=KMS | KMS_ENCRYPTED</code>
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
        </tr>
        <tr>
            <td>kek.uri</td>
            <td>URI referring to the key encryption key stored in the respective remote/cloud KMS, currently only support Google Cloud KMS</td>
            <td>string</td>
            <td><pre>!no default!</pre></td>
            <td>a valid and supported Tink key encryption key URI, e.g. pointing to a key in Google Cloud KMS (<code>kek_type=GCP</code>)
            <pre>gcp-kms://...</pre>
            </td>
        </tr>
        <tr>
            <td>cipher.algorithm</td>
            <td>default cipher algorithm used for data encryption if not specified in the field-specific configuration</td>
            <td>string</td>
            <td><pre>TINK/AES_GCM</pre></td>
            <td>
                <pre>TINK/AES_GCM</pre>
                <pre>TINK/AES_GCM_SIV</pre>
                <pre>CUSTOM/MYSTO_FPE_FF3_1</pre>
            </td>
        </tr>
        <tr>
            <td>cipher.fpe.tweak</td>
            <td>default tweak value for Format Preserving Encryption (FPE) if not specified in field-specific configuration. The tweak provides additional cryptographic variation - different tweaks produce different ciphertexts for the same plaintext.</td>
            <td>string</td>
            <td><pre>0000000</pre></td>
            <td>any 7 character string value</td>
        </tr>
        <tr>
            <td>cipher.fpe.alphabet.type</td>
            <td>default alphabet type for Format Preserving Encryption (FPE) if not specified in field-specific configuration. Defines the character set used for encryption - ciphertext will only contain characters from this alphabet.</td>
            <td>string</td>
            <td><pre>ALPHANUMERIC</pre></td>
            <td>
                <pre>DIGITS</pre>
                <pre>UPPERCASE</pre>
                <pre>LOWERCASE</pre>
                <pre>ALPHANUMERIC</pre>
                <pre>ALPHANUMERIC_EXTENDED</pre>
                <pre>HEXADECIMAL</pre>
                <pre>CUSTOM</pre>
            </td>
        </tr>
        <tr>
            <td>cipher.fpe.alphabet.custom</td>
            <td>custom alphabet for Format Preserving Encryption (FPE) when <code>cipher.fpe.alphabet.type=CUSTOM</code>. Specifies the exact set of characters to use for encryption (e.g., "01" for binary, "0123456789ABCDEF" for hexadecimal).</td>
            <td>string</td>
            <td><pre></pre></td>
            <td>any non-empty string defining a custom character set (minimum 2 unique characters)</td>
        </tr>
    </tbody>
</table>

### Format Preserving Encryption (FPE)

Starting with version 0.6.0, the Kryptonite HTTP API supports **Format Preserving Encryption (FPE)** using the FF3-1 algorithm. Unlike standard AEAD encryption (AES-GCM/AES-GCM-SIV) which produces variable-length ciphertext, FPE maintains the original format and length of the plaintext data.

#### Key Characteristics of FPE

- **Format Preservation**: Encrypted data maintains the same format and length as the original plaintext
- **Character Set Preservation**: The ciphertext uses the same character set (alphabet) as the plaintext
- **Use Cases**: Ideal for scenarios where encrypted data must conform to specific formats, such as:
  - Credit card numbers (CCN)
  - Social security numbers (SSN)
  - Phone numbers
  - Postal codes
  - Application fields with strict format constraints

#### FPE Configuration

To use FPE via the HTTP API, you can specify FPE-specific parameters in the `fieldConfig` array when calling the `/encrypt/value-with-config` or `/decrypt/value-with-config` endpoints:

- **algorithm**: Set to `CUSTOM/MYSTO_FPE_FF3_1` (required for FPE)
- **fpeAlphabetType**: Specifies the character set for encryption (required for FPE)
- **fpeTweak** (optional): A tweak value for additional cryptographic variation (uses default if not specified)
- **fpeAlphabetCustom** (optional): Required only when `fpeAlphabetType=CUSTOM`

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

#### FPE HTTP API Example

**Encryption Request** to `/encrypt/value-with-config`:

```json
{
  "data": {
    "customerId": "CUST-12345",
    "creditCardNumber": "4455202014528870",
    "ssn": "230564998"
  },
  "fieldConfig": [
    {
      "name": "creditCardNumber",
      "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
      "keyId": "myFpeKey",
      "fpeAlphabetType": "DIGITS"
    },
    {
      "name": "ssn",
      "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
      "keyId": "myFpeKey",
      "fpeAlphabetType": "DIGITS"
    }
  ]
}
```

**Encryption Response**:

```json
{
    "customerId": "CUST-12345",
    "creditCardNumber": "0472659418391244",
    "ssn": "348538193"
}
```

Notice how the encrypted fields maintain their original format and length!

**Decryption Request** to `/decrypt/value-with-config`:

```json
{
  "data": {
    "customerId": "CUST-12345",
    "creditCardNumber": "0472659418391244",
    "ssn": "348538193"
  },
  "fieldConfig": [
    {
      "name": "creditCardNumber",
      "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
      "keyId": "myFpeKey",
      "fpeAlphabetType": "DIGITS"
    },
    {
      "name": "ssn",
      "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
      "keyId": "myFpeKey",
      "fpeAlphabetType": "DIGITS"
    }
  ]
}
```

**Decryption Response**:

```json
{
    "customerId": "CUST-12345",
    "creditCardNumber": "4455202014528870",
    "ssn": "230564998"
}
```

#### FPE Keyset Configuration

FPE requires special keyset material with a custom type URL. Add FPE keysets to your `cipher.data.keys` configuration:

```json
[
  {
    "identifier": "myFpeKey",
    "material": {
      "primaryKeyId": 2000001,
      "key": [
        {
          "keyData": {
            "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey",
            "value": "<BASE64_ENCODED_FPE_KEY_HERE>",
            "keyMaterialType": "SYMMETRIC"
          },
          "status": "ENABLED",
          "keyId": 2000001,
          "outputPrefixType": "RAW"
        }
      ]
    }
  }
]
```

**Key differences from standard AEAD keysets:**
- `typeUrl`: Must be `io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey`
- `outputPrefixType`: Should be `RAW` (not `TINK`)

#### FPE Considerations

- **Minimum Length**: FPE requires input data to meet minimum length requirements based on the alphabet size
- **Consistent Configuration**: The same `fpeAlphabetType`, `fpeAlphabetCustom`, and `fpeTweak` must be used for both encryption and decryption
- **Security vs Format**: While FPE preserves format, it may offer slightly different security properties compared to standard AEAD encryption
- **Tweak Parameter**: The optional `fpeTweak` adds cryptographic variation, allowing different ciphertext for the same plaintext when different tweaks are used

### OpenAPI specification

The HTTP service supports endpoints to encrypt and decrypt HTTP payloads or fields thereof. After configuring and launching the Quarkus application in `dev mode` (maven: `./mvnw quarkus:dev` | quarkus cli: `quarkus dev`) you can directly access the [Swagger UI](http://localhost:8080/q/swagger-ui/) to experiments with the exposed endpoints. This is the underlying openAPI spec:

```yaml
openapi: 3.0.3
info:
  title: Kryptonite for Kafka's HTTP API
  description: Open API spec for the Cipher Field Resource based on Quarkus Funqy
  version: "0.1.0"
  license:
    name: Apache 2.0
    url: http://www.apache.org/licenses/LICENSE-2.0.html

servers:
  - url: http://localhost:8080
    description: (dev mode server)
tags:
  - name: encryption
    description: endpoints for encrypting payloads or fields thereof
  - name: decryption
    description: endpoints for decrypting payloads or fields thereof
paths:
  /encrypt/value:
    post:
      tags:
        - encryption
      requestBody:
        description: The request body may contain any value representing a valid JSON type (string, number, boolean, array, object, null).
        content:
          application/json:
            schema:
              oneOf:
                - type: string
                - type: number
                - type: boolean
                - type: array
                  items:
                    type: object
                - type: object
                  nullable: true
      responses:
        200:
          description: The Base64 encoded ciphertext using the `CipherFieldResource` encryption settings as stated in `application.properties`.
          content:
            application/json:
              schema:
                type: string

  /encrypt/array-elements:
    post:
      tags:
        - encryption
      requestBody:
        description: The request body may contain a JSON array containing elements with any valid JSON type (string, number, boolean, array, object, null).
        content:
          application/json:
            schema:
              type: array
              items:
                type: object
                nullable: true
      responses:
        200:
          description: The JSON array containing the Base64 encoded ciphertexts of all elements using the `CipherFieldResource` encryption settings as stated in `application.properties`.
          content:
            application/json:
              schema:
                type: array
                items:
                  type: string

  /encrypt/map-entries:
    post:
      tags:
        - encryption
      requestBody:
        description: The request body may contain a JSON object containing properties with any valid JSON type (string, number, boolean, array, object, null).
        content:
          application/json:
            schema:
              type: object
      responses:
        200:
          description: The JSON object containing the Base64 encoded ciphertexts of all properties using the `CipherFieldResource` encryption settings as stated in `application.properties`.
          content:
            application/json:
              schema:
                type: object

  /encrypt/value-with-config:
    post:
      tags:
        - encryption
      requestBody:
        description: The request body may contain a JSON object containing a `data` property of type JSON object which itself may contain properties of any valid JSON type (string, number, boolean, array, object, null). Additionally, the JSON object can define an optional `fieldConfig` property, which is an array containing individual configuration to apply specific encryption settings for particular properties found in the `data` property.
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: object
                fieldConfig:
                  type: array
                  items:
                    $ref: '#/components/schemas/FieldConfig'      
      responses:
        200:
          description: The JSON object containing the Base64 encoded ciphertexts of all properties using the settings as provided in the `fieldConfig` array of the request body and/or the `CipherFieldResource` encryption settings as stated in `application.properties`.
          content:
            application/json:
              schema:
                type: object

  /decrypt/value:
    post:
      tags:
        - decryption
      requestBody:
        description: The request body must contain a valid Base64 encoded string representing the ciphertext of an encrypted value.
        content:
          application/json:
            schema:
              type: string
      responses:
        200:
          description: The response body may contain any valid JSON type (string, number, boolean, array, object, null) as the result of a successful decryption.
          content:
            application/json:
              schema:
                oneOf:
                  - type: string
                  - type: number
                  - type: boolean
                  - type: array
                    items:
                      type: object
                  - type: object
                    nullable: true

  /decrypt/array-elements:
    post:
      tags:
        - decryption
      requestBody:
        description: The request body may contain a JSON array containing elements of type string. Each element is expected to be a valid Base64 encoded string representing the ciphertext of an encrypted value.
        content:
          application/json:
            schema:
              type: array
              items:
                type: string
                nullable: true
      responses:
        200:
          description: The JSON array containing elements of any valid JSON type (string, number, boolean, array, object, null) as the result of a successful decryption.
          content:
            application/json:
              schema:
                type: array
                items:
                  type: object
                  nullable: true

  /decrypt/map-entries:
    post:
      tags:
        - decryption
      requestBody:
        description: The request body may contain a JSON object containing properties of type string. Each property is expected to be a valid Base64 encoded string representing the ciphertext of an encrypted value.
        content:
          application/json:
            schema:
              type: object
      responses:
        200:
          description: The JSON object containing properties of any valid JSON type (string, number, boolean, array, object, null) as the result of a successful decryption.
          content:
            application/json:
              schema:
                type: object

  /decrypt/value-with-config:
    post:
      tags:
        - decryption
      requestBody:
        description: The request body may contain a JSON object containing a `data` property of type JSON object which itself may contain properties of any valid JSON type (string, number, boolean, array, object, null). Additionally, the JSON object can define an optional `fieldConfig` property, which is an array containing individual configuration to apply specific decryption settings for particular properties found in the `data` property.
        content:
          application/json:
            schema:
              type: object
              properties:
                data:
                  type: object
                fieldConfig:
                  type: array
                  items:
                    $ref: '#/components/schemas/FieldConfig'      
      responses:
        200:
          description: The JSON object containing the properties of any valid JSON type (string, number, boolean, array, object, null) as the result of a successful decryption operation using the settings as provided in the `fieldConfig` array of the request body.
          content:
            application/json:
              schema:
                type: object

components:
  schemas:
    FieldConfig:
      properties:
        name:
          type: string
        algorithm:
          type: string
          enum: [TINK/AES_GCM, TINK/AES_GCM_SIV, CUSTOM/MYSTO_FPE_FF3_1]
        keyId:
          type: string
        schema:
          type: object
          nullable: true
        fieldMode:
          type: string
          enum: [OBJECT, ELEMENT]
        fpeTweak:
          type: string
          description: Tweak value for Format Preserving Encryption (optional)
        fpeAlphabetType:
          type: string
          enum: [DIGITS, UPPERCASE, LOWERCASE, ALPHANUMERIC, ALPHANUMERIC_EXTENDED, HEXADECIMAL, CUSTOM]
          description: Alphabet type for Format Preserving Encryption (required when using FPE)
        fpeAlphabetCustom:
          type: string
          description: Custom alphabet for Format Preserving Encryption (required when fpeAlphabetType=CUSTOM)
```

### HTTP API Usage Examples:

The example requests are using a demo configuration as `application.properties`: 

```properties
#############################################
# Kryptonite for Kafka HTTP API configuration
#############################################
#
# MANDATORY config settings
cipher.data.keys=[ { "identifier": "keyA", "material": { "primaryKeyId": 1000000001, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "GhDRulECKAC8/19NMXDjeCjK", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 1000000001, "outputPrefixType": "TINK" } ] } }, { "identifier": "keyB", "material": { "primaryKeyId": 1000000002, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey", "value": "GiBIZWxsbyFXb3JsZEZVQ0sxYWJjZGprbCQxMjM0NTY3OA==", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 1000000002, "outputPrefixType": "TINK" } ] } }, { "identifier": "key9", "material": { "primaryKeyId": 1000000003, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey", "value": "EkByiHi3H9shy2FO5UWgStNMmgqF629esenhnm0wZZArUkEU1/9l9J3ajJQI0GxDwzM1WFZK587W0xVB8KK4dqnz", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 1000000003, "outputPrefixType": "TINK" } ] } }, { "identifier": "key8", "material": { "primaryKeyId": 1000000004, "key": [ { "keyData": { "typeUrl": "type.googleapis.com/google.crypto.tink.AesSivKey", "value": "EkBWT3ZL7DmAN91erW3xAzMFDWMaQx34Su3VlaMiTWzjVDbKsH3IRr2HQFnaMvvVz2RH/+eYXn3zvAzWJbReCto/", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 1000000004, "outputPrefixType": "TINK" } ] } }, { "identifier": "keyC", "material": { "primaryKeyId": 2000001, "key": [ { "keyData": { "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey", "value": "VU5O0VBE6+bIygj2z/BiVg==", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 2000001, "outputPrefixType": "RAW" } ] } }, { "identifier": "keyD", "material": { "primaryKeyId": 2000002, "key": [ { "keyData": { "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey", "value": "GA0CtxRfjqN/9tW4CmnzY+SU9k5EbBJ4", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 2000002, "outputPrefixType": "RAW" } ] } }, { "identifier": "keyE", "material": { "primaryKeyId": 2000003, "key": [ { "keyData": { "typeUrl": "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey", "value": "vJDWFED3R04F6blW1FxZMg/JF8qSfY5+WJLPjSYeW9w=", "keyMaterialType": "SYMMETRIC" }, "status": "ENABLED", "keyId": 2000003, "outputPrefixType": "RAW" } ] } } ]
cipher.data.key.identifier=keyA
#
# OPTIONAL config settings with the following defaults
key.source=CONFIG
kms.type=NONE
kms.config={}
kek.type=NONE
kek.config={}
kek.uri=gcp-kms://
dynamic.key.id.prefix=__#
path.delimiter=.
field.mode=ELEMENT
cipher.algorithm=TINK/AES_GCM
cipher.fpe.tweak=0000000
cipher.fpe.alphabet.type=ALPHANUMERIC
cipher.fpe.alphabet.custom=
#############################################
```

**NOTE: The contained secret keys in this `application.properties` file are used for tests and public demos in various different places and are thus compromised by definition. Never use these secret keys to protect sensitive real world data in any of your production workloads!**

An exported Postman collection can be found in [this file](src/main/resources/META-INF/funqy-kryptonite-http-api-samples.postman_collection.json) `src/main/resources/META-INF/funqy-kryptonite-http-api-samples.postman_collection.json` which contains 16 example requests to play with. Additionally, the same example requests are also available as [publicly shared Postman workspace collection](https://www.postman.com/hpgrahsl/workspace/kryptonite-for-kafka-http-api-public-samples/collection/25347096-89fc9ca4-c6fb-4925-afab-9a2f469c75bd?ctx=documentation). You can fork them to conveniently run the requests in your local environment via the Postman desktop app by clicking the button below:

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/25347096-89fc9ca4-c6fb-4925-afab-9a2f469c75bd?action=collection%2Ffork&collection-url=entityId%3D25347096-89fc9ca4-c6fb-4925-afab-9a2f469c75bd%26entityType%3Dcollection%26workspaceId%3Ddd103435-bfac-4fc3-aace-daaac567434c)
