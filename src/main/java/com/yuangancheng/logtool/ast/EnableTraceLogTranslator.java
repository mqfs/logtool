package com.yuangancheng.logtool.ast;

import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import com.yuangancheng.logtool.enums.ConstantsEnum;

import javax.annotation.processing.Messager;
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
    private Set<String> enableMethodLevelSwitchSet;
    private LineMap lineMap;
    private String curReqIdName;

    public EnableTraceLogTranslator(Messager messager, TreeMaker treeMaker, Names names, Symtab symtab, ClassReader classReader, Map<String, Object> enableTraceLogMembersMap, ArrayList<String> methodListWithAnnotation, LineMap lineMap) {
        this.messager = messager;
        this.treeMaker = treeMaker;
        this.enableTraceLogMembersMap = enableTraceLogMembersMap;
        this.methodListWithAnnotation = methodListWithAnnotation;
        this.lineMap = lineMap;
        this.classDecl = null;
        endPosition = new ArrayList<>();
        astUtils = new ASTUtils(names, symtab, classReader, treeMaker);
        methodLevelSwitchKeyMap = new HashMap<>();
        enableMethodLevelSwitchSet = new HashSet<>();
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
        if(classDecl != null) {
            endPosition.add(getClassEndPosition(jcClassDecl));
            super.visitClassDef(jcClassDecl);
            return;
        }

        classDecl = jcClassDecl;

        /*
          Important!!! Currently need to set the default pos value (negative one) of treeMaker to any valid pos value (non negative one) in current jcClassDecl.
         */
        this.treeMaker.pos = jcClassDecl.pos;

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
            List<JCTree.JCExpression> annotationArgList = traceLogAnnotation.getArguments();
            for(JCTree.JCExpression arg : annotationArgList) {
                JCTree.JCAssign assign  = (JCTree.JCAssign)arg;
                if(((JCTree.JCIdent)assign.getVariable()).getName().toString().equals(ConstantsEnum.ENABLE_METHOD_LEVEL_SWITCH.getValue())) {
                    if(((JCTree.JCLiteral)assign.getExpression()).getValue().equals(true)) {
                        enableMethodLevelSwitchSet.add(methodDecl.getName().toString());
                        break;
                    }
                }
            }

            /* generate method-level-switch-keys for all method annotated with @TraceLog */
            String methodSwitchKey = null;
            JCTree.JCVariableDecl methodSwitchVariableDecl;
            if(enableMethodLevelSwitchSet.contains(methodDecl.getName().toString())) {
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
        if(!isMethodWithinFirstClass(jcMethodDecl) || !methodListWithAnnotation.contains(jcMethodDecl.getName().toString()))  {
            super.visitMethodDef(jcMethodDecl);
            return;
        }

        /* insert log method parameters part */
        if(jcMethodDecl.getParameters().size() > 0) {
            insertLogMethodParamsPart(jcMethodDecl);
        }

        /* insert method invocation to log-method-result-func */
        if(!(jcMethodDecl.getReturnType().type instanceof Type.JCVoidType)) {
            insertLogMethodResultFuncInvocationStatement(jcMethodDecl);
        }

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
                astUtils.createLiteral(1)
        );
    }

    /**
     * insert a log part for method's parameters into its body
     *
     * @param methodDecl
     * @return
     */
    private void insertLogMethodParamsPart(JCTree.JCMethodDecl methodDecl) {
        String prefix = methodDecl.getName().toString() + "{in: {";
        String colonSpace = ": ";
        String comma = ", ";
        String suffix = "}}";
        String braces = "{}";
        String logger = (String)enableTraceLogMembersMap.get(ConstantsEnum.LOGGER_NAME.getValue());
        StringBuilder preparedBraces = new StringBuilder();
        JCTree.JCStatement headerStringAssignStatement = null;
        JCTree.JCVariableDecl headerStringDecl = astUtils.createVarDecl(
                0,
                List.nil(),
                "reqId" + UUID.randomUUID().toString().replace("-", ""),
                "String",
                astUtils.createLiteral("")
        );
        curReqIdName = headerStringDecl.getName().toString();
        if(!enableTraceLogMembersMap.get(ConstantsEnum.REQ_ID_NAME.getValue()).equals("")) {
            headerStringAssignStatement = astUtils.createAssignStatement(
                    astUtils.createIdent(headerStringDecl.getName().toString()),
                    astUtils.createMethodInvocation1(
                            astUtils.createMethodInvocation1(
                                    astUtils.createParensExpression(
                                            astUtils.createTypeCastExpression(
                                                    astUtils.getClassType("org.springframework.web.context.request.ServletRequestAttributes"),
                                                    astUtils.createMethodInvocation1(
                                                            astUtils.createCompleteFieldAccess("org.springframework.web.context.request.RequestContextHolder"),
                                                            "getRequestAttributes",
                                                            new ArrayList<>())
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
        }
        preparedBraces.append(prefix);
        methodDecl.getParameters().forEach(jcVariableDecl -> {
            preparedBraces.append(jcVariableDecl.getName().toString())
                    .append(colonSpace)
                    .append(braces)
                    .append(comma);
        });
        preparedBraces.delete(preparedBraces.length() - 2, preparedBraces.length());
        preparedBraces.append(suffix);
        JCTree.JCStatement logMethodParamsStatement = astUtils.createMethodInvocationExpressionStatement(
                logger + ".info",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(!enableTraceLogMembersMap.get(ConstantsEnum.REQ_ID_NAME.getValue()).equals("") ?
                                astUtils.createBinaryExpression(astUtils.createIdent(curReqIdName), JCTree.Tag.PLUS, astUtils.createBinaryExpression(astUtils.createLiteral(":"), JCTree.Tag.PLUS, astUtils.createLiteral(preparedBraces.toString()))) :
                                astUtils.createLiteral(preparedBraces.toString())
                        );
                        addAll(
                                methodDecl.getParameters().stream()
                                        .map(jcVariableDecl -> {
                                            if(jcVariableDecl.getType() instanceof JCTree.JCPrimitiveTypeTree) {
                                                return astUtils.createIdent(jcVariableDecl.getName().toString());
                                            }else{
                                                return astUtils.createMethodInvocation0(
                                                        "String.valueOf",
                                                        new ArrayList<JCTree.JCExpression>() {
                                                            {
                                                                add(astUtils.createIdent(jcVariableDecl.getName().toString()));
                                                            }
                                                        }
                                                );
                                            }
                                        })
                                        .collect(Collectors.toList())
                        );
                    }
                }
        );
        JCTree.JCStatement switchIfStatement = astUtils.createIfStatement(
                enableMethodLevelSwitchSet.contains(methodDecl.getName().toString()) ?
                        astUtils.createBinaryExpression(astUtils.createIdent(methodLevelSwitchKeyMap.get(methodDecl.getName().toString())), JCTree.Tag.EQ, astUtils.createLiteral(1)) :
                        astUtils.createLiteral(true),
                astUtils.createBlock(headerStringAssignStatement == null ? List.nil() : List.of(headerStringAssignStatement), List.of(logMethodParamsStatement)),
                null
        );
        if((Boolean)enableTraceLogMembersMap.get(ConstantsEnum.ENABLE_CLASS_LEVEL_SWITCH.getValue())) {
            switchIfStatement = astUtils.createIfStatement(
                    astUtils.createBinaryExpression(astUtils.createIdent(classLevelSwitchKey), JCTree.Tag.EQ, astUtils.createLiteral(1)),
                    switchIfStatement,
                    null
            );
        }
        methodDecl.body = astUtils.createBlock(List.of(headerStringDecl, switchIfStatement), methodDecl.body.getStatements());
    }

    private void insertLogMethodResultFuncInvocationStatement(JCTree.JCMethodDecl methodDecl) {
        methodDecl.body = processJCBlock(methodDecl.getBody(), methodDecl);
    }

    private JCTree.JCBlock processJCBlock(JCTree.JCBlock oldBlock, JCTree.JCMethodDecl methodDecl) {
        List<JCTree.JCStatement> newList = List.nil();
        for(JCTree.JCStatement statement : oldBlock.getStatements()) {
            if(statement instanceof JCTree.JCReturn) {
                JCTree.JCBlock returnBlock = processJCReturn((JCTree.JCReturn)statement, methodDecl);
                newList = newList.appendList(returnBlock.getStatements());
            }else{
                newList = newList.append(processJCStatement(statement, methodDecl));
            }
        }
        return astUtils.createBlock(newList);
    }

    private JCTree.JCWhileLoop processJCWhileLoop(JCTree.JCWhileLoop whileLoop, JCTree.JCMethodDecl methodDecl) {
        JCTree.JCStatement newStatement;
        if(whileLoop.getStatement() instanceof JCTree.JCReturn) {
            JCTree.JCBlock returnBlock = processJCReturn((JCTree.JCReturn)whileLoop.getStatement(), methodDecl);
            if(returnBlock.getStatements().size() == 1) {
                newStatement = returnBlock.getStatements().get(0);
            }else{
                newStatement = astUtils.createBlock(returnBlock.getStatements());
            }
        }else{
            newStatement = processJCStatement(whileLoop.getStatement(), methodDecl);
        }
        return astUtils.createWhileLoopStatement(whileLoop.getCondition(), newStatement);
    }

    private JCTree.JCDoWhileLoop processJCDoWhileLoop(JCTree.JCDoWhileLoop doWhileLoop, JCTree.JCMethodDecl methodDecl) {
        JCTree.JCStatement newStatement;
        if(doWhileLoop.getStatement() instanceof JCTree.JCReturn) {
            JCTree.JCBlock returnBlock = processJCReturn((JCTree.JCReturn)doWhileLoop.getStatement(), methodDecl);
            if(returnBlock.getStatements().size() == 1) {
                newStatement = returnBlock.getStatements().get(0);
            }else{
                newStatement = astUtils.createBlock(returnBlock.getStatements());
            }
        }else{
            newStatement = processJCStatement(doWhileLoop.getStatement(), methodDecl);
        }
        return astUtils.createDoWhileLoopStatement(doWhileLoop.getCondition(), newStatement);
    }

    private JCTree.JCForLoop processJCForLoop(JCTree.JCForLoop forLoop, JCTree.JCMethodDecl methodDecl) {
        JCTree.JCStatement newStatement;
        if(forLoop.getStatement() instanceof JCTree.JCReturn) {
            JCTree.JCBlock returnBlock = processJCReturn((JCTree.JCReturn)forLoop.getStatement(), methodDecl);
            if(returnBlock.getStatements().size() == 1) {
                newStatement = returnBlock.getStatements().get(0);
            }else{
                newStatement = astUtils.createBlock(returnBlock.getStatements());
            }
        }else{
            newStatement = processJCStatement(forLoop.getStatement(), methodDecl);
        }
        return astUtils.createForLoopStatement(forLoop.getInitializer(), forLoop.getCondition(), forLoop.getUpdate(), newStatement);
    }

    private JCTree.JCSwitch processJCSwitch(JCTree.JCSwitch jcSwitch, JCTree.JCMethodDecl methodDecl) {
        List<JCTree.JCCase> newCases = List.nil();
        for(JCTree.JCCase jcCase : jcSwitch.getCases()) {
            List<JCTree.JCStatement> newCaseStatements = List.nil();
            for(JCTree.JCStatement statement : jcCase.getStatements()) {
                if(statement instanceof JCTree.JCReturn) {
                    JCTree.JCBlock returnBlock = processJCReturn((JCTree.JCReturn)statement, methodDecl);
                    newCaseStatements = newCaseStatements.appendList(returnBlock.getStatements());
                }else{
                    newCaseStatements = newCaseStatements.append(processJCStatement(statement, methodDecl));
                }
            }
            newCases = newCases.append(astUtils.createCaseStatement(jcCase.getExpression(), newCaseStatements));
        }
        return astUtils.createSwitchStatement(jcSwitch.getExpression(), newCases);
    }

    private JCTree.JCIf processJCIf(JCTree.JCIf jcIf, JCTree.JCMethodDecl methodDecl) {
        JCTree.JCStatement newThenStatement;
        JCTree.JCStatement newElseStatement;
        if(jcIf.getThenStatement() instanceof JCTree.JCReturn) {
            JCTree.JCBlock returnBlock = processJCReturn((JCTree.JCReturn)jcIf.getThenStatement(), methodDecl);
            if(returnBlock.getStatements().size() == 1) {
                newThenStatement = returnBlock.getStatements().get(0);
            }else{
                newThenStatement = astUtils.createBlock(returnBlock.getStatements());
            }
        }else{
            newThenStatement = processJCStatement(jcIf.getThenStatement(), methodDecl);
        }
        if(jcIf.getElseStatement() instanceof JCTree.JCReturn) {
            JCTree.JCBlock returnBlock = processJCReturn((JCTree.JCReturn)jcIf.getElseStatement(), methodDecl);
            if(returnBlock.getStatements().size() == 1) {
                newElseStatement = returnBlock.getStatements().get(0);
            }else{
                newElseStatement = astUtils.createBlock(returnBlock.getStatements());
            }
        }else{
            newElseStatement = processJCStatement(jcIf.getElseStatement(), methodDecl);
        }
        return astUtils.createIfStatement(jcIf.getCondition(), newThenStatement, newElseStatement);
    }

    private JCTree.JCTry processJCTry(JCTree.JCTry jcTry, JCTree.JCMethodDecl methodDecl) {
        JCTree.JCBlock newTryBody = processJCBlock(jcTry.getBlock(), methodDecl);
        List<JCTree.JCCatch> newCatches = List.nil();
        for(JCTree.JCCatch jcCatch : jcTry.getCatches()) {
            JCTree.JCBlock newCatchBody = processJCBlock(jcCatch.getBlock(), methodDecl);
            newCatches = newCatches.append(astUtils.createCatch(jcCatch.getParameter(), newCatchBody));
        }
        JCTree.JCBlock newFinalizerBody = processJCBlock(jcTry.getFinallyBlock(), methodDecl);
        return astUtils.createTryStatement(newTryBody, newCatches, newFinalizerBody);
    }

    private JCTree.JCSynchronized processJCSynchronized(JCTree.JCSynchronized jcSynchronized, JCTree.JCMethodDecl methodDecl) {
        JCTree.JCBlock newBody = processJCBlock(jcSynchronized.getBlock(), methodDecl);
        return astUtils.createSynchronizedStatement(jcSynchronized.getExpression(), newBody);
    }

    private JCTree.JCReturn getNewJCReturn(JCTree.JCVariableDecl methodResultVarDecl) {
        return astUtils.createReturnStatement(astUtils.createIdent(methodResultVarDecl.getName().toString()));
    }

    private JCTree.JCVariableDecl generateMethodResultVariable(JCTree.JCExpression resultExpr, JCTree.JCMethodDecl methodDecl) {
        JCTree jcTree = methodDecl.getReturnType();
        return astUtils.createVarDecl(
                0,
                List.nil(),
                "varResult" + UUID.randomUUID().toString().replace("-", ""),
                jcTree.toString(),
                resultExpr
        );
    }

    private JCTree.JCStatement processJCStatement(JCTree.JCStatement statement, JCTree.JCMethodDecl methodDecl) {
        if(statement instanceof JCTree.JCWhileLoop) {
            return processJCWhileLoop((JCTree.JCWhileLoop)statement, methodDecl);
        }else if(statement instanceof JCTree.JCDoWhileLoop) {
            return processJCDoWhileLoop((JCTree.JCDoWhileLoop)statement, methodDecl);
        }else if(statement instanceof JCTree.JCForLoop) {
            return processJCForLoop((JCTree.JCForLoop)statement, methodDecl);
        }else if(statement instanceof JCTree.JCSwitch) {
            return processJCSwitch((JCTree.JCSwitch)statement, methodDecl);
        }else if(statement instanceof JCTree.JCIf) {
            return processJCIf((JCTree.JCIf)statement, methodDecl);
        }else if(statement instanceof JCTree.JCTry) {
            return processJCTry((JCTree.JCTry)statement, methodDecl);
        }else if(statement instanceof JCTree.JCSynchronized) {
            return processJCSynchronized((JCTree.JCSynchronized)statement, methodDecl);
        }else if(statement instanceof JCTree.JCBlock) {
            return processJCBlock((JCTree.JCBlock)statement, methodDecl);
        }
        return statement;
    }

    private JCTree.JCBlock processJCReturn(JCTree.JCReturn jcReturn, JCTree.JCMethodDecl methodDecl) {
        JCTree.JCExpression returnedExpr = TreeInfo.skipParens(jcReturn.getExpression());
        String pattern = methodDecl.getName().toString() + "{out: {result: {}}}";
        String logger = (String)enableTraceLogMembersMap.get(ConstantsEnum.LOGGER_NAME.getValue());
        JCTree.JCStatement newReturn = null;
        JCTree.JCVariableDecl methodResultVarDecl = null;
        JCTree.JCStatement logReturnLineNumberStatement = null;
        if(!(returnedExpr instanceof JCTree.JCIdent) && !(returnedExpr instanceof JCTree.JCLiteral) && !(returnedExpr instanceof JCTree.JCArrayAccess)) {
            logReturnLineNumberStatement = astUtils.createMethodInvocationExpressionStatement(
                    logger + ".info",
                    new ArrayList<JCTree.JCExpression>() {
                        {
                            add(
                                    astUtils.createLiteral(
                                            "debug" + ":" + methodDecl.getName().toString() + "{exceptions or errors may occur at return-statement's line number: " + lineMap.getLineNumber(jcReturn.getStartPosition()) + "}"
                                    )
                            );
                        }
                    }
            );
            methodResultVarDecl = generateMethodResultVariable(returnedExpr, methodDecl);
            newReturn = getNewJCReturn(methodResultVarDecl);
        }
        JCTree.JCVariableDecl finalMethodResultVarDecl = methodResultVarDecl;
        JCTree.JCStatement logMethodResultStatement = astUtils.createMethodInvocationExpressionStatement(
                logger + ".info",
                new ArrayList<JCTree.JCExpression>() {
                    {
                        add(!enableTraceLogMembersMap.get(ConstantsEnum.REQ_ID_NAME.getValue()).equals("") ?
                                astUtils.createBinaryExpression(astUtils.createIdent(curReqIdName), JCTree.Tag.PLUS, astUtils.createBinaryExpression(astUtils.createLiteral(":"), JCTree.Tag.PLUS, astUtils.createLiteral(pattern))) :
                                astUtils.createLiteral(pattern)
                        );
                        add(
                                methodDecl.getReturnType() instanceof JCTree.JCPrimitiveTypeTree ?
                                        finalMethodResultVarDecl == null ? returnedExpr : astUtils.createIdent(finalMethodResultVarDecl.getName().toString()) :
                                        astUtils.createMethodInvocation0(
                                                "String.valueOf",
                                                new ArrayList<JCTree.JCExpression>() {
                                                    {
                                                        add(finalMethodResultVarDecl == null ? returnedExpr : astUtils.createIdent(finalMethodResultVarDecl.getName().toString()));
                                                    }
                                                }
                                        )
                        );
                    }
                }
        );
        JCTree.JCStatement switchIfStatement = astUtils.createIfStatement(
                enableMethodLevelSwitchSet.contains(methodDecl.getName().toString()) ?
                        astUtils.createBinaryExpression(astUtils.createIdent(methodLevelSwitchKeyMap.get(methodDecl.getName().toString())), JCTree.Tag.EQ, astUtils.createLiteral(1)) :
                        astUtils.createLiteral(true),
                logMethodResultStatement,
                null
        );
        if((Boolean)enableTraceLogMembersMap.get(ConstantsEnum.ENABLE_CLASS_LEVEL_SWITCH.getValue())) {
            switchIfStatement = astUtils.createIfStatement(
                    astUtils.createBinaryExpression(astUtils.createIdent(classLevelSwitchKey), JCTree.Tag.EQ, astUtils.createLiteral(1)),
                    switchIfStatement,
                    null
            );
        }
        return astUtils.createBlock(
                methodResultVarDecl == null ? List.nil() : List.of(logReturnLineNumberStatement, methodResultVarDecl),
                List.of(switchIfStatement),
                newReturn == null ? List.of(jcReturn) : List.of(newReturn)
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
     * Get class "end position" in AST recursively (Currently the "end position" means the start position of its last member)
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
