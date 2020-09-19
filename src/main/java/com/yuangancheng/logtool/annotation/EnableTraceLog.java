package com.yuangancheng.logtool.annotation;

import java.lang.annotation.*;

/**
 * @author: Gancheng Yuan
 * @date: 2020/9/15 17:49
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface EnableTraceLog {
    String loggerName() default "log";
    String reqIdName() default "";
    boolean enableClassLevelSwitch() default false;
    String switchKey() default "";
}
