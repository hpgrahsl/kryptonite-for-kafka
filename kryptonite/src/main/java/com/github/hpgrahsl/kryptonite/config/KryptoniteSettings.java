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
    AZ_KV_SECRETS
  }

  public enum KekType {
    NONE,
    GCP
  }

  public static final String FIELD_CONFIG = "field_config";
  public static final String PATH_DELIMITER = "path_delimiter";
  public static final String FIELD_MODE = "field_mode";
  public static final String CIPHER_ALGORITHM = "cipher_algorithm";
  public static final String CIPHER_DATA_KEY_IDENTIFIER = "cipher_data_key_identifier";
  public static final String CIPHER_DATA_KEYS = "cipher_data_keys";
  public static final String CIPHER_TEXT_ENCODING = "cipher_text_encoding";
  public static final String CIPHER_MODE = "cipher_mode";
  public static final String KEY_SOURCE = "key_source";
  public static final String KMS_TYPE = "kms_type";
  public static final String KMS_CONFIG = "kms_config";
  public static final String KEK_TYPE = "kek_type";
  public static final String KEK_CONFIG = "kek_config";
  public static final String KEK_URI = "kek_uri";

  public static final String PATH_DELIMITER_DEFAULT = ".";
  public static final String FIELD_MODE_DEFAULT = "ELEMENT";
  public static final String CIPHER_ALGORITHM_DEFAULT = "TINK/AES_GCM";
  public static final String CIPHER_DATA_KEY_IDENTIFIER_DEFAULT = "";
  public static final String CIPHER_DATA_KEYS_DEFAULT = "[]";
  public static final String CIPHER_TEXT_ENCODING_DEFAULT = "BASE64";
  public static final String KEY_SOURCE_DEFAULT = "CONFIG";
  public static final String KMS_TYPE_DEFAULT = "NONE";
  public static final String KMS_CONFIG_DEFAULT = "{}";
  public static final String KEK_TYPE_DEFAULT = "NONE";
  public static final String KEK_CONFIG_DEFAULT = "{}";
  public static final String KEK_URI_DEFAULT = "xyz-kms://";

}
