package com.yuangancheng.logtool.ast;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Pair;

import java.util.HashMap;
import java.util.Map;

public class ASTUtil {

    private Names names;
    private Symtab symtab;
    private ClassReader classReader;
    private TreeMaker treeMaker;
    private static Map<String, Type> baseTypeMap = new HashMap<>();

    public ASTUtil(Names names, Symtab symtab, ClassReader classReader, TreeMaker treeMaker) {
        this.names = names;
        this.symtab = symtab;
        this.classReader = classReader;
        this.treeMaker = treeMaker;
        if(baseTypeMap.size() == 0) {
            baseTypeMap.put("java.lang.String", symtab.stringType);
            baseTypeMap.put("int", symtab.intType);
            baseTypeMap.put("char", symtab.charType);
            baseTypeMap.put("boolean", symtab.booleanType);
            baseTypeMap.put("long", symtab.longType);
        }
    }

    private static class FlagsFieldType {
        public static Map<String, Long> map = new HashMap<>();

        static {
            map.put("Type$Method", (long)(Flags.ACYCLIC | Flags.PUBLIC));
        }
    }

    public JCTree.JCAnnotation createAnnotation(String qualifiedName, Map<String, Object> keyValueMap, Map<String, String> keyQualifiedNameMap) {
        Attribute propertiesAttribute = createCompound(qualifiedName, keyValueMap, keyQualifiedNameMap);
        return treeMaker.Annotation(propertiesAttribute);
    }

    public JCTree.JCVariableDecl createVarDecl(JCTree.JCModifiers modifiers, String varName, JCTree.JCExpression type, JCTree.JCExpression initVal) {
        return treeMaker.VarDef(modifiers, names.fromString(varName), type, initVal);
    }

//    public JCTree.JCMethodDecl createMethodDecl() {
//        return treeMaker.MethodDef()
//    }

    /**
     * Create an instance of Attribute.Compound
     *
     * @param qualifiedName Canonical name of package or class, e.g. com....util.NameUtil
     * @return
     */
    public Attribute createCompound(String qualifiedName, Map<String, Object> keyValueMap, Map<String, String> keyQualifiedNameMap) {
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
