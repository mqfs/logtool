<p align="right"><a href="https://github.com/mqfs/logtool/blob/master/README_zh.md">中文版</a></p>

# Logtool
Logtool is a Java library that provides facilities to log each method's parameters and result within method-invocation-chain in Spring/SpringBoot application. It is based on JSR 269 (Pluggable Annotation Processing API) and javac-relevant api. Just use one annotation on class and another one annotation on its method to avoid redundant log hard code.

## Features
1. Currently coordinate with Lombok's `@Data` or `@ToString` annotation
2. Support Spring Boot or Spring application
3. Support configurable class/method-level-switch based on properties within application.yml/application.properties (Spring-relevant application) or Apollo in enabling to log contents
4. Support  getting TraceId from HttpServletRequest Header

## TODO
1. Support configurable time period for logging upon class/method level
2. Support generic methods and generic parameters
3. Support log within multi-threads
4. Support anonymous class
5. ~~Support print line number of return statement before printing its result~~
6. Normalize line-number-table attribute of modified classes

## Maven
```
<dependency>
    <groupId>com.yuangancheng</groupId>
    <artifactId>logtool</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Quick Start
* Annotate a class with `@EnableTraceLog`.
* Annotate a method with `@TraceLog` in the previous annotated class.  
  For example, there is a class and an affiliated method.  
  ```
    @EnableTraceLog
    public class Dog {
    ...
    @TraceLog
    private int bark(int frequency) {
        ...
        return 1;
        ...
    }
    ...
    }
  ```  
  Then, you will see printed `bark's` parameters and result on the screen when invoke this method at runtime.

## Advanced usage in Spring application
* Enable logging request id  
  In general, every servlet request has an UUID within its header. So if you want to log it, you need to configure it. For example,  
  Given `@EnableTraceLog(reqIdName="abc")`,  
  Then, the logtool will get the request id by searching the request header fields with the name `abc` and the printed information of methods will include the request id.
  
* Enable static class-level-switch  
  The _class-level-switch_ means a variable in a class. This variable can control the behaviour of logtool in the whole class at runtime. The value of this variable can be `1` which represents __"activate printing"__ or `0` which represents __"deactivate printing"__. This functionality is based on Spring annotation `@Value`.  
  Given `@EnableTraceLog(enableClassLevelSwitch = true, switchKey = "x.y.z")`,  
  Then, you need to ensure there exists a property pair named `x.y.z=1` or `x.y.z=0` in `application.properties` or `application.yml` file. The logtool will generate a variable and put into the class. The logtool will control the behaviour of logging part in the whole class based on the property's value at runtime.  
  
  * Enable dynamic class-level-switch (advanced)  
    Just using [Apollo](https://github.com/ctripcorp/apollo) or any configuration manager which works perfectly with Spring annotation `@Value` in your Spring application.
    
* Enable static method-level-switch  
  The _method-level-switch_ has a similiar meaning as _class-level-switch_. However, _method-level-switch_ has a smaller granularity that it can only control the behaviour of a single method at runtime.  
  Given `@TraceLog(enableMethodLevelSwitch = true, switchKey = "a.b.c")  
  Then, as the former one, you also need to ensure there exists a property pair named `a.b.c=1` or `a.b.c=0` in `application.properties` or `application.yml` file. and the logtool will generate a variable and put into the class. The logtool will control the behaviour of logging part of the method based on the property's value at runtime.  
  
    * Enable dynamic method-level-switch (advanced)  
      The usage is the same as the "dynamic class-level-switch".
