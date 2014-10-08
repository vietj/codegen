package io.vertx.codegen.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method to be extension method, the extension method must be <i>public</i> and <i>static</i> and
 * declare at least one parameter, this parameter type is the extended type.<p>
 *
 * The extended type will be extended with a new method whose signature is the signature of the extension
 * method without the first extended parameter type and the same return type.<p>
 *
 * <code><pre>
 *   {@literal @}GenInterface
 *   public interface Extended { }
 *
 *   public class Extension {
 *     {@literal @}Extension
 *     public static String doSomething(Extended extended, String s) {
 *       .. do something ..
 *     }
 *   }
 * </pre></code>
 *
 * The <code>Extended</code> interface will be seen by the code generator as having a single method:
 *
 * <code><pre>
 *   {@literal @}GenInterface
 *   public interface Extended {
 *     public String doSomething(String s) {
 *       .. do something ..
 *     }
 *   }
 * </pre></code>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Extension {
}
