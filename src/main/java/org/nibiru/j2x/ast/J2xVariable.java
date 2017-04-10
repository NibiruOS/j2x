package org.nibiru.j2x.ast;


import static com.google.common.base.Preconditions.checkNotNull;

public class J2xVariable {
    private final String name;
    private final J2xClass type;

    public J2xVariable(String name,
                       J2xClass type) {
        this.name = checkNotNull(name);
        this.type = checkNotNull(type);
    }

    public String getName() {
        return name;
    }

    public J2xClass getType() {
        return type;
    }
}
