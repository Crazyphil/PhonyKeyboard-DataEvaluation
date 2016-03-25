package at.jku.fim.phonykeyboard.latin.utils;

public class Log {
    public static void e(String tag, String message) {
        System.err.print(tag);
        System.err.print("\t[E]");
        System.err.println(message);
    }

    public static void i(String tag, String message) {
        System.out.print(tag);
        System.out.print("\t[I]");
        System.out.println(message);
    }
}
