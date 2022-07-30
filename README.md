# Kryptonite for Kafka: Client-Side 🔒 Field-Level 🔓 Cryptography for Apache Kafka®

[![Donate](https://img.shields.io/badge/Donate-PayPal-green.svg)](https://www.paypal.com/donate/?hosted_button_id=NUCLPDTLNJ8KE)

**Disclaimer: This is an UNOFFICIAL community project!**

### Overview

Kryptonite is a library to do field-level cryptography for records on their way into and out of [Apache Kafka®](https://kafka.apache.org/). Currently, it targets two main use cases:

1. [data integration scenarios](connect-transform-kryptonite/README.md) based on [Kafka Connect](https://kafka.apache.org/documentation/#connect) by means of a turn-key ready [transformation](https://kafka.apache.org/documentation/#connect_transforms) (SMT) to run encryption / decryption operations on selected fields of records with or without schema.
2. [stream processing scenarios](ksqldb-udfs-kryptonite/README.md) based on [ksqlDB](https://ksqlDB.io) by providing custom [user-defined functions](https://docs.ksqldb.io/en/latest/reference/user-defined-functions/) (UDF) to encrypt / decrypt selected data columns in STREAMs and TABLEs respectively.

### Build, Installation and Deployment

Either you build this project from sources via Maven or you can download pre-built, self-contained packages of the latest Kryptonite artefacts.

##### Kafka Connect SMT

The pre-built Kafka Connect SMT can be downloaded from here [kafka-connect-transform-kryptonite-0.X.0-SNAPSHOT.jar](https://drive.google.com/file/d/1bpJxHCXBrx2p2uqTg9ipBoYqrpZuDJNN/view?usp=sharing)

In order to deploy this custom SMT **put the jar into your _'connect plugin path'_** that is configured to be scanned during boostrap of the kafka connect worker node(s).

After that, configure Kryptonite's `CipherField` transformation for any of your source / sink connectors.

##### ksqlDB UDFs

The pre-built ksqlDB UDFs can be downloaded from here [ksqldb-udfs-kryptonite-0.1.0-EXPERIMENTAL.jar]()

In order to deploy the UDFs **put the jar into your _'ksql extension directory'_** that is configured to be scanned during bootstrap of the ksqldb server process(es).

After that, start using the UDFs, namely `K4KENCRYPT` and `K4KDECRYPT`, to selectively encrypt and decrypt column values in ksqlDB rows of _TABLES_ and _STREAMS_ respectively.

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
