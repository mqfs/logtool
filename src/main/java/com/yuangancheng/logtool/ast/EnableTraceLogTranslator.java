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
import javax.lang.model.element.Name;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: Gancheng Yuan
 * @date: 2020/9/17 10:50
 */
public class EnableTraceLogTranslator extends TreeTranslator {

    private final Messager messager;
    private final TreeMaker treeMaker;
    private final Map<String, Object> enableTraceLogMembersMap;
    private final ArrayList<String> methodListWithAnnotation;
    private JCTree.JCClassDecl classDecl;
    private final ArrayList<Integer> endPosition;
    private final ASTUtils astUtils;
    private String classLevelSwitchKey;
    private Map<String, String> methodLevelSwitchKeyMap;
    private String logParamsFuncName = "";
    private String logResFuncName = "";

    public EnableTraceLogTranslator(Messager messager, TreeMaker treeMaker, Names names, Symtab symtab, ClassReader classReader, Map<String, Object> enableTraceLogMembersMap, ArrayList<String> methodListWithAnnotation) {
        this.messager = messager;
        this.treeMaker = treeMaker;
        this.enableTraceLogMembersMap = enableTraceLogMembersMap;
        this.methodListWithAnnotation = methodListWithAnnotation;
        this.classDecl = null;
        endPosition = new ArrayList<>();
        astUtils = new ASTUtils(names, symtab, classReader, treeMaker);
        methodLevelSwitchKeyMap = new HashMap<>();
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
        if(classDecl != null) {
            endPosition.add(getClassEndPosition(jcClassDecl));
            super.visitClassDef(jcClassDecl);
            return;
        }

        this.classDecl = jcClassDecl;

        /*
          Important!!! Currently need to set the default pos value (negative one) of treeMaker to the pos value (non negative one) of first declaration of current jcClassDecl.
         */
        for(JCTree jcTree : jcClassDecl.defs) {
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

        ArrayList<JCTree.JCMethodDecl> methodDecls = new ArrayList<JCTree.JCMethodDecl>(){
            {
                addAll(jcClassDecl.getMembers().stream()
                        .filter(jcTree -> jcTree instanceof JCTree.JCMethodDecl)
                        .filter(jcTree -> {
                            JCTree.JCMethodDecl methodDecl = (JCTree.JCMethodDecl)jcTree;
                            for(JCTree.JCAnnotation jcAnnotation : methodDecl.getModifiers().getAnnotations()) {
                                if(((JCTree.JCIdent)jcAnnotation.getAnnotationType()).getName().toString().equals("TraceLog")) {
                                    return true;
                                }
                            }
                            return false;
                        })
                        .map(jcTree -> (JCTree.JCMethodDecl)jcTree).collect(Collectors.toList())
                );
            }
        };
        for(JCTree.JCMethodDecl methodDecl : methodDecls) {
            List<JCTree.JCAnnotation> annotationList = methodDecl.getModifiers().getAnnotations();
            JCTree.JCAnnotation traceLogAnnotation = null;
            for(JCTree.JCAnnotation jcAnnotation : annotationList) {
                traceLogAnnotation = jcAnnotation;
                if(((JCTree.JCIdent)traceLogAnnotation.getAnnotationType()).getName().toString().equals("TraceLog")) {
                    break;
                }
            }
            boolean enableMethodLevelSwitch = false;
            List<JCTree.JCExpression> annotationArgList = traceLogAnnotation.getArguments();
            for(JCTree.JCExpression arg : annotationArgList) {
                JCTree.JCAssign assign  = (JCTree.JCAssign)arg;
                if(((JCTree.JCIdent)assign.getVariable()).getName().toString().equals(ConstantsEnum.ENABLE_METHOD_LEVEL_SWITCH.getValue())) {
                    if(((JCTree.JCLiteral)assign.getExpression()).getValue().equals(true)) {
                        enableMethodLevelSwitch = true;
                        break;
                    }
                }
            }

            /* generate method-level-switch-keys for all method annotated with @TraceLog */
            String methodSwitchKey = null;
            JCTree.JCVariableDecl methodSwitchVariableDecl = null;
            if(enableMethodLevelSwitch) {
                for(JCTree.JCExpression arg : annotationArgList) {
                    JCTree.JCAssign assign  = (JCTree.JCAssign)arg;
                    if(((JCTree.JCIdent)assign.getVariable()).getName().toString().equals("switchKey") && !((JCTree.JCLiteral)assign.getExpression()).getValue().equals("")) {
                        methodSwitchKey = (String)((JCTree.JCLiteral)assign.getExpression()).getValue();
                        break;
                    }
                }
                methodSwitchVariableDecl = generateSwitchKey(
                        methodSwitchKey,
                        "int",
                        ConstantsEnum.VAR_METHOD_SWITCH_KEY
                );
                classDecl.defs = classDecl.defs.prepend(methodSwitchVariableDecl);
                methodLevelSwitchKeyMap.put(methodDecl.getName().toString(), methodSwitchVariableDecl.getName().toString());
            }
        }
        
        super.visitClassDef(jcClassDecl);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl jcMethodDecl) {
        if(!isMethodWithinFirstClass(jcMethodDecl) || !methodListWithAnnotation.contains(jcMethodDecl.getName().toString()) || logParamsFuncName.equals(""))  {
            super.visitMethodDef(jcMethodDecl);
            return;
        }

        List<JCTree.JCAnnotation> annotationList = jcMethodDecl.getModifiers().getAnnotations();
        JCTree.JCAnnotation traceLogAnnotation = null;
        for (JCTree.JCAnnotation jcAnnotation : annotationList) {
            traceLogAnnotation = jcAnnotation;
            if (traceLogAnnotation.getAnnotationType().equals(astUtils.createIdent("TraceLog"))) {
                break;
            }
        }
        boolean enableMethodLevelSwitch = false;
        List<JCTree.JCExpression> annotationArgList = traceLogAnnotation.getArguments();
        for(JCTree.JCExpression arg : annotationArgList) {
            JCTree.JCAssign assign  = (JCTree.JCAssign)arg;
            if(((JCTree.JCIdent)assign.getVariable()).getName().toString().equals(ConstantsEnum.ENABLE_METHOD_LEVEL_SWITCH.getValue())) {
                if(((JCTree.JCLiteral)assign.getExpression()).getValue().equals(true)) {
                    enableMethodLevelSwitch = true;
                    break;
                }
            }
        }

        /* generate method invocation to log-method-params-func */
        JCTree.JCStatement methodInvocationStatement = generateMethodInvocationStatement2LogParamsFunc(
                enableMethodLevelSwitch,
                methodLevelSwitchKeyMap.get(jcMethodDecl.getName().toString()),
                jcMethodDecl
        );
        jcMethodDecl.body = astUtils.createStatementBlock(
                List.of(methodInvocationStatement),
                jcMethodDecl.getBody().getStatements()
        );

        super.visitMethodDef(jcMethodDecl);
    }

    /**
     * Declare a class/method level log switch variable
     *
     * @param switchKeyCompleteQualifiedName the complete qualified name of switch key property in application configuration file
     * @param keyType the type of log switch key
     * @return an instance of JCTree.JCVariableDecl
     */
    private JCTree.JCVariableDecl generateSwitchKey(String switchKeyCompleteQualifiedName, String keyType, ConstantsEnum level) {
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
        return astUtils.createVarDecl(
                Flags.PRIVATE,
                List.of(valueAnnotation),
                level.getValue() + UUID.randomUUID().toString().replace("-", ""),
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
                Flags.PRIVATE | Flags.FINAL,
                List.nil(),
                "void",
                null,
                "printMethodParams" + UUID.randomUUID().toString().replace("-", ""),
                List.nil(),
                new LinkedHashMap<String, String>() {
                    {
                        put("methodLevelSwitchKey", "int");
                        put("methodName", "String");
                        put("names", "String[]");
                        put("objects", "Object[]");
                    }
                },
                null,
                new ArrayList<>(),
                astUtils.createStatementBlock(List.of(switchIfStatement))
        );
    }

    private JCTree.JCStatement generateLogParamsPart() {
        String prefix = "{in: {";
        String suffix = "}}";
        String comma = ",";
        String colon = ": ";
        String logger = (String)enableTraceLogMembersMap.get(ConstantsEnum.LOGGER_NAME.getValue());
        JCTree.JCStatement stringBuilderAssignStatement = astUtils.createNewAssignStatement(
                "stringBuilder",
                "StringBuilder",
                null,
                List.nil(),
                List.nil(),
                null
        );
        List<JCTree.JCStatement> headerRelatedStatements = List.nil();
        if(!enableTraceLogMembersMap.get(ConstantsEnum.REQ_ID_NAME.getValue()).equals("")) {
            JCTree.JCStatement headerStringAssignStatement = astUtils.createVarDecl(
                    0,
                    List.nil(),
                    "reqId",
                    "String",
                    astUtils.createMethodInvocation1(
                            astUtils.createMethodInvocation1(
                                    astUtils.createParensExpression(
                                            astUtils.createTypeCastExpression(
                                                    astUtils.getClassType("org.springframework.web.context.request.ServletRequestAttributes"),
                                                    astUtils.createMethodInvocation0("RequestContextHolder.getRequestAttributes", new ArrayList<>())
                                            )
                                    ),
                                    "getRequest",
                                    new ArrayList<>()
                            ),
                            "getHeader",
                            new ArrayList<JCTree.JCExpression>() {
                                {
                                    add(astUtils.createLiteral(enableTraceLogMembersMap.get(ConstantsEnum.REQ_ID_NAME.getValue())));
                                }
                            }
                    )
            );
            JCTree.JCStatement stringBuilderAppendHeaderStatement = astUtils.createMethodInvocationExpressionStatement(
                    "stringBuilder.append",
                    new ArrayList<JCTree.JCExpression>() {
                        {
                            add(astUtils.createBinaryExpression(astUtils.createIdent("reqId"), JCTree.Tag.PLUS, astUtils.createLiteral(":")));
                        }
                    }
            );
            headerRelatedStatements = headerRelatedStatements.append(headerStringAssignStatement).append(stringBuilderAppendHeaderStatement);
        }
        JCTree.JCStatement stringBuilderAppendMethodNameStatement = astUtils.createMethodInvocationExpressionStatement(
                "stringBuilder.append",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(astUtils.createIdent("methodName"));
                    }
                }
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
        JCTree.JCStatement forLoopSubIfStatement = astUtils.createIfStatement(astUtils.createBinaryExpression(astUtils.createIdent("i"), JCTree.Tag.NE, astUtils.createBinaryExpression(astUtils.createCompleteFieldAccess("objects.length"), JCTree.Tag.MINUS, astUtils.createLiteral(1))),
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
                astUtils.createBinaryExpression(astUtils.createIdent("i"), JCTree.Tag.LT, astUtils.createCompleteFieldAccess("objects.length")),
                List.of(astUtils.createUnaryStatement(JCTree.Tag.POSTINC, astUtils.createIdent("i"))),
                astUtils.createStatementBlock(List.of(forLoopSubStatement, forLoopSubStatement1, forLoopSubStatement2, forLoopSubIfStatement))
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
                List.of(stringBuilderAssignStatement),
                headerRelatedStatements,
                List.of(stringBuilderAppendMethodNameStatement, stringBuilderAppendPrefixStatement, forLoopStatement, postForLoopStatement, loggerInfoInvocationStatement)
        );
    }


    /**
     * Generate a method invocation statement to log-params-func in each annotated methods with @TraceLog
     *
     * @param enableMethodLevelSwitch
     * @param methodDecl
     * @return
     */
    private JCTree.JCStatement generateMethodInvocationStatement2LogParamsFunc(boolean enableMethodLevelSwitch, String methodLevelSwitchKey, JCTree.JCMethodDecl methodDecl) {
        /* generate method invocation of print-method-params method */
        return astUtils.createMethodInvocationExpressionStatement(
                "this." + logParamsFuncName,
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(enableMethodLevelSwitch ? astUtils.createIdent(methodLevelSwitchKey) : astUtils.createLiteral(1));
                        add(astUtils.createLiteral(methodDecl.getName().toString()));
                        add(
                                astUtils.createNewArrayExpression(
                                        "String",
                                        1,
                                        new ArrayList<Object>() {
                                            {
                                                addAll(methodDecl.getParameters().stream().map(jcVariableDecl -> JCTree.JCLiteral.class).collect(Collectors.toList()));
                                            }
                                        },
                                        new ArrayList<Object>() {
                                            {
                                                addAll(methodDecl.getParameters().stream().map(JCTree.JCVariableDecl::getName).map(Name::toString).collect(Collectors.toList()));
                                            }
                                        }
                                )
                        );
                        add(
                                astUtils.createNewArrayExpression(
                                        "Object",
                                        1,
                                        new ArrayList<Object>() {
                                            {
                                                addAll(methodDecl.getParameters().stream().map(jcVariableDecl -> JCTree.JCIdent.class).collect(Collectors.toList()));
                                            }
                                        },
                                        new ArrayList<Object>() {
                                            {
                                                addAll(methodDecl.getParameters().stream().map(JCTree.JCVariableDecl::getName).map(Name::toString).collect(Collectors.toList()));
                                            }
                                        }
                                )
                        );
                    }
                }
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
        if(classDecl == null || endPosition.size() == 0) {
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
