package io.vertx.codegen;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.mvel2.MVEL;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@SupportedAnnotationTypes({"io.vertx.codegen.annotations.VertxGen","io.vertx.codegen.annotations.Options",
  "io.vertx.codegen.annotations.GenModule"})
@javax.annotation.processing.SupportedOptions({"outputDirectory","codeGenerators"})
@javax.annotation.processing.SupportedSourceVersion(javax.lang.model.SourceVersion.RELEASE_8)
public class CodeGenProcessor extends AbstractProcessor {

  private static final Logger log = Logger.getLogger(CodeGenProcessor.class.getName());
  private File outputDirectory;
  private Map<String, CodeGenerator> codeGenerators;

  private Collection<CodeGenerator> getCodeGenerators() {
    if (codeGenerators == null) {
      Map<String, CodeGenerator> codeGenerators = new LinkedHashMap<>();
      Enumeration<URL> descriptors = Collections.emptyEnumeration();
      try {
        descriptors = CodeGenProcessor.class.getClassLoader().getResources("codegen.json");
      } catch (IOException ignore) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Could not load code generator descriptors");
      }
      while (descriptors.hasMoreElements()) {
        URL descriptor = descriptors.nextElement();
        try (Scanner scanner = new Scanner(descriptor.openStream(), "UTF-8").useDelimiter("\\A")) {
          String s = scanner.next();
          JsonObject obj = new JsonObject(s);
          String lang = obj.getString("name");
          JsonArray generatorsCfg = obj.getArray("generators");
          JsonObject optionsCfg = obj.getObject("options");
          for (Object o : generatorsCfg) {
            JsonObject generator = (JsonObject) o;
            String kind = generator.getString("kind");
            String templateFileName = generator.getString("templateFileName");
            String fileName = generator.getString("fileName");
            Serializable fileNameExpression = MVEL.compileExpression(fileName);
            Template compiledTemplate = new Template(templateFileName);
            if (optionsCfg != null) {
              for (String optionName : optionsCfg.getFieldNames()) {
                compiledTemplate.getOptions().put(optionName, optionsCfg.getField(optionName).toString());
              }
            }
            compiledTemplate.getOptions().putAll(processingEnv.getOptions());
            codeGenerators.put(lang, new CodeGenerator(lang, kind, fileNameExpression, compiledTemplate));
          }
          log.info("Loaded " + lang + " code generator");
        } catch (Exception e) {
          String msg = "Could not load code generator " + descriptor;
          log.log(Level.SEVERE, msg, e);
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
        }
      }
      String outputDirectoryOption = processingEnv.getOptions().get("outputDirectory");
      if (outputDirectoryOption != null) {
        outputDirectory = new File(outputDirectoryOption);
        if (!outputDirectory.exists()) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Output directory " + outputDirectoryOption + " does not exist");
        }
        if (!outputDirectory.isDirectory()) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Output directory " + outputDirectoryOption + " is not a directory");
        }
      }
      String codeGeneratorsOption = processingEnv.getOptions().get("codeGenerators");
      if (codeGeneratorsOption != null) {
        Set<String> wanted = Stream.of(codeGeneratorsOption.split(",")).map(String::trim).collect(Collectors.toSet());
        if (codeGenerators.keySet().containsAll(wanted)) {
          codeGenerators.keySet().retainAll(wanted);
        } else {
          codeGenerators.clear();
          wanted.removeAll(codeGenerators.keySet());
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Code generators " + wanted + " not found");
        }
      }
      this.codeGenerators = codeGenerators;
    }
    return codeGenerators.values();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      Collection<CodeGenerator> codeGenerators = getCodeGenerators();
      if (!roundEnv.errorRaised()) {
        CodeGen codegen = new CodeGen(processingEnv, roundEnv);
        codeGenerators.stream().collect(Collectors.groupingBy(gen -> gen.lang)).forEach((lang, generators) -> {
          codegen.getModels(lang).forEach(entry -> {
            try {
              Model model = entry.getValue();
              // Generate source code
              for (CodeGenerator generator : generators) {
                if (outputDirectory != null) {
                  Map<String, Object> vars = new HashMap<>();
                  vars.put("helper", new Helper());
                  vars.put("options", processingEnv.getOptions());
                  vars.put("fileSeparator", File.separator);
                  vars.put("fqn", model.getFqn());
                  vars.putAll(model.getVars());
                  if (generator.kind.equals(model.getKind())) {
                    String relativeName = (String) MVEL.executeExpression(generator.fileNameExpression, vars);
                    if (relativeName != null) {
                      if (relativeName.endsWith(".java")) {
                        // Special handling for .java
                        JavaFileObject target = processingEnv.getFiler().createSourceFile(relativeName.substring(0, relativeName.length() - ".java".length()));
                        String output = generator.transformTemplate.render(model);
                        try (Writer writer = target.openWriter()) {
                          writer.append(output);
                        }
                      } else {
                        File target = new File(outputDirectory, relativeName);
                        generator.transformTemplate.apply(model, target);
                      }
                      log.info("Generated model " + model.getFqn() + ": " + relativeName);
                    }
                  }
                } else {
                  log.info("Validated model " + model.getFqn());
                }
              }
            } catch (GenException e) {
              String msg = "Could not generate model for " + e.element + ": " + e.msg;
              log.log(Level.SEVERE, msg, e);
              processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e.element);
            } catch (Exception e) {
              String msg = "Could not generate element for " + entry.getKey() + ": " + e.getMessage();
              log.log(Level.SEVERE, msg, e);
              processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, entry.getKey());
            }
          });
        });
      }
    }
    return true;
  }

  static class CodeGenerator {
    final String lang;
    final String kind;
    final Serializable fileNameExpression;
    final Template transformTemplate;
    CodeGenerator(String lang, String kind, Serializable fileNameExpression, Template transformTemplate) {
      this.lang = lang;
      this.kind = kind;
      this.fileNameExpression = fileNameExpression;
      this.transformTemplate = transformTemplate;
    }
  }
}
