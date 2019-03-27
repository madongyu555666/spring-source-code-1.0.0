package com.spring.demo.controller;


import com.spring.demo.service.IDemoService;
import com.spring.demo.service.impl.DemoService;
import com.spring.mvcframework.v1.annotation.MAAutowired;
import com.spring.mvcframework.v1.annotation.MAController;
import com.spring.mvcframework.v1.annotation.MARequestMapping;
import com.spring.mvcframework.v1.annotation.MARequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@MAController
@MARequestMapping("/login")
public class DemoController {

    @MAAutowired
    private IDemoService demoService;

    @MARequestMapping("/get")
    public  void  getString(HttpServletRequest req, HttpServletResponse resp,
                              @MARequestParam("name") String name){
        String s = demoService.get(name);
        try{
            resp.getWriter().write(s);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
