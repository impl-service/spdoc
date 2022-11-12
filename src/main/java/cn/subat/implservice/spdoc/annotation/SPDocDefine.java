package cn.subat.implservice.spdoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface SPDocDefine {
    /**
     * The title of the application.
     *
     * @return the application's title
     **/
    String title() default "";

    /**
     * A short description of the application. CommonMark syntax can be used for rich text representation.
     *
     * @return the application's description
     **/
    String description() default "";
    String url() default "";

    /**
     * A URL to the Terms of Service for the API. Must be in the format of a URL.
     *
     * @return the application's terms of service
     **/
    String termsOfService() default "";

    /**
     * The version of the API definition.
     *
     * @return the application's version
     **/
    String version() default "";

}
