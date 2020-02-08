package io.vertx.codegen;

import io.vertx.codegen.annotations.ModuleGen;
import io.vertx.codegen.type.ClassKind;
import io.vertx.codegen.type.MapperInfo;
import io.vertx.codegen.type.TypeMirrorFactory;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CodeGen {

  private static final List<ModelProvider> PROVIDERS;

  static {
    List<ModelProvider> list = new ArrayList<>();
    list.add(ModelProvider.CLASS);
    list.add(ModelProvider.DATA_OBJECT);
    list.add(ModelProvider.ENUM);
    try {
      ServiceLoader<ModelProvider> loader = ServiceLoader.load(ModelProvider.class, ModelProvider.class.getClassLoader());
      for (ModelProvider aLoader : loader) {
        list.add(aLoader);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    PROVIDERS = list;
  }

  private static final Logger logger = Logger.getLogger(CodeGen.class.getName());
  final static Map<ProcessingEnvironment, ClassLoader> loaderMap = new WeakHashMap<>();

  private final Map<String, Map<String, Map.Entry<TypeElement, Model>>> models = new HashMap<>();
  private final Set<TypeElement> all = new HashSet<>();
  private final Set<ExecutableElement> mapperElts = new HashSet<>();

  private final HashMap<String, PackageElement> modules = new HashMap<>();
  private final ProcessingEnvironment env;
  private final Elements elementUtils;
  private final Types typeUtils;
  private final TypeMirrorFactory tmf;

  public CodeGen(ProcessingEnvironment env) {
    this.env = env;
    this.elementUtils = env.getElementUtils();
    this.typeUtils = env.getTypeUtils();
    this.tmf = new TypeMirrorFactory(elementUtils, typeUtils);
  }

  private final List<Bilto> serializers = new ArrayList<>();

  public static class Bilto {
    public final String type;
    public final String serializer;
    public final List<String> selectors;
    public Bilto(String type, String serializer, List<String> selectors) {
      this.type = type;
      this.serializer = serializer;
      this.selectors = selectors;
    }
  }

  public void registerSerializer(Bilto b) {
    serializers.add(b);
  }

  public void registerSerializer(String className, String serializer, String... selectors) {
    serializers.add(new Bilto(className, serializer, Arrays.asList(selectors)));
  }

  public void registerDeserializer(String className, String serializer, String... selectors) {
    serializers.add(new Bilto(className, serializer, Arrays.asList(selectors)));
  }

  public void init(RoundEnvironment round, ClassLoader loader) {
    loaderMap.put(env, loader);
    Predicate<Element> implFilter = elt -> {
      String fqn = elementUtils.getPackageOf(elt).getQualifiedName().toString();
      if (fqn.contains(".impl.") || fqn.endsWith(".impl"))  {
        logger.warning("Processed element " + elt + " is in an implementation package");
        return false;
      } else {
        return true;
      }
    };

    serializers.forEach(c -> {
      TypeElement typeElt = elementUtils.getTypeElement(c.type);
      TypeElement serializerElt = elementUtils.getTypeElement(c.serializer);
      if (serializerElt == null) {
        System.out.println("NOT FOUND " + c.type + "#" + String.join(".", c.selectors));
        return;
      }
      TypeMirror bilto = serializerElt.asType();
      for (int i = 0;i < c.selectors.size();i++) {
        Resolved ttt = resolveMemberSelect(serializerElt, bilto, c.selectors.get(i));
        Set<Modifier> modifiers = ttt.element.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC)) {
          throw new GenException(serializerElt, "Annotated mapper element must be public");
        }
        if (i == 0 && !modifiers.contains(Modifier.STATIC)) {
          throw new GenException(serializerElt, "Annotated mapper element must be static");
        }
        bilto = ttt.type;
      }
      if (bilto.getKind() == TypeKind.EXECUTABLE) {
        ExecutableType execType = (ExecutableType) bilto;
        processSerializerOrDeserializer(serializerElt, typeElt, c.selectors, execType);
      } else if (bilto.getKind() == TypeKind.DECLARED) {
        // Handle function automatically
        TypeElement functionElt = elementUtils.getTypeElement(Function.class.getName());
        TypeMirror t2 = typeUtils.erasure(functionElt.asType());
        if (typeUtils.isSubtype(bilto, t2)) {
          Resolved ttt = resolveMemberSelect(serializerElt, bilto, "apply");
          ArrayList<String> selectors = new ArrayList<>(c.selectors);
          selectors.add("apply");
          processSerializerOrDeserializer(serializerElt, typeElt, selectors, (ExecutableType) ttt.type);
        }
        // Incorrect
      }
    });
    round.getRootElements().stream()
      .filter(implFilter)
      .filter(elt -> elt instanceof TypeElement)
      .map(elt -> (TypeElement)elt).forEach(te -> {
      for (ModelProvider provider : PROVIDERS) {
        Model model = provider.getModel(env, tmf, te);
        if (model != null) {
          String kind = model.getKind();
          all.add(te);
          Map<String, Map.Entry<TypeElement, Model>> map = models.computeIfAbsent(kind, a -> new HashMap<>());
          ModelEntry<TypeElement, Model> entry = new ModelEntry<>(te, () -> model);
          map.put(Helper.getNonGenericType(te.asType().toString()), entry);
        }
      }
    });
    round.getElementsAnnotatedWith(ModuleGen.class).
      stream().
      map(element -> (PackageElement) element).
      forEach(element -> modules.put(element.getQualifiedName().toString(), element));
  }

  private static class Resolved {
    final Element element;
    final TypeMirror type;
    private Resolved(Element element, TypeMirror type) {
      this.element = element;
      this.type = type;
    }
  }

  private Resolved resolveMemberSelect(TypeElement rootElt, TypeMirror type, String member) {
    TypeKind kind = type.getKind();
    if (kind == TypeKind.DECLARED) {
      // For now only support this
      DeclaredType dt = (DeclaredType) type;
      TypeElement typeElt = (TypeElement) dt.asElement();
      Element abc = resolveSerializer(typeElt, member);
      return new Resolved(abc, typeUtils.asMemberOf(dt, abc));
    }
    throw new UnsupportedOperationException();
  }

  private Element resolveSerializer(TypeElement elt, String selector) {
    Optional<? extends Element> selectorElt = elt
      .getEnclosedElements()
      .stream()
      .filter(e -> e.getSimpleName().toString().equals(selector))
      .findFirst();
    return selectorElt.orElse(null);
  }

  private void processSerializerOrDeserializer(TypeElement serializerElt, TypeElement typeElt, List<String> selectors, ExecutableType methodType) {

    if (methodType.getParameterTypes().size() < 1) {
      throw new GenException(serializerElt, "Annotated method mapper cannot have empty arguments");
    }
    if (methodType.getParameterTypes().size() > 1) {
      throw new GenException(serializerElt, "Annotated method mapper must have one argument");
    }

    TypeMirror paramType = methodType.getParameterTypes().get(0);
    if (paramType.toString().equals("java.lang.CharSequence")) {
      // Special handling
      paramType = elementUtils.getTypeElement("java.lang.String").asType();
    }
    TypeMirror returnType = methodType.getReturnType();
    ClassKind paramKind = ClassKind.getKind(paramType.toString(), false, false);
    ClassKind returnKind = ClassKind.getKind(returnType.toString(), false, false);
    if (paramKind.json || paramKind.basic || paramKind == ClassKind.OBJECT) {
      MapperInfo mapper;
      switch (selectors.size()) {
        case 1:
          // Static method
          mapper = new MapperInfo();
          mapper.setQualifiedName(serializerElt.getQualifiedName().toString());
          mapper.setTargetType(tmf.create(paramType));
          mapper.setSelectors(selectors);
          mapper.setKind(MapperKind.STATIC_METHOD);
          break;
        case 2:
          // Function
          mapper = new MapperInfo();
          mapper.setQualifiedName(serializerElt.getQualifiedName().toString());
          mapper.setTargetType(tmf.create(paramType));
          mapper.setSelectors(selectors);
          mapper.setKind(MapperKind.STATIC_METHOD);
          break;
        default:
          throw new UnsupportedOperationException();
      }
      tmf.addDataObjectDeserializer(serializerElt, typeElt.asType(), mapper);
    } else if (returnKind.json || returnKind.basic || returnKind == ClassKind.OBJECT) {
      MapperInfo mapper;
      switch (selectors.size()) {
        case 1:
          // Static method
          mapper = new MapperInfo();
          mapper.setQualifiedName(serializerElt.getQualifiedName().toString());
          mapper.setTargetType(tmf.create(returnType));
          mapper.setSelectors(selectors);
          mapper.setKind(MapperKind.STATIC_METHOD);
          break;
        case 2:
          // Function
          mapper = new MapperInfo();
          mapper.setQualifiedName(serializerElt.getQualifiedName().toString());
          mapper.setTargetType(tmf.create(returnType));
          mapper.setSelectors(selectors);
          mapper.setKind(MapperKind.STATIC_METHOD);
          break;
        default:
          throw new UnsupportedOperationException();
      }
      tmf.addDataObjectSerializer(serializerElt, typeElt.asType(), mapper);
    } else {
      throw new GenException(serializerElt, "Mapper method does not declare a JSON type");
    }


    /*
    if (!elt.getModifiers().contains(Modifier.STATIC)) {
      throw new GenException(elt, "Annotated mapper element must be static");
    }
    if (!elt.getModifiers().contains(Modifier.PUBLIC)) {
      throw new GenException(elt, "Annotated mapper element must be public");
    }
    if (elt instanceof ExecutableElement) {
      ExecutableElement methElt = (ExecutableElement) elt;
      if (methElt.getParameters().size() < 1) {
        throw new GenException(elt, "Annotated method mapper cannot have empty arguments");
      }
      if (methElt.getParameters().size() > 1) {
        throw new GenException(elt, "Annotated method mapper must have one argument");
      }
      TypeMirror paramType = methElt.getParameters().get(0).asType();
      TypeMirror returnType = methElt.getReturnType();
      ClassKind paramKind = ClassKind.getKind(paramType.toString(), false, false);
      ClassKind returnKind = ClassKind.getKind(returnType.toString(), false, false);
      if (paramKind.json || paramKind.basic || paramKind == ClassKind.OBJECT || paramType.toString().equals(CharSequence.class.getName())) {
        tmf.addDataObjectDeserializer(methElt, returnType, paramType);
      } else if (returnKind.json || returnKind.basic || returnKind == ClassKind.OBJECT) {
        tmf.addDataObjectSerializer(methElt, paramType, returnType);
      } else {
        throw new GenException(methElt, "Mapper method doees not declare a JSON type");
      }
    } else {
      VariableElement variableElt = (VariableElement) elt;
      TypeElement parameterizedElt = elementUtils.getTypeElement("java.util.function.Function");
      TypeMirror parameterizedType = parameterizedElt.asType();
      TypeMirror rawType = typeUtils.erasure(parameterizedType);
      DeclaredType blah = (DeclaredType) variableElt.asType();
      if (typeUtils.isSubtype(blah, rawType)) {
        TypeMirror paramType = Helper.resolveTypeParameter(typeUtils, blah, parameterizedElt.getTypeParameters().get(0));
        TypeMirror returnType = Helper.resolveTypeParameter(typeUtils, blah, parameterizedElt.getTypeParameters().get(1));
        ClassKind paramKind = ClassKind.getKind(paramType.toString(), false, false);
        ClassKind returnKind = ClassKind.getKind(returnType.toString(), false, false);
        if (paramKind.json || paramKind.basic || paramKind == ClassKind.OBJECT) {
          tmf.addDataObjectDeserializer(variableElt, returnType, paramType);
        } else if (returnKind.json || returnKind.basic || returnKind == ClassKind.OBJECT) {
          tmf.addDataObjectSerializer(variableElt, paramType, returnType);
        } else {
          throw new GenException(variableElt, "Mapper method doees not declare a JSON type");
        }
      }
    }
*/
  }

  public Stream<Map.Entry<? extends Element, ? extends Model>> getModels() {
    Stream<Map.Entry<? extends Element, ? extends Model>> s = Stream.empty();
    for (Map<String, Map.Entry<TypeElement, Model>> m : models.values()) {
      s = Stream.concat(s, m.values().stream());
    }
    return Stream.concat(s, Stream.concat(getModuleModels(), getPackageModels()));
  }

  private Stream<Map.Entry<PackageElement, PackageModel>> getPackageModels() {
    return all.stream()
      .map(elementUtils::getPackageOf).distinct()
      .map(element ->
            new ModelEntry<>(element, () -> new PackageModel(
                element.getQualifiedName().toString(),
                ModuleInfo.resolve(elementUtils, element))
            ));
  }

  private Stream<Map.Entry<PackageElement, ModuleModel>> getModuleModels() {
    return modules.entrySet().stream().map(entry -> new ModelEntry<>(entry.getValue(), () -> getModuleModel(entry.getKey())));
  }

  public ModuleModel getModuleModel(String modulePackage) {
    PackageElement element = modules.get(modulePackage);
    return new ModuleModel(elementUtils, typeUtils, element);
  }

  public PackageModel getPackageModel(String fqn) {
    return getPackageModels().filter(pkg -> pkg.getValue().getFqn().equals(fqn)).findFirst().map(Map.Entry::getValue).orElse(null);
  }

  public Model getModel(String fqcn, String kind) {
    Map<String, Map.Entry<TypeElement, Model>> map = models.get(kind);
    if (map == null) {
      throw new IllegalArgumentException("Source for " + fqcn + " not found");
    }
    Map.Entry<TypeElement, Model> entry = map.get(fqcn);
    if (entry == null) {
      throw new IllegalArgumentException("Source for " + fqcn + " not found");
    }
    return entry.getValue();
  }

  public ClassModel getClassModel(String fqcn) {
    return (ClassModel) getModel(fqcn, "class");
  }

  public EnumModel getEnumModel(String fqcn) {
    return (EnumModel) getModel(fqcn, "enum");
  }

  public DataObjectModel getDataObjectModel(String fqcn) {
    return (DataObjectModel) getModel(fqcn, "dataObject");
  }

  private static class ModelEntry<E extends Element, M extends Model> implements Map.Entry<E, M> {

    private final E key;
    private final Supplier<M> supplier;
    private M value;

    private ModelEntry(E key, Supplier<M> supplier) {
      this.key = key;
      this.supplier = supplier;
    }

    @Override
    public E getKey() {
      return key;
    }

    @Override
    public M getValue() {
      if (value == null) {
        value = supplier.get();
        value.process();
      }
      return value;
    }

    @Override
    public M setValue(M value) {
      throw new UnsupportedOperationException();
    }
  }
}
