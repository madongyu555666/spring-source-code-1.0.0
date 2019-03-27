package com.spring.mvcframework.v1.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MARequestMapping {
    String value() default "";
}
