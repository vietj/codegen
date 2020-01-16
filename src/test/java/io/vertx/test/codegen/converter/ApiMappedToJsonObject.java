/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.codegen.converter;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@VertxGen
public interface ApiMappedToJsonObject {

  static ApiMappedToJsonObject fromJson(JsonObject obj) {
    Impl instance = new Impl();
    instance.setFirstName(obj.getString("firstName"));
    instance.setLastName(obj.getString("lastName"));
    return instance;
  }

  String getFirstName();
  String getLastName();

  void setFirstName(String s);
  void setLastName(String s);

  JsonObject toJson();

  @GenIgnore
  class Impl implements ApiMappedToJsonObject {

    private String firstName;
    private String lastName;

    @Override
    public String getFirstName() {
      return firstName;
    }

    @Override
    public void setFirstName(String s) {
      this.firstName = s;
    }

    @Override
    public String getLastName() {
      return lastName;
    }

    @Override
    public void setLastName(String s) {
      this.lastName = s;
    }

    @Override
    public JsonObject toJson() {
      return new JsonObject().put("firstName", firstName).put("lastname", lastName);
    }
  }
}
