package com.github.hpgrahsl.kryptonite.crypto.custom.mysto.fpe;

import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.proto.KeyData;
import com.google.crypto.tink.proto.Keyset;
import com.google.protobuf.ByteString;

import java.security.GeneralSecurityException;

/**
 * Provides integration between FPE and Tink's KeysetHandle.
 *
 * This class allows you to:
 * 1. Generate FPE keysets that can be serialized/deserialized
 * 2. Store and load keysets from JSON
 * 3. Get FPE primitives from KeysetHandles
 *
 * Note: Due to Tink's restrictions on custom primitives in 1.17.0,
 * this uses a wrapper approach rather than direct Registry integration.
 */
public final class FpeKeysetHandle {
    
    private static final String TYPE_URL = "io.github.hpgrahsl.kryptonite/crypto.custom.mysto.fpe.FpeKey";
    
    private FpeKeysetHandle() {}

    /**
     * Extracts an FPE primitive from a KeysetHandle.
     *
     * This method reads the keyset and creates an FPE instance from the
     * primary key's data.
     */
    public static Fpe getPrimitive(KeysetHandle keysetHandle) throws GeneralSecurityException {
        try {
            // Get the keyset
            Keyset keyset = CleartextKeysetHandle.getKeyset(keysetHandle);

              //TODO: this method should validate the keyset handle for fpe usage
              //if the settings are not correct it should throw an exception

            // Find the primary key
            Keyset.Key primaryKey = null;
            for (Keyset.Key key : keyset.getKeyList()) {
                if (key.getKeyId() == keyset.getPrimaryKeyId()) {
                    primaryKey = key;
                    break;
                }
            }

            if (primaryKey == null) {
                throw new GeneralSecurityException("no primary key found in keyset");
            }

            // Get key data
            KeyData keyData = primaryKey.getKeyData();

            if (!TYPE_URL.equals(keyData.getTypeUrl())) {
                throw new GeneralSecurityException(
                    "expected FPE key (type url: "+ TYPE_URL +"), got: " + keyData.getTypeUrl());
            }

            // Deserialize the FPE key
            FpeKey fpeKey = deserializeKey(keyData.getValue());

            // Create and return the FPE primitive
            return new FpeImpl(fpeKey);

        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to extract FPE primitive", e);
        }
    }

    /**
     * Deserializes an FpeKey from ByteString.
     */
    private static FpeKey deserializeKey(ByteString byteString) throws GeneralSecurityException {
        //TODO: what does this do exactly? it should primarily deserialize the FpeKey proto
        //any validation should be done after the fact...
        byte[] data = byteString.toByteArray();
        if (data.length < 4) {
            throw new GeneralSecurityException("Invalid key data");
        }

        int alphabetLen = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) |
                         ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        if (data.length < 4 + alphabetLen) {
            throw new GeneralSecurityException("Invalid key data");
        }

        String alphabet = new String(data, 4, alphabetLen);
        byte[] keyValue = new byte[data.length - 4 - alphabetLen];
        System.arraycopy(data, 4 + alphabetLen, keyValue, 0, keyValue.length);

        return new FpeKey(keyValue, alphabet);
    }

}
