package com.spring.mvcframework.v1.annotation;


import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MAAutowired {
    String value() default "";
}
