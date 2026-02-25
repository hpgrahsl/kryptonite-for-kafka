---
hide:
  - navigation
  - toc
---

# Kryptonite for Apache Kafka (K4K)

<div class="hero-text" markdown>
## Client-Side Field Level Cryptography for Data Streaming Workloads

Encrypt and decrypt payload fields end-to-end **before sensitive data ever reaches the Kafka brokers**.<br/>Seamlessly works across four modules: [Apache Kafka Connect](https://kafka.apache.org/documentation/#connect), [Apache Flink](https://flink.apache.org), [ksqlDB](https://www.confluent.io/product/ksqldb/), and a standalone [Quarkus](https://quarkus.io) HTTP service.

[Get Started :fontawesome-solid-rocket:](getting-started.md){ .md-button .md-button--primary }
[GitHub :fontawesome-brands-github:](https://github.com/hpgrahsl/kryptonite-for-kafka){ .md-button }
</div>

---

## Why Kryptonite for Apache Kafka?

<div class="grid cards" markdown>

-   :material-shield-lock-outline: &nbsp; **True Client-Side Encryption**

    ---

    **Sensitive plaintext never leaves the client-side application.**
    
    Neither Kafka brokers nor any intermediary Kafka proxy infrastructure ever gets to see sensitive payload fields in plaintext. Encryption / Decryption of data happens exclusively within the security perimeter of the client-side application.

-   :material-cursor-default-click: &nbsp; **Field-Level Precision**

    ---

    **Configure which payload fields need this extra level of data protection.**
    
    Encryption of just one field, a handful of fields, or maybe all payload fields? Everything else stays untouched and is still directly processable by any downstream applications.

-   :material-key-chain: &nbsp; **Flexible Key Management**

    ---

    **You define how to manage cryptographic keys.**
    
    Quickly need to inline keysets for development? Need to store keysets in a cloud key management systems (KMS)? Want to encrypt keysets with a key encryption key stored (KEK) in a cloud provider's KMS? [GCP Cloud KMS](https://cloud.google.com/security/products/security-key-management), [AWS KMS](https://aws.amazon.com/kms/), and [Azure Key Vault](https://azure.microsoft.com/en-us/products/key-vault) is supported out of the box - the choice is yours!

-   :material-puzzle-outline: &nbsp; **Four Ready-Made Integrations**

    ---

    Apache Kafka Connect [SMTs](https://kafka.apache.org/42/kafka-connect/user-guide/#transformations), Apache Flink [UDFs](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/table/functions/udfs/), ksqlDB [UDFs](https://docs.confluent.io/platform/current/ksqldb/reference/user-defined-functions.html), and a Quarkus [Funqy](https://quarkus.io/guides/funqy) HTTP service. No custom serializers or any custom code required as the encrypt/decrypt capabilities are largely based on flexible configuration options.

-   :material-format-letter-matches: &nbsp; **Versatile Encryption Capabilities**

    ---

    By default, all modules apply [probabilistic encryption](https://en.wikipedia.org/wiki/Probabilistic_encryption) using AES GCM 256-bit encryption. If your use case requires [deterministic encryption](https://en.wikipedia.org/wiki/Deterministic_encryption) you can choose AES GCM SIV. In order to allow for [format-preserving encryption](https://en.wikipedia.org/wiki/Format-preserving_encryption), FF3-1 is currently supported.

-   :material-google: &nbsp; **Built on top of Google Tink**

    ---

    Cryptographic primitives come directly from [Tink](https://github.com/tink-crypto) — a vetted and widely deployed open-source cryptography library implemented and maintained by cryptographers and security engineers at Google. If you want to learn more read [What is Tink?](https://developers.google.com/tink/what-is).

</div>

---

## Four Integration Modules

<div class="k4k-modules" markdown>

<div class="k4k-module-card" markdown>

<div class="k4k-module-img">
<a href="assets/images/module-connect-smt.svg" class="glightbox" data-glightbox="type: image"><img src="assets/images/module-connect-smt.svg" alt="Kafka Connect SMT"></a>
</div>

<div class="k4k-module-body" markdown>

:material-transit-connection-variant: &nbsp; **Apache Kafka Connect SMT**

The `CipherField` Single Message Transformation (SMT) encrypts or decrypts payload fields in combination with pretty much any source or sink connector available for Kafka Connect. The SMT works with schemaless JSON and common schema-aware formats (Avro, Protobuf, JSON Schema).

:octicons-book-24: [Learn more](modules/connect-smt.md)

</div>

</div>

<div class="k4k-module-card" markdown>

<div class="k4k-module-img">
<a href="assets/images/module-flink-udfs.svg" class="glightbox" data-glightbox="type: image"><img src="assets/images/module-flink-udfs.svg" alt="Apache Flink UDFs"></a>
</div>

<div class="k4k-module-body" markdown>

:material-table-arrow-right: &nbsp; **Apache Flink UDFs**

Multiple user-defined functions (`K4K_ENCRYPT_*`/`K4K_DECRYPT_*`) can be applied in Flink Table API or Flink SQL jobs. The UDFs can process both primitive and complex Flink Table API data types.

:octicons-book-24: [Learn more](modules/flink-udfs.md)

</div>

</div>

<div class="k4k-module-card" markdown>

<div class="k4k-module-img">
<a href="assets/images/module-ksqldb-udfs.svg" class="glightbox" data-glightbox="type: image"><img src="assets/images/module-ksqldb-udfs.svg" alt="ksqlDB UDFs"></a>
</div>

<div class="k4k-module-body" markdown>

:material-database-search: &nbsp; **ksqlDB UDFs**

Multiple user-defined functions (`K4KENCRYPT*`/`K4KDECRYPT*`) can be applied in ksqlDB `STREAM` or `TABLE` queries. The UDFs can process both primitive and complex ksqlDB data types.

:octicons-book-24: [Learn more](modules/ksqldb-udfs.md)

</div>

</div>

<div class="k4k-module-card" markdown>

<div class="k4k-module-img">
<a href="assets/images/module-funqy-http.svg" class="glightbox" data-glightbox="type: image"><img src="assets/images/module-funqy-http.svg" alt="Quarkus HTTP API"></a>
</div>

<div class="k4k-module-body" markdown>

:material-api: &nbsp; **Quarkus HTTP API**

A lightweight HTTP service that exposes a web API with multiple encryption and decryption endpoints. This enables applications written in any language to participate in end-to-end encryption scenarios.

:octicons-book-24: [Learn more](modules/funqy-http.md)

</div>

</div>

</div>

---

## Supported Encryption Algorithms

| Algorithm | Mode | Input | Output | Usage |
|---|---|---|---|---|
| `TINK/AES_GCM` | [AEAD probabilistic](https://developers.google.com/tink/aead) | any supported data type | string (Base64) | most cases (default) |
| `TINK/AES_GCM_SIV` | [AEAD deterministic](https://developers.google.com/tink/deterministic-aead) | any supported data type | string (Base64) | equality match, join operations, aggregrations on encrypted data |
| `CUSTOM/MYSTO_FPE_FF3_1` | [format-preserving encryption](https://en.wikipedia.org/wiki/Format-preserving_encryption) | string (specific alphabet) | string (same alphabet) | if alphabet must be preserved (credit cards, SSNs, IBANs, ...) |

:octicons-book-24: [Learn more](security.md)

---

## Available Key Management Options

| Mode | Keysets stored | At-rest protection | Typical use |
|---|---|---|---|
| `CONFIG` | Inline in config | None | Development & testing |
| `CONFIG_ENCRYPTED` | Inline, KEK-encrypted | Cloud KMS wrapping key | Production — no secret manager |
| `KMS` | Cloud secret manager | None | Production — centralised rotation |
| `KMS_ENCRYPTED` | Cloud secret manager, KEK-encrypted | Cloud KMS wrapping key | Production — maximum security |

:octicons-book-24: [Key Management Details](key-management.md) &nbsp;| &nbsp;:octicons-book-24: [Cloud KMS Overview](kms/overview.md)

---

!!! note "Community Project Disclaimer"
    Kryptonite for Kafka is an independent community project. It's neither affiliated with the ASF and the respective upstream projects nor is it an official product of any company related to the addressed technologies.
    
    Apache License 2.0 &nbsp;·&nbsp; Copyright © 2021–present Hans-Peter Grahsl.
