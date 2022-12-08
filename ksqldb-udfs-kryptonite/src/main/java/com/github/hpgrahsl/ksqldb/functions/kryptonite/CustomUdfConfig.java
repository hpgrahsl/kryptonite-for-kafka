package com.github.hpgrahsl.ksqldb.functions.kryptonite;

public class CustomUdfConfig {

    public enum KeySource {
        CONFIG,
        KMS
    }

    public enum KmsType {
        NONE,
        AZ_KV_SECRETS
    }

}
