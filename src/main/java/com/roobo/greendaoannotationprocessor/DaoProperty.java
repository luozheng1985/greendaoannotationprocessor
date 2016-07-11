package com.roobo.greendaoannotationprocessor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by LuoZheng on 2016/7/7.
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface DaoProperty {
    int ordinal();// 该字段在表中的列号

    boolean isPrimaryKey() default false;// 是否为主键

}
