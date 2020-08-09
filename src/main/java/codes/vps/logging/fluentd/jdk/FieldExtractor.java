package codes.vps.logging.fluentd.jdk;

import java.util.logging.LogRecord;

public interface FieldExtractor {

    String getFieldName();
    Object extract(LogRecord l);

}
