package io.vertx.test.codegen.testapi.future;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.codegen.testmodel.TestDataObject;
import io.vertx.codegen.testmodel.TestEnum;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.test.codegen.testapi.VertxGenClass1;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface MethodWithValidFutureReturn {

  Future<Byte> byteFuture();
  Future<Short> shortFuture();
  Future<Integer> integerFuture();
  Future<Long> longFuture();
  Future<Float> floatFuture();
  Future<Double> doubleFuture();
  Future<Boolean> booleanFuture();
  Future<Character> characterFuture();
  Future<String> stringFuture();
  Future<VertxGenClass1> vertxGenFuture();
  Future<JsonObject> jsonObjectFuture();
  Future<JsonArray> jsonArrayFuture();
  Future<Void> voidFuture();
  Future<TestDataObject> dataObjectFuture();
  Future<TestEnum> enumFuture();

}
