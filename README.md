# Kryptonite for Kafka: Client-Side 🔒 Field-Level 🔓 Cryptography for Apache Kafka®

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

## Overview

Kryptonite for Kafka is a library to do field-level cryptography for records on their way into and out of [Apache Kafka®](https://kafka.apache.org/). Currently, it targets three main use cases:

1. [data integration scenarios](connect-transform-kryptonite/README.md) based on [Kafka Connect](https://kafka.apache.org/documentation/#connect) by means of a turn-key ready [transformation](https://kafka.apache.org/documentation/#connect_transforms) (SMT) to run encryption / decryption operations on selected fields of records with or without schema
2. [stream processing scenarios](ksqldb-udfs-kryptonite/README.md) based on [ksqlDB](https://ksqlDB.io) by providing custom [user-defined functions](https://docs.ksqldb.io/en/latest/reference/user-defined-functions/) (UDF) to encrypt / decrypt selected data columns in STREAMs and TABLEs respectively
3. [cross language/runtime scenarios](funqy-http-kryptonite/README.md) by running a co-located [Quarkus](http://quarkus.io) [Funqy](https://quarkus.io/guides/funqy) service exposing a lightweight web API to encrypt / decrypt payloads, or fields thereof, from any client application talking HTTP.

### Build, Installation and Deployment

Either you build this project from sources via Maven or you can download pre-built, self-contained packages of the latest Kryptonite for Kafka artefacts.

##### Kafka Connect SMT

Starting with Kryptonite for Kafka 0.4.0, the pre-built Kafka Connect SMT can be downloaded directly from the [release pages](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).

In order to deploy this custom SMT **put the root folder of the extracted archive into your _'connect plugin path'_** that is configured to be scanned during boostrap of the kafka connect worker node(s).

After that, configure Kryptonite's `CipherField` transformation for any of your source / sink connectors. **Read here about the configuration options and how to [apply the SMT](connect-transform-kryptonite/README.md) based on simple examples.**

##### ksqlDB UDFs

Starting with Kryptonite for Kafka 0.4.0, the pre-built ksqlDB UDFs can be downloaded directly from the [release pages](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).

In order to deploy the UDFs **put the jar into your _'ksql extension directory'_** that is configured to be scanned during bootstrap of the ksqlDB server process(es).

After that, start using the UDFs, namely `K4KENCRYPT` and `K4KDECRYPT`, to selectively encrypt and decrypt column values in ksqlDB rows of `TABLES` and `STREAMS` respectively. **Read here about the configuration options and how to [apply the UDFs](ksqldb-udfs-kryptonite/README.md) based on simple examples.**

##### Quarkus Funqy HTTP API service

Starting with Kryptonite for Kafka 0.4.0, the pre-built Quarkus application can be downloaded directly from the [release pages](https://github.com/hpgrahsl/kryptonite-for-kafka/releases).

When building from sources and before running the Quarkus application in dev mode (`./mvnw quarkus:dev` or `quarkus dev`), make sure to specify your individual configuration options in `application.properties`. In case you run the pre-built binaries in prod mode (`java -jar target/quarkus-app/quarkus-run.jar`), you have to properly override any of the mandatory/default settings when starting the application. **Read here about the configuration options and how to [use the HTTP API](funqy-http-kryptonite/README.md) based on example requests.**

### Cipher Algorithm Specifics

The project uses authenticated encryption with associated data ([AEAD](https://en.wikipedia.org/wiki/Authenticated_encryption)) and in particular applies [AES](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard) in [GCM](https://en.wikipedia.org/wiki/Galois/Counter_Mode) mode for probabilistic encryption (default) or [SIV](https://en.wikipedia.org/wiki/AES-GCM-SIV) mode for uses cases which either require or at least benefit from deterministic encryption. The preferred and new default way is to configure Kryptonite to use Google's [Tink](https://github.com/google/tink) multi-language, cross-platform open-source cryptography library. Kryptonite for Kafka version 0.4.0+ provides the following cipher algorithms:

- [AEAD](https://developers.google.com/tink/aead) using **AES in GCM mode for probabilistic encryption** based on Tink's implementation
- [DAEAD](https://developers.google.com/tink/deterministic-aead) using **AES in SIV mode for deterministic encryption** based on Tink's implementation

These cryptographic primitives offer support for _authenticated encryption with associated data_ (AEAD). This basically means that besides the ciphertext, an encrypted field additionally contains unencrypted but authenticated meta-data. In order to keep the storage overhead per encrypted field relatively low, the implementation currently only incorporates a version identifier for Kryptonite itself together with a short identifier representing the algorithm as well as the keyset identifier which was used to encrypt the field in question. Future versions might benefit from additional meta-data.

By design, every application of AEAD in probabilistic mode on a specific record field results in different ciphertexts for one and the same plaintext. This is in general not only desirable but very important to make attacks harder. However, in the context of Kafka records this has an unfavorable consequence for producing clients e.g. a source connector. **Applying Kryptonite using AEAD in probabilistic mode on a source record's key would result in a 'partition mix-up'** because records with the same original plaintext key would end up in different topic partitions. In other words, **if you plan to use Kryptonite for source record keys make sure to configure it to apply deterministic AEAD i.e. AES in SIV mode**. Doing so safely supports the encryption of record keys and keeps topic partitioning and record ordering intact.

## Donate

If you like this project and want to support its future development and maintenance we are happy about your [PayPal donation](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE). 

## License Information

This project is licensed according to [Apache License Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)

```
Copyright (c) 2021 - present. Hans-Peter Grahsl (grahslhp@gmail.com)

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
