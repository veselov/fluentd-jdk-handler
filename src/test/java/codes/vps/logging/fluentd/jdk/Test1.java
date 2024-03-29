package codes.vps.logging.fluentd.jdk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class Test1 {

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
    public void test2() {

        List<FieldExtractor> extractors = FluentdHandler.parseFormat("hello\"$[HELLO]\";pod_name\"$[POD_NAME]\";namespace\"$[NAMESPACE]$[NOT-THERE]\"");

        LogRecord lr = new LogRecord(Level.FINE, "a");

        //set fake env vars
        setEnv("HELLO", "world");
        setEnv("POD_NAME", "myPodName");
        setEnv("NAMESPACE", "myNamespace");

        lr.setLoggerName("log");
        lr.setSourceClassName("src");
        lr.setSourceMethodName("method");
        lr.setMillis(100);
        lr.setThreadID(14);

        Map<String, Object> result = new HashMap<>();

        for (FieldExtractor fe : extractors) {
            result.put(fe.getFieldName(), fe.extract(lr));
        }

        Assertions.assertEquals("world", result.get("hello"));
        Assertions.assertEquals("myPodName", result.get("pod_name"));
        Assertions.assertEquals("myNamespace", result.get("namespace"));
        Assertions.assertNotEquals("badNamespace", result.get("namespace"));
    }

    private static void setEnv(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
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
