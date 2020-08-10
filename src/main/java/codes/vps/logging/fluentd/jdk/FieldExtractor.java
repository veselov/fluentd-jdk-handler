package codes.vps.logging.fluentd.jdk;

import java.util.logging.LogRecord;

/**
 * Defines extractors that harvest arbitrary field data from a log record.
 */
public interface FieldExtractor {

    /**
     * Name of the field to populate into the map sent to fluentd.
     * @return field name to use.
     */
    String getFieldName();

    /**
     * Extracts data to be populated into the map sent to fluentd.
     * @param l log record to extract data from
     * @return object to populate into the data map sent to fluentd,
     * with the key provided by {@link #getFieldName()}.
     */
    Object extract(LogRecord l);

}
