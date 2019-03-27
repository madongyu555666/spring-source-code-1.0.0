package com.spring.mvcframework.v1.annotation;


import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MARequestParam {
    String value() default "";
}
