package io.vertx.test.codegen.testapi.nativemethod;

import io.vertx.codegen.annotations.Native;
import io.vertx.codegen.annotations.VertxGen;

import java.util.Locale;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MethodWithNativeParamType {

  /**
   * <native:java></native:java>
   */
  @Native
  void method(Locale locale);

}
