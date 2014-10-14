package io.vertx.codegen;

/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.IndexGetter;
import io.vertx.codegen.annotations.IndexSetter;
import io.vertx.codegen.annotations.Native;
import io.vertx.codegen.annotations.VertxGen;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A processed source.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ClassModel implements Model {

  private static final Logger logger = Logger.getLogger(ClassModel.class.getName());
  private static Pattern IMPLEM_PATTERN = Pattern.compile("<native:(\\p{Alpha}*)>((?:.|\n)*)</native:\\1>", Pattern.MULTILINE);

  public static final String VERTX_ASYNC_RESULT = "io.vertx.core.AsyncResult";
  public static final String VERTX_HANDLER = "io.vertx.core.Handler";
  public static final String JSON_OBJECT = "io.vertx.core.json.JsonObject";
  public static final String JSON_ARRAY = "io.vertx.core.json.JsonArray";
  public static final String VERTX = "io.vertx.core.Vertx";

  private final Messager messager;
  private final TypeInfo.Factory typeFactory;
  private final Map<String, TypeElement> sources;
  private final TypeElement modelElt;
  private final Elements elementUtils;
  private final Types typeUtils;
  private final List<ExecutableElement> extensions = new ArrayList<>();
  private boolean processed = false;
  private List<MethodInfo> methods = new ArrayList<>();
  private HashSet<TypeInfo.Class> importedTypes = new HashSet<>();
  private Set<TypeInfo.Class> referencedTypes = new HashSet<>();
  private boolean concrete;
  private TypeInfo type;
  private String ifaceSimpleName;
  private String ifaceFQCN;
  private String ifacePackageName;
  private String ifaceComment;
  private List<TypeInfo> superTypes = new ArrayList<>();
  private List<TypeInfo> concreteSuperTypes = new ArrayList<>();
  private List<TypeInfo> abstractSuperTypes = new ArrayList<>();
  // The methods, grouped by name
  private Map<String, List<MethodInfo>> methodMap = new LinkedHashMap<>();
  private Set<String> referencedOptionsTypes = new HashSet<>();
  private List<TypeParamInfo> typeParams = new ArrayList<>();

  public ClassModel(Messager messager, Map<String, TypeElement> sources, Elements elementUtils, Types typeUtils, TypeElement modelElt) {
    this.messager = messager;
    this.sources = sources;
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;
    this.modelElt = modelElt;
    this.typeFactory = new TypeInfo.Factory(elementUtils, typeUtils);
  }

  @Override
  public String getKind() {
    return "class";
  }

  @Override
  public String getFqn() {
    return type.getRaw().getName();
  }

  public TypeElement getElement() {
    return modelElt;
  }

  public List<MethodInfo> getMethods() {
    return methods;
  }

  public Set<TypeInfo.Class> getImportedTypes() {
    return importedTypes;
  }

  public boolean isConcrete() {
    return concrete;
  }

  public Set<TypeInfo.Class> getReferencedTypes() {
    return referencedTypes;
  }

  public String getIfaceSimpleName() {
    return ifaceSimpleName;
  }

  public String getIfaceFQCN() {
    return ifaceFQCN;
  }

  public String getIfacePackageName() {
    return ifacePackageName;
  }

  public String getIfaceComment() {
    return ifaceComment;
  }

  public TypeInfo getType() {
    return type;
  }

  public List<TypeInfo> getSuperTypes() {
    return superTypes;
  }

  public List<TypeInfo> getConcreteSuperTypes() {
    return concreteSuperTypes;
  }

  public List<TypeInfo> getAbstractSuperTypes() {
    return abstractSuperTypes;
  }

  public Map<String, List<MethodInfo>> getMethodMap() {
    return methodMap;
  }

  public Set<String> getReferencedOptionsTypes() {
    return referencedOptionsTypes;
  }

  public List<TypeParamInfo> getTypeParams() {
    return typeParams;
  }

  private void sortMethodMap(Map<String, List<MethodInfo>> map) {
    for (List<MethodInfo> list: map.values()) {
      list.sort((meth1, meth2) -> meth1.params.size() - meth2.params.size());
    }
  }

  private void checkParamType(Element elem, TypeInfo typeInfo, boolean nativeMethod) {

    // Basic types, int, long, String etc
    // JsonObject or JsonArray
    // Also can use Object as a param type (e.g. for EventBus)
    if (typeInfo.getKind().basic || typeInfo.getKind().json || typeInfo.getKind() == ClassKind.OBJECT) {
      return;
    }
    // We also allow enums as parameter types
    if (typeInfo.getKind() == ClassKind.ENUM) {
      return;
    }
    // Check legal handlers
    if (isLegalHandlerType(typeInfo)) {
      return;
    }
    if (isLegalHandlerAsyncResultType(typeInfo)) {
      return;
    }
    // Another user defined interface with the @VertxGen annotation is OK
    if (isVertxGenInterface(typeInfo)) {
      return;
    }
    // Can also specify option classes (which aren't VertxGen)
    if (isOptionType(typeInfo)) {
      return;
    }
    // We also allow type parameters for param types
    if (isVariableType(typeInfo)) {
      return;
    }
    if (!nativeMethod) {
      throw new GenException(elem, "type " + typeInfo + " is not legal for use for a parameter in code generation");
    }
  }

  private void checkReturnType(Element elem, TypeInfo type, boolean nativeMethod) {
    // Basic types, int, long, String etc
    // JsonObject or JsonArray
    // void
    if (type.getKind().basic || type instanceof TypeInfo.Void || type.getKind().json) {
      return;
    }
    // We also allow enums as return types
    if (type.getKind() == ClassKind.ENUM) {
      return;
    }

    // List<T> and Set<T> are also legal for returns if T = basic type
    // Map<K,V> is also legal for returns if K is a String and V is a basic type, json, or a @VertxGen interface
    if (rawTypeIs(type, List.class, Set.class, Map.class)) {
      TypeInfo argument = ((TypeInfo.Parameterized) type).getArgs().get(0);
      if (type.getKind() != ClassKind.MAP) {
        if (argument.getKind().basic || argument.getKind().json) {
          return;
        } else if (isVertxGenInterface(argument)) {
          return;
        }
      } else if (argument.getKind() == ClassKind.STRING) { // Only allow Map's with String's for keys
        argument = ((TypeInfo.Parameterized) type).getArgs().get(1);
        if (argument.getKind().basic || argument.getKind().json) {
          return;
        } else if (isVertxGenInterface(argument)) {
          return;
        }
      }
    }

    // Another user defined interface with the @VertxGen annotation is OK
    if (isVertxGenInterface(type)) {
      return;
    }

    // Variable type is ok
    if (isVariableType(type)) {
      return;
    }

    if (!nativeMethod) {
      throw new GenException(elem, "type " + type + " is not legal for use for a return type in code generation");
    }
  }

  private boolean isVariableType(TypeInfo type) {
    return type instanceof TypeInfo.Variable;
  }

  private boolean isOptionType(TypeInfo type) {
    if (type.getKind() == ClassKind.OPTIONS) {
      referencedOptionsTypes.add(type.getName());
      return true;
    }
    return false;
  }

  private boolean isLegalListOrSet(TypeInfo type) {
    if (type instanceof TypeInfo.Parameterized) {
      TypeInfo raw = ((TypeInfo.Parameterized) type).getRaw();
      if (raw.getName().equals(List.class.getName()) || raw.getName().equals(Set.class.getName())) {
        TypeInfo elementType = ((TypeInfo.Parameterized) type).getArgs().get(0);
        if (elementType.getKind().basic || elementType.getKind().json || isVertxGenInterface(elementType)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isVertxGenInterface(TypeInfo type) {
    if (type.getKind() == ClassKind.API) {
      if (!type.getName().equals(VERTX)) {
        referencedTypes.add(type.getRaw());
      }
      return true;
    }
    return false;
  }

  private boolean isLegalHandlerType(TypeInfo type) {
    if (type.getErased().getKind() == ClassKind.HANDLER) {
      TypeInfo eventType = ((TypeInfo.Parameterized) type).getArgs().get(0);
      if (eventType.getKind().json || eventType.getKind().basic || isVertxGenInterface(eventType) ||
          isLegalListOrSet(eventType) || eventType.getKind() == ClassKind.VOID ||
          eventType.getKind() == ClassKind.THROWABLE || isVariableType(eventType)) {
        return true;
      }
    }
    return false;
  }

  private boolean isLegalHandlerAsyncResultType(TypeInfo type) {
    if (type.getErased().getKind() == ClassKind.HANDLER) {
      TypeInfo eventType = ((TypeInfo.Parameterized) type).getArgs().get(0);
      if (eventType.getErased().getKind() == ClassKind.ASYNC_RESULT) {
        TypeInfo resultType = ((TypeInfo.Parameterized) eventType).getArgs().get(0);
        if (resultType.getKind().json || resultType.getKind().basic || isVertxGenInterface(resultType) ||
            isLegalListOrSet(resultType) || resultType.getKind() == ClassKind.VOID ||
            isVariableType(resultType)) {
          return true;
        }
      }
    }
    return false;
  }

  void addExtension(ExecutableElement extension) {
    extensions.add(extension);
  }

  boolean process(String lang) {
    if (!processed) {
      processType(lang);
      determineSiteDeclVariance();
      processed = true;
      return true;
    } else {
      return false;
    }
  }

  private void determineSiteDeclVariance() {
    List<? extends TypeParameterElement> typeParamElts = modelElt.getTypeParameters();
    for (TypeParameterElement typeParamElt : typeParamElts) {
      Set<Variance> siteVariance = EnumSet.noneOf(Variance.class);
      for (Variance variance : Variance.values()) {
        if (Helper.resolveSiteVariance(typeParamElt, variance)) {
          siteVariance.add(variance);
        }
      }
      logger.log(Level.FINE, "Site variances of " + modelElt + " " + typeParamElt + " : " + siteVariance);
      typeParams.add(new TypeParamInfo(typeParamElt.getSimpleName().toString(), siteVariance));
    }
  }

  private void processType(String lang) {
    if (modelElt.getKind() != ElementKind.INTERFACE) {
      throw new GenException(modelElt, "@VertxGen can only be used with interfaces in " + modelElt.asType().toString());
    }
    for (Element enclosedElt : modelElt.getEnclosedElements()) {
      if (enclosedElt instanceof TypeElement) {
        throw new GenException(enclosedElt, "@VertxGen cannot have nested type declarations " + enclosedElt.asType().toString());
      }
    }
    type = typeFactory.create(modelElt.asType());
    ifaceFQCN = modelElt.asType().toString();
    ifaceSimpleName = modelElt.getSimpleName().toString();
    ifacePackageName = elementUtils.getPackageOf(modelElt).toString();
    ifaceComment = elementUtils.getDocComment(modelElt);
    concrete = modelElt.getAnnotation(VertxGen.class).concrete();
    DeclaredType tm = (DeclaredType) modelElt.asType();
    List<? extends TypeMirror> typeArgs = tm.getTypeArguments();
    for (TypeMirror typeArg : typeArgs) {
      TypeVariable varTypeArg = (TypeVariable) typeArg;
      if (!isObjectBound(varTypeArg.getUpperBound())) {
        throw new GenException(modelElt, "Type variable bounds not supported " + varTypeArg.getUpperBound());
      }
    }
    List<? extends TypeMirror> st = typeUtils.directSupertypes(tm);
    for (TypeMirror tmSuper: st) {
      Element superElement = typeUtils.asElement(tmSuper);
      VertxGen superGen = superElement.getAnnotation(VertxGen.class);
      if (!tmSuper.toString().equals(Object.class.getName())) {
        if (superElement.getAnnotation(VertxGen.class) != null) {
          try {
            TypeInfo.Class superType = typeFactory.create(tmSuper).getRaw();
            referencedTypes.add(superType);
          } catch (Exception e) {
            throw new GenException(modelElt, e.getMessage());
          }
        }
        try {
          TypeInfo superTypeInfo = typeFactory.create(tmSuper);
          superTypeInfo.collectImports(importedTypes);
          if (superGen != null) {
            (superGen.concrete() ? concreteSuperTypes : abstractSuperTypes).add(superTypeInfo);
            superTypes.add(superTypeInfo);
          }
        } catch (IllegalArgumentException e) {
          throw new GenException(modelElt, e.getMessage());
        }
      }
    }
    if (concrete && concreteSuperTypes.size() > 1) {
      throw new GenException(modelElt, "A concrete interface cannot extend more than two concrete interfaces");
    }
    if (!concrete && concreteSuperTypes.size() > 0) {
      throw new GenException(modelElt, "A abstract interface cannot extend more a concrete interface");
    }
    for (Iterator<TypeInfo.Class> i = importedTypes.iterator();i.hasNext();) {
      TypeInfo.Class type = i.next();
      if (Helper.getPackageName(type.toString()).equals(Helper.getPackageName(ifaceFQCN))) {
        i.remove();
      }
    }

    TypeMirror objectType = elementUtils.getTypeElement("java.lang.Object").asType();

    // Traverse methods
    elementUtils.getAllMembers((TypeElement) modelElt).stream().
        filter(elt -> !typeUtils.isSameType(elt.getEnclosingElement().asType(), objectType)).
        flatMap(Helper.FILTER_METHOD).
        forEach(methodElt -> processMethod(methodElt, lang));

    // Add extensions
    extensions.forEach(methodElt -> processExtensionMethod(methodElt, lang));

    // We're done
    if (methods.isEmpty() && superTypes.isEmpty()) {
      throw new GenException(modelElt, "Interface " + ifaceFQCN + " does not contain any methods for generation");
    }
    // don't reference yourself
    for (Iterator<TypeInfo.Class> i = referencedTypes.iterator();i.hasNext();) {
      TypeInfo.Class next = i.next();
      if (next.getName().equals(Helper.getNonGenericType(ifaceFQCN))) {
        i.remove();
      }
    }
    sortMethodMap(methodMap);
  }

  private void processExtensionMethod(ExecutableElement methodElt, String lang) {
    Set<Modifier> mods = methodElt.getModifiers();
    if (!mods.contains(Modifier.PUBLIC)) {
      throw new GenException(methodElt, "Extension method must be public");
    }
    if (!mods.contains(Modifier.STATIC)) {
      throw new GenException(methodElt, "Extension method must be static");
    }

    boolean isNative = methodElt.getAnnotation(Native.class) != null;
    String implementation = null;
    if (isNative) {
      implementation = getNativeImplementations(methodElt, lang);
      if (implementation == null) {
        return;
      }
    }

    ExecutableType methodType = (ExecutableType) methodElt.asType();
    TypeInfo.Class ownerType = typeFactory.create(methodType.getParameterTypes().get(0)).getRaw();
    boolean isCacheReturn = methodElt.getAnnotation(CacheReturn.class) != null;
    boolean isFluent = methodElt.getAnnotation(Fluent.class) != null;
    String methodName = methodElt.getSimpleName().toString();
    TypeMirror extendedType = methodType.getParameterTypes().get(0);
    HashMap<TypeVariable, TypeMirror> aliases = new HashMap<>();
    ArrayList<String> typeParams = new ArrayList<>();
    if (extendedType instanceof DeclaredType) {
      // Handle type variable assocations, we detect any type variable declared by the first parameter
      // and remove it from the method signature for instance:
      // <T> Observable<T> asObservable(ReadStream<T> readStream) -> <T> is removed
      // then <T> is resolved against the type we add this method to, for the type
      // MessageConsumer<U> that extends ReadStream<Message<U>> then <T> is resolved to
      // Message<U> and the extension method is:
      // Observable<U> asObservable()
      DeclaredType declaredExtendedType = (DeclaredType) extendedType;
      TypeElement extendedTypeElt = (TypeElement) declaredExtendedType.asElement();
      List<? extends TypeMirror> typeArgs = declaredExtendedType.getTypeArguments();
      for (TypeVariable typeVar : methodType.getTypeVariables()) {
        boolean remove = false;
        for (int i = 0;i < typeArgs.size();i++) {
          TypeMirror typeArg = typeArgs.get(i);
          if (typeArg.equals(typeVar)) {
            TypeParameterElement typeParamElt = extendedTypeElt.getTypeParameters().get(i);
            TypeMirror resolved = Helper.resolveTypeParameter(typeUtils, typeParamElt, (DeclaredType) modelElt.asType());
            aliases.put(typeVar, resolved);
            remove = true;
          }
        }
        if (!remove) {
          typeParams.add(typeVar.asElement().getSimpleName().toString());
        }
      }
    }

    //
    List<? extends TypeMirror> typeMirrors = methodType.
        getParameterTypes().
        stream().
        skip(1).
        map(type -> Helper.resolveType(typeUtils, type, aliases)).collect(Collectors.toList());

    // Params
    List<ParamInfo> mParams = getParams(
        methodElt.getParameters().subList(1, methodElt.getParameters().size()),
        typeMirrors, isNative);

    // Return type
    TypeInfo returnType = typeFactory.create(Helper.resolveType(typeUtils, methodType.getReturnType(), aliases));
    returnType.collectImports(importedTypes);
    checkReturnType(methodElt, returnType, isNative);

    // Determine method kind + validate
    MethodKind kind = getMethodKind(methodElt, returnType, methodName, mParams, isFluent);
    TypeInfo.Class invoker = (TypeInfo.Class) typeFactory.create(methodElt.getEnclosingElement().asType());

    //
    addMethod(new MethodInfo(Collections.singleton(ownerType), methodName, kind, returnType,
        isFluent, isCacheReturn, mParams, elementUtils.getDocComment(methodElt), false, typeParams, implementation, invoker));
  }

  private void processMethod(ExecutableElement methodElt, String lang) {
    boolean isIgnore = methodElt.getAnnotation(GenIgnore.class) != null;
    if (isIgnore) {
      return;
    }
    Set<Modifier> mods = methodElt.getModifiers();
    if (!mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.DEFAULT)) {
      return;
    }

    TypeElement declaringElt = (TypeElement) methodElt.getEnclosingElement();
    if (!declaringElt.equals(modelElt)) {
      VertxGen ownerGen = declaringElt.getAnnotation(VertxGen.class);
      if (ownerGen == null || ownerGen.concrete()) {
        return;
      }
    }

    boolean isNative = methodElt.getAnnotation(Native.class) != null;
    String implementation = null;
    if (isNative) {
      implementation = getNativeImplementations(methodElt, lang);
      if (implementation == null) {
        return;
      }
    }

    TypeInfo.Class ownerType = typeFactory.create(declaringElt.asType()).getRaw();

    boolean isStatic = mods.contains(Modifier.STATIC);
    boolean isCacheReturn = methodElt.getAnnotation(CacheReturn.class) != null;
    List<String> typeParams = getMethodTypeParameters(methodElt);

    //
    ExecutableType methodType = (ExecutableType) typeUtils.asMemberOf((DeclaredType) modelElt.asType(), methodElt);
    List<ParamInfo> mParams = getParams(methodElt.getParameters(), methodType.getParameterTypes(), isNative);

    //
    AnnotationMirror fluentAnnotation = Helper.resolveMethodAnnotation(Fluent.class, elementUtils, typeUtils, declaringElt, methodElt);
    boolean isFluent = fluentAnnotation != null;
    if (isFluent) {
      isFluent = true;
      if (!typeUtils.isSameType(declaringElt.asType(), modelElt.asType())) {
        String msg = "Interface " + modelElt + " does not redeclare the @Fluent return type " +
            " of method " + methodElt + " declared by " + declaringElt;
        messager.printMessage(Diagnostic.Kind.WARNING, msg, modelElt, fluentAnnotation);
        logger.warning(msg);
      } else {
        TypeMirror fluentType = methodElt.getReturnType();
        if (!typeUtils.isAssignable(fluentType, modelElt.asType())) {
          throw new GenException(methodElt, "Methods marked with @Fluent must have a return type that extends the type");
        }
      }
    }

    TypeInfo returnType = typeFactory.create(methodType.getReturnType());
    returnType.collectImports(importedTypes);
    if (isCacheReturn && returnType instanceof TypeInfo.Void) {
      throw new GenException(methodElt, "void method can't be marked with @CacheReturn");
    }
    String methodName = methodElt.getSimpleName().toString();

    // Only check the return type if not fluent, because generated code won't look it at anyway
    if (!isFluent) {
      checkReturnType(methodElt, returnType, isNative);
    }

    // Determine method kind + validate
    MethodKind kind = getMethodKind(methodElt, returnType, methodName, mParams, isFluent);

    //
    addMethod(new MethodInfo(Collections.singleton(ownerType), methodName, kind, returnType,
        isFluent, isCacheReturn, mParams, elementUtils.getDocComment(methodElt), isStatic, typeParams,
        implementation, null));
  }

  private void addMethod(MethodInfo methodInfo) {

    // Check we don't hide another method
    next:
    for (MethodInfo method : methods) {
      if (method.getName().equals(methodInfo.getName())) {
        List<ParamInfo> params1 = method.getParams();
        List<ParamInfo> params2 = methodInfo.getParams();
        int size = params1.size();
        if (size == params2.size()) {
          for (int i = 0;i < size;i++) {
            if (!params1.get(i).equals(params2.get(i))) {
              continue next;
            }
          }
          method.ownerTypes.addAll(methodInfo.getOwnerTypes());
          return;
        }
      }
    }
    List<MethodInfo> methodsByName = methodMap.get(methodInfo.getName());
    if (methodsByName == null) {
      methodMap.put(methodInfo.getName(), methodsByName = new ArrayList<>());
    } else {
      // Overloaded methods must have same return type
      for (MethodInfo meth: methodsByName) {
        if (!meth.returnType.equals(methodInfo.returnType)) {
          throw new GenException(this.modelElt, "Overloaded method " + methodInfo.name + " must have the same return type "
              + meth.returnType + " != " + methodInfo.returnType);
        }
      }
    }
    methodsByName.add(methodInfo);
    methods.add(methodInfo);
    methodInfo.collectImports(importedTypes);
  }

  private String getNativeImplementations(ExecutableElement methodElt, String lang) {
    String s = elementUtils.getDocComment(methodElt);
    if (s != null) {
      Matcher matcher = IMPLEM_PATTERN.matcher(s);
      while (matcher.find()) {
        if (matcher.group(1).equals(lang)) {
          String body = matcher.group(2);
          while (body.startsWith("\n")) {
            body = body.substring(1);
          }
          while (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
          }
          body = body.trim();
          return body;
        }
      }
    } else {
      // Warn ?
    }
    return null;
  }

  private MethodKind getMethodKind(Element methodElt, TypeInfo returnType, String name, List<ParamInfo> params, boolean isFluent) {
    MethodKind kind = MethodKind.OTHER;
    if (methodElt.getAnnotation(IndexGetter.class) != null) {
      if (!params.stream().anyMatch(param -> param.type.getName().equals("int"))) {
        throw new GenException(methodElt, "No int arg found in index getter method");
      }
      kind = MethodKind.INDEX_GETTER;
    } else if (methodElt.getAnnotation(IndexSetter.class) != null) {
      if (!params.stream().anyMatch(param -> param.type.getName().equals("int"))) {
        throw new GenException(methodElt, "No int arg found in index setter method");
      }
      kind = MethodKind.INDEX_SETTER;
    } else {
      if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2)) &&
          params.isEmpty() && !(returnType instanceof TypeInfo.Void)) {
        kind = MethodKind.GETTER;
      } else if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3)) &&
          params.isEmpty() && !(returnType instanceof TypeInfo.Void)) {
        kind = MethodKind.GETTER;
      } else {
        int lastParamIndex = params.size() - 1;
        if (lastParamIndex >= 0 && (returnType instanceof TypeInfo.Void || isFluent)) {
          TypeInfo lastParamType = params.get(lastParamIndex).type;
          if (lastParamType.getKind() == ClassKind.HANDLER) {
            TypeInfo typeArg = ((TypeInfo.Parameterized) lastParamType).getArgs().get(0);
            if (typeArg.getKind() == ClassKind.ASYNC_RESULT) {
              kind = MethodKind.FUTURE;
            } else {
              kind = MethodKind.HANDLER;
            }
          }
        }
      }
    }
    return kind;
  }

  private boolean isObjectBound(TypeMirror bound) {
    return bound.getKind() == TypeKind.DECLARED && bound.toString().equals(Object.class.getName());
  }

  private List<String> getMethodTypeParameters(ExecutableElement methodElt) {
    ArrayList<String> params = new ArrayList<>();
    for (TypeParameterElement typeParam : methodElt.getTypeParameters()) {
      for (TypeMirror bound : typeParam.getBounds()) {
        if (!isObjectBound(bound)) {
          throw new GenException(methodElt, "Type parameter bound not supported " + bound);
        }
      }
      params.add(typeParam.getSimpleName().toString());
    }
    return params;
  }

  private List<ParamInfo> getParams(List<? extends VariableElement> paramElts,
                                    List<? extends TypeMirror> paramTypes, boolean nativeMethod) {
    List<ParamInfo> params = new ArrayList<>();
    for (int i = 0;i < paramElts.size();i++) {
      VariableElement paramElt = paramElts.get(i);
      TypeInfo paramType;
      try {
        paramType = typeFactory.create(paramTypes.get(i));
      } catch (Exception e) {
        throw new GenException(paramElt, e.getMessage());
      }
      checkParamType(paramElt, paramType, nativeMethod);
      ParamInfo mParam = new ParamInfo(paramElt.getSimpleName().toString(), paramType);
      params.add(mParam);
    }
    return params;
  }

  @Override
  public Map<String, Object> getVars() {
    Map<String, Object> vars = new HashMap<>();
    vars.put("importedTypes", getImportedTypes());
    vars.put("concrete", isConcrete());
    vars.put("type", getType());
    vars.put("ifacePackageName", getIfacePackageName());
    vars.put("ifaceSimpleName", getIfaceSimpleName());
    vars.put("ifaceFQCN", getIfaceFQCN());
    vars.put("ifaceComment", getIfaceComment());
    vars.put("helper", new Helper());
    vars.put("methods", getMethods());
    vars.put("referencedTypes", getReferencedTypes());
    vars.put("superTypes", getSuperTypes());
    vars.put("concreteSuperTypes", getConcreteSuperTypes());
    vars.put("abstractSuperTypes", getAbstractSuperTypes());
    vars.put("methodsByName", getMethodMap());
    vars.put("referencedOptionsTypes", getReferencedOptionsTypes());
    vars.put("typeParams", getTypeParams());
    vars.putAll(ClassKind.vars());
    vars.putAll(MethodKind.vars());
    return vars;
  }

  private static boolean rawTypeIs(TypeInfo type, Class<?>... classes) {
    if (type instanceof TypeInfo.Parameterized) {
      String rawClassName = type.getRaw().getName();
      for (Class<?> c : classes) {
        if (rawClassName.equals(c.getName())) {
          return true;
        }
      }
    }

    return false;
  }
}
