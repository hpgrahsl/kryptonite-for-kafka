package com.github.hpgrahsl.kryptonite.edek;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.github.hpgrahsl.kryptonite.keys.EdekStore;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FingerprintKeyTest {

  // -------------------------------------------------------------------------
  // FingerprintKey — equality and map behaviour
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("FingerprintKey: same bytes → equal and same hashCode")
  void testEqualKeysForSameBytes() {
    byte[] bytes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    var k1 = new FingerprintKey(bytes);
    var k2 = new FingerprintKey(bytes);
    assertEquals(k1, k2);
    assertEquals(k1.hashCode(), k2.hashCode());
  }

  @Test
  @DisplayName("FingerprintKey: different bytes → not equal")
  void testNotEqualForDifferentBytes() {
    var k1 = new FingerprintKey(new byte[]{1, 2, 3});
    var k2 = new FingerprintKey(new byte[]{1, 2, 4});
    assertNotEquals(k1, k2);
  }

  @Test
  @DisplayName("FingerprintKey: constructor defensively copies — external mutation does not affect key")
  void testDefensiveCopyOnConstruction() {
    byte[] original = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    var key = new FingerprintKey(original);
    original[0] = 99; // mutate source array after construction
    assertFalse(key.bytes()[0] == 99, "mutation of source array must not affect FingerprintKey");
  }

  @Test
  @DisplayName("FingerprintKey: bytes() returns a copy — caller mutation does not corrupt the key")
  void testBytesAccessorReturnsCopy() {
    byte[] original = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    var key = new FingerprintKey(original);
    byte[] a = key.bytes();
    byte[] b = key.bytes();
    assertNotSame(a, b, "bytes() must return a fresh copy each time");
    assertArrayEquals(a, b);
    a[0] = 99; // mutate the returned array
    assertEquals(1, key.bytes()[0], "mutation of returned array must not affect the key's internal state");
  }

  @Test
  @DisplayName("FingerprintKey: works correctly as HashMap key")
  void testAsMapKey() {
    byte[] fp = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    Map<FingerprintKey, byte[]> map = new HashMap<>();
    map.put(new FingerprintKey(fp), new byte[]{42});
    byte[] result = map.get(new FingerprintKey(fp));
    assertArrayEquals(new byte[]{42}, result, "lookup with equal key must find the stored value");
  }

  // -------------------------------------------------------------------------
  // EdekStore.fingerprint() static helper
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("fingerprint(): always returns exactly 16 bytes")
  void testFingerprintLength() {
    byte[] wrappedDek = new byte[32];
    byte[] fp = EdekStore.fingerprint(wrappedDek);
    assertEquals(16, fp.length);
  }

  @Test
  @DisplayName("fingerprint(): same input produces same fingerprint (deterministic)")
  void testFingerprintDeterministic() {
    byte[] wrappedDek = "some-wrapped-dek-bytes".getBytes();
    assertArrayEquals(EdekStore.fingerprint(wrappedDek), EdekStore.fingerprint(wrappedDek));
  }

  @Test
  @DisplayName("fingerprint(): different inputs produce different fingerprints")
  void testFingerprintDistinct() {
    byte[] a = "wrapped-dek-a".getBytes();
    byte[] b = "wrapped-dek-b".getBytes();
    assertFalse(
        java.util.Arrays.equals(EdekStore.fingerprint(a), EdekStore.fingerprint(b)),
        "distinct wrapped DEKs must produce distinct fingerprints");
  }

}
