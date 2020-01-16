package io.vertx.test.codegen.testdataobject;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class DataObjectWithFromString {

  public static DataObjectWithFromString fromJson(String s) {
    throw new UnsupportedOperationException();
  }
}
