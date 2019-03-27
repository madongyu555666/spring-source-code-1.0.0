package com.spring.demo.service.impl;

import com.spring.demo.service.IDemoService;
import com.spring.mvcframework.v1.annotation.MAService;

@MAService
public class DemoService implements IDemoService {
    @Override
    public String get(String name) {
        return "My name is " + name;
    }
}
