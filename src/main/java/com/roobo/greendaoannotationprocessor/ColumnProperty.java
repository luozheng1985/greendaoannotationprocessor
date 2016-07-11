package com.roobo.greendaoannotationprocessor;

import java.util.HashMap;

/**
 * Created by LuoZheng on 2016/7/4.
 */
public class ColumnProperty implements Comparable<ColumnProperty>{

    private final int ordinal;
    private final Class<?> type;
    private final String stmtBindType;
    private final String cursorGetType;
    private final String name;
    private final boolean primaryKey;
    private final String columnName;

    public static final HashMap<Class,String> propertyTypeMap;

    static {
        propertyTypeMap = new HashMap<Class,String>();
        propertyTypeMap.put(Long.class,"INTEGER");
        propertyTypeMap.put(Integer.class,"INTEGER");
        propertyTypeMap.put(String.class,"TEXT");
        propertyTypeMap.put(Boolean.class,"INTEGER");
    }

    /**
     * @param ordinal 该字段在表中的索引
     * @param type 该字段属于那种类型，String/Long/Date/Boolean/....
     * @param name 该字段在bean中对应的属性名
     * @param primaryKey 是否为主键
     * @param columnName 在表中的字段名
     */
    public ColumnProperty(int ordinal, Class<?> type, String name, boolean primaryKey, String columnName,String stmtBindType,String cursorGetType) {
        this.ordinal = ordinal;
        this.type = type;
        this.name = name;
        this.primaryKey = primaryKey;
        this.columnName = columnName;
        this.stmtBindType = stmtBindType;
        this.cursorGetType = cursorGetType;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public Class<?> getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public String getColumnName() {
        return columnName;
    }

    public String getStmtBindType() {
        return stmtBindType;
    }

    public String getCursorGetType() {
        return cursorGetType;
    }

    @Override
    public int compareTo(ColumnProperty another) {

        if(another.getOrdinal() == ordinal){
            throw new IllegalArgumentException("Property Ordinal Cannot be the same !");
        }

        if(ordinal > another.getOrdinal()){
            return 1;
        }else if(ordinal < another.getOrdinal()){
            return -1;
        }else{
            return 0;
        }
    }

    @Override
    public String toString() {
        return "ColumnProperty{" +
                "ordinal=" + ordinal +
                ", type=" + type +
                ", stmtBindType='" + stmtBindType + '\'' +
                ", cursorGetType='" + cursorGetType + '\'' +
                ", name='" + name + '\'' +
                ", primaryKey=" + primaryKey +
                ", columnName='" + columnName + '\'' +
                '}';
    }
}
