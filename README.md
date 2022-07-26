# fluentd-jdk-handler
Fluentd JDK log handler implementation

[![javadoc](https://javadoc.io/badge2/codes.vps/fluentd-jdk-handler/javadoc.svg)](https://javadoc.io/doc/codes.vps/fluentd-jdk-handler)

This project provides JDK logging handler implementation that logs to fluentd daemon. The handler
uses [fluency][1] library, which sends logging data to an `in_forward` input plugin.

The handler requires JDK 8 or above to run.

Details on JDK logging can be found [here][2].

JavaDocs for the implementation (latest version) can be found [here][3]

# Integration

This code is available at Maven Central

```xml
<dependency>
    <groupId>codes.vps</groupId>
    <artifactId>fluentd-jdk-handler</artifactId>
    <version>0.3</version>
</dependency>
```

The project also produced a jar with all dependencies which is more suitable for DevOps integration.
You can use the following Maven command to retrieve the jar:

```
$ mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.2:get\
 -Dartifact=codes.vps:fluentd-jdk-handler:0.3:jar:jar-with-dependencies
```

The JAR will then be downloaded into (unless you've changed your local Maven repository location) to
`~/.m2/repository/codes/vps/fluentd-jdk-handler/0.3/fluentd-jdk-handler-0.3-jar-with-dependencies.jar`

This can be used in scripts and so on.

# Configuration

When invoked through API, configuration information is to be provided in the Builder object to the
handler, [see example][4]

When configuring through a logging framework property files, the handler shall be configured
with its classname, `FluentdHandler`, and
the following properties are available:

* `FluentdHandler.tag_prefix`, default is an empty string
<br>Specifies tag prefix for all messages sent through the corresponding fluentd logger.
* `FluentdHandler.host`
<br>Specifies host name to send messages to, default is `127.0.0.1`
* `FluentdHandler.port`
<br>Specifies port to send messages to, default is `24224`
* `FluentdHandler.format`
<br>Specifies formatting string (see [Formatting](#formatting)) below. Default is
`tag"";message"${level10n} [${tid}] ${class}.${method} ${l10n}";stack"${trace}"`.
* Fluency configuration options; please see [fluency][1] for the additional documentation on those. 
  * `FluentdHandler.sender_max_retry_count`
<br>Maximum retry count, default is 7
  * `FluentdHandler.sender_base_retry_interval_millis`
<br>Initial retry interval, in milliseconds, default is `400`
  * `FluentdHandler.sender_max_retry_interval_millis`
<br>Maximum retry interval, in milliseconds, default is `30000`
  * `FluentdHandler.ack_response_mode`
<br>Request acknowledgement for packets sent to fluentd, default is `false`
  * `FluentdHandler.ssl_enabled`
<br>Specifies whether SSL connection should be used, default is `false`
  * `FluentdHandler.connection_timeout_milli`
<br>Specified connection timeout, in milliseconds, default is `5000`
  * `FluentdHandler.read_timeout_milli`
<br>Specified socket read timeout, in milliseconds, default is `5000`
  * `FluentdHandler.max_buffer_size`
<br>Maximum buffer size to use, in bytes, default is `536870912`
  * `FluentdHandler.buffer_chunk_initial_size`
<br>Initial buffer chunk size, default is `1048576`
  * `FluentdHandler.buffer_chunk_retention_size`
<br>Threshold chunk buffer size to flush, default is `4194304`
  * `FluentdHandler.buffer_chunk_retention_time_millis`
<br>Threshold time to flush the buffer, in milliseconds, default is `1000`
  * `FluentdHandler.flush_attempt_interval_millis`
<br>Flush attempt interval, in milliseconds, default is `600`
  * `FluentdHandler.file_backup_dir`
<br>Directory where the logging message shall be backed up in, default is not set.
  * `FluentdHandler.wait_until_buffer_flushed`
<br>Time to wait until the buffer is flushed, in seconds, default is `60` 
  * `FluentdHandler.wait_until_flusher_terminated`
<br>Time to wait until the flusher is terminated, in seconds, default is `60` 
  * `FluentdHandler.jvm_head_buffer_mode`
<br>Specified whether to enable heap buffer memory (`true`) or off-heap buffer memory (`false`), default is `false`. 

# Formatting

[fluency][1] accepts the following parameters when logging a single message:
* tag
* timestamp
* map with arbitrary key/value pairs (values are objects)

The formatter process takes a [LogRecord][5] object and converts it into the input suitable for
[fluency][1]. Format definition specifies a series of statements that indicate which keys
in that arbitrary map should be populated with which values. Then, keys with names "$tag" and "$timestamp"
are treated specially, they are removed from the map, and fed as tag and timestamp parameters directly
into [fluency][1].

If tag ends up being not specified, it is populated from logger name value of the log record. If timestamp ends up 
being not specified, then it is populated from `millis` property of the log record.

Formatter string is defined as follows:
* `format := item [ ';' item ... ]`
* `item := field '"' format '"' [ type ]`
* `field := <literal map field name>`
* `type := 's' | 'n' | 'b'` (string, number, or boolean)
* `format := <format string to generate value>`

The literals in format string will be copied (after escaping) to the output as is.
variables can, however, be referenced using `${...}`, e.g. `${level}`. When referencing
`millis` - additional date format, after `,`, can be provided, in this case the value
will be passed through a date formatter:
[SimpleDateFormatter][6]. For example: `date"${millis,yyyy-MM-dd'T'HH:mm:ss.SSSZ}`

Any character can be escaped from current level of processing
by specifying backslash (`\ `) character in front of it. To insert backslash itself,
simply escape it (`\\` parses as `\ `). Escaping must be done on multiple
levels if need to escape nested structures, i.e. the text is "unescaped" when:
* items are extracted
* field, type, format are extracted
* contents of format are extracted

List of variables that can be referenced (based on LogRecord class parameters):
* `level` - log level
* `level10n` - localized log level
* `sequence` - log sequence
* `class` - source class
* `method` - source method
* `message` - original message value as is;
* `l10n` - localized message, message+parameters will be passed through l10n
* `params` - localization parameters (printed as comma-separated string representations)
* `millis` - timestamp
* `logger` - name of the logger
* `tid` - thread ID
* `trace` - entire stack trace of an attached exception, if any, or an empty string

Example format:
`logger"${logger}";level"${level}";$timestamp"${millis}n";message"${l10n}"`


[1]: https://github.com/komamitsu/fluency
[2]: https://docs.oracle.com/javase/8/docs/api/java/util/logging/Logger.html
[3]: https://javadoc.io/doc/codes.vps/fluentd-jdk-handler
[4]: https://github.com/veselov/fluentd-jdk-handler/blob/master/src/main/java/codes/vps/logging/fluentd/jdk/sample/CreateHandler.java
[5]: https://docs.oracle.com/javase/8/docs/api/java/util/logging/LogRecord.html
[6]: https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html
