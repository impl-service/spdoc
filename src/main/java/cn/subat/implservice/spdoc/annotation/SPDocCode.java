package cn.subat.implservice.spdoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
public @interface SPDocCode {
    int code();
    String msg();
}
