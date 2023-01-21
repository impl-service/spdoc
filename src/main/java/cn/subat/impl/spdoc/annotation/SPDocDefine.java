package cn.subat.impl.spdoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface SPDocDefine {
    String title() default "";
    String description() default "";
    String url() default "";
    String termsOfService() default "";
    String version() default "1.0";
    String key() default "";
    SPDocSecurity securitySchema() default @SPDocSecurity(scheme = "bearer",type = "http");

}
