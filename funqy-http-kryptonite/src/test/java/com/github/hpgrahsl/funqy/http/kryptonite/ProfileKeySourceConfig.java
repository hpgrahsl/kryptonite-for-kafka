package com.github.hpgrahsl.funqy.http.kryptonite;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.provider.Arguments;

import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;

import io.quarkus.test.junit.QuarkusTestProfile;

public class ProfileKeySourceConfig implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
            Map.entry("cipher.data.keys",TestFixtures.CIPHER_DATA_KEYS_CONFIG),
            Map.entry("cipher.data.key.identifier","keyA"),
            Map.entry("key.source","CONFIG"),
            Map.entry("kms.type","NONE"),
            Map.entry("kms.config","{}"),
            Map.entry("kek.type","NONE"),
            Map.entry("kek.config","{}"),
            Map.entry("kek.uri","gcp-kms://"),
            Map.entry("dynamic.key.id.prefix","__#"),
            Map.entry("path.delimiter","."),
            Map.entry("field.mode","ELEMENT"),
            Map.entry("cipher.algorithm","TINK/AES_GCM")
        );
    }
    
    static List<Arguments> generateValidParamCombinations() {
        return List.of(
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyA","keyB"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM),"keyB","keyA"
            ),
            Arguments.of(
                FieldMode.ELEMENT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key9","key8"
            ),
            Arguments.of(
                FieldMode.OBJECT,CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM),"key8","key9"
            )
        );
    }
    
}
