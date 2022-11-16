package cn.subat.implservice.spdoc.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Inherited
public @interface SPDocField {
    String value();
    String format() default "";
}
