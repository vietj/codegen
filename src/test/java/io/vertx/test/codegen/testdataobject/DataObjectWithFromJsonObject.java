package io.vertx.test.codegen.testdataobject;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class DataObjectWithFromJsonObject {

  public static DataObjectWithFromJsonObject fromJson(JsonObject obj) {
    throw new UnsupportedOperationException();
  }
}
