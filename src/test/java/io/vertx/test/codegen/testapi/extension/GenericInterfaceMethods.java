package io.vertx.test.codegen.testapi.extension;

import io.vertx.codegen.annotations.Extension;
import io.vertx.core.Handler;
import io.vertx.test.codegen.testapi.GenericInterface;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class GenericInterfaceMethods {

  @Extension
  public static <T> void genericReceiver(GenericInterface<T> receiver) {
    throw new UnsupportedOperationException();
  }

  @Extension
  public static <T> void typeVariableParameterBoundToReceiver(GenericInterface<T> receiver, T typeVariable) {
    throw new UnsupportedOperationException();
  }

  @Extension
  public static <T> T typeVariableReturnTypeBoundToReceiver(GenericInterface<T> receiver) {
    throw new UnsupportedOperationException();
  }

  @Extension
  public static <T, U> void typeVariableParameter(GenericInterface<U> receiver, T typeVariable) {
    throw new UnsupportedOperationException();
  }

  @Extension
  public static <T, U> T typeVariableReturnType(GenericInterface<U> receiver) {
    throw new UnsupportedOperationException();
  }

  @Extension
  public static <T> void genericParameter(GenericInterface<T> receiver, Handler<T> generic) {
    throw new UnsupportedOperationException();
  }

  @Extension
  public static <T> GenericInterface<T> genericReturnType(GenericInterface<T> receiver) {
    throw new UnsupportedOperationException();
  }
}
