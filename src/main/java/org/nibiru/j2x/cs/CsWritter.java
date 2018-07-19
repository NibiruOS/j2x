package org.nibiru.j2x.cs;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.nibiru.j2x.ast.J2xAccess;
import org.nibiru.j2x.ast.J2xArray;
import org.nibiru.j2x.ast.J2xClass;
import org.nibiru.j2x.ast.J2xField;
import org.nibiru.j2x.ast.J2xMember;
import org.nibiru.j2x.ast.J2xMethod;
import org.nibiru.j2x.ast.element.J2xVariable;
import org.nibiru.j2x.ast.sentence.J2xMethodCallSentence;
import org.nibiru.j2x.ast.sentence.J2xSentence;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkNotNull;

public class CsWritter {
    private static final Map<String, String> PREDEFINED_TYPES =
            ImmutableMap.of(Object.class.getName(), "object",
                    String.class.getName(), "string");
    private final Writer out;
    private final boolean pretty;
    private int indentation;

    public CsWritter(Writer out, boolean pretty) {
        this.out = checkNotNull(out);
        this.pretty = pretty;
    }

    public void write(J2xClass j2xClass) {
        try {
            line("namespace %s {", capitalize(j2xClass.getPackageName()));
            indentation++;
            line("%sclass %s {", access(j2xClass.getAccess()), j2xClass.getName());
            indentation++;

            for (J2xField field : j2xClass.getFields()) {
                write(field);
            }

            for (J2xMethod method : j2xClass.getMethods()) {
                write(j2xClass, method);
            }
            indentation--;
            line("}");
            indentation--;
            line("}");
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void write(J2xField field) {
        line("%s%s %s;",
                modifiers(field),
                type(field.getType()),
                field.getName());
    }

    private void write(J2xClass j2xClass, J2xMethod method) {


        line("%s%s%s(%s) %s {",
                modifiers(method),
                method.isConstructor()
                        ? ""
                        : type(method.getType()) + " ",
                capitalize(method.isConstructor()
                        ? j2xClass.getName()
                        : method.getName()),
                Joiner.on(", ").join(StreamSupport.stream(method.getArguments().spliterator(), false)
                        .map(CsWritter::variable)
                        .collect(Collectors.toList())),
                method.getBody().getSentences().isEmpty() && isSuperCall(method.getBody().getSentences().get(0)) // TODO: arreglar esto que est are feo y no anda
                        ? " : super(" + buildArgs((J2xMethodCallSentence) method.getBody().getSentences().get(0)) + ")"
                        : "");

        indentation++;
        for (J2xVariable variable : method.getBody().getVariables()) {
            line(type(variable.getType()) + " " + variable.getName() + ";");
        }
        for (J2xSentence sentence : method.getBody().getSentences()) {
            if (sentence instanceof J2xMethodCallSentence && !isSuperCall(sentence)) {
                J2xMethodCallSentence callSentence = (J2xMethodCallSentence) sentence;
                line(callSentence.getTarget().getName()
                        + "."
                        + callSentence.getMethod().getName()
                        + "("
                        + buildArgs(callSentence)
                        + ");");

            }
        }

        indentation--;

        line("}");
    }

    private static boolean isSuperCall(J2xSentence sentence) {
        if (sentence instanceof J2xMethodCallSentence) {
            J2xMethodCallSentence callSentence = (J2xMethodCallSentence) sentence;
            return callSentence.getMethod().isConstructor();
        } else {
            return false;
        }
    }

    private static String buildArgs(J2xMethodCallSentence callSentence) {
        return Joiner.on(',').join(callSentence.getArgs());
    }

    private static String variable(J2xVariable variable) {
        return String.format("%s %s",
                type(variable.getType()),
                variable.getName());
    }

    private static String type(J2xClass type) {
        if (type instanceof J2xArray) {
            J2xArray arrayType = (J2xArray) type;
            return type(arrayType.getItemClass()) + Strings.repeat("[]", arrayType.getDimensions());
        } else {
            return type.isPrimitive() ?
                    type.getName()
                    : PREDEFINED_TYPES.getOrDefault(type.getFullName(), capitalize(type.getFullName()));
        }
    }

    private static String access(J2xAccess access) {
        if (access == J2xAccess.PUBLIC) {
            return "public ";
        } else {
            return "";
        }
    }

    private static String modifiers(J2xMember member) {
        return String.format("%s%s%s",
                access(member.getAccess()),
                member.isStatic() ? "static " : "",
                member.isFinal() ? "readonly " : "");
    }

    private void startLine() {
        try {
            if (pretty) {
                out.append(Strings.repeat("\t", indentation));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void write(String format, Object... args) {
        try {
            out.append(String.format(format, args));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void endLine() {
        try {
            if (pretty) {
                out.append("\r\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void line(String line, Object... args) {
        startLine();
        write(line, args);
        endLine();
    }

    private static String capitalize(String packageName) {
        return Joiner.on('.')
                .join(Iterables.transform(Splitter.on('.')
                        .split(packageName), CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.UPPER_CAMEL)));
    }
}
