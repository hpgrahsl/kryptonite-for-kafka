package com.github.hpgrahsl.kryptonite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;

public class KryptoniteTest {

    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kryptonite.KryptoniteTest#provideValidInputParamsLocalKeyVaultNoKeyEncryption")
    @DisplayName("test decrypt(encrypt(plaintext)) == plaintext")
    void testEncryptDecryptUsingLocalKeyVaultWithoutKeyEncryption(AbstractKeyVault keyVault, byte[] originalData, PayloadMetaData metaData) {
        var kryptonite = new Kryptonite(keyVault);
        assertArrayEquals(originalData,kryptonite.decipherField(kryptonite.cipherField(originalData, metaData)));
    }

    static List<Arguments> provideValidInputParamsLocalKeyVaultNoKeyEncryption() {
        var tinkKeyVault = new TinkKeyVault(ConfigReader.tinkKeyConfigFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG));
        return List.of(
            Arguments.of(
                tinkKeyVault,
                "alice".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION,
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)),
                "keyA")
            ),
            Arguments.of(
                tinkKeyVault,
                "bob".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION,
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)),
                "keyB")
            ),
            Arguments.of(
                tinkKeyVault,
                "hello".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION,
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)),
                "key9")
            ),
            Arguments.of(
                tinkKeyVault,
                "kryptonite".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION,
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)),
                "key8")
            )
        );
    }

}
