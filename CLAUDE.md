# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kryptonite for Kafka provides client-side field-level cryptography for Apache Kafka using authenticated encryption (AEAD). It supports encryption/decryption via:

1. **Kafka Connect SMT** - Single Message Transformation for data integration
2. **ksqlDB UDFs** - User-Defined Functions for stream processing
3. **Flink UDFs** - User-Defined Functions for Flink Table API/SQL
4. **Quarkus Funqy HTTP API** - Web service for cross-language scenarios

The cryptographic implementation uses Google's Tink library with AES-GCM (probabilistic) or AES-GCM-SIV (deterministic) modes.

## Build Commands

```bash
# Build entire project
./mvnw clean install

# Build specific module
./mvnw clean install -pl <module-name>

# Run tests for entire project
./mvnw test

# Run tests for specific module
./mvnw test -pl <module-name>

# Package without running tests
./mvnw clean package -DskipTests
```

## Architecture

**Multi-module Maven project structure:**

- `kryptonite/` - Core cryptography library (shared by all modules)
- `connect-transform-kryptonite/` - Kafka Connect SMT implementation
- `ksqldb-udfs-kryptonite/` - ksqlDB user-defined functions
- `flink-udfs-kryptonite/` - Flink user-defined functions
- `funqy-http-kryptonite/` - Quarkus-based HTTP API service

**Core library (`kryptonite/`) packages:**

- `com.github.hpgrahsl.kryptonite` - Main entry point (`Kryptonite` class) and field metadata
- `com.github.hpgrahsl.kryptonite.config` - Configuration handling and keyset management
- `com.github.hpgrahsl.kryptonite.crypto` - Cryptographic algorithm implementations (Tink-based)
- `com.github.hpgrahsl.kryptonite.keys` - Key vault abstractions and key material resolution
- `com.github.hpgrahsl.kryptonite.kms` - Cloud KMS integrations (Azure Key Vault, GCP KMS)
- `com.github.hpgrahsl.kryptonite.serdes` - Serialization/deserialization (Kryo-based)

**Key architecture concepts:**

1. **Encryption modes**: OBJECT (encrypt entire field) vs ELEMENT (encrypt array/map elements individually)
2. **Key management**: Supports local config, encrypted keysets, and cloud KMS (Azure, GCP)
3. **Cipher algorithms**: TINK/AES_GCM (probabilistic), TINK/AES_GCM_SIV (deterministic - use for record keys)
4. **Encrypted field format**: Base64-encoded string containing ciphertext + authenticated metadata (version, algorithm, keyset ID)

## Important Notes

- **Java 17+** required (see `maven.compiler.release` in pom.xml)
- **Deterministic encryption**: Use AES-GCM-SIV for Kafka record keys to maintain partitioning
- **Schema handling**: Encrypted fields are stored as VARCHAR/STRING in schema-aware systems
- **Keyset security**: Key materials must be treated with utmost secrecy - use external config or cloud KMS
- **Tink keysets**: All cryptographic key materials must be valid Tink keyset JSON specifications

## Configuration Examples

All modules require keyset configuration. Example Tink keyset structure:

```json
{
  "identifier": "my-demo-secret-key-123",
  "material": {
    "primaryKeyId": 123456789,
    "key": [{
      "keyData": {
        "typeUrl": "type.googleapis.com/google.crypto.tink.AesGcmKey",
        "value": "<BASE64_ENCODED_KEY_HERE>",
        "keyMaterialType": "SYMMETRIC"
      },
      "status": "ENABLED",
      "keyId": 123456789,
      "outputPrefixType": "TINK"
    }]
  }
}
```

## Development Workflow

1. Core library changes affect all dependent modules
2. Module-specific changes are isolated to their respective directories
3. Each module has its own README.md with detailed usage examples
4. Pre-built binaries available on GitHub releases page (since v0.4.0)