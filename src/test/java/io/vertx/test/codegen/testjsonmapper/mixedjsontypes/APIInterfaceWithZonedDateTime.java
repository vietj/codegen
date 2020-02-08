package io.vertx.test.codegen.testjsonmapper.mixedjsontypes;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.time.ZonedDateTime;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface APIInterfaceWithZonedDateTime {

  @GenIgnore
  Function<String, ZonedDateTime> deserializer = null;

  @GenIgnore
  Function<ZonedDateTime, Integer> serializer = null;

}
