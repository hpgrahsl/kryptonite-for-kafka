# Security

## Encryption Algorithms

### AES-GCM — Probabilistic AEAD

`TINK/AES_GCM` is the default algorithm. It applies AES in Galois/Counter Mode with a fresh random IV for every encryption call.

**Properties:**
- Probabilistic: encrypting the same plaintext twice produces different ciphertexts
- Authenticated: the ciphertext includes a GCM authentication tag; any tampering is detected on decryption
- Key sizes: 128-bit or 256-bit (256-bit default)

**When to use:** Record values. The randomness prevents statistical analysis and makes known-plaintext attacks significantly harder.

!!! warning "Do not use for Kafka record keys"
    Applying probabilistic encryption to a Kafka record key causes records with the same original key to land in different partitions. This breaks partitioning guarantees and record ordering. Use `TINK/AES_GCM_SIV` for keys instead.

### AES-GCM-SIV — Deterministic AEAD

`TINK/AES_GCM_SIV` is a nonce-misuse-resistant variant. For the same plaintext and key, it always produces the same ciphertext.

**Properties:**
- Deterministic: same plaintext + same key → same ciphertext, always
- Authenticated: tamper detection is retained
- Nonce misuse resistant: immune to nonce reuse attacks (unlike AES-GCM)

**When to use:**

- **Kafka record keys** — deterministic encryption preserves partitioning; records with the same original key land in the same partition
- **Lookups and joins** — when the encrypted value needs to be used as a join key or lookup field
- Do not use when ciphertext distinguishability is a security concern (two identical plaintexts are visibly identical in ciphertext)

### FPE FF3-1 — Format-Preserving Encryption

`CUSTOM/MYSTO_FPE_FF3_1` uses the NIST-standardised FF3-1 algorithm. It encrypts data while preserving the original length and character set.

**Properties:**
- Format-preserving: a 16-digit number encrypts to a 16-digit number
- Character-set-preserving: ciphertext uses only characters from the configured alphabet
- Deterministic with tweak: same plaintext + same key + same tweak → same ciphertext

**When to use:**
- Credit card numbers, SSNs, phone numbers, postal codes
- Database columns with strict format constraints where changing the type is impractical
- Systems downstream that cannot handle Base64 strings

**Trade-offs compared to AES-GCM:**
- Generally considered weaker security properties than standard AEAD; use only where format preservation is a hard requirement
- Minimum plaintext length requirement based on alphabet size (e.g., `DIGITS` requires at least 6 characters)
- Tweak values should be unique per field type to prevent cross-field ciphertext correlation

---

## Encrypted Field Format (AEAD)

Encrypted field values are Base64-encoded strings that embed metadata alongside the ciphertext:

```
<version><algorithm-id><keyset-id><IV><ciphertext><GCM-tag>
```

The metadata allows the runtime to select the correct keyset and algorithm for decryption without storing this information externally. The metadata itself is authenticated (AAD) but not encrypted.

---

## Key Material Security

!!! danger "Key material is the root of trust"
    Exposure of a Tink keyset's `value` field is equivalent to total loss of confidentiality for all data encrypted with that key. Treat it with the same care as a private key or database password.

Recommendations:

1. **Never embed plain keysets directly in connector JSON** that is accessible via the Kafka Connect REST API. Use [file config provider externalisation](modules/connect-smt.md#externalising-key-material) or a cloud KMS (`key_source=KMS`).
2. **Prefer `KMS_ENCRYPTED`** in production: keysets are stored encrypted; an attacker needs both secret manager access and KEK access to decrypt anything.
3. **Rotate keys periodically.** Tink's multi-key keyset design makes rotation straightforward — add a new key, promote it to primary, leave old keys in the keyset for backward-compatible decryption.
4. **Use separate KEK instances** for separate environments. Never share a production KEK with staging or development.
5. **Limit IAM permissions** to the minimum required:
    - Secret manager access: `get` + `list` for the k4k-prefixed secrets only
    - KEK access: `encrypt` + `decrypt` (or `wrapKey` + `unwrapKey` for Azure) on the specific key

---

## Threat Model

| Threat | Mitigated by |
|---|---|
| Attacker reads Kafka topic data | AEAD encryption — ciphertext is unintelligible without the key |
| Attacker tampers with ciphertext | GCM authentication tag — decryption fails with an integrity error |
| Attacker replays a ciphertext from one field into another | Keyset identifier embedded in ciphertext metadata |
| Attacker reads connector configuration | Use `key_source=KMS` or externalise via file config provider |
| Attacker reads cloud secret manager secrets | Use `key_source=KMS_ENCRYPTED` with a separate KEK |
| Kafka record key partitioning broken by encryption | Use `TINK/AES_GCM_SIV` for deterministic encryption of keys |
| Statistical analysis of ciphertexts | Probabilistic AES-GCM produces unique ciphertexts per call |

---

## Dependencies

Cryptographic operations are implemented via **Google Tink** (Java), a peer-reviewed, audited multi-language cryptography library. Kryptonite for Kafka does not implement any cryptographic primitives itself — all AEAD operations delegate to Tink.

The FPE implementation uses the [**mysto/ffx**](https://github.com/mysto/java-fpe) library for the FF3-1 algorithm wrapped in a custom Tink key type.
