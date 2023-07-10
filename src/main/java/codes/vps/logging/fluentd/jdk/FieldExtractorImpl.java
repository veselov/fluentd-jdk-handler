package codes.vps.logging.fluentd.jdk;

import codes.vps.logging.fluentd.jdk.util.ForwardString;
import codes.vps.logging.fluentd.jdk.util.StringWinder;
import codes.vps.logging.fluentd.jdk.util.U;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.LogRecord;

public class FieldExtractorImpl implements FieldExtractor {

    private final String fieldName;
    private final Function<LogRecord, Object> extract;

    @SuppressWarnings("unused")
    public FieldExtractorImpl(String fieldName, Function<LogRecord, Object> extract) {
        this.fieldName = fieldName;
        this.extract = extract;
    }

    FieldExtractorImpl(String item) {

        StringWinder sw = new ForwardString(item);

        boolean escape = false;

        StringBuilder sb = new StringBuilder();
        String fieldName = null;
        String format = null;

        int mode = 0;

        while (sw.hasNext()) {

            char c = sw.next();
            if (escape) {
                escape = false;
                sb.append(c);
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (mode == 0 && c == '"') {
                mode = 1;
                fieldName = U.sTrim(sb.toString());
                sb = new StringBuilder();
                continue;
            }

            if (mode == 1 && c == '"') {
                mode = 2;
                format = U.sTrim(sb.toString());
                sb = new StringBuilder();
                continue;
            }

            sb.append(c);

        }

        if (escape) {
            throw new IllegalArgumentException("Extractor statement "+item+" ends with an escape character");
        }

        if (mode != 2 || fieldName == null) {
            throw new IllegalArgumentException("No format or fieldName found in "+item);
        }

        String type = U.sTrim(sb.toString());
        if (format == null) { format = ""; }

        this.fieldName = fieldName;

        Function<LogRecord, Object> ext = null;

        sw = new ForwardString(format);

        sb = new StringBuilder();

        mode = 0;

        BiFunction<Function<LogRecord, Object>, Function<LogRecord, Object>, Function<LogRecord, Object>> meld = (a, b)->{
            if (b == null) { throw new RuntimeException("That's not supposed to happen"); }
            if (a == null) { return b; }
            return (l)-> String.valueOf(a.apply(l)) + b.apply(l);
        };
        BiFunction<Function<Object, Object>, Function<LogRecord, Object>, Function<LogRecord, Object>> sub = (a,b)->{
            if (b == null || a == null) { throw new RuntimeException("That's not supposed to happen"); }
            return l->a.apply(b.apply(l));
        };

        while (sw.hasNext()) {

            char c = sw.next();

            if (escape) {
                sb.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (mode == 0 && c == '$') {
                mode = 1;
                continue;
            }

            if (mode == 1 && c == '[') {
                mode = 3;
                if (sb.length() > 0) {
                    String constant = sb.toString();
                    ext = meld.apply(ext, (l)->constant);
                    sb = new StringBuilder();
                }
                continue;
            }

            if (mode == 3 && c == ']') {
                mode = 0;
                String inlay = sb.toString();
                ext = meld.apply(ext, (l) -> System.getenv(inlay));
                sb = new StringBuilder();
                continue;
            }

            if (mode == 1 && c == '{') {
                mode = 2;
                if (sb.length() > 0) {
                    String constant = sb.toString();
                    ext = meld.apply(ext, (l)->constant);
                    sb = new StringBuilder();
                }
                continue;
            }

            if (mode == 2 && c == '}') {
                mode = 0;
                String inlay = sb.toString();
                if ("level".equals(inlay)) {
                    ext = meld.apply(ext, (l)->l.getLevel().getName());
                } else if ("level10n".equals(inlay)) {
                    ext = meld.apply(ext, (l)->l.getLevel().getLocalizedName());
                } else if ("sequence".equals(inlay)) {
                    ext = meld.apply(ext, LogRecord::getSequenceNumber);
                } else if ("class".equals(inlay)) {
                    ext = meld.apply(ext, LogRecord::getSourceClassName);
                } else if ("method".equals(inlay)) {
                    ext = meld.apply(ext, LogRecord::getSourceMethodName);
                } else if ("message".equals(inlay)) {
                    ext = meld.apply(ext, LogRecord::getMessage);
                } else if ("l10n".equals(inlay)) {
                    ext = meld.apply(ext, U::formatMessage);
                } else if ("params".equals(inlay)) {
                    ext = meld.apply(ext, l->{
                        Object [] ps = l.getParameters();
                        StringBuilder sb2 = new StringBuilder();
                        boolean first = true;
                        if (ps != null) {
                            for (Object p : ps) {
                                if (first) {
                                    first = false;
                                } else {
                                    sb2.append(',');
                                }
                                sb2.append(p);
                            }
                        }
                        return sb2.toString();
                    });
                } else if ("millis".equals(inlay)) {
                    ext = meld.apply(ext, LogRecord::getMillis);
                } else if ("tid".equals(inlay)) {
                    ext = meld.apply(ext, LogRecord::getThreadID);
                } else if ("trace".equals(inlay)) {
                    ext = meld.apply(ext, l->U.ifNotNull(l.getThrown(), U::throwableToString, ""));
                } else if (inlay.startsWith("millis,")) {
                    String dtf = inlay.substring(7);
                    SimpleDateFormat sdf;
                    try {
                        sdf = new SimpleDateFormat(dtf);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to parse date format in "+dtf+" out of "+inlay + " in "+item, e);
                    }
                    ext = meld.apply(ext, (l)->{
                        Date date = new Date(l.getMillis());
                        return sdf.format(date);
                    });
                } else {
                    throw new IllegalArgumentException("Can't resolve "+inlay + " in "+item);
                }
                sb = new StringBuilder();
                continue;
            }

            if (mode == 1) {
                sb.append('$'); // this was a stray '$', without '{'
                mode = 0;
            }

            sb.append(c);

        }

        if (mode == 1) {
            sb.append('$');
        }

        if (mode == 2) {
            throw new IllegalArgumentException("Unterminated } in "+item);
        }

        if (sb.length() > 0) {
            String constant = sb.toString();
            ext = meld.apply(ext, (l)->constant);
        }

        if (ext == null) {
            ext = l->"";
        }

        if ("s".equals(type)) {
            ext = sub.apply(String::valueOf, ext);
        } else if ("n".equals(type)) {
            ext = sub.apply(o->new Long(String.valueOf(o)), ext);
        } else if ("b".equals(type)) {
            ext = sub.apply(o-> Boolean.valueOf(String.valueOf(o)), ext);
        } else if (type != null) {
            throw new RuntimeException("Unknown type "+type+" in item");
        }

        extract = ext;

    }

    public Object extract(LogRecord l) {
        return extract.apply(l);
    }

    public String getFieldName() {
        return fieldName;
    }


}
