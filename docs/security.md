# Security

## Encryption Algorithms

### AES-GCM

`TINK/AES_GCM` is the default algorithm. It applies AES in [Galois/Counter Mode](https://en.wikipedia.org/wiki/Galois/Counter_Mode).

**Properties:**

- **probabilistic:** encrypting the same plaintext repeatedly always produces different ciphertexts
- **authenticated:** the ciphertext includes a GCM authentication tag; any tampering is detected on decryption
- **key sizes:** 128-bit or 256-bit (default)

!!! question "When to use?"
    It's the right default choice unless you have specific, use case-driven reasons for one of the other options explained further below. The randomness prevents statistical analysis and makes certain cryptanalytic attack models harder.

!!! warning "Do not use for Kafka record keys"
    Applying probabilistic encryption to a Kafka record key causes records with the same original key to land in different partitions. This breaks partitioning guarantees and record ordering. If you plan to encrypt the whole key or parts of it, switch to `TINK/AES_GCM_SIV` for keys instead.

### AES-GCM Envelope Encryption (Keyset-based)

`TINK/AES_GCM_ENVELOPE_KEYSET` applies [envelope encryption](https://en.wikipedia.org/wiki/Hybrid_cryptosystem#Envelope_encryption) using a Tink keyset as the Key Encryption Key (KEK). For each session, Kryptonite generates a fresh AES-GCM DEK, uses it to encrypt field data, and then wraps it with the KEK keyset. Kryptonite bundles the wrapped DEK inline with the ciphertext.

**Properties:**

- **probabilistic:** each DEK is randomly generated; field ciphertexts produced by different sessions are cryptographically independent
- **authenticated:** the DEK ciphertext includes a GCM authentication tag allowing detection of tampering on decryption
- **DEK rotation:** the DEK rotates automatically after a configurable number of encryptions or time window

!!! question "When to use?"
    When you want envelope encryption without external KMS infrastructure for DEK encryption. The KEK keyset itself can still be sourced from configuration or a cloud secret manager using the standard `key_source` options. See [Envelope Encryption](envelope-encryption.md) for details.

---

### AES-GCM Envelope Encryption (KMS-based)

`TINK/AES_GCM_ENVELOPE_KMS` applies [envelope encryption](https://en.wikipedia.org/wiki/Hybrid_cryptosystem#Envelope_encryption) using a cloud KMS key as the KEK. **The KEK never leaves the KMS, so all wrap/unwrap operations of the DEK are remote calls.** Wrapped DEKs are stored externally; only a compact 16-byte fingerprint reference is embedded in each ciphertext rather than the wrapped DEK in full.

**Properties:**

- **probabilistic:** each DEK is randomly generated; field ciphertexts produced by different sessions are cryptographically independent
- **authenticated:** the DEK ciphertext includes a GCM authentication tag allowing detection of tampering on decryption
- **KEK isolation:** the cloud KMS holds and manages the KEK entirely; **the raw KEK material never becomes directly exposed to Kryptonite for Kafka modules**
- **DEK rotation:** the DEK rotates automatically after a configurable number of encryptions or time window

!!! question "When to use?"
    For cloud deployments where strong key isolation is required and the KEK must not leave the cloud KMS security perimeter. Using this mode requires a compatible `EdekStore` implementation which defaults to KCache/Kafka and requires proper configuration (`edek_store_config`) and a running Kafka cluster accessible to Kryptonite for Kafka. See [Envelope Encryption](envelope-encryption.md) for details.

### AES-GCM-SIV

`TINK/AES_GCM_SIV` is a nonce misuse-resistant variant. For the same plaintext and key, it always produces the same ciphertext.

**Properties:**

- **deterministic:** same plaintext + same key always produces the same ciphertext
- **authenticated:** tamper detection is retained
- **nonce misuse-resistant:** the cipher does not fail catastrophically if a nonce is repeated

!!! question "When to use?"
    Lookups / Joins / Aggregations on ciphertext require this deterministic encryption property to work correctly. Kafka record keys or parts thereof can only be encrypted deterministically in order not to break the partitioning and ordering of records.

---

### FPE FF3-1 

`CUSTOM/MYSTO_FPE_FF3_1` is based on the NIST-standardised FF3-1 algorithm. It's a format-preserving encryption (FPE) algorithm.

**Properties:**

- **length-preserving:** a 16-digit number encrypts to different 16-digit number
- **character-set-preserving:** ciphertext uses only characters from the configured alphabet matching the character set of the input data
- **deterministic with tweak:** same plaintext + same key + same tweak = same ciphertext

!!! question "When to use?"
    For payload fields of type string you want/must preserve the format for. Examples include credit card numbers, SSNs, or phone numbers. Also, if your target system such as a database has table columns definitions with strict format constraints and changing the column type would be impractical. In general, whenever any downstream system cannot handle Base64 encoded ciphertexts resulting from the other non-FPE algorithms.

!!! warning "Trade-offs"
    It's generally considered weaker security-wise than standard AEAD ciphers. Use it only where format preservation is required. Also, this cipher has a shortest supported plaintext length that depends on alphabet size (e.g., the `DIGITS` alphabet requires at least 6 characters of input data).
    
!!! danger "Tweak Choice"    
    Tweak values should ideally chosen to be unique per field type to prevent cross-field ciphertext correlation!

---

## Key Material Security

1. **Try to avoid working with plain keysets** and don't source them from configuration, as this could easily leak keys by accident.

2. Ideally default to **either encrypted keysets in configuration** or **plain keysets in cloud secret managers.** Both approaches provide reasonable security for your keysets and offer much better protection than storing plain keysets in configuration for convenience.

3. **Prefer `KMS_ENCRYPTED` for strong security in production.** Keysets are stored encrypted in a cloud secret manager, which means an attacker would need both secret manager access and possession of the key encryption key to decrypt any ciphertext.

4. **Rotate keys periodically.** Tink's multi-key keyset design makes key rotation straightforward. Promote another key in the keyset as the new primary, leave old keys in the keyset for backward-compatible decryption and add new keys to the keyset whenever needed. Optionally phase out / delete keys from the keysets for which you are certain that you don't need them any longer due to data retention. 

5. **Use separate key encryption keys (KEK)** for different environments. Never share a production KEK with staging or development.

6. **Limit IAM permissions for cloud KMS usage** to the least privilege required:
    - secret manager access: `get` + `list` for the k4k-prefixed secrets only
    - key encryption key access: `encrypt` + `decrypt` (or `wrapKey` + `unwrapKey` for Azure) on the cloud KMS key in question

!!! danger "Key material is the root of trust"
    Exposure of a Tink keyset's `value` field means total loss of confidentiality for all data encrypted with that key. Treat it with the same care as an important private key or password. **NEVER commit plain keysets to source control - ever!**


---

## Threat Model

| Threat | Mitigation |
|---|---|
| Attacker gets access to partially encrypted Kafka topic data | Due to AEAD encryption the ciphertext is unintelligible without access to the keyset. Breaking AES_GCM itself or running a brute-force attack against a 256-bit key is currently considered impractical. |
| Attacker tries to run statistical analysis on ciphertexts | Probabilistic AES-GCM produces unique ciphertexts per encryption operation for the same input text which prevents common attacks in that regard. |
| Attacker tampers with ciphertext or the attached metadata | The authentication tag in AES_GCM makes decryption fail with an integrity error. |
| Attacker somehow gets unauthorized access to keysets from the configuration | Instead of plain keysets stored in the configuration (`key_source=CONFIG`) either use `key_source=KMS` to externalize keysets to a cloud secret manager or switch to `key_source=CONFIG_ENCRYPTED` to work with encrypted keysets based on a key encryption key. The encrypted keyset is useless without access to the KEK that is separately stored in a cloud KMS. |
| Attacker manages to access the cloud secret manager secrets | Use `key_source=KMS_ENCRYPTED` to work with encrypted keysets based on a key encryption key. The encrypted keyset is useless without access to the KEK that is separately stored in a cloud KMS. |
| Attacker "magically" gets access to the partially encrypted Kafka topic data, the encrypted keysets stored in the cloud secret manager, and the corresponding key encryption key from the cloud KMS | **💀 None. This scenario means total compromise. 💥** |

---

## Dependencies

Cryptographic operations are implemented via **[Google Tink](https://github.com/tink-crypto/)** (Java), a peer-reviewed, audited multi-language cryptography library. Kryptonite for Kafka does not implement any cryptographic primitives itself — all AEAD operations delegate to Tink. The FPE implementation uses the [**mysto**](https://github.com/mysto) library for the FF3-1 algorithm. A custom Tink key type is used for FPE to transparently integrate with Tink's keyset mechanism.

!!! question "Why FF3-1?"
    Since NIST officially withdrew FF3 in 2025, FF1 would have been the preferred FPE cipher algorithm in Kryptonite for Kafka. Yet there are still patent claims and copyrights which forbid its usage in open-source software. As there are no better open alternatives with broad adoption, FF3-1 was chosen as the next best option that has been NIST-vetted and has also seen reasonable adoption in the field.
