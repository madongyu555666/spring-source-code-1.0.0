package com.spring.mvcframework.v1.annotation;


import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MAController {
    String value() default "";
}
