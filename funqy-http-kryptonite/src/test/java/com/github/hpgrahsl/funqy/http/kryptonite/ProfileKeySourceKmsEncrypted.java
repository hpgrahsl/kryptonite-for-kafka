package com.github.hpgrahsl.funqy.http.kryptonite;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.provider.Arguments;

import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ProfileKeySourceKmsEncrypted implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        try {
            var credentials = TestFixturesCloudKms.readCredentials();
            return Map.ofEntries(
                Map.entry("cipher.data.keys",TestFixtures.CIPHER_DATA_KEYS_EMPTY),
                Map.entry("cipher.data.key.identifier","keyX"),
                Map.entry("key.source","KMS_ENCRYPTED"),
                Map.entry("kms.type","AZ_KV_SECRETS"),
                Map.entry("kms.config",credentials.getProperty("test.kms.config.encrypted")),
                Map.entry("kek.type","GCP"),
                Map.entry("kek.config",credentials.getProperty("test.kek.config")),
                Map.entry("kek.uri",credentials.getProperty("test.kek.uri")),
                Map.entry("dynamic.key.id.prefix","__#"),
                Map.entry("path.delimiter","."),
                Map.entry("field.mode","ELEMENT"),
                Map.entry("cipher.algorithm","TINK/AES_GCM")
            );
        } catch (IOException e) {
            throw new RuntimeException("couldn't load credential props");
        }
    }
    
    static List<Arguments> generateValidParamCombinations() {
        return List.of(
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyX","keyY"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyY","keyX"
            ),
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key1","key0"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key0","key1"
            )
        );
    }
    
}
