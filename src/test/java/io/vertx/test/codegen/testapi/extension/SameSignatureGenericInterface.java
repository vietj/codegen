package io.vertx.test.codegen.testapi.extension;

import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface SameSignatureGenericInterface<T> {

  public void m(T t);

}
