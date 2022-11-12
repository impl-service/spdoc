package cn.subat.implservice.spdoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;
import java.util.Map;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface SPDocConsumer {
    String name() default "";
}
