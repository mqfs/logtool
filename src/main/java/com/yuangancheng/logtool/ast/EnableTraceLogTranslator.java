package com.yuangancheng.logtool.ast;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import com.yuangancheng.logtool.enums.ConstantsEnum;

import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.util.ArrayList;
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
    private Map<String, Object> enableTraceLogMembersMap;
    private ArrayList<String> methodListWithAnnotation;
    private int classCount = 1;
    private ArrayList<Integer> endPosition;

    public EnableTraceLogTranslator(Messager messager, JavacTrees trees, TreeMaker treeMaker, Names names, Map<String, Object> enableTraceLogMembersMap, ArrayList<String> methodListWithAnnotation) {
        this.messager = messager;
        this.trees = trees;
        this.treeMaker = treeMaker;
        this.names = names;
        this.enableTraceLogMembersMap = enableTraceLogMembersMap;
        this.methodListWithAnnotation = methodListWithAnnotation;
        endPosition = new ArrayList<>();
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
//        Symbol.TypeSymbol typeSymbol = new Symbol.TypeSymbol(Tree.Kind.CLASS, Flags.PUBLIC, names.fromString("Value"));
//        Type classType = new Type.ClassType(Type.noType, List.nil(), );
//        treeMaker.Annotation();
        if((Boolean)enableTraceLogMembersMap.get(ConstantsEnum.ENABLE_OPEN_CLOSE_SWITCH.getValue())) {
            JCTree.JCVariableDecl openCloseKeyVar = treeMaker.VarDef(treeMaker.Modifiers(Flags.PRIVATE),
                    names.fromString(ConstantsEnum.VAR_OPEN_CLOSE_KEY.getValue()),
                    treeMaker.Ident(names.fromString("String")),
                    null);
            jcClassDecl.defs = jcClassDecl.defs.prepend(openCloseKeyVar);
        }
        super.visitClassDef(jcClassDecl);
    }

    @Override
    public void visitAnnotation(JCTree.JCAnnotation jcAnnotation) {
//        messager.printMessage(Diagnostic.Kind.NOTE, "annotation: " + UUID.randomUUID().toString() +
//                "-" + jcAnnotation.toString() +
//                "-" + ((Symbol.MethodSymbol)jcAnnotation.attribute.values.get(0).fst).owner.toString() +
//                "-" + jcAnnotation.attribute.values.get(0).snd +
//                "-" + jcAnnotation.attribute.type.tsym.name +
//                "-" + jcAnnotation.attribute.type.tsym.type +
//                "-" + jcAnnotation.attribute.type.tsym.owner);
        super.visitAnnotation(jcAnnotation);
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
        //messager.printMessage(Diagnostic.Kind.NOTE, "return type: " + UUID.randomUUID().toString() + "-" + returnType.sym.getClass().toString());
        super.visitMethodDef(jcMethodDecl);
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
