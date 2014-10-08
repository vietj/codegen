package io.vertx.test.codegen.testapi.extension;

import io.vertx.codegen.annotations.Extension;
import io.vertx.codegen.annotations.Native;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class NativeExtension {

  /**
   * <native:java>
   *   somejava
   * </native:java>
   * <native:js>
   *   somejs
   * </native:js>
   */
  @Native
  @Extension
  public static Locale nativeExtensionMethod(Extended extended, ResourceBundle bundle) {
    throw new UnsupportedOperationException();
  }

}
