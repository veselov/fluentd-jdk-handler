package codes.vps.logging.fluentd.jdk;

import codes.vps.logging.fluentd.jdk.util.ConsumerT;
import codes.vps.logging.fluentd.jdk.util.ForwardString;
import codes.vps.logging.fluentd.jdk.util.StringWinder;
import codes.vps.logging.fluentd.jdk.util.U;
import org.jetbrains.annotations.NotNull;
import org.komamitsu.fluency.EventTime;
import org.komamitsu.fluency.Fluency;
import org.komamitsu.fluency.fluentd.FluencyBuilderForFluentd;

import java.io.IOException;
import java.net.InetSocketAddress;
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
    public final static String DEFAULT_FORMAT = "$tag\"\";message\"${level10n} [${tid}] ${class}.${method} ${l10n}\";stack\"${trace}\";pod_name\"$[pod_name]\"";

    private Function<LogRecord, Map<String, Object>> mapper;
    private List<FieldExtractor> extractors;

    private Fluency logger;

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

        this.extractors = b.extractors;
        this.mapper = b.mapper;

        initLogger(b);

    }

    private void configure() {
        Builder b = new Builder();
        // our stuff
        cfg("tag_prefix", p->b.tagPrefix = p);
        cfg("host", p->b.host = p);
        cfg("port", p->b.port = p);
        cfg("format", p->b.extractors = parseFormat(p));

        FluencyBuilderForFluentd fb = b.fluencyBuilder;

        // fluency-fluentd
        iCfg("sender_max_retry_count", fb::setSenderMaxRetryCount);
        iCfg("sender_base_retry_interval_millis", fb::setSenderBaseRetryIntervalMillis);
        iCfg("sender_max_retry_interval_millis", fb::setSenderMaxRetryIntervalMillis);
        bCfg("ack_response_mode", fb::setAckResponseMode);
        bCfg("ssl_enabled", fb::setSslEnabled);
        iCfg("connection_timeout_milli", fb::setConnectionTimeoutMilli);
        iCfg("read_timeout_milli", fb::setReadTimeoutMilli);

        // fluency
        lCfg("max_buffer_size", fb::setMaxBufferSize);
        iCfg("buffer_chunk_initial_size", fb::setBufferChunkInitialSize);
        iCfg("buffer_chunk_retention_size", fb::setBufferChunkRetentionSize);
        iCfg("buffer_chunk_retention_time_millis", fb::setBufferChunkRetentionTimeMillis);
        iCfg("flush_attempt_interval_millis", fb::setFlushAttemptIntervalMillis);
        cfg("file_backup_dir", fb::setFileBackupDir);
        iCfg("wait_until_buffer_flushed", fb::setWaitUntilBufferFlushed);
        iCfg("wait_until_flusher_terminated", fb::setWaitUntilFlusherTerminated);
        bCfg("jvm_head_buffer_mode", fb::setJvmHeapBufferMode);

        configure(b);
    }

    private void cfg(String prop, @NotNull ConsumerT<String, Exception> fun) {
        U.whenNotNull(getProperty(prop), fun);
    }

    private void iCfg(String prop, @NotNull ConsumerT<Integer, Exception> fun) {
        U.whenNotNull(getProperty(prop), p->fun.accept(Integer.parseInt(p)));
    }

    @SuppressWarnings("SameParameterValue")
    private void lCfg(String prop, @NotNull ConsumerT<Long, Exception> fun) {
        U.whenNotNull(getProperty(prop), p->fun.accept(Long.parseLong(p)));
    }

    private void bCfg(String prop, @NotNull ConsumerT<Boolean, Exception> fun) {
        U.whenNotNull(getProperty(prop), p->fun.accept("true".equals(p)));
    }

    private void initLogger(Builder b) {

        FluencyBuilderForFluentd builder = new FluencyBuilderForFluentd();

        String [] hosts = b.getHost().split(",");
        String [] ports = b.getPort().split(",");

        if (hosts.length != ports.length) {
            throw new IllegalArgumentException("List of hosts must match list of ports");
        }

        if (hosts.length == 1) {

            logger = builder.build(hosts[0], Integer.parseInt(ports[0]));

        } else {

            List<InetSocketAddress> list = new ArrayList<>();
            for (int i=0; i<hosts.length; i++) {
                list.add(new InetSocketAddress(hosts[i], Integer.parseInt(ports[i])));
            }

            logger = builder.build(list);

        }

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

        String tag = (String) result.remove("$tag");

        Long timestamp = U.ifNotNull(result.remove("$timestamp"), r->((Number)r).longValue(), null);

        if (tag == null) {
            tag = record.getLoggerName();
        }
        if (timestamp == null) {
            timestamp = record.getMillis();
        }

        try {
            logger.emit(tag, EventTime.fromEpochMilli(timestamp), result);
        } catch (IOException e) {
            throw U.doThrow(e);
        }

    }

    /**
     * Flushes logged messages.
     */
    public void flush() {
        U.reThrow(()->logger.flush());
    }

    /**
     * Closes the handler. Underlying fluentd connection is also closed.
     * Handler must not be used after this method is called.
     */
    public void close() {
        U.reThrow(()->logger.close());
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

        // builder is filled with default values. Fluency default values are based on
        // https://github.com/komamitsu/fluency (and from source code when needed)

        FluencyBuilderForFluentd fluencyBuilder = new FluencyBuilderForFluentd();

        private String host = "127.0.0.1";
        private String port = "24224";
        private String tagPrefix = "";
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
         * Returns currently set host(s) to log messages to.
         * @return currently set host(s).
         */
        public String getHost() {
            return host;
        }

        /**
         * Sets host to send log messages to. To connect to multiple fluentd instances simultaneously,
         * specify comma-separated list. The list length must match value passed to
         * {@link #setPort(String)}
         * @param host host to use
         * @return this builder instance
         */
        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        /**
         * Returns current set port(s) to log messages to.
         * @return currently set port(s).
         */
        public String getPort() {
            return port;
        }

        /**
         * Sets port to send log messages to.  To connect to multiple fluentd instances
         * simultaneously, specify comma-separated list. The list length must match value passed to
         * {@link #setHost(String)}
         * @param port port(s) to use
         * @return this builder instance
         */
        public Builder setPort(String port) {
            this.port = port;
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

        /**
         * Return underlying fluency fluentd builder. Configure this builder
         * to modify fluency specific parameters.
         * @return fluency builder
         */
        @NotNull
        public FluencyBuilderForFluentd getFluencyBuilder() {
            return fluencyBuilder;
        }

        public Builder() {
        }
    }

}
