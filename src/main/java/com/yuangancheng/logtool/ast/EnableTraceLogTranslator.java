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
import com.yuangancheng.logtool.annotation.EnableTraceLog;
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
    private Map<String, String> methodLevelSwitchKeyMap;
    private String logParamsFuncName = "";
    private String logResFuncName = "";

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
        methodLevelSwitchKeyMap = new HashMap<>();
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
            if(enableTraceLogMembersMap.get(ConstantsEnum.SWITCH_KEY.getValue()).equals("")) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Error: " + jcClassDecl.getSimpleName() + "@EnableTraceLog: Please specify a switch key");
            }
            JCTree.JCVariableDecl classLevelSwitchKeyDecl = generateSwitchKey(enableTraceLogMembersMap, "int");
            classLevelSwitchKey = classLevelSwitchKeyDecl.getName().toString();
            jcClassDecl.defs = jcClassDecl.defs.prepend(classLevelSwitchKeyDecl);
            logParamsFuncName = "test";
        }
        super.visitClassDef(jcClassDecl);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
        if(!isMethodWithinFirstClass(jcMethodDecl) || !methodListWithAnnotation.contains(jcMethodDecl.getName().toString()) || logParamsFuncName.equals(""))  {
            super.visitMethodDef(jcMethodDecl);
            return;
        }
        messager.printMessage(Diagnostic.Kind.NOTE, "method: " + UUID.randomUUID().toString() +
                "-" + jcMethodDecl.getName().toString() +
                "-" + jcMethodDecl.sym.owner.toString());
        super.visitMethodDef(jcMethodDecl);
    }

    /**
     * Declare a class/method level log switch variable
     *
     * @param membersMap the map of annotation's properties name to value
     * @param keyType the type of log switch key
     * @return an instance of JCTree.JCVariableDecl
     */
    private JCTree.JCVariableDecl generateSwitchKey(Map<String, Object> membersMap, String keyType) {
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
        return astUtil.createVarDecl(Flags.PRIVATE,
                List.of(valueAnnotation),
                ConstantsEnum.VAR_CLASS_SWITCH_KEY.getValue() + UUID.randomUUID().toString().replace("-", ""),
                keyType,
                null);
    }

//    private JCTree.JCMethodDecl generateLogMethodParamsFunc() {
//
//    }

    private JCTree.JCStatement generateLogPart() {
        String prefix = "{args: {";
        String suffix = "}}";
        String comma = ",";
        String colon = ": ";
        String logger = (String)enableTraceLogMembersMap.get(ConstantsEnum.LOGGER_NAME.getValue());
        JCTree.JCStatement stringBuilderAssignStatement = astUtil.createNewAssignStatement("stringBuilder",
                "StringBuilder", null, null, List.of(astUtil.createIdent("methodName")), null);
        JCTree.JCStatement stringBuilderAppendPrefixStatement = astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createLiteral(prefix));
                    }
                });
        JCTree.JCStatement forLoopSubStatement = astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createLiteral("names[i]"));
                    }
                });
        JCTree.JCStatement forLoopSubStatement1 = astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createLiteral(colon));
                    }
                });
        JCTree.JCStatement forLoopSubStatement2 = astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createMethodInvocation("objects[i].toString", null));
                    }
                });
        JCTree.JCStatement forLoopSubIfStatement = astUtil.createIfStatement(astUtil.createBinaryExpression(JCTree.Tag.NE, astUtil.createIdent("i"), astUtil.createLiteral(0)),
                astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                        new ArrayList<JCTree.JCExpression>() {
                            {
                                add(astUtil.createLiteral(comma));
                            }
                        }),
                null);
        JCTree.JCStatement forLoopStatement = astUtil.createForLoopStatement(
                List.of(astUtil.createVarDecl(0, null, "i", "int", astUtil.createLiteral(0))),
                astUtil.createBinaryExpression(JCTree.Tag.LE, astUtil.createIdent("i"), astUtil.createIdent("objects.length")),
                List.of(astUtil.createUnaryStatement(JCTree.Tag.POSTINC, astUtil.createIdent("i"))),
                astUtil.createStatementBlock(null, List.of(forLoopSubStatement, forLoopSubStatement1, forLoopSubStatement2, forLoopSubIfStatement), null)
        );
        JCTree.JCStatement postForLoopStatement = astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createLiteral(suffix));
                    }
                });
        JCTree.JCStatement loggerInfoInvocationStatement = astUtil.createMethodInvocationExpressionStatement(
                logger + ".info",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createMethodInvocation("stringBuilder.toString", null));
                    }
                }
        );
        return astUtil.createStatementBlock(
                null,
                List.of(stringBuilderAssignStatement, stringBuilderAppendPrefixStatement, forLoopStatement, postForLoopStatement),
                null
        );
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
