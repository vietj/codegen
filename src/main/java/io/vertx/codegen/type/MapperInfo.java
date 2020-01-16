package io.vertx.codegen.type;

import io.vertx.codegen.MapperKind;

public class MapperInfo {

  private TypeInfo jsonType;
  private String qualifiedName;
  private String name;
  private MapperKind kind;

  public MapperInfo() {
  }

  public TypeInfo getJsonType() {
    return jsonType;
  }

  public void setJsonType(TypeInfo jsonType) {
    this.jsonType = jsonType;
  }

  public String getQualifiedName() {
    return qualifiedName;
  }

  public void setQualifiedName(String qualifiedName) {
    this.qualifiedName = qualifiedName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public MapperKind getKind() {
    return kind;
  }

  public void setKind(MapperKind kind) {
    this.kind = kind;
  }
}
