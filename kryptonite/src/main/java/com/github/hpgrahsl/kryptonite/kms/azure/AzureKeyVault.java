package com.github.hpgrahsl.kryptonite.kms.azure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hpgrahsl.kryptonite.config.TinkKeyConfig;
import com.github.hpgrahsl.kryptonite.keys.AbstractKeyVault;
import com.github.hpgrahsl.kryptonite.keys.KeyInvalidException;
import com.github.hpgrahsl.kryptonite.keys.KeyMaterialResolver;
import com.github.hpgrahsl.kryptonite.keys.NoOpKeyStrategy;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetReader;
import com.google.crypto.tink.KeysetHandle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AzureKeyVault extends AbstractKeyVault {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final KeyMaterialResolver keyMaterialResolver;
  private final Map<String, TinkKeyConfig> keyConfigs = new HashMap<>();
  private final Map<String, KeysetHandle> keysetHandles = new HashMap<>();

  public AzureKeyVault(KeyMaterialResolver keyMaterialResolver) {
    this(keyMaterialResolver,false);
  }

  public AzureKeyVault(KeyMaterialResolver keyMaterialResolver, boolean prefetch) {
    super(new NoOpKeyStrategy());
    this.keyMaterialResolver = keyMaterialResolver;
    if (prefetch) {
      warmUpKeyCache();
    }
  }

  private void warmUpKeyCache() {
    keyMaterialResolver.resolveIdentifiers().forEach(this::fetchIntoKeyCache);
  }

  private void fetchIntoKeyCache(String identifier) {
    try {
      String rawConfig = keyMaterialResolver.resolveKeyset(identifier);
      keyConfigs.put(identifier, OBJECT_MAPPER.readValue(rawConfig, TinkKeyConfig.class));
      keysetHandles.put(identifier, CleartextKeysetHandle.read(JsonKeysetReader.withString(rawConfig)));
    } catch (Exception e) {
      throw new KeyInvalidException("invalid key config for identifier '"
          +identifier+"' in "+ AzureKeyVault.class.getName() + " key vault",e);
    }
  }

  @Override
  public TinkKeyConfig readKeyConfig(String identifier) {
    var keyConfig = keyConfigs.get(identifier);
    if (keyConfig == null) {
      fetchIntoKeyCache(identifier);
      keyConfig = keyConfigs.get(identifier);
    }
    return keyConfig;
  }

  @Override
  public KeysetHandle readKeysetHandle(String identifier) {
    var keysetHandle = keysetHandles.get(identifier);
    if(keysetHandle == null) {
      fetchIntoKeyCache(identifier);
      keysetHandle = keysetHandles.get(identifier);
    }
    return keysetHandle;
  }

  @Override
  public byte[] readKey(String identifier) {
    var keyConfig = keyConfigs.get(identifier);
    if(keyConfig == null) {
      fetchIntoKeyCache(identifier);
      keyConfig = keyConfigs.get(identifier);
    }
    var keyBytes = keyConfig.getKeyBytesForEnabledPkId();
    //strip off first 2 key bytes which are meta-data related while the rest is the raw key material
    var rawKeyBytes = Arrays.copyOfRange(keyBytes,TinkKeyConfig.RAW_KEY_BYTES_OFFSET,keyBytes.length);
    return keyStrategy.processKey(rawKeyBytes,identifier);
  }

}
