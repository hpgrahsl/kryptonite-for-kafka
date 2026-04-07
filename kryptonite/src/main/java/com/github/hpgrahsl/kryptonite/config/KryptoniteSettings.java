package com.github.hpgrahsl.kryptonite.config;

public class KryptoniteSettings {

  public enum KeySource {
    CONFIG,
    KMS,
    CONFIG_ENCRYPTED,
    KMS_ENCRYPTED
  }

  public enum KmsType {
    NONE,
    AZ_KV_SECRETS,
    AWS_SM_SECRETS,
    GCP_SM_SECRETS
  }

  public enum KekType {
    NONE,
    GCP,
    AWS,
    AZURE
  }

  public enum SerdeType {
    KRYO,
    AVRO
  }

  public enum AlphabetTypeFPE {
    
    DIGITS("0123456789"),
    ALPHANUMERIC("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"),
    ALPHANUMERIC_EXTENDED("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz _,.!?@%$&§\"'°^-+*/;:#(){}[]<>=~|"),
    UPPERCASE("ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    LOWERCASE("abcdefghijklmnopqrstuvwxyz"),
    HEXADECIMAL("0123456789ABCDEF"),
    CUSTOM(null); //defined via separate configuration property

    private final String alphabet;

    AlphabetTypeFPE(String alphabet) {
      this.alphabet = alphabet;
    } 

    public String getAlphabet() {
      return alphabet;
    }

  }

  public static final String FIELD_CONFIG = "field_config";
  public static final String PATH_DELIMITER = "path_delimiter";
  public static final String FIELD_MODE = "field_mode";
  public static final String CIPHER_ALGORITHM = "cipher_algorithm";
  public static final String CIPHER_DATA_KEY_IDENTIFIER = "cipher_data_key_identifier";
  public static final String CIPHER_DATA_KEYS = "cipher_data_keys";
  public static final String CIPHER_TEXT_ENCODING = "cipher_text_encoding";
  public static final String CIPHER_FPE_TWEAK = "cipher_fpe_tweak";
  public static final String CIPHER_FPE_ALPHABET_TYPE = "cipher_fpe_alphabet_type";
  public static final String CIPHER_FPE_ALPHABET_CUSTOM = "cipher_fpe_alphabet_custom";
  public static final String CIPHER_MODE = "cipher_mode";
  public static final String KEY_SOURCE = "key_source";
  public static final String KMS_TYPE = "kms_type";
  public static final String KMS_CONFIG = "kms_config";
  public static final String KMS_REFRESH_INTERVAL_MINUTES = "kms_refresh_interval_minutes";
  public static final String DEK_CACHE_SIZE = "dek_cache_size";
  public static final String DEK_MAX_ENCRYPTIONS = "dek_max_encryptions";
  public static final String DEK_TTL_MINUTES = "dek_ttl_minutes";

  public static final String KEK_TYPE = "kek_type";
  public static final String KEK_CONFIG = "kek_config";
  public static final String KEK_URI = "kek_uri";
  public static final String SERDE_TYPE = "serde_type";

  public static final String PATH_DELIMITER_DEFAULT = ".";
  public static final String FIELD_MODE_DEFAULT = "ELEMENT";
  public static final String CIPHER_ALGORITHM_DEFAULT = "TINK/AES_GCM";
  public static final String CIPHER_DATA_KEY_IDENTIFIER_DEFAULT = "";
  public static final String CIPHER_DATA_KEYS_DEFAULT = "[]";
  public static final String CIPHER_TEXT_ENCODING_DEFAULT = "BASE64";
  public static final String CIPHER_FPE_TWEAK_DEFAULT = "0000000";
  public static final String CIPHER_FPE_ALPHABET_TYPE_DEFAULT = "ALPHANUMERIC";
  public static final String CIPHER_FPE_ALPHABET_CUSTOM_DEFAULT = "";
  public static final String KEY_SOURCE_DEFAULT = "CONFIG";
  public static final String KMS_TYPE_DEFAULT = "NONE";
  public static final String KMS_CONFIG_DEFAULT = "{}";
  public static final int KMS_REFRESH_INTERVAL_MINUTES_DEFAULT = 0;
  public static final int DEK_CACHE_SIZE_DEFAULT = 1024;
  public static final long DEK_MAX_ENCRYPTIONS_DEFAULT = 100_000L;
  public static final long DEK_TTL_MINUTES_DEFAULT = 720L;
  public static final String KEK_TYPE_DEFAULT = "NONE";
  public static final String KEK_CONFIG_DEFAULT = "{}";
  public static final String KEK_URI_DEFAULT = "xyz-kms://";
  public static final String SERDE_TYPE_DEFAULT = "KRYO";

}
