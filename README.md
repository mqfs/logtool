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
1. Annotate a class with `@EnableTraceLog`.
2. Annotate a method with `@TraceLog` in the previous annotated class.

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
Then, you will see printed `bark's` parameters and result on the screen when invoke it during runtime.
