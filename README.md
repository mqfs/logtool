# Logtool
Logtool is a Java library that provides facilities to log each method's parameters and result within method-invocation-chain. It is based on JSR 269 (Pluggable Annotation Processing API) and javac-relevant api. Just use one annotation on class and another one annotation on its method to avoid redundant log hard code.

## Features
1. Coordinate with Lombok.
2. Support Spring Boot or Spring application.
3. Support configurable class/method level switch based on properties within application.yml/application.properties (Spring-relevant application) or Apollo in enabling to log contents.
4. Support  getting UUID from HttpServletRequest Header or Parameters.