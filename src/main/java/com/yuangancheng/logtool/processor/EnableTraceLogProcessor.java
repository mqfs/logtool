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
public class EnableTraceLogProcessor extends AbstractProcessor {

    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;
    private Symtab symtab;
    private ClassReader classReader;
    private int dummy = 0;

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
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<String>() {
            {
                add("com.yuangancheng.logtool.annotation.EnableTraceLog");
            }
        };
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.values()[SourceVersion.values().length - 1];
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        /* Check if the processing environment has been initialized */
        if(!isInitialized()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "The logtool's annotation processor has not been initialized. Please try it again.");
        }

        /* Important!!! Currently count the loops for detecting unknown error to avoid infinite loop which occurs sometimes in Windows 7 (IntelliJ IDEA 'build project' option) */
        if(dummy == 0) {
            String count = System.getProperty("com.yuangancheng.logtool.dummy");
            if(count == null) {
                System.setProperty("com.yuangancheng.logtool.dummy", "1");
            }else{
                if(Integer.parseInt(count) > 2) {
                    messager.printMessage(Diagnostic.Kind.WARNING, "Encountered an infinite annotation processing loop. Please use 'Rebuild Project' option instead of 'Build Project' in IntelliJ IDEA to enable @TraceLog.");
                    return true;
                }
                System.setProperty("com.yuangancheng.logtool.dummy", String.valueOf(Integer.parseInt(count) + 1));
            }
            dummy++;
        }

        Set<? extends Element> elementSet = roundEnv.getElementsAnnotatedWith(EnableTraceLog.class);
        for(Element element : elementSet) {
            EnableTraceLog enableTraceLog = element.getAnnotation(EnableTraceLog.class);
            messager.printMessage(Diagnostic.Kind.NOTE, "TraceLog: modifying class: " + element.toString());
            JCTree classTree = trees.getTree(element);
            List<? extends Element> memberList = element.getEnclosedElements();
            List<Object> list = processClassMembers(enableTraceLog, memberList);
            if(((ArrayList<String>)list.get(1)).size() > 0) {
                EnableTraceLogTranslator classTranslator = new EnableTraceLogTranslator(
                        messager,
                        treeMaker,
                        names,
                        symtab,
                        classReader,
                        (Map<String, Object>)list.get(0),
                        (ArrayList<String>)list.get(1),
                        trees.getPath(element).getCompilationUnit().getLineMap()
                );
                classTree.accept(classTranslator);
            }
        }
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
