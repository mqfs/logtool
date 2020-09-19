package com.yuangancheng.logtool.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface TraceLog {
    boolean enableMethodLevelSwitch() default false;
    String switchKey() default "";
}
