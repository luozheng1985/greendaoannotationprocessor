package com.roobo.greendaoannotationprocessor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Created by LuoZheng on 2016/7/8.
 * 注意点：
 * 1、对于Bean中的Boolean变量，有个要求是get/set方法要和其他属性一样要写全，编译器自己生成的如果属性是is开头的话，会被干掉！！！
 * 2、Bean里面必须含有一个符合要求的构造函数，要求是啥？呵呵！
 * 3、在对应的Bean同级目录下会生成一个BeanDao，并注入到DaoSession中
 */
@SupportedAnnotationTypes(value = {"com.roobo.greendaoannotationprocessor.Dao","com.roobo.greendaoannotationprocessor.DaoProperty"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions(value = {"appDaoSession","appDaoMaster","appDatabaseVersion"})
public class DaoAnnotationProcessor extends AbstractProcessor{
    private static final String OPTION_DAO_SESSION = "appDaoSession";
    private static final String OPTION_DAO_MASTER = "appDaoMaster";
    private static final String OPTION_DATABASE_VERSION = "appDatabaseVersion";
    private Messager logMessger;


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        logMessger = processingEnv.getMessager();
        if(annotations.isEmpty()){
            return false;
        }

        // 找到所有@Dao注解的类
        Set<? extends Element> daoElements = roundEnv.getElementsAnnotatedWith(Dao.class);
        // DaoSession类生成
        createDaoSession(daoElements);
        // DaoMaster类生成
        createDaoMater(daoElements);
        // 创建每个Bean对应的Dao类
        for(Element element : daoElements){
            // 每个@Dao注解的类生成对应的Dao文件
            createDaoFile(element);
        }
        return true;
    }

    /**
     * 生成对应的Dao文件
     * @param daoElement
     */
    private void createDaoFile(Element daoElement){

        //logMessger.printMessage(Diagnostic.Kind.OTHER,"element simpleName:" + daoElement.getSimpleName());
        if(daoElement instanceof TypeElement){
            BufferedWriter writer = null;
            try { // write the file
                TypeElement daoTypeElement = (TypeElement) daoElement;//

                String beanClassSimpleName = daoTypeElement.getSimpleName().toString();// bean对应的类名
                String beanClassFullName = daoTypeElement.getQualifiedName().toString();// bean对应的包名和类名
                Dao dao = daoElement.getAnnotation(Dao.class);
                //logMessger.printMessage(Diagnostic.Kind.OTHER,"dao type element simpleName:" + beanClassSimpleName);
                //logMessger.printMessage(Diagnostic.Kind.OTHER,"dao type element QualifiedName:" + beanClassFullName);
                int period = beanClassFullName.lastIndexOf('.');
                String myPackage = period > 0 ? beanClassFullName.substring(0, period) : null;// 包名,和Bean同一个package下
                String classSimpleName = beanClassSimpleName + "Dao";// 类名，bean+Dao
                String classFullName = myPackage + "." + classSimpleName;// 得到文件名
                logMessger.printMessage(Diagnostic.Kind.OTHER,"#######Create Table : " + dao.value());
                logMessger.printMessage(Diagnostic.Kind.OTHER,"### Dao Name :" + classFullName);
                List<ColumnProperty> daoProperties = daoProperties(daoTypeElement);// 所有@DaoProperty注解的属性
                // 创建文件
                JavaFileObject source = processingEnv.getFiler().createSourceFile(classFullName);
                writer = new BufferedWriter(source.openWriter());
                writer.write("package " + myPackage + ";\n\n");
                writer.write("import android.database.Cursor;\n");
                writer.write("import android.database.sqlite.SQLiteDatabase;\n");
                writer.write("import android.database.sqlite.SQLiteStatement;\n");
                writer.write("import de.greenrobot.dao.AbstractDao;\n");
                writer.write("import de.greenrobot.dao.Property;\n");
                writer.write("import android.util.Log;\n");
                String daoSessionFileName = processingEnv.getOptions().get(OPTION_DAO_SESSION);
                writer.write(String.format(Locale.getDefault(),"import %s;\n",daoSessionFileName));
                writer.write("import de.greenrobot.dao.internal.DaoConfig;\n\n");

                writer.write(String.format(Locale.getDefault(),"public class %s extends AbstractDao<%s, Long> {\n\n",classSimpleName,beanClassSimpleName));
                // 表名
                writer.write(String.format(Locale.getDefault(),"    public static final String TABLENAME = \"%s\";\n\n",dao.value()));
                // Properties类生成
                writePropertiesClass(writer,daoProperties);
                // 构造函数生成
                writer.write(String.format(Locale.getDefault(),"    public %s(DaoConfig config) {\n",classSimpleName));
                writer.write(String.format(Locale.getDefault(),"        super(config);\n"));
                writer.write(String.format(Locale.getDefault(),"    }\n\n"));
                writer.write(String.format(Locale.getDefault(),"    public %s(DaoConfig config, DaoSession daoSession) {\n",classSimpleName));
                writer.write(String.format(Locale.getDefault(),"        super(config, daoSession);\n"));
                writer.write(String.format(Locale.getDefault(),"    }\n\n"));
                // 建表方法createTable生成
                writeCreateTable(writer,daoProperties,dao.value());
                // 删表方法dropTable生成
                writeDropTable(writer,dao.value());
                // bindValues方法生成
                writeBindValues(writer,beanClassSimpleName,daoProperties);
                // readKey方法生成
                writer.write(String.format(Locale.getDefault(),"    public Long readKey(Cursor cursor, int offset) {\n"));
                writer.write(String.format(Locale.getDefault(),"        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);\n"));
                writer.write(String.format(Locale.getDefault(),"    }\n\n"));
                // readEntity方法生成
                writeReadEntity(writer,beanClassSimpleName,daoProperties);
                // updateKeyAfterInsert方法生成
                writer.write(String.format(Locale.getDefault(),"    protected Long updateKeyAfterInsert(%s entity, long rowId) {\n",beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"        Log.v(\"Dao\",\"\"+entity + \",rowId = \" + rowId);\n"));
                writer.write(String.format(Locale.getDefault(),"        entity.setId(rowId);\n"));
                writer.write(String.format(Locale.getDefault(),"        return rowId;\n"));
                writer.write(String.format(Locale.getDefault(),"    }\n\n"));
                // getKey方法生成
                writer.write(String.format(Locale.getDefault(),"    public Long getKey(%s entity) {\n",beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"        if(entity != null) {\n"));
                writer.write(String.format(Locale.getDefault(),"            return entity.getId();\n"));
                writer.write(String.format(Locale.getDefault(),"        } else {\n"));
                writer.write(String.format(Locale.getDefault(),"            return null;\n"));
                writer.write(String.format(Locale.getDefault(),"        }\n"));
                writer.write(String.format(Locale.getDefault(),"    }\n\n"));
                // isEntityUpdateable方法生成
                writer.write(String.format(Locale.getDefault(),"    protected boolean isEntityUpdateable() {\n"));
                writer.write(String.format(Locale.getDefault(),"        return true;\n"));
                writer.write(String.format(Locale.getDefault(),"    }\n\n"));

                writer.write(String.format(Locale.getDefault(),"}\n"));
            } catch (IOException e) {
                // 这里不要向外抛异常，在文件已经生成的情况下会有IO异常，这是正常的。
                //throw new RuntimeException("Could not write source for " + sourceFile, e);
            }finally {
                if(writer != null){
                    try {
                        //writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }else{
            logMessger.printMessage(Diagnostic.Kind.ERROR,"@Dao is only valid for class",daoElement);
        }
    }// end createDaoFile()

    private void createDaoMater(Set<? extends Element> daoElements){
        String sourceFile = processingEnv.getOptions().get(OPTION_DAO_MASTER);
        BufferedWriter writer = null;
        try { // write the file
            int period = sourceFile.lastIndexOf('.');
            // 包名
            String myPackage = period > 0 ? sourceFile.substring(0, period) : null;
            // 类名
            String clazz = sourceFile.substring(period + 1);
            // 创建文件
            JavaFileObject source = processingEnv.getFiler().createSourceFile(sourceFile);
            writer = new BufferedWriter(source.openWriter());
            writer.write("package " + myPackage + ";\n\n");
            writer.write("import android.content.Context;\n");
            writer.write("import android.database.sqlite.SQLiteDatabase;\n");
            writer.write("import android.database.sqlite.SQLiteDatabase.CursorFactory;\n");
            writer.write("import android.database.sqlite.SQLiteOpenHelper;\n");
            writer.write("import de.greenrobot.dao.AbstractDaoMaster;\n");
            writer.write("import de.greenrobot.dao.identityscope.IdentityScopeType;\n\n");
            for(Element element : daoElements){
                if(element instanceof TypeElement){
                    TypeElement te = (TypeElement) element;
                    String beanClassFullName = te.getQualifiedName().toString();
                    writer.write(String.format(Locale.getDefault(),"import %sDao;\n",beanClassFullName));
                }else{
                    logMessger.printMessage(Diagnostic.Kind.ERROR,"@Dao is only valid for class",element);
                }
            }

            writer.write("\n\n");

            writer.write(String.format(Locale.getDefault(),"public class %s extends AbstractDaoMaster {\n",clazz));

            writer.write(String.format(Locale.getDefault(),"    public static final int SCHEMA_VERSION = %d;\n\n",Integer.parseInt(processingEnv.getOptions().get(OPTION_DATABASE_VERSION))));

            // createAllTables
            writer.write(String.format(Locale.getDefault(),"    public static void createAllTables(SQLiteDatabase db, boolean ifNotExists) {\n"));
            for(Element element : daoElements){
                TypeElement te = (TypeElement) element;
                String beanClassSimpleName = te.getSimpleName().toString();
                writer.write(String.format(Locale.getDefault(),"        %sDao.createTable(db, ifNotExists);\n",beanClassSimpleName));
            }
            writer.write(String.format(Locale.getDefault(),"    }\n\n"));

            // dropAllTables
            writer.write(String.format(Locale.getDefault(),"    public static void dropAllTables(SQLiteDatabase db, boolean ifExists) {\n"));
            for(Element element : daoElements){
                TypeElement te = (TypeElement) element;
                String beanClassSimpleName = te.getSimpleName().toString();
                writer.write(String.format(Locale.getDefault(),"        %sDao.dropTable(db, ifExists);\n",beanClassSimpleName));
            }
            writer.write(String.format(Locale.getDefault(),"    }\n\n"));
            // 构造函数
            writer.write(String.format(Locale.getDefault(),"    public %s(SQLiteDatabase db) {\n",clazz));
            writer.write(String.format(Locale.getDefault(),"        super(db, SCHEMA_VERSION);\n"));
            for(Element element : daoElements){
                TypeElement te = (TypeElement) element;
                String beanClassSimpleName = te.getSimpleName().toString();
                writer.write(String.format(Locale.getDefault(),"        registerDaoClass(%sDao.class);\n",beanClassSimpleName));
            }
            writer.write(String.format(Locale.getDefault(),"    }\n\n"));
            // newSession函数，这里直接写死了DaoSession,如果要写活一点，可以参考createDaoSession方法
            writer.write(String.format(Locale.getDefault(),"    public DaoSession newSession() {\n"));
            writer.write(String.format(Locale.getDefault(),"        return new DaoSession(db, IdentityScopeType.Session, daoConfigMap);\n"));
            writer.write(String.format(Locale.getDefault(),"    }\n\n"));

            writer.write(String.format(Locale.getDefault(),"    public DaoSession newSession(IdentityScopeType type) {\n"));
            writer.write(String.format(Locale.getDefault(),"        return new DaoSession(db, type, daoConfigMap);\n"));
            writer.write(String.format(Locale.getDefault(),"    }\n\n"));

            writer.write(String.format(Locale.getDefault(),"}\n"));
        } catch (IOException e) {
            // 这里不要向外抛异常，在文件已经生成的情况下会有IO异常，这是正常的。
            //throw new RuntimeException("Could not write source for " + sourceFile, e);
        }finally {
            if(writer != null){
                try {
                    //writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 生成DaoSession，这个类名其实是可以任意的，但是为了减少麻烦，也便于和官方统一，所以在配置的时候一定要是DaoSession
     * @param daoElements
     */
    private void createDaoSession( Set<? extends Element> daoElements){
        String sourceFile = processingEnv.getOptions().get(OPTION_DAO_SESSION);
        if(!sourceFile.endsWith(".DaoSession")){
            logMessger.printMessage(Diagnostic.Kind.ERROR,"Class Name Must be DaoSession");
            return ;
        }

        BufferedWriter writer = null;
        try { // write the file
            int period = sourceFile.lastIndexOf('.');
            // 包名
            String myPackage = period > 0 ? sourceFile.substring(0, period) : null;
            // 类名
            String clazz = sourceFile.substring(period + 1);
            // 创建文件
            JavaFileObject source = processingEnv.getFiler().createSourceFile(sourceFile);
            writer = new BufferedWriter(source.openWriter());
            writer.write("package " + myPackage + ";\n\n");
            writer.write("import java.util.Map;\n");
            writer.write("import android.database.sqlite.SQLiteDatabase;\n");
            writer.write("import de.greenrobot.dao.AbstractDao;\n");
            writer.write("import de.greenrobot.dao.AbstractDaoSession;\n");
            writer.write("import de.greenrobot.dao.identityscope.IdentityScopeType;\n");
            writer.write("import de.greenrobot.dao.internal.DaoConfig;\n");
            for(Element element : daoElements){
                if(element instanceof TypeElement){
                    TypeElement te = (TypeElement) element;
                    String beanClassFullName = te.getQualifiedName().toString();
                    writer.write(String.format(Locale.getDefault(),"import %s;\n",beanClassFullName));
                    writer.write(String.format(Locale.getDefault(),"import %sDao;\n",beanClassFullName));
                }else{
                    logMessger.printMessage(Diagnostic.Kind.ERROR,"@Dao is only valid for class",element);
                }
            }
            writer.write("\n\n");

            writer.write(String.format(Locale.getDefault(),"public class %s extends AbstractDaoSession {\n",clazz));
            for(Element element : daoElements){
                TypeElement te = (TypeElement) element;
                String beanClassSimpleName = te.getSimpleName().toString();
                writer.write(String.format(Locale.getDefault(),"    private final DaoConfig %sDaoConfig;\n",beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"    private final %sDao %sDao;\n",beanClassSimpleName,beanClassSimpleName));
                writer.write("\n");
            }
            // DaoSeesion构造函数
            writer.write(String.format(Locale.getDefault(),"    public %s(SQLiteDatabase db, IdentityScopeType type," + //
                    "Map<Class<? extends AbstractDao<?, ?>>, DaoConfig> daoConfigMap) {\n",clazz));
            writer.write(String.format(Locale.getDefault(),"        super(db);\n"));
            for(Element element : daoElements){
                TypeElement te = (TypeElement) element;
                String beanClassSimpleName = te.getSimpleName().toString();
                writer.write(String.format(Locale.getDefault(),"        %sDaoConfig = daoConfigMap.get(%sDao.class).clone();\n",beanClassSimpleName,beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"        %sDaoConfig.initIdentityScope(type);\n",beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"        %sDao = new %sDao(%sDaoConfig, this);\n",beanClassSimpleName,beanClassSimpleName,beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"        registerDao(%s.class, %sDao);\n",beanClassSimpleName,beanClassSimpleName));
                writer.write("\n");
            }
            writer.write(String.format(Locale.getDefault(),"    }\n\n"));
            // clear 方法
            writer.write(String.format(Locale.getDefault(),"    public void clear() {\n"));
            for(Element element : daoElements){
                TypeElement te = (TypeElement) element;
                String beanClassSimpleName = te.getSimpleName().toString();
                writer.write(String.format(Locale.getDefault(),"        %sDaoConfig.getIdentityScope().clear();\n",beanClassSimpleName));
            }
            writer.write(String.format(Locale.getDefault(),"    }\n\n"));

            // getXXDao方法
            for(Element element : daoElements){
                TypeElement te = (TypeElement) element;
                String beanClassSimpleName = te.getSimpleName().toString();
                writer.write(String.format(Locale.getDefault(),"    public %sDao get%sDao() {\n",beanClassSimpleName,beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"        return %sDao;\n",beanClassSimpleName));
                writer.write(String.format(Locale.getDefault(),"    }\n\n"));
            }

            // end
            writer.write(String.format(Locale.getDefault(),"}\n"));
        } catch (IOException e) {
            // 这里不要向外抛异常，在文件已经生成的情况下会有IO异常，这是正常的。
            //throw new RuntimeException("Could not write source for " + sourceFile, e);
        }finally {
            if(writer != null){
                try {
                    //writer.flush();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 获取Dao中所有的Property
     * @param daoTypeElement
     * @return 所有被 DaoProperty 注解的字段
     */
    private List<ColumnProperty> daoProperties(TypeElement daoTypeElement){
        // 这里获取的Element应该都是VariableElement类型
        List<? extends Element> enclosingElements = daoTypeElement.getEnclosedElements();// 得到所有的属性
        List<ColumnProperty> properties = new ArrayList<ColumnProperty>();
        for(Element e : enclosingElements){//
            //logMessger.printMessage(Diagnostic.Kind.OTHER,"enclosingElement simpleName:" + e.getSimpleName());
            DaoProperty daoProperty = e.getAnnotation(DaoProperty.class);
            if(daoProperty != null){// 其实都是VariableElement

                if(e instanceof VariableElement){
                    Class columnClazz = null;
                    String stmtBindType = "";
                    String cursorGetType = "";
                    // 在annotation中，被注解的属性只能是基本数据类型，Class,String
                    String fullTypeClass = e.asType().toString();
                    logMessger.printMessage(Diagnostic.Kind.OTHER,"fullTypeClass = " + fullTypeClass);
                    if(fullTypeClass.equals("java.lang.Long") || fullTypeClass.equals("long")){
                        columnClazz = Long.class;
                        stmtBindType = columnClazz.getSimpleName();
                        cursorGetType = stmtBindType;
                    }else if(fullTypeClass.equals("java.lang.Integer") || fullTypeClass.equals("int")){
                        columnClazz = Integer.class;
                        stmtBindType = Long.class.getSimpleName();
                        cursorGetType = "Int";
                    }else if(fullTypeClass.equals("java.lang.Boolean") || fullTypeClass.equals("boolean")){
                        columnClazz = Boolean.class;
                        stmtBindType = Long.class.getSimpleName();
                        cursorGetType = "Short";
                    }else { //if(fullTypeClass.equals("java.lang.String")){
                        if(fullTypeClass.equals("java.lang.String")){
                            columnClazz = String.class;
                            stmtBindType = String.class.getSimpleName();
                            cursorGetType = stmtBindType;
                        }else{
                            logMessger.printMessage(Diagnostic.Kind.ERROR,"@DaoProperty Field Must be Base Type or Class or String",e);
                        }
                    }
                    String fieldName = e.getSimpleName().toString();
                    String columnName = fieldName.equals("id")?"_id":fieldName.toUpperCase();

                    ColumnProperty property = new ColumnProperty(daoProperty.ordinal(),columnClazz,fieldName,daoProperty.isPrimaryKey(),columnName,stmtBindType,cursorGetType);
                    properties.add(property);
//                    logMessger.printMessage(Diagnostic.Kind.OTHER,property.toString());
                }else{
                    logMessger.printMessage(Diagnostic.Kind.ERROR,"@DaoProperty is only valid for field",e);
                    break;
                }
            }
        }
        Collections.sort(properties);
        return properties;
    }

    /***
     * 生成Properties类
     * @param writer
     * @param daoProperties
     * @throws IOException
     */
    private void writePropertiesClass(BufferedWriter writer,List<ColumnProperty> daoProperties) throws IOException {
        writer.write(String.format(Locale.getDefault(),"    public static class Properties {\n"));
        for(ColumnProperty p : daoProperties){
            writer.write(String.format(Locale.getDefault(),"        public final static Property %s = new Property(%d, %s, \"%s\", %s, \"%s\");\n",
                    p.getName().substring(0,1).toUpperCase()+p.getName().substring(1),p.getOrdinal(),p.getType().getSimpleName() + ".class",
                    p.getName(),String.valueOf(p.isPrimaryKey()),p.getColumnName()));
        }
        writer.write(String.format(Locale.getDefault(),"    };\n\n"));
    }

    /**
     * 生成建表的方法
     * @param writer
     * @param daoProperties
     * @param tableName
     * @throws IOException
     */
    private void writeCreateTable(BufferedWriter writer, List<ColumnProperty> daoProperties, String tableName) throws IOException {
        writer.write(String.format(Locale.getDefault(),"    public static void createTable(SQLiteDatabase db, boolean ifNotExists) {\n"));
        writer.write(String.format(Locale.getDefault(),"        String constraint = ifNotExists? \"IF NOT EXISTS \": \"\";\n"));
        writer.write(String.format(Locale.getDefault(),"        db.execSQL(\"CREATE TABLE \" + constraint + \"\\\"%s\\\" (\" + //\n",tableName));
        StringBuffer columnBuffer = new StringBuffer();
        for(int i=0,len=daoProperties.size();i<len;i++){
            columnBuffer.setLength(0);// 清空StringBuffer
            ColumnProperty p = daoProperties.get(i);
            String suffer = i==len-1?");\"); // %d: %s":",\" + // %d: %s";
            columnBuffer.append("          ");
            if(p.isPrimaryKey()){// 主键
                if(p.getType().isAssignableFrom(Long.class)){// 主键为Long类型
                    columnBuffer.append("\"\\\"%s\\\" %s PRIMARY KEY AUTOINCREMENT");
                }else{// 主键为非Long类型
                    columnBuffer.append("\"\\\"%s\\\" %s PRIMARY KEY");
                }
            }else{// 非主键
                columnBuffer.append("\"\\\"%s\\\" %s");
            }
            columnBuffer.append(suffer).append("\n");
            writer.write(String.format(Locale.getDefault(),columnBuffer.toString(),//
                    p.getColumnName(),ColumnProperty.propertyTypeMap.get(p.getType()),p.getOrdinal(),p.getName()));
        }
        writer.write(String.format(Locale.getDefault(),"    }\n\n"));
    }


    /**
     * 删表方法生成
     * @param writer
     * @param tableName
     */
    private void writeDropTable(BufferedWriter writer,String tableName) throws IOException{
        writer.write(String.format(Locale.getDefault(),"    public static void dropTable(SQLiteDatabase db, boolean ifExists) {\n"));
        writer.write(String.format(Locale.getDefault(),"        String sql = \"DROP TABLE \" + (ifExists ? \"IF EXISTS \" : \"\") + \"\\\"%s\\\"\";\n",tableName));
        writer.write(String.format(Locale.getDefault(),"        db.execSQL(sql);\n"));
        writer.write(String.format(Locale.getDefault(),"    }\n\n"));
    }


    /**
     * bindValues方法生成
     * @param writer
     * @param beanSimpleName
     * @param daoProperties
     * @throws IOException
     */
    private void writeBindValues(BufferedWriter writer,String beanSimpleName,List<ColumnProperty> daoProperties) throws IOException{
        writer.write(String.format(Locale.getDefault(),"    protected void bindValues(SQLiteStatement stmt, %s entity) {\n",beanSimpleName));
        writer.write(String.format(Locale.getDefault(),"        stmt.clearBindings();\n\n"));
        for(int i=0,len=daoProperties.size();i<len;i++){
            ColumnProperty p = daoProperties.get(i);
            writer.write(String.format(Locale.getDefault(),"        %s %s = entity.get%s();\n",
                    p.getType().getSimpleName(),p.getName(),p.getName().substring(0,1).toUpperCase()+p.getName().substring(1)));
            writer.write(String.format(Locale.getDefault(),"        if (%s != null) {\n",p.getName()));
            if(p.getType().equals(Boolean.class)){
                writer.write(String.format(Locale.getDefault(),"            stmt.bind%s(%d, %s ? 1L: 0L);\n",
                        p.getStmtBindType(),p.getOrdinal()+1,p.getName()));
            }else{
                writer.write(String.format(Locale.getDefault(),"            stmt.bind%s(%d, %s);\n",
                        p.getStmtBindType(),p.getOrdinal()+1,p.getName()));
            }

            writer.write(String.format(Locale.getDefault(),"        }\n\n",p.getName()));
        }
        writer.write(String.format(Locale.getDefault(),"    }\n\n"));
    }


    /**
     * readEntity 方法生成
     * @param writer
     * @param beanSimpleName
     * @param daoProperties
     */
    private void writeReadEntity(BufferedWriter writer,String beanSimpleName,List<ColumnProperty> daoProperties) throws IOException{
        writer.write(String.format(Locale.getDefault(),"    public %s readEntity(Cursor cursor, int offset) {\n",beanSimpleName));
        writer.write(String.format(Locale.getDefault(),"        %s entity = new %s( //\n",beanSimpleName,beanSimpleName));
        StringBuffer sBuffer = new StringBuffer();
        for(int i=0,len=daoProperties.size();i<len;i++) {
            sBuffer.setLength(0);
            sBuffer.append("            ");
            ColumnProperty p = daoProperties.get(i);
            if(p.getType().equals(Boolean.class)){
                sBuffer.append("cursor.isNull(offset + %d) ? null : cursor.get%s(offset + %d) != 0");
            }else {
                sBuffer.append("cursor.isNull(offset + %d) ? null : cursor.get%s(offset + %d)");
            }
            sBuffer.append(i == len - 1?"":",");
            sBuffer.append("    // %s").append("\n");
            writer.write(String.format(Locale.getDefault(),sBuffer.toString(), //
                    p.getOrdinal(),p.getCursorGetType(), p.getOrdinal(),p.getName()));
        }
        writer.write(String.format(Locale.getDefault(),"        );\n"));
        writer.write(String.format(Locale.getDefault(),"        return entity;\n"));
        writer.write(String.format(Locale.getDefault(),"    }\n\n"));

        /////////////////////
        writer.write(String.format(Locale.getDefault(),"    public void readEntity(Cursor cursor, %s entity, int offset) {\n",beanSimpleName));
        for(int i=0,len=daoProperties.size();i<len;i++) {
            sBuffer.setLength(0);
            sBuffer.append("        ");
            ColumnProperty p = daoProperties.get(i);
            if(p.getType().equals(Boolean.class)){
                sBuffer.append("entity.set%s(cursor.isNull(offset + %d) ? null : cursor.get%s(offset + %d) != 0)");
            }else {
                sBuffer.append("entity.set%s(cursor.isNull(offset + %d) ? null : cursor.get%s(offset + %d))");
            }
            sBuffer.append(";").append("\n");
            writer.write(String.format(Locale.getDefault(),sBuffer.toString(), //
                    p.getName().substring(0,1).toUpperCase()+p.getName().substring(1),p.getOrdinal(),p.getCursorGetType(),p.getOrdinal()));
        }
        writer.write(String.format(Locale.getDefault(),"    }\n\n"));
    }



}
