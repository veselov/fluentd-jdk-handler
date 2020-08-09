package codes.vps.logging.fluentd.jdk;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        Assertions.assertEquals("", result.get("tag"));
        Assertions.assertEquals("", result.get("stack"));
        Assertions.assertEquals("FINE [14] src.method a", result.get("message"));

    }

}
