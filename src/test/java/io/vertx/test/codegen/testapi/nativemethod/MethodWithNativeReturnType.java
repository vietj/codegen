package io.vertx.test.codegen.testapi.nativemethod;

import io.vertx.codegen.annotations.Native;
import io.vertx.codegen.annotations.VertxGen;

import java.util.Locale;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MethodWithNativeReturnType {

  /**
   * <native:java></native:java>
   */
  @Native
  Locale method();

}
