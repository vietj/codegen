package io.vertx.test.codegen.testdataobject;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@VertxGen
public interface DataObjectApi {

  static DataObjectApi fromJson(JsonObject obj) {
    throw new UnsupportedOperationException();
  }

  JsonObject toJson();

}
