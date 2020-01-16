package io.vertx.core.buffer;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
@DataObject
public interface Buffer {

  static Buffer fromJson(String encoded) {
    return buffer(java.util.Base64.getDecoder().decode(encoded));
  }

  static Buffer buffer(byte[] bytes) {
    return new Buffer() {
      @Override
      public byte[] getBytes() {
        return bytes;
      }
      @Override
      public boolean equals(Object obj) {
        if (obj instanceof Buffer) {
          Buffer that = (Buffer) obj;
          return Arrays.equals(bytes, that.getBytes());
        }
        return false;
      }
    };
  }

  byte[] getBytes();

  default String toJson() {
    return java.util.Base64.getEncoder().encodeToString(getBytes());
  }
}
