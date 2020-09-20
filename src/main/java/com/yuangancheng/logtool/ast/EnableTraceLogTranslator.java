package com.yuangancheng.logtool.ast;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import com.yuangancheng.logtool.enums.ConstantsEnum;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author: Gancheng Yuan
 * @date: 2020/9/17 10:50
 */
public class EnableTraceLogTranslator extends TreeTranslator {

    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;
    private Symtab symtab;
    private ClassReader classReader;
    private Map<String, Object> enableTraceLogMembersMap;
    private ArrayList<String> methodListWithAnnotation;
    private int classCount = 1;
    private ArrayList<Integer> endPosition;
    private ASTUtil astUtil;
    private String classLevelSwitchKey;
    private ArrayList<String> methodLevelSwitchKey;

    public EnableTraceLogTranslator(Messager messager, JavacTrees trees, TreeMaker treeMaker, Names names, Symtab symtab, ClassReader classReader, Map<String, Object> enableTraceLogMembersMap, ArrayList<String> methodListWithAnnotation) {
        this.messager = messager;
        this.trees = trees;
        this.treeMaker = treeMaker;
        this.names = names;
        this.symtab = symtab;
        this.classReader = classReader;
        this.enableTraceLogMembersMap = enableTraceLogMembersMap;
        this.methodListWithAnnotation = methodListWithAnnotation;
        endPosition = new ArrayList<>();
        astUtil = new ASTUtil(names, symtab, classReader, treeMaker);
        methodLevelSwitchKey = new ArrayList<>();
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
        if(classCount == 0) {
            endPosition.add(getClassEndPosition(jcClassDecl));
            super.visitClassDef(jcClassDecl);
            return;
        }
        messager.printMessage(Diagnostic.Kind.NOTE, "class: " + UUID.randomUUID().toString() + "-" + jcClassDecl.getSimpleName().toString());
        classCount--;

        //Check if enable the open-close switch
        if((Boolean)enableTraceLogMembersMap.get(ConstantsEnum.ENABLE_CLASS_LEVEL_SWITCH.getValue())) {
            if(!enableTraceLogMembersMap.get(ConstantsEnum.SWITCH_KEY.getValue()).equals("")) {
                JCTree.JCVariableDecl classLevelSwitchKeyDecl = generateSwitchKey(enableTraceLogMembersMap, treeMaker.TypeIdent(TypeTag.INT));
                classLevelSwitchKey = classLevelSwitchKeyDecl.getName().toString();
                jcClassDecl.defs = jcClassDecl.defs.prepend(classLevelSwitchKeyDecl);
            }
        }
        super.visitClassDef(jcClassDecl);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
        if(!isMethodWithinFirstClass(jcMethodDecl) || !methodListWithAnnotation.contains(jcMethodDecl.getName().toString())) {
            super.visitMethodDef(jcMethodDecl);
            return;
        }
        messager.printMessage(Diagnostic.Kind.NOTE, "method: " + UUID.randomUUID().toString() +
                "-" + jcMethodDecl.getName().toString() +
                "-" + jcMethodDecl.sym.owner.toString());
        super.visitMethodDef(jcMethodDecl);
    }

    private JCTree.JCVariableDecl generateSwitchKey(Map<String, Object> membersMap, JCTree.JCExpression type) {
        JCTree.JCAnnotation valueAnnotation = astUtil.createAnnotation("org.springframework.beans.factory.annotation.Value",
                new HashMap<String, Object>() {
                    {
                        put("value", "${" + membersMap.get(ConstantsEnum.SWITCH_KEY.getValue()) + "}");
                    }
                },
                new HashMap<String, String>() {
                    {
                        put("value", "java.lang.String");
                    }
                });
        return astUtil.createVarDecl(treeMaker.Modifiers(Flags.PRIVATE, List.of(valueAnnotation)),
                ConstantsEnum.VAR_CLASS_SWITCH_KEY.getValue() + UUID.randomUUID().toString().replace("-", ""),
                type,
                null);
    }

    /**
     * Check if the method is in the outmost class
     *
     * @param jcMethodDecl
     * @return
     */
    private boolean isMethodWithinFirstClass(JCTree.JCMethodDecl jcMethodDecl) {
        int methodStartPosition = jcMethodDecl.getEndPosition(null);
        if(classCount == 1 || endPosition.size() == 0) {
            return true;
        }

        //Check if the start position of method isn't in the range of class position
        if(methodStartPosition > endPosition.get(endPosition.size() - 1)) {
            endPosition.remove(endPosition.size() - 1);
            return true;
        }else{
            return false;
        }
    }

    /**
     * Get class end position in AST recursively
     *
     * @param jcClassDecl
     * @return
     */
    private int getClassEndPosition(JCTree.JCClassDecl jcClassDecl) {
        List<JCTree> jcTreeList = jcClassDecl.getMembers();
        int listLength = jcTreeList.size();

        //Check if the last member is a class declaration and then call itself recursively
        if(jcTreeList.get(listLength - 1).getKind() == Tree.Kind.CLASS) {
            return getClassEndPosition((JCTree.JCClassDecl)jcTreeList.get(listLength - 1));
        }

        return jcTreeList.get(listLength - 1).getEndPosition(null);
    }
}
