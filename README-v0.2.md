# fluentd-jdk-handler
Fluentd JDK log handler implementation

[![javadoc](https://javadoc.io/badge2/codes.vps/fluentd-jdk-handler/javadoc.svg)](https://javadoc.io/doc/codes.vps/fluentd-jdk-handler/0.2/)

This documentation is for latest (0.3 and above) version of the library. For older versions that used a 
different Fluentd sender, please see [README-v0.2.md](./README-v0.2.md).

This project provides JDK logging handler implementation that logs to fluentd daemon. The handler
uses [fluent-logger-java][1] library, which sends logging data to an `in_forward` input plugin.

The handler requires JDK 8 or above to run.

Details on JDK logging can be found [here][2].

JavaDocs for the implementation can be found [here][3]

# Integration

This code is available at Maven Central

```xml
<dependncy>
    <groupId>codes.vps</groupId>
    <artifactId>fluentd-jdk-handler</artifactId>
    <version>0.2</version>
</dependency>
```

The project also produced a jar with all dependencies which is more suitable for DevOps integration.
You can use the following Maven command to retrieve the jar:

```
$ mvn org.apache.maven.plugins:maven-dependency-plugin:3.1.2:get\
 -Dartifact=codes.vps:fluentd-jdk-handler:0.2:jar:jar-with-dependencies
```

The JAR will then be downloaded into (unless you've changed your local Maven repository location) to
`~/.m2/repository/codes/vps/fluentd-jdk-handler/0.2/fluentd-jdk-handler-0.2-jar-with-dependencies.jar`

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
* `FluentdHandler.timeout_ms`
<br>Specifies network timeout, in milliseconds, for sending data to fluentd (please see [fluent-logger-java][1] documentation
for information on how it is applied), default is `3000`
* `FluentdHandler.buffer_capacity`
<br>Specifies buffer capacity of the fluentd sender (please see [fluent-logger-java][1] documentation 
for information on how it is used), default is 8 mebibytes.
* `FluentdHandler.format`
<br>Specifies formatting string (see [Formatting](#formatting)) below. Default is
`tag"";message"${level10n} [${tid}] ${class}.${method} ${l10n}";stack"${trace}"`.

# Formatting

[fluent-logger-java][1] accepts the following parameters when logging a single message:
* tag
* timestamp
* map with arbitrary key/value pairs (values are objects)

The formatter process takes a [LogRecord][5] object and converts it into the input suitable for
[fluent-logger-java][1]. Format definition specifies a series of statements that indicate which keys
in that arbitrary map should be populated with which values. Then, keys with names "$tag" and "$timestamp"
are treated specially, they are removed from the map, and fed as tag and timestamp parameters directly
into [fluent-logger-java][1].

If tag ends up being not specified, it is populated from logger name value of the log record. If timestamp ends up 
being not specified, then it is populated from `millis` property of the log record. Note that timestamp value
as received by this library shall be in milliseconds, however it is converted to seconds (milliseconds are
truncated off) before it is passed to the Fluentd library. 

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


[1]: https://github.com/fluent/fluent-logger-java
[2]: https://docs.oracle.com/javase/8/docs/api/java/util/logging/Logger.html
[3]: https://javadoc.io/doc/codes.vps/fluentd-jdk-handler/0.2/
[4]: https://github.com/veselov/fluentd-jdk-handler/blob/master/src/main/java/codes/vps/logging/fluentd/jdk/sample/CreateHandler.java
[5]: https://docs.oracle.com/javase/8/docs/api/java/util/logging/LogRecord.html
[6]: https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html
