package com.dongboy.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author dongboy
 * @what time    2023/7/31 15:05
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DongDistributedLock {

    String prefix() default "";

    String[] value();

    long waitTimeout() default -1;

    long lockTime() default -1;

}
