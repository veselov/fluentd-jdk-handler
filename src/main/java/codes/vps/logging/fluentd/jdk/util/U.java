package codes.vps.logging.fluentd.jdk.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.logging.LogRecord;

public class U {

    /**
     * This method trims the specified string, and returns NULL if the string
     * was {@code null}, or empty.
     *
     * @param s string to trim
     * @return trimmed string, or {@code null}
     */
    @Nullable
    public static String sTrim(String s) {
        if (s == null || "".equals(s = s.trim())) {
            return null;
        }
        return s;
    }

    /**
     * Creates a string representation of a throwable, including its stack
     * trace.
     * @param e throwable object
     * @return string containing pretty printed message and stack trace
     * of the specified throwable object.
     */
    public static String throwableToString(Throwable e) {

        return throwableToString(new StringBuilder(), e).toString();

    }

    public static StringBuilder throwableToString(StringBuilder sb, Throwable xx) {

        if (xx == null) { return sb; }

        StackTraceElement [] nextStack = xx.getStackTrace();
        Throwable next = xx;
        int nextStop = -1;

        while (true) {

            Throwable cur = next;
            StackTraceElement[] stacks = nextStack;
            int stop = nextStop;

            next = cur.getCause();
            if (next != null) {
                nextStack = next.getStackTrace();
            }

            sb.append(cur.getClass().getName());
            sb.append(' ');
            sb.append(cur.getMessage());

            if (stacks == null) {

                sb.append("\n<NULL STACK TRACE>");

            } else {

                // int mark = -1;

                if (nextStack != null) {

                    int my_len = stacks.length;
                    int next_len = nextStack.length;

                    int j, k;

                    for (j=my_len-1, k=next_len-1; j>=0 && k>=0; j--, k--) {

                        if (stacks[j].equals(nextStack[k])) {
                            nextStop = k;
                            // mark = j-1;
                        } else {
                            break;
                        }
                    }
                } else {
                    nextStop = -1;
                }

                for (int j=0; j<stacks.length; j++) {

                    StackTraceElement ste = stacks[j];

                    sb.append("\n  at ");
                    sb.append(ste.getClassName());
                    sb.append('.');
                    sb.append(ste.getMethodName());
                    sb.append('(');
                    if (ste.isNativeMethod()) {
                        sb.append("NATIVE");
                    } else {
                        String fileName = ste.getFileName();
                        if (fileName == null) {
                            sb.append("UNKNOWN");
                        } else {
                            sb.append(fileName);
                            sb.append(':');
                            sb.append(ste.getLineNumber());
                        }
                    }
                    sb.append(')');

                    /*
                    // that entire "mark" business is commented out because
                    // it's not at all clear what it shows...
                    if (mark > 0 && j == mark) {
                        sb.append (" <-cause");
                    }
                     */

                    if (stop == j) {
                        int skipped = stacks.length - j - 1;
                        if (skipped > 0) {
                            sb.append("\n <... skipped ").
                                    append(skipped).
                                    append(" duplicate trace lines...>");
                            break;
                        }
                    }

                }
            }

            if (next != null) {
                sb.append("\nCaused by :");
            } else {
                break;
            }

        }

        return sb;

    }

    /**
     * Enables to throw an exception as a run-time exception.
     * This method does not declare any thrown exceptions, but any exception
     * can be safely passed to it, so it is re-thrown to the caller as-is.
     * @param e exception to throw
     * @return thrown exception.
     */
    // Thanks to http://blog.jooq.org/2012/09/14/throw-checked-exceptions-like-runtime-exceptions-in-java/
    public static RuntimeException doThrow(Throwable e) {
        return doThrow0(e);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException doThrow0(Throwable e) throws E {
        throw (E) e;
    }


    @Nullable
    public static <S, T> S ifNotNull(@Nullable T val, Function<T, S> fun, S whenNull) {
        try {
            if (val == null) {
                return whenNull;
            }
            return fun.apply(val);
        } catch (Exception e) {
            throw doThrow(e);
        }
    }

    public static String formatMessage(LogRecord record) {

        // this is copied from java.util.logging.Formatter.formatMessage()
        String format = record.getMessage();
        java.util.ResourceBundle catalog = record.getResourceBundle();
        if (catalog != null) {
            try {
                format = catalog.getString(record.getMessage());
            } catch (java.util.MissingResourceException ex) {
                // Drop through.  Use record message as format
                format = record.getMessage();
            }
        }
        // Do the formatting.
        try {
            Object[] parameters = record.getParameters();
            if (parameters == null || parameters.length == 0) {
                // No parameters.  Just return format string.
                return format;
            }
            // Is it a java.text style format?
            // Ideally we could match with
            // Pattern.compile("\\{\\d").matcher(format).find())
            // However the cost is 14% higher, so we cheaply check for
            // 1 of the first 4 parameters
            if (format.contains("{0") || format.contains("{1") ||
                    format.contains("{2") || format.contains("{3")) {
                return java.text.MessageFormat.format(format, parameters);
            }
            return format;

        } catch (Exception ex) {
            // Formatting failed: use localized format string.
            return format;
        }


    }

    public static <S> void whenNotNull(@Nullable S val, @NotNull ConsumerT<S, Exception> fun) {
        whenNotNullOr(val, fun, null);
    }

    @SuppressWarnings("unused")
    public static <S> void whenNotNull(@Nullable S val, @NotNull ConsumerT<S, Exception> fun, @Nullable SupplierT<S, Exception> whenNull) {
        reThrow(()->{
            S v = val;
            if (v == null) {
                if (whenNull != null) {
                    v = whenNull.get();
                }
            }
            if (v != null) {
                fun.accept(v);
            }
        });
    }

    public static <S> void whenNotNullOr(@Nullable S val, @Nullable ConsumerT<S, Exception> fun, @Nullable RunnableT<Exception> whenNull) {
        reThrow(()->{
            if (val == null) {
                if (whenNull != null) {
                    whenNull.run();
                }
            } else {
                if (fun != null) {
                    fun.accept(val);
                }
            }
        });
    }

    /**
     * Extracts result from a {@link Callable}, throwing any produced exception as
     * a runtime exception.
     * @param from get result from
     * @return result from Callable
     */
    @SuppressWarnings("unused")
    public static <T> T reThrow(Callable<T> from) {
        try {
            return from.call();
        } catch (Exception e) {
            throw doThrow(e);
        }
    }

    /**
     * Executes a {@link RunnableT}, throwing any produced exception as
     * a runtime exception.
     * @param r Runnable to execute
     */
    public static void reThrow(RunnableT<? extends Throwable> r) {

        try {
            r.run();
        } catch (Throwable e) {
            throw doThrow(e);
        }

    }


}
