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
1. 支持可配置的时间段日志输出。
2. 支持泛型。
3. 支持匿名类。
4. 清洗class文件的行号表。

## Maven引入
```
<dependency>
    <groupId>com.yuangancheng</groupId>
    <artifactId>logtool</artifactId>
    <version>0.0.1</version>
</dependency>
```

## 快速开始
* 在一个类加上`@EnableTraceLog`注解。
* 然后再在这个类中的一个方法加上`@TraceLog`注解。
  例如，这里有一个包含一个方法的类。  
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
  然后，当你在运行期调用这个方法时，会在控制台看见打印出的`bark`方法的入参和返回值。

## 在Spring应用中的高级用法
* 启用打印请求id  
  通常来说，每个请求的头部应该包含一个id。如果你想打印这个id，就需要配置它。例如，  
  给定一个`@EnableTraceLog(reqIdName="abc")`注解，  
  然后logtool会通过搜索请求的头部去获取名字叫`abc`的值，这样在打印出的信息中就会包含它。
  
* 启用**静态**的类级别的开关  
  _类级别开关_ 是类中的变量。这个变量能在运行期控制**整个**类的行为。这个变量的值只能为`0`或`1`。`0`代表关闭打印的功能；`1`代表打开打印的功能。这个功能是基于Spring的`@Value`注解。  
  给定`@EnableTraceLog(enableClassLevelSwitch = true, switchKey = "x.y.z")`注解，  
  然后，你需要确保在应用的配置文件`application.properties`或`application.yml`中存在`x.y.z=1`或`x.y.z=0`属性对。logtool会在该类中生成一个变量。这样在运行期就能根据该变量的值去控制整个类的打印的行为。
  
  * 启用**动态**的类级别的开关（高级）  
    通过搭配使用[Apollo](https://github.com/ctripcorp/apollo)或其他的具有配置中心功能的能完美配合Spring的`@Value`注解的框架。
    
* 启用**静态**的方法级别的开关
  _方法级别开关_ 和_类级别开关_有着类似的定义，但是 _方法级别开关_ 有更小的粒度也就是只能在运行期控制类中的一个方法。  
  给定`@TraceLog(enableMethodLevelSwitch = true, switchKey = "a.b.c")`注解，  
  然后，就和前面所介绍的一样，你需要确保在应用的配置文件`application.properties`或`application.yml`中存在`a.b.c=1`或`a.b.c=0`属性对。logtool会在类中生成一个变量，这样在运行期就能根据该变量的值去控制这个方法的打印的行为。
  
    * 启用**动态**的方法级别的开关（高级）  
      和前述一样，通过搭配使用[Apollo](https://github.com/ctripcorp/apollo)或其他的具有配置中心功能的能完美配合Spring的`@Value`注解的框架。
      
## 构建
如果你想克隆这个项目，然后你最好跟随下面的步骤：
* 通过Intellij IDEA导入
* 手动添加本地的jdk(版本：1.8)的库(`jdk\lib\tools.jar`)到该项目的库中。
