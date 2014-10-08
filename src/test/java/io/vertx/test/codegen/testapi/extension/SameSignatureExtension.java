package io.vertx.test.codegen.testapi.extension;

import io.vertx.codegen.annotations.Extension;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SameSignatureExtension {

  @Extension
  public static void m(SameSignatureInterface foo, String s) {
  }
}
