<p align="right"><a href="https://github.com/mqfs/logtool/blob/master/README.md">English version</a></p>

# Logtool
Logtool是一个支持打印Spring应用里的方法调用链中的每个方法的入参和返回值的Java库。这款库是基于JSR 269和javac编译器api进行开发。在类上和方法上各加一个注解就能打印信息，解放人力，专注业务。

## 特点
1. 除虚方法之外，支持任何访问限定符的方法。
2. 当前需要和[Lombok](https://github.com/rzwitserloot/lombok)的`@Data`注解或`@ToString`注解搭配使用。
3. 支持Spring或Spring Boot应用。
4. 当前支持在Spring或Spring Boot应用中配置`application.yml`或者`application.properties`进行使用类级别和方法级别的开关；使用[Apollo](https://github.com/ctripcorp/apollo)或类似的配置中心支持动态特性。
5. 在Spring或Spring Boot应用中支持从请求的头部中获取id。

## 待解决问题
