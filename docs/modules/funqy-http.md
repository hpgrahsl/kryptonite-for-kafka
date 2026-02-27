# :material-api: Quarkus HTTP Service

The Quarkus Funqy HTTP API is a lightweight standalone service that exposes encryption and decryption over HTTP. Its primary purpose is to allow client applications written in languages other than Java to participate in end-to-end encryption scenarios built on top of Kafka.

---

## Getting the Service

Download the pre-built archive from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases) (available since version 0.4.0).

Or build from source:

```bash
./mvnw clean package -DskipTests -pl funqy-http-kryptonite
```

---

## Running the Service

**Development mode (live reload):**

```bash
./mvnw quarkus:dev -pl funqy-http-kryptonite
```

**Production mode (pre-built JAR):**

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Configuration is read from `application.properties`. For the pre-built binary, override properties via system properties or environment variables:

```bash
java \
  -Dcipher.data.keys='[...]' \
  -Dcipher.data.key.identifier=my-key \
  -jar target/quarkus-app/quarkus-run.jar
```

---

## Configuration

The `application.properties` file:

```properties
#############################################
# Kryptonite for Kafka HTTP API configuration
#############################################
# MANDATORY
cipher.data.keys=[]
cipher.data.key.identifier=

# OPTIONAL (with defaults)
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

See the [Configuration Reference](../configuration.md) for all parameters. The HTTP API uses dot-separated names (`cipher.data.keys`, `kms.type`, etc.).

---

## Endpoints

The service exposes six endpoints — three for encryption, three for decryption:

| Endpoint | Input | Output |
|---|---|---|
| `POST /encrypt/value` | Any JSON value | Base64 ciphertext string |
| `POST /encrypt/array-elements` | JSON array | JSON array of Base64 ciphertext strings |
| `POST /encrypt/map-entries` | JSON object | JSON object with Base64 ciphertext values |
| `POST /decrypt/value` | Base64 ciphertext string | Original JSON value |
| `POST /decrypt/array-elements` | JSON array of Base64 ciphertext strings | JSON array of original values |
| `POST /decrypt/map-entries` | JSON object with Base64 ciphertext values | JSON object with original values |
| `POST /encrypt/value-with-config` | `{data: {...}, fieldConfig: [...]}` | JSON object with encrypted fields |
| `POST /decrypt/value-with-config` | `{data: {...}, fieldConfig: [...]}` | JSON object with decrypted fields |

The Swagger UI is available at `http://localhost:8080/q/swagger-ui/` when running in dev mode.

---

## Usage Examples

### Encrypt a single value

```bash
curl -s -X POST http://localhost:8080/encrypt/value \
  -H 'Content-Type: application/json' \
  -d '"some sensitive text"'
```

Response:

```json
"M007MIScg8F0A/cAddWbayvUPObjxuGFxisu5MUckDhBss6fo3g..."
```

### Decrypt a single value

```bash
curl -s -X POST http://localhost:8080/decrypt/value \
  -H 'Content-Type: application/json' \
  -d '"M007MIScg8F0A/cAddWbayvUPObjxuGFxisu5MUckDhBss6fo3g..."'
```

Response:

```json
"some sensitive text"
```

### Encrypt selected fields of a JSON object

```bash
curl -s -X POST http://localhost:8080/encrypt/value-with-config \
  -H 'Content-Type: application/json' \
  -d '{
    "data": {
      "customerId": "CUST-12345",
      "creditCardNumber": "4455202014528870",
      "ssn": "230564998"
    },
    "fieldConfig": [
      { "name": "creditCardNumber" },
      { "name": "ssn" }
    ]
  }'
```

Response:

```json
{
  "customerId": "CUST-12345",
  "creditCardNumber": "UuEKnrv91bLImQvKqXTET7RTP93XeLfNRhzJaXVc6OGA...",
  "ssn": "M007MIScg8F0A/cAddWbayvUPObjxuGFxisu5MUckDhB..."
}
```

### FPE encryption with per-field config

```bash
curl -s -X POST http://localhost:8080/encrypt/value-with-config \
  -H 'Content-Type: application/json' \
  -d '{
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
  }'
```

Response (format preserved — 16 digits stay 16 digits):

```json
{
  "customerId": "CUST-12345",
  "creditCardNumber": "0472659418391244",
  "ssn": "348538193"
}
```

### Encrypt array elements

```bash
curl -s -X POST http://localhost:8080/encrypt/array-elements \
  -H 'Content-Type: application/json' \
  -d '["value1", 42, true, {"nested": "doc"}]'
```

Response — each element encrypted individually:

```json
[
  "M007MIScg8...",
  "JAE7msoBgq...",
  "JAE7msoBjN...",
  "hgE7msoBzM..."
]
```

---

## `fieldConfig` Object

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

## Postman Collection

A Postman collection with 16 example requests is included in the source at:

`funqy-http-kryptonite/src/main/resources/META-INF/funqy-kryptonite-http-api-samples.postman_collection.json`

A [publicly shared Postman workspace](https://www.postman.com/hpgrahsl/workspace/kryptonite-for-kafka-http-api-public-samples/collection/25347096-89fc9ca4-c6fb-4925-afab-9a2f469c75bd) is also available.
