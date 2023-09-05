package net.goui.flogger.testing.log4j2;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import net.goui.flogger.testing.LevelClass;
import net.goui.flogger.testing.LogEntry;
import net.goui.flogger.testing.api.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter.Result;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.LevelRangeFilter;
import org.apache.logging.log4j.util.ReadOnlyStringMap;

/** Log interceptor for Log4J2 logging. */
public final class Log4jInterceptor implements LogInterceptor {
  private static final int JDK_SEVERE_VALUE = java.util.logging.Level.SEVERE.intValue();
  private static final int JDK_WARNING_VALUE = java.util.logging.Level.WARNING.intValue();
  private static final int JDK_INFO_VALUE = java.util.logging.Level.INFO.intValue();
  private static final int JDK_FINE_VALUE = java.util.logging.Level.FINE.intValue();

  @AutoService(LogInterceptor.Factory.class)
  public static final class Factory extends AbstractLogInterceptorFactory {
    @Override
    public LogInterceptor get() {
      return Log4jInterceptor.create();
    }

    @Override
    protected void configureUnderlyingLoggerForInfoLogging(String loggerName) {
      Logger underlyingLogger = (Logger) LogManager.getLogger(loggerName);
      Configurator.setLevel(underlyingLogger, Level.INFO);
      underlyingLogger.setAdditive(false);
    }
  }

  private static final MetadataExtractor<LogEvent> DEFAULT_LOG4J_EXTRACTOR =
      e -> DefaultFormatMetadataParser.parse(e.getMessage().getFormattedMessage());

  public static LogInterceptor create() {
    return create(DEFAULT_LOG4J_EXTRACTOR);
  }

  public static LogInterceptor create(MetadataExtractor<LogEvent> metadataExtractor) {
    return new Log4jInterceptor(metadataExtractor);
  }

  private final MetadataExtractor<LogEvent> metadataExtractor;

  private Log4jInterceptor(MetadataExtractor<LogEvent> metadataExtractor) {
    this.metadataExtractor = checkNotNull(metadataExtractor);
  }

  @Override
  public Recorder attachTo(
      String loggerName,
      java.util.logging.Level jdkLevel,
      Consumer<LogEntry> collector,
      String testId) {
    // WARNING: Log4J is unintuitive with log level ordering (compared to JDK). A "high" level means
    // high verbosity (i.e. what most people call "low level logging").
    Level log4JLevel = toLog4JLevel(jdkLevel);
    LevelRangeFilter specifiedLevelAndAbove =
        LevelRangeFilter.createFilter(
            /* minLevel (null ==> max) */ null,
            /* maxLevel */ log4JLevel,
            /* onMatch (null ==> accept) */ Result.NEUTRAL,
            /* onMismatch (null ==> deny) */ Result.DENY);

    Appender appender =
        new AbstractAppender(
            "CapturingAppender",
            specifiedLevelAndAbove,
            /* layout */ null,
            /* ignoreExceptions */ true,
            EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            MessageAndMetadata mm = metadataExtractor.extract(event);
            if (LogInterceptor.shouldCollect(mm, testId)) {
              collector.accept(toLogEntry(event, mm));
            }
          }
        };

    // If the call to configureUnderlyingLoggerForInfoLogging() succeeded, we should be
    // able to cast the context instance.
    LoggerContext context = (LoggerContext) LogManager.getContext(false);
    // The existing config for the logger name might be a parent config.
    LoggerConfig oldConfig = context.getConfiguration().getLoggerConfig(loggerName);
    // Add a new logger config or use the existing one.
    LoggerConfig config =
        loggerName.equals(oldConfig.getName())
            ? oldConfig
            : new LoggerConfig(loggerName, log4JLevel, true);
    // Actually adds an AppenderRef
    config.addAppender(appender, null, null);
    // Sets the level of the LoggerConfig
    config.setLevel(log4JLevel);
    context.getConfiguration().addLogger(loggerName, config);
    context.updateLoggers();
    return () -> {
      try {
        context.getConfiguration().removeLogger(loggerName);
        context.updateLoggers();
      } catch (RuntimeException e) {
        // Ignored on close().
      }
    };
  }

  private LogEntry toLogEntry(LogEvent event, MessageAndMetadata mm) {
    StackTraceElement source = event.getSource(); // nullable
    Level log4jLevel = event.getLevel();
    var ts = event.getInstant();
    Instant timestamp = Instant.ofEpochSecond(ts.getEpochSecond(), ts.getNanoOfSecond());
    ImmutableMap<String, ImmutableList<Object>> metadata = mergeMdcValues(event, mm.metadata());
    return LogEntry.of(
        source != null ? source.getClassName() : null,
        source != null ? source.getMethodName() : null,
        log4jLevel.name(),
        toLevelClass(log4jLevel),
        timestamp,
        event.getThreadId(),
        mm.message(),
        metadata,
        event.getThrown());
  }

  private static ImmutableMap<String, ImmutableList<Object>> mergeMdcValues(
      LogEvent event, ImmutableMap<String, ImmutableList<Object>> metadata) {
    ReadOnlyStringMap mdc = event.getContextData();
    if (!mdc.isEmpty()) {
      LinkedHashMap<String, ImmutableList<Object>> withMdc = new LinkedHashMap<>(metadata);
      mdc.forEach(
          (k, v, b) -> {
            Object safeValue = toSafeValue(v);
            if (safeValue != null) {
              ImmutableList<Object> values = withMdc.get(k);
              if (values != null) {
                // Since the MDC is NOT a multimap, each key only appears at most once, so we never
                // see repeated resizing of the values list.
                ArrayList<Object> appended = new ArrayList<>(values.size() + 1);
                appended.add(safeValue);
                withMdc.put(k, ImmutableList.copyOf(appended));
              } else {
                withMdc.put(k, ImmutableList.of(safeValue));
              }
            }
          },
          withMdc);
      metadata = ImmutableMap.copyOf(withMdc);
    }
    return metadata;
  }

  // Convert arbitrary values into metadata compatible types (Double, Long, Boolean, String) to
  // avoid carrying large/mutable instances into test logic.
  private static Object toSafeValue(Object v) {
    if (v instanceof Double || v instanceof Float || v instanceof BigDecimal) {
      return ((Number) v).doubleValue();
    }
    if (v instanceof Number) {
      return ((Number) v).longValue();
    }
    if (v instanceof Boolean) {
      return v;
    }
    try {
      return v.toString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  // WARNING: Log4J level values *decrease* with severity.
  private static LevelClass toLevelClass(Level level) {
    if (level.compareTo(Level.ERROR) <= 0) return LevelClass.SEVERE;
    if (level.compareTo(Level.WARN) <= 0) return LevelClass.WARNING;
    if (level.compareTo(Level.INFO) <= 0) return LevelClass.INFO;
    if (level.compareTo(Level.DEBUG) <= 0) return LevelClass.FINE;
    return LevelClass.FINEST;
  }

  private static org.apache.logging.log4j.Level toLog4JLevel(java.util.logging.Level level) {
    int jdkLevelValue = level.intValue();
    if (jdkLevelValue >= JDK_SEVERE_VALUE) return Level.ERROR;
    if (jdkLevelValue >= JDK_WARNING_VALUE) return Level.WARN;
    if (jdkLevelValue >= JDK_INFO_VALUE) return Level.INFO;
    if (jdkLevelValue >= JDK_FINE_VALUE) return Level.DEBUG;
    return org.apache.logging.log4j.Level.TRACE;
  }
}
