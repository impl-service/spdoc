package cn.subat.impl.spdoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface SPDocConsumer {
    String value() default "";
    SPDocCode[] code() default {};
    String tag() default "";
    String description() default "";
}
