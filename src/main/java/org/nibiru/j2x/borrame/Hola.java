package org.nibiru.j2x.borrame;

import org.nibiru.j2x.ast.J2xNative;

public class Hola {
    public Hola() {
        //super(456);
        String toto = "123";
        toto.toUpperCase();
        // toto.toUpperCase();
//        String caca ="pablo";
//        caca.toString();
    }

    private static int numero(int a) {
        return 789;
    }

    private static int numero(int a, int b) {
        return 666;
    }

    @J2xNative(language = "C#",
            value = "System.Console.WriteLine(\"Hola!\");" +
            "\nreturn 5+1;")
    public native int sumar(int valor); // TODO: no está reconociendo los parámetros en los métodos nativos
//    private static int numero(int a, int b) {
//        return 666;
//    }
    //    final String saludo = "hola";
//    public int kkck = 123;
//    public String[] otro;
//    public Date[][] fechas;
//    boolean zxing;
//
//    public void simple() {
//        System.out.println("hola");
//    }
//    public Hola(String pepito, boolean x) {
//        super();
//    }
//
//    public void saludar() {
    //boolean mal = true;
//        if (mal) {
//            return "Hola guampa";
//        } else {
//            return "Hola";
//        }
    //return "Hola";
//    }
}
