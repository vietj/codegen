package io.vertx.test.codegen.converter;

import io.vertx.core.json.JsonCodec;

import java.time.ZonedDateTime;

public class ZonedDateTimeCodec implements JsonCodec<ZonedDateTime, String> {

  public static final ZonedDateTimeCodec INSTANCE = new ZonedDateTimeCodec();

  @Override
  public String encode(ZonedDateTime value) {
    return value.toString();
  }

  @Override
  public ZonedDateTime decode(String value) {
    return ZonedDateTime.parse(value);
  }
}
