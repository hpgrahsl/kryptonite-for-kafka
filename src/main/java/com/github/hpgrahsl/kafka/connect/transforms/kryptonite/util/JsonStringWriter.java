/*
 * Copyright (c) 2021. Hans-Peter Grahsl (grahslhp@gmail.com)
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

package com.github.hpgrahsl.kafka.connect.transforms.kryptonite.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JsonStringWriter<R extends ConnectRecord<R>> implements Transformation<R> {

  public static final ConfigDef EMPTY_CONFIG_DEF = new ConfigDef();

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOG = LoggerFactory.getLogger(JsonStringWriter.class);

  @Override
  public R apply(R r) {

    var data = operatingValue(r);

    if (data == null) {
      LOG.warn("data was null -> passing it through without SMT processing");
      return null;
    }

    if (!(data instanceof Map)) {
      LOG.error("unexpected data type: {}", data.getClass());
      throw new DataException("error: data expected to be of type Map but was "
          + data.getClass());
    }

    try {
      String jsonString = OBJECT_MAPPER.writeValueAsString(data);
      return newRecord(r, jsonString);
    } catch (JsonProcessingException e) {
      throw new DataException("error: processing the record value '"
          + data +"' failed", e);
    }

  }

  @Override
  public ConfigDef config() {
    return EMPTY_CONFIG_DEF;
  }

  @Override
  public void close() {

  }

  @Override
  public void configure(Map<String, ?> map) {

  }

  protected abstract Schema operatingSchema(R record);

  protected abstract Object operatingValue(R record);

  protected abstract R newRecord(R record, Object updatedValue);

  public static class Key<R extends ConnectRecord<R>> extends JsonStringWriter<R> {

    @Override
    protected Schema operatingSchema(R record) {
      return record.keySchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.key();
    }

    @Override
    protected R newRecord(R record, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), null, updatedValue,
          null, record.value(), record.timestamp());
    }

  }

  public static class Value<R extends ConnectRecord<R>> extends JsonStringWriter<R> {

    @Override
    protected Schema operatingSchema(R record) {
      return record.valueSchema();
    }

    @Override
    protected Object operatingValue(R record) {
      return record.value();
    }

    @Override
    protected R newRecord(R record, Object updatedValue) {
      return record.newRecord(record.topic(), record.kafkaPartition(), null, record.key(),
          null, updatedValue, record.timestamp());
    }

  }

}