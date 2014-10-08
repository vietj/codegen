package io.vertx.codegen.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method as native, such native method follows the codegen translations rules, however
 * illegal parameter type and return type are also allowed and will be ignored by the translation.<p>
 *
 * The method body will also be ignored, instead it will be provided in the method javadoc. Several method
 * implementations can be provided, a method with no implementation should be considered as ignored by
 * the target language.<p>
 *
 * <code><pre>
 *   {@literal @}VertxGen
 *   public interface Buffer {
 *
 *     /**
 *      * &lt;native:java&gt;
 *      *   return appendString(cs.toString());
 *      * &lt;/native:java&gt;
 *      *{@literal /}
 *     {@literal @}Native
 *     {@literal @}Fluent
 *     public Buffer appendCharSequence(CharSequence cs);
 *   }
 * </pre></code>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Native {
}
