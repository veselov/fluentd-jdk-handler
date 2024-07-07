package codes.vps.logging.fluentd.jdk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class Test1 {

    private boolean isJava8; // if not, assumes better than 8
    private boolean isJava16OrBetter; // if not, assumes better than 8

    public Test1() throws Exception {
        String jv = System.getProperty("java.version");
        isJava8 = jv.startsWith("1.8");
        System.out.println(jv);

        if (isJava8) {
            isJava16OrBetter = false;
        } else {
            Method gv = Runtime.class.getMethod("version");
            Object vv = gv.invoke(null);
            int major = (int) vv.getClass().getMethod("feature").invoke(vv);
            isJava16OrBetter = major >= 16;
        }
    }

    @Test
    public void test1() {

        List<FieldExtractor> extractors = FluentdHandler.parseFormat(FluentdHandler.DEFAULT_FORMAT);

        LogRecord lr = new LogRecord(Level.FINE, "a");

        lr.setLoggerName("log");
        lr.setSourceClassName("src");
        lr.setSourceMethodName("method");
        lr.setMillis(100);
        lr.setThreadID(14);

        Map<String, Object> result = new HashMap<>();

        for (FieldExtractor fe : extractors) {
            result.put(fe.getFieldName(), fe.extract(lr));
        }

        Assertions.assertEquals("", result.get("$tag"));
        Assertions.assertEquals("", result.get("stack"));
        Assertions.assertEquals(Level.FINE.getLocalizedName() + " [14] src.method a", result.get("message"));
    }

    @Test
    public void test2() throws Exception {


        String format = "hello\"$[HELLO]\";pod_name\"$[POD_NAME]\";namespace\"$[NAMESPACE]$[NOT-THERE]\";millis\"${millis}\";logger\"${logger}\";tid\"${tid}\"";
        if (!isJava8) {
            format += ";nanos\"${nanos}\"";
        }
        List<FieldExtractor> extractors = FluentdHandler.parseFormat(format);

        LogRecord lr = new LogRecord(Level.FINE, "a");

        //set fake env vars
        setEnv("HELLO", "world");
        setEnv("POD_NAME", "myPodName");
        setEnv("NAMESPACE", "myNamespace");

        lr.setLoggerName("log");
        lr.setSourceClassName("src");
        lr.setSourceMethodName("method");
        lr.setMillis(100);

      if (!isJava8) {
            LogRecord.class.getMethod("setInstant", Instant.class).invoke(lr, Instant.ofEpochSecond(14, 812714563));
        }

        if (isJava16OrBetter) {
            LogRecord.class.getMethod("setLongThreadID", long.class).invoke(lr, 8589934592L);
        } else {
            lr.setThreadID(14);
        }
      
        lr.setLoggerName(getClass().getName());

        Map<String, Object> result = new HashMap<>();

        for (FieldExtractor fe : extractors) {
            result.put(fe.getFieldName(), fe.extract(lr));
        }

        if (isJava8) {
            Assertions.assertEquals("world", result.get("hello"));
            Assertions.assertEquals("myPodName", result.get("pod_name"));
            Assertions.assertEquals("myNamespace", result.get("namespace"));
        }

        Assertions.assertEquals(getClass().getName(), result.get("logger"));

        if (!isJava8) {
            Assertions.assertEquals(BigInteger.valueOf(14812714563L), result.get("nanos"));
            Assertions.assertEquals(14812L, result.get("millis"));
        } else {
            Assertions.assertEquals(100L, result.get("millis"));
        }

        if (isJava16OrBetter) {
            Assertions.assertEquals(8589934592L, result.get("tid"));
        } else {
            Assertions.assertEquals(14, result.get("tid"));
        }
    }

    private void setEnv(String key, String value) {

        if (!isJava8) { return; }

        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable. Make sure you're testing me with JDK8", e);
        }
    }

    @Test
    public void testTime() {

        long testMillis = 1470140394891L;
        String testTimeFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
        String testFormat = "time\"${millis,"+testTimeFormat+"}\"";

        List<FieldExtractor> extractors = FluentdHandler.parseFormat(testFormat);
        Calendar c = new GregorianCalendar();
        c.setTime(new Date(testMillis));
        String expected = new SimpleDateFormat(testTimeFormat).format(new Date(testMillis));

        LogRecord lr = new LogRecord(Level.FINE, "a");
        lr.setMillis(testMillis);

        Map<String, Object> result = new HashMap<>();

        for (FieldExtractor fe : extractors) {
            result.put(fe.getFieldName(), fe.extract(lr));
        }

        Assertions.assertEquals(expected, result.get("time"));

    }

}
