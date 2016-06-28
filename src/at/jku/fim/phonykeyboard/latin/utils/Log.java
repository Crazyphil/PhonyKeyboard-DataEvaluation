package at.jku.fim.phonykeyboard.latin.utils;

public class Log {
    private static boolean dataOnly = false;
    private static boolean silent = false;

    public static void e(String tag, String message) {
        if (!silent && !dataOnly) {
            System.err.print(tag);
            System.err.print("\t[E] ");
            System.err.println(message);
        }
    }

    public static void i(String tag, String message) {
        if (!silent && !dataOnly) {
            System.out.print(tag);
            System.out.print("\t[I] ");
            System.out.println(message);
        }
    }

    public static void setDataOnly(boolean dataOnly) {
        Log.dataOnly = dataOnly;
    }

    public static void setSilent(boolean silent) {
        Log.silent = silent;
    }

    public static void data(String data) {
        if (!silent && dataOnly) {
            System.out.println(data);
        }
    }
}
