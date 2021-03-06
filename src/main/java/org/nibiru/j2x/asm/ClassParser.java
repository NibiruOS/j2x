package org.nibiru.j2x.asm;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.nibiru.j2x.ast.J2xAccess;
import org.nibiru.j2x.ast.J2xArray;
import org.nibiru.j2x.ast.J2xBlock;
import org.nibiru.j2x.ast.J2xClass;
import org.nibiru.j2x.ast.J2xField;
import org.nibiru.j2x.ast.J2xMethod;
import org.nibiru.j2x.ast.element.J2xAssignment;
import org.nibiru.j2x.ast.element.J2xLiteral;
import org.nibiru.j2x.ast.element.J2xMethodCall;
import org.nibiru.j2x.ast.element.J2xNativeCode;
import org.nibiru.j2x.ast.element.J2xReturn;
import org.nibiru.j2x.ast.element.J2xVariable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public class ClassParser extends ClassVisitor {
    private static Map<String, J2xClass> systemClasses = Maps.newHashMap();

    static {
        try {
            for (Field field : J2xClass.class.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(J2xClass.class)) {
                    J2xClass value = (J2xClass) field.get(null);

                    systemClasses.put((value.getPackageName().isEmpty()
                            ? ""
                            : value.getPackageName().replaceAll("\\.", "/")
                            + "/") + value.getName(), value);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, J2xClass> parse(String classPath,
                                              ParsePolicy parsePolicy) {
        Map<String, J2xClass> generatedClasses = Maps.newHashMap();

        parseClassPath(classPath, generatedClasses, parsePolicy);

        return generatedClasses;
    }

    private static J2xClass parseClassPath(String classPath,
                                           Map<String, J2xClass> generatedClasses,
                                           ParsePolicy parsePolicy) {
        if (classPath == null) {
            return null;
        }
        J2xClass systemClass = systemClasses.get(classPath);
        if (systemClass != null) {
            return systemClass;
        }
        J2xClass generatedClass = generatedClasses.get(classPath);
        if (generatedClass != null) {
            return generatedClass;
        }
        int dimensions = extractDimensions(classPath);
        if (dimensions > 0) {
            String itemClassPath = extractName(classPath);
            return new J2xArray(parseClassPath(itemClassPath,
                    generatedClasses,
                    parsePolicy),
                    dimensions,
                    parseClassPath("java/lang/Object", generatedClasses, parsePolicy));
        } else {
            return new ClassParser(generatedClasses, parsePolicy)
                    .parseInternal(classPath);
        }
    }

    private static String extractName(String name) {
        int pos = name.indexOf(J2xArray.ARRAY);
        return pos >= 0
                ? name.substring(0, pos)
                : name;
    }

    private static int extractDimensions(String name) {
        int dimensions = 0;
        int pos = name.indexOf(J2xArray.ARRAY);
        while (pos >= 0) {
            dimensions++;
            pos = name.indexOf(J2xArray.ARRAY, pos + 1);
        }
        return dimensions;
    }

    private final Map<String, J2xClass> generatedClasses;
    private final ParsePolicy parsePolicy;
    private J2xClass j2xClass;

    private ClassParser(Map<String, J2xClass> generatedClasses,
                        ParsePolicy parsePolicy) {
        super(Opcodes.ASM5);
        this.generatedClasses = generatedClasses;
        this.parsePolicy = parsePolicy;
    }

    private J2xClass parseClassPath(String path) {
        return parseClassPath(path, generatedClasses, parsePolicy);
    }

    private J2xClass parseDesc(String desc) {
        return parseClassPath(descToPath(desc));
    }

    private J2xClass parseInternal(String classPath) {
        try {
            ClassReader reader = new ClassReader(Class.class
                    .getResourceAsStream(String.format("/%s.class", classPath)));
            reader.accept(this, 0);
            return generatedClasses.get(classPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void visit(int version,
                      int access,
                      String name,
                      String signature,
                      String superName,
                      String[] interfaces) {
        int pos = name.lastIndexOf("/");
        String packageName = name.substring(0, pos).replaceAll("/", ".");
        j2xClass = new J2xClass(name.substring(pos + 1),
                packageName,
                parseClassPath(superName),
                access(access));
        generatedClasses.put(name, j2xClass);
    }

    @Override
    public FieldVisitor visitField(int access,
                                   String name,
                                   String desc,
                                   String signature,
                                   Object value) {
        j2xClass.getFields().add(new J2xField(name,
                parseDesc(desc),
                access(access),
                isStatic(access),
                isFinal(access)));
        return null;
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {
        return new MethodParser(access,
                name,
                desc,
                signature,
                exceptions);
    }

    private static boolean isStatic(int access) {
        return (access & Opcodes.ACC_STATIC) != 0;
    }

    private static boolean isFinal(int access) {
        return (access & Opcodes.ACC_FINAL) != 0;
    }

    private static J2xAccess access(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            return J2xAccess.PUBLIC;
        } else if ((access & Opcodes.ACC_PROTECTED) != 0) {
            return J2xAccess.PROTECTED;
        } else if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return J2xAccess.PRIVATE;
        } else {
            return J2xAccess.DEFAULT;
        }
    }

    private String descToPath(String signature) {
        if (signature.startsWith("[")) {
            // TODO: esto capaz falla con mas de una dimension
            return descToPath(signature.substring(1)) + "[]";
        } else {
            switch (signature) {
                case "V":
                    return "void";
                case "Z":
                    return "boolean";
                case "C":
                    return "char";
                case "B":
                    return "byte";
                case "S":
                    return "short";
                case "I":
                    return "int";
                case "F":
                    return "float";
                case "J":
                    return "long";
                case "D":
                    return "double";
                default:
                    return signature.substring(1,
                            signature.length() - 1);
            }
        }
    }


    private class MethodParser extends MethodVisitor {
        private final int access;
        private final String name;
        private final String desc;
        private final String signature;
        private final String[] exceptions;

        private final List<J2xVariable> variables;
        private final List<J2xVariable> arguments;
        private final J2xBlock body;

        private int argCount;
        private final Stack stack;

        private MethodParser(int access,
                             String name,
                             String desc,
                             String signature,
                             String[] exceptions) {
            super(Opcodes.ASM5);
            if (j2xClass.getName().equals("Hola") && (access & Opcodes.ACC_NATIVE) != 0) {
                System.out.print("matanga");
            }
            this.access = access;
            this.name = name;
            this.desc = desc;
            this.signature = signature;
            this.exceptions = exceptions;

            variables = Lists.newLinkedList();
            arguments = Lists.newArrayList();
            body = new J2xBlock();

            stack = new Stack();
            argCount = argCount(desc);
        }

        @Override
        public void visitLocalVariable(String name,
                                       String desc,
                                       String signature,
                                       Label start,
                                       Label end,
                                       int index) {
            J2xVariable variable = variables.isEmpty()
                    ? new J2xVariable()
                    : variables.remove(0);
            variable.setName(name);
            variable.setType(parseDesc(desc));
            if (argCount > 0 && !variable.isThis()) {
                argCount--;
                arguments.add(variable);
            } else if (mustParseContent()) {
                body.getVariables().add(variable);
            }
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            super.visitFrame(type, nLocal, local, nStack, stack);
        }

        @Override
        public void visitInsn(int opcode) {
            if (mustParseContent()) {
                switch (opcode) {
                    case Opcodes.LNEG:
                        break;
                    case Opcodes.ICONST_0:
                    case Opcodes.ICONST_1:
                    case Opcodes.ICONST_2:
                    case Opcodes.ICONST_3:
                    case Opcodes.ICONST_4:
                    case Opcodes.ICONST_5:
                        stack.push(new J2xLiteral(opcode - Opcodes.ICONST_0));
                        break;
                    case Opcodes.IRETURN:
                        stack.push(new J2xReturn(stack.pop()));
                        break;
                    case Opcodes.RETURN:
                        stack.push(new J2xReturn());
                        break;
                }
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (mustParseContent()) {
                switch (opcode) {
                    case Opcodes.ALOAD:
                        stack.push(new J2xLiteral(operand));
                        break;
                    case Opcodes.BIPUSH:
                        stack.push(new J2xLiteral((byte) operand));
                        break;
                    case Opcodes.SIPUSH:
                        stack.push(new J2xLiteral((short) operand));
                        break;
                }
            }
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            J2xVariable variable = variable(var);
            if (mustParseContent()) {
                switch (opcode) {
                    case Opcodes.ALOAD:
                        stack.push(variable);
                        break;
                    case Opcodes.ISTORE:
                    case Opcodes.LSTORE:
                    case Opcodes.FSTORE:
                    case Opcodes.DSTORE:
                    case Opcodes.ASTORE:
                        // TODO: deberia usar el opcode para determinar el tipo de la variable? esa info la tengo despues en visitLocalVariable (no sé si eso no es info de debug - estara siempre disponible?)
                        stack.push(new J2xAssignment(variable, stack.pop()));
                        break;
                }
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (mustParseContent()) {
                super.visitTypeInsn(opcode, type);
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (mustParseContent()) {
                switch (opcode) {
                    case Opcodes.GETSTATIC:
                        break;
                    case Opcodes.INVOKESPECIAL:
                        break;
                }
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (mustParseContent()) {
                switch (opcode) {
                    case Opcodes.INVOKEVIRTUAL:
                    case Opcodes.INVOKESPECIAL:
                        List<Object> args = Lists.newArrayList();
                        for (Object dummy : new DescIterable(argTypes(desc))) {
                            args.add(stack.pop());
                        }
                        J2xVariable target = stack.pop();
                        stack.push(new J2xMethodCall(target,
                                parseClassPath(owner).findMethod(name, desc),
                                args));
                        break;
                }
            }
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            if (mustParseContent()) {
                super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
            }
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (mustParseContent()) {
                super.visitJumpInsn(opcode, label);
            }
        }

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            if (mustParseContent()) {
                stack.push(new J2xLiteral(cst));
            }
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            if (mustParseContent()) {
                super.visitIincInsn(var, increment);
            }
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return super.visitInsnAnnotation(typeRef, typePath, desc, visible);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (mustParseContent()) {
                if (descToPath(desc).equals("org/nibiru/j2x/ast/J2xNative")) {
                    return new AnnotationVisitor(Opcodes.ASM5) {
                        String language;
                        String code;

                        @Override
                        public void visit(String name, Object value) {
                            if (name.equals("language")) {
                                language = String.valueOf(value);
                            } else if (name.equals("value")) {
                                code = String.valueOf(value);
                            }
                        }

                        @Override
                        public void visitEnd() {
                            body.getElements()
                                    .add(new J2xNativeCode(language, code));
                        }
                    };
                }
            }
            return super.visitAnnotation(desc, visible);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            if (mustParseContent()) {
                System.out.print("");
            }
            return super.visitTypeAnnotation(typeRef, typePath, desc, visible);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            if (mustParseContent()) {
                System.out.print("");
            }
            return super.visitParameterAnnotation(parameter, desc, visible);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, type);
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            return super.visitTryCatchAnnotation(typeRef, typePath, desc, visible);
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
            return super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, visible);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(maxStack, maxLocals);
        }

        @Override
        public void visitEnd() {
            J2xClass returnType = parseDesc(returnType(desc));
            if (mustParseContent()) {
                body.getElements().addAll(stack.asCollection());
            } else {
                body.getElements().add(buildEmptyReturn(returnType));
            }

            int arg = 0;
            for (String argType : iterateArgs(desc)) {
                if (arguments.size() <= arg) {
                    J2xVariable argVar = new J2xVariable();
                    argVar.setName("a" + arg);
                    argVar.setType(parseClassPath(descToPath(argType)));
                    arguments.add(argVar);
                }
                arg++;
            }

            J2xMethod method = new J2xMethod(name,
                    returnType,
                    access(access),
                    isStatic(access),
                    isFinal(access),
                    desc,
                    arguments,
                    body);

            // El retorno covariante genera 2 métodos con el mismo nombre y argumentos, pero con distinto tipo de retorno
            // Busco si el método ya fue parseado, para tomar el que retorne la clase más específica
            J2xMethod existingMethod = j2xClass.findMethod(name, desc);
            if (existingMethod == null) {
                j2xClass.getMethods().add(method);
            } else {
                if (!method.getType().isAssignableFrom(existingMethod.getType())) {
                    j2xClass.getMethods().remove(existingMethod);
                    j2xClass.getMethods().add(method);
                }
            }
        }

        private J2xVariable variable(int var) {
            while (variables.size() <= var) {
                variables.add(null);
            }
            J2xVariable variable = variables.get(var);
            if (variable == null) {
                variable = new J2xVariable();
                variables.set(var, variable);
            }
            return variable;
        }
    }

    private static J2xReturn buildEmptyReturn(J2xClass returnType) {
        J2xReturn returnValue;
        if (J2xClass.VOID.equals(returnType)) {
            returnValue = new J2xReturn();
        } else if (J2xClass.BOOLEAN.equals(returnType)) {
            returnValue = new J2xReturn(new J2xLiteral(false));
        } else if (J2xClass.CHAR.equals(returnType)) {
            returnValue = new J2xReturn(new J2xLiteral(' '));
        } else if (J2xClass.BYTE.equals(returnType)
                || J2xClass.CHAR.equals(returnType)
                || J2xClass.DOUBLE.equals(returnType)
                || J2xClass.FLOAT.equals(returnType)
                || J2xClass.INT.equals(returnType)
                || J2xClass.LONG.equals(returnType)
                || J2xClass.SHORT.equals(returnType)) {
            returnValue = new J2xReturn(new J2xLiteral(0));
        } else {
            returnValue = new J2xReturn(new J2xLiteral(null));
        }
        return returnValue;
    }

    private boolean mustParseContent() {
        return parsePolicy.mustParseContent(j2xClass.getFullName());
    }

    private static Iterable<String> iterateArgs(String desc) {
        return new DescIterable(argTypes(desc));
    }

    private static int argCount(String desc) {
        return Iterables.size(iterateArgs(desc));
    }

    private static String argTypes(String desc) {
        return desc.substring(1, desc.indexOf(')'));
    }

    private String returnType(String desc) {
        return desc.substring(desc.indexOf(')') + 1);
    }
}