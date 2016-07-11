package com.roobo.greendaoannotationprocessor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/**
 * Created by LuoZheng on 2016/7/7.
 */
@SupportedAnnotationTypes("com.roobo.greendaoannotationprocessor.DataBase")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
// 这个是用来配置生成的class全名（包名+类名）,数据库名，数据库版本
@SupportedOptions(value = {"appSQLiteOpenHelper","appDatabaseName","appDatabaseVersion"})
public class DataBaseAnnotationProcessor extends AbstractProcessor{
    private static final String OPTION_SOURCE_FILE_NAME = "appSQLiteOpenHelper";
    private static final String OPTION_APP_DATABASE_NAME = "appDatabaseName";
    private static final String OPTION_APP_DATABASE_VERSION = "appDatabaseVersion";
    // 用来打印日志
//    private Messager logMessger;
    // 包名 + 类名
    private String sourceFileName;
    private String databaseName;
    private int databaseVersion;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        logMessger = processingEnv.getMessager();

        sourceFileName = processingEnv.getOptions().get(OPTION_SOURCE_FILE_NAME);
        databaseName = processingEnv.getOptions().get(OPTION_APP_DATABASE_NAME);
        databaseVersion = Integer.parseInt(processingEnv.getOptions().get(OPTION_APP_DATABASE_VERSION));

//        logMessger.printMessage(Diagnostic.Kind.OTHER,"###fileName:" + sourceFileName);
//        logMessger.printMessage(Diagnostic.Kind.OTHER,"###dbName:" + databaseName);
//        logMessger.printMessage(Diagnostic.Kind.OTHER,"###dbVersion:" + databaseVersion);
        createJavaSourceFile(sourceFileName);

        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private void createJavaSourceFile(String sourceFile){
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
            writer.write("import android.database.sqlite.SQLiteOpenHelper;\n\n");
            writer.write(String.format(Locale.getDefault(),"public abstract class %s extends SQLiteOpenHelper {\n",clazz));
            writer.write(String.format(Locale.getDefault(),"    public %s(Context context){\n",clazz));
            writer.write(String.format(Locale.getDefault(),"        super(context, \"%s\", null, %d);\n",databaseName,databaseVersion));
            writer.write(String.format(Locale.getDefault(),"    }\n"));
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



}
