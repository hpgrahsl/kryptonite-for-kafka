/*
 * Copyright (c) 2022. Hans-Peter Grahsl (grahslhp@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.hpgrahsl.funqy.http.kryptonite;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import com.github.hpgrahsl.funqy.http.kryptonite.KryptoniteConfiguration.FieldMode;
import com.github.hpgrahsl.kryptonite.CipherMode;
import io.quarkus.funqy.Funq;

@ApplicationScoped
public class CipherFieldResource {

    CipherFieldService cipherFieldService;
    
    public CipherFieldResource(CipherFieldService cipherFieldService) {
        this.cipherFieldService = cipherFieldService;
    }

    @Funq("encrypt/value")
    public String encryptValue(Object value) {
        return cipherFieldService.encryptData(value);
    }

    @Funq("encrypt/array")
    public String encryptArray(List<?> array) {
        return cipherFieldService.encryptData(array);
    }

    @Funq("encrypt/array-elements")
    public List<String> encryptArrayElements(List<?> array) {
        return array.stream()
                .map(v -> cipherFieldService.encryptData(v))
                .collect(Collectors.toList());
    }

    @Funq("encrypt/map")
    public String encryptMap(Map<String, ?> map) {
        return cipherFieldService.encryptData(map);
    }

    @Funq("encrypt/map-entries")
    public Map<String, String> encryptMapEntries(Map<String, ?> map) {
        return map.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(
                            e.getKey(),
                            cipherFieldService.encryptData(e.getValue())
                        )
                )
                .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    @Funq("encrypt/value-with-config")
    @SuppressWarnings("unchecked")
    public Object encryptValueWithConfig(KryptonitePayload kp) {

        Objects.requireNonNull(kp, "KryptonitePayload kp must not be null");

        var fieldConfig = Optional.ofNullable(kp.fieldConfig).map(
                fc -> fc.stream().collect(Collectors.toMap(FieldConfig::getName, Function.identity())))
                .orElse(new LinkedHashMap<>());

        if (fieldConfig.keySet().isEmpty()) {
            if (FieldMode.ELEMENT == cipherFieldService.getKryptoniteConfiguration().fieldMode) {
                return encryptMapEntries((Map<String, ?>) kp.data);
            }
            return encryptValue(kp.data);
        }

        return cipherFieldService.processDataWithFieldConfig(kp.data, fieldConfig, CipherMode.ENCRYPT);
    }

    @Funq("decrypt/value")
    public Object decryptValue(String value) {
        return cipherFieldService.decryptData(value);
    }

    @Funq("decrypt/array")
    public List<?> decryptArray(String array) {
        return (List<?>)cipherFieldService.decryptData(array);
    }

    @Funq("decrypt/array-elements")
    public List<?> decryptArrayElements(List<String> array) {
        return array.stream()
                .map(cipherFieldService::decryptData)
                .collect(Collectors.toList());
    }

    @Funq("decrypt/map")
    @SuppressWarnings("unchecked")
    public Map<String, ?> decryptMap(String map) {
        return (Map<String, ?>)cipherFieldService.decryptData(map);
    }

    @Funq("decrypt/map-entries")
    public Map<String, ?> decryptMapEntries(Map<String, String> map) {
        return map.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), cipherFieldService.decryptData(e.getValue())))
                .collect(LinkedHashMap::new, (lhm, e) -> lhm.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    @Funq("decrypt/value-with-config")
    @SuppressWarnings("unchecked")
    public Object decryptValueWithConfig(KryptonitePayload kp) {

        Objects.requireNonNull(kp, "KryptonitePayload kp must not be null");

        var fieldConfig = Optional.ofNullable(kp.fieldConfig).map(
                fc -> fc.stream().collect(Collectors.toMap(FieldConfig::getName, Function.identity())))
                .orElse(new LinkedHashMap<>());

        if (fieldConfig.keySet().isEmpty()) {
            if (FieldMode.ELEMENT == cipherFieldService.getKryptoniteConfiguration().fieldMode) {
                return decryptMapEntries((Map<String, String>) kp.data);
            }
            return decryptValue((String) kp.data);
        }

        return cipherFieldService.processDataWithFieldConfig(kp.data, fieldConfig, CipherMode.DECRYPT);
    }

}
