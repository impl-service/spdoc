package cn.subat.impl.spdoc.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.SOURCE)
@Inherited
public @interface SPDocField {
    String value();
    String format() default "";
}
