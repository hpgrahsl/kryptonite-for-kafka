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

}
