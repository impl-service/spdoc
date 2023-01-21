package cn.subat.impl.spdoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
public @interface SPDocSecurity {
    String type();
    String scheme();
    String bearerFormat() default "JWT";
}
