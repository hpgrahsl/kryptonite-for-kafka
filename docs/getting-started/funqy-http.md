# Getting Started — Quarkus HTTP API

The Quarkus Funqy HTTP API is a lightweight standalone service that exposes encryption and decryption over HTTP. It lets applications written in any language participate in end-to-end field-level encryption without needing a JVM runtime on the client side.

---

## Prerequisites

- **Java 17+** on the host running the service
- A running Apache Kafka cluster is **not** required just to run the service itself — it operates independently and is called directly by client applications

---

## Step 1 — Get the service

### Option A — Pre-built artifact (recommended)

1. Download the ZIP archive from the [GitHub Releases page](https://github.com/hpgrahsl/kryptonite-for-kafka/releases) (available since version 0.4.0).
2. Extract the archive. It contains a `quarkus-app/` directory with the runnable JAR and its dependencies.

### Option B — Build from source

```bash
git clone https://github.com/hpgrahsl/kryptonite-for-kafka.git
cd kryptonite-for-kafka
./mvnw clean package -DskipTests -pl funqy-http-kryptonite
```

The runnable application is produced under `funqy-http-kryptonite/target/quarkus-app/`.

---

## Step 2 — Generate a keyset

Use the [Keyset Tool](../keyset-tool.md) to produce key material:

```bash
java -jar kryptonite-keyset-tool/target/kryptonite-keyset-tool-0.1.0.jar \
  -a AES_GCM -i my-demo-key -f FULL -p
```

Copy the printed JSON — you will reference it in the configuration in the next step.

!!! warning "Key material is a secret"
    Do not commit key material to source control. For production use, see [Key Management](../key-management.md).

---

## Step 3 — Configure the service

Configuration lives in `funqy-http-kryptonite/src/main/resources/application.properties`. The two mandatory settings are:

```properties
cipher.data.keys=[{"identifier":"my-demo-key","material":{"primaryKeyId":10000,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey","value":"<BASE64_KEY>","keyMaterialType":"SYMMETRIC"},"status":"ENABLED","keyId":10000,"outputPrefixType":"TINK"}]}}]
cipher.data.key.identifier=my-demo-key
```

For the pre-built JAR, override settings at runtime via system properties instead of editing the file:

```bash
java \
  -Dcipher.data.keys='[{"identifier":"my-demo-key","material":{...}}]' \
  -Dcipher.data.key.identifier=my-demo-key \
  -jar target/quarkus-app/quarkus-run.jar
```

See the [Configuration reference](../configuration.md) for all available parameters.

---

## Step 4 — Start the service

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

The service starts on port `8080` by default. In development mode (with live reload):

```bash
./mvnw quarkus:dev -pl funqy-http-kryptonite
```

The Swagger UI is available at `http://localhost:8080/q/swagger-ui/` in dev mode.

---

## Step 5 — Make your first request

Encrypt a single value:

```bash
curl -s -X POST http://localhost:8080/encrypt/value \
  -H 'Content-Type: application/json' \
  -d '"4455202014528870"'
```

Response:

```json
"UuEKnrv91bLImQvKqXTET7RTP93XeLfNRhzJaXVc6OGA..."
```

Decrypt it back:

```bash
curl -s -X POST http://localhost:8080/decrypt/value \
  -H 'Content-Type: application/json' \
  -d '"UuEKnrv91bLImQvKqXTET7RTP93XeLfNRhzJaXVc6OGA..."'
```

Response:

```json
"4455202014528870"
```

---

## Next Steps

- [Quarkus HTTP API reference](../modules/funqy-http.md) — all eight endpoints, `fieldConfig` object, FPE examples, Postman collection
- [Configuration reference](../configuration.md) — all parameters and their naming conventions
- [Key Management](../key-management.md) — moving from inline keys to cloud KMS
- [Keyset Tool](../keyset-tool.md) — generate keys for all algorithms and KMS backends
