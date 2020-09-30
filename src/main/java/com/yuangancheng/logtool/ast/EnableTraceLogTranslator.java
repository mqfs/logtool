package com.yuangancheng.logtool.ast;

import com.sun.source.tree.Tree;
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

    private final Messager messager;
    private final TreeMaker treeMaker;
    private final Map<String, Object> enableTraceLogMembersMap;
    private final ArrayList<String> methodListWithAnnotation;
    private int classCount = 1;
    private final ArrayList<Integer> endPosition;
    private final ASTUtils astUtils;
    private String classLevelSwitchKey;
    private final Map<String, String> methodLevelSwitchKeyMap;
    private String logParamsFuncName = "";
    private String logResFuncName = "";

    public EnableTraceLogTranslator(Messager messager, TreeMaker treeMaker, Names names, Symtab symtab, ClassReader classReader, Map<String, Object> enableTraceLogMembersMap, ArrayList<String> methodListWithAnnotation) {
        this.messager = messager;
        this.treeMaker = treeMaker;
        this.enableTraceLogMembersMap = enableTraceLogMembersMap;
        this.methodListWithAnnotation = methodListWithAnnotation;
        endPosition = new ArrayList<>();
        astUtils = new ASTUtils(names, symtab, classReader, treeMaker);
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
            JCTree.JCVariableDecl classLevelSwitchKeyDecl = generateSwitchKey(
                    (String)enableTraceLogMembersMap.get(ConstantsEnum.SWITCH_KEY.getValue()),
                    "int",
                    ConstantsEnum.VAR_CLASS_SWITCH_KEY
            );
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
     * @param switchKeyCompleteQualifiedName the complete qualified name of switch key property in application configuration
     * @param keyType the type of log switch key
     * @return an instance of JCTree.JCVariableDecl
     */
    private JCTree.JCVariableDecl generateSwitchKey(String switchKeyCompleteQualifiedName, String keyType, ConstantsEnum type) {
        JCTree.JCAnnotation valueAnnotation = astUtils.createAnnotation("org.springframework.beans.factory.annotation.Value",
                new HashMap<String, Object>() {
                    {
                        put("value", "${" + switchKeyCompleteQualifiedName + "}");
                    }
                },
                new HashMap<String, String>() {
                    {
                        put("value", "java.lang.String");
                    }
                }
        );
        return astUtils.createVarDecl(Flags.PRIVATE,
                List.of(valueAnnotation),
                type.getValue() + UUID.randomUUID().toString().replace("-", ""),
                keyType,
                null
        );
    }

    private JCTree.JCMethodDecl generateLogMethodParamsFunc() {
        JCTree.JCStatement switchIfStatement = astUtils.createIfStatement(
                astUtils.createBinaryExpression(astUtils.createIdent("methodLevelSwitchKey"), JCTree.Tag.EQ, astUtils.createLiteral(1)),
                generateLogParamsPart(),
                null
        );
        if((Boolean)enableTraceLogMembersMap.get(ConstantsEnum.ENABLE_CLASS_LEVEL_SWITCH.getValue())) {
            switchIfStatement = astUtils.createIfStatement(
                    astUtils.createBinaryExpression(astUtils.createIdent(classLevelSwitchKey), JCTree.Tag.EQ, astUtils.createLiteral(1)),
                    switchIfStatement,
                    null
            );
        }
        return astUtils.createMethodDecl(
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
                astUtils.createStatementBlock(List.nil(), List.of(switchIfStatement), List.nil())
        );
    }

    private JCTree.JCStatement generateLogParamsPart() {
        String prefix = "{in: {";
        String suffix = "}}";
        String comma = ",";
        String colon = ": ";
        String logger = (String)enableTraceLogMembersMap.get(ConstantsEnum.LOGGER_NAME.getValue());
        if(!enableTraceLogMembersMap.get(ConstantsEnum.REQ_ID_NAME.getValue()).equals("")) {
            astUtils.createTypeCastExpression(
                    astUtils.getClassType("org.springframework.web.context.request.ServletRequestAttributes"),
                    astUtils.createMethodInvocation0("RequestContextHolder.getRequestAttributes", new ArrayList<>())
                    );
        }
        JCTree.JCStatement stringBuilderAssignStatement = astUtils.createNewAssignStatement(
                "stringBuilder",
                "StringBuilder",
                null,
                List.nil(), List.of(astUtils.createIdent("methodName")),
                null
        );
        JCTree.JCStatement stringBuilderAppendPrefixStatement = astUtils.createMethodInvocationExpressionStatement(
                "stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtils.createLiteral(prefix));
                    }
                }
        );
        JCTree.JCStatement forLoopSubStatement = astUtils.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtils.createArrayAccessIteratively("names[i]"));
                    }
                }
        );
        JCTree.JCStatement forLoopSubStatement1 = astUtils.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtils.createLiteral(colon));
                    }
                }
        );
        JCTree.JCStatement forLoopSubStatement2 = astUtils.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtils.createMethodInvocation0("objects[i].toString", new ArrayList<>()));
                    }
                }
        );
        JCTree.JCStatement forLoopSubIfStatement = astUtils.createIfStatement(astUtils.createBinaryExpression(astUtils.createIdent("i"), JCTree.Tag.NE, astUtils.createBinaryExpression(astUtils.createFieldAccess("objects.length"), JCTree.Tag.MINUS, astUtils.createLiteral(1))),
                astUtils.createMethodInvocationExpressionStatement(
                        "stringBuilder.append",
                        new ArrayList<JCTree.JCExpression>() {
                            {
                                add(astUtils.createLiteral(comma));
                            }
                        }
                ),
                null
        );
        JCTree.JCStatement forLoopStatement = astUtils.createForLoopStatement(
                List.of(astUtils.createVarDecl(0, List.nil(), "i", "int", astUtils.createLiteral(0))),
                astUtils.createBinaryExpression(astUtils.createIdent("i"), JCTree.Tag.LT, astUtils.createFieldAccess("objects.length")),
                List.of(astUtils.createUnaryStatement(JCTree.Tag.POSTINC, astUtils.createIdent("i"))),
                astUtils.createStatementBlock(List.nil(), List.of(forLoopSubStatement, forLoopSubStatement1, forLoopSubStatement2, forLoopSubIfStatement), List.nil())
        );
        JCTree.JCStatement postForLoopStatement = astUtils.createMethodInvocationExpressionStatement("stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtils.createLiteral(suffix));
                    }
                }
        );
        JCTree.JCStatement loggerInfoInvocationStatement = astUtils.createMethodInvocationExpressionStatement(
                logger + ".info",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtils.createMethodInvocation0("stringBuilder.toString", new ArrayList<>()));
                    }
                }
        );
        return astUtils.createStatementBlock(
                List.nil(),
                List.of(stringBuilderAssignStatement, stringBuilderAppendPrefixStatement, forLoopStatement, postForLoopStatement, loggerInfoInvocationStatement),
                List.nil()
        );
    }

    /**
     * Generate an method invocation to log-params-func in each annotated methods with @TraceLog
     *
     * @param methodName
     * @param paramsName
     * @param paramsType
     */
//    private JCTree.JCStatement generateMethodInvocation2LogParamsFunc(String methodName, String[] paramsName, String[] paramsType) {
//
//    }

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
