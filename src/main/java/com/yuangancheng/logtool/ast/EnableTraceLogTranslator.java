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
import java.util.*;

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
        logParamsFuncName = "test";

        /*
          Important!!! Currently need to set the default pos value (negative one) of treeMaker to the pos value (non negative one) of first declaration of current jcClassDecl.
         */
        Iterator<JCTree> iterator = jcClassDecl.defs.iterator();
        while(iterator.hasNext()) {
            JCTree jcTree = iterator.next();
            this.treeMaker.pos = jcTree.pos;
            break;
        }

        //Check if enable the open-close switch
        if((Boolean)enableTraceLogMembersMap.get(ConstantsEnum.ENABLE_CLASS_LEVEL_SWITCH.getValue())) {
            if(enableTraceLogMembersMap.get(ConstantsEnum.SWITCH_KEY.getValue()).equals("")) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Error: " + jcClassDecl.getSimpleName() + "@EnableTraceLog: Please specify a switch key");
            }
            JCTree.JCVariableDecl classLevelSwitchKeyDecl = generateSwitchKey(enableTraceLogMembersMap, "int");
            classLevelSwitchKey = classLevelSwitchKeyDecl.getName().toString();
            jcClassDecl.defs = jcClassDecl.defs.prepend(classLevelSwitchKeyDecl);
            JCTree.JCMethodDecl logParamsFuncDecl = generateLogMethodParamsFunc();
            jcClassDecl.defs = jcClassDecl.defs.append(logParamsFuncDecl);
            logParamsFuncName = logParamsFuncDecl.getName().toString();
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

    private JCTree.JCMethodDecl generateLogMethodParamsFunc() {
        JCTree.JCStatement switchIfStatement = astUtil.createIfStatement(
                astUtil.createBinaryExpression(astUtil.createIdent("methodLevelSwitchKey"), JCTree.Tag.EQ, astUtil.createLiteral(1)),
                generateLogPart(),
                null
        );
        if((Boolean)enableTraceLogMembersMap.get(ConstantsEnum.ENABLE_CLASS_LEVEL_SWITCH.getValue())) {
            switchIfStatement = astUtil.createIfStatement(
                    astUtil.createBinaryExpression(astUtil.createIdent(classLevelSwitchKey), JCTree.Tag.EQ, astUtil.createLiteral(1)),
                    switchIfStatement,
                    null
            );
        }
        return astUtil.createMethodDecl(
                Flags.PRIVATE,
                List.nil(),
                "void",
                null,
                "printMethodParams" + UUID.randomUUID().toString().replace("-", ""),
                List.nil(),
                new HashMap<String, String>() {
                    {
                        put("methodLevelSwitchKey", "int");
                        put("methodName", "String");
                        put("names", "String[]");
                        put("objects", "Object[]");
                    }
                },
                null,
                new ArrayList<>(),
                astUtil.createStatementBlock(List.nil(), List.of(switchIfStatement), List.nil())
        );
    }

    private JCTree.JCStatement generateLogPart() {
        String prefix = "{in: {";
        String suffix = "}}";
        String comma = ",";
        String colon = ": ";
        String logger = (String)enableTraceLogMembersMap.get(ConstantsEnum.LOGGER_NAME.getValue());
        JCTree.JCStatement stringBuilderAssignStatement = astUtil.createNewAssignStatement("stringBuilder",
                "StringBuilder", null, List.nil(), List.of(astUtil.createIdent("methodName")), null);
        JCTree.JCStatement stringBuilderAppendPrefixStatement = astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createLiteral(prefix));
                    }
                });
        JCTree.JCStatement forLoopSubStatement = astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtil.createArrayAccessIteratively("names[i]"));
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
                        add(astUtil.createMethodInvocation("objects[i].toString", new ArrayList<>()));
                    }
                });
        JCTree.JCStatement forLoopSubIfStatement = astUtil.createIfStatement(astUtil.createBinaryExpression(astUtil.createIdent("i"), JCTree.Tag.NE, astUtil.createBinaryExpression(astUtil.createFieldAccess("objects.length"), JCTree.Tag.MINUS, astUtil.createLiteral(1))),
                astUtil.createMethodInvocationExpressionStatement("stringBuilder.append",
                        new ArrayList<JCTree.JCExpression>() {
                            {
                                add(astUtil.createLiteral(comma));
                            }
                        }),
                null);
        JCTree.JCStatement forLoopStatement = astUtil.createForLoopStatement(
                List.of(astUtil.createVarDecl(0, List.nil(), "i", "int", astUtil.createLiteral(0))),
                astUtil.createBinaryExpression(astUtil.createIdent("i"), JCTree.Tag.LT, astUtil.createFieldAccess("objects.length")),
                List.of(astUtil.createUnaryStatement(JCTree.Tag.POSTINC, astUtil.createIdent("i"))),
                astUtil.createStatementBlock(List.nil(), List.of(forLoopSubStatement, forLoopSubStatement1, forLoopSubStatement2, forLoopSubIfStatement), List.nil())
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
                        add(astUtil.createMethodInvocation("stringBuilder.toString", new ArrayList<>()));
                    }
                }
        );
        return astUtil.createStatementBlock(
                List.nil(),
                List.of(stringBuilderAssignStatement, stringBuilderAppendPrefixStatement, forLoopStatement, postForLoopStatement, loggerInfoInvocationStatement),
                List.nil()
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
