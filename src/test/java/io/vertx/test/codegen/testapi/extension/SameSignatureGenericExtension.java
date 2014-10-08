package io.vertx.test.codegen.testapi.extension;

import io.vertx.codegen.annotations.Extension;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SameSignatureGenericExtension {

  @Extension
  public static <T> void m(SameSignatureGenericInterface<T> foo, T s) {
  }
}
