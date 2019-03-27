package com.spring.mvcframework.v1.servlet;

import com.spring.mvcframework.v1.annotation.*;



import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MADispatcherServlet extends HttpServlet {
    private Map<String,Object> mapping = new HashMap<String, Object>();

    //存储aplication.properties 的配置内容
    private Properties contextConfig = new Properties();
    //存储所有扫描到的类
    private List<String> classNames = new ArrayList<String>();
    //IOC 容器，保存所有实例化对象
    private Map<String,Object> ioc = new HashMap<String,Object>();
    //保存Contrller 中所有Mapping 的对应关系
   // private Map<String, Method> handlerMapping = new HashMap<String,Method>();
    private List<Handler> handlerMapping = new ArrayList<Handler>();//就是把参数的对应关系封装到对象里

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try{
            doDispatch(req,resp);
        }catch (Exception e){
            resp.getWriter().write("500 Exception " + Arrays.toString(e.getStackTrace()));
        }
    }


    /**
     * 初始化
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、扫描相关的类
        doScanner(contextConfig.getProperty("scanPackage"));
        //3、初始化所有相关的类的实例，并且放入到IOC 容器之中
        doInstance();
        //4、完成依赖注入
        doAutowired();
        //5、初始化HandlerMapping
        initHandlerMapping();
        System.out.println("GP Spring framework is init.");
    }

    /**
     * 初始化HandlerMapping
     */
    private void initHandlerMapping() {
         //url和方法对应
        if(ioc.isEmpty()){ return; }
        for ( Map.Entry<String, Object> entry:ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MAController.class)){continue;}
            String url="";
            //获取Controller 的url 配置
            if(clazz.isAnnotationPresent(MARequestMapping.class)){
                MARequestMapping maRequestMapping = clazz.getAnnotation(MARequestMapping.class);
                url = maRequestMapping.value();
            }
            //获取方法上的(改造)
            Method [] methods = clazz.getMethods();
            for (Method method : methods) {
                //没有加RequestMapping 注解的直接忽略
                if(!method.isAnnotationPresent(MARequestMapping.class)){ continue; }
                //映射URL
                MARequestMapping annotation = method.getAnnotation(MARequestMapping.class);
                String regex = ("/" + url + annotation.value()).replaceAll("/+", "/");
                Pattern pattern = Pattern.compile(regex);//jdk提供的正则类
                handlerMapping.add(new Handler(pattern,entry.getValue(),method));//把对应的数据封装到一个Handler，现在Handler有方法的url，方法实例，参数
                System.out.println("mapping " + regex + "," + method);

            }
            


            /*String baseUrl = "";
            if(clazz.isAnnotationPresent(MAController.class)){
                MARequestMapping maRequestMapping = clazz.getAnnotation(MARequestMapping.class);
                baseUrl = maRequestMapping.value();
            }

            for(Method method :clazz.getMethods()){
                if(!method.isAnnotationPresent(MARequestMapping.class)){continue;}
                MARequestMapping annotation = method.getAnnotation(MARequestMapping.class);
                String url = ("/" + baseUrl + "/" + annotation.value())
                        .replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("Mapped :" + url + "," + method);
            }*/
        }

    }

    /**
     * 完成依赖注入
     */
    private void doAutowired() {
        if(ioc.isEmpty()){return;}
        for ( Map.Entry<String, Object> entry:ioc.entrySet()) {
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(MAAutowired.class)){continue;}
                MAAutowired service = field.getAnnotation(MAAutowired.class);
                String beanName = service.value().trim();
                if("".equals(beanName)){
                     beanName=field.getType().getName();
                }
                field.setAccessible(true);//强行获取反射（private）
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 扫描相关的类
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        //根据传进来的包，来扫描相关的类
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        File classPath=new File(url.getFile());
        for (File file:classPath.listFiles()) {
            if(file.isDirectory()){
                //如果是文件在遍历
                doScanner(scanPackage + "." + file.getName());
            }else{
                //文件就保存在classNames
                if(!file.getName().endsWith(".class")){ continue;}
                String className = (scanPackage + "." + file.getName().replace(".class",""));
                classNames.add(className);
            }
        }

    }

    /**
     * 初始化所有相关的类的实例，并且放入到IOC 容器之中
     */
    private void doInstance() {
        //初始化classNames中的类，为DI做准备
        if(classNames.isEmpty()){return;}
        try{
            for (String  className:classNames) {
                Class<?> clazz = Class.forName(className);
               if(clazz.isAnnotationPresent(MAController.class)){//去查找MAController注解
                   Object instance = clazz.newInstance();
                   //Spring默认类名首字母小写
                   String beanName = toLowerFirstCase(clazz.getSimpleName());
                   ioc.put(beanName,instance);//名字+实例
               }else if (clazz.isAnnotationPresent(MAService.class)){//去查找MAService注解
                   MAService annotation = clazz.getAnnotation(MAService.class);
                   String beanName = annotation.value();
                   if("".equals(beanName.trim())){
                       beanName=toLowerFirstCase(clazz.getSimpleName());
                   }
                   Object instance = clazz.newInstance();
                   ioc.put(beanName,instance);
                   //根据类型自动赋值
                   for (Class<?> i:clazz.getInterfaces()) {
                       if(ioc.containsKey(i.getName())){
                           throw new Exception("The “" + i.getName() + "” is exists!!");
                       }
                       //把类型的接口当成key
                       ioc.put(i.getName(),instance);
                   }
               }else {
                   continue;
               }
            }
        }catch (Exception e){
            e.printStackTrace();
        }



    }


    //如果类名本身是小写字母，确实会出问题
    //但是我要说明的是：这个方法是我自己用，private的
    //传值也是自己传，类也都遵循了驼峰命名法
    //默认传入的值，存在首字母小写的情况，也不可能出现非字母的情况

    //为了简化程序逻辑，就不做其他判断了，大家了解就OK
    //其实用写注释的时间都能够把逻辑写完了
    private String toLowerFirstCase(String simpleName) {
        char [] chars = simpleName.toCharArray();
        //之所以加，是因为大小写字母的ASCII码相差32，
        // 而且大写字母的ASCII码要小于小写字母的ASCII码
        //在Java中，对char做算学运算，实际上就是对ASCII码做算学运算
        chars[0] += 32;
        return String.valueOf(chars);
    }

    /**
     * 加载配置文件
     * @param contextConfigLocation
     */
    private void doLoadConfig(String contextConfigLocation) {
        InputStream inputStream=null;
        try{
            inputStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
            contextConfig.load(inputStream);
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            try {
                if(null != inputStream){inputStream.close();}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 运行时的方法
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        try {
            Handler handler = getHandler(req);
            if (handler == null) {
// if(!this.handlerMapping.containsKey(url)){
                resp.getWriter().write("404 Not Found!!!");
                return;
            }

            //获得方法的形参列表
            Class<?>[] paramTypes = handler.method.getParameterTypes();

            //保存所有需要自动赋值的参数值
            Object[] paramValues = new Object[paramTypes.length];

            //获取请求的参数列表
            Map<String, String[]> params = req.getParameterMap();

            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");

                //如果找到匹配的对象，则开始填充参数值
                if (!handler.paramIndexMapping.containsKey(param.getKey())) {
                    continue;
                }

                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index], value);
            }

            if (handler.paramIndexMapping.containsKey(HttpServletRequest.class.getName())) {
                int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
                paramValues[reqIndex] = req;
            }

            if (handler.paramIndexMapping.containsKey(HttpServletResponse.class.getName())) {
                int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
                paramValues[respIndex] = resp;
            }
            Object returnValue = handler.method.invoke(handler.controller, paramValues);
            if (returnValue == null || returnValue instanceof Void) {
                return;
            }
            resp.getWriter().write(returnValue.toString());


        }catch (Exception e){
            e.printStackTrace();
        }

        //改造
        /*try{
            String  url=req.getRequestURI();
            //处理成相对路径
            String contextPath = req.getContextPath();
            url = url.replaceAll(contextPath,"").replaceAll("/+","/");

            if(!this.handlerMapping.containsKey(url)){
                resp.getWriter().write("404 Not Found!!!");
                return;
            }

            Method method = this.handlerMapping.get(url);
            String beanName  = toLowerFirstCase(method.getDeclaringClass().getSimpleName());
            //获取客户请求的参数参数列表
            Map<String,String[]> params = req.getParameterMap();

            //获取方法的形参列表
            Class<?> [] parameterTypes = method.getParameterTypes();
            //执行方法，写死
            method.invoke(ioc.get(beanName),new Object[]{req,resp,params.get("name")[0]});
        }catch (Exception e){
            e.printStackTrace();
        }*/



    }
   /* 一个是
    convert()方法，主要负责url 参数的强制类型转换。*/
    //转换参数
   //url传过来的参数都是String类型的，HTTP是基于字符串协议
   //只需要把String转换为任意类型就好
   private Object convert(Class<?> type,String value){
       //如果是int
       if(Integer.class == type){
           return Integer.valueOf(value);
       }
       else if(Double.class == type){
           return Double.valueOf(value);
       }
       //如果还有double或者其他类型，继续加if
       //这时候，我们应该想到策略模式了
       //在这里暂时不实现，希望小伙伴自己来实现
       return value;
   }

    //一个是getHandler()方法，主要负责处理url 的正则匹配
    private Handler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()){ return null; }
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        //得到请求的url
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            try{
                //通过jdk正则类处理url（请求），来匹配handler里的url地址
                Matcher matcher = handler.pattern.matcher(url);
                if(!matcher.matches()){ continue; }
                //找到请求对应的方法
                return handler;
            }catch (Exception e){
                e.printStackTrace();
            }
        }
       return null;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }



    private class Handler{
        protected Object controller; //保存方法对应的实例
        protected Method method; //保存映射的方法
        protected Pattern pattern;//java的jdk提供的正则类（url）
        private Class<?> [] paramTypes; //参数顺序列表


        public Pattern getPattern() {
            return pattern;
        }

        public Method getMethod() {
            return method;
        }

        public Object getController() {
            return controller;
        }

        public Class<?>[] getParamTypes() {
            return paramTypes;
        }

        //形参列表
        //参数的名字作为key,参数的顺序，位置作为值
        private Map<String,Integer> paramIndexMapping;

        //构造handler
        protected Handler(Pattern pattern,Object controller,Method method){
             this.pattern=pattern;
             this.controller=controller;
             this.method=method;
            paramTypes = method.getParameterTypes();
            paramIndexMapping=new HashMap<String,Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method){
            //提取方法中加了注解的参数
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length ; i ++) {
                for (Annotation a:parameterAnnotations[i]) {
                    if(a instanceof MARequestParam){
                        String paramName = ((MARequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }
           //提取方法中的request 和response 参数
            Class<?> [] paramsTypes=method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length ; i ++){
                Class<?> type = paramsTypes[i];
                if(type==HttpServletRequest.class || type==HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);

                }
            }

        }


    }
}
