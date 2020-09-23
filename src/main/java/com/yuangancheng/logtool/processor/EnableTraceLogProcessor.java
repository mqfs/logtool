package com.yuangancheng.logtool.processor;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import com.yuangancheng.logtool.annotation.EnableTraceLog;
import com.yuangancheng.logtool.annotation.TraceLog;
import com.yuangancheng.logtool.ast.EnableTraceLogTranslator;
import com.yuangancheng.logtool.enums.ConstantsEnum;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * @author: Gancheng Yuan
 * @date: 2020/9/15 18:20
 */
@SupportedAnnotationTypes("com.yuangancheng.logtool.annotation.EnableTraceLog")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EnableTraceLogProcessor extends AbstractProcessor {

    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;
    private Symtab symtab;
    private ClassReader classReader;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment)processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.symtab = Symtab.instance(context);
        this.classReader = ClassReader.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> elementSet = roundEnv.getElementsAnnotatedWith(EnableTraceLog.class);
        elementSet.forEach(element -> {
            EnableTraceLog enableTraceLog = element.getAnnotation(EnableTraceLog.class);
            messager.printMessage(Diagnostic.Kind.NOTE, UUID.randomUUID().toString() + "-" + element.toString());
            JCTree classTree = trees.getTree(element);
            List<? extends Element> memberList = element.getEnclosedElements();
            List<Object> list = processClassMembers(enableTraceLog, memberList);
            EnableTraceLogTranslator classTranslator = new EnableTraceLogTranslator(
                    messager,
                    treeMaker,
                    names,
                    symtab,
                    classReader,
                    (Map<String, Object>)list.get(0),
                    (ArrayList<String>)list.get(1)
            );
            classTree.accept(classTranslator);
        });
        return true;
    }


    /**
     * Generate annotation's key-value pair map and methods' name with TraceLog annotation
     *
     * @param enableTraceLog
     * @param memberList
     * @return
     */
    private List<Object> processClassMembers(EnableTraceLog enableTraceLog, List<? extends Element> memberList) {
        List<Object> result = new ArrayList<>();
        Map<String, Object> enableTraceLogMembersMap = new HashMap<>();
        List<String> methodListWithAnnotation = new ArrayList<>();

        //Generate key-value pair map
        enableTraceLogMembersMap.put(ConstantsEnum.LOGGER_NAME.getValue(), enableTraceLog.loggerName());
        enableTraceLogMembersMap.put(ConstantsEnum.REQ_ID_NAME.getValue(), enableTraceLog.reqIdName());
        enableTraceLogMembersMap.put(ConstantsEnum.ENABLE_CLASS_LEVEL_SWITCH.getValue(), enableTraceLog.enableClassLevelSwitch());
        enableTraceLogMembersMap.put(ConstantsEnum.SWITCH_KEY.getValue(), enableTraceLog.switchKey());

        //Generate list of methods with TraceLog annotation
        memberList.forEach(member -> {
            TraceLog traceLog = member.getAnnotation(TraceLog.class);
            if(traceLog == null) {
                return;
            }
            methodListWithAnnotation.add(member.getSimpleName().toString());
        });

        result.add(enableTraceLogMembersMap);
        result.add(methodListWithAnnotation);
        return result;
    }
}
