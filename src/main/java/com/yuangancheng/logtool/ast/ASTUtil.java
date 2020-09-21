package com.yuangancheng.logtool.ast;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ASTUtil {

    private Names names;
    private Symtab symtab;
    private ClassReader classReader;
    private TreeMaker treeMaker;
    private static Map<String, Type> baseTypeMap = new HashMap<>();
    private static Map<String, TypeTag> baseTypeTagMap = new HashMap<>();

    public ASTUtil(Names names, Symtab symtab, ClassReader classReader, TreeMaker treeMaker) {
        this.names = names;
        this.symtab = symtab;
        this.classReader = classReader;
        this.treeMaker = treeMaker;
        if(baseTypeMap.size() == 0) {
            baseTypeMap.put("java.lang.String", symtab.stringType);
            baseTypeMap.put("byte", symtab.byteType);
            baseTypeMap.put("char", symtab.charType);
            baseTypeMap.put("short", symtab.shortType);
            baseTypeMap.put("long", symtab.longType);
            baseTypeMap.put("float", symtab.floatType);
            baseTypeMap.put("int", symtab.intType);
            baseTypeMap.put("double", symtab.doubleType);
            baseTypeMap.put("boolean", symtab.booleanType);

            baseTypeTagMap.put("byte", TypeTag.BYTE);
            baseTypeTagMap.put("char", TypeTag.CHAR);
            baseTypeTagMap.put("short", TypeTag.SHORT);
            baseTypeTagMap.put("long", TypeTag.LONG);
            baseTypeTagMap.put("float", TypeTag.FLOAT);
            baseTypeTagMap.put("int", TypeTag.INT);
            baseTypeTagMap.put("double", TypeTag.DOUBLE);
            baseTypeTagMap.put("boolean", TypeTag.BOOLEAN);
        }
    }

    private static class FlagsFieldType {
        public static Map<String, Long> map = new HashMap<>();

        static {
            map.put("Type$Method", (long)(Flags.ACYCLIC | Flags.PUBLIC));
        }
    }

    /**
     * Create an annotation
     *
     * @param qualifiedName the annotation's qualified name (e.g. "com...annotation.TestAnnotation")
     * @param keyValueMap the annotation's map of method name to its value
     * @param keyTypeQualifiedNameMap the annotation's map of method name to its qualified name
     * @return an instance of JCTree.JCAnnotation
     */
    public JCTree.JCAnnotation createAnnotation(String qualifiedName,
                                                Map<String, Object> keyValueMap,
                                                Map<String, String> keyTypeQualifiedNameMap) {
        Attribute propertiesAttribute = createCompound(qualifiedName, keyValueMap, keyTypeQualifiedNameMap);
        return treeMaker.Annotation(propertiesAttribute);
    }

    /**
     * Declare a variable
     *
     * @param flags the control flags of method
     * @param annotations the affiliated annotations of method
     * @param name the name of variable
     * @param varType the type of variable
     * @param initValExpression the initial value of variable
     * @return an instance of JCTree.JCVariableDecl
     */
    public JCTree.JCVariableDecl createVarDecl(long flags,
                                               List<JCTree.JCAnnotation> annotations,
                                               String name,
                                               String varType,
                                               JCTree.JCExpression initValExpression) {
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(flags, annotations);
        Name varName = names.fromString(name);
        JCTree.JCExpression varTypeExpression = baseTypeTagMap.containsKey(varType) ? treeMaker.TypeIdent(baseTypeTagMap.get(varType)) : treeMaker.Ident(names.fromString(varType));
        return treeMaker.VarDef(modifiers, varName, varTypeExpression, initValExpression);
    }

    /**
     * Declare a method
     *
     * @param flags the control flags of method
     * @param annotations the affiliated annotations of method
     * @param resType the result type of method
     * @param defaultValue the default value of method when this method is declared in an annotation
     * @param name the name of method
     * @param typeParams
     * @param paramMap the map of parameter's name to parameter's type
     * @param recvParam
     * @param exceptionThrownNameArrayList the list of thrown exceptions by method
     * @param methodBody the code block of method
     * @return an instance of JCTree.JCMethodDecl
     */
    public JCTree.JCMethodDecl createMethodDecl(long flags,
                                                List<JCTree.JCAnnotation> annotations,
                                                String resType,
                                                String defaultValue,
                                                String name,
                                                List<JCTree.JCTypeParameter> typeParams,
                                                Map<String, String> paramMap,
                                                JCTree.JCVariableDecl recvParam,
                                                ArrayList<String> exceptionThrownNameArrayList,
                                                JCTree.JCBlock methodBody) {
        JCTree.JCModifiers modifiers = treeMaker.Modifiers(flags, annotations);
        Name methodName = names.fromString(name);
        JCTree.JCExpression resTypeExpression = baseTypeTagMap.containsKey(resType) ? treeMaker.TypeIdent(baseTypeTagMap.get(resType)) : treeMaker.Ident(names.fromString(resType));
        JCTree.JCExpression defaultValueExpression = null;
        if(defaultValue != null && !defaultValue.equals("")) {
            defaultValueExpression = treeMaker.Ident(names.fromString(defaultValue));
        }
        List<JCTree.JCVariableDecl> params = List.nil();
        for(Map.Entry<String, String> entry : paramMap.entrySet()) {
            JCTree.JCVariableDecl jcVariableDecl = createVarDecl(0, List.nil(), entry.getKey(), entry.getValue(), null);
            params = params.append(jcVariableDecl);
        }
        List<JCTree.JCExpression> exceptionThrown = List.nil();
        for(String string : exceptionThrownNameArrayList) {
            exceptionThrown = exceptionThrown.append(treeMaker.Ident(names.fromString(string)));
        }
        return treeMaker.MethodDef(modifiers,
                methodName,
                resTypeExpression,
                typeParams,
                recvParam,
                params,
                exceptionThrown,
                methodBody,
                defaultValueExpression);
    }

    /**
     * Create a "if" statement
     *
     * @param condition the condition of "if" statement
     * @param thenPart the "then"'s statements part of "if" statement
     * @param elsePart the "else"'s statements part of "if" statement
     * @return an instance of JCTree.JCIF
     */
    public JCTree.JCStatement createIfStatement(JCTree.JCExpression condition,
                                                JCTree.JCStatement thenPart,
                                                JCTree.JCStatement elsePart) {
        return treeMaker.If(condition, thenPart, elsePart);
    }

    public JCTree.JCStatement createForLoopStatement(List<JCTree.JCStatement> init,
                                                   JCTree.JCExpression condition,
                                                   List<JCTree.JCExpressionStatement> step,
                                                   JCTree.JCStatement body) {
        return treeMaker.ForLoop(init, condition, step, body);
    }

    public JCTree.JCExpressionStatement createBinaryStatement(JCTree.Tag opTag, JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return treeMaker.Exec(createBinaryExpression(opTag, lhs, rhs));
    }

    public JCTree.JCExpressionStatement createUnaryStatement(JCTree.Tag opTag, JCTree.JCExpression jcExpression) {
        return treeMaker.Exec(createUnaryExpression(opTag, jcExpression));
    }

    public JCTree.JCExpression createUnaryExpression(JCTree.Tag opTag, JCTree.JCExpression jcExpression) {
        return treeMaker.Unary(opTag, jcExpression);
    }

    public JCTree.JCExpressionStatement createAssignStatement(JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return treeMaker.Exec(treeMaker.Assign(lhs, rhs));
    }

    public JCTree.JCExpression createBinaryExpression(JCTree.Tag opTag, JCTree.JCExpression lhs, JCTree.JCExpression rhs) {
        return treeMaker.Binary(opTag, lhs, rhs);
    }

    public JCTree.JCStatement createNewAssignStatement(String assignName,
                                                       String newClassName,
                                                       JCTree.JCExpression encl,
                                                       List<JCTree.JCExpression> typeArgs,
                                                       List<JCTree.JCExpression> args,
                                                       JCTree.JCClassDecl jcClassDecl) {
        return createVarDecl(0, null, assignName, newClassName,
                treeMaker.Assign(treeMaker.Ident(names.fromString(assignName)),
                        treeMaker.NewClass(encl, typeArgs, treeMaker.Ident(names.fromString(newClassName)), args, jcClassDecl)));
    }

    public JCTree.JCStatement createStatementBlock(List<JCTree.JCStatement> preStatements,
                                                 List<JCTree.JCStatement> curStatements,
                                                 List<JCTree.JCStatement> nextStatements) {
        return treeMaker.Block(0, preStatements.appendList(curStatements).appendList(nextStatements));
    }

    public JCTree.JCStatement createMethodInvocationExpressionStatement(String completeFieldName, ArrayList<JCTree.JCExpression> keyValueList) {
        return treeMaker.Exec(createMethodInvocation(completeFieldName, keyValueList));
    }

    /**
     * Create a method invocation
     *
     * @param completeFieldName complete field name (e.g. its representation like "System.out.println")
     * @param keyValueList the arraylist of arguments
     * @return an instance of JCTree.JCMethodInvocation
     */
    public JCTree.JCExpression createMethodInvocation(String completeFieldName, ArrayList<JCTree.JCExpression> keyValueList) {
        if(completeFieldName == null || completeFieldName.equals("")) {
            return null;
        }
        List<JCTree.JCExpression> args = List.nil();
        for(JCTree.JCExpression jcExpression : keyValueList) {
            args = args.append(jcExpression);
        }
        JCTree.JCExpression completeFieldAccess = createFieldAccess(completeFieldName);
        return treeMaker.Apply(List.nil(), completeFieldAccess, args);
    }

    public JCTree.JCExpression createIdent(String name) {
        return treeMaker.Ident(names.fromString(name));
    }

    public JCTree.JCExpression createLiteral(Object value) {
        return treeMaker.Literal(value);
    }

    /**
     * Create a complete field access
     *
     * @param completeFieldName complete name of field (e.g. "System.out.println")
     * @return an instance of JCTree.JCFieldAccess
     */
    private JCTree.JCExpression createFieldAccess(String completeFieldName) {
        String[] splitNameArray = completeFieldName.split("\\.");
        JCTree.JCExpression result = treeMaker.Ident(names.fromString(splitNameArray[0]));
        for(int i = 1; i < splitNameArray.length; i++) {
            result = treeMaker.Select(result, names.fromString(splitNameArray[i]));
        }
        return result;
    }

    /**
     * Create an instance of Attribute.Compound
     *
     * @param qualifiedName Canonical name of package or class, e.g. com....util.NameUtil
     * @param keyValueMap the map of property name to property value
     * @param keyQualifiedNameMap the map of property name to property type qualified name
     * @return an instance of Attribute.Compound
     */
    private Attribute createCompound(String qualifiedName, Map<String, Object> keyValueMap, Map<String, String> keyQualifiedNameMap) {
        List<Pair<Symbol.MethodSymbol, Attribute>> values = List.nil();
        Type classType = getClassType(qualifiedName);
        for(Map.Entry<String, Object> entry : keyValueMap.entrySet()) {
            Symbol.MethodSymbol first = createMethodSymbol(keyQualifiedNameMap.get(entry.getKey()),
                    entry.getKey(),
                    List.nil(),
                    List.nil(),
                    Flags.ABSTRACT | Flags.PUBLIC,
                    classType.tsym);
            Attribute second = createConstant(getClassType(keyQualifiedNameMap.get(entry.getKey())),
                    keyValueMap.get(entry.getKey()));
            Pair<Symbol.MethodSymbol, Attribute> pair = new Pair<>(first, second);
            values = values.append(pair);
        }
        return new Attribute.Compound(classType, values);
    }

    public Attribute.Constant createConstant(Type type, Object value) {
        return new Attribute.Constant(type, value);
    }

    private Symbol.MethodSymbol createMethodSymbol(String resQualifiedName, String name, List<Type> argTypes, List<Type> thrown, long flags, Symbol ownerSymbol) {
        Type methodType = createMethodType(resQualifiedName, argTypes, thrown);
        return new Symbol.MethodSymbol(flags, names.fromString(name), methodType, ownerSymbol);

    }

    private Type.MethodType createMethodType(String resQualifiedName, List<Type> argTypes, List<Type> thrown) {
        Type resType = getClassType(resQualifiedName);
        Type temp = createClassType0(FlagsFieldType.map.get("Type$Method"), "Method", symtab.noSymbol);
        return new Type.MethodType(argTypes, resType, thrown, temp.tsym);
    }

    public Type getClassType(String qualifiedName) {
        if(baseTypeMap.containsKey(qualifiedName)) {
            return baseTypeMap.get(qualifiedName);
        }else{
            Symbol.ClassSymbol classSymbol = classReader.enterClass(names.fromString(qualifiedName));
            return createClassType1(classSymbol);
        }
    }

    private Symbol.TypeSymbol createClassSymbol(long flags, String className, Type classType, Symbol ownerSymbol) {
        return new Symbol.ClassSymbol(flags, names.fromString(className), classType, ownerSymbol);
    }

    private Type createClassType0(long flags, String className, Symbol ownerSymbol) {
        Type.ClassType classType = new Type.ClassType(Type.noType, List.nil(), null);
        classType.tsym = createClassSymbol(flags, className, classType, ownerSymbol);
        return  classType;
    }

    public Type createClassType1(Symbol.TypeSymbol typeSymbol) {
        return new Type.ClassType(Type.noType, List.nil(), typeSymbol);
    }
}
