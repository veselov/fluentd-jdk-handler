package codes.vps.logging.fluentd.jdk;

import codes.vps.logging.fluentd.jdk.util.ForwardString;
import org.fluentd.logger.FluentLogger;
import org.fluentd.logger.FluentLoggerFactory;
import org.fluentd.logger.sender.ExponentialDelayReconnector;
import org.fluentd.logger.sender.Reconnector;
import codes.vps.logging.fluentd.jdk.util.StringWinder;
import codes.vps.logging.fluentd.jdk.util.U;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * JDK logging handler implementation for forwarding logging data
 * to Fluentd.
 */
@SuppressWarnings("unused")
public class FluentdHandler extends Handler {

    // this follows apache tomcat default format, but without a timestamp, and stack trace logged separately
    /**
     * Default logger format.
     */
    public final static String DEFAULT_FORMAT = "$tag\"\";message\"${level10n} [${tid}] ${class}.${method} ${l10n}\";stack\"${trace}\"";
    private final static FluentLoggerFactory factory = new FluentLoggerFactory();

    private Function<LogRecord, Map<String, Object>> mapper;
    private List<FieldExtractor> extractors;

    private FluentLogger logger;

    /**
     * Creates new handler from JDK logging configuration. This construction should only
     * be invoked by the JDK logging framework; otherwise you will need to populate the properties
     * needed by this handler into the JDK logger manager.
     */
    public FluentdHandler() {
        configure();
    }

    /**
     * Creates new handler from specified builder.
     * @param with builder with the information on how to create the handler.
     */
    public FluentdHandler(Builder with) {
        configure(with);
    }

    private void configure(Builder b) {

        if (b.extractors == null && b.mapper == null) {
            throw new NullPointerException("No extraction properties provided, specify extractors or mapper in the builder");
        }

        initLogger(b);
        this.extractors = b.extractors;
        this.mapper = b.mapper;
    }

    private void configure() {
        Builder b = new Builder();
        U.whenNotNull(getProperty("tag_prefix"), p->b.tagPrefix = p);
        U.whenNotNull(getProperty("host"), p->b.host = p);
        U.whenNotNull(getProperty("port"), p->b.port = Integer.parseInt(p));
        U.whenNotNull(getProperty("timeout_ms"), p->b.timeout = Integer.parseInt(p));
        U.whenNotNull(getProperty("buffer_capacity"), p->b.bufferCapacity = Integer.parseInt(p));
        U.whenNotNull(getProperty("format"), p->b.extractors = parseFormat(p));
        configure(b);
    }

    private void initLogger(Builder b) {
        logger = factory.getLogger(b.tagPrefix, b.host, b.port, b.timeout, b.bufferCapacity, b.reconnector);
    }

    private String getProperty(String name) {
        String value = LogManager.getLogManager().getProperty(getClass().getName() + '.' + name);
        if (value == null) {
            return null;
        } else {
            value = U.sTrim(value);
        }
        return value;
    }

    /**
     * Publishes logging record through the handler.
     * @param record record to publish.
     */
    public void publish(LogRecord record) {

        Map<String, Object> result;
        if (mapper != null) {
            result = mapper.apply(record);
        } else {
            result = new HashMap<>();
            for (FieldExtractor f : extractors) {
                result.put(f.getFieldName(), f.extract(record));
            }
        }

        String tag = (String) result.get("$tag");
        result.remove("$tag");

        Long timestamp = U.ifNotNull(result.get("$timestamp"), r->((Number)r).longValue(), null);
        result.remove("$timestamp");

        if (tag == null) {
            tag = record.getLoggerName();
        }
        if (timestamp == null) {
            timestamp = record.getMillis();
        }

        logger.log(tag, result, timestamp / 1000);

    }

    /**
     * Flushes logged messages.
     */
    public void flush() {
        logger.flush();
    }

    /**
     * Closes the handler. Underlying fluentd connection is also closed.
     * Handler must not be used after this method is called.
     */
    public void close() {
        logger.close();
    }

    /**
     * Parses format string to produce a list of field extractors
     * that will be used to populate the map.
     * See https://github.com/veselov/fluentd-jdk-handler/blob/master/README.md
     * for the information on this format.
     * @param s format string to translate to list of field extractors.
     * @return list of field extractors created based on the specified format.
     */
    public static List<FieldExtractor> parseFormat(String s) {

        List<FieldExtractor> items = new ArrayList<>();
        StringWinder sw = new ForwardString(s);
        boolean escape = false;
        StringBuilder buf = new StringBuilder();

        Consumer<StringBuilder> addOne = buf2-> {
            String item = U.sTrim(buf2.toString());
            if (item != null) {
                try {
                    items.add(new FieldExtractorImpl(item));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to parse format "+s, e);
                }
            }
        };

        // the top-level just cuts the string into individual FieldExtractors

        while (sw.hasNext()) {

            char c = sw.next();
            if (escape) {
                buf.append(c);
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == ';') {
                addOne.accept(buf);
                buf = new StringBuilder();
                continue;
            }

            buf.append(c);

        }

        if (buf.length() > 0) {
            addOne.accept(buf);
        }

        if (escape) {
            throw new IllegalArgumentException("String "+s+" terminated on escape character");
        }

        return items;

    }

    /**
     * Builder class used to provide configuration for the handler.
     * When a new build is created, it is populated with default values.
     * See https://github.com/veselov/fluentd-jdk-handler/blob/master/README.md for
     * list of default values not provided here.
     */
    @SuppressWarnings({"FieldMayBeFinal", "UnusedReturnValue"})
    public static class Builder {

        // builder is filled with default values.
        private String tagPrefix = "";
        private String host = "127.0.0.1";
        private int port = 24224;
        private int timeout = 3 * 1000;
        private int bufferCapacity = 8 * 1024 * 1024;
        private Reconnector reconnector = new ExponentialDelayReconnector();
        private Function<LogRecord, Map<String, Object>> mapper;
        private List<FieldExtractor> extractors = parseFormat(DEFAULT_FORMAT);

        /**
         * Returns currently set tag prefix.
         * @return currently set tag prefix.
         */
        public String getTagPrefix() {
            return tagPrefix;
        }

        /**
         * Sets tag prefix. All messages will be prefixed with it, indiscriminately.
         * @param tagPrefix tag prefix to use.
         * @return this builder instance
         */
        public Builder setTagPrefix(String tagPrefix) {
            this.tagPrefix = tagPrefix;
            return this;
        }

        /**
         * Returns currently set host to log messages to.
         * @return currently set host.
         */
        public String getHost() {
            return host;
        }

        /**
         * Sets host to send log messages to.
         * @param host host to use
         * @return this builder instance
         */
        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        /**
         * Returns current set port to log messages to.
         * @return currently set port.
         */
        public int getPort() {
            return port;
        }

        /**
         * Sets port to send log messages to.
         * @param port port to use
         * @return this builder instance
         */
        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        /**
         * Returns currently set connection timeout.
         * @return currently set connection timeout
         */
        public int getTimeout() {
            return timeout;
        }

        /**
         * Sets connection timeout, in milliseconds.
         * See https://github.com/fluent/fluent-logger-java for more information.
         * @param timeout_ms connection timeout to use
         * @return this builder instance
         */
        public Builder setTimeout(int timeout_ms) {
            this.timeout = timeout_ms;
            return this;
        }

        /**
         * Returns currently set buffer capacity.
         * @return currently set buffer capacity.
         */
        public int getBufferCapacity() {
            return bufferCapacity;
        }

        /**
         * Sets buffer capacity for the fluentd library, in bytes.
         * See https://github.com/fluent/fluent-logger-java for more information.
         * @param bufferCapacity buffer capacity to use
         * @return this builder instance
         */
        public Builder setBufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
            return this;
        }

        /**
         * Returns currently used reconnector.
         * @return currently used reconnector.
         */
        public Reconnector getReconnector() {
            return reconnector;
        }

        /**
         * Sets reconnector to use.
         * See https://github.com/fluent/fluent-logger-java for more information.
         * By default {@link ExponentialDelayReconnector} is used.
         * @param reconnector reconnector to use
         * @return this builder instance
         */
        public Builder setReconnector(Reconnector reconnector) {
            this.reconnector = reconnector;
            return this;
        }

        /**
         * Returns currently set function to map log records to
         * outgoing message. See {@link #setMapper(Function)} for
         * more details.
         * @return currently set mapping function.
         */
        public Function<LogRecord, Map<String, Object>> getMapper() {
            return mapper;
        }

        /**
         * Sets mapper function to use for mapping log records into
         * outgoing message. For contents of the map, please
         * see https://github.com/veselov/fluentd-jdk-handler/blob/master/README.md#formatting.
         * If mapper function is defined, extractors (default or set by {@link #setExtractors(List)}
         * are not used. Mapper function is invoked for every incoming log record,
         * and must produce a map. Except for {@code tag} and {@code timestamp} properties of
         * the map, its contents are forwarded to fluentd.
         * @param mapper mapper to use
         * @return this builder instance
         */
        public Builder setMapper(Function<LogRecord, Map<String, Object>> mapper) {
            this.mapper = mapper;
            return this;
        }

        /**
         * Lists currently set extractors. See {@link #setExtractors(List)} for more details.
         * @return list currently set extractors used to create outgoing messages.
         */
        public List<FieldExtractor> getExtractors() {
            return extractors;
        }

        /**
         * Sets list of extractors to use for sending out a message. Note that
         * extractors are not used if a non-null mapper is set with {@link #setMapper(Function)}.
         * Extractors are used to populate the object map that is then forwarded to
         * fluentd. See see https://github.com/veselov/fluentd-jdk-handler/blob/master/README.md#formatting
         * on how the map is interpreted. Each extractor must indicate which map property it populates,
         * and contain functionality that produces its value.
         * Default extractors are created using {@link #parseFormat(String)}. Caller can create
         * a function-based extractor using {@link FieldExtractorImpl#FieldExtractorImpl(String, Function)}.
         *
         * @param extractors extractors to use.
         * @return this builder instance
         */
        public Builder setExtractors(List<FieldExtractor> extractors) {
            this.extractors = extractors;
            return this;
        }

        public Builder() {
        }
    }

}
