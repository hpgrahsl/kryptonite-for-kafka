package com.github.hpgrahsl.kryptonite.config;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigReader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static Map<String, TinkKeyConfig> tinkKeyConfigFromJsonString(String json) {
        try {
            var dataKeyConfig = OBJECT_MAPPER.readValue(
                    json, new TypeReference<Set<DataKeyConfig>>() {}
            );
            return dataKeyConfig.stream().collect(
                    Collectors.toMap(DataKeyConfig::getIdentifier, DataKeyConfig::getMaterial)
            );
        } catch (JsonProcessingException exc) {
            throw new ConfigurationException(exc);
        }
    }

    public static Map<String, TinkKeyConfigEncrypted> tinkKeyConfigEncryptedFromJsonString(String json) {
        try {
            var dataKeyConfigEncrypted = OBJECT_MAPPER.readValue(
                    json, new TypeReference<Set<DataKeyConfigEncrypted>>() {}
            );
            return dataKeyConfigEncrypted.stream().collect(
                    Collectors.toMap(DataKeyConfigEncrypted::getIdentifier, DataKeyConfigEncrypted::getMaterial)
            );
        } catch (JsonProcessingException exc) {
            throw new ConfigurationException(exc);
        }
    }

}
