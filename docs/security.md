# Security

## Encryption Algorithms

### AES-GCM

`TINK/AES_GCM` is the default algorithm. It applies AES in [Galois/Counter Mode](https://en.wikipedia.org/wiki/Galois/Counter_Mode).

**Properties:**

- **probabilistic:** encrypting the same plaintext multiple times always produces different ciphertexts
- **authenticated:** the ciphertext includes a GCM authentication tag; any tampering is detected on decryption
- **key sizes:** 128-bit or 256-bit (default)

!!! question "When to use?"
    It's the right default choice unless you have very specific, use case-driven reasons for one of the other options explained further below. The randomness prevents statistical analysis and makes certain attack models for cryptanalysis significantly harder.

!!! warning "Do not use for Kafka record keys"
    Applying probabilistic encryption to a Kafka record key causes records with the same original key to land in different partitions. This breaks partitioning guarantees and record ordering. If you really plan to encrypt the whole key or parts thereof, switch to `TINK/AES_GCM_SIV` for keys instead.

### AES-GCM-SIV

`TINK/AES_GCM_SIV` is a nonce misuse-resistant variant. For the same plaintext and key, it always produces the same ciphertext.

**Properties:**

- **deterministic:** same plaintext + same key always produces the same ciphertext
- **authenticated:** tamper detection is retained
- **nonce misuse-resistant:** the cipher does not fail catastrophically if a nonce is repeated

!!! question "When to use?"
    Lookups / Joins / Aggregations on ciphertext require this deterministic encryption property to work correctly. Kafka record keys or parts thereof can only be encrypted deterministically in order not to break the partitioning and ordering of records.

### FPE FF3-1 

`CUSTOM/MYSTO_FPE_FF3_1` is based on the NIST-standardised FF3-1 algorithm. It's a format-preserving encryption (FPE) algorithm.

**Properties:**

- **length-preserving:** a 16-digit number encrypts to different 16-digit number
- **character-set-preserving:** ciphertext uses only characters from the configured alphabet matching the input data's character set
- **deterministic with tweak:** same plaintext + same key + same tweak = same ciphertext

!!! question "When to use?"
    For payload fields of type string you want/must preserve the format for. Examples include credit card numbers, SSNs, or phone numbers. Also, if your target system such as a database has table columns definitions with strict format constraints and changing the column type would be impractical. In general, whenever any downstream system cannot handle Base64 encoded ciphertexts resulting from the other non-FPE algorithms.

!!! warning "Trade-offs"
    It's generally considered weaker security-wise than standard AEAD ciphers. Use it only where format preservation is a hard requirement. Also, this cipher has a minimum plaintext length requirement depending on alphabet size (e.g., the `DIGITS` alphabet  requires at least 6 characters of input data).
    
!!! danger "Tweak Choice"    
    Tweak values should ideally chosen to be unique per field type to prevent cross-field ciphertext correlation!

---

## Key Material Security

1. **Try to avoid working with plain keysets** and don't source them from configuration as this could easily cause keys to be leaked by accident.

2. Ideally default to **either encrypted keysets in configuration** or **plain keysets in cloud secret managers.** Both approaches provide reasonable security for your keysets and are significantly better than just going with plain keysets in configuration for convenience.

3. **Prefer `KMS_ENCRYPTED` for maximum security in production.** Keysets are stored encrypted in a cloud secret manager which means an attacker would need both secret manager access and possession of the key encryption key to be able to decrypt any ciphertext.

4. **Rotate keys periodically.** Tink's multi-key keyset design makes key rotation straightforward. Promote another key in the keyset as the new primary, leave old keys in the keyset for backward-compatible decryption and add new keys to the keyset whenever needed. Optionally phase out / delete keys from the keysets for which you are certain that you don't need them any longer due to data retention. 

5. **Use separate key encryption keys (KEK)** for different environments. Never share a production KEK with staging or development.

6. **Limit IAM permissions for cloud KMS usage** to the minimum required:
    - secret manager access: `get` + `list` for the k4k-prefixed secrets only
    - key encryption key access: `encrypt` + `decrypt` (or `wrapKey` + `unwrapKey` for Azure) on the cloud KMS key in question

!!! danger "Key material is the root of trust"
    Exposure of a Tink keyset's `value` field is equivalent to total loss of confidentiality for all data encrypted with that key. Treat it with the same care as an important private key or password. **NEVER commit plain keysets to source control - ever!**


---

## Threat Model

| Threat | Mitigation |
|---|---|
| Attacker gets access to partially encrypted Kafka topic data | Due to AEAD encryption the ciphertext is unintelligible without access to the keyset. Breaking AES_GCM itself or running a brute-force attack against a 256-bit key is considered largely impractical at the moment. |
| Attacker tries to run statistical analysis on ciphertexts | Probabilistic AES-GCM produces unique ciphertexts per encryption operation for the same input text which prevents common attacks in that regard. |
| Attacker tampers with ciphertext or the attached metadata | The authentication tag in AES_GCM will make the decryption fail due to an integrity error. |
| Attacker somehow gets unauthorized access to keysets from the configuration | Instead of plain keysets stored in the configuration (`key_source=CONFIG`) either use `key_source=KMS` to externalize keysets to a cloud secret manager or switch to `key_source=CONFIG_ENCRYPTED` to work with encrypted keysets based on a key encryption key. The encrypted keyset is useless without access to the KEK that is separately stored in a cloud KMS. |
| Attacker manages to access the cloud secret manager secrets | Use `key_source=KMS_ENCRYPTED` to work with encrypted keysets based on a key encryption key. The encrypted keyset is useless without access to the KEK that is separately stored in a cloud KMS. |
| Attacker "magically" gets access to the partially encrypted Kafka topic data, the encrypted keysets stored in the cloud secret manager, and the corresponding key encryption key from the cloud KMS | **💀 NONE - YOU'RE REALLY F... ! 💥** |

---

## Dependencies

Cryptographic operations are implemented via **[Google Tink](https://github.com/tink-crypto/)** (Java), a peer-reviewed, audited multi-language cryptography library. Kryptonite for Kafka does not implement any cryptographic primitives itself — all AEAD operations delegate to Tink. The FPE implementation uses the [**mysto**](https://github.com/mysto) library for the FF3-1 algorithm. A custom Tink key type is used for FPE to transparently integrate with Tink's keyset mechanism.

!!! note "Why FF3-1 for format-preserving encryption?"
    Since NIST has officially withdrawn FF3 in 2025, FF1 should have preferrably been chosed as the FPE cipher algorithm in Kryptonite for Kafka. However, there are still patent claims and copyrights which forbid its usage in open-source software. As there aren't any other significantly better FPE cipher alternatives left, FF3-1 was chosen as the next best thing that has been NIST vetted and at the same time has seen some reasonable adoption in the field.
