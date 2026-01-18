package com.github.hpgrahsl.kryptonite;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.esotericsoftware.kryo.io.Input;
import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.serdes.KryoInstance;
import com.github.hpgrahsl.kryptonite.serdes.KryoSerdeProcessor;

public class TestIssueArray {

    @Test
    void testIssueArrayHandling() {
        var keyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG));
        var kryptonite = new Kryptonite(keyVault);

        var ciphertextFromFlink = "LAE7msoBh8gGIGrWwDDYZbbhcQEJC/kK+2kAdtnhXJ797VE6uZEzOFV6O0AMMLJrZXnBa7E="; 
        //var cipherTextFromFunqy = "KgE7msoBtubtbi8lLGne3T+gKbKm4iLNDLlUB6BqWCzBtYuob6A2YaeODDCya2V5wWux";
        var encryptedField = KryoInstance.get().readObject(
                new Input(Base64.getDecoder()
                        .decode(ciphertextFromFlink)),
                        //.decode(cipherTextFromFunqy)),
                EncryptedField.class);
        System.out.println(encryptedField);
        var plaintextFlink = kryptonite.decipherField(encryptedField);
        var serde = new KryoSerdeProcessor();
        var restored = serde.bytesToObject(plaintextFlink);
        System.out.println(restored.getClass());
        System.out.println(restored);
    }

}
