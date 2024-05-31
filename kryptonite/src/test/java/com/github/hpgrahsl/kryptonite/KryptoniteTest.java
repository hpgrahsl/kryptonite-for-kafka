package com.github.hpgrahsl.kryptonite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.github.hpgrahsl.kryptonite.Kryptonite.CipherSpec;
import com.github.hpgrahsl.kryptonite.config.ConfigReader;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcm;
import com.github.hpgrahsl.kryptonite.crypto.tink.TinkAesGcmSiv;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVault;
import com.github.hpgrahsl.kryptonite.keys.TinkKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVault;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureKeyVaultEncrypted;
import com.github.hpgrahsl.kryptonite.kms.azure.AzureSecretResolver;

public class KryptoniteTest {
    
    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kryptonite.KryptoniteTest#provideValidInputParamsLocalKeyVaultNoKeyEncryption")
    @DisplayName("test decrypt(encrypt(plaintext)) == plaintext")
    void testEncryptDecryptUsingLocalKeyVaultWithoutKeyEncryption(AbstractKeyVault keyVault, byte[] originalData, PayloadMetaData metaData) {
        var kryptonite = new Kryptonite(keyVault);
        assertArrayEquals(originalData,kryptonite.decipherField(kryptonite.cipherField(originalData, metaData)));
    }

    @EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kryptonite.KryptoniteTest#provideValidInputParamsLocalKeyVaultKeyEncryption")
    @DisplayName("test decrypt(encrypt(plaintext)) == plaintext")
    void testEncryptDecryptUsingLocalKeyVaultWithKeyEncryption(AbstractKeyVault keyVault, byte[] originalData, PayloadMetaData metaData) {
        var kryptonite = new Kryptonite(keyVault);
        assertArrayEquals(originalData,kryptonite.decipherField(kryptonite.cipherField(originalData, metaData)));
    }

    @EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kryptonite.KryptoniteTest#provideValidInputParamsCloudKeyVaultNoKeyEncryption")
    @DisplayName("test decrypt(encrypt(plaintext)) == plaintext")
    void testEncryptDecryptUsingCloudKeyVaultWithoutKeyEncryption(AbstractKeyVault keyVault, byte[] originalData, PayloadMetaData metaData) {
        var kryptonite = new Kryptonite(keyVault);
        assertArrayEquals(originalData,kryptonite.decipherField(kryptonite.cipherField(originalData, metaData)));
    }

    @EnabledIfSystemProperty(named = "cloud.kms.tests", matches = "true")
    @ParameterizedTest
    @MethodSource("com.github.hpgrahsl.kryptonite.KryptoniteTest#provideValidInputParamsCloudKeyVaultKeyEncryption")
    @DisplayName("test decrypt(encrypt(plaintext)) == plaintext")
    void testEncryptDecryptUsingCloudKeyVaultWithKeyEncryption(AbstractKeyVault keyVault, byte[] originalData, PayloadMetaData metaData) {
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

    static List<Arguments> provideValidInputParamsLocalKeyVaultKeyEncryption() {
        var tinkKeyConfig = ConfigReader.tinkKeyConfigEncryptedFromJsonString(TestFixtures.CIPHER_DATA_KEYS_CONFIG_ENCRYPTED);
        var tinkKeyVaultEncrypted = new TinkKeyVaultEncrypted(tinkKeyConfig,TestFixturesCloudKms.configureKmsKeyEncryption());
        return List.of(
            Arguments.of(
                tinkKeyVaultEncrypted,
                "alice".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)), 
                "keyX")
            ),
            Arguments.of(
                tinkKeyVaultEncrypted,
                "bob".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)), 
                "keyY")
            ),
            Arguments.of(
                tinkKeyVaultEncrypted,
                "hello".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)), 
                "key1")
            ),
            Arguments.of(
                tinkKeyVaultEncrypted,
                "kryptonite".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)), 
                "key0")
            )
        );
    }

    static List<Arguments> provideValidInputParamsCloudKeyVaultNoKeyEncryption() {
        var SECRET_RESOLVER_PLAIN_KEYS =
            new AzureSecretResolver(TestFixturesCloudKms.readCredentials().getProperty("test.kms.az_kv_secrets.config"));
        var azureKeyVault = new AzureKeyVault(SECRET_RESOLVER_PLAIN_KEYS,true);
        return List.of(
            Arguments.of(
                azureKeyVault,
                "alice".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)), 
                "keyA")
            ),
            Arguments.of(
                azureKeyVault,
                "bob".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)), 
                "keyB")
            ),
            Arguments.of(
                azureKeyVault,
                "hello".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)), 
                "key9")
            ),
            Arguments.of(
                azureKeyVault,
                "kryptonite".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)), 
                "key8")
            )
        );
    }

    static List<Arguments> provideValidInputParamsCloudKeyVaultKeyEncryption() {
        var azureKeyVaultEncrypted = new AzureKeyVaultEncrypted(
            TestFixturesCloudKms.configureKmsKeyEncryption(),
            new AzureSecretResolver(TestFixturesCloudKms.readCredentials().getProperty("test.kms.az_kv_secrets.config.encrypted")),
            true
        );
        return List.of(
            Arguments.of(
                azureKeyVaultEncrypted,
                "alice".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)), 
                "keyX")
            ),
            Arguments.of(
                azureKeyVaultEncrypted,
                "bob".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcm.CIPHER_ALGORITHM)), 
                "keyY")
            ),
            Arguments.of(
                azureKeyVaultEncrypted,
                "hello".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)), 
                "key1")
            ),
            Arguments.of(
                azureKeyVaultEncrypted,
                "kryptonite".getBytes(StandardCharsets.UTF_8),
                new PayloadMetaData(Kryptonite.KRYPTONITE_VERSION, 
                Kryptonite.CIPHERSPEC_ID_LUT.get(CipherSpec.fromName(TinkAesGcmSiv.CIPHER_ALGORITHM)), 
                "key0")
            )
        );
    }

}
