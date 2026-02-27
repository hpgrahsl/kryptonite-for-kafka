# :material-api: Quarkus HTTP Service

The Quarkus Funqy HTTP API is a lightweight standalone service that exposes encryption and decryption over HTTP. Its primary purpose is to allow client applications written in languages other than Java to participate in end-to-end encryption scenarios built on top of Kafka.

**Field-Level Encryption with HTTP API**

<div class="k4k-module-img">
<a href="../../assets/images/06a_csflc_quarkus_funqy_encryption.png" class="glightbox" data-glightbox="type: image"><img src="../../assets/images/06a_csflc_quarkus_funqy_encryption.png" alt="Quarkus HTTP API"></a>
</div>

**Field-Level Decryption with HTTP API**

<div class="k4k-module-img">
<a href="../../assets/images/06b_csflc_quarkus_funqy_decryption.png" class="glightbox" data-glightbox="type: image"><img src="../../assets/images/06b_csflc_quarkus_funqy_decryption.png" alt="Quarkus HTTP API"></a>
</div>

## Installation / Deployment

1. Download the ZIP archive from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).
2. Extract the archive 

If building from sources:

```bash
./mvnw clean package -DskipTests -pl funqy-http-kryptonite
```

---

## Running the Service

### Pre-built container image

The most convenient way is to pull the [pre-built container image](https://hub.docker.com/r/hpgrahsl/funqy-http-kryptonite/tags).

```bash
K4K_FUNQY_HTTP_IMAGE_TAG='0.3.0'
docker pull hpgrahsl/funqy-http-kryptonite:$K4K_FUNQY_HTTP_IMAGE_TAG
```

!!! warning "Mandatory Configuration"
    The default `application.properties` file which ships with the pre-built container images requires you to override the two mandatory config settings `cipher_data_keys` and `cipher_data_key_identifier` by means of environment variables.

Start a container with your container runtime of choice (Docker or Podman) and specify the mandatory environment variable overrides for `cipher_data_keys` and `cipher_data_key_identifier`.

```bash
K4K_FUNQY_HTTP_IMAGE_TAG='0.3.0'
docker run --name funqy-http-kryptonite \
  -p 8080:8080 \
  -e cipher_data_keys='[<YOUR_TINK_KEYSETS HERE>]' \
  -e cipher_data_key_identifier=yourKey \
  hpgrahsl/funqy-http-kryptonite:$K4K_FUNQY_HTTP_IMAGE_TAG
```

### Production mode

!!! warning "Mandatory Configuration"
    The default `application.properties` file which ships with the pre-built application binaries requires you to override the two mandatory config settings `cipher_data_keys` and `cipher_data_key_identifier`. You can do that by specifying overrides either with system properties or environment variables:

To run the Quarkus application in production mode:

```bash
CUSTOM_JVM_ARGS=(
  --add-opens java.base/java.util=ALL-UNNAMED
)
java "${CUSTOM_JVM_ARGS[@]}" \
  -Dcipher.data.keys='[<YOUR_TINK_KEYSETS HERE>]' \
  -Dcipher.data.key.identifier=yourKey \
  -jar <PATH_TO_APP>/quarkus-app/quarkus-run.jar
```

### Development mode

!!! warning "Mandatory Configuration"
    The default `application.properties` file which ships with the sources requires you to set the two mandatory config settings `cipher_data_keys` and `cipher_data_key_identifier`.

Before running the Quarkus application in dev mode make sure to specify your individual configuration options in `src/main/resources/application.properties`.

Then navigate into the `funqy-http-kryptonite` module folder and run:

```bash
./mvnw quarkus:dev
```

If you have the Quarkus CLI installed in your environment you could alternatively run this command from within the `funqy-http-kryptonite` module folder:

```bash
quarkus dev
```

---

## Configuration

The default `application.properties` file that ship with the `funqy-http-kryptonite` module is this:

```properties
#############################################
# Kryptonite for Kafka HTTP API configuration
#############################################
#
# MANDATORY config settings
#
cipher_data_keys=[]
cipher_data_key_identifier=
#
# OPTIONAL config settings with the following defaults
#
cipher_text_encoding=BASE64
cipher_fpe_tweak=0000000
cipher_fpe_alphabet_type=ALPHANUMERIC
cipher_fpe_alphabet_custom=
key_source=CONFIG
kms_type=NONE
kms_config={}
kek_type=NONE
kek_config={}
kek_uri=gcp-kms://
dynamic_key_id_prefix=__#
path_delimiter=.
field_mode=ELEMENT
cipher_algorithm=TINK/AES_GCM
#############################################
```

See the full [Configuration Reference](../configuration.md) for learn about all parameters.

---

## API Endpoints

### Encryption

The service exposes the following API endpoints to encrypt different JSON inputs:

| Endpoint | Input | Output |
|---|---|---|
| `POST /encrypt/value` | any JSON value | Base64-encoded ciphertext |
| `POST /encrypt/array-elements` | any JSON array | JSON array with Base64-encoded ciphertext strings |
| `POST /encrypt/map-entries` | any JSON object | JSON object with Base64-encoded ciphertext values |
| `POST /encrypt/value-with-config` | `{data: {<JSON_INPUT_DATA>}, fieldConfig: [...]}` | JSON object with encrypted fields according to the [field config](#fieldconfig-object) specified |

### Decryption

The service exposes the following API endpoints to encrypt different JSON inputs:

| Endpoint | Input | Output |
|---|---|---|
| `POST /decrypt/value` | Base64 ciphertext string | original JSON plaintext value |
| `POST /decrypt/array-elements` | JSON array with Base64-encoded ciphertext strings | JSON array with original plaintext values |
| `POST /decrypt/map-entries` | JSON object with Base64-encoded ciphertext values | JSON object with original plaintext values |
| `POST /decrypt/value-with-config` | `{data: {<JSON_INPUT_DATA}, fieldConfig: [...]}` | JSON object with decrypted fields according to the [field config](#fieldconfig-object) specified |

When running the application in dev mode the Swagger UI is available at `http://localhost:8080/q/swagger-ui/`

### `fieldConfig` Object

Used in `/encrypt/value-with-config` and `/decrypt/value-with-config` to override settings per field:

| Field | Type | Description |
|---|---|---|
| `name` | string | Field name (dot-separated for nested fields) |
| `algorithm` | string | Override cipher algorithm for this field |
| `keyId` | string | Override keyset identifier for this field |
| `schema` | object | For schema-aware decryption — original field schema |
| `fieldMode` | `OBJECT` or `ELEMENT` | Override field mode |
| `fpeTweak` | string | FPE tweak value |
| `fpeAlphabetType` | string | FPE alphabet type |
| `fpeAlphabetCustom` | string | FPE custom alphabet (when `fpeAlphabetType=CUSTOM`) |

---

## API Usage Examples

### Encrypt a single value (String)

**Request**

```bash
curl --location 'localhost:8080/encrypt/value' \
  --header 'Content-Type: application/json' \
  --data '"As I was going to St. Ives, I met a man with seven wives ..."'
```

**Response**

```json
"YAE7msoBggtaFnc7JIOCF145pdFxMiNXRzT/qBajvOMlo18BUrmuPYcldLc1MN2fWz25V6sEF0LIZ1aS6XvlE3X5hO87EJshteV6aRfdrmiMCBDaOLxtcyWA/Uh59yoeDDCya2V5wWux"
```

### Decrypt a single value (String)

**Request**

```bash
curl --location 'localhost:8080/decrypt/value' \
  --header 'Content-Type: application/json' \
  --data '"YAE7msoBiCS8sRZ3Y1Jp7taFUxVTvz0MmQqIqWrTyeuVnw/lozTbCFn8tVTQeTNg8LTUEu/aHpe7+yAbRdkrvnaNAEHP/CXdsQi3myPX3dPd+17NgDGNatFECwwhbGRJDDCya2V5wWux"'
```

**Response**

```json
"As I was going to St. Ives, I met a man with seven wives ..."
```

### Encrypt array elements (Number)

**Request**

```bash
curl --location 'localhost:8080/encrypt/array-elements' \
  --header 'Content-Type: application/json' \
  --data '[100000001,100000002,100000003]'
```

**Response**

```json
[
  "JwE7msoBrPs9HYlLV5CopL6ObC2EjfhHw6Z8Oy+d3awGBzycrn+DDDCya2V5wWux",
  "JwE7msoBXE6MNKNarBqgFewiZ2844khAVFCKxbGlCuTbdlo1vyJADDCya2V5wWux",
  "JwE7msoBUIrZN4/nkD6zdOoaETEAOjqSjXi2wIl3OungN/cJR5ZzDDCya2V5wWux"
]
```

### Decrypt array elements (Number)

**Request**

```bash
curl --location 'localhost:8080/decrypt/array-elements' \
--header 'Content-Type: application/json' \
--data '[
  "JwE7msoBrPs9HYlLV5CopL6ObC2EjfhHw6Z8Oy+d3awGBzycrn+DDDCya2V5wWux",
  "JwE7msoBXE6MNKNarBqgFewiZ2844khAVFCKxbGlCuTbdlo1vyJADDCya2V5wWux",
  "JwE7msoBUIrZN4/nkD6zdOoaETEAOjqSjXi2wIl3OungN/cJR5ZzDDCya2V5wWux"
]'
```

**Response**

```json
[
  100000001,
  100000002,
  100000003
]
```


### Encrypt selected fields of JSON objects

**Request**

```bash
curl --location 'localhost:8080/encrypt/value-with-config' \
--header 'Content-Type: application/json' \
--data '{
    "data": {
        "id": 1234,
        "personal": "As I was going to St. Ives",
        "numbers": [
            1,
            2,
            3,
            4
        ],
        "my": {
            "enc": "foo & blah",
            "plain": "no secret"
        }
    },
    "fieldConfig": [
        {
            "name": "personal"
        },
        {
            "name": "numbers"
        },
        {
            "name": "my",
            "fieldMode": "ELEMENT"
        },
        {
            "name": "my.enc"
        }
    ]
}'
```

**Response**

```json
{
    "id": 1234,
    "personal": "PQE7msoBqUkPTDCOQQgrwW1sJOAcMO7dVP/te7Km8ouYcHzA0fiwJo7wQ2r6bftzKYKHhSaFnU/p/cBwNQwwsmtlecFrsQ==",
    "numbers": [
        "JAE7msoBftmCboHYW2tihvf6g8ag7Vxzc+tzyN4J9CHTh4x0DDCya2V5wWux",
        "JAE7msoBFfmDYVrEL+Zl58sCCpw2C+6WyfEeGFbgGkQsk2AGDDCya2V5wWux",
        "JAE7msoBlYTz1TU5tLWQU7wqZP/IY2mcJSoNgieLhSVtLNViDDCya2V5wWux",
        "JAE7msoBXMD0fFznPFAfGhZStc7OEugz9ugaJ5VmmkuaLeSuDDCya2V5wWux"
    ],
    "my": {
        "enc": "LQE7msoBD114IjA4ULArLuBoy9xSRH8CpV3rt2ncZbI57ePotIAzYxiGTNIADDCya2V5wWux",
        "plain": "no secret"
    }
}
```

### Decrypt selected fields of JSON objects

**Request**

```bash
curl --location 'localhost:8080/decrypt/value-with-config' \
  --header 'Content-Type: application/json' \
  --data '{
      "data": {
          "id": 1234,
          "personal": "PQE7msoBqUkPTDCOQQgrwW1sJOAcMO7dVP/te7Km8ouYcHzA0fiwJo7wQ2r6bftzKYKHhSaFnU/p/cBwNQwwsmtlecFrsQ==",
          "numbers": [
              "JAE7msoBftmCboHYW2tihvf6g8ag7Vxzc+tzyN4J9CHTh4x0DDCya2V5wWux",
              "JAE7msoBFfmDYVrEL+Zl58sCCpw2C+6WyfEeGFbgGkQsk2AGDDCya2V5wWux",
              "JAE7msoBlYTz1TU5tLWQU7wqZP/IY2mcJSoNgieLhSVtLNViDDCya2V5wWux",
              "JAE7msoBXMD0fFznPFAfGhZStc7OEugz9ugaJ5VmmkuaLeSuDDCya2V5wWux"
          ],
          "my": {
              "enc": "LQE7msoBD114IjA4ULArLuBoy9xSRH8CpV3rt2ncZbI57ePotIAzYxiGTNIADDCya2V5wWux",
              "plain": "no secret"
          }
      },
      "fieldConfig": [
          {
              "name": "personal"
          },
          {
              "name": "numbers",
              "fieldMode": "ELEMENT"
          },
          {
              "name": "my",
              "fieldMode": "ELEMENT"
          },
          {
              "name": "my.enc"
          }
      ]
  }'
```

**Response**

```json
{
    "id": 1234,
    "personal": "As I was going to St. Ives",
    "numbers": [
        1,
        2,
        3,
        4
    ],
    "my": {
        "enc": "foo & blah",
        "plain": "no secret"
    }
}
```

### FPE encryption of JSON objects

**Request**

```bash
curl --location 'localhost:8080/encrypt/value-with-config' \
  --header 'Content-Type: application/json' \
  --data '{
      "data": {
          "id": 12345,
          "ccn": "4455202014528870",
          "ssn": "230564998"
      },
      "fieldConfig": [
          {
              "name": "ccn",
              "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
              "keyId": "someFpeKey",
              "fpeAlphabetType": "DIGITS"
          },
          {
              "name": "ssn",
              "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
              "keyId": "someFpeKey",
              "fpeAlphabetType": "DIGITS"
          }
      ]
  }'
```

**Response**

Note how the `ccn` format of 16-digits and the `ssn` format of 9-digits is preserved!

```json
{
    "id": 12345,
    "ccn": "9793007697134152",
    "ssn": "460221321"
}
```

### FPE decryption of JSON objects

**Request**

```bash
curl --location 'localhost:8080/decrypt/value-with-config' \
  --header 'Content-Type: application/json' \
  --data '{
      "data": {
          "id": 12345,
          "ccn": "9793007697134152",
          "ssn": "460221321"
      },
      "fieldConfig": [
          {
              "name": "ccn",
              "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
              "keyId": "someFpeKey",
              "fpeAlphabetType": "DIGITS"
          },
          {
              "name": "ssn",
              "algorithm": "CUSTOM/MYSTO_FPE_FF3_1",
              "keyId": "someFpeKey",
              "fpeAlphabetType": "DIGITS"
          }
      ]
  }'
```

**Response**

```json
{
    "id": 12345,
    "ccn": "4455202014528870",
    "ssn": "230564998"
}
```

---

## Postman Collection

A Postman collection with many example requests is included in the source at:

`funqy-http-kryptonite/src/main/resources/META-INF/funqy-kryptonite-http-api-samples.postman_collection.json`

A [publicly shared Postman workspace](https://www.postman.com/hpgrahsl/workspace/kryptonite-for-kafka-http-api-public-samples/collection/25347096-89fc9ca4-c6fb-4925-afab-9a2f469c75bd) is also available.


[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/25347096-89fc9ca4-c6fb-4925-afab-9a2f469c75bd?action=collection%2Ffork&collection-url=entityId%3D25347096-89fc9ca4-c6fb-4925-afab-9a2f469c75bd%26entityType%3Dcollection%26workspaceId%3Ddd103435-bfac-4fc3-aace-daaac567434c)

