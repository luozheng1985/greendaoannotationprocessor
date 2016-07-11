## greendaoannotationprocessor
####1、该注解的主要目的
    ORM数据库目前[greendao][http://greenrobot.org/greendao/]各种性能都比较不错，
但是有个比较郁闷的地方是创建Bean和生成Dao是在一个java工程中，
这个对于项目的管理、合作开发以及维护方面会带来一些问题，因而这里采用注解的方式来解决这个这个问题，
使得Dao的创建以及注入到DaoSession中都在同一个项目中，便于升级和维护，另外，因为采用的是Java Annotation Processing，
而非反射的技术，因而不会对运行时的性能有影响。

####2、注解的基本功能
    目前这个注解框架并没有完全支持到greendao的所有相关功能，比如一对多，多对一等等，
另外，因为annotation本身的返回值只支持基本数据类型、String以及Class,
因而这里的表中字段也有些限制，比如不能为Date。另外还需注意的地方有：
1)对于Boolean类型的字段get/set方法有一个要求是不能去掉is;
2)Bean里面必须含有一个符合要求的构造函数;


####3、如何使用,详细使用参考这里：http://www.jianshu.com/p/0646b338ee0a
@Dao:用来指定表名；
@DaoProperty:用来指定表中的字段；
```java
package com.roobo.domgy.database;


import com.roobo.greendaoannotationprocessor.Dao;
import com.roobo.greendaoannotationprocessor.DaoProperty;

/**
 * Created by LuoZheng on 2016/7/8.
 */
@Dao("TB_USER")
public class User {
    @DaoProperty(ordinal = 0,isPrimaryKey = true)
    private Long id;

    @DaoProperty(ordinal = 1)
    private String nickName;

    @DaoProperty(ordinal = 2)
    private int age;

    private String sex;

    @DaoProperty(ordinal = 3)
    private boolean isMan;

    public User(Long id) {
        this.id = id;
    }

    public User(Long id, String nickName, Integer age, Boolean isMan) {
        this.id = id;
        this.nickName = nickName;
        this.age = age;
        this.isMan = isMan;
    }

    public User(String nickName,int age,boolean isMan) {
        this.nickName = nickName;
        this.age = age;
        this.isMan = isMan;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public boolean getIsMan() {// 注意这里的get方法
        return isMan;
    }

    public void setIsMan(boolean man) {// 注意这里的set方法
        isMan = man;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", nickName='" + nickName + '\'' +
                ", age=" + age +
                ", sex='" + sex + '\'' +
                ", isMan=" + isMan +
                '}';
    }
}

```







