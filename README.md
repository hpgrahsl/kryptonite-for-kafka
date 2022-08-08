# Kryptonite for Kafka: Client-Side 🔒 Field-Level 🔓 Cryptography for Apache Kafka®

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

### Overview

Kryptonite is a library to do field-level cryptography for records on their way into and out of [Apache Kafka®](https://kafka.apache.org/). Currently, it targets two main use cases:

1. [data integration scenarios](connect-transform-kryptonite/README.md) based on [Kafka Connect](https://kafka.apache.org/documentation/#connect) by means of a turn-key ready [transformation](https://kafka.apache.org/documentation/#connect_transforms) (SMT) to run encryption / decryption operations on selected fields of records with or without schema.
2. [stream processing scenarios](ksqldb-udfs-kryptonite/README.md) based on [ksqlDB](https://ksqlDB.io) by providing custom [user-defined functions](https://docs.ksqldb.io/en/latest/reference/user-defined-functions/) (UDF) to encrypt / decrypt selected data columns in STREAMs and TABLEs respectively.

### Build, Installation and Deployment

Either you build this project from sources via Maven or you can download pre-built, self-contained packages of the latest Kryptonite for Kafka artefacts.

##### Kafka Connect SMT

The pre-built Kafka Connect SMT can be downloaded from here [connect-transform-kryptonite-0.3.0.jar](https://drive.google.com/file/d/1XVDnvId5bnemOTtqmJhL0R0114JT8Mjv/view?usp=sharing)

In order to deploy this custom SMT **put the jar into your _'connect plugin path'_** that is configured to be scanned during boostrap of the kafka connect worker node(s).

After that, configure Kryptonite's `CipherField` transformation for any of your source / sink connectors. **Read here about the configuration options and how to [apply the SMT](connect-transform-kryptonite/README.md) based on simple examples.**

##### ksqlDB UDFs

The pre-built ksqlDB UDFs can be downloaded from here [ksqldb-udfs-kryptonite-0.1.0-EXPERIMENTAL.jar](https://drive.google.com/file/d/1_VG3LbWx2qdZgX7tXJk2hX1_cDSC49y9/view?usp=sharing)

In order to deploy the UDFs **put the jar into your _'ksql extension directory'_** that is configured to be scanned during bootstrap of the ksqlDB server process(es).

After that, start using the UDFs, namely `K4KENCRYPT` and `K4KDECRYPT`, to selectively encrypt and decrypt column values in ksqlDB rows of `TABLES` and `STREAMS` respectively. **Read here about the configuration options and how to [apply the UDFs](ksqldb-udfs-kryptonite/README.md) based on simple examples.**

### Cipher Algorithm Specifics

The project uses authenticated encryption with associated data ([AEAD](https://en.wikipedia.org/wiki/Authenticated_encryption)) and in particular applies [AES](https://en.wikipedia.org/wiki/Advanced_Encryption_Standard) in [GCM](https://en.wikipedia.org/wiki/Galois/Counter_Mode) mode for probabilistic encryption (default) or [SIV](https://en.wikipedia.org/wiki/AES-GCM-SIV) mode for uses cases which either require or at least benefit from deterministic encryption. The preferred and new default way is to configure Kryptonite to use Google's [Tink](https://github.com/google/tink) multi-language, cross-platform open-source cryptography library. Kryptonite for Kafka version 0.2.0+ provides the following cipher algorithms:

- [AEAD](https://developers.google.com/tink/aead) using **AES in GCM mode for probabilistic encryption** based on Tink's implementation
- [DAEAD](https://developers.google.com/tink/deterministic-aead) using **AES in SIV mode for deterministic encryption** based on Tink's implementation
- for backwards compatibility to earlier versions of Kryptonite for Kafka a JCE-based AEAD AES GCM implementation which should be considered _@Deprecated_ in the context of this project and not used any longer

All three cryptographic primitives offer support for _authenticated encryption with associated data_ (AEAD). This basically means that besides the ciphertext, an encrypted field additionally contains unencrypted but authenticated meta-data. In order to keep the storage overhead per encrypted field relatively low, the implementation currently only incorporates a version identifier for Kryptonite itself together with a short identifier representing the algorithm as well as the keyset identifier which was used to encrypt the field in question. Future versions might benefit from additional meta-data.

By design, every application of AEAD in probabilistic mode on a specific record field results in different ciphertexts for one and the same plaintext. This is in general not only desirable but very important to make attacks harder. However, in the context of Kafka records this has an unfavorable consequence for producing clients e.g. a source connector. **Applying Kryptonite using AEAD in probabilistic mode on a source record's key would result in a 'partition mix-up'** because records with the same original plaintext key would end up in different topic partitions. In other words, **if you plan to use Kryptonite for source record keys make sure to configure it to apply deterministic AEAD i.e. AES in SIV mode**. Doing so safely supports the encryption of record keys and keeps topic partitioning and record ordering intact.

## Donate

If you like this project and want to support its further development and maintenance we are happy about your [PayPal donation](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE). 

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
