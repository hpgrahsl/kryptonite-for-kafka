package com.github.hpgrahsl.kryptonite.edek;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KCacheEdekStoreUnitTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName("init(): missing bootstrap.servers throws IllegalArgumentException")
  void testInitMissingBootstrapServers() throws Exception {
    var store = new KCacheEdekStore();
    String config = MAPPER.writeValueAsString(Map.of(
        "kafkacache.topic", "edek-store"
    ));
    var ex = assertThrows(Exception.class, () -> store.init(config));
    assertInstanceOf(IllegalArgumentException.class, ex);
  }

  @Test
  @DisplayName("init(): missing topic throws IllegalArgumentException")
  void testInitMissingTopic() throws Exception {
    var store = new KCacheEdekStore();
    String config = MAPPER.writeValueAsString(Map.of(
        "kafkacache.bootstrap.servers", "localhost:9092"
    ));
    var ex = assertThrows(Exception.class, () -> store.init(config));
    assertInstanceOf(IllegalArgumentException.class, ex);
  }

  @Test
  @DisplayName("put(): before init() throws IllegalStateException")
  void testPutBeforeInitThrows() {
    var store = new KCacheEdekStore();
    byte[] fp = new byte[16];
    byte[] wrappedDek = new byte[32];
    assertThrows(IllegalStateException.class, () -> store.put(fp, wrappedDek));
  }

  @Test
  @DisplayName("get(): before init() throws IllegalStateException")
  void testGetBeforeInitThrows() {
    var store = new KCacheEdekStore();
    byte[] fp = new byte[16];
    assertThrows(IllegalStateException.class, () -> store.get(fp));
  }

  @Test
  @DisplayName("close(): before init() does not throw")
  void testCloseBeforeInitIsNoOp() {
    var store = new KCacheEdekStore();
    assertDoesNotThrow(store::close);
  }

}
