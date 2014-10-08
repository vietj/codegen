package io.vertx.test.codegen.testapi.extension;

import io.vertx.codegen.annotations.Extension;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ExtendedMethods {

  @Extension
  public static void emptyParameters(Extended extended) {
    throw new UnsupportedOperationException();
  }

}
