package io.vertx.test.codegen.testapi.nativemethod;

import io.vertx.codegen.annotations.Native;
import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MethodWithNativeImplementations {

  /**
   * <native:java>
   *   somejava
   * </native:java>
   * <native:js>
   *   somejs
   * </native:js>
   */
  @Native
  void method();

}
